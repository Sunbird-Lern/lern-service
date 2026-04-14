package org.sunbird.observability.transform

import org.sunbird.request.RequestContext

/**
 * Contract for all transform utilities.
 *
 * fetchDetails is the only responsibility — which field triggers this util,
 * what key is added to the row, and caching are all handled externally by
 * TransformRegistry and TransformCache. This keeps each util a pure data-fetcher
 * and trivially testable without any config or cache dependency.
 */
trait TransformUtil {

  /**
   * Given a deduplicated list of IDs (values of the source field collected from
   * the report rows), fetch their details and return a lookup map.
   *
   * @param ids     Unique IDs to fetch; only uncached IDs are passed here by TransformCache.
   * @param fields  Allowlisted field names from config (projection applied at this layer).
   * @param context Request context for logging / tracing.
   * @return        Map of id -> projected detail map.
   */
  def fetchDetails(
      ids:     List[String],
      fields:  List[String],
      context: RequestContext
  ): Map[String, Map[String, AnyRef]]
}
