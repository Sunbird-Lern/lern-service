package org.sunbird.observability.executor

import com.datastax.driver.core.Session
import org.sunbird.helper.CassandraConnectionMngrFactory
import org.sunbird.logging.LoggerUtil

import scala.collection.JavaConverters._
import scala.collection.mutable.ListBuffer

/**
 * Executes CQL reports against YugabyteDB YCQL (Cassandra-compatible layer) via the
 * Datastax Java Driver.  Uses PreparedStatement for safe positional-parameter binding.
 *
 * The session is retrieved from the shared [[CassandraConnectionMngrFactory]] singleton,
 * which manages lifecycle (pool, reconnection, shutdown hook) for the whole application.
 * We deliberately do NOT close the session after each query — it is owned by the manager.
 *
 * @param keyspace  Cassandra keyspace used to obtain the session.  All query templates
 *                  should use fully-qualified table names (e.g. sunbird_courses.user_enrolments)
 *                  so that a single keyspace handle is sufficient for cross-keyspace queries.
 */
class YugabyteCqlQueryExecutor(keyspace: String = "sunbird_courses") extends QueryExecutor {

  private val logger = new LoggerUtil(classOf[YugabyteCqlQueryExecutor])

  override def execute(renderedQuery: String, params: List[Any]): List[Map[String, Any]] = {
    val results = ListBuffer[Map[String, Any]]()
    try {
      val session: Session = CassandraConnectionMngrFactory.getInstance().getSession(keyspace)

      val prepared = session.prepare(renderedQuery)
      val boundParams: Array[AnyRef] = params.map {
        case v: Int     => java.lang.Integer.valueOf(v)
        case v: Long    => java.lang.Long.valueOf(v)
        case v: Double  => java.lang.Double.valueOf(v)
        case v: Boolean => java.lang.Boolean.valueOf(v)
        case v          => v.asInstanceOf[AnyRef]
      }.toArray

      val rs = session.execute(prepared.bind(boundParams: _*))
      val colDefs = rs.getColumnDefinitions.asList().asScala.toList

      rs.all().asScala.foreach { row =>
        val rowMap = colDefs.map { col =>
          val name  = col.getName
          val value = row.getObject(name)
          name -> (if (row.isNull(name)) null.asInstanceOf[Any] else value.asInstanceOf[Any])
        }.toMap
        results += rowMap
      }
    } catch {
      case ex: Exception =>
        logger.error("YugabyteCqlQueryExecutor: Query execution failed", ex)
        throw ex
    }
    results.toList
  }
}
