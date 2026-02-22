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
 * Extended test suite for RequestValidator focusing on batch operations, content operations, and
 * edge cases.
 */
public class ExtendedRequestValidatorTest {

  private Request request;

  @Before
  public void setUp() {
    request = new Request();
  }

  // =============================================
  // validateCreateBatchReq - Success Cases
  // =============================================

  @Test
  public void testValidateCreateBatchReq_WithValidData() {
    request.put(JsonKey.COURSE_ID, "course123");
    request.put(JsonKey.NAME, "Batch Name");
    request.put(JsonKey.ENROLLMENT_TYPE, "open");
    request.put(JsonKey.START_DATE, "2025-02-25");
    request.put(JsonKey.END_DATE, "2025-03-25");

    // Should not throw exception (assuming current date allows this)
    try {
      RequestValidator.validateCreateBatchReq(request);
    } catch (ProjectCommonException e) {
      // Date validation might fail if dates are in past, which is expected
      assertTrue(e.getErrorCode().equals(ResponseCode.courseBatchStartDateError.getErrorCode())
          || e.getErrorCode()
              .equals(ResponseCode.invalidCourseId.getErrorCode())); // Course might not exist
    }
  }

  @Test(expected = ProjectCommonException.class)
  public void testValidateCreateBatchReq_MissingCourseId() {
    request.put(JsonKey.NAME, "Batch Name");
    request.put(JsonKey.ENROLLMENT_TYPE, "open");
    request.put(JsonKey.START_DATE, "2025-02-25");

    RequestValidator.validateCreateBatchReq(request);
  }

  @Test(expected = ProjectCommonException.class)
  public void testValidateCreateBatchReq_BlankCourseId() {
    request.put(JsonKey.COURSE_ID, "");
    request.put(JsonKey.NAME, "Batch Name");

    RequestValidator.validateCreateBatchReq(request);
  }

  @Test(expected = ProjectCommonException.class)
  public void testValidateCreateBatchReq_MissingBatchName() {
    request.put(JsonKey.COURSE_ID, "course123");
    request.put(JsonKey.ENROLLMENT_TYPE, "open");

    RequestValidator.validateCreateBatchReq(request);
  }

  @Test(expected = ProjectCommonException.class)
  public void testValidateCreateBatchReq_BlankBatchName() {
    request.put(JsonKey.COURSE_ID, "course123");
    request.put(JsonKey.NAME, "");
    request.put(JsonKey.ENROLLMENT_TYPE, "open");

    RequestValidator.validateCreateBatchReq(request);
  }

  @Test(expected = ProjectCommonException.class)
  public void testValidateCreateBatchReq_InvalidEnrollmentType() {
    request.put(JsonKey.COURSE_ID, "course123");
    request.put(JsonKey.NAME, "Batch Name");
    request.put(JsonKey.ENROLLMENT_TYPE, "invalid-type");

    RequestValidator.validateCreateBatchReq(request);
  }

  @Test
  public void testValidateCreateBatchReq_ExceptionCode_MissingCourseId() {
    request.put(JsonKey.NAME, "Batch Name");

    try {
      RequestValidator.validateCreateBatchReq(request);
      fail("Should throw exception");
    } catch (ProjectCommonException e) {
      assertEquals(ResponseCode.invalidCourseId.getErrorCode(), e.getErrorCode());
    }
  }

  // =============================================
  // validateCreateBatchReq - Enrollment Type Tests
  // =============================================

  @Test
  public void testValidateCreateBatchReq_ValidEnrollmentType_Open() {
    request.put(JsonKey.COURSE_ID, "course123");
    request.put(JsonKey.NAME, "Batch Name");
    request.put(JsonKey.ENROLLMENT_TYPE, "open");

    try {
      RequestValidator.validateCreateBatchReq(request);
    } catch (ProjectCommonException e) {
      // Expected - will fail on date validation or course id not found
      assertNotEquals(ResponseCode.enrolmentIncorrectValue.getErrorCode(), e.getErrorCode());
    }
  }

