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
  private val MAX_FILTER_VALUE_LENGTH = 500

  def validate(requestFilters: Map[String, Any], supportedFilters: List[String]): Unit = {
    if (requestFilters == null || requestFilters.isEmpty) return

    val unknown = requestFilters.keySet.diff(supportedFilters.toSet)
    if (unknown.nonEmpty)
      throw new ProjectCommonException(
        ResponseCode.invalidRequestData.getErrorCode,
        s"Unsupported filter(s): ${unknown.mkString(", ")}. Supported: ${supportedFilters.mkString(", ")}",
        ResponseCode.CLIENT_ERROR.getResponseCode
      )

    // Reject array/object values — filter values must be scalar (String, Number, Boolean).
    // Passing an array would propagate to the CQL driver as a LIST bound to a TEXT column,
    // producing a cryptic InvalidTypeException (500) instead of a clean 400.
    val nonScalar = requestFilters.collect {
      case (k, v) if v.isInstanceOf[java.util.Collection[_]] || v.isInstanceOf[java.util.Map[_, _]] => k
    }
    if (nonScalar.nonEmpty)
      throw new ProjectCommonException(
        ResponseCode.invalidRequestData.getErrorCode,
        s"Filter(s) [${nonScalar.mkString(", ")}] must be a scalar value (string/number), not an array or object",
        ResponseCode.CLIENT_ERROR.getResponseCode
      )

    // Guard against excessively long string values that could cause memory pressure
    // or be used to probe the system.
    val tooLong = requestFilters.collect {
      case (k, v: String) if v.length > MAX_FILTER_VALUE_LENGTH => k
    }
    if (tooLong.nonEmpty)
      throw new ProjectCommonException(
        ResponseCode.invalidRequestData.getErrorCode,
        s"Filter value(s) for [${tooLong.mkString(", ")}] exceed maximum length of $MAX_FILTER_VALUE_LENGTH characters",
        ResponseCode.CLIENT_ERROR.getResponseCode
      )
  }
}
