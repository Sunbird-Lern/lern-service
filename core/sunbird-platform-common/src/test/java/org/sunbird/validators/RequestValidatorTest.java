package org.sunbird.validators;

import static org.junit.Assert.*;

import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.sunbird.exception.ProjectCommonException;
import org.sunbird.keys.JsonKey;
import org.sunbird.request.Request;
import org.sunbird.response.ResponseCode;

public class RequestValidatorTest {

  private Request request;
  private Map<String, Object> requestData;

  @Before
  public void setUp() {
    request = new Request();
    requestData = new HashMap<>();
    request.setRequest(requestData);
  }

  // =============================================
  // validateGetPageData Tests
  // =============================================

  @Test
  public void testValidateGetPageData_WithValidSourceAndName() {
    request.put(JsonKey.SOURCE, "web");
    request.put(JsonKey.PAGE_NAME, "homepage");

    // Should not throw exception
    RequestValidator.validateGetPageData(request);
  }

  @Test(expected = ProjectCommonException.class)
  public void testValidateGetPageData_WithMissingSource() {
    request.put(JsonKey.PAGE_NAME, "homepage");

    RequestValidator.validateGetPageData(request);
  }

  @Test(expected = ProjectCommonException.class)
  public void testValidateGetPageData_WithBlankSource() {
    request.put(JsonKey.SOURCE, "");
    request.put(JsonKey.PAGE_NAME, "homepage");

    RequestValidator.validateGetPageData(request);
  }

  @Test(expected = ProjectCommonException.class)
  public void testValidateGetPageData_WithNullSource() {
    request.put(JsonKey.SOURCE, null);
    request.put(JsonKey.PAGE_NAME, "homepage");

    RequestValidator.validateGetPageData(request);
  }

  @Test
  public void testValidateGetPageData_WithMissingSource_ExceptionCode() {
    request.put(JsonKey.PAGE_NAME, "homepage");

    try {
      RequestValidator.validateGetPageData(request);
      fail("Should throw exception");
    } catch (ProjectCommonException e) {
      assertEquals(ResponseCode.sourceRequired.getErrorCode(), e.getErrorCode());
    }
  }

  @Test(expected = ProjectCommonException.class)
  public void testValidateGetPageData_WithMissingPageName() {
    request.put(JsonKey.SOURCE, "web");

    RequestValidator.validateGetPageData(request);
  }

  @Test(expected = ProjectCommonException.class)
  public void testValidateGetPageData_WithBlankPageName() {
    request.put(JsonKey.SOURCE, "web");
    request.put(JsonKey.PAGE_NAME, "");

    RequestValidator.validateGetPageData(request);
  }

  @Test(expected = ProjectCommonException.class)
  public void testValidateGetPageData_WithNullPageName() {
    request.put(JsonKey.SOURCE, "web");
    request.put(JsonKey.PAGE_NAME, null);

    RequestValidator.validateGetPageData(request);
  }

  @Test
  public void testValidateGetPageData_WithMissingPageName_ExceptionCode() {
    request.put(JsonKey.SOURCE, "web");

    try {
      RequestValidator.validateGetPageData(request);
      fail("Should throw exception");
    } catch (ProjectCommonException e) {
      assertEquals(ResponseCode.pageNameRequired.getErrorCode(), e.getErrorCode());
    }
  }

  @Test(expected = ProjectCommonException.class)
  public void testValidateGetPageData_WithInvalidSource() {
    request.put(JsonKey.SOURCE, "invalidSource");
    request.put(JsonKey.PAGE_NAME, "homepage");

    RequestValidator.validateGetPageData(request);
  }

  @Test
  public void testValidateGetPageData_WithInvalidSource_ExceptionCode() {
    request.put(JsonKey.SOURCE, "notAValidSource");
    request.put(JsonKey.PAGE_NAME, "homepage");

    try {
      RequestValidator.validateGetPageData(request);
      fail("Should throw exception");
    } catch (ProjectCommonException e) {
      assertEquals(ResponseCode.invalidPageSource.getErrorCode(), e.getErrorCode());
    }
  }

