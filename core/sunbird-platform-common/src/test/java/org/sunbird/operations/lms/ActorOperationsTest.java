package org.sunbird.operations.lms;

import static org.junit.Assert.*;

import org.junit.Test;

public class ActorOperationsTest {

  // =============================================
  // getValue() Tests - Verify String Literals
  // =============================================

  @Test
  public void testGetValue_EnrollCourse() {
    assertEquals("enrollCourse", ActorOperations.ENROLL_COURSE.getValue());
  }

  @Test
  public void testGetValue_UnenrollCourse() {
    assertEquals("unenrollCourse", ActorOperations.UNENROLL_COURSE.getValue());
  }

  @Test
  public void testGetValue_GetCourse() {
    assertEquals("getCourse", ActorOperations.GET_COURSE.getValue());
  }

  @Test
  public void testGetValue_CreateCourse() {
    assertEquals("createCourse", ActorOperations.CREATE_COURSE.getValue());
  }

  @Test
  public void testGetValue_UpdateCourse() {
    assertEquals("updateCourse", ActorOperations.UPDATE_COURSE.getValue());
  }

  @Test
  public void testGetValue_PublishCourse() {
    assertEquals("publishCourse", ActorOperations.PUBLISH_COURSE.getValue());
  }

  @Test
  public void testGetValue_SearchCourse() {
    assertEquals("searchCourse", ActorOperations.SEARCH_COURSE.getValue());
  }

  @Test
  public void testGetValue_DeleteCourse() {
    assertEquals("deleteCourse", ActorOperations.DELETE_COURSE.getValue());
  }

  @Test
  public void testGetValue_CreateUser() {
    assertEquals("createUser", ActorOperations.CREATE_USER.getValue());
  }

  @Test
  public void testGetValue_UpdateUser() {
    assertEquals("updateUser", ActorOperations.UPDATE_USER.getValue());
  }

  @Test
  public void testGetValue_UserAuth() {
    assertEquals("userAuth", ActorOperations.USER_AUTH.getValue());
  }

  @Test
  public void testGetValue_GetUserProfile() {
    assertEquals("getUserProfile", ActorOperations.GET_USER_PROFILE.getValue());
  }

  @Test
  public void testGetValue_GetUserProfileV2() {
    assertEquals("getUserProfileV2", ActorOperations.GET_USER_PROFILE_V2.getValue());
  }

  @Test
  public void testGetValue_CreateOrg() {
    assertEquals("createOrg", ActorOperations.CREATE_ORG.getValue());
  }

  @Test
  public void testGetValue_UpdateOrg() {
    assertEquals("updateOrg", ActorOperations.UPDATE_ORG.getValue());
  }

  @Test
  public void testGetValue_UpdateOrgStatus() {
    assertEquals("updateOrgStatus", ActorOperations.UPDATE_ORG_STATUS.getValue());
  }

  @Test
  public void testGetValue_GetOrgDetails() {
    assertEquals("getOrgDetails", ActorOperations.GET_ORG_DETAILS.getValue());
  }

  @Test
  public void testGetValue_CreatePage() {
    assertEquals("createPage", ActorOperations.CREATE_PAGE.getValue());
  }

  @Test
  public void testGetValue_UpdatePage() {
    assertEquals("updatePage", ActorOperations.UPDATE_PAGE.getValue());
  }

  @Test
  public void testGetValue_DeletePage() {
    assertEquals("deletePage", ActorOperations.DELETE_PAGE.getValue());
  }

  @Test
  public void testGetValue_CreateSection() {
    assertEquals("createSection", ActorOperations.CREATE_SECTION.getValue());
  }

  @Test
  public void testGetValue_UpdateSection() {
    assertEquals("updateSection", ActorOperations.UPDATE_SECTION.getValue());
  }

  @Test
  public void testGetValue_GetAllSection() {
    assertEquals("getAllSection", ActorOperations.GET_ALL_SECTION.getValue());
  }

  @Test
  public void testGetValue_GetSection() {
    assertEquals("getSection", ActorOperations.GET_SECTION.getValue());
  }

  @Test
  public void testGetValue_HealthCheck() {
    assertEquals("healthCheck", ActorOperations.HEALTH_CHECK.getValue());
  }

  @Test
  public void testGetValue_SendMail() {
    assertEquals("sendMail", ActorOperations.SEND_MAIL.getValue());
  }

  @Test
  public void testGetValue_BulkUpload() {
    assertEquals("bulkUpload", ActorOperations.BULK_UPLOAD.getValue());
  }

  @Test
  public void testGetValue_ProcessBulkUpload() {
    assertEquals("processBulkUpload", ActorOperations.PROCESS_BULK_UPLOAD.getValue());
  }

  @Test
  public void testGetValue_EmailService() {
    assertEquals("emailService", ActorOperations.EMAIL_SERVICE.getValue());
  }

  @Test
  public void testGetValue_FileStorageService() {
    assertEquals("fileStorageService", ActorOperations.FILE_STORAGE_SERVICE.getValue());
  }

