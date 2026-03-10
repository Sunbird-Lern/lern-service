package org.sunbird.observability.model

/**
 * Represents a row in the standard_reports_meta SQL table.
 *
 * @param reportId         Unique identifier for the report
 * @param title            Human-readable title
 * @param description      Optional description
 * @param domain           Domain: generic | consumption | user_profile
 * @param dataSource       Data source: ELASTICSEARCH | YUGABYTE_SQL
 * @param queryTemplate    Query template string with {{key}} placeholders
 * @param supportedFilters List of filter keys this report accepts
 * @param enabled          Whether the report is available for use
 */
case class ReportMeta(
    reportId: String,
    title: String,
    description: Option[String],
    domain: String,
    dataSource: String,
    queryTemplate: String,
    supportedFilters: List[String],
    enabled: Boolean
)
