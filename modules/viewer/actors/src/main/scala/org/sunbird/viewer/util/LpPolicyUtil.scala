package org.sunbird.viewer.util

import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.commons.lang3.StringUtils
import org.slf4j.LoggerFactory
import org.sunbird.common.ProjectUtil
import org.sunbird.http.HttpUtil
import org.sunbird.request.RequestContext

import java.util
import scala.collection.JavaConverters._

case class NodeMeta(primaryCategory: String, childNodes: List[String])

/**
 * Structural metadata of a Learning Path: its progression policy, the competency framework it
 * declares, and the primaryCategory/childNodes of its nodes.
 *
 * Competency claims are not read here. They come from CompetencyFrameworkUtil, against
 * `competencyFramework` and the `competencies` field, rather than from the taxonomy framework's
 * highest-index category.
 */
case class LpMeta(policy: String, competencyFramework: String, nodes: Map[String, NodeMeta])

object LpPolicyUtil {
  private val mapper = new ObjectMapper()
  private val logger = LoggerFactory.getLogger(classOf[LpPolicyUtil])

  private val PRACTICE_QUESTION_SET: String =
    Option(ProjectUtil.getConfigValue("lp_question_set_primary_category")).map(_.trim).filter(_.nonEmpty)
      .getOrElse("Practice Question Set")
  private val ASSESSMENT_PRIMARY_CATEGORY: String =
    Option(ProjectUtil.getConfigValue("lp_assessment_primary_category")).map(_.trim).filter(_.nonEmpty)
      .getOrElse("Evaluation Course")
  private val COURSE_PRIMARY_CATEGORY: String =
    Option(ProjectUtil.getConfigValue("lp_course_primary_category")).map(_.trim).filter(_.nonEmpty)
      .getOrElse("Course")

  private def result(json: String): util.Map[String, AnyRef] =
    try {
      mapper.readValue(json, classOf[util.Map[String, AnyRef]])
        .getOrDefault("result", new util.HashMap[String, AnyRef]()).asInstanceOf[util.Map[String, AnyRef]]
    } catch {
      case ex: Exception =>
        logger.warn("LpPolicyUtil: failed to parse response body; degrading to empty result", ex)
        new util.HashMap[String, AnyRef]()
    }

  private def rows(res: util.Map[String, AnyRef]): List[util.Map[String, AnyRef]] =
    res.asScala.values.collect { case l: util.List[_] =>
      l.asInstanceOf[util.List[util.Map[String, AnyRef]]].asScala }.flatten.toList

  def parseLpNodes(json: String): Map[String, NodeMeta] =
    rows(result(json)).flatMap { r =>
      Option(r.get("identifier")).map(_.toString).map { id =>
        id -> NodeMeta(
          Option(r.get("primaryCategory")).map(_.toString).getOrElse(""),
          Option(r.get("childNodes")).collect { case l: util.List[_] => l.asScala.map(_.toString).toList }.getOrElse(Nil))
      }
    }.toMap

  def parseField(json: String, id: String, field: String): Option[String] =
    rows(result(json)).find(r => Option(r.get("identifier")).map(_.toString).contains(id))
      .flatMap(r => Option(r.get(field)).map(_.toString)).filter(_.nonEmpty)

  def isAssessment(courseId: String, nodes: Map[String, NodeMeta]): Boolean =
    nodes.get(courseId).exists(_.primaryCategory == ASSESSMENT_PRIMARY_CATEGORY)

  def questionSets(courseId: String, nodes: Map[String, NodeMeta]): List[String] =
    nodes.get(courseId).toList.flatMap(_.childNodes)
      .filter(id => nodes.get(id).exists(_.primaryCategory == PRACTICE_QUESTION_SET))

  /** Every course in the programme, assessment courses included. Ordered for stable output. */
  def courses(nodes: Map[String, NodeMeta]): List[String] =
    nodes.collect { case (id, n)
      if n.primaryCategory == COURSE_PRIMARY_CATEGORY ||
         n.primaryCategory == ASSESSMENT_PRIMARY_CATEGORY => id }.toList.sorted

  /** Every question set in the programme, whichever course holds it. */
  def allQuestionSets(nodes: Map[String, NodeMeta]): List[String] =
    nodes.collect { case (id, n) if n.primaryCategory == PRACTICE_QUESTION_SET => id }.toList.sorted

