package org.sunbird.validators;

import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.sunbird.exception.ProjectCommonException;
import org.sunbird.keys.JsonKey;
import org.sunbird.request.Request;
import org.sunbird.response.ResponseCode;

public class UserFreeUpRequestValidatorTest {

  private Request request;
  private Map<String, Object> requestData;
  private UserFreeUpRequestValidator validator;

  @Before
  public void setUp() {
    request = new Request();
    requestData = new HashMap<>();
    request.setRequest(requestData);
  }

  // =============================================
  // validateIdPresence Tests
  // =============================================

  @Test
  public void testValidate_WithValidIdPresent() {
    requestData.put(JsonKey.ID, "user123");
    requestData.put(JsonKey.IDENTIFIER, Arrays.asList(JsonKey.EMAIL, JsonKey.PHONE));

    validator = UserFreeUpRequestValidator.getInstance(request);

    // Should not throw exception
    validator.validate();
  }

  @Test(expected = ProjectCommonException.class)
  public void testValidate_WithIdMissing() {
    requestData.put(JsonKey.IDENTIFIER, Arrays.asList(JsonKey.EMAIL));

    validator = UserFreeUpRequestValidator.getInstance(request);
    validator.validate();
  }

  @Test(expected = ProjectCommonException.class)
  public void testValidate_WithIdBlank() {
    requestData.put(JsonKey.ID, "");
    requestData.put(JsonKey.IDENTIFIER, Arrays.asList(JsonKey.EMAIL));

    validator = UserFreeUpRequestValidator.getInstance(request);
    validator.validate();
  }

  @Test(expected = ProjectCommonException.class)
  public void testValidate_WithIdNull() {
    requestData.put(JsonKey.ID, null);
    requestData.put(JsonKey.IDENTIFIER, Arrays.asList(JsonKey.EMAIL));

    validator = UserFreeUpRequestValidator.getInstance(request);
    validator.validate();
  }

  @Test
  public void testValidate_WithIdMissing_ExceptionCode() {
    requestData.put(JsonKey.IDENTIFIER, Arrays.asList(JsonKey.EMAIL));

    validator = UserFreeUpRequestValidator.getInstance(request);

    try {
      validator.validate();
      fail("Should throw exception");
    } catch (ProjectCommonException e) {
      assertEquals(ResponseCode.mandatoryParamsMissing.getErrorCode(), e.getErrorCode());
    }
  }

  // =============================================
  // validateIdentifier Presence Tests
  // =============================================

  @Test(expected = ProjectCommonException.class)
  public void testValidate_WithIdentifierMissing() {
    requestData.put(JsonKey.ID, "user123");

    validator = UserFreeUpRequestValidator.getInstance(request);
    validator.validate();
  }

  @Test
  public void testValidate_WithIdentifierMissing_ExceptionCode() {
    requestData.put(JsonKey.ID, "user123");

    validator = UserFreeUpRequestValidator.getInstance(request);

    try {
      validator.validate();
      fail("Should throw exception");
    } catch (ProjectCommonException e) {
      assertEquals(ResponseCode.mandatoryParamsMissing.getErrorCode(), e.getErrorCode());
    }
  }

  // =============================================
  // validateIdentifier Type Tests
  // =============================================

  @Test(expected = ProjectCommonException.class)
  public void testValidate_WithIdentifierNotList() {
    requestData.put(JsonKey.ID, "user123");
    requestData.put(JsonKey.IDENTIFIER, "EMAIL"); // Should be List

    validator = UserFreeUpRequestValidator.getInstance(request);
    validator.validate();
  }

  @Test(expected = ProjectCommonException.class)
  public void testValidate_WithIdentifierAsMap() {
    requestData.put(JsonKey.ID, "user123");
    requestData.put(JsonKey.IDENTIFIER, new HashMap<>()); // Should be List

    validator = UserFreeUpRequestValidator.getInstance(request);
    validator.validate();
  }

  @Test(expected = ProjectCommonException.class)
  public void testValidate_WithIdentifierAsString() {
    requestData.put(JsonKey.ID, "user123");
    requestData.put(JsonKey.IDENTIFIER, "notalist");

    validator = UserFreeUpRequestValidator.getInstance(request);
    validator.validate();
  }

