package org.sunbird.viewer.competency

import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.commons.lang3.StringUtils
import org.slf4j.LoggerFactory
import org.sunbird.common.ProjectUtil
import org.sunbird.http.HttpUtil
import org.sunbird.request.RequestContext

import java.util
import scala.collection.JavaConverters._

/**
 * Resolves a competency framework into the vocabulary the engine needs, and resolves the `skills`
 * tags off content.
 *
 * The framework holds the skill tree only: the `competency` category, whose terms nest by
 * `children`. A term with no children is a leaf, and a leaf is the only thing content may be tagged
 * with or a role may require. Roles live in `role_skill` and are read per request by `withRoles`.
 *
 * An unresolvable framework is not silently treated as "no skills": callers get
 * CompetencyMeta.empty and are expected to surface it.
 */
object CompetencyFrameworkUtil {

  private val mapper = new ObjectMapper()
  private val logger = LoggerFactory.getLogger(classOf[CompetencyFrameworkUtil])

  private def cfg(key: String, default: String): String =
    Option(ProjectUtil.getConfigValue(key)).map(_.trim).filter(_.nonEmpty).getOrElse(default)

  private def cfgLong(key: String, default: Long): Long =
    Option(ProjectUtil.getConfigValue(key)).map(_.trim).filter(_.nonEmpty).map(_.toLong).getOrElse(default)

  private val metaTtl: Long = cfgLong("competency_meta_cache_ttl", 3600L) * 1000L
  private val metaCache = new java.util.concurrent.ConcurrentHashMap[String, (Long, CompetencyMeta)]()
  // node id -> its skill tags. Keeps transition-time crediting off the search service.
  private val claimCache = new java.util.concurrent.ConcurrentHashMap[String, (Long, List[String])]()
  // collection id -> the competencyFramework it declares
  private val fwOfCollection = new java.util.concurrent.ConcurrentHashMap[String, (Long, String)]()

  def invalidate(frameworkId: String): Unit =
    if (StringUtils.isBlank(frameworkId)) {
      metaCache.clear(); claimCache.clear(); fwOfCollection.clear()
    } else {
      metaCache.remove(frameworkId)
      claimCache.clear(); fwOfCollection.clear()
    }

  def cachedFrameworks: Set[String] = metaCache.keySet().asScala.toSet

  // ---- response parsing (kept pure and package-visible so it is unit-testable) ----------------

  private[competency] def result(json: String): util.Map[String, AnyRef] =
    try {
      mapper.readValue(json, classOf[util.Map[String, AnyRef]])
        .getOrDefault("result", new util.HashMap[String, AnyRef]()).asInstanceOf[util.Map[String, AnyRef]]
    } catch {
      case ex: Exception =>
        logger.warn("CompetencyFrameworkUtil: unparseable response body", ex)
        new util.HashMap[String, AnyRef]()
    }

  private def asList(v: AnyRef): List[util.Map[String, AnyRef]] = v match {
    case l: util.List[_] => l.asInstanceOf[util.List[util.Map[String, AnyRef]]].asScala.toList
    case _ => Nil
  }

  private def asStrings(v: AnyRef): List[String] = v match {
    case l: util.List[_] =>
      l.asInstanceOf[util.List[AnyRef]].asScala.toList
        .filter(_ != null).map(_.toString.trim).filter(_.nonEmpty)
    case s: String if s.nonEmpty => List(s)
    case _ => Nil
  }

  private def str(m: util.Map[String, AnyRef], k: String): Option[String] =
    Option(m.get(k)).map(_.toString).filter(_.nonEmpty)

  private[competency] def frameworkMap(frameworkJson: String): util.Map[String, AnyRef] =
    result(frameworkJson).getOrDefault("framework", new util.HashMap[String, AnyRef]())
      .asInstanceOf[util.Map[String, AnyRef]]

  private[competency] def frameworkField(frameworkJson: String, field: String): Option[String] =
    str(frameworkMap(frameworkJson), field)

  /** Categories in authored order: by `index`, falling back to their position in the response. */
  private[competency] def categoriesOf(frameworkJson: String): List[util.Map[String, AnyRef]] =
    asList(frameworkMap(frameworkJson).get("categories")).zipWithIndex.sortBy { case (c, pos) =>
      Option(c.get("index")).collect { case n: Number => n.intValue() }.getOrElse(pos)
    }.map(_._1)

  /** Skills are the last category by `index`; its code is the field content carries them in. */
  private[competency] def parseSkillField(frameworkJson: String): Option[String] =
    categoriesOf(frameworkJson).lastOption.flatMap(c => str(c, "code"))

