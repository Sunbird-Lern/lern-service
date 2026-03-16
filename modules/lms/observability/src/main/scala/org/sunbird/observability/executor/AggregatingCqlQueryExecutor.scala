package org.sunbird.observability.executor

import org.sunbird.exception.ProjectCommonException
import org.sunbird.logging.LoggerUtil
import org.sunbird.message.ResponseCode
import org.sunbird.observability.model._

/**
 * Executes a plain YCQL SELECT (no GROUP BY / aggregates) and performs in-memory
 * grouping + aggregation driven by an [[AggregationSpec]].
 *
 * This is the canonical workaround for YCQL's restriction:
 *   "Selecting aggregate together with rows of non-aggregate values is not allowed"
 *
 * Design decisions:
 *  - The raw fetch is delegated to [[YugabyteCqlQueryExecutor]] so all PreparedStatement
 *    caching and session management live in one place.
 *  - groupBy is performed on the values exactly as returned by the driver; null values
 *    are treated as a distinct group key component (consistent with SQL NULL semantics).
 *  - Type-safety: MAX, MIN require numeric or java.util.Date values; SUM, AVG require
 *    numeric values.  A [[ProjectCommonException]] is thrown at runtime if the first
 *    non-null value in the group does not satisfy the constraint.
 *  - The output row order is not guaranteed (Map iteration); callers that need ordering
 *    should sort on a known field after the fact.
 */
