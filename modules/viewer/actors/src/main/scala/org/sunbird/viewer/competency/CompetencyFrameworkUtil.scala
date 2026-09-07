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
 * Resolves a competency framework into the vocabulary the engine needs, and resolves the
 * `competencies` tags off content.
 *
 * This replaces LpPolicyUtil's "highest-index taxonomy category is the skill field" heuristic.
 * Unlike that heuristic, an unresolvable framework is not silently treated as "no competencies":
 * callers get CompetencyMeta.empty and are expected to surface it, and batch creation rejects it.
 */
object CompetencyFrameworkUtil {

  private val mapper = new ObjectMapper()
  private val logger = LoggerFactory.getLogger(classOf[CompetencyFrameworkUtil])

  val CAT_AREA = "competencyarea"
  val CAT_COMPETENCY = "competency"
  val CAT_LEVEL = "proficiencylevel"
  val CAT_POSITION = "position"
  val CAT_REQUIREMENT = "competencyrequirement"
  // The authoring sheets name this category `requirement` (a requirement belongs to the
  // POSITION, not to the competency). Frameworks created before the rename still carry the
  // old spelling, so both resolve -- otherwise a framework built from the current sheets
  // yields no requirement terms at all, and every learner reads as 100% ready.
  val CAT_REQUIREMENT_ALIASES = List(CAT_REQUIREMENT, "requirement")

  private def cfg(key: String, default: String): String =
    Option(ProjectUtil.getConfigValue(key)).map(_.trim).filter(_.nonEmpty).getOrElse(default)

  private def cfgLong(key: String, default: Long): Long =
    Option(ProjectUtil.getConfigValue(key)).map(_.trim).filter(_.nonEmpty).map(_.toLong).getOrElse(default)

  private val metaTtl: Long = cfgLong("competency_meta_cache_ttl", 3600L) * 1000L
  private val metaCache = new java.util.concurrent.ConcurrentHashMap[String, (Long, CompetencyMeta)]()
  private val reqCache = new java.util.concurrent.ConcurrentHashMap[String, (Long, Map[String, List[RequirementDef]])]()
  // node id -> its competencies claims. Keeps transition-time crediting off the search service.
  private val claimCache = new java.util.concurrent.ConcurrentHashMap[String, (Long, List[CompetencyClaim])]()
  // collection id -> the competencyFramework it declares
  private val fwOfCollection = new java.util.concurrent.ConcurrentHashMap[String, (Long, String)]()

