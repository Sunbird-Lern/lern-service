package org.sunbird.operations.userorg;

import static org.junit.Assert.*;

import org.junit.Test;

public class ActorOperationsTest {

  // =============================================
  // getValue() Tests - Verify String Values
  // =============================================

  @Test
  public void testGetValue_CreateUser() {
    assertEquals("createUser", org.sunbird.operations.userorg.ActorOperations.CREATE_USER.getValue());
  }

  @Test
  public void testGetValue_CreateSSOUser() {
    assertEquals("createSSOUser", org.sunbird.operations.userorg.ActorOperations.CREATE_SSO_USER.getValue());
  }

  @Test
  public void testGetValue_UpdateUser() {
    assertEquals("updateUser", org.sunbird.operations.userorg.ActorOperations.UPDATE_USER.getValue());
  }

  @Test
  public void testGetValue_UpdateUserV2() {
    assertEquals("updateUserV2", org.sunbird.operations.userorg.ActorOperations.UPDATE_USER_V2.getValue());
  }

  @Test
  public void testGetValue_UpdateUserV3() {
    assertEquals("updateUserV3", org.sunbird.operations.userorg.ActorOperations.UPDATE_USER_V3.getValue());
  }

  @Test
  public void testGetValue_GetUserProfileV3() {
    assertEquals("getUserProfileV3", org.sunbird.operations.userorg.ActorOperations.GET_USER_PROFILE_V3.getValue());
  }

  @Test
  public void testGetValue_GetUserProfileV4() {
    assertEquals("getUserProfileV4", org.sunbird.operations.userorg.ActorOperations.GET_USER_PROFILE_V4.getValue());
  }

  @Test
  public void testGetValue_GetUserProfileV5() {
    assertEquals("getUserProfileV5", org.sunbird.operations.userorg.ActorOperations.GET_USER_PROFILE_V5.getValue());
  }

  @Test
  public void testGetValue_BlockUser() {
    assertEquals("blockUser", org.sunbird.operations.userorg.ActorOperations.BLOCK_USER.getValue());
  }

  @Test
  public void testGetValue_UnblockUser() {
    assertEquals("unblockUser", org.sunbird.operations.userorg.ActorOperations.UNBLOCK_USER.getValue());
  }

  @Test
  public void testGetValue_BulkUpload() {
    assertEquals("bulkUpload", org.sunbird.operations.userorg.ActorOperations.BULK_UPLOAD.getValue());
  }

  @Test
  public void testGetValue_ProcessBulkUpload() {
    assertEquals("processBulkUpload", org.sunbird.operations.userorg.ActorOperations.PROCESS_BULK_UPLOAD.getValue());
  }

  @Test
  public void testGetValue_AssignRoles() {
    assertEquals("assignRoles", org.sunbird.operations.userorg.ActorOperations.ASSIGN_ROLES.getValue());
  }

  @Test
  public void testGetValue_CreateNote() {
    assertEquals("createNote", org.sunbird.operations.userorg.ActorOperations.CREATE_NOTE.getValue());
  }

  @Test
  public void testGetValue_UpdateNote() {
    assertEquals("updateNote", org.sunbird.operations.userorg.ActorOperations.UPDATE_NOTE.getValue());
  }

  @Test
  public void testGetValue_DeleteNote() {
    assertEquals("deleteNote", org.sunbird.operations.userorg.ActorOperations.DELETE_NOTE.getValue());
  }

  @Test
  public void testGetValue_GenerateOTP() {
    assertEquals("generateOTP", org.sunbird.operations.userorg.ActorOperations.GENERATE_OTP.getValue());
  }

  @Test
  public void testGetValue_VerifyOTP() {
    assertEquals("verifyOTP", org.sunbird.operations.userorg.ActorOperations.VERIFY_OTP.getValue());
  }

  @Test
  public void testGetValue_ResetPassword() {
    assertEquals("resetPassword", org.sunbird.operations.userorg.ActorOperations.RESET_PASSWORD.getValue());
  }

  @Test
  public void testGetValue_MergeUser() {
    assertEquals("mergeUser", org.sunbird.operations.userorg.ActorOperations.MERGE_USER.getValue());
  }

