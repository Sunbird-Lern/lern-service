package org.sunbird.observability.executor

import com.fasterxml.jackson.databind.ObjectMapper
import org.elasticsearch.action.search.SearchRequest
import org.elasticsearch.client.RequestOptions
import org.elasticsearch.index.query.QueryBuilders
import org.elasticsearch.search.aggregations.AggregationBuilders
import org.elasticsearch.search.aggregations.bucket.histogram.{DateHistogramInterval, LongBounds, ParsedDateHistogram}
import org.elasticsearch.search.builder.SearchSourceBuilder
import org.sunbird.common.ProjectUtil
import org.sunbird.exception.ProjectCommonException
import org.sunbird.helper.ConnectionManager
import org.sunbird.logging.LoggerUtil
import org.sunbird.message.ResponseCode
import org.sunbird.request.RequestContext

import java.time.{LocalDate, ZoneOffset}
import scala.collection.JavaConverters._

/**
 * Executes user creation count reports against the Elasticsearch `user` index.
 *
 * Two modes depending on whether `fromDate`/`toDate` are present in the rendered query:
 *
 *  - **Range mode** (both dates provided): returns a single total count for the date range.
 *    `fromDate` is inclusive, `toDate` is exclusive. Dates must be `yyyy-MM-dd`.
 *
 *  - **Monthly mode** (no dates): returns per-month counts for the last 12 months via a
 *    single `date_histogram` aggregation, bucketed by calendar month.
 *
 * The `createdAt` field in the `user` index must be mapped as `date` with `format: yyyy-MM-dd`.
 * Range queries and `date_histogram` aggregations work natively on this type — no `.raw`
 * sub-field is needed.
 *
 * The Cassandra `sunbird.user` table must have a `createdat text` column.
 * Both are populated by `UserUtil.setUserDefaultValue()`.
 */
class EsExecutor extends QueryExecutor {

  private val logger = new LoggerUtil(classOf[EsExecutor])
  private val mapper = new ObjectMapper()

  override def execute(renderedQuery: String, params: List[Any], requestContext: RequestContext): List[Map[String, Any]] = {
    val config   = mapper.readValue(renderedQuery, classOf[java.util.Map[String, AnyRef]])
    val fromDate = Option(config.get("fromDate")).map(_.toString).filter(_.nonEmpty)
    val toDate   = Option(config.get("toDate")).map(_.toString).filter(_.nonEmpty)

    (fromDate, toDate) match {
      case (Some(from), Some(to)) =>
        validateDateFormat(from, "fromDate")
        validateDateFormat(to, "toDate")
        logger.info(requestContext, s"EsExecutor: range count from=$from to=$to")
        List(Map[String, Any]("userCount" -> rangeCount(from, to, requestContext)))
      case (None, None) =>
        logger.info(requestContext, "EsExecutor: no dates provided — computing last 12 months")
        monthlyBreakdown(requestContext)
      case (Some(_), None) =>
        throw new ProjectCommonException(
          ResponseCode.invalidRequestData.getErrorCode,
          "toDate is required when fromDate is provided",
          ResponseCode.CLIENT_ERROR.getResponseCode
        )
      case (None, Some(_)) =>
        throw new ProjectCommonException(
          ResponseCode.invalidRequestData.getErrorCode,
          "fromDate is required when toDate is provided",
          ResponseCode.CLIENT_ERROR.getResponseCode
        )
    }
  }

  private def rangeCount(fromDate: String, toDate: String, ctx: RequestContext): Long = {
    val query         = QueryBuilders.rangeQuery("createdAt").gte(fromDate).lt(toDate)
    val sourceBuilder = new SearchSourceBuilder().query(query).size(0)
    val request       = new SearchRequest(ProjectUtil.EsType.user.getTypeName()).source(sourceBuilder)
    logger.info(ctx, s"EsExecutor.rangeCount: from=$fromDate to=$toDate")
    val response = ConnectionManager.getRestClient().search(request, RequestOptions.DEFAULT)
    response.getHits.getTotalHits.value
  }

  private def validateDateFormat(date: String, fieldName: String): Unit = {
    if (!date.matches("""\d{4}-\d{2}-\d{2}"""))
      throw new ProjectCommonException(
        ResponseCode.invalidRequestData.getErrorCode,
        s"$fieldName must be in yyyy-MM-dd format, got: '$date'",
        ResponseCode.CLIENT_ERROR.getResponseCode
      )
  }

  private def monthlyBreakdown(ctx: RequestContext): List[Map[String, Any]] = {
    val today = LocalDate.now(ZoneOffset.UTC)
    val from  = today.minusMonths(11).withDayOfMonth(1)
    val to    = today.plusMonths(1).withDayOfMonth(1) // exclusive upper bound

    val agg = AggregationBuilders.dateHistogram("users_per_month")
      .field("createdAt")
      .calendarInterval(DateHistogramInterval.MONTH)
      .format("yyyy-MM")
      .minDocCount(0)
      .extendedBounds(new LongBounds(from.toString.substring(0, 7), today.toString.substring(0, 7)))

    val query         = QueryBuilders.rangeQuery("createdAt").gte(from.toString).lt(to.toString)
    val sourceBuilder = new SearchSourceBuilder().query(query).aggregation(agg).size(0)
    val request       = new SearchRequest(ProjectUtil.EsType.user.getTypeName()).source(sourceBuilder)

    logger.info(ctx, s"EsExecutor.monthlyBreakdown: from=$from to=$to")
    val response = ConnectionManager.getRestClient().search(request, RequestOptions.DEFAULT)

    val aggs = response.getAggregations
    if (aggs == null) {
      logger.info(ctx, "EsExecutor.monthlyBreakdown: no aggregations in response — returning empty")
      return List.empty
    }

    val histogram = aggs.get[ParsedDateHistogram]("users_per_month")
    if (histogram == null) {
      logger.info(ctx, "EsExecutor.monthlyBreakdown: histogram not found in response — returning empty")
      return List.empty
    }

    histogram.getBuckets.asScala.map { bucket =>
      Map[String, Any]("month" -> bucket.getKeyAsString, "userCount" -> bucket.getDocCount)
    }.toList
  }
}
