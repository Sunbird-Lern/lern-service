package org.sunbird.activity.util

import com.fasterxml.jackson.databind.ObjectMapper
import org.sunbird.common.ProjectUtil
import org.sunbird.http.HttpUtil
import org.sunbird.request.RequestContext

import java.util
import scala.collection.JavaConverters._

case class NodeMeta(primaryCategory: String, skills: Set[String], childNodes: List[String])
case class LpMeta(policy: String, framework: String, categoryCode: String, nodes: Map[String, NodeMeta])

object LpPolicyUtil {
  private val mapper = new ObjectMapper()

  private val PRACTICE_QUESTION_SET = "Practice Question Set"
  private val ASSESSMENT_PRIMARY_CATEGORY: String =
    Option(ProjectUtil.getConfigValue("lp_assessment_primary_category")).map(_.trim).filter(_.nonEmpty).getOrElse("Evaluation Course")

  private def result(json: String): util.Map[String, AnyRef] =
    mapper.readValue(json, classOf[util.Map[String, AnyRef]])
      .getOrDefault("result", new util.HashMap[String, AnyRef]()).asInstanceOf[util.Map[String, AnyRef]]

  private def rows(res: util.Map[String, AnyRef]): List[util.Map[String, AnyRef]] =
    res.asScala.values.collect { case l: util.List[_] =>
      l.asInstanceOf[util.List[util.Map[String, AnyRef]]].asScala }.flatten.toList

  private def strs(v: AnyRef): Set[String] = v match {
    case l: util.List[_] => l.asScala.map(_.toString).toSet
    case s: String if s.nonEmpty => Set(s)
    case _ => Set.empty
  }

  def parseFrameworkCategoryCode(json: String): Option[String] = {
    val fw = result(json).getOrDefault("framework", new util.HashMap[String, AnyRef]()).asInstanceOf[util.Map[String, AnyRef]]
    val cats = Option(fw.get("categories")).collect { case l: util.List[_] =>
      l.asInstanceOf[util.List[util.Map[String, AnyRef]]].asScala.toList }.getOrElse(Nil)
    if (cats.isEmpty) None
    else Some(cats.maxBy(c => Option(c.get("index")).map(_.asInstanceOf[Number].doubleValue()).getOrElse(0.0))
      .get("code").toString)
  }

  def parseLpNodes(json: String, categoryCode: String): Map[String, NodeMeta] =
    rows(result(json)).flatMap { r =>
      Option(r.get("identifier")).map(_.toString).map { id =>
        id -> NodeMeta(
          Option(r.get("primaryCategory")).map(_.toString).getOrElse(""),
          Option(if (categoryCode.isEmpty) null else r.get(categoryCode)).map(strs).getOrElse(Set.empty),
          Option(r.get("childNodes")).collect { case l: util.List[_] => l.asScala.map(_.toString).toList }.getOrElse(Nil))
      }
    }.toMap

  def parseField(json: String, id: String, field: String): Option[String] =
    rows(result(json)).find(r => Option(r.get("identifier")).map(_.toString).contains(id))
      .flatMap(r => Option(r.get(field)).map(_.toString))

  def isAssessment(courseId: String, nodes: Map[String, NodeMeta]): Boolean =
    nodes.get(courseId).exists(_.primaryCategory == ASSESSMENT_PRIMARY_CATEGORY)

  def questionSets(courseId: String, nodes: Map[String, NodeMeta]): List[String] =
    nodes.get(courseId).toList.flatMap(_.childNodes).filter(id => nodes.get(id).exists(_.primaryCategory == PRACTICE_QUESTION_SET))

  private val metaTtl: Long =
    Option(ProjectUtil.getConfigValue("lp_meta_cache_ttl")).filter(_.trim.nonEmpty).map(_.trim.toLong).getOrElse(3600L) * 1000L
  private val codeTtl: Long =
    Option(ProjectUtil.getConfigValue("framework_category_cache_ttl")).filter(_.trim.nonEmpty).map(_.trim.toLong).getOrElse(86400L) * 1000L
  private val metaCache = new java.util.concurrent.ConcurrentHashMap[String, (Long, LpMeta)]()
  private val codeCache = new java.util.concurrent.ConcurrentHashMap[String, (Long, String)]()
  private def cachedMeta(k: String)(load: => LpMeta): LpMeta = {
    val now = System.currentTimeMillis(); val h = metaCache.get(k)
    if (h != null && h._1 > now) h._2 else { val v = load; if (v.nodes.nonEmpty) metaCache.put(k, (now + metaTtl, v)); v }
  }
  private def cachedCode(k: String)(load: => Option[String]): Option[String] = {
    val now = System.currentTimeMillis(); val h = codeCache.get(k)
    if (h != null && h._1 > now) Some(h._2) else { val v = load; v.foreach(c => codeCache.put(k, (now + codeTtl, c))); v }
  }