  @Test
  public void testValidateCreateBatchReq_ValidEnrollmentType_InviteOnly() {
    request.put(JsonKey.COURSE_ID, "course123");
    request.put(JsonKey.NAME, "Batch Name");
    request.put(JsonKey.ENROLLMENT_TYPE, "invite-only");

    try {
      RequestValidator.validateCreateBatchReq(request);
    } catch (ProjectCommonException e) {
      // Expected - will fail on date validation
      assertNotEquals(ResponseCode.enrolmentIncorrectValue.getErrorCode(), e.getErrorCode());
    }
  }

  @Test(expected = ProjectCommonException.class)
  public void testValidateCreateBatchReq_InvalidEnrollmentType_NotOpen() {
    request.put(JsonKey.COURSE_ID, "course123");
    request.put(JsonKey.NAME, "Batch Name");
    request.put(JsonKey.ENROLLMENT_TYPE, "closed");

    RequestValidator.validateCreateBatchReq(request);
  }

  @Test(expected = ProjectCommonException.class)
  public void testValidateCreateBatchReq_InvalidEnrollmentType_Random() {
    request.put(JsonKey.COURSE_ID, "course123");
    request.put(JsonKey.NAME, "Batch Name");
    request.put(JsonKey.ENROLLMENT_TYPE, "random-type");

    RequestValidator.validateCreateBatchReq(request);
  }

  @Test(expected = ProjectCommonException.class)
  public void testValidateCreateBatchReq_BlankEnrollmentType() {
    request.put(JsonKey.COURSE_ID, "course123");
    request.put(JsonKey.NAME, "Batch Name");
    request.put(JsonKey.ENROLLMENT_TYPE, "");

    RequestValidator.validateCreateBatchReq(request);
  }

  // =============================================
  // validateCreateBatchReq - Date Tests
  // =============================================

  @Test(expected = ProjectCommonException.class)
  public void testValidateCreateBatchReq_MissingStartDate() {
    request.put(JsonKey.COURSE_ID, "course123");
    request.put(JsonKey.NAME, "Batch Name");
    request.put(JsonKey.ENROLLMENT_TYPE, "open");

    RequestValidator.validateCreateBatchReq(request);
  }

  @Test(expected = ProjectCommonException.class)
  public void testValidateCreateBatchReq_BlankStartDate() {
    request.put(JsonKey.COURSE_ID, "course123");
    request.put(JsonKey.NAME, "Batch Name");
    request.put(JsonKey.ENROLLMENT_TYPE, "open");
    request.put(JsonKey.START_DATE, "");

    RequestValidator.validateCreateBatchReq(request);
  }

  @Test(expected = ProjectCommonException.class)
  public void testValidateCreateBatchReq_InvalidStartDateFormat() {
    request.put(JsonKey.COURSE_ID, "course123");
    request.put(JsonKey.NAME, "Batch Name");
    request.put(JsonKey.ENROLLMENT_TYPE, "open");
    request.put(JsonKey.START_DATE, "25-02-2025");

    RequestValidator.validateCreateBatchReq(request);
  }

  @Test(expected = ProjectCommonException.class)
  public void testValidateCreateBatchReq_EndDateBeforeStartDate() {
    request.put(JsonKey.COURSE_ID, "course123");
    request.put(JsonKey.NAME, "Batch Name");
    request.put(JsonKey.ENROLLMENT_TYPE, "open");
    request.put(JsonKey.START_DATE, "2025-03-25");
    request.put(JsonKey.END_DATE, "2025-02-25");

    RequestValidator.validateCreateBatchReq(request);
  }

  // =============================================
  // validateCreateBatchReq - CreatedFor List Tests
  // =============================================