  @Test
  public void testGetValue_DeleteUser() {
    assertEquals("deleteUser", org.sunbird.operations.userorg.ActorOperations.DELETE_USER.getValue());
  }

  // =============================================
  // getOperationCode() Tests - Verify Op Codes
  // =============================================

  @Test
  public void testGetOperationCode_CreateUser() {
    assertEquals("USRCRT", org.sunbird.operations.userorg.ActorOperations.CREATE_USER.getOperationCode());
  }

  @Test
  public void testGetOperationCode_CreateSSOUser() {
    assertEquals("USRCRT", org.sunbird.operations.userorg.ActorOperations.CREATE_SSO_USER.getOperationCode());
  }

  @Test
  public void testGetOperationCode_UpdateUser() {
    assertEquals("USRUPD", org.sunbird.operations.userorg.ActorOperations.UPDATE_USER.getOperationCode());
  }

  @Test
  public void testGetOperationCode_UpdateUserV2() {
    assertEquals("USRUPD", org.sunbird.operations.userorg.ActorOperations.UPDATE_USER_V2.getOperationCode());
  }

  @Test
  public void testGetOperationCode_BlockUser() {
    assertEquals("USRBLOK", org.sunbird.operations.userorg.ActorOperations.BLOCK_USER.getOperationCode());
  }

  @Test
  public void testGetOperationCode_UnblockUser() {
    assertEquals("USRUNBLOK", org.sunbird.operations.userorg.ActorOperations.UNBLOCK_USER.getOperationCode());
  }

  @Test
  public void testGetOperationCode_BulkUpload() {
    assertEquals("BLKUPLD", org.sunbird.operations.userorg.ActorOperations.BULK_UPLOAD.getOperationCode());
  }

  @Test
  public void testGetOperationCode_ProcessBulkUpload() {
    assertEquals("BLKUPLD", org.sunbird.operations.userorg.ActorOperations.PROCESS_BULK_UPLOAD.getOperationCode());
  }

  @Test
  public void testGetOperationCode_AssignRoles() {
    assertEquals("ROLUPD", org.sunbird.operations.userorg.ActorOperations.ASSIGN_ROLES.getOperationCode());
  }

  @Test
  public void testGetOperationCode_CreateNote() {
    assertEquals("NOTECRT", org.sunbird.operations.userorg.ActorOperations.CREATE_NOTE.getOperationCode());
  }

  @Test
  public void testGetOperationCode_UpdateNote() {
    assertEquals("NOTEUPD", org.sunbird.operations.userorg.ActorOperations.UPDATE_NOTE.getOperationCode());
  }

  @Test
  public void testGetOperationCode_DeleteNote() {
    assertEquals("NOTEDEL", org.sunbird.operations.userorg.ActorOperations.DELETE_NOTE.getOperationCode());
  }

  @Test
  public void testGetOperationCode_GenerateOTP() {
    assertEquals("OTPCRT", org.sunbird.operations.userorg.ActorOperations.GENERATE_OTP.getOperationCode());
  }

  @Test
  public void testGetOperationCode_VerifyOTP() {
    assertEquals("OTPVERFY", org.sunbird.operations.userorg.ActorOperations.VERIFY_OTP.getOperationCode());
  }

  @Test
  public void testGetOperationCode_ResetPassword() {
    assertEquals("PASSRST", org.sunbird.operations.userorg.ActorOperations.RESET_PASSWORD.getOperationCode());
  }

  @Test
  public void testGetOperationCode_MergeUser() {
    assertEquals("USRMRG", org.sunbird.operations.userorg.ActorOperations.MERGE_USER.getOperationCode());
  }

  @Test
  public void testGetOperationCode_DeleteUser() {
    assertEquals("USRDLT", org.sunbird.operations.userorg.ActorOperations.DELETE_USER.getOperationCode());
  }

  // =============================================
  // All Operations Have Values and Codes Tests
  // =============================================

  @Test
  public void testAllOperationsHaveValues() {
    for (org.sunbird.operations.userorg.ActorOperations operation : org.sunbird.operations.userorg.ActorOperations.values()) {
      assertNotNull("Operation " + operation.name() + " should have a value", operation.getValue());
      assertFalse("Operation " + operation.name() + " value should not be empty",
          operation.getValue().isEmpty());
    }
  }

