package org.sunbird.observability.executor

import com.datastax.driver.core.exceptions.InvalidQueryException
import com.datastax.driver.core.{PreparedStatement, ResultSet, Session}
import org.sunbird.helper.CassandraConnectionMngrFactory
import org.sunbird.logging.LoggerUtil

import java.util.concurrent.ConcurrentHashMap
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
 * Prepared statements are cached in a ConcurrentHashMap keyed by the query string to avoid
 * the "re-preparing already prepared query" anti-pattern.  If a stale statement is detected
 * (e.g. after a session reconnect), the cache entry is evicted and the statement re-prepared.
 *
 * @param keyspace  Cassandra keyspace used to obtain the session.  All query templates
 *                  should use fully-qualified table names (e.g. sunbird_courses.user_enrolments)
 *                  so that a single keyspace handle is sufficient for cross-keyspace queries.
 */
class YugabyteCqlQueryExecutor(keyspace: String = "sunbird_courses") extends QueryExecutor {

  private val logger    = new LoggerUtil(classOf[YugabyteCqlQueryExecutor])
  private val stmtCache = new ConcurrentHashMap[String, PreparedStatement]()

  private def getPrepared(session: Session, query: String): PreparedStatement =
    // computeIfAbsent is atomic — eliminates the get-then-putIfAbsent race that allowed
    // duplicate session.prepare() calls under concurrent requests for the same query.
    stmtCache.computeIfAbsent(query, _ => session.prepare(query))

  /**
   * Executes a prepared statement, handling the case where YugabyteDB went down and
   * reconnected with a new Cluster instance.
   *
   * When a new session is created after reconnect, the old [[PreparedStatement]] objects
   * in [[stmtCache]] are unknown to the new cluster and throw [[InvalidQueryException]]
   * at execution time (not at prepare time). On that specific exception we evict the
   * stale cache entry, re-prepare against the current session, and retry once.
   */
  private def executeWithRetry(
      session:     Session,
      query:       String,
      boundParams: Array[AnyRef]
  ): ResultSet = {
    val prepared = getPrepared(session, query)
    try {
      session.execute(prepared.bind(boundParams: _*))
    } catch {
      case _: InvalidQueryException =>
        logger.info(s"YugabyteCqlQueryExecutor: stale PreparedStatement detected, evicting cache and re-preparing")
        stmtCache.remove(query)
        val fresh = session.prepare(query)
        stmtCache.putIfAbsent(query, fresh)
        session.execute(fresh.bind(boundParams: _*))
    }
  }

  override def execute(renderedQuery: String, params: List[Any]): List[Map[String, Any]] = {
    val results = ListBuffer[Map[String, Any]]()
    try {
      val session: Session = CassandraConnectionMngrFactory.getInstance().getSession(keyspace)

      val boundParams: Array[AnyRef] = params.map {
        case v: Int     => java.lang.Integer.valueOf(v)
        case v: Long    => java.lang.Long.valueOf(v)
        case v: Double  => java.lang.Double.valueOf(v)
        case v: Boolean => java.lang.Boolean.valueOf(v)
        case v          => v.asInstanceOf[AnyRef]
      }.toArray

      // rs.all() materializes the entire result set into memory at once.
      // This is acceptable for admin/reporting queries that are expected to return
      // bounded row counts (thousands, not millions). Do not use this executor for
      // unbounded table scans — add a LIMIT clause to the query template instead.
      val rs = executeWithRetry(session, renderedQuery, boundParams)
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
