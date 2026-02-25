package org.sunbird.validators;

import static org.junit.Assert.*;

import java.util.ArrayList;
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

/**
 * Comprehensive test suite for BaseRequestValidator focusing on all methods with positive and
 * negative scenarios.
 */
public class EnhancedBaseRequestValidatorTest {

  private BaseRequestValidator validator;
  private Map<String, Object> testData;

  @Before
  public void setUp() {
    validator = new BaseRequestValidator();
    testData = new HashMap<>();
  }

  // =============================================
  // checkMandatoryFieldsPresent Tests (Varargs)
  // =============================================

  @Test
  public void testCheckMandatoryFieldsPresent_AllFieldsPresent() {
    testData.put("field1", "value1");
    testData.put("field2", "value2");

    // Should not throw exception
    validator.checkMandatoryFieldsPresent(testData, "field1", "field2");
  }

  @Test(expected = ProjectCommonException.class)
  public void testCheckMandatoryFieldsPresent_MissingField() {
    testData.put("field1", "value1");

    validator.checkMandatoryFieldsPresent(testData, "field1", "field2");
  }

  @Test(expected = ProjectCommonException.class)
  public void testCheckMandatoryFieldsPresent_BlankField() {
    testData.put("field1", "");
    testData.put("field2", "value2");

    validator.checkMandatoryFieldsPresent(testData, "field1", "field2");
  }

  @Test(expected = ProjectCommonException.class)
  public void testCheckMandatoryFieldsPresent_NullField() {
    testData.put("field1", null);

    validator.checkMandatoryFieldsPresent(testData, "field1");
  }

  @Test(expected = ProjectCommonException.class)
  public void testCheckMandatoryFieldsPresent_EmptyMap() {
    validator.checkMandatoryFieldsPresent(new HashMap<>(), "field1");
  }

  @Test
  public void testCheckMandatoryFieldsPresent_ExceptionCode() {
    testData.put("field1", "");

    try {
      validator.checkMandatoryFieldsPresent(testData, "field1");
      fail("Should throw exception");
    } catch (ProjectCommonException e) {
      assertEquals(
          ResponseCode.mandatoryParamsMissing.getErrorCode(), e.getErrorCode());
    }
  }

  // =============================================
  // checkMandatoryFieldsPresent Tests (List)
  // =============================================

  @Test
  public void testCheckMandatoryFieldsPresent_List_AllFieldsPresent() {
    testData.put("field1", "value1");
    testData.put("field2", "value2");

    List<String> mandatoryFields = Arrays.asList("field1", "field2");
    validator.checkMandatoryFieldsPresent(testData, mandatoryFields);
  }

  @Test(expected = ProjectCommonException.class)
  public void testCheckMandatoryFieldsPresent_List_MissingField() {
    testData.put("field1", "value1");

    List<String> mandatoryFields = Arrays.asList("field1", "field2");
    validator.checkMandatoryFieldsPresent(testData, mandatoryFields);
  }

  @Test
  public void testCheckMandatoryFieldsPresent_List_AllValidStringTypes() {
    testData.put("field1", "value1");
    testData.put("field2", "value2");

    List<String> mandatoryFields = Arrays.asList("field1", "field2");
    // Should not throw exception
    validator.checkMandatoryFieldsPresent(testData, mandatoryFields);
  }

  @Test(expected = ProjectCommonException.class)
  public void testCheckMandatoryFieldsPresent_List_InvalidBlankValue() {
    testData.put("field1", "value1");
    testData.put("field2", "");

    List<String> mandatoryFields = Arrays.asList("field1", "field2");
    validator.checkMandatoryFieldsPresent(testData, mandatoryFields);
  }

  // =============================================
  // checkMandatoryParamsPresent Tests
  // =============================================

  @Test
  public void testCheckMandatoryParamsPresent_AllFieldsPresent() {
    testData.put("field1", "value1");
    testData.put("field2", "value2");

    validator.checkMandatoryParamsPresent(testData, "Custom error message", "field1", "field2");
  }

