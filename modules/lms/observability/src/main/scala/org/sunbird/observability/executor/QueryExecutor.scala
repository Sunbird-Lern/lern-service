package org.sunbird.observability.executor

/**
 * Contract for executing a rendered report query.
 *
 * @param renderedQuery  The fully-rendered query string (ES DSL JSON or SQL).
 * @param params         Ordered list of bind parameters (used for SQL PreparedStatement).
 * @return               List of result rows, each row a Map of column → value.
 */
trait QueryExecutor {
  def execute(renderedQuery: String, params: List[Any]): List[Map[String, Any]]
}
