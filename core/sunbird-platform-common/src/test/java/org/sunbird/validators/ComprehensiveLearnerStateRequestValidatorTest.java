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

/**
 * Comprehensive test suite for LearnerStateRequestValidator covering all validation scenarios.
 */
public class ComprehensiveLearnerStateRequestValidatorTest {

  private LearnerStateRequestValidator validator;
  private Request request;

  @Before
  public void setUp() {
    validator = new LearnerStateRequestValidator();
    request = new Request();
  }

  // =============================================
  // validateGetContentState - Success Cases
  // =============================================

  @Test
  public void testValidateGetContentState_WithCourseIdBatchIdUserId() {
    request.put(JsonKey.COURSE_ID, "course123");
    request.put(JsonKey.BATCH_ID, "batch456");
    request.put(JsonKey.USER_ID, "user789");
    request.put(JsonKey.CONTENT_IDS, Arrays.asList("content1", "content2"));

    // Should not throw exception
    validator.validateGetContentState(request);
  }

  @Test
  public void testValidateGetContentState_WithCollectionIdBatchIdUserId() {
    request.put(JsonKey.COLLECTION_ID, "collection123");
    request.put(JsonKey.BATCH_ID, "batch456");
    request.put(JsonKey.USER_ID, "user789");
    request.put(JsonKey.CONTENT_IDS, Arrays.asList("content1"));

    // Should not throw exception
    validator.validateGetContentState(request);
  }

  @Test
  public void testValidateGetContentState_WithCourseIdsAsListPicksFirst() {
    List<String> courseIds = Arrays.asList("course1", "course2", "course3");
    request.put(JsonKey.COURSE_IDS, courseIds);
    request.put(JsonKey.BATCH_ID, "batch456");
    request.put(JsonKey.USER_ID, "user789");
    request.put(JsonKey.CONTENT_IDS, Arrays.asList("content1"));

    validator.validateGetContentState(request);

    // After validation, COURSE_ID should be set to first element from COURSE_IDS
    assertEquals("course1", request.get(JsonKey.COURSE_ID));
    // COURSE_IDS should be removed
    assertNull(request.get(JsonKey.COURSE_IDS));
  }

  @Test
  public void testValidateGetContentState_WithCourseIdAlreadyPresent() {
    request.put(JsonKey.COURSE_ID, "existingCourse");
    request.put(JsonKey.COURSE_IDS, Arrays.asList("newCourse1", "newCourse2"));
    request.put(JsonKey.BATCH_ID, "batch456");
    request.put(JsonKey.USER_ID, "user789");
    request.put(JsonKey.CONTENT_IDS, Arrays.asList("content1"));

    validator.validateGetContentState(request);

    // Existing COURSE_ID should be preserved
    assertEquals("existingCourse", request.get(JsonKey.COURSE_ID));
  }

  @Test
  public void testValidateGetContentState_WithCollectionIdAlreadyPresent() {
    request.put(JsonKey.COLLECTION_ID, "collection123");
    request.put(JsonKey.COURSE_IDS, Arrays.asList("course1"));
    request.put(JsonKey.BATCH_ID, "batch456");
    request.put(JsonKey.USER_ID, "user789");
    request.put(JsonKey.CONTENT_IDS, Arrays.asList("content1"));

    validator.validateGetContentState(request);

    // COLLECTION_ID should be used as COURSE_ID when COURSE_ID is not present
    assertEquals("collection123", request.get(JsonKey.COURSE_ID));
  }

  @Test
  public void testValidateGetContentState_SingleContentId() {
    request.put(JsonKey.COURSE_ID, "course123");
    request.put(JsonKey.BATCH_ID, "batch456");
    request.put(JsonKey.USER_ID, "user789");
    request.put(JsonKey.CONTENT_IDS, Arrays.asList("singleContent"));

    // Should not throw exception
    validator.validateGetContentState(request);
  }