  @Test
  public void testGetValue_FileGenerationAndUpload() {
    assertEquals("fileGenerationAndUpload", ActorOperations.FILE_GENERATION_AND_UPLOAD.getValue());
  }

  @Test
  public void testGetValue_CreateBatch() {
    assertEquals("createBatch", ActorOperations.CREATE_BATCH.getValue());
  }

  @Test
  public void testGetValue_UpdateBatch() {
    assertEquals("updateBatch", ActorOperations.UPDATE_BATCH.getValue());
  }

  @Test
  public void testGetValue_RemoveBatch() {
    assertEquals("removeBatch", ActorOperations.REMOVE_BATCH.getValue());
  }

  @Test
  public void testGetValue_GetBatch() {
    assertEquals("getBatch", ActorOperations.GET_BATCH.getValue());
  }

  @Test
  public void testGetValue_CreateNote() {
    assertEquals("createNote", ActorOperations.CREATE_NOTE.getValue());
  }

  @Test
  public void testGetValue_UpdateNote() {
    assertEquals("updateNote", ActorOperations.UPDATE_NOTE.getValue());
  }

  @Test
  public void testGetValue_SearchNote() {
    assertEquals("searchNote", ActorOperations.SEARCH_NOTE.getValue());
  }

  @Test
  public void testGetValue_GetNote() {
    assertEquals("getNote", ActorOperations.GET_NOTE.getValue());
  }

  @Test
  public void testGetValue_DeleteNote() {
    assertEquals("deleteNote", ActorOperations.DELETE_NOTE.getValue());
  }

  @Test
  public void testGetValue_ResetPassword() {
    assertEquals("resetPassword", ActorOperations.RESET_PASSWORD.getValue());
  }

  @Test
  public void testGetValue_MergeUser() {
    assertEquals("mergeUser", ActorOperations.MERGE_USER.getValue());
  }

  @Test
  public void testGetValue_MergeUserToElastic() {
    assertEquals("mergeUserToElastic", ActorOperations.MERGE_USER_TO_ELASTIC.getValue());
  }

  @Test
  public void testGetValue_ValidateCertificate() {
    assertEquals("validateCertificate", ActorOperations.VALIDATE_CERTIFICATE.getValue());
  }

  @Test
  public void testGetValue_AddCertificate() {
    assertEquals("addCertificate", ActorOperations.ADD_CERTIFICATE.getValue());
  }

  @Test
  public void testGetValue_MigrateUser() {
    assertEquals("migrateUser", ActorOperations.MIGRATE_USER.getValue());
  }

  @Test
  public void testGetValue_CreateUserV3() {
    assertEquals("createUserV3", ActorOperations.CREATE_USER_V3.getValue());
  }

  // =============================================
  // All Operations Have Values Tests
  // =============================================

  @Test
  public void testAllOperationsHaveValues() {
    for (ActorOperations operation : ActorOperations.values()) {
      assertNotNull("Operation " + operation.name() + " should have a value", operation.getValue());
      assertFalse("Operation " + operation.name() + " value should not be empty",
          operation.getValue().isEmpty());
    }
  }

  // =============================================
  // Value Format Tests
  // =============================================

  @Test
  public void testValueFormat_CamelCase() {
    // Most operations should be in camelCase format
    assertEquals("enrollCourse", ActorOperations.ENROLL_COURSE.getValue());
    assertEquals("createUser", ActorOperations.CREATE_USER.getValue());
    assertEquals("updateOrgStatus", ActorOperations.UPDATE_ORG_STATUS.getValue());
  }

  @Test
  public void testValueFormat_NoSpaces() {
    for (ActorOperations operation : ActorOperations.values()) {
      assertFalse("Operation value should not contain spaces: " + operation.getValue(),
          operation.getValue().contains(" "));
    }
  }

  @Test
  public void testValueFormat_NoUnderscores() {
    for (ActorOperations operation : ActorOperations.values()) {
      assertFalse("Operation value should not contain underscores: " + operation.getValue(),
          operation.getValue().contains("_"));
    }
  }

  // =============================================
  // Enum Uniqueness Tests
  // =============================================

  @Test
  public void testEnumValues_Unique() {
    java.util.Set<String> values = new java.util.HashSet<>();
    for (ActorOperations operation : ActorOperations.values()) {
      assertTrue("Duplicate operation value found: " + operation.getValue(),
          values.add(operation.getValue()));
    }
  }

  @Test
  public void testEnumNames_Unique() {
    java.util.Set<String> names = new java.util.HashSet<>();
    for (ActorOperations operation : ActorOperations.values()) {
      assertTrue("Duplicate operation name found: " + operation.name(),
          names.add(operation.name()));
    }
  }

  // =============================================
  // Enum Count Tests
  // =============================================

  @Test
  public void testEnumCount_MoreThanZero() {
    assertTrue("ActorOperations should have at least one value", ActorOperations.values().length > 0);
  }

  @Test
  public void testEnumCount_ReasonableNumber() {
    // Should have a reasonable number of operations
    assertTrue("ActorOperations should have multiple values",
        ActorOperations.values().length > 50);
  }

  // =============================================
  // Specific Operation Groups Tests
  // =============================================

