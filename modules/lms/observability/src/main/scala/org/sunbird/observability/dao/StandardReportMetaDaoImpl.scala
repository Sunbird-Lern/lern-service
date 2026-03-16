package org.sunbird.observability.dao

import com.fasterxml.jackson.core.`type`.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import org.sunbird.db.PostgreSQLConnectionManager
import org.sunbird.logging.LoggerUtil
import org.sunbird.observability.model.{AggregationSpec, ReportMeta}

import java.sql.{Connection, PreparedStatement, ResultSet}
import scala.collection.mutable.ListBuffer

/**
 * PostgreSQL-backed implementation of [[StandardReportMetaDao]].
 *
 * Reads report definitions from the `standard_reports_meta` table.
 * Only rows with `enabled = TRUE` are returned so that draft or retired
 * report configs are invisible to callers without any code change.
 *
 * `aggregation_spec` is stored as JSONB and deserialized into [[AggregationSpec]]
 * using Jackson with [[DefaultScalaModule]] (required for Scala case class support).
 * A parse failure is logged and treated as `None` rather than crashing the request,
 * so mis-configured rows degrade gracefully.
 */
class StandardReportMetaDaoImpl extends StandardReportMetaDao {

  private val logger = new LoggerUtil(classOf[StandardReportMetaDaoImpl])
  private val mapper = new ObjectMapper().registerModule(DefaultScalaModule)
  private val listTypeRef = new TypeReference[java.util.List[String]]() {}

  private val GET_BY_ID_SQL =
    """SELECT report_id, title, description, domain, data_source, query_template, supported_filters, enabled, aggregation_spec
      |FROM standard_reports_meta
      |WHERE report_id = ? AND enabled = TRUE""".stripMargin

  private val LIST_ALL_SQL =
    """SELECT report_id, title, description, domain, data_source, query_template, supported_filters, enabled, aggregation_spec
      |FROM standard_reports_meta
      |WHERE enabled = TRUE
      |ORDER BY domain, report_id""".stripMargin

  override def getById(reportId: String): Option[ReportMeta] = {
    var conn: Connection = null
    var stmt: PreparedStatement = null
    var rs: ResultSet = null
    try {
      conn = PostgreSQLConnectionManager.getInstance().getConnection
      stmt = conn.prepareStatement(GET_BY_ID_SQL)
      stmt.setString(1, reportId)
      rs = stmt.executeQuery()
      // Only return None for genuine "no rows" — infrastructure exceptions propagate
      // to the actor and produce a 500, distinguishing DB-down from report-not-found.
      if (rs.next()) Some(mapRow(rs)) else None
    } finally {
      closeQuietly(rs, stmt, conn)
    }
  }

  override def listAll(): List[ReportMeta] = {
    var conn: Connection = null
    var stmt: PreparedStatement = null
    var rs: ResultSet = null
    val results = ListBuffer[ReportMeta]()
    try {
      conn = PostgreSQLConnectionManager.getInstance().getConnection
      stmt = conn.prepareStatement(LIST_ALL_SQL)
      rs = stmt.executeQuery()
      while (rs.next()) results += mapRow(rs)
    } finally {
      closeQuietly(rs, stmt, conn)
    }
    results.toList
  }

  private def mapRow(rs: ResultSet): ReportMeta = {
    val reportId = rs.getString("report_id")

    // Required columns — null means a corrupt DB row; fail loudly rather than NPE downstream
    val dataSource = Option(rs.getString("data_source")).getOrElse(
      throw new java.sql.SQLException(s"data_source is NULL for report_id=$reportId")
    )
    val queryTemplate = Option(rs.getString("query_template")).getOrElse(
      throw new java.sql.SQLException(s"query_template is NULL for report_id=$reportId")
    )

    val supportedFiltersJson = rs.getString("supported_filters")
    val supportedFilters: List[String] =
      if (supportedFiltersJson == null || supportedFiltersJson.isEmpty) List.empty
      else {
        val javaList = mapper.readValue(supportedFiltersJson, listTypeRef)
        import scala.collection.JavaConverters._
        javaList.asScala.toList
      }

    val aggregationSpecJson = rs.getString("aggregation_spec")
    val aggregationSpec: Option[AggregationSpec] =
      if (aggregationSpecJson == null || aggregationSpecJson.isEmpty) None
      else {
        try Some(mapper.readValue(aggregationSpecJson, classOf[AggregationSpec]))
        catch {
          case ex: Exception =>
            logger.error(s"StandardReportMetaDaoImpl.mapRow: Failed to parse aggregation_spec for report_id=${rs.getString("report_id")}", ex)
            None
        }
      }

    ReportMeta(
      reportId         = reportId,
      title            = rs.getString("title"),
      description      = Option(rs.getString("description")),
      domain           = rs.getString("domain"),
      dataSource       = dataSource,
      queryTemplate    = queryTemplate,
      supportedFilters = supportedFilters,
      enabled          = rs.getBoolean("enabled"),
      aggregationSpec  = aggregationSpec
    )
  }

  private def closeQuietly(rs: ResultSet, stmt: PreparedStatement, conn: Connection): Unit = {
    try { if (rs   != null) rs.close()   } catch { case _: Exception => }
    try { if (stmt != null) stmt.close() } catch { case _: Exception => }
    try { if (conn != null) conn.close() } catch { case _: Exception => }
  }
}