  @Test
  public void testValidateGetContentState_MultipleContentIds() {
    request.put(JsonKey.COURSE_ID, "course123");
    request.put(JsonKey.BATCH_ID, "batch456");
    request.put(JsonKey.USER_ID, "user789");
    request.put(JsonKey.CONTENT_IDS,
        Arrays.asList("content1", "content2", "content3", "content4", "content5"));

    // Should not throw exception
    validator.validateGetContentState(request);
  }

  // =============================================
  // validateGetContentState - Failure Cases (Missing Mandatory Fields)
  // =============================================

  @Test(expected = ProjectCommonException.class)
  public void testValidateGetContentState_MissingUserId() {
    request.put(JsonKey.COURSE_ID, "course123");
    request.put(JsonKey.BATCH_ID, "batch456");
    // USER_ID missing
    request.put(JsonKey.CONTENT_IDS, Arrays.asList("content1"));

    validator.validateGetContentState(request);
  }

  @Test(expected = ProjectCommonException.class)
  public void testValidateGetContentState_MissingBatchId() {
    request.put(JsonKey.COURSE_ID, "course123");
    // BATCH_ID missing
    request.put(JsonKey.USER_ID, "user789");
    request.put(JsonKey.CONTENT_IDS, Arrays.asList("content1"));

    validator.validateGetContentState(request);
  }

  @Test(expected = ProjectCommonException.class)
  public void testValidateGetContentState_MissingCourseIdAndCollectionId() {
    // Neither COURSE_ID nor COLLECTION_ID provided, and COURSE_IDS is empty/missing
    request.put(JsonKey.BATCH_ID, "batch456");
    request.put(JsonKey.USER_ID, "user789");
    request.put(JsonKey.CONTENT_IDS, Arrays.asList("content1"));

    validator.validateGetContentState(request);
  }

  @Test(expected = ProjectCommonException.class)
  public void testValidateGetContentState_EmptyCourseIdsList() {
    request.put(JsonKey.COURSE_IDS, Arrays.asList()); // Empty list
    request.put(JsonKey.BATCH_ID, "batch456");
    request.put(JsonKey.USER_ID, "user789");
    request.put(JsonKey.CONTENT_IDS, Arrays.asList("content1"));

    validator.validateGetContentState(request);
  }

  // =============================================
  // validateGetContentState - Field Type Validation
  // =============================================

  @Test(expected = ProjectCommonException.class)
  public void testValidateGetContentState_ContentIdsNotList() {
    request.put(JsonKey.COURSE_ID, "course123");
    request.put(JsonKey.BATCH_ID, "batch456");
    request.put(JsonKey.USER_ID, "user789");
    request.put(JsonKey.CONTENT_IDS, "notAList");

    validator.validateGetContentState(request);
  }

  @Test(expected = ProjectCommonException.class)
  public void testValidateGetContentState_CourseIdsNotList() {
    request.put(JsonKey.COURSE_IDS, "notAList");
    request.put(JsonKey.BATCH_ID, "batch456");
    request.put(JsonKey.USER_ID, "user789");
    request.put(JsonKey.CONTENT_IDS, Arrays.asList("content1"));

    validator.validateGetContentState(request);
  }

  // =============================================
  // validateGetContentState - Blank/Null Field Values
  // =============================================

  @Test(expected = ProjectCommonException.class)
  public void testValidateGetContentState_BlankUserId() {
    request.put(JsonKey.COURSE_ID, "course123");
    request.put(JsonKey.BATCH_ID, "batch456");
    request.put(JsonKey.USER_ID, ""); // Blank
    request.put(JsonKey.CONTENT_IDS, Arrays.asList("content1"));

    validator.validateGetContentState(request);
  }

  @Test(expected = ProjectCommonException.class)
  public void testValidateGetContentState_NullUserId() {
    request.put(JsonKey.COURSE_ID, "course123");
    request.put(JsonKey.BATCH_ID, "batch456");
    request.put(JsonKey.USER_ID, null);
    request.put(JsonKey.CONTENT_IDS, Arrays.asList("content1"));

    validator.validateGetContentState(request);
  }