  @Test(expected = ProjectCommonException.class)
  public void testCheckMandatoryParamsPresent_MissingField() {
    testData.put("field1", "value1");

    validator.checkMandatoryParamsPresent(testData, "Custom error message", "field1", "field2");
  }

  @Test(expected = ProjectCommonException.class)
  public void testCheckMandatoryParamsPresent_BlankField() {
    testData.put("field1", "");

    validator.checkMandatoryParamsPresent(testData, "Custom message", "field1");
  }

  @Test(expected = ProjectCommonException.class)
  public void testCheckMandatoryParamsPresent_EmptyMap() {
    validator.checkMandatoryParamsPresent(new HashMap<>(), "message", "field1");
  }

  @Test
  public void testCheckMandatoryParamsPresent_ErrorMessage() {
    testData.put("field1", "");

    try {
      validator.checkMandatoryParamsPresent(testData, "Custom error", "field1");
      fail("Should throw exception");
    } catch (ProjectCommonException e) {
      assertEquals(
          ResponseCode.mandatoryParamsMissing.getErrorCode(), e.getErrorCode());
      assertTrue(e.getMessage().contains("Custom error"));
    }
  }

  // =============================================
  // checkReadOnlyAttributesAbsent Tests
  // =============================================

  @Test
  public void testCheckReadOnlyAttributesAbsent_NoReadOnlyFields() {
    testData.put("field1", "value1");
    testData.put("field2", "value2");

    // Should not throw exception
    validator.checkReadOnlyAttributesAbsent(testData, "readonly1", "readonly2");
  }

  @Test(expected = ProjectCommonException.class)
  public void testCheckReadOnlyAttributesAbsent_ReadOnlyFieldPresent() {
    testData.put("field1", "value1");
    testData.put("id", "12345");

    validator.checkReadOnlyAttributesAbsent(testData, "id");
  }

  @Test(expected = ProjectCommonException.class)
  public void testCheckReadOnlyAttributesAbsent_MultipleReadOnlyFields() {
    testData.put("field1", "value1");
    testData.put("createdDate", "2025-01-01");
    testData.put("updatedDate", "2025-01-02");

    validator.checkReadOnlyAttributesAbsent(testData, "createdDate", "updatedDate");
  }

  @Test(expected = ProjectCommonException.class)
  public void testCheckReadOnlyAttributesAbsent_EmptyMap() {
    validator.checkReadOnlyAttributesAbsent(new HashMap<>(), "id");
  }

  @Test
  public void testCheckReadOnlyAttributesAbsent_ExceptionCode() {
    testData.put("id", "12345");

    try {
      validator.checkReadOnlyAttributesAbsent(testData, "id");
      fail("Should throw exception");
    } catch (ProjectCommonException e) {
      assertEquals(ResponseCode.unupdatableField.getErrorCode(), e.getErrorCode());
    }
  }

  // =============================================
  // checkForFieldsNotAllowed Tests
  // =============================================

  @Test
  public void testCheckForFieldsNotAllowed_NoDisallowedFields() {
    testData.put("field1", "value1");
    testData.put("field2", "value2");

    List<String> disallowedFields = Arrays.asList("field3", "field4");
    validator.checkForFieldsNotAllowed(testData, disallowedFields);
  }

  @Test(expected = ProjectCommonException.class)
  public void testCheckForFieldsNotAllowed_SingleDisallowedField() {
    testData.put("field1", "value1");
    testData.put("fieldX", "valueX");

    List<String> disallowedFields = Arrays.asList("fieldX");
    validator.checkForFieldsNotAllowed(testData, disallowedFields);
  }

  @Test(expected = ProjectCommonException.class)
  public void testCheckForFieldsNotAllowed_MultipleDisallowedFields() {
    testData.put("field1", "value1");
    testData.put("fieldX", "valueX");
    testData.put("fieldY", "valueY");

    List<String> disallowedFields = Arrays.asList("fieldX", "fieldY");
    validator.checkForFieldsNotAllowed(testData, disallowedFields);
  }

