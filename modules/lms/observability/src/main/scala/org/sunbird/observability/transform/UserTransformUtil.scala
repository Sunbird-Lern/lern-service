package org.sunbird.observability.transform

import org.apache.commons.collections.MapUtils
import org.sunbird.common.factory.EsClientFactory
import org.sunbird.common.{ElasticSearchHelper, ProjectUtil}
import org.sunbird.dto.SearchDTO
import org.sunbird.keys.JsonKey
import org.sunbird.logging.LoggerUtil
import org.sunbird.request.RequestContext

import scala.collection.JavaConverters._

/**
 * Fetches user details from the Elasticsearch user index in a single batched query.
 *
 * Why ES instead of HTTP to UserOrg service:
 *   - One query for N users (vs N parallel HTTP calls with getUsersByIds).
 *   - No x-authenticated-user-token required — ES is an internal data store.
 *   - EsClientFactory is already initialised by sunbird-platform-common at startup.
 *   - Fields not present in the ES document are silently omitted (safe default).
 */
class UserTransformUtil extends TransformUtil {

  private val logger    = new LoggerUtil(classOf[UserTransformUtil])
  private val esService = EsClientFactory.getInstance(JsonKey.REST)

  override def fetchDetails(
      ids:     List[String],
      fields:  List[String],
      context: RequestContext
  ): Map[String, Map[String, AnyRef]] = {
    if (ids.isEmpty) return Map.empty

    // Log count only — user IDs are PII and must not appear in INFO-level logs.
    logger.info(context, s"UserTransformUtil: ES lookup for ${ids.size} user(s)")

    val searchDTO = new SearchDTO()
    // ElasticSearchHelper.getTermQueryFromList calls stringList.replaceAll(String::toLowerCase)
    // which mutates the list in place. ids.asJava returns a read-only Scala wrapper (AbstractList
    // with no set() support), so replaceAll throws UnsupportedOperationException.
    // Wrapping in new ArrayList produces a fully mutable copy that ES helper can modify safely.
    searchDTO.getAdditionalProperties.put(
      JsonKey.FILTERS,
      Map(JsonKey.ID -> new java.util.ArrayList[String](ids.asJava)).asJava
    )
    searchDTO.setLimit(ids.size)

    val resultF  = esService.search(searchDTO, ProjectUtil.EsType.user.getTypeName(), context)
    val esResult = ElasticSearchHelper.getResponseFromFuture(resultF)
      .asInstanceOf[java.util.Map[String, AnyRef]]

    if (!MapUtils.isNotEmpty(esResult)) {
      logger.info(context, "UserTransformUtil: ES returned empty result")
      return Map.empty
    }

    val users = Option(esResult.get(JsonKey.CONTENT))
      .collect { case l: java.util.List[_] => l.asScala.toList }
      .getOrElse(List.empty)

    users.flatMap {
      case user: java.util.Map[_, _] =>
        val m = user.asInstanceOf[java.util.Map[String, AnyRef]]
        Option(m.get(JsonKey.ID).asInstanceOf[String]).map { uid =>
          val projected = fields
            .flatMap(f => Option(m.get(f)).map(v => f -> v))
            .toMap
          uid -> projected
        }
      case _ => None
    }.toMap
  }
}