  def invalidate(frameworkId: String): Unit =
    if (StringUtils.isBlank(frameworkId)) {
      metaCache.clear(); reqCache.clear(); claimCache.clear(); fwOfCollection.clear()
    } else {
      metaCache.remove(frameworkId); reqCache.remove(frameworkId)
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

  private def str(m: util.Map[String, AnyRef], k: String): Option[String] =
    Option(m.get(k)).map(_.toString).filter(_.nonEmpty)

  private def num(m: util.Map[String, AnyRef], k: String): Option[Double] =
    Option(m.get(k)).flatMap {
      case n: Number => Some(n.doubleValue())
      case s: String => scala.util.Try(s.trim.toDouble).toOption
      case _ => None
    }

  /** Terms of one framework category, by category `code`. */
  private[competency] def termsOf(frameworkJson: String, categoryCode: String): List[util.Map[String, AnyRef]] = {
    val fw = result(frameworkJson).getOrDefault("framework", new util.HashMap[String, AnyRef]())
      .asInstanceOf[util.Map[String, AnyRef]]
    asList(fw.get("categories"))
      .find(c => str(c, "code").exists(_.equalsIgnoreCase(categoryCode)))
      .map(c => asList(c.get("terms")))
      .getOrElse(Nil)
  }

  private[competency] def frameworkField(frameworkJson: String, field: String): Option[String] = {
    val fw = result(frameworkJson).getOrDefault("framework", new util.HashMap[String, AnyRef]())
      .asInstanceOf[util.Map[String, AnyRef]]
    str(fw, field)
  }

  /** Proficiency scale, ordered ascending by index. */
  private[competency] def parseLevels(frameworkJson: String): List[LevelDef] =
    termsOf(frameworkJson, CAT_LEVEL).flatMap { t =>
      str(t, "code").map { code =>
        LevelDef(
          code = code,
          index = num(t, "index").map(_.toInt).getOrElse(0),
          cutScore = num(t, "cutScore").getOrElse(0d),
          minEvidenceCount = num(t, "minEvidenceCount").map(_.toInt).getOrElse(0),
          validityMonths = num(t, "validityMonths").map(_.toInt).filter(_ > 0))
      }
    }.sortBy(_.index)

  /** Per-competency validity, which overrides the level default when set. */
  private[competency] def parseValidity(frameworkJson: String): Map[String, Int] =
    termsOf(frameworkJson, CAT_COMPETENCY).flatMap { t =>
      for { code <- str(t, "code"); v <- num(t, "validityMonths").map(_.toInt) if v > 0 } yield code -> v
    }.toMap

  /** A term's `associations`, grouped by the associated term's category code. */
  private[competency] def associationsByCategory(term: util.Map[String, AnyRef]): Map[String, List[String]] =
    asList(term.get("associations"))
      .flatMap(a => for { cat <- str(a, "category"); code <- str(a, "code").orElse(str(a, "identifier")) }
        yield cat.toLowerCase -> code)
      .groupBy(_._1).map { case (k, v) => k -> v.map(_._2) }

  /**
   * Requirement edges per position.
   *
   * Full variant: each `competencyrequirement` term carries one association to each of position,
   * competency and proficiencylevel, plus a `criticality`.
   * Small variant: no requirement terms, so each position term's own associations to competency
   * terms are the required set, at the framework's `defaultRequiredLevel`.
   */
  private[competency] def parseRequirements(frameworkJson: String,
                                            defaultLevel: String,
                                            defaultLevelIndex: Int,
                                            levelIndexOf: String => Int): Map[String, List[RequirementDef]] = {
    val reqTerms = CAT_REQUIREMENT_ALIASES.iterator
      .map(termsOf(frameworkJson, _)).find(_.nonEmpty).getOrElse(Nil)
    val parsed = reqTerms.map { t =>
      val assoc = associationsByCategory(t)
      val criticality = str(t, "criticality").getOrElse(Criticality.MANDATORY)
      val edge = for {
        pos <- assoc.getOrElse(CAT_POSITION, Nil).headOption
        comp <- assoc.getOrElse(CAT_COMPETENCY, Nil).headOption
      } yield {
        val lvl = assoc.getOrElse(CAT_LEVEL, Nil).headOption.getOrElse(defaultLevel)
        pos -> RequirementDef(comp, lvl, levelIndexOf(lvl), criticality)
      }
      (t, edge)
    }
    val edges = parsed.flatMap(_._2)
    // A requirement term needs one association to a position AND one to a competency to mean
    // anything. Dropping the half-linked ones silently made a mis-authored framework look like a
    // smaller-but-valid one: the position simply reported fewer requirements, and a position whose
    // edges all failed reported 100% ready (see readiness's empty-mandatory case).
    val dropped = parsed.collect { case (t, None) => str(t, "code").getOrElse("?") }
    if (dropped.nonEmpty)
      logger.warn(s"CompetencyFrameworkUtil: ${dropped.size} of ${reqTerms.size} requirement terms " +
        s"lack a position and/or competency association and were ignored: ${dropped.mkString(", ")}")
    if (edges.nonEmpty) edges.groupBy(_._1).map { case (k, v) => k -> v.map(_._2) }
    else
      termsOf(frameworkJson, CAT_POSITION).flatMap { t =>
        str(t, "code").map { pos =>
          pos -> associationsByCategory(t).getOrElse(CAT_COMPETENCY, Nil)
            .map(c => RequirementDef(c, defaultLevel, defaultLevelIndex, Criticality.MANDATORY))
        }
      }.filter(_._2.nonEmpty).toMap
  }

  /** `competencies: [{code, level}]` off a content/collection/question search row. */
  private[competency] def parseClaims(searchJson: String): Map[String, List[CompetencyClaim]] = {
    val rows = result(searchJson).asScala.values.collect {
      case l: util.List[_] => l.asInstanceOf[util.List[util.Map[String, AnyRef]]].asScala
    }.flatten.toList
    rows.flatMap { r =>
      str(r, "identifier").map { id =>
        id -> asList(r.get("competencies")).flatMap { c =>
          for { code <- str(c, "code") } yield CompetencyClaim(code, str(c, "level").getOrElse(""))
        }
      }
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
    val levels = parseLevels(json)
    if (levels.isEmpty) {
      logger.warn(s"CompetencyFrameworkUtil: framework $frameworkId resolved no proficiencylevel terms")
      return CompetencyMeta.empty
    }
    val byCode = (c: String) => levels.find(_.code.equalsIgnoreCase(c)).map(_.index).getOrElse(0)
    val defaultLevel = frameworkField(json, "defaultRequiredLevel").getOrElse(levels.last.code)
    val capLevel = frameworkField(json, "maxCompletionDerivedLevel").getOrElse("")
    val built = CompetencyMeta(
      frameworkId = frameworkId,
      levels = levels,
      maxCompletionDerivedIndex = if (capLevel.isEmpty) 0 else byCode(capLevel),
      defaultRequiredLevelIndex = byCode(defaultLevel),
      defaultRequiredLevel = defaultLevel,
      validityMonths = parseValidity(json),
      claimsByNode = Map.empty)
    metaCache.put(frameworkId, (now + metaTtl, built))
    reqCache.put(frameworkId, (now + metaTtl,
      parseRequirements(json, defaultLevel, byCode(defaultLevel), byCode)))
    built
  }

  /** Requirement set of one position. Empty when the position declares none. */
  def requirements(frameworkId: String, positionId: String, ctx: RequestContext): List[RequirementDef] = {
    if (StringUtils.isBlank(frameworkId) || StringUtils.isBlank(positionId)) return Nil
    meta(frameworkId, ctx)
    Option(reqCache.get(frameworkId)).map(_._2).getOrElse(Map.empty)
      .getOrElse(positionId, Nil)
  }

  def allRequirements(frameworkId: String, ctx: RequestContext): Map[String, List[RequirementDef]] = {
    if (StringUtils.isBlank(frameworkId)) return Map.empty
    meta(frameworkId, ctx)
    Option(reqCache.get(frameworkId)).map(_._2).getOrElse(Map.empty)
  }

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

  /** `competencies` claims for the given content ids (courses, question sets, questions). */
  def claimsOf(nodeIds: List[String], ctx: RequestContext): Map[String, List[CompetencyClaim]] = {
    if (nodeIds.isEmpty) return Map.empty
    val now = System.currentTimeMillis()
    val wanted = nodeIds.distinct.filter(_.nonEmpty)
    val cached = wanted.flatMap { id =>
      val hit = claimCache.get(id)
      if (hit != null && hit._1 > now) Some(id -> hit._2) else None
    }.toMap
    val missing = wanted.filterNot(cached.contains)
    val fetched =
      if (missing.isEmpty) Map.empty[String, List[CompetencyClaim]]
      else parseClaims(searchByIds(missing, List("competencies", "primaryCategory")))
    // negative-cache the misses too, so an untagged node is not searched again on every event
    missing.foreach(id => claimCache.put(id, (now + metaTtl, fetched.getOrElse(id, Nil))))
    (cached ++ fetched).filter(_._2.nonEmpty)
  }

  /** Validity for a competency at a level: the competency's own override, else the level default. */
  def validityMonthsFor(competencyId: String, level: LevelDef, m: CompetencyMeta): Option[Int] =
    m.validityMonths.get(competencyId).filter(_ > 0).orElse(level.validityMonths)
}
