package validators;

import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.sunbird.exception.ProjectCommonException;
import org.sunbird.message.ResponseCode;
import org.sunbird.request.Request;

/**
 * Validator for device registration requests.
 */
public class DeviceRegisterRequestValidator {

  /**
   * Validates device registration request.
   * Required: deviceId (non-blank).
   * Optional: fcmToken, producer, dspec, userDeclaredLocation, etc.
   *
   * @param request The request to validate
   * @throws ProjectCommonException if validation fails
   */
  public void validate(Request request) {
    Map<String, Object> req = request.getRequest();

    String deviceId = (String) req.get("deviceId");
    if (StringUtils.isBlank(deviceId)) {
      throw new ProjectCommonException(
          ResponseCode.mandatoryParameterMissing.getErrorCode(),
          ResponseCode.mandatoryParameterMissing.getErrorMessage() + " deviceId",
          ResponseCode.CLIENT_ERROR.getResponseCode());
    }
  }
}
