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
 * Two categories: `competency`, whose terms nest by `children`, and `role`, whose terms associate
 * to the leaf skills they require. A term with no children is a leaf, and a leaf is the only thing
 * content may be tagged with or a role may require.
 *
 * An unresolvable framework is not silently treated as "no skills": callers get
 * CompetencyMeta.empty and are expected to surface it.
 */
object CompetencyFrameworkUtil {

  private val mapper = new ObjectMapper()
  private val logger = LoggerFactory.getLogger(classOf[CompetencyFrameworkUtil])

  val CAT_COMPETENCY = "competency"
  val CAT_ROLE = "role"

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

  /** Terms of one framework category, by category `code`. */
  private[competency] def termsOf(frameworkJson: String, categoryCode: String): List[util.Map[String, AnyRef]] =
    asList(frameworkMap(frameworkJson).get("categories"))
      .find(c => str(c, "code").exists(_.equalsIgnoreCase(categoryCode)))
      .map(c => asList(c.get("terms")))
      .getOrElse(Nil)

  private[competency] def frameworkField(frameworkJson: String, field: String): Option[String] =
    str(frameworkMap(frameworkJson), field)

  /**
   * Display labels for the tree's depths, outermost first. Accepts either a list or a single
   * pipe-separated string, because a workbook round-trip produces the latter.
   */
  private[competency] def parseTierLabels(frameworkJson: String): List[String] =
    Option(frameworkMap(frameworkJson).get("tierLabels")) match {
      case Some(s: String) => s.split("\\|").map(_.trim).filter(_.nonEmpty).toList
      case Some(other) => asStrings(other)
      case None => Nil
    }

  /**
   * The skill tree's leaves, and how deep it goes.
   *
   * Primary shape is nested `children`, which is what framework read returns. The fallback covers
   * a flat term list carrying `parents` or `parentCode`: a code no other term claims as its parent
   * is a leaf. Both shapes agree on the answer; only the traversal differs.
   */
  private[competency] def parseTree(frameworkJson: String): (Set[String], Int) = {
    val roots = termsOf(frameworkJson, CAT_COMPETENCY)
    if (roots.isEmpty) return (Set.empty, 0)

    val nestedLeaves = scala.collection.mutable.Set[String]()
    var nestedDepth = 0

    def walk(terms: List[util.Map[String, AnyRef]], tier: Int): Unit =
      terms.foreach { t =>
        val kids = asList(t.get("children"))
        if (kids.isEmpty) str(t, "code").foreach { code =>
          nestedLeaves += code
          if (tier > nestedDepth) nestedDepth = tier
        }
        else walk(kids, tier + 1)
      }

    walk(roots, 1)
    if (nestedDepth > 1) return (nestedLeaves.toSet, nestedDepth)

    // Flat shape: derive the parent of each term, then anything that is nobody's parent is a leaf.
    val parentOf: Map[String, String] = roots.flatMap { t =>
      val parent = str(t, "parentCode")
        .orElse(asList(t.get("parents")).flatMap(p => str(p, "code")).headOption)
      for { code <- str(t, "code"); p <- parent } yield code -> p
    }.toMap
    if (parentOf.isEmpty) return (nestedLeaves.toSet, nestedDepth)

    val codes = roots.flatMap(t => str(t, "code")).toSet
    val parents = parentOf.values.toSet
    val leaves = codes.diff(parents)

    def tierOf(code: String, seen: Set[String] = Set.empty): Int =
      parentOf.get(code) match {
        case Some(p) if !seen.contains(p) && codes.contains(p) => 1 + tierOf(p, seen + code)
        case _ => 1
      }

    (leaves, if (leaves.isEmpty) 0 else leaves.map(c => tierOf(c)).max)
  }

  /** A term's `associations`, grouped by the associated term's category code. */
  private[competency] def associationsByCategory(term: util.Map[String, AnyRef]): Map[String, List[String]] =
    asList(term.get("associations"))
      .flatMap(a => for { cat <- str(a, "category"); code <- str(a, "code").orElse(str(a, "identifier")) }
        yield cat.toLowerCase -> code)
      .groupBy(_._1).map { case (k, v) => k -> v.map(_._2) }

  /**
   * Role code to the leaf skills it requires.
   *
   * A role associating a non-leaf term is a spec error the importer should have caught; drop the
   * association and say so rather than crediting a skill nothing can be tagged with.
   */
  private[competency] def parseRoles(frameworkJson: String, leaves: Set[String]): Map[String, Set[String]] =
    termsOf(frameworkJson, CAT_ROLE).flatMap { t =>
      str(t, "code").map { role =>
        val declared = associationsByCategory(t).getOrElse(CAT_COMPETENCY, Nil).distinct
        val (ok, bad) = declared.partition(leaves.contains)
        if (bad.nonEmpty)
          logger.warn(s"CompetencyFrameworkUtil: role $role requires non-leaf terms [${bad.mkString(",")}]; ignored")
        role -> ok.toSet
      }
    }.toMap