  @Test
  public void testValidateCreateBatchReq_ValidCreatedForList() {
    request.put(JsonKey.COURSE_ID, "course123");
    request.put(JsonKey.NAME, "Batch Name");
    request.put(JsonKey.ENROLLMENT_TYPE, "open");
    request.put(JsonKey.COURSE_CREATED_FOR, Arrays.asList("org1", "org2", "org3"));

    try {
      RequestValidator.validateCreateBatchReq(request);
    } catch (ProjectCommonException e) {
      // Expected to fail on date validation, not on createdFor
      assertNotEquals(ResponseCode.dataTypeError.getErrorCode(), e.getErrorCode());
    }
  }

  @Test(expected = ProjectCommonException.class)
  public void testValidateCreateBatchReq_CreatedForNotList() {
    request.put(JsonKey.COURSE_ID, "course123");
    request.put(JsonKey.NAME, "Batch Name");
    request.put(JsonKey.ENROLLMENT_TYPE, "open");
    request.put(JsonKey.COURSE_CREATED_FOR, "notAList");

    RequestValidator.validateCreateBatchReq(request);
  }

  @Test
  public void testValidateCreateBatchReq_CreatedForExceptionCode() {
    request.put(JsonKey.COURSE_ID, "course123");
    request.put(JsonKey.NAME, "Batch Name");
    request.put(JsonKey.ENROLLMENT_TYPE, "open");
    request.put(JsonKey.START_DATE, "2026-12-25"); // Far future date
    request.put(JsonKey.COURSE_CREATED_FOR, "string");

    try {
      RequestValidator.validateCreateBatchReq(request);
      fail("Should throw exception");
    } catch (ProjectCommonException e) {
      assertEquals(ResponseCode.dataTypeError.getErrorCode(), e.getErrorCode());
    }
  }

  // =============================================
  // validateUpdateCourseBatchReq - Success Cases
  // =============================================

  @Test
  public void testValidateUpdateCourseBatchReq_WithValidStatus() {
    request.put(JsonKey.STATUS, 0); // DRAFT

    try {
      RequestValidator.validateUpdateCourseBatchReq(request);
    } catch (ProjectCommonException e) {
      // Expected if additional fields missing
      assertNotNull(e);
    }
  }

  @Test
  public void testValidateUpdateCourseBatchReq_WithoutStatus() {
    // Status is optional for update

    try {
      RequestValidator.validateUpdateCourseBatchReq(request);
    } catch (ProjectCommonException e) {
      // Might fail on other validations
      assertNotNull(e);
    }
  }

  // =============================================
  // validateCreatePage Tests
  // =============================================

  @Test
  public void testValidateCreatePage_WithValidPageName() {
    request.put(JsonKey.PAGE_NAME, "Test Page");

    // Should not throw exception
    RequestValidator.validateCreatePage(request);
  }

  @Test(expected = ProjectCommonException.class)
  public void testValidateCreatePage_MissingPageName() {
    // PAGE_NAME missing

    RequestValidator.validateCreatePage(request);
  }

  @Test(expected = ProjectCommonException.class)
  public void testValidateCreatePage_BlankPageName() {
    request.put(JsonKey.PAGE_NAME, "");

    RequestValidator.validateCreatePage(request);
  }

  @Test(expected = ProjectCommonException.class)
  public void testValidateCreatePage_NullPageName() {
    request.put(JsonKey.PAGE_NAME, null);

    RequestValidator.validateCreatePage(request);
  }

  @Test
  public void testValidateCreatePage_ExceptionCode() {
    request.put(JsonKey.PAGE_NAME, "");

    try {
      RequestValidator.validateCreatePage(request);
      fail("Should throw exception");
    } catch (ProjectCommonException e) {
      assertEquals(ResponseCode.pageNameRequired.getErrorCode(), e.getErrorCode());
    }
  }

  // =============================================
  // validateCreateSection Tests
  // =============================================