  @Test
  public void testCourseOperations_AllPresent() {
    assertNotNull(ActorOperations.CREATE_COURSE);
    assertNotNull(ActorOperations.UPDATE_COURSE);
    assertNotNull(ActorOperations.GET_COURSE);
    assertNotNull(ActorOperations.DELETE_COURSE);
    assertNotNull(ActorOperations.SEARCH_COURSE);
    assertNotNull(ActorOperations.PUBLISH_COURSE);
    assertNotNull(ActorOperations.ENROLL_COURSE);
    assertNotNull(ActorOperations.UNENROLL_COURSE);
  }

  @Test
  public void testUserOperations_AllPresent() {
    assertNotNull(ActorOperations.CREATE_USER);
    assertNotNull(ActorOperations.UPDATE_USER);
    assertNotNull(ActorOperations.GET_USER_PROFILE);
    assertNotNull(ActorOperations.BLOCK_USER);
    assertNotNull(ActorOperations.UNBLOCK_USER);
    assertNotNull(ActorOperations.GET_USER_BY_KEY);
  }

  @Test
  public void testOrgOperations_AllPresent() {
    assertNotNull(ActorOperations.CREATE_ORG);
    assertNotNull(ActorOperations.UPDATE_ORG);
    assertNotNull(ActorOperations.GET_ORG_DETAILS);
    assertNotNull(ActorOperations.UPDATE_ORG_STATUS);
  }

  @Test
  public void testBatchOperations_AllPresent() {
    assertNotNull(ActorOperations.CREATE_BATCH);
    assertNotNull(ActorOperations.UPDATE_BATCH);
    assertNotNull(ActorOperations.REMOVE_BATCH);
    assertNotNull(ActorOperations.GET_BATCH);
    assertNotNull(ActorOperations.ADD_USER_TO_BATCH);
    assertNotNull(ActorOperations.REMOVE_USER_FROM_BATCH);
  }

  @Test
  public void testPageOperations_AllPresent() {
    assertNotNull(ActorOperations.CREATE_PAGE);
    assertNotNull(ActorOperations.UPDATE_PAGE);
    assertNotNull(ActorOperations.DELETE_PAGE);
    assertNotNull(ActorOperations.GET_PAGE_DATA);
  }

  @Test
  public void testNoteOperations_AllPresent() {
    assertNotNull(ActorOperations.CREATE_NOTE);
    assertNotNull(ActorOperations.UPDATE_NOTE);
    assertNotNull(ActorOperations.DELETE_NOTE);
    assertNotNull(ActorOperations.GET_NOTE);
    assertNotNull(ActorOperations.SEARCH_NOTE);
  }

  // =============================================
  // System Operations Tests
  // =============================================

  @Test
  public void testSystemOperations_HealthCheck() {
    assertEquals("healthCheck", ActorOperations.HEALTH_CHECK.getValue());
  }

  @Test
  public void testSystemOperations_SendMail() {
    assertEquals("sendMail", ActorOperations.SEND_MAIL.getValue());
  }

  @Test
  public void testSystemOperations_Sync() {
    assertEquals("sync", ActorOperations.SYNC.getValue());
  }

  @Test
  public void testSystemOperations_ClearCache() {
    assertEquals("clearCache", ActorOperations.CLEAR_CACHE.getValue());
  }

  // =============================================
  // Enum valueOf Tests
  // =============================================

  @Test
  public void testValueOf_ValidEnumName() {
    ActorOperations op = ActorOperations.valueOf("CREATE_USER");
    assertEquals(ActorOperations.CREATE_USER, op);
  }

  @Test
  public void testValueOf_InvalidEnumName_ThrowsException() {
    assertThrows(IllegalArgumentException.class, () -> {
      ActorOperations.valueOf("INVALID_OPERATION");
    });
  }

  @Test
  public void testValues_ReturnAllEnums() {
    ActorOperations[] ops = ActorOperations.values();
    assertTrue(ops.length > 0);
    // Check that at least some well-known operations are present
    boolean hasCreateUser = false;
    boolean hasHealthCheck = false;
    for (ActorOperations op : ops) {
      if (op == ActorOperations.CREATE_USER) hasCreateUser = true;
      if (op == ActorOperations.HEALTH_CHECK) hasHealthCheck = true;
    }
    assertTrue(hasCreateUser);
    assertTrue(hasHealthCheck);
  }

  // =============================================
  // toString() Tests
  // =============================================

  @Test
  public void testToString_ContainsEnumName() {
    String str = ActorOperations.CREATE_USER.toString();
    assertTrue(str.contains("CREATE_USER"));
  }

  @Test
  public void testComparison_SameEnumValue() {
    ActorOperations op1 = ActorOperations.CREATE_USER;
    ActorOperations op2 = ActorOperations.CREATE_USER;
    assertEquals(op1, op2);
    assertTrue(op1 == op2);
  }

  @Test
  public void testComparison_DifferentEnumValue() {
    ActorOperations op1 = ActorOperations.CREATE_USER;
    ActorOperations op2 = ActorOperations.UPDATE_USER;
    assertNotEquals(op1, op2);
    assertFalse(op1 == op2);
  }
}