  /**
   * The skill category's leaves, and how deep the framework goes.
   *
   * Every term of the last category is a skill. Inside it, terms may still nest by `children` or
   * name a `parentCode`/`parents` in the same category; then only a term nobody descends from counts.
   * Depth is the categories above plus the nesting inside the skill category.
   */
  private[competency] def parseTree(frameworkJson: String): (Set[String], Int) = {
    val categories = categoriesOf(frameworkJson)
    val terms = categories.lastOption.map(c => asList(c.get("terms"))).getOrElse(Nil)
    if (terms.isEmpty) return (Set.empty, 0)

    def flatten(t: util.Map[String, AnyRef], tier: Int): List[(util.Map[String, AnyRef], Int)] =
      (t, tier) :: asList(t.get("children")).flatMap(flatten(_, tier + 1))
    val all = terms.flatMap(flatten(_, 1))
    val codes = all.flatMap { case (t, _) => str(t, "code") }.toSet

    val parentOf: Map[String, String] = all.flatMap { case (t, _) =>
      val parent = str(t, "parentCode")
        .orElse(asList(t.get("parents")).flatMap(p => str(p, "code")).headOption)
        .filter(codes.contains)
      for { code <- str(t, "code"); p <- parent } yield code -> p
    }.toMap
    def chainTier(code: String, seen: Set[String] = Set.empty): Int =
      parentOf.get(code) match {
        case Some(p) if !seen.contains(p) => 1 + chainTier(p, seen + code)
        case _ => 1
      }

    val parents = parentOf.values.toSet
    val leaves = all.collect {
      case (t, tier) if asList(t.get("children")).isEmpty => str(t, "code").map(_ -> tier)
    }.flatten.filterNot { case (code, _) => parents.contains(code) }
    if (leaves.isEmpty) return (Set.empty, 0)
    val inner = leaves.map { case (code, tier) => tier + chainTier(code) - 1 }.max
    (leaves.map(_._1).toSet, categories.size - 1 + inner)
  }

  /**
   * Keeps only the role requirements that name a real leaf.
   *
   * The role -> skill map is authored in `role_skill`, not in the framework, so nothing validates it
   * at write time against a tree the writer cannot see. A role requiring a non-leaf - an interior
   * term, or a code retired out of the tree - is a spec error: interior terms are never tagged, so
   * no evidence path exists and the skill can never be held. Dropping it keeps readiness honest;
   * crediting it would report a learner ready for a role they cannot complete.
   *
   * Drops are logged rather than thrown: one bad requirement must not take down the profile page
   * for every learner on the framework.
   */
  private[competency] def leafOnly(frameworkId: String,
                                   authored: Map[String, Set[String]],
                                   leaves: Set[String]): Map[String, Set[String]] =
    authored.map { case (role, declared) =>
      val (ok, bad) = declared.partition(leaves.contains)
      if (bad.nonEmpty)
        logger.warn(s"CompetencyFrameworkUtil: framework $frameworkId role $role requires non-leaf " +
          s"or unknown skills [${bad.toList.sorted.mkString(",")}]; ignored")
      role -> ok
    }

  /** Courses and Learning Paths off a search row, for the recommendation. */
  private[competency] def parseCandidates(searchJson: String, skillField: String): List[Candidate] = {
    val rows = result(searchJson).asScala.values.collect {
      case l: util.List[_] => l.asInstanceOf[util.List[util.Map[String, AnyRef]]].asScala
    }.flatten.toList
    rows.flatMap { r =>
      val skills = asStrings(r.get(skillField)).distinct.toSet
      for { id <- str(r, "identifier") if skills.nonEmpty } yield Candidate(
        id = id,
        name = str(r, "name").getOrElse(id),
        primaryCategory = str(r, "primaryCategory").getOrElse(""),
        skills = skills)
    }.distinct
  }

  /** `<skillField>: [code]` off a content, collection or question search row. */
  private[competency] def parseClaims(searchJson: String, skillField: String): Map[String, List[String]] = {
    val rows = result(searchJson).asScala.values.collect {
      case l: util.List[_] => l.asInstanceOf[util.List[util.Map[String, AnyRef]]].asScala
    }.flatten.toList
    rows.flatMap { r =>
      str(r, "identifier").map(id => id -> asStrings(r.get(skillField)).distinct)
    }.filter(_._2.nonEmpty).toMap
  }

  /**
   * `roleSource` supplies the role -> skill map for a framework, normally `CompetencyDao
   * .readRoleSkills`. Injected rather than taken as a DAO dependency so the parsing half of this
   * object stays free of Cassandra, and so a test can drive it with a literal map.
   */
  def apply(roleSource: (String, RequestContext) => RoleIndex): CompetencyFrameworkUtil =
    new CompetencyFrameworkUtil(roleSource)
}

class CompetencyFrameworkUtil(roleSource: (String, RequestContext) => RoleIndex) {

