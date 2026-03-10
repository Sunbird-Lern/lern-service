package org.sunbird.observability.dao

import com.fasterxml.jackson.core.`type`.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import org.sunbird.db.PostgreSQLConnectionManager
import org.sunbird.logging.LoggerUtil
import org.sunbird.observability.model.ReportMeta

import java.sql.{Connection, PreparedStatement, ResultSet}
import scala.collection.mutable.ListBuffer

class StandardReportMetaDaoImpl extends StandardReportMetaDao {

  private val logger = new LoggerUtil(classOf[StandardReportMetaDaoImpl])
  private val mapper = new ObjectMapper()
  private val listTypeRef = new TypeReference[java.util.List[String]]() {}

  private val GET_BY_ID_SQL =
    """SELECT report_id, title, description, domain, data_source, query_template, supported_filters, enabled
      |FROM standard_reports_meta
      |WHERE report_id = ? AND enabled = TRUE""".stripMargin

  private val LIST_ALL_SQL =
    """SELECT report_id, title, description, domain, data_source, query_template, supported_filters, enabled
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
      if (rs.next()) Some(mapRow(rs)) else None
    } catch {
      case ex: Exception =>
        logger.error(s"StandardReportMetaDaoImpl.getById: Failed for reportId=$reportId", ex)
        None
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
    } catch {
      case ex: Exception =>
        logger.error("StandardReportMetaDaoImpl.listAll: Failed", ex)
    } finally {
      closeQuietly(rs, stmt, conn)
    }
    results.toList
  }

  private def mapRow(rs: ResultSet): ReportMeta = {
    val supportedFiltersJson = rs.getString("supported_filters")
    val supportedFilters: List[String] =
      if (supportedFiltersJson == null || supportedFiltersJson.isEmpty) List.empty
      else {
        val javaList = mapper.readValue(supportedFiltersJson, listTypeRef)
        import scala.collection.JavaConverters._
        javaList.asScala.toList
      }
    ReportMeta(
      reportId         = rs.getString("report_id"),
      title            = rs.getString("title"),
      description      = Option(rs.getString("description")),
      domain           = rs.getString("domain"),
      dataSource       = rs.getString("data_source"),
      queryTemplate    = rs.getString("query_template"),
      supportedFilters = supportedFilters,
      enabled          = rs.getBoolean("enabled")
    )
  }

  private def closeQuietly(rs: ResultSet, stmt: PreparedStatement, conn: Connection): Unit = {
    try { if (rs   != null) rs.close()   } catch { case _: Exception => }
    try { if (stmt != null) stmt.close() } catch { case _: Exception => }
    try { if (conn != null) conn.close() } catch { case _: Exception => }
  }
}