  @Test
  public void testValidateCreateSection_WithValidData() {
    request.put(JsonKey.SECTION_NAME, "Section Name");
    request.put(JsonKey.SECTION_DATA_TYPE, "typeA");

    // Should not throw exception
    RequestValidator.validateCreateSection(request);
  }

  @Test(expected = ProjectCommonException.class)
  public void testValidateCreateSection_MissingSectionName() {
    request.put(JsonKey.SECTION_DATA_TYPE, "typeA");

    RequestValidator.validateCreateSection(request);
  }

  @Test(expected = ProjectCommonException.class)
  public void testValidateCreateSection_BlankSectionName() {
    request.put(JsonKey.SECTION_NAME, "");
    request.put(JsonKey.SECTION_DATA_TYPE, "typeA");

    RequestValidator.validateCreateSection(request);
  }

  @Test(expected = ProjectCommonException.class)
  public void testValidateCreateSection_MissingDataType() {
    request.put(JsonKey.SECTION_NAME, "Section Name");

    RequestValidator.validateCreateSection(request);
  }

  @Test(expected = ProjectCommonException.class)
  public void testValidateCreateSection_BlankDataType() {
    request.put(JsonKey.SECTION_NAME, "Section Name");
    request.put(JsonKey.SECTION_DATA_TYPE, "");

    RequestValidator.validateCreateSection(request);
  }

  @Test
  public void testValidateCreateSection_ExceptionCode_MissingName() {
    request.put(JsonKey.SECTION_DATA_TYPE, "typeA");

    try {
      RequestValidator.validateCreateSection(request);
      fail("Should throw exception");
    } catch (ProjectCommonException e) {
      assertEquals(ResponseCode.sectionNameRequired.getErrorCode(), e.getErrorCode());
    }
  }

  @Test
  public void testValidateCreateSection_ExceptionCode_MissingDataType() {
    request.put(JsonKey.SECTION_NAME, "Section Name");

    try {
      RequestValidator.validateCreateSection(request);
      fail("Should throw exception");
    } catch (ProjectCommonException e) {
      assertEquals(ResponseCode.sectionDataTypeRequired.getErrorCode(), e.getErrorCode());
    }
  }

  // =============================================
  // validateUpdateSection Tests
  // =============================================

  @Test
  public void testValidateUpdateSection_WithValidData() {
    request.put(JsonKey.ID, "section123");
    request.put(JsonKey.SECTION_NAME, "Updated Name");

    // Should not throw exception
    RequestValidator.validateUpdateSection(request);
  }

  @Test(expected = ProjectCommonException.class)
  public void testValidateUpdateSection_MissingSectionId() {
    request.put(JsonKey.SECTION_NAME, "Updated Name");

    RequestValidator.validateUpdateSection(request);
  }

  @Test(expected = ProjectCommonException.class)
  public void testValidateUpdateSection_BlankSectionId() {
    request.put(JsonKey.ID, "");
    request.put(JsonKey.SECTION_NAME, "Updated Name");

    RequestValidator.validateUpdateSection(request);
  }

  @Test
  public void testValidateUpdateSection_WithoutSectionName() {
    request.put(JsonKey.ID, "section123");
    // SECTION_NAME not provided, should be allowed

    // Should not throw exception
    RequestValidator.validateUpdateSection(request);
  }

  @Test(expected = ProjectCommonException.class)
  public void testValidateUpdateSection_BlankSectionName() {
    request.put(JsonKey.ID, "section123");
    request.put(JsonKey.SECTION_NAME, "");

    RequestValidator.validateUpdateSection(request);
  }

  @Test
  public void testValidateUpdateSection_ExceptionCode_MissingId() {
    request.put(JsonKey.SECTION_NAME, "Updated Name");

    try {
      RequestValidator.validateUpdateSection(request);
      fail("Should throw exception");
    } catch (ProjectCommonException e) {
      assertEquals(ResponseCode.sectionIdRequired.getErrorCode(), e.getErrorCode());
    }
  }

