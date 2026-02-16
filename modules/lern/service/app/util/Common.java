package util;

import org.apache.commons.lang3.StringUtils;
import org.sunbird.response.ResponseCode;
import org.sunbird.keys.JsonKey;
import org.sunbird.response.ResponseParams;
import play.libs.typedmap.TypedKey;
import play.mvc.Http;

/**
 * Common utility class providing helper methods for request attribute retrieval
 * and standardized response parameter object creation.
 */
public class Common {

  /**
   * Retrieves an attribute value from the given HTTP request in a type-safe manner.
   *
   * @param httpReq The HTTP request object.
   * @param attribute The TypedKey representing the attribute to retrieve.
   * @return The attribute value as a String if present, null otherwise.
   */
  public static String getFromRequest(Http.Request httpReq, TypedKey<?> attribute) {
    String attributeValue = null;
    if (httpReq.attrs() != null && httpReq.attrs().containsKey(attribute)) {
      attributeValue = (String) httpReq.attrs().get(attribute);
    }
    return attributeValue;
  }

  /**
   * Creates and populates a ResponseParams object based on the provided response code
   * and tracking identifiers.
   *
   * @param code The ResponseCode indicating the outcome of the operation.
   * @param customMessage An optional custom error message to override the default.
   * @param requestId The unique ID of the request, used for both resmsgid and msgid.
   * @return A populated ResponseParams object.
   */
  public static ResponseParams createResponseParamObj(
      ResponseCode code, String customMessage, String requestId) {
    ResponseParams params = new ResponseParams();
    if (code.getResponseCode() != 200) {
      params.setErr(code.getErrorCode());
      params.setErrmsg(
          StringUtils.isNotBlank(customMessage) ? customMessage : code.getErrorMessage());
      params.setStatus(JsonKey.FAILED);
    } else {
      params.setStatus(JsonKey.SUCCESS);
    }
    params.setResmsgid(requestId);
    params.setMsgid(requestId);
    return params;
  }
}
