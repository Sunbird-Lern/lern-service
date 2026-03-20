package org.sunbird.observability.executor

import org.sunbird.db.PostgreSQLConnectionManager
import org.sunbird.logging.LoggerUtil
import org.sunbird.request.RequestContext

import java.sql.{Connection, PreparedStatement, ResultSet}
import scala.collection.mutable.ListBuffer

/**
 * Executes SQL reports against YugabyteDB SQL (or PostgreSQL) via JDBC.
 * Uses PreparedStatement for safe parameter binding.
 */
class YugabyteQueryExecutor extends QueryExecutor {

  private val logger = new LoggerUtil(classOf[YugabyteQueryExecutor])

  override def execute(renderedQuery: String, params: List[Any], requestContext: RequestContext): List[Map[String, Any]] = {
    var conn: Connection = null
    var stmt: PreparedStatement = null
    var rs: ResultSet = null
    val results = ListBuffer[Map[String, Any]]()

    try {
      conn = PostgreSQLConnectionManager.getInstance().getConnection
      stmt = conn.prepareStatement(renderedQuery)

      params.zipWithIndex.foreach { case (param, idx) =>
        val position = idx + 1
        param match {
          case v: String  => stmt.setString(position, v)
          case v: Int     => stmt.setInt(position, v)
          case v: Long    => stmt.setLong(position, v)
          case v: Double  => stmt.setDouble(position, v)
          case v: Boolean => stmt.setBoolean(position, v)
          case v          => stmt.setObject(position, v)
        }
      }

      rs = stmt.executeQuery()
      val meta = rs.getMetaData
      val colCount = meta.getColumnCount

      while (rs.next()) {
        val row = (1 to colCount).map { i =>
          val colName = meta.getColumnLabel(i)
          val value   = rs.getObject(i)
          colName -> (if (rs.wasNull()) null.asInstanceOf[Any] else value.asInstanceOf[Any])
        }.toMap
        results += row
      }
    } catch {
      case ex: Exception =>
        logger.error("YugabyteQueryExecutor: Query execution failed", ex)
        throw ex
    } finally {
      try { if (rs   != null) rs.close()   } catch { case ex: Exception => logger.warn("YugabyteQueryExecutor: failed to close ResultSet",   ex) }
      try { if (stmt != null) stmt.close() } catch { case ex: Exception => logger.warn("YugabyteQueryExecutor: failed to close PreparedStatement", ex) }
      try { if (conn != null) conn.close() } catch { case ex: Exception => logger.warn("YugabyteQueryExecutor: failed to close Connection",        ex) }
    }

    results.toList
  }
}