  @Test
  public void testCheckForFieldsNotAllowed_ExceptionCode() {
    testData.put("disallowed", "value");

    try {
      validator.checkForFieldsNotAllowed(testData, Arrays.asList("disallowed"));
      fail("Should throw exception");
    } catch (ProjectCommonException e) {
      assertEquals(ResponseCode.invalidRequestParameter.getErrorCode(), e.getErrorCode());
    }
  }

  // =============================================
  // validateListParam Tests
  // =============================================

  @Test
  public void testValidateListParam_FieldIsValidList() {
    testData.put("items", Arrays.asList("item1", "item2"));

    validator.validateListParam(testData, "items");
  }

  @Test(expected = ProjectCommonException.class)
  public void testValidateListParam_FieldIsNotList() {
    testData.put("items", "not a list");

    validator.validateListParam(testData, "items");
  }

  @Test
  public void testValidateListParam_FieldIsNull() {
    testData.put("items", null);

    // Null value is not considered as a list, but field exists with null
    // Should not throw exception because field is null (checked with field instanceof List)
    validator.validateListParam(testData, "items");
  }

  @Test
  public void testValidateListParam_FieldNotPresent() {
    // Field not present, should not throw exception
    validator.validateListParam(testData, "items");
  }

  @Test
  public void testValidateListParam_ExceptionCode() {
    testData.put("items", "not a list");

    try {
      validator.validateListParam(testData, "items");
      fail("Should throw exception");
    } catch (ProjectCommonException e) {
      assertEquals(ResponseCode.dataTypeError.getErrorCode(), e.getErrorCode());
    }
  }

  // =============================================
  // validateDateParam Tests
  // =============================================

  @Test
  public void testValidateDateParam_ValidDate() {
    // Should not throw exception
    validator.validateDateParam("2025-01-15");
  }

  @Test(expected = ProjectCommonException.class)
  public void testValidateDateParam_InvalidDateFormat() {
    validator.validateDateParam("15-01-2025");
  }

  @Test(expected = ProjectCommonException.class)
  public void testValidateDateParam_InvalidDate() {
    validator.validateDateParam("2025-13-45");
  }

  @Test
  public void testValidateDateParam_BlankDate() {
    // Blank date should not throw exception
    validator.validateDateParam("");
  }

  @Test
  public void testValidateDateParam_NullDate() {
    // Null date should not throw exception
    validator.validateDateParam(null);
  }

  @Test
  public void testValidateDateParam_ExceptionCode() {
    try {
      validator.validateDateParam("invalid");
      fail("Should throw exception");
    } catch (ProjectCommonException e) {
      assertEquals(ResponseCode.dateFormatError.getErrorCode(), e.getErrorCode());
    }
  }

  // =============================================
  // validateSearchRequest Tests
  // =============================================

  @Test
  public void testValidateSearchRequest_WithValidFilters() {
    Request request = new Request();
    request.put(JsonKey.FILTERS, new HashMap<>());

    // Should not throw exception
    validator.validateSearchRequest(request);
  }

  @Test(expected = ProjectCommonException.class)
  public void testValidateSearchRequest_WithoutFilters() {
    Request request = new Request();

    validator.validateSearchRequest(request);
  }

  @Test(expected = ProjectCommonException.class)
  public void testValidateSearchRequest_FiltersNotMap() {
    Request request = new Request();
    request.put(JsonKey.FILTERS, "not a map");

    validator.validateSearchRequest(request);
  }

  @Test
  public void testValidateSearchRequest_WithValidFields() {
    Request request = new Request();
    request.put(JsonKey.FILTERS, new HashMap<>());
    request.put(JsonKey.FIELDS, Arrays.asList("field1", "field2"));

    // Should not throw exception
    validator.validateSearchRequest(request);
  }

  @Test(expected = ProjectCommonException.class)
  public void testValidateSearchRequest_FieldsNotList() {
    Request request = new Request();
    request.put(JsonKey.FILTERS, new HashMap<>());
    request.put(JsonKey.FIELDS, "not a list");

    validator.validateSearchRequest(request);
  }