  @Test
  public void testAllOperationsHaveOperationCodes() {
    for (org.sunbird.operations.userorg.ActorOperations operation : org.sunbird.operations.userorg.ActorOperations.values()) {
      assertNotNull("Operation " + operation.name() + " should have an operation code", operation.getOperationCode());
      assertFalse("Operation " + operation.name() + " operation code should not be empty",
          operation.getOperationCode().isEmpty());
    }
  }

  // =============================================
  // Value Format Tests
  // =============================================

  @Test
  public void testValueFormat_CamelCase() {
    assertEquals("createUser", org.sunbird.operations.userorg.ActorOperations.CREATE_USER.getValue());
    assertEquals("updateUser", org.sunbird.operations.userorg.ActorOperations.UPDATE_USER.getValue());
    assertEquals("getUserProfileV3", org.sunbird.operations.userorg.ActorOperations.GET_USER_PROFILE_V3.getValue());
  }

  @Test
  public void testOperationCodeFormat_UpperCase() {
    assertEquals("USRCRT", org.sunbird.operations.userorg.ActorOperations.CREATE_USER.getOperationCode());
    assertEquals("USRUPD", org.sunbird.operations.userorg.ActorOperations.UPDATE_USER.getOperationCode());
    assertEquals("BLKUPLD", org.sunbird.operations.userorg.ActorOperations.BULK_UPLOAD.getOperationCode());
  }

  @Test
  public void testValueFormat_NoSpaces() {
    for (org.sunbird.operations.userorg.ActorOperations operation : org.sunbird.operations.userorg.ActorOperations.values()) {
      assertFalse("Operation value should not contain spaces: " + operation.getValue(),
          operation.getValue().contains(" "));
    }
  }

  @Test
  public void testOperationCodeFormat_NoSpaces() {
    for (org.sunbird.operations.userorg.ActorOperations operation : org.sunbird.operations.userorg.ActorOperations.values()) {
      assertFalse("Operation code should not contain spaces: " + operation.getOperationCode(),
          operation.getOperationCode().contains(" "));
    }
  }

  // =============================================
  // Enum Uniqueness Tests
  // =============================================

  @Test
  public void testEnumValues_Unique() {
    java.util.Set<String> values = new java.util.HashSet<>();
    for (org.sunbird.operations.userorg.ActorOperations operation : org.sunbird.operations.userorg.ActorOperations.values()) {
      assertTrue("Duplicate operation value found: " + operation.getValue(),
          values.add(operation.getValue()));
    }
  }

  @Test
  public void testEnumNames_Unique() {
    java.util.Set<String> names = new java.util.HashSet<>();
    for (org.sunbird.operations.userorg.ActorOperations operation : org.sunbird.operations.userorg.ActorOperations.values()) {
      assertTrue("Duplicate operation name found: " + operation.name(),
          names.add(operation.name()));
    }
  }

  // =============================================
  // getOperationCodeByActorOperation() Tests
  // =============================================

  @Test
  public void testGetOperationCodeByActorOperation_ValidOperations() {
    assertEquals("USRCRT", org.sunbird.operations.userorg.ActorOperations.getOperationCodeByActorOperation("createUser"));
    assertEquals("USRUPD", org.sunbird.operations.userorg.ActorOperations.getOperationCodeByActorOperation("updateUser"));
    assertEquals("USRBLOK", org.sunbird.operations.userorg.ActorOperations.getOperationCodeByActorOperation("blockUser"));
    assertEquals("BLKUPLD", org.sunbird.operations.userorg.ActorOperations.getOperationCodeByActorOperation("bulkUpload"));
    assertEquals("NOTECRT", org.sunbird.operations.userorg.ActorOperations.getOperationCodeByActorOperation("createNote"));
  }

  @Test
  public void testGetOperationCodeByActorOperation_InvalidOperation() {
    assertEquals("", org.sunbird.operations.userorg.ActorOperations.getOperationCodeByActorOperation("invalidOperation"));
  }

  @Test
  public void testGetOperationCodeByActorOperation_NullOperation() {
    assertEquals("", org.sunbird.operations.userorg.ActorOperations.getOperationCodeByActorOperation(null));
  }