  /**
   * The children of every question set in the programme.
   *
   * Under-reports where a question set groups its items into sections: the section id resolves no
   * skills, so a skill only measured inside a section reads as unassessed.
   */
  def questions(nodes: Map[String, NodeMeta]): List[String] =
    allQuestionSets(nodes).flatMap(qs => nodes.get(qs).toList.flatMap(_.childNodes)).distinct

  private val metaTtl: Long =
    Option(ProjectUtil.getConfigValue("lp_meta_cache_ttl")).filter(_.trim.nonEmpty).map(_.trim.toLong).getOrElse(3600L) * 1000L
  private val metaCache = new java.util.concurrent.ConcurrentHashMap[String, (Long, LpMeta)]()

  private def cachedMeta(k: String)(load: => LpMeta): LpMeta = {
    val now = System.currentTimeMillis(); val h = metaCache.get(k)
    if (h != null && h._1 > now) h._2 else { val v = load; if (v.nodes.nonEmpty) metaCache.put(k, (now + metaTtl, v)); v }
  }

  def invalidate(rootId: String): Unit =
    if (StringUtils.isBlank(rootId)) metaCache.clear() else metaCache.remove(rootId)

  def apply(): LpPolicyUtil = new LpPolicyUtil()
}

class LpPolicyUtil {
  import LpPolicyUtil._

  private def searchUrl: String = ProjectUtil.getConfigValue("service_search_base_path") + "/v3/search"

  private def post(body: String): String = {
    val headers = new util.HashMap[String, String]() {{ put("Content-Type", "application/json") }}
    try {
      val r = HttpUtil.doPostRequest(searchUrl, body, headers)
      if (r != null && r.getStatusCode == 200 && StringUtils.isNotBlank(r.getBody)) r.getBody
      else {
        logger.warn(s"LpPolicyUtil: search degraded (status=${if (r != null) r.getStatusCode else -1}) url=$searchUrl")
        "{}"
      }
    } catch {
      case ex: Exception =>
        logger.warn(s"LpPolicyUtil: search call failed url=$searchUrl; degrading to empty result", ex)
        "{}"
    }
  }

  private def searchByIds(ids: List[String], fields: List[String]): String = {
    if (ids.isEmpty || fields.isEmpty) return "{}"
    val filters = new util.HashMap[String, AnyRef]() {{ put("status", util.Arrays.asList("Live")); put("identifier", ids.asJava) }}
    val request = new util.HashMap[String, AnyRef]() {{ put("filters", filters); put("fields", fields.asJava) }}
    post(mapper.writeValueAsString(new util.HashMap[String, AnyRef]() {{ put("request", request) }}))
  }

  def lpMeta(rootId: String, ctx: RequestContext): LpMeta = cachedMeta(rootId) {
    val rootJson = searchByIds(List(rootId), List("policy", "competencyFramework", "childNodes"))
    val policy = parseField(rootJson, rootId, "policy").getOrElse("Strict")
    val competencyFramework = parseField(rootJson, rootId, "competencyFramework").getOrElse("")
    val childNodes = parseLpNodes(rootJson).get(rootId).map(_.childNodes).getOrElse(Nil)
    val ids = (rootId :: childNodes).distinct
    LpMeta(policy, competencyFramework, parseLpNodes(searchByIds(ids, List("primaryCategory", "childNodes"))))
  }

  def policyOf(meta: LpMeta): String = meta.policy match {
    case p if p != null && p.equalsIgnoreCase("Adaptive")      => "Adaptive"
    case p if p != null && p.equalsIgnoreCase("PriorLearning") => "PriorLearning"
    case _                                                     => "Strict"
  }

  def isAssessmentCourse(courseId: String, meta: LpMeta): Boolean = isAssessment(courseId, meta.nodes)

  def questionSetsOf(courseId: String, meta: LpMeta): List[String] = questionSets(courseId, meta.nodes)

  /** Assessment flag per course. Competency claims come from CompetencyFrameworkUtil. */
  def assessmentFlags(courseIds: List[String], meta: LpMeta): Map[String, Boolean] =
    courseIds.map(c => c -> isAssessment(c, meta.nodes)).toMap

  def coursesOf(meta: LpMeta): List[String] = courses(meta.nodes)

  def allQuestionSetsOf(meta: LpMeta): List[String] = allQuestionSets(meta.nodes)

  def questionsIn(meta: LpMeta): List[String] = questions(meta.nodes)
}
