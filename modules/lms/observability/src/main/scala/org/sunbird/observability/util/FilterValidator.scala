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
  private val MAX_ARRAY_FILTER_SIZE   = 1000

  def validate(requestFilters: Map[String, Any], supportedFilters: List[String]): Unit = {
    if (requestFilters == null || requestFilters.isEmpty) return

    val unknown = requestFilters.keySet.diff(supportedFilters.toSet)
    if (unknown.nonEmpty)
      throw new ProjectCommonException(
        ResponseCode.invalidRequestData.getErrorCode,
        s"Unsupported filter(s): ${unknown.mkString(", ")}. Supported: ${supportedFilters.mkString(", ")}",
        ResponseCode.CLIENT_ERROR.getResponseCode
      )

    // Objects (Maps) are always rejected — only scalars and arrays of scalars are allowed.
    val objectFilters = requestFilters.collect {
      case (k, v) if v.isInstanceOf[java.util.Map[_, _]] => k
    }
    if (objectFilters.nonEmpty)
      throw new ProjectCommonException(
        ResponseCode.invalidRequestData.getErrorCode,
        s"Filter(s) [${objectFilters.mkString(", ")}] must be a scalar or array value, not an object",
        ResponseCode.CLIENT_ERROR.getResponseCode
      )

    // Array (Collection) values are allowed for IN clause queries but must be non-empty and bounded.
    // An empty array would expand to WHERE x IN () which is invalid SQL/CQL.
    val emptyArrays = requestFilters.collect {
      case (k, v: java.util.Collection[_]) if v.isEmpty => k
    }
    if (emptyArrays.nonEmpty)
      throw new ProjectCommonException(
        ResponseCode.invalidRequestData.getErrorCode,
        s"Filter array(s) [${emptyArrays.mkString(", ")}] must not be empty",
        ResponseCode.CLIENT_ERROR.getResponseCode
      )

    val oversizedArrays = requestFilters.collect {
      case (k, v: java.util.Collection[_]) if v.size() > MAX_ARRAY_FILTER_SIZE => k
    }
    if (oversizedArrays.nonEmpty)
      throw new ProjectCommonException(
        ResponseCode.invalidRequestData.getErrorCode,
        s"Filter array(s) [${oversizedArrays.mkString(", ")}] exceed maximum size of $MAX_ARRAY_FILTER_SIZE elements",
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
