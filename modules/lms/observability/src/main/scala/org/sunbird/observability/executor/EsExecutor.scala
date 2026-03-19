package org.sunbird.observability.executor

import com.fasterxml.jackson.databind.ObjectMapper
import org.sunbird.common.factory.EsClientFactory
import org.sunbird.common.{ElasticSearchHelper, ProjectUtil}
import org.sunbird.dto.SearchDTO
import org.sunbird.keys.JsonKey
import org.sunbird.logging.LoggerUtil

import java.time.LocalDate
import scala.collection.JavaConverters._

/**
 * Executes user creation count reports against the Elasticsearch `user` index.
 *
 * Two modes depending on whether `fromDate`/`toDate` are present in the rendered query:
 *
 *  - **Range mode** (both dates provided): returns a single total count for the date range.
 *    `fromDate` is inclusive, `toDate` is exclusive. Dates must be `yyyy-MM-dd`.
 *
 *  - **Monthly mode** (no dates): returns per-month counts for the last 12 months,
 *    from the start of the month 11 months ago through to the end of the current month.
 *    Runs 12 sequential range queries — one per calendar month.
 *
 * The `createdAt` field in the `user` index must be mapped as `keyword` with a `.raw`
 * sub-field — consistent with all other filterable fields in the index.
 * ElasticSearchHelper.createRangeQuery appends `.raw` to the field name, so the range
 * query hits `createdAt.raw` (keyword), which sorts correctly for `yyyy-MM-dd` strings.
 *
 * The Cassandra `sunbird.user` table must have a `createdat date` column.
 * Both are populated by `UserUtil.setUserDefaultValue()`.
 */
class EsExecutor extends QueryExecutor {

  private val logger    = new LoggerUtil(classOf[EsExecutor])
  private val esService = EsClientFactory.getInstance(JsonKey.REST)
  private val mapper    = new ObjectMapper()

  override def execute(renderedQuery: String, params: List[Any]): List[Map[String, Any]] = {
    val config   = mapper.readValue(renderedQuery, classOf[java.util.Map[String, AnyRef]])
    val fromDate = Option(config.get("fromDate")).map(_.toString).filter(_.nonEmpty)
    val toDate   = Option(config.get("toDate")).map(_.toString).filter(_.nonEmpty)

    (fromDate, toDate) match {
      case (Some(from), Some(to)) =>
        logger.info(s"EsExecutor: range count from=$from to=$to")
        rangeCount(from, to)
      case _ =>
        logger.info(s"EsExecutor: no dates provided — computing last 12 months")
        monthlyBreakdown()
    }
  }

  /**
   * Returns a single-element list: [{ "userCount": N }]
   * `fromDate` inclusive, `toDate` exclusive — both as `yyyy-MM-dd` strings.
   */
  private def rangeCount(fromDate: String, toDate: String): List[Map[String, Any]] = {
    val rangeFilter = Map(
      ">=" -> fromDate.asInstanceOf[AnyRef],
      "<"  -> toDate.asInstanceOf[AnyRef]
    ).asJava

    val searchDTO = new SearchDTO()
    searchDTO.getAdditionalProperties.put(
      JsonKey.FILTERS,
      Map("createdAt" -> rangeFilter).asJava
    )
    searchDTO.setLimit(0) // no documents needed — only the count

    val resultF  = esService.search(searchDTO, ProjectUtil.EsType.user.getTypeName(), null)
    val esResult = Option(ElasticSearchHelper.getResponseFromFuture(resultF))
      .map(_.asInstanceOf[java.util.Map[String, AnyRef]])
      .orNull

    val count = Option(esResult).flatMap(r => Option(r.get(JsonKey.COUNT))).map(_.toString.toLong).getOrElse(0L)
    List(Map("userCount" -> count.asInstanceOf[Any]))
  }

  /**
   * Returns 12 rows — one per calendar month — in chronological order:
   * [{ "month": "yyyy-MM", "userCount": N }, ...]
   *
   * Covers the last 12 months: from start of month 11 months ago through end of current month.
   * Runs 12 sequential [[rangeCount]] calls — one per month.
   */
  private def monthlyBreakdown(): List[Map[String, Any]] = {
    val today = LocalDate.now()

    // monthsAgo=11 → oldest month, monthsAgo=0 → current month
    (11 to 0 by -1).map { monthsAgo =>
      val monthStart = today.minusMonths(monthsAgo).withDayOfMonth(1)
      val monthEnd   = monthStart.plusMonths(1)
      val label      = monthStart.toString.substring(0, 7) // "yyyy-MM"

      val count = rangeCount(monthStart.toString, monthEnd.toString)
        .headOption
        .flatMap(_.get("userCount"))
        .map(_.toString.toLong)
        .getOrElse(0L)

      Map(
        "month"     -> label.asInstanceOf[Any],
        "userCount" -> count.asInstanceOf[Any]
      )
    }.toList
  }
}