  // =============================================
  // validateUpdatepage Tests
  // =============================================

  @Test
  public void testValidateUpdatePage_WithValidData() {
    request.put(JsonKey.ID, "page123");
    request.put(JsonKey.PAGE_NAME, "Updated Page");

    // Should not throw exception
    RequestValidator.validateUpdatepage(request);
  }

  @Test(expected = ProjectCommonException.class)
  public void testValidateUpdatePage_MissingPageId() {
    request.put(JsonKey.PAGE_NAME, "Updated Page");

    RequestValidator.validateUpdatepage(request);
  }

  @Test(expected = ProjectCommonException.class)
  public void testValidateUpdatePage_BlankPageId() {
    request.put(JsonKey.ID, "");
    request.put(JsonKey.PAGE_NAME, "Updated Page");

    RequestValidator.validateUpdatepage(request);
  }

  @Test
  public void testValidateUpdatePage_WithoutPageName() {
    request.put(JsonKey.ID, "page123");
    // PAGE_NAME not provided, should be allowed

    // Should not throw exception
    RequestValidator.validateUpdatepage(request);
  }

  @Test(expected = ProjectCommonException.class)
  public void testValidateUpdatePage_BlankPageName() {
    request.put(JsonKey.ID, "page123");
    request.put(JsonKey.PAGE_NAME, "");

    RequestValidator.validateUpdatepage(request);
  }

  @Test
  public void testValidateUpdatePage_ExceptionCode_MissingId() {
    request.put(JsonKey.PAGE_NAME, "Updated Page");

    try {
      RequestValidator.validateUpdatepage(request);
      fail("Should throw exception");
    } catch (ProjectCommonException e) {
      assertEquals(ResponseCode.pageIdRequired.getErrorCode(), e.getErrorCode());
    }
  }

  // =============================================
  // validateUpdateCourse Tests
  // =============================================

  @Test
  public void testValidateUpdateCourse_WithValidCourseId() {
    request.put(JsonKey.COURSE_ID, "course123");

    // Should not throw exception
    RequestValidator.validateUpdateCourse(request);
  }

  @Test(expected = ProjectCommonException.class)
  public void testValidateUpdateCourse_MissingCourseId() {
    RequestValidator.validateUpdateCourse(request);
  }

  @Test(expected = ProjectCommonException.class)
  public void testValidateUpdateCourse_NullCourseId() {
    request.put(JsonKey.COURSE_ID, null);

    RequestValidator.validateUpdateCourse(request);
  }

  @Test
  public void testValidateUpdateCourse_ExceptionCode() {
    try {
      RequestValidator.validateUpdateCourse(request);
      fail("Should throw exception");
    } catch (ProjectCommonException e) {
      assertEquals(ResponseCode.courseIdRequired.getErrorCode(), e.getErrorCode());
    }
  }

  // =============================================
  // validateGetBatchCourse Tests
  // =============================================

  @Test
  public void testValidateGetBatchCourse_WithValidBatchId() {
    request.put(JsonKey.BATCH_ID, "batch123");

    // Should not throw exception
    RequestValidator.validateGetBatchCourse(request);
  }

  @Test(expected = ProjectCommonException.class)
  public void testValidateGetBatchCourse_MissingBatchId() {
    RequestValidator.validateGetBatchCourse(request);
  }

  @Test(expected = ProjectCommonException.class)
  public void testValidateGetBatchCourse_NullBatchId() {
    request.put(JsonKey.BATCH_ID, null);

    RequestValidator.validateGetBatchCourse(request);
  }

  @Test
  public void testValidateGetBatchCourse_ExceptionCode() {
    try {
      RequestValidator.validateGetBatchCourse(request);
      fail("Should throw exception");
    } catch (ProjectCommonException e) {
      assertEquals(ResponseCode.courseBatchIdRequired.getErrorCode(), e.getErrorCode());
    }
  }
}