  import CompetencyFrameworkUtil._

  private def searchUrl: String = cfg("service_search_base_path", "http://localhost:9000") + "/v3/search"

  private def frameworkReadUrl(id: String): String =
    cfg("content_service_base_url", "http://localhost:9000") +
      cfg("sunbird_competency_framework_read_api", "/competency-framework/v3/read") + "/" + id

  private def post(url: String, body: String): String = {
    val headers = new util.HashMap[String, String]() {{ put("Content-Type", "application/json") }}
    try {
      val r = HttpUtil.doPostRequest(url, body, headers)
      if (r != null && r.getStatusCode == 200 && StringUtils.isNotBlank(r.getBody)) r.getBody
      else { logger.warn(s"CompetencyFrameworkUtil: POST degraded status=${if (r != null) r.getStatusCode else -1} url=$url"); "{}" }
    } catch {
      case ex: Exception => logger.warn(s"CompetencyFrameworkUtil: POST failed url=$url", ex); "{}"
    }
  }

  private def get(url: String): String =
    try {
      val body = HttpUtil.sendGetRequest(url, new util.HashMap[String, String]())
      if (StringUtils.isBlank(body)) "{}" else body
    } catch {
      case ex: Exception => logger.warn(s"CompetencyFrameworkUtil: GET failed url=$url", ex); "{}"
    }

  private def searchByIds(ids: List[String], fields: List[String]): String = {
    if (ids.isEmpty || fields.isEmpty) return "{}"
    val filters = new util.HashMap[String, AnyRef]() {{
      put("status", util.Arrays.asList("Live")); put("identifier", ids.asJava)
    }}
    val request = new util.HashMap[String, AnyRef]() {{ put("filters", filters); put("fields", fields.asJava) }}
    post(searchUrl, mapper.writeValueAsString(new util.HashMap[String, AnyRef]() {{ put("request", request) }}))
  }

  /** Raw framework read response. Overridable so a test can serve a framework without Knowlg. */
  protected def fetchFramework(frameworkId: String): String = get(frameworkReadUrl(frameworkId))

  /** Framework vocabulary, cached. CompetencyMeta.empty when the framework does not resolve. */
  def meta(frameworkId: String, ctx: RequestContext): CompetencyMeta = {
    if (StringUtils.isBlank(frameworkId)) return CompetencyMeta.empty
    val now = System.currentTimeMillis()
    val hit = metaCache.get(frameworkId)
    if (hit != null && hit._1 > now) return hit._2

    val json = fetchFramework(frameworkId)
    val (leaves, depth) = parseTree(json)
    if (leaves.isEmpty) {
      logger.warn(s"CompetencyFrameworkUtil: framework $frameworkId resolved no leaf skills; " +
        "check that its last category (by index) holds the skill terms")
      return CompetencyMeta.empty
    }
    if (depth < 3)
      logger.warn(s"CompetencyFrameworkUtil: framework $frameworkId is only $depth tiers deep; " +
        "the spec requires at least three")

    // Roles are NOT cached here: the cache is per JVM, so a role edit on another pod would stay
    // invisible for the whole TTL. `withRoles` reads them fresh.
    val built = CompetencyMeta(
      frameworkId = frameworkId,
      skillField = parseSkillField(json).getOrElse(""),
      leaves = leaves,
      depth = depth,
      roleSkills = Map.empty,
      claimsByNode = Map.empty)
    metaCache.put(frameworkId, (now + metaTtl, built))
    built
  }

  /**
   * The cached vocabulary plus the framework's roles, read from `role_skill` on every call so a
   * role edit on any pod is seen at once. One partition read; only the role paths pay for it.
   */
  def withRoles(frameworkId: String, ctx: RequestContext): CompetencyMeta = {
    val m = meta(frameworkId, ctx)
    if (m.isEmpty) m
    else {
      val roles = roleSource(frameworkId, ctx)
      m.copy(roleSkills = leafOnly(frameworkId, roles.skills, m.leaves), retiredRoles = roles.retired)
    }
  }

  /**
   * Leaf skills one role requires, RETIRED roles included. None when the role does not exist or
   * requires no skill that is still a leaf: such a role cannot be measured, and an empty
   * requirement set must never read as fully met.
   */
  def requirements(frameworkId: String, roleId: String, ctx: RequestContext): Option[Set[String]] =
    if (StringUtils.isBlank(frameworkId) || StringUtils.isBlank(roleId)) None
    else Some(withRoles(frameworkId, ctx).skillsOf(roleId)).filter(_.nonEmpty)

  def allRequirements(frameworkId: String, ctx: RequestContext): Map[String, Set[String]] =
    if (StringUtils.isBlank(frameworkId)) Map.empty
    else withRoles(frameworkId, ctx).roleSkills