  @Test(expected = ProjectCommonException.class)
  public void testValidateSearchRequest_FieldsContainNonString() {
    Request request = new Request();
    request.put(JsonKey.FILTERS, new HashMap<>());
    request.put(JsonKey.FIELDS, Arrays.asList("field1", 123));

    validator.validateSearchRequest(request);
  }

  @Test
  public void testValidateSearchRequest_FiltersExceptionCode() {
    Request request = new Request();

    try {
      validator.validateSearchRequest(request);
      fail("Should throw exception");
    } catch (ProjectCommonException e) {
      assertEquals(ResponseCode.mandatoryParamsMissing.getErrorCode(), e.getErrorCode());
    }
  }

  // =============================================
  // validateSearchRequest with Filter Values Tests
  // =============================================

  @Test(expected = ProjectCommonException.class)
  public void testValidateSearchRequest_FilterWithNullKey() {
    Request request = new Request();
    Map<String, Object> filters = new HashMap<>();
    filters.put(null, "value");
    request.put(JsonKey.FILTERS, filters);

    validator.validateSearchRequest(request);
  }

  @Test(expected = ProjectCommonException.class)
  public void testValidateSearchRequest_FilterWithBlankStringValue() {
    Request request = new Request();
    Map<String, Object> filters = new HashMap<>();
    filters.put("key", "");
    request.put(JsonKey.FILTERS, filters);

    validator.validateSearchRequest(request);
  }

  @Test
  public void testValidateSearchRequest_FilterWithValidListValue() {
    Request request = new Request();
    Map<String, Object> filters = new HashMap<>();
    filters.put("key", Arrays.asList("value1", "value2"));
    request.put(JsonKey.FILTERS, filters);

    // Should not throw exception
    validator.validateSearchRequest(request);
  }

  @Test(expected = ProjectCommonException.class)
  public void testValidateSearchRequest_FilterWithNullListElement() {
    Request request = new Request();
    Map<String, Object> filters = new HashMap<>();
    filters.put("key", Arrays.asList("value1", null));
    request.put(JsonKey.FILTERS, filters);

    validator.validateSearchRequest(request);
  }

  @Test
  public void testValidateSearchRequest_FilterWithValidMapValue() {
    Request request = new Request();
    Map<String, Object> filters = new HashMap<>();
    Map<String, Object> nestedMap = new HashMap<>();
    nestedMap.put("nested", "value");
    filters.put("key", nestedMap);
    request.put(JsonKey.FILTERS, filters);

    // Should not throw exception
    validator.validateSearchRequest(request);
  }

  @Test(expected = ProjectCommonException.class)
  public void testValidateSearchRequest_FilterWithNullMapValue() {
    Request request = new Request();
    Map<String, Object> filters = new HashMap<>();
    Map<String, Object> nestedMap = new HashMap<>();
    nestedMap.put("nested", null);
    filters.put("key", nestedMap);
    request.put(JsonKey.FILTERS, filters);

    validator.validateSearchRequest(request);
  }

  // =============================================
  // validateEmail Tests
  // =============================================

  @Test
  public void testValidateEmail_ValidEmail() {
    // Should not throw exception
    validator.validateEmail("test@example.com");
  }

  @Test(expected = ProjectCommonException.class)
  public void testValidateEmail_InvalidEmail_NoAt() {
    validator.validateEmail("testemail.com");
  }

  @Test(expected = ProjectCommonException.class)
  public void testValidateEmail_InvalidEmail_NoDomain() {
    validator.validateEmail("test@");
  }

  @Test(expected = ProjectCommonException.class)
  public void testValidateEmail_InvalidEmail_NoTLD() {
    validator.validateEmail("test@domain");
  }

  @Test(expected = ProjectCommonException.class)
  public void testValidateEmail_InvalidEmail_BlankEmail() {
    validator.validateEmail("");
  }

