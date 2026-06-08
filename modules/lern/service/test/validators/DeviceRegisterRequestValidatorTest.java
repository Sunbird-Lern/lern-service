package validators;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.sunbird.exception.ProjectCommonException;
import org.sunbird.message.ResponseCode;
import org.sunbird.request.Request;

/**
 * Unit tests for DeviceRegisterRequestValidator.
 */
public class DeviceRegisterRequestValidatorTest {

  private DeviceRegisterRequestValidator validator;
  private Request request;

  @Before
  public void setUp() {
    validator = new DeviceRegisterRequestValidator();
    request = new Request();
    request.setRequest(new java.util.HashMap<>());
  }

  @Test
  public void testValidate_throwsWhenDeviceIdMissing() {
    // Arrange
    request.getRequest().put("deviceId", null);

    // Act & Assert
    try {
      validator.validate(request);
      Assert.fail("Expected ProjectCommonException");
    } catch (ProjectCommonException ex) {
      Assert.assertEquals(ResponseCode.mandatoryParameterMissing.getErrorCode(), ex.getErrorCode());
    }
  }

  @Test
  public void testValidate_throwsWhenDeviceIdBlank() {
    // Arrange
    request.getRequest().put("deviceId", "");

    // Act & Assert
    try {
      validator.validate(request);
      Assert.fail("Expected ProjectCommonException");
    } catch (ProjectCommonException ex) {
      Assert.assertEquals(ResponseCode.mandatoryParameterMissing.getErrorCode(), ex.getErrorCode());
    }
  }

  @Test
  public void testValidate_passesWithOnlyDeviceId() {
    // Arrange
    request.getRequest().put("deviceId", "test-device-001");

    // Act & Assert
    validator.validate(request); // Should not throw
  }

  @Test
  public void testValidate_passesWithFullRequest() {
    // Arrange
    request.getRequest().put("deviceId", "test-device-001");
    request.getRequest().put("fcmToken", "token123");
    request.getRequest().put("producer", "test-app");

    // Act & Assert
    validator.validate(request); // Should not throw
  }
}