  /** `skills: [code]` off a content, collection or question search row. */
  private[competency] def parseClaims(searchJson: String): Map[String, List[String]] = {
    val rows = result(searchJson).asScala.values.collect {
      case l: util.List[_] => l.asInstanceOf[util.List[util.Map[String, AnyRef]]].asScala
    }.flatten.toList
    rows.flatMap { r =>
      str(r, "identifier").map(id => id -> asStrings(r.get("skills")).distinct)
    }.filter(_._2.nonEmpty).toMap
  }

  def apply(): CompetencyFrameworkUtil = new CompetencyFrameworkUtil()
}

class CompetencyFrameworkUtil {

  import CompetencyFrameworkUtil._

  private def searchUrl: String = cfg("service_search_base_path", "http://localhost:9000") + "/v3/search"

  private def frameworkReadUrl(id: String): String =
    cfg("sunbird_api_base_url", "http://localhost:5000") +
      cfg("sunbird_framework_read_api", "/v1/framework/read") + "/" + id

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

  /** Framework vocabulary, cached. CompetencyMeta.empty when the framework does not resolve. */
  def meta(frameworkId: String, ctx: RequestContext): CompetencyMeta = {
    if (StringUtils.isBlank(frameworkId)) return CompetencyMeta.empty
    val now = System.currentTimeMillis()
    val hit = metaCache.get(frameworkId)
    if (hit != null && hit._1 > now) return hit._2

    val json = get(frameworkReadUrl(frameworkId))
    val (leaves, depth) = parseTree(json)
    if (leaves.isEmpty) {
      logger.warn(s"CompetencyFrameworkUtil: framework $frameworkId resolved no leaf skills; " +
        "check that the competency category exists and its terms nest by children")
      return CompetencyMeta.empty
    }
    if (depth < 3)
      logger.warn(s"CompetencyFrameworkUtil: framework $frameworkId is only $depth tiers deep; " +
        "the spec requires at least three")

    val built = CompetencyMeta(
      frameworkId = frameworkId,
      tierLabels = parseTierLabels(json),
      leaves = leaves,
      depth = depth,
      roleSkills = parseRoles(json, leaves),
      claimsByNode = Map.empty)
    metaCache.put(frameworkId, (now + metaTtl, built))
    built
  }

  /** Leaf skills one role requires. Empty when the role declares none or does not exist. */
  def requirements(frameworkId: String, roleId: String, ctx: RequestContext): Set[String] =
    if (StringUtils.isBlank(frameworkId) || StringUtils.isBlank(roleId)) Set.empty
    else meta(frameworkId, ctx).skillsOf(roleId)

  def allRequirements(frameworkId: String, ctx: RequestContext): Map[String, Set[String]] =
    if (StringUtils.isBlank(frameworkId)) Map.empty
    else meta(frameworkId, ctx).roleSkills

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

  /** `skills` tags for the given content ids (courses, question sets, questions). */
  def claimsOf(nodeIds: List[String], ctx: RequestContext): Map[String, List[String]] = {
    if (nodeIds.isEmpty) return Map.empty
    val now = System.currentTimeMillis()
    val wanted = nodeIds.distinct.filter(_.nonEmpty)
    val cached = wanted.flatMap { id =>
      val hit = claimCache.get(id)
      if (hit != null && hit._1 > now) Some(id -> hit._2) else None
    }.toMap
    val missing = wanted.filterNot(cached.contains)
    val fetched =
      if (missing.isEmpty) Map.empty[String, List[String]]
      else parseClaims(searchByIds(missing, List("skills", "primaryCategory")))
    // negative-cache the misses too, so an untagged node is not searched again on every event
    missing.foreach(id => claimCache.put(id, (now + metaTtl, fetched.getOrElse(id, Nil))))
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
    claimsOf(nodeIds, ctx).map { case (node, codes) =>
      val (ok, bad) = codes.partition(m.isLeaf)
      if (bad.nonEmpty)
        logger.warn(s"CompetencyFrameworkUtil: node $node is tagged with non-leaf skills " +
          s"[${bad.mkString(",")}] in ${m.frameworkId}; ignored")
      node -> ok
    }.filter(_._2.nonEmpty)
  }
}