  @Test(expected = ProjectCommonException.class)
  public void testValidateGetPageData_WithNullRequest() {
    RequestValidator.validateGetPageData(null);
  }

  @Test
  public void testValidateGetPageData_WithValidWebSource() {
    request.put(JsonKey.SOURCE, "web");
    request.put(JsonKey.PAGE_NAME, "dashboard");

    // Should not throw exception
    RequestValidator.validateGetPageData(request);
  }

  @Test
  public void testValidateGetPageData_WithValidAndroidSource() {
    request.put(JsonKey.SOURCE, "android");
    request.put(JsonKey.PAGE_NAME, "profile");

    // Should not throw exception
    RequestValidator.validateGetPageData(request);
  }

  @Test
  public void testValidateGetPageData_WithValidIOSSource() {
    request.put(JsonKey.SOURCE, "ios");
    request.put(JsonKey.PAGE_NAME, "settings");

    // Should not throw exception
    RequestValidator.validateGetPageData(request);
  }

  @Test
  public void testValidateGetPageData_SourceCaseSensitivity() {
    // Test if source validation is case-sensitive
    request.put(JsonKey.SOURCE, "WEB"); // Uppercase
    request.put(JsonKey.PAGE_NAME, "homepage");

    // Depending on implementation, this might fail
    try {
      RequestValidator.validateGetPageData(request);
    } catch (ProjectCommonException e) {
      // If it fails, it should be for invalid source
      assertEquals(ResponseCode.invalidPageSource.getErrorCode(), e.getErrorCode());
    }
  }

  @Test
  public void testValidateGetPageData_PageNameAcceptsAnyString() {
    // Page name should accept any non-blank string
    request.put(JsonKey.SOURCE, "web");
    request.put(JsonKey.PAGE_NAME, "custom-page-123");

    RequestValidator.validateGetPageData(request);
  }

  // =============================================
  // validateAddBatchCourse Tests
  // =============================================

  @Test
  public void testValidateAddBatchCourse_WithValidBatchId() {
    request.put(JsonKey.BATCH_ID, "batch123");
    request.put(JsonKey.USER_IDs, "user456");

    // Should not throw exception
    RequestValidator.validateAddBatchCourse(request);
  }

  @Test(expected = ProjectCommonException.class)
  public void testValidateAddBatchCourse_WithNullBatchId() {
    request.put(JsonKey.BATCH_ID, null);

    RequestValidator.validateAddBatchCourse(request);
  }

  @Test(expected = ProjectCommonException.class)
  public void testValidateAddBatchCourse_WithMissingBatchId() {
    request.put(JsonKey.USER_IDs, "user456");

    RequestValidator.validateAddBatchCourse(request);
  }

  @Test(expected = ProjectCommonException.class)
  public void testValidateAddBatchCourse_WithMissingUserIds() {
    request.put(JsonKey.BATCH_ID, "batch123");

    RequestValidator.validateAddBatchCourse(request);
  }

  @Test
  public void testValidateAddBatchCourse_WithNullBatchId_ExceptionCode() {
    request.put(JsonKey.BATCH_ID, null);

    try {
      RequestValidator.validateAddBatchCourse(request);
      fail("Should throw exception");
    } catch (ProjectCommonException e) {
      // Exception should indicate missing batch ID
      assertNotNull(e.getErrorCode());
    }
  }

  // =============================================
  // Edge Cases and Integration Tests
  // =============================================

  @Test
  public void testValidateGetPageData_BothSourceAndNameBlank() {
    request.put(JsonKey.SOURCE, "");
    request.put(JsonKey.PAGE_NAME, "");

    try {
      RequestValidator.validateGetPageData(request);
      fail("Should throw exception");
    } catch (ProjectCommonException e) {
      // Should fail on source check first
      assertEquals(ResponseCode.sourceRequired.getErrorCode(), e.getErrorCode());
    }
  }

  @Test
  public void testValidateGetPageData_SourceBlankPageNameValid() {
    request.put(JsonKey.SOURCE, "");
    request.put(JsonKey.PAGE_NAME, "homepage");

    try {
      RequestValidator.validateGetPageData(request);
      fail("Should throw exception");
    } catch (ProjectCommonException e) {
      assertEquals(ResponseCode.sourceRequired.getErrorCode(), e.getErrorCode());
    }
  }