  @Test(expected = ProjectCommonException.class)
  public void testValidateGetContentState_BlankBatchId() {
    request.put(JsonKey.COURSE_ID, "course123");
    request.put(JsonKey.BATCH_ID, ""); // Blank
    request.put(JsonKey.USER_ID, "user789");
    request.put(JsonKey.CONTENT_IDS, Arrays.asList("content1"));

    validator.validateGetContentState(request);
  }

  @Test(expected = ProjectCommonException.class)
  public void testValidateGetContentState_NullBatchId() {
    request.put(JsonKey.COURSE_ID, "course123");
    request.put(JsonKey.BATCH_ID, null);
    request.put(JsonKey.USER_ID, "user789");
    request.put(JsonKey.CONTENT_IDS, Arrays.asList("content1"));

    validator.validateGetContentState(request);
  }

  @Test(expected = ProjectCommonException.class)
  public void testValidateGetContentState_BlankCourseId() {
    request.put(JsonKey.COURSE_ID, ""); // Blank
    request.put(JsonKey.BATCH_ID, "batch456");
    request.put(JsonKey.USER_ID, "user789");
    request.put(JsonKey.CONTENT_IDS, Arrays.asList("content1"));

    validator.validateGetContentState(request);
  }

  // =============================================
  // validateGetContentState - Exception Code Validation
  // =============================================

  @Test
  public void testValidateGetContentState_MissingUserIdExceptionCode() {
    request.put(JsonKey.COURSE_ID, "course123");
    request.put(JsonKey.BATCH_ID, "batch456");
    request.put(JsonKey.CONTENT_IDS, Arrays.asList("content1"));

    try {
      validator.validateGetContentState(request);
      fail("Should throw exception");
    } catch (ProjectCommonException e) {
      assertEquals(ResponseCode.mandatoryParamsMissing.getErrorCode(), e.getErrorCode());
    }
  }

  @Test
  public void testValidateGetContentState_MissingBatchIdExceptionCode() {
    request.put(JsonKey.COURSE_ID, "course123");
    request.put(JsonKey.USER_ID, "user789");
    request.put(JsonKey.CONTENT_IDS, Arrays.asList("content1"));

    try {
      validator.validateGetContentState(request);
      fail("Should throw exception");
    } catch (ProjectCommonException e) {
      assertEquals(ResponseCode.mandatoryParamsMissing.getErrorCode(), e.getErrorCode());
    }
  }

  @Test
  public void testValidateGetContentState_MissingCourseIdExceptionCode() {
    request.put(JsonKey.BATCH_ID, "batch456");
    request.put(JsonKey.USER_ID, "user789");
    request.put(JsonKey.CONTENT_IDS, Arrays.asList("content1"));

    try {
      validator.validateGetContentState(request);
      fail("Should throw exception");
    } catch (ProjectCommonException e) {
      assertEquals(ResponseCode.mandatoryParamsMissing.getErrorCode(), e.getErrorCode());
    }
  }

  @Test
  public void testValidateGetContentState_InvalidContentIdsTypeExceptionCode() {
    request.put(JsonKey.COURSE_ID, "course123");
    request.put(JsonKey.BATCH_ID, "batch456");
    request.put(JsonKey.USER_ID, "user789");
    request.put(JsonKey.CONTENT_IDS, "notAList");

    try {
      validator.validateGetContentState(request);
      fail("Should throw exception");
    } catch (ProjectCommonException e) {
      assertEquals(ResponseCode.dataTypeError.getErrorCode(), e.getErrorCode());
    }
  }

  // =============================================
  // validateGetContentState - Edge Cases
  // =============================================

  @Test
  public void testValidateGetContentState_CourseIdsWithSingleElement() {
    request.put(JsonKey.COURSE_IDS, Arrays.asList("course1"));
    request.put(JsonKey.BATCH_ID, "batch456");
    request.put(JsonKey.USER_ID, "user789");
    request.put(JsonKey.CONTENT_IDS, Arrays.asList("content1"));

    validator.validateGetContentState(request);

    assertEquals("course1", request.get(JsonKey.COURSE_ID));
  }