  def apply(): LpPolicyUtil = new LpPolicyUtil()
}

class LpPolicyUtil {
  import LpPolicyUtil._

  private def searchUrl: String = ProjectUtil.getConfigValue("service_search_base_path") + "/v3/search"

  private def post(body: String): String = {
    val r = HttpUtil.doPostRequest(searchUrl, body, new util.HashMap[String, String]())
    if (r != null && r.getStatusCode == 200) r.getBody else "{}"
  }

  private def searchByIds(ids: List[String], fields: List[String]): String = {
    if (ids.isEmpty || fields.isEmpty) return "{}"
    val filters = new util.HashMap[String, AnyRef]() {{ put("status", util.Arrays.asList("Live")); put("identifier", ids.asJava) }}
    val request = new util.HashMap[String, AnyRef]() {{ put("filters", filters); put("fields", fields.asJava) }}
    post(mapper.writeValueAsString(new util.HashMap[String, AnyRef]() {{ put("request", request) }}))
  }

  private def frameworkCategoryCode(frameworkId: String): Option[String] =
    if (frameworkId == null || frameworkId.isEmpty) None
    else cachedCode(frameworkId) {
      val base = ProjectUtil.getConfigValue("content_service_base_url")
      val api = Option(ProjectUtil.getConfigValue("sunbird_framework_read_api")).filter(_.nonEmpty).getOrElse("/v1/framework/read")
      val body = HttpUtil.sendGetRequest(base + api + "/" + frameworkId, new util.HashMap[String, String]())
      parseFrameworkCategoryCode(if (body == null) "{}" else body)
    }

  def lpMeta(rootId: String, ctx: RequestContext): LpMeta = cachedMeta(rootId) {
    val rootJson = searchByIds(List(rootId), List("policy", "framework", "childNodes"))
    val policy = parseField(rootJson, rootId, "policy").getOrElse("Strict")
    val framework = parseField(rootJson, rootId, "framework").getOrElse("")
    val childNodes = parseLpNodes(rootJson, "").get(rootId).map(_.childNodes).getOrElse(Nil)
    val categoryCode = frameworkCategoryCode(framework).getOrElse("")
    val ids = (rootId :: childNodes).distinct
    val fields = if (categoryCode.isEmpty) List("primaryCategory", "childNodes") else List(categoryCode, "primaryCategory", "childNodes")
    LpMeta(policy, framework, categoryCode, parseLpNodes(searchByIds(ids, fields), categoryCode))
  }

  def policyOf(meta: LpMeta): String = meta.policy match {
    case p if p != null && p.equalsIgnoreCase("Adaptive")      => "Adaptive"
    case p if p != null && p.equalsIgnoreCase("PriorLearning") => "PriorLearning"
    case _                                                     => "Strict"
  }
  def isAssessmentCourse(courseId: String, meta: LpMeta): Boolean = isAssessment(courseId, meta.nodes)
  def questionSetsOf(courseId: String, meta: LpMeta): List[String] = questionSets(courseId, meta.nodes)
  def courseMeta(courseIds: List[String], meta: LpMeta): Map[String, (Set[String], Boolean)] =
    courseIds.map(c => c -> (meta.nodes.get(c).map(_.skills).getOrElse(Set.empty), isAssessment(c, meta.nodes))).toMap

  def skillsOfQuestions(questionIds: List[String], meta: LpMeta): Set[String] = {
    if (questionIds.isEmpty || meta.categoryCode.isEmpty) return Set.empty
    parseLpNodes(searchByIds(questionIds, List(meta.categoryCode)), meta.categoryCode).values.flatMap(_.skills).toSet
  }
}