  @Test
  public void testValidateEmail_ExceptionCode() {
    try {
      validator.validateEmail("invalid");
      fail("Should throw exception");
    } catch (ProjectCommonException e) {
      assertEquals(ResponseCode.emailFormatError.getErrorCode(), e.getErrorCode());
    }
  }

  // =============================================
  // validatePhone Tests
  // =============================================

  @Test
  public void testValidatePhone_ValidPhone() {
    // Should not throw exception
    validator.validatePhone("9876543210");
  }

  @Test(expected = ProjectCommonException.class)
  public void testValidatePhone_InvalidPhone() {
    validator.validatePhone("invalid");
  }

  @Test(expected = ProjectCommonException.class)
  public void testValidatePhone_BlankPhone() {
    validator.validatePhone("");
  }

  @Test
  public void testValidatePhone_ExceptionCode() {
    try {
      validator.validatePhone("notaphone");
      fail("Should throw exception");
    } catch (ProjectCommonException e) {
      assertEquals(ResponseCode.phoneNoFormatError.getErrorCode(), e.getErrorCode());
    }
  }

  // =============================================
  // validateUserId Tests
  // =============================================

  @Test
  public void testValidateUserId_MatchingIds() {
    Request request = new Request();
    request.put(JsonKey.USER_ID, "user123");
    request.setContext(new HashMap<String, Object>() {
      {
        put(JsonKey.REQUESTED_BY, "user123");
      }
    });

    // Should not throw exception
    BaseRequestValidator.validateUserId(request, JsonKey.USER_ID);
  }

  @Test(expected = ProjectCommonException.class)
  public void testValidateUserId_MismatchingIds() {
    Request request = new Request();
    request.put(JsonKey.USER_ID, "user123");
    request.setContext(new HashMap<String, Object>() {
      {
        put(JsonKey.REQUESTED_BY, "user456");
      }
    });

    BaseRequestValidator.validateUserId(request, JsonKey.USER_ID);
  }

  @Test
  public void testValidateUserId_ExceptionCode() {
    Request request = new Request();
    request.put(JsonKey.USER_ID, "user123");
    request.setContext(new HashMap<String, Object>() {
      {
        put(JsonKey.REQUESTED_BY, "user456");
      }
    });

    try {
      BaseRequestValidator.validateUserId(request, JsonKey.USER_ID);
      fail("Should throw exception");
    } catch (ProjectCommonException e) {
      assertEquals(ResponseCode.invalidParameterValue.getErrorCode(), e.getErrorCode());
    }
  }

  // =============================================
  // validateParam Tests
  // =============================================

  @Test
  public void testValidateParam_ValidValue() {
    // Should not throw exception
    validator.validateParam("value", ResponseCode.mandatoryParamsMissing);
  }

  @Test(expected = ProjectCommonException.class)
  public void testValidateParam_BlankValue() {
    validator.validateParam("", ResponseCode.mandatoryParamsMissing);
  }

  @Test(expected = ProjectCommonException.class)
  public void testValidateParam_NullValue() {
    validator.validateParam(null, ResponseCode.mandatoryParamsMissing);
  }

  @Test
  public void testValidateParam_WithArgument_ValidValue() {
    // Should not throw exception
    validator.validateParam("value", ResponseCode.mandatoryParamsMissing, "fieldName");
  }

  @Test(expected = ProjectCommonException.class)
  public void testValidateParam_WithArgument_BlankValue() {
    validator.validateParam("", ResponseCode.mandatoryParamsMissing, "fieldName");
  }

  @Test
  public void testValidateParam_ExceptionCode() {
    try {
      validator.validateParam("", ResponseCode.mandatoryParamsMissing);
      fail("Should throw exception");
    } catch (ProjectCommonException e) {
      assertEquals(ResponseCode.mandatoryParamsMissing.getErrorCode(), e.getErrorCode());
    }
  }

  // =============================================
  // validateParamValue Tests
  // =============================================

  @Test
  public void testValidateParamValue_ValidValue() {
    // Should not throw exception
    validator.validateParamValue("value", ResponseCode.mandatoryParamsMissing, "fieldName");
  }

