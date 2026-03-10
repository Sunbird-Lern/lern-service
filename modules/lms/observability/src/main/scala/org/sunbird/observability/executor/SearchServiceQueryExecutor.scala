package org.sunbird.observability.executor

import com.fasterxml.jackson.core.`type`.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import org.sunbird.common.ProjectUtil
import org.sunbird.http.HttpUtil
import org.sunbird.logging.LoggerUtil

import java.util
import scala.collection.JavaConverters._

/**
 * Executes ES-backed reports by calling the Search Service /v3/search endpoint.
 * Follows the same pattern as ContentSearchUtil in activity-aggregator.
 */
class SearchServiceQueryExecutor extends QueryExecutor {

  private val logger   = new LoggerUtil(classOf[SearchServiceQueryExecutor])
  private val mapper   = new ObjectMapper()
  private val listType = new TypeReference[java.util.List[java.util.Map[String, AnyRef]]]() {}
  private val mapType  = new TypeReference[java.util.Map[String, AnyRef]]() {}

  override def execute(renderedQuery: String, params: List[Any]): List[Map[String, Any]] = {
    val searchBasePath = ProjectUtil.getConfigValue("service_search_base_path")
    val url = searchBasePath + "/v3/search"

    val headers = new util.HashMap[String, String]()
    headers.put("Content-Type", "application/json")
    headers.put("Accept", "application/json")
    val authToken = ProjectUtil.getConfigValue("sunbird_api_auth_token")
    if (authToken != null && authToken.nonEmpty) headers.put("Authorization", "Bearer " + authToken)

    logger.info(s"SearchServiceQueryExecutor: POST $url")
    val response =
      try { HttpUtil.doPostRequest(url, renderedQuery, headers) }
      catch { case ex: Exception =>
        logger.error("SearchServiceQueryExecutor: HTTP request failed", ex)
        return List.empty
      }

    if (response == null || response.getStatusCode != 200) {
      val code = if (response != null) response.getStatusCode else -1
      logger.error(s"SearchServiceQueryExecutor: Search Service returned HTTP $code", null)
      return List.empty
    }

    extractResults(response.getBody)
  }

  private def extractResults(responseBody: String): List[Map[String, Any]] = {
    try {
      val root = mapper.readValue(responseBody, mapType)
      val result = root.get("result").asInstanceOf[java.util.Map[String, AnyRef]]
      if (result == null) return List.empty

      // Try "content", "data", "response" keys in order
      val dataKey = Seq("content", "data", "response").find(k => result.containsKey(k)).getOrElse("")
      val rawList = result.get(dataKey)

      rawList match {
        case list: java.util.List[_] =>
          list.asScala.map {
            case m: java.util.Map[_, _] => m.asScala.map { case (k, v) => k.toString -> v.asInstanceOf[Any] }.toMap
            case other => Map("value" -> other.asInstanceOf[Any])
          }.toList
        case _ => List.empty
      }
    } catch {
      case ex: Exception =>
        logger.error("SearchServiceQueryExecutor: Failed to parse response", ex)
        List.empty
    }
  }
}