  @Test
  public void testGetOperationCodeByActorOperation_BlankOperation() {
    assertEquals("", org.sunbird.operations.userorg.ActorOperations.getOperationCodeByActorOperation(""));
  }

  @Test
  public void testGetOperationCodeByActorOperation_WhitespaceOperation() {
    assertEquals("", org.sunbird.operations.userorg.ActorOperations.getOperationCodeByActorOperation("   "));
  }

  @Test
  public void testGetOperationCodeByActorOperation_AllValidOperations() {
    for (org.sunbird.operations.userorg.ActorOperations operation : org.sunbird.operations.userorg.ActorOperations.values()) {
      String opCode = org.sunbird.operations.userorg.ActorOperations.getOperationCodeByActorOperation(operation.getValue());
      assertEquals("Operation code mismatch for: " + operation.getValue(),
          operation.getOperationCode(), opCode);
    }
  }

  // =============================================
  // Operation Code Lookup Consistency Tests
  // =============================================

  @Test
  public void testOperationCodeLookup_Consistency() {
    org.sunbird.operations.userorg.ActorOperations op = org.sunbird.operations.userorg.ActorOperations.CREATE_USER;
    String retrievedCode = org.sunbird.operations.userorg.ActorOperations.getOperationCodeByActorOperation(op.getValue());
    assertEquals(op.getOperationCode(), retrievedCode);
  }

  @Test
  public void testOperationCodeMapping_Complete() {
    // Test that all operations have a proper mapping in the static map
    org.sunbird.operations.userorg.ActorOperations[] operations = org.sunbird.operations.userorg.ActorOperations.values();
    for (org.sunbird.operations.userorg.ActorOperations operation : operations) {
      String code = org.sunbird.operations.userorg.ActorOperations.getOperationCodeByActorOperation(operation.getValue());
      assertFalse("Operation code should not be blank for: " + operation.getValue(),
          code.isEmpty());
      assertEquals("Operation code mismatch for: " + operation.getValue(),
          operation.getOperationCode(), code);
    }
  }

  // =============================================
  // Specific Operation Groups Tests
  // =============================================

  @Test
  public void testUserOperations_AllPresent() {
    assertNotNull(org.sunbird.operations.userorg.ActorOperations.CREATE_USER);
    assertNotNull(org.sunbird.operations.userorg.ActorOperations.UPDATE_USER);
    assertNotNull(org.sunbird.operations.userorg.ActorOperations.GET_USER_PROFILE_V3);
    assertNotNull(org.sunbird.operations.userorg.ActorOperations.BLOCK_USER);
    assertNotNull(org.sunbird.operations.userorg.ActorOperations.UNBLOCK_USER);
  }

  @Test
  public void testNoteOperations_AllPresent() {
    assertNotNull(org.sunbird.operations.userorg.ActorOperations.CREATE_NOTE);
    assertNotNull(org.sunbird.operations.userorg.ActorOperations.UPDATE_NOTE);
    assertNotNull(org.sunbird.operations.userorg.ActorOperations.DELETE_NOTE);
    assertNotNull(org.sunbird.operations.userorg.ActorOperations.GET_NOTE);
    assertNotNull(org.sunbird.operations.userorg.ActorOperations.SEARCH_NOTE);
  }

  @Test
  public void testOtpOperations_AllPresent() {
    assertNotNull(org.sunbird.operations.userorg.ActorOperations.GENERATE_OTP);
    assertNotNull(org.sunbird.operations.userorg.ActorOperations.VERIFY_OTP);
    assertNotNull(org.sunbird.operations.userorg.ActorOperations.SEND_OTP);
  }

  @Test
  public void testBulkUploadOperations_AllPresent() {
    assertNotNull(org.sunbird.operations.userorg.ActorOperations.BULK_UPLOAD);
    assertNotNull(org.sunbird.operations.userorg.ActorOperations.PROCESS_BULK_UPLOAD);
    assertNotNull(org.sunbird.operations.userorg.ActorOperations.USER_BULK_UPLOAD);
    assertNotNull(org.sunbird.operations.userorg.ActorOperations.ORG_BULK_UPLOAD);
  }