  /** `competencyFramework` declared on a collection. */
  def frameworkOf(collectionId: String, ctx: RequestContext): Option[String] = {
    if (StringUtils.isBlank(collectionId)) return None
    val now = System.currentTimeMillis()
    val hit = fwOfCollection.get(collectionId)
    if (hit != null && hit._1 > now) return Some(hit._2).filter(_.nonEmpty)
    val json = searchByIds(List(collectionId), List("competencyFramework"))
    val fw = result(json).asScala.values.collect {
      case l: util.List[_] => l.asInstanceOf[util.List[util.Map[String, AnyRef]]].asScala
    }.flatten.toList.headOption
      .flatMap(r => Option(r.get("competencyFramework")).map(_.toString)).filter(_.nonEmpty)
    fwOfCollection.put(collectionId, (now + metaTtl, fw.getOrElse("")))
    fw
  }

  /** `targetRole` declared on a collection. Not cached: read once per coverage request. */
  def targetRoleOf(collectionId: String, ctx: RequestContext): Option[String] = {
    if (StringUtils.isBlank(collectionId)) return None
    val json = searchByIds(List(collectionId), List("targetRole"))
    result(json).asScala.values.collect {
      case l: util.List[_] => l.asInstanceOf[util.List[util.Map[String, AnyRef]]].asScala
    }.flatten.toList.headOption.flatMap(r => str(r, "targetRole"))
  }

  /**
   * Live courses and Learning Paths teaching any of the given skills.
   *
   * Not cached: the learner's gap is the query, so a hit rate would be poor, and a stale
   * recommendation is worse than a slow one. Assessment courses are excluded — an evaluation is
   * not something to recommend as learning.
   */
  def candidatesFor(skills: Set[String], skillField: String, ctx: RequestContext): List[Candidate] = {
    if (skills.isEmpty || StringUtils.isBlank(skillField)) return Nil
    val categories = cfg("competency_recommend_primary_categories", "Course,Learning Path")
      .split(",").map(_.trim).filter(_.nonEmpty).toList
    val filters = new util.HashMap[String, AnyRef]() {{
      put("status", util.Arrays.asList("Live"))
      put(skillField, skills.toList.asJava)
      put("primaryCategory", categories.asJava)
    }}
    val request = new util.HashMap[String, AnyRef]() {{
      put("filters", filters)
      put("fields", List("identifier", "name", "primaryCategory", skillField).asJava)
      put("limit", Integer.valueOf(cfg("competency_recommend_limit", "50").toInt))
    }}
    val json = post(searchUrl,
      mapper.writeValueAsString(new util.HashMap[String, AnyRef]() {{ put("request", request) }}))
    parseCandidates(json, skillField)
  }

  /** Skill tags, read from `skillField`, for the given content ids (courses, question sets, questions). */
  def claimsOf(nodeIds: List[String], skillField: String, ctx: RequestContext): Map[String, List[String]] = {
    if (nodeIds.isEmpty || StringUtils.isBlank(skillField)) return Map.empty
    val now = System.currentTimeMillis()
    val wanted = nodeIds.distinct.filter(_.nonEmpty)
    val cached = wanted.flatMap { id =>
      val hit = claimCache.get(skillField + "|" + id)
      if (hit != null && hit._1 > now) Some(id -> hit._2) else None
    }.toMap
    val missing = wanted.filterNot(cached.contains)
    val fetched =
      if (missing.isEmpty) Map.empty[String, List[String]]
      else parseClaims(searchByIds(missing, List(skillField, "primaryCategory")), skillField)
    // negative-cache the misses too, so an untagged node is not searched again on every event
    missing.foreach(id => claimCache.put(skillField + "|" + id, (now + metaTtl, fetched.getOrElse(id, Nil))))
    (cached ++ fetched).filter(_._2.nonEmpty)
  }

  /**
   * Skill tags of one node, restricted to leaves of the given framework.
   *
   * A course tagged with a non-leaf term credits nothing: there is no rule for rolling a parent up
   * from its children, and crediting the parent directly would make the tree's shape meaningless.
   */
  def leafClaimsOf(nodeIds: List[String], m: CompetencyMeta,
                   ctx: RequestContext): Map[String, List[String]] = {
    if (m.isEmpty) return Map.empty
    claimsOf(nodeIds, m.skillField, ctx).map { case (node, codes) =>
      val (ok, bad) = codes.partition(m.isLeaf)
      if (bad.nonEmpty)
        logger.warn(s"CompetencyFrameworkUtil: node $node is tagged with non-leaf skills " +
          s"[${bad.mkString(",")}] in ${m.frameworkId}; ignored")
      node -> ok
    }.filter(_._2.nonEmpty)
  }
}
