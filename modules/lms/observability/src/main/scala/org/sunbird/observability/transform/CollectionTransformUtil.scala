package org.sunbird.observability.transform

import com.fasterxml.jackson.databind.ObjectMapper
import org.sunbird.keys.JsonKey
import org.sunbird.learner.util.ContentUtil
import org.sunbird.logging.LoggerUtil
import org.sunbird.request.RequestContext

import scala.collection.JavaConverters._

/**
 * Fetches collection/course details via ContentUtil.searchContent().
 *
 * Key: ContentUtil.searchContent() stores the content list under JsonKey.CONTENTS = "contents"
 * (plural), NOT "content". Verified from ContentUtil.getCourseObjectFromEkStep() which reads
 * result.get(JsonKey.CONTENTS).
 */
class CollectionTransformUtil extends TransformUtil {

  private val logger = new LoggerUtil(classOf[CollectionTransformUtil])
  private val mapper = new ObjectMapper()

  override def fetchDetails(
      ids:     List[String],
      fields:  List[String],
      context: RequestContext
  ): Map[String, Map[String, AnyRef]] = {
    if (ids.isEmpty) return Map.empty

    logger.info(context, s"CollectionTransformUtil: fetching ${ids.size} collection(s): ${ids.mkString(", ")}")

    val identifierJson = mapper.writeValueAsString(ids.asJava)
    val fieldsJson     = mapper.writeValueAsString(fields.asJava)
    val query =
      s"""{"request":{"filters":{"identifier":$identifierJson},"fields":$fieldsJson,"limit":${ids.size}}}"""

    val result: java.util.Map[String, Object] =
      ContentUtil.searchContent(query, ContentUtil.headerMap)

    if (result == null) {
      logger.info(context, "CollectionTransformUtil: searchContent returned null")
      return Map.empty
    }

    // ContentUtil.searchContent() stores the list under JsonKey.CONTENTS = "contents" (plural).
    // Using "content" (singular) would silently return an empty map — confirmed from getCourseObjectFromEkStep.
    val contentList = Option(result.get(JsonKey.CONTENTS))
      .collect { case l: java.util.List[_] => l.asScala.toList }
      .getOrElse(List.empty)

    contentList.flatMap {
      case item: java.util.Map[_, _] =>
        val m = item.asInstanceOf[java.util.Map[String, AnyRef]]
        Option(m.get("identifier").asInstanceOf[String]).map { id =>
          val projected = fields
            .flatMap(f => Option(m.get(f)).map(v => f -> v))
            .toMap
          id -> projected
        }
      case _ => None
    }.toMap
  }
}