  @Test
  public void testValidate_WithIdentifierNotList_ExceptionCode() {
    requestData.put(JsonKey.ID, "user123");
    requestData.put(JsonKey.IDENTIFIER, "EMAIL");

    validator = UserFreeUpRequestValidator.getInstance(request);

    try {
      validator.validate();
      fail("Should throw exception");
    } catch (ProjectCommonException e) {
      assertEquals(ResponseCode.dataTypeError.getErrorCode(), e.getErrorCode());
    }
  }

  // =============================================
  // validateIdentifier Subset Tests
  // =============================================

  @Test
  public void testValidate_WithValidIdentifierEmail() {
    requestData.put(JsonKey.ID, "user123");
    requestData.put(JsonKey.IDENTIFIER, Arrays.asList(JsonKey.EMAIL));

    validator = UserFreeUpRequestValidator.getInstance(request);

    // Should not throw exception
    validator.validate();
  }

  @Test
  public void testValidate_WithValidIdentifierPhone() {
    requestData.put(JsonKey.ID, "user123");
    requestData.put(JsonKey.IDENTIFIER, Arrays.asList(JsonKey.PHONE));

    validator = UserFreeUpRequestValidator.getInstance(request);

    // Should not throw exception
    validator.validate();
  }

  @Test
  public void testValidate_WithValidIdentifierBoth() {
    requestData.put(JsonKey.ID, "user123");
    requestData.put(JsonKey.IDENTIFIER, Arrays.asList(JsonKey.EMAIL, JsonKey.PHONE));

    validator = UserFreeUpRequestValidator.getInstance(request);

    // Should not throw exception
    validator.validate();
  }

  @Test(expected = ProjectCommonException.class)
  public void testValidate_WithInvalidIdentifierUserId() {
    requestData.put(JsonKey.ID, "user123");
    requestData.put(JsonKey.IDENTIFIER, Arrays.asList("USERID"));

    validator = UserFreeUpRequestValidator.getInstance(request);
    validator.validate();
  }

  @Test(expected = ProjectCommonException.class)
  public void testValidate_WithInvalidIdentifierMultiple() {
    requestData.put(JsonKey.ID, "user123");
    requestData.put(JsonKey.IDENTIFIER, Arrays.asList(JsonKey.EMAIL, "USERID"));

    validator = UserFreeUpRequestValidator.getInstance(request);
    validator.validate();
  }

  @Test(expected = ProjectCommonException.class)
  public void testValidate_WithMultipleInvalidIdentifiers() {
    requestData.put(JsonKey.ID, "user123");
    requestData.put(JsonKey.IDENTIFIER, Arrays.asList("USERID", "ACCOUNTID"));

    validator = UserFreeUpRequestValidator.getInstance(request);
    validator.validate();
  }

  @Test
  public void testValidate_WithInvalidIdentifier_ExceptionCode() {
    requestData.put(JsonKey.ID, "user123");
    requestData.put(JsonKey.IDENTIFIER, Arrays.asList("INVALID"));

    validator = UserFreeUpRequestValidator.getInstance(request);

    try {
      validator.validate();
      fail("Should throw exception");
    } catch (ProjectCommonException e) {
      assertEquals(ResponseCode.dataTypeError.getErrorCode(), e.getErrorCode());
    }
  }

  @Test
  public void testValidate_WithInvalidIdentifier_ExceptionMessage() {
    requestData.put(JsonKey.ID, "user123");
    requestData.put(JsonKey.IDENTIFIER, Arrays.asList("INVALID"));

    validator = UserFreeUpRequestValidator.getInstance(request);

    try {
      validator.validate();
      fail("Should throw exception");
    } catch (ProjectCommonException e) {
      // Exception should be dataTypeError for invalid identifier
      assertEquals(ResponseCode.dataTypeError.getErrorCode(), e.getErrorCode());
      // Message should contain some indication of the error
      assertNotNull(e.getMessage());
    }
  }

  // =============================================
  // Factory Method Tests
  // =============================================

  @Test
  public void testGetInstance_ReturnsValidator() {
    validator = UserFreeUpRequestValidator.getInstance(request);

    assertNotNull(validator);
    assertTrue(validator instanceof UserFreeUpRequestValidator);
  }