  @Test
  public void testLocationOperations_AllPresent() {
    assertNotNull(org.sunbird.operations.userorg.ActorOperations.CREATE_LOCATION);
    assertNotNull(org.sunbird.operations.userorg.ActorOperations.UPDATE_LOCATION);
    assertNotNull(org.sunbird.operations.userorg.ActorOperations.DELETE_LOCATION);
    assertNotNull(org.sunbird.operations.userorg.ActorOperations.SEARCH_LOCATION);
  }

  @Test
  public void testOrgOperations_AllPresent() {
    assertNotNull(org.sunbird.operations.userorg.ActorOperations.CREATE_ORG);
    assertNotNull(org.sunbird.operations.userorg.ActorOperations.UPDATE_ORG);
    assertNotNull(org.sunbird.operations.userorg.ActorOperations.GET_ORG_DETAILS);
    assertNotNull(org.sunbird.operations.userorg.ActorOperations.UPDATE_ORG_STATUS);
  }

  // =============================================
  // Enum Ordinal Tests
  // =============================================

  @Test
  public void testEnumCount_ReasonableNumber() {
    org.sunbird.operations.userorg.ActorOperations[] operations = org.sunbird.operations.userorg.ActorOperations.values();
    assertTrue("ActorOperations should have multiple values", operations.length > 50);
  }

  @Test
  public void testEnumOrdinal_Consistency() {
    org.sunbird.operations.userorg.ActorOperations[] operations = org.sunbird.operations.userorg.ActorOperations.values();
    for (int i = 0; i < operations.length; i++) {
      assertEquals("Ordinal should match index", i, operations[i].ordinal());
    }
  }

  // =============================================
  // valueOf Tests
  // =============================================

  @Test
  public void testValueOf_ValidEnumName() {
    org.sunbird.operations.userorg.ActorOperations op = org.sunbird.operations.userorg.ActorOperations.valueOf("CREATE_USER");
    assertEquals(org.sunbird.operations.userorg.ActorOperations.CREATE_USER, op);
  }

  @Test
  public void testValueOf_InvalidEnumName_ThrowsException() {
    assertThrows(IllegalArgumentException.class, () -> {
      org.sunbird.operations.userorg.ActorOperations.valueOf("INVALID_OPERATION");
    });
  }

  @Test
  public void testValues_ReturnAllEnums() {
    org.sunbird.operations.userorg.ActorOperations[] ops = org.sunbird.operations.userorg.ActorOperations.values();
    assertTrue(ops.length > 0);
    // Check that at least some well-known operations are present
    boolean hasCreateUser = false;
    boolean hasResetPassword = false;
    for (org.sunbird.operations.userorg.ActorOperations op : ops) {
      if (op == org.sunbird.operations.userorg.ActorOperations.CREATE_USER) hasCreateUser = true;
      if (op == org.sunbird.operations.userorg.ActorOperations.RESET_PASSWORD) hasResetPassword = true;
    }
    assertTrue(hasCreateUser);
    assertTrue(hasResetPassword);
  }

  // =============================================
  // Comparison Tests
  // =============================================

  @Test
  public void testComparison_SameEnumValue() {
    org.sunbird.operations.userorg.ActorOperations op1 = org.sunbird.operations.userorg.ActorOperations.CREATE_USER;
    org.sunbird.operations.userorg.ActorOperations op2 = org.sunbird.operations.userorg.ActorOperations.CREATE_USER;
    assertEquals(op1, op2);
    assertTrue(op1 == op2);
  }

  @Test
  public void testComparison_DifferentEnumValue() {
    org.sunbird.operations.userorg.ActorOperations op1 = org.sunbird.operations.userorg.ActorOperations.CREATE_USER;
    org.sunbird.operations.userorg.ActorOperations op2 = org.sunbird.operations.userorg.ActorOperations.UPDATE_USER;
    assertNotEquals(op1, op2);
    assertFalse(op1 == op2);
  }

  // =============================================
  // toString Tests
  // =============================================

  @Test
  public void testToString_ContainsEnumName() {
    String str = org.sunbird.operations.userorg.ActorOperations.CREATE_USER.toString();
    assertTrue(str.contains("CREATE_USER"));
  }
}