class AggregatingCqlQueryExecutor(
    rawExecutor: YugabyteCqlQueryExecutor = new YugabyteCqlQueryExecutor()
) {

  private val logger = new LoggerUtil(classOf[AggregatingCqlQueryExecutor])

  /**
   * Fetches raw rows via [[YugabyteCqlQueryExecutor]] then applies in-memory grouping
   * and aggregation driven by the given [[AggregationSpec]].
   *
   * Execution is two-phase when `spec.preAggregation` is defined:
   *  1. Pre-aggregation: collapse rows to one per inner group (e.g. best attempt per user+content).
   *  2. Main aggregation: group the intermediate rows and compute final field-level aggregates.
   *
   * @param query  Plain SELECT query (no GROUP BY / aggregates in SQL).
   * @param params Positional bind parameters for the query.
   * @param spec   Describes grouping, optional best-row selection, and field-level aggregations.
   * @return       One output row per outer group; empty list if the raw query returns no rows.
   */
  def execute(
      query:  String,
      params: List[Any],
      spec:   AggregationSpec
  ): List[Map[String, Any]] = {

    val rawRows = rawExecutor.execute(query, params)
    logger.info(s"AggregatingCqlQueryExecutor: fetched ${rawRows.size} raw rows, groupBy=${spec.groupBy.mkString(",")}")

    if (rawRows.isEmpty) return List.empty

    // Phase 1 (optional): reduce raw rows before the main aggregation.
    // E.g. pick best attempt per (user_id, content_id) before summing per user_id.
    val inputRows: List[Map[String, Any]] = spec.preAggregation match {
      case Some(pre) =>
        val preRows = applyGrouping(rawRows, pre.groupBy, pre.selectBy, pre.aggregations)
        logger.info(s"AggregatingCqlQueryExecutor: pre-aggregation produced ${preRows.size} intermediate rows")
        preRows
      case None => rawRows
    }

    applyGrouping(inputRows, spec.groupBy, spec.selectBy, spec.aggregations)
  }

  private def applyGrouping(
      rows:         List[Map[String, Any]],
      groupBy:      List[String],
      selectBy:     Option[SelectBy],
      aggregations: List[AggregationDef]
  ): List[Map[String, Any]] = {
    val groups: Map[List[Any], List[Map[String, Any]]] =
      rows.groupBy(row => groupBy.map(f => row.getOrElse(f, null)))

    groups.map { case (groupKey, groupRows) =>
      val groupByFields: Map[String, Any] =
        groupBy.zip(groupKey).map { case (field, value) => field -> value }.toMap

      val rowBase: Map[String, Any] = selectBy match {
        case Some(sel) => selectBestRow(sel, groupRows)
        case None      => groupByFields
      }

      val aggregated: Map[String, Any] =
        aggregations.map { agg => agg.outputField -> applyAgg(agg, groupRows) }.toMap

      rowBase ++ aggregated
    }.toList
  }

  /**
   * Picks the single row from the group where `sel.field` is MAX or MIN,
   * then returns all its columns as the output row base.
   * Falls back to the first row if no non-null values exist for the rank field.
   */
  private def selectBestRow(sel: SelectBy, rows: List[Map[String, Any]]): Map[String, Any] = {
    val candidates = rows.filter(r => r.get(sel.field).exists(_ != null))
    if (candidates.isEmpty) return rows.head

    requireOrderable(sel.field, candidates.head(sel.field), s"selectBy(${sel.order})")

    sel.order.toUpperCase match {
      case "MIN" => candidates.minBy(r => toComparable(r(sel.field)))
      case "MAX" => candidates.maxBy(r => toComparable(r(sel.field)))
      case other =>
        throw new ProjectCommonException(
          ResponseCode.serverError.getErrorCode,
          s"SelectBy.order must be MAX or MIN, got: '$other'",
          ResponseCode.SERVER_ERROR.getResponseCode
        )
    }
  }

  // ---------------------------------------------------------------------------
  // Aggregation logic
  // ---------------------------------------------------------------------------

  private def applyAgg(agg: AggregationDef, rows: List[Map[String, Any]]): Any = agg match {

    case a: CountAgg =>
      rows.count(row => row.get(a.sourceField).exists(_ != null)).toLong

    case a: CountAllAgg =>
      rows.size.toLong

    case a: MaxAgg =>
      val values = nonNullValues(a.sourceField, rows)
      if (values.isEmpty) null
      else {
        requireOrderable(a.sourceField, values.head, "MAX")
        values.maxBy(toComparable)
      }

    case a: MinAgg =>
      val values = nonNullValues(a.sourceField, rows)
      if (values.isEmpty) null
      else {
        requireOrderable(a.sourceField, values.head, "MIN")
        values.minBy(toComparable)
      }

    case a: SumAgg =>
      val values = nonNullValues(a.sourceField, rows)
      if (values.isEmpty) null
      else {
        requireNumeric(a.sourceField, values.head, "SUM")
        val sum = values.foldLeft(BigDecimal(0)) { (acc, v) => acc + toBigDecimal(v) }
        normalizeNumeric(sum, values.head)
      }

    case a: AvgAgg =>
      val values = nonNullValues(a.sourceField, rows)
      if (values.isEmpty) null
      else {
        requireNumeric(a.sourceField, values.head, "AVG")
        val sum = values.foldLeft(BigDecimal(0)) { (acc, v) => acc + toBigDecimal(v) }
        (sum / values.size).toDouble.asInstanceOf[Any]
      }

    case a: FirstAgg =>
      rows.flatMap(row => row.get(a.sourceField).filter(_ != null)).headOption.orNull

    case a: LastAgg =>
      rows.flatMap(row => row.get(a.sourceField).filter(_ != null)).lastOption.orNull
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  private def nonNullValues(field: String, rows: List[Map[String, Any]]): List[Any] =
    rows.flatMap(row => row.get(field).filter(_ != null))

  private def requireOrderable(field: String, sample: Any, aggType: String): Unit =
    if (!isOrderable(sample))
      throw new ProjectCommonException(
        ResponseCode.serverError.getErrorCode,
        s"Aggregation $aggType on field '$field' requires a numeric or Date value; got ${sample.getClass.getSimpleName}",
        ResponseCode.SERVER_ERROR.getResponseCode
      )

  private def requireNumeric(field: String, sample: Any, aggType: String): Unit =
    if (!isNumeric(sample))
      throw new ProjectCommonException(
        ResponseCode.serverError.getErrorCode,
        s"Aggregation $aggType on field '$field' requires a numeric value; got ${sample.getClass.getSimpleName}",
        ResponseCode.SERVER_ERROR.getResponseCode
      )

  private def isNumeric(v: Any): Boolean = v.isInstanceOf[java.lang.Number]

  private def isOrderable(v: Any): Boolean =
    v.isInstanceOf[java.lang.Number] || v.isInstanceOf[java.util.Date]

  private def toBigDecimal(v: Any): BigDecimal = v match {
    case n: java.lang.Number => BigDecimal(n.doubleValue())
    case _                   => BigDecimal(0)
  }

  /** Return the same numeric type as the input value to preserve column semantics. */
  private def normalizeNumeric(result: BigDecimal, sample: Any): Any = sample match {
    case _: java.lang.Integer => result.toInt.asInstanceOf[Any]
    case _: java.lang.Long    => result.toLong.asInstanceOf[Any]
    case _: java.lang.Float   => result.toFloat.asInstanceOf[Any]
    case _                    => result.toDouble.asInstanceOf[Any]
  }

  /**
   * Convert to a Double for use in maxBy/minBy comparisons.
   * Throws rather than silently returning 0.0 for unsupported types — a silent 0.0
   * would produce meaningless MAX/MIN results with no indication of the misconfiguration.
   */
  private def toComparable(v: Any): Double = v match {
    case n: java.lang.Number => n.doubleValue()
    case d: java.util.Date   => d.getTime.toDouble
    case _ =>
      throw new ProjectCommonException(
        ResponseCode.serverError.getErrorCode,
        s"Cannot compare value of type ${v.getClass.getSimpleName} — expected numeric or java.util.Date",
        ResponseCode.SERVER_ERROR.getResponseCode
      )
  }
}
