package org.sunbird.observability.model

import com.fasterxml.jackson.annotation.{JsonSubTypes, JsonTypeInfo}

/**
 * Describes how raw YCQL rows should be grouped and aggregated in memory.
 *
 * Stored as JSONB in standard_reports_meta.aggregation_spec.
 *
 * --- Mode 1: field-level aggregations (aggregations list) ---
 * Each field in the output row is computed independently.
 * {
 *   "groupBy": ["user_id"],
 *   "aggregations": [
 *     {"type": "COUNT_ALL", "sourceField": "attempt_id",        "outputField": "attempt_count"},
 *     {"type": "MAX",       "sourceField": "total_score",       "outputField": "max_score"},
 *     {"type": "LAST",      "sourceField": "last_attempted_on", "outputField": "last_attempted_on"}
 *   ]
 * }
 *
 * --- Mode 2: best-row selection (selectBy) ---
 * From each group, pick the single row where `selectBy.field` is MAX or MIN,
 * and emit ALL columns of that row. Use this when you need a complete snapshot
 * of the best attempt rather than independently computed per-field aggregates.
 * Additional entries in `aggregations` are merged on top of the selected row.
 * {
 *   "groupBy": ["content_id"],
 *   "selectBy": {"field": "total_score", "order": "MAX"},
 *   "aggregations": [
 *     {"type": "COUNT_ALL", "sourceField": "attempt_id", "outputField": "attempt_count"}
 *   ]
 * }
 *
 * Aggregation type semantics:
 *  COUNT       — count of non-null values for sourceField in the group
 *  COUNT_ALL   — count of all rows in the group (sourceField is ignored)
 *  COUNT_IF    — count rows satisfying a condition (matchValue or nonEmpty)
 *  MAX / MIN   — maximum / minimum value; sourceField must be numeric or java.util.Date
 *  SUM / AVG   — sum / average; sourceField must be numeric (java.lang.Number subtypes)
 *  FIRST       — first non-null value encountered in the group (insertion order)
 *  LAST        — last non-null value encountered in the group (insertion order)
 */
case class AggregationSpec(
    groupBy:        List[String],
    aggregations:   List[AggregationDef]        = List.empty,
    selectBy:       Option[SelectBy]            = None,
    preAggregation: Option[PreAggregationSpec]  = None
)

/**
 * Selects the single row from a group where `field` is maximized (order=MAX)
 * or minimized (order=MIN). All columns of that row are emitted as output.
 *
 * @param field The column to rank rows by (must be numeric or java.util.Date).
 * @param order "MAX" (default) or "MIN".
 */
case class SelectBy(field: String, order: String = "MAX")

/**
 * Optional first-pass aggregation run before the main aggregation.
 *
 * Use when the final aggregation needs to operate on already-reduced rows.
 * Example: to sum the best score per content per user (two-phase):
 *   preAggregation groups by (user_id, content_id) and picks best attempt row,
 *   then the main aggregation groups by user_id and sums total_score.
 *
 * @param groupBy      Inner group-by fields.
 * @param selectBy     Pick the best row per inner group (e.g. MAX total_score).
 * @param aggregations Additional per-field aggregations on the inner group.
 */
case class PreAggregationSpec(
    groupBy:      List[String],
    selectBy:     Option[SelectBy]        = None,
    aggregations: List[AggregationDef]    = List.empty
)

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes(Array(
  new JsonSubTypes.Type(value = classOf[CountAgg],        name = "COUNT"),
  new JsonSubTypes.Type(value = classOf[CountAllAgg],     name = "COUNT_ALL"),
  new JsonSubTypes.Type(value = classOf[CountIfAgg],      name = "COUNT_IF"),
  new JsonSubTypes.Type(value = classOf[MaxAgg],          name = "MAX"),
  new JsonSubTypes.Type(value = classOf[MinAgg],          name = "MIN"),
  new JsonSubTypes.Type(value = classOf[SumAgg],          name = "SUM"),
  new JsonSubTypes.Type(value = classOf[AvgAgg],          name = "AVG"),
  new JsonSubTypes.Type(value = classOf[FirstAgg],        name = "FIRST"),
  new JsonSubTypes.Type(value = classOf[LastAgg],         name = "LAST")
))
sealed trait AggregationDef {
  def sourceField: String
  def outputField: String
}

/** Count non-null values of sourceField within the group. */
case class CountAgg(sourceField: String, outputField: String)    extends AggregationDef

/**
 * Count all rows in the group.
 * `sourceField` is semantically unused — COUNT_ALL counts rows, not field values.
 * It is retained in the model for JSON schema consistency (all AggregationDef subtypes
 * have sourceField) and to keep existing DB records valid without a migration.
 * Callers should pass an empty string or any placeholder value.
 */
case class CountAllAgg(sourceField: String, outputField: String) extends AggregationDef

/**
 * Count rows in the group where `sourceField` satisfies a condition.
 * Exactly one of `matchValue` or `nonEmpty` should be configured per aggregation.
 *
 * @param sourceField  Column to evaluate in each group row.
 * @param outputField  Output column name for the resulting count.
 * @param matchValue   Count rows where `sourceField` equals this value (string comparison).
 *                     Use for discrete values such as `{"matchValue": 2}` for status=completed.
 * @param nonEmpty     When `true`, count rows where `sourceField` is non-null and non-empty.
 *                     Handles strings, java.util.Collection, and java.util.Map gracefully.
 *                     Use for presence checks such as `issued_certificates` nonEmpty.
 */
case class CountIfAgg(
    sourceField: String,
    outputField: String,
    matchValue:  Option[Any]     = None,
    nonEmpty:    Option[Boolean] = None
) extends AggregationDef

/** Maximum value — sourceField must be numeric or java.util.Date. */
case class MaxAgg(sourceField: String, outputField: String)      extends AggregationDef

/** Minimum value — sourceField must be numeric or java.util.Date. */
case class MinAgg(sourceField: String, outputField: String)      extends AggregationDef

/** Arithmetic sum — sourceField must be numeric (java.lang.Number). */
case class SumAgg(sourceField: String, outputField: String)      extends AggregationDef

/** Arithmetic average — sourceField must be numeric (java.lang.Number). */
case class AvgAgg(sourceField: String, outputField: String)      extends AggregationDef

/** First non-null value of sourceField in insertion order. */
case class FirstAgg(sourceField: String, outputField: String)    extends AggregationDef

/** Last non-null value of sourceField in insertion order. */
case class LastAgg(sourceField: String, outputField: String)     extends AggregationDef
