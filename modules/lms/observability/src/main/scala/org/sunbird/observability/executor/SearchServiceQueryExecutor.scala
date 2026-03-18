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
      val body = if (response != null && response.getBody != null) response.getBody else "(no body)"
      logger.error(s"SearchServiceQueryExecutor: Search Service returned HTTP $code — body: $body", null)
      return List.empty
    }

    extractResults(response.getBody)
  }

  private def extractResults(responseBody: String): List[Map[String, Any]] = {
    try {
      val root = mapper.readValue(responseBody, mapType)
      val result = root.get("result") match {
        case m: java.util.Map[_, _] => m.asInstanceOf[java.util.Map[String, AnyRef]]
        case _                      => return List.empty
      }

      // Facets response: flatten each facet bucket into {facet, <facetName>, count} rows.
      // The value is keyed by the facet name itself (e.g. "createdBy", "status") so that
      // the standard transform mechanism can resolve IDs to details using transform: ["createdBy"].
      Option(result.get("facets")).collect { case list: java.util.List[_] => list } match {
        case Some(facetList) =>
          return facetList.asInstanceOf[java.util.List[java.util.Map[String, AnyRef]]].asScala.flatMap { facet =>
            // Skip facets with missing name key (safe NPE guard)
            Option(facet.get("name")).map(_.toString) match {
              case Some(facetName) =>
                Option(facet.get("values")).collect { case list: java.util.List[_] => list } match {
                  case Some(valueList) =>
                    valueList.asInstanceOf[java.util.List[java.util.Map[String, AnyRef]]].asScala.flatMap { v =>
                      // Skip values with missing name or count key (safe NPE guard)
                      (Option(v.get("name")), Option(v.get("count"))) match {
                        case (Some(name), Some(count)) =>
                          List(Map(
                            "facet"   -> facetName.asInstanceOf[Any],
                            facetName -> name.asInstanceOf[Any],
                            "count"   -> count.asInstanceOf[Any]
                          ))
                        case _ => List.empty
                      }
                    }
                  case None => List.empty
                }
              case None => List.empty
            }
          }.toList
        case None => // fall through to content/data/response extraction
      }

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