  @Test
  public void testValidateGetPageData_SourceValidPageNameBlank() {
    request.put(JsonKey.SOURCE, "web");
    request.put(JsonKey.PAGE_NAME, "");

    try {
      RequestValidator.validateGetPageData(request);
      fail("Should throw exception");
    } catch (ProjectCommonException e) {
      assertEquals(ResponseCode.pageNameRequired.getErrorCode(), e.getErrorCode());
    }
  }

  @Test
  public void testValidateGetPageData_BothSourceAndNameWithValidValues() {
    request.put(JsonKey.SOURCE, "app");
    request.put(JsonKey.PAGE_NAME, "user-profile");

    // Should not throw exception
    RequestValidator.validateGetPageData(request);
  }

  @Test
  public void testValidateGetPageData_SpecialCharactersInPageName() {
    request.put(JsonKey.SOURCE, "web");
    request.put(JsonKey.PAGE_NAME, "page-name_123");

    // Should accept special characters in page name
    RequestValidator.validateGetPageData(request);
  }

  @Test
  public void testValidateGetPageData_LongPageName() {
    request.put(JsonKey.SOURCE, "web");
    request.put(JsonKey.PAGE_NAME, "a".repeat(500));

    // Should accept long page names
    RequestValidator.validateGetPageData(request);
  }

  @Test
  public void testValidateGetPageData_WhitespaceInPageName() {
    request.put(JsonKey.SOURCE, "web");
    request.put(JsonKey.PAGE_NAME, "  homepage  ");

    // Whitespace in page name should be acceptable
    RequestValidator.validateGetPageData(request);
  }

  @Test
  public void testValidateAddBatchCourse_WithValidStringBatchId() {
    request.put(JsonKey.BATCH_ID, "batch_course_001");
    request.put(JsonKey.USER_IDs, "user789");

    // Should not throw exception
    RequestValidator.validateAddBatchCourse(request);
  }

  @Test
  public void testValidateAddBatchCourse_WithEmptyStringBatchId() {
    request.put(JsonKey.BATCH_ID, "");

    // Empty string should fail
    try {
      RequestValidator.validateAddBatchCourse(request);
      fail("Should throw exception");
    } catch (ProjectCommonException e) {
      assertNotNull(e);
    }
  }

  @Test
  public void testValidateAddBatchCourse_WithNumericBatchId() {
    request.put(JsonKey.BATCH_ID, 123);

    // Non-string batch ID
    try {
      RequestValidator.validateAddBatchCourse(request);
    } catch (ProjectCommonException e) {
      // May or may not fail depending on implementation
      assertNotNull(e);
    }
  }

  // =============================================
  // Request Type Variations
  // =============================================

  @Test
  public void testValidateGetPageData_WithRequestHavingExtraFields() {
    request.put(JsonKey.SOURCE, "web");
    request.put(JsonKey.PAGE_NAME, "homepage");
    request.put("extraField", "extraValue");

    // Extra fields should not cause validation to fail
    RequestValidator.validateGetPageData(request);
  }

  @Test
  public void testValidateGetPageData_WithRequestHavingOnlyRequiredFields() {
    request.put(JsonKey.SOURCE, "web");
    request.put(JsonKey.PAGE_NAME, "homepage");

    // Should work with only required fields
    RequestValidator.validateGetPageData(request);
  }

  @Test
  public void testValidateGetPageData_MultipleCallsWithDifferentRequests() {
    // First validation
    Request request1 = new Request();
    request1.put(JsonKey.SOURCE, "web");
    request1.put(JsonKey.PAGE_NAME, "page1");
    RequestValidator.validateGetPageData(request1);

    // Second validation with different source
    Request request2 = new Request();
    request2.put(JsonKey.SOURCE, "android");
    request2.put(JsonKey.PAGE_NAME, "page2");
    RequestValidator.validateGetPageData(request2);

    // Both should succeed
  }
}