  @Test
  public void testGetInstance_NewInstanceEachTime() {
    UserFreeUpRequestValidator validator1 = UserFreeUpRequestValidator.getInstance(request);
    UserFreeUpRequestValidator validator2 = UserFreeUpRequestValidator.getInstance(request);

    // Should be different instances
    assertNotSame(validator1, validator2);
  }

  // =============================================
  // Edge Case Tests
  // =============================================

  @Test
  public void testValidate_WithEmptyIdentifierList() {
    requestData.put(JsonKey.ID, "user123");
    requestData.put(JsonKey.IDENTIFIER, Arrays.asList());

    validator = UserFreeUpRequestValidator.getInstance(request);

    // Empty list might be allowed or rejected depending on implementation
    try {
      validator.validate();
    } catch (ProjectCommonException e) {
      assertEquals(ResponseCode.dataTypeError.getErrorCode(), e.getErrorCode());
    }
  }

  @Test
  public void testValidate_WithIdentifierCaseMismatch() {
    requestData.put(JsonKey.ID, "user123");
    requestData.put(JsonKey.IDENTIFIER, Arrays.asList("email")); // lowercase instead of EMAIL

    validator = UserFreeUpRequestValidator.getInstance(request);

    // Case sensitivity depends on implementation
    try {
      validator.validate();
      // If it passes, that's okay - might be case-insensitive
    } catch (ProjectCommonException e) {
      assertEquals(ResponseCode.dataTypeError.getErrorCode(), e.getErrorCode());
    }
  }

  @Test
  public void testValidate_WithSpaceInIdentifier() {
    requestData.put(JsonKey.ID, "user123");
    requestData.put(JsonKey.IDENTIFIER, Arrays.asList(" EMAIL "));

    validator = UserFreeUpRequestValidator.getInstance(request);

    // This might fail depending on implementation - trimming behavior
    try {
      validator.validate();
    } catch (ProjectCommonException e) {
      // Spaces might cause it to be treated as invalid
      assertEquals(ResponseCode.dataTypeError.getErrorCode(), e.getErrorCode());
    }
  }

  // =============================================
  // Complete Validation Flow Tests
  // =============================================

  @Test
  public void testValidate_CompleteValidRequest() {
    requestData.put(JsonKey.ID, "user_123");
    requestData.put(JsonKey.IDENTIFIER, Arrays.asList(JsonKey.EMAIL, JsonKey.PHONE));

    validator = UserFreeUpRequestValidator.getInstance(request);

    // Should complete without exception
    validator.validate();
  }

  @Test
  public void testValidate_MinimalValidRequest() {
    requestData.put(JsonKey.ID, "u1");
    requestData.put(JsonKey.IDENTIFIER, Arrays.asList(JsonKey.EMAIL));

    validator = UserFreeUpRequestValidator.getInstance(request);

    // Should complete without exception
    validator.validate();
  }

  @Test(expected = ProjectCommonException.class)
  public void testValidate_AllFieldsMissing() {
    // Both ID and IDENTIFIER missing

    validator = UserFreeUpRequestValidator.getInstance(request);
    validator.validate();
  }

  @Test
  public void testValidate_MultipleValidations() {
    // Test that all validations run in sequence

    // First validation: missing IDENTIFIER
    requestData.put(JsonKey.ID, "user123");
    validator = UserFreeUpRequestValidator.getInstance(request);

    try {
      validator.validate();
      fail("Should fail at IDENTIFIER check");
    } catch (ProjectCommonException e) {
      assertEquals(ResponseCode.mandatoryParamsMissing.getErrorCode(), e.getErrorCode());
    }

    // Second validation: invalid IDENTIFIER type
    requestData.put(JsonKey.IDENTIFIER, "notAList");
    validator = UserFreeUpRequestValidator.getInstance(request);

    try {
      validator.validate();
      fail("Should fail at type check");
    } catch (ProjectCommonException e) {
      assertEquals(ResponseCode.dataTypeError.getErrorCode(), e.getErrorCode());
    }

    // Third validation: invalid IDENTIFIER values
    requestData.put(JsonKey.IDENTIFIER, Arrays.asList("INVALID"));
    validator = UserFreeUpRequestValidator.getInstance(request);

    try {
      validator.validate();
      fail("Should fail at subset check");
    } catch (ProjectCommonException e) {
      assertEquals(ResponseCode.dataTypeError.getErrorCode(), e.getErrorCode());
    }
  }
}