  @Test(expected = ProjectCommonException.class)
  public void testValidateParamValue_BlankValue() {
    validator.validateParamValue("", ResponseCode.mandatoryParamsMissing, "fieldName");
  }

  @Test
  public void testValidateParamValue_ExceptionMessage() {
    try {
      validator.validateParamValue("", ResponseCode.mandatoryParamsMissing, "fieldName");
      fail("Should throw exception");
    } catch (ProjectCommonException e) {
      assertEquals(ResponseCode.mandatoryParamsMissing.getErrorCode(), e.getErrorCode());
      assertTrue(e.getMessage().contains("fieldName"));
    }
  }

  // =============================================
  // checkMandatoryHeadersPresent Tests
  // =============================================

  @Test
  public void testCheckMandatoryHeadersPresent_AllHeadersPresent() {
    Map<String, String[]> headers = new HashMap<>();
    headers.put("Authorization", new String[]{"Bearer token"});
    headers.put("Content-Type", new String[]{"application/json"});

    // Should not throw exception
    validator.checkMandatoryHeadersPresent(headers, "Authorization", "Content-Type");
  }

  @Test(expected = ProjectCommonException.class)
  public void testCheckMandatoryHeadersPresent_MissingHeader() {
    Map<String, String[]> headers = new HashMap<>();
    headers.put("Authorization", new String[]{"Bearer token"});

    validator.checkMandatoryHeadersPresent(headers, "Authorization", "Content-Type");
  }

  @Test(expected = ProjectCommonException.class)
  public void testCheckMandatoryHeadersPresent_EmptyHeaderArray() {
    Map<String, String[]> headers = new HashMap<>();
    headers.put("Authorization", new String[]{});

    validator.checkMandatoryHeadersPresent(headers, "Authorization");
  }

  @Test(expected = ProjectCommonException.class)
  public void testCheckMandatoryHeadersPresent_EmptyHeaderMap() {
    validator.checkMandatoryHeadersPresent(new HashMap<>(), "Authorization");
  }

  @Test
  public void testCheckMandatoryHeadersPresent_ExceptionCode() {
    Map<String, String[]> headers = new HashMap<>();

    try {
      validator.checkMandatoryHeadersPresent(headers, "Authorization");
      fail("Should throw exception");
    } catch (ProjectCommonException e) {
      // Empty map is treated as invalidRequestData before checking headers
      assertTrue(
          e.getErrorCode().equals(ResponseCode.invalidRequestData.getErrorCode())
              || e.getErrorCode()
                  .equals(ResponseCode.mandatoryHeadersMissing.getErrorCode()));
    }
  }

  // =============================================
  // createExceptionByResponseCode Tests
  // =============================================

  @Test
  public void testCreateExceptionByResponseCode_WithValidCode() {
    ProjectCommonException exception =
        validator.createExceptionByResponseCode(
            ResponseCode.mandatoryParamsMissing, ResponseCode.CLIENT_ERROR.getResponseCode());

    assertNotNull(exception);
    assertEquals(
        ResponseCode.mandatoryParamsMissing.getErrorCode(), exception.getErrorCode());
  }

  @Test
  public void testCreateExceptionByResponseCode_WithNullCode() {
    ProjectCommonException exception =
        validator.createExceptionByResponseCode(null, ResponseCode.CLIENT_ERROR.getResponseCode());

    assertNotNull(exception);
    assertEquals(ResponseCode.invalidData.getErrorCode(), exception.getErrorCode());
  }

  @Test
  public void testCreateExceptionByResponseCode_WithArgument() {
    ProjectCommonException exception =
        validator.createExceptionByResponseCode(
            ResponseCode.mandatoryParamsMissing,
            ResponseCode.CLIENT_ERROR.getResponseCode(),
            "testField");

    assertNotNull(exception);
    assertEquals(
        ResponseCode.mandatoryParamsMissing.getErrorCode(), exception.getErrorCode());
    assertTrue(exception.getMessage().contains("testField"));
  }
}