  @Test
  public void testValidateGetContentState_WithoutContentIds() {
    request.put(JsonKey.COURSE_ID, "course123");
    request.put(JsonKey.BATCH_ID, "batch456");
    request.put(JsonKey.USER_ID, "user789");
    // CONTENT_IDS not provided

    // Should not throw exception (optional field)
    validator.validateGetContentState(request);
  }

  @Test
  public void testValidateGetContentState_EmptyContentIdsList() {
    request.put(JsonKey.COURSE_ID, "course123");
    request.put(JsonKey.BATCH_ID, "batch456");
    request.put(JsonKey.USER_ID, "user789");
    request.put(JsonKey.CONTENT_IDS, Arrays.asList()); // Empty list

    // Should not throw exception (optional field, but if present must be valid list)
    validator.validateGetContentState(request);
  }

  @Test
  public void testValidateGetContentState_LargeCourseIdsList() {
    List<String> courseIds =
        Arrays.asList("course1", "course2", "course3", "course4", "course5", "course6");
    request.put(JsonKey.COURSE_IDS, courseIds);
    request.put(JsonKey.BATCH_ID, "batch456");
    request.put(JsonKey.USER_ID, "user789");
    request.put(JsonKey.CONTENT_IDS, Arrays.asList("content1"));

    validator.validateGetContentState(request);

    // Should pick first element
    assertEquals("course1", request.get(JsonKey.COURSE_ID));
  }

  @Test
  public void testValidateGetContentState_LargeContentIdsList() {
    List<String> contentIds =
        Arrays.asList("c1", "c2", "c3", "c4", "c5", "c6", "c7", "c8", "c9", "c10");
    request.put(JsonKey.COURSE_ID, "course123");
    request.put(JsonKey.BATCH_ID, "batch456");
    request.put(JsonKey.USER_ID, "user789");
    request.put(JsonKey.CONTENT_IDS, contentIds);

    // Should not throw exception
    validator.validateGetContentState(request);
  }

  @Test
  public void testValidateGetContentState_SpecialCharactersInIds() {
    request.put(JsonKey.COURSE_ID, "course-123_special.id");
    request.put(JsonKey.BATCH_ID, "batch_456-special");
    request.put(JsonKey.USER_ID, "user.789_special");
    request.put(JsonKey.CONTENT_IDS, Arrays.asList("content-1_special", "content-2.special"));

    // Should not throw exception (special characters are allowed in IDs)
    validator.validateGetContentState(request);
  }

  @Test
  public void testValidateGetContentState_VeryLongIds() {
    String longId = "a".repeat(500);
    request.put(JsonKey.COURSE_ID, longId);
    request.put(JsonKey.BATCH_ID, longId);
    request.put(JsonKey.USER_ID, longId);
    request.put(JsonKey.CONTENT_IDS, Arrays.asList(longId));

    // Should not throw exception (length validation not in scope)
    validator.validateGetContentState(request);
  }

  // =============================================
  // validateGetContentState - Collection ID Handling
  // =============================================

  @Test
  public void testValidateGetContentState_WithCollectionIdAndCourseIds() {
    request.put(JsonKey.COLLECTION_ID, "collection123");
    request.put(JsonKey.COURSE_IDS, Arrays.asList("course1", "course2"));
    request.put(JsonKey.BATCH_ID, "batch456");
    request.put(JsonKey.USER_ID, "user789");
    request.put(JsonKey.CONTENT_IDS, Arrays.asList("content1"));

    validator.validateGetContentState(request);

    // COLLECTION_ID should be preserved in COURSE_ID position
    assertEquals("collection123", request.get(JsonKey.COURSE_ID));
  }

  @Test
  public void testValidateGetContentState_CourseIdPreservedOverCollection() {
    request.put(JsonKey.COURSE_ID, "course123");
    request.put(JsonKey.COLLECTION_ID, "collection456");
    request.put(JsonKey.BATCH_ID, "batch789");
    request.put(JsonKey.USER_ID, "user000");
    request.put(JsonKey.CONTENT_IDS, Arrays.asList("content1"));

    validator.validateGetContentState(request);

    // COURSE_ID should be preserved
    assertEquals("course123", request.get(JsonKey.COURSE_ID));
  }
}
