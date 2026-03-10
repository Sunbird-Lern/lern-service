package org.sunbird.observability.util

import org.sunbird.exception.ProjectCommonException
import org.sunbird.message.ResponseCode

/**
 * Validates that every filter key supplied in a request is declared in
 * the report's supportedFilters list.  Unknown filters are rejected to
 * prevent accidental query manipulation.
 */
object FilterValidator {

  /**
   * @param requestFilters  Filters provided by the caller (may be empty or null)
   * @param supportedFilters Keys listed in standard_reports_meta.supported_filters
   * @throws ProjectCommonException if an unknown filter key is found
   */
  def validate(requestFilters: Map[String, Any], supportedFilters: List[String]): Unit = {
    if (requestFilters == null || requestFilters.isEmpty) return

    val unknown = requestFilters.keySet.diff(supportedFilters.toSet)
    if (unknown.nonEmpty) {
      throw new ProjectCommonException(
        ResponseCode.invalidRequestData.getErrorCode,
        s"Unsupported filter(s): ${unknown.mkString(", ")}. Supported: ${supportedFilters.mkString(", ")}",
        ResponseCode.CLIENT_ERROR.getResponseCode
      )
    }
  }
}
