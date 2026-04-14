package org.sunbird.operations.lms;

import static org.junit.Assert.*;

import org.junit.Test;

public class BulkUploadActorOperationTest {

  // =============================================
  // getValue() Tests
  // =============================================

  @Test
  public void testGetValue_UserBulkUpload() {
    assertEquals("userBulkUpload", BulkUploadActorOperation.USER_BULK_UPLOAD.getValue());
  }

  @Test
  public void testGetValue_UserBulkUploadBackground() {
    assertEquals("userBulkUploadBackground", BulkUploadActorOperation.USER_BULK_UPLOAD_BACKGROUND_JOB.getValue());
  }

  @Test
  public void testGetValue_OrgBulkUpload() {
    assertEquals("orgBulkUpload", BulkUploadActorOperation.ORG_BULK_UPLOAD.getValue());
  }

  @Test
  public void testGetValue_OrgBulkUploadBackground() {
    assertEquals("orgBulkUploadBackground", BulkUploadActorOperation.ORG_BULK_UPLOAD_BACKGROUND_JOB.getValue());
  }

  @Test
  public void testGetValue_LocationBulkUpload() {
    assertEquals("locationBulkUpload", BulkUploadActorOperation.LOCATION_BULK_UPLOAD.getValue());
  }

  @Test
  public void testGetValue_LocationBulkUploadBackground() {
    assertEquals("locationBulkUploadBackground", BulkUploadActorOperation.LOCATION_BULK_UPLOAD_BACKGROUND_JOB.getValue());
  }

  @Test
  public void testGetValue_UserBulkMigration() {
    assertEquals("userBulkMigration", BulkUploadActorOperation.USER_BULK_MIGRATION.getValue());
  }

  // =============================================
  // All Operations Have Values
  // =============================================

  @Test
  public void testAllOperationsHaveValues() {
    for (BulkUploadActorOperation operation : BulkUploadActorOperation.values()) {
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
    for (BulkUploadActorOperation operation : BulkUploadActorOperation.values()) {
      String value = operation.getValue();
      assertFalse("Value should not start with uppercase: " + value,
          Character.isUpperCase(value.charAt(0)));
      assertFalse("Value should not contain underscores: " + value,
          value.contains("_"));
      assertFalse("Value should not contain spaces: " + value,
          value.contains(" "));
    }
  }

  // =============================================
  // Enum Uniqueness Tests
  // =============================================

  @Test
  public void testEnumValues_Unique() {
    java.util.Set<String> values = new java.util.HashSet<>();
    for (BulkUploadActorOperation operation : BulkUploadActorOperation.values()) {
      assertTrue("Duplicate operation value found: " + operation.getValue(),
          values.add(operation.getValue()));
    }
  }

  @Test
  public void testEnumNames_Unique() {
    java.util.Set<String> names = new java.util.HashSet<>();
    for (BulkUploadActorOperation operation : BulkUploadActorOperation.values()) {
      assertTrue("Duplicate operation name found: " + operation.name(),
          names.add(operation.name()));
    }
  }

  // =============================================
  // Enum Count Tests
  // =============================================

  @Test
  public void testEnumCount_MoreThanZero() {
    assertTrue("BulkUploadActorOperation should have at least one value",
        BulkUploadActorOperation.values().length > 0);
  }

  // =============================================
  // valueOf Tests
  // =============================================

  @Test
  public void testValueOf_ValidEnumName() {
    BulkUploadActorOperation op = BulkUploadActorOperation.valueOf("USER_BULK_UPLOAD");
    assertEquals(BulkUploadActorOperation.USER_BULK_UPLOAD, op);
  }

  @Test
  public void testValueOf_InvalidEnumName_ThrowsException() {
    assertThrows(IllegalArgumentException.class, () -> {
      BulkUploadActorOperation.valueOf("INVALID_OPERATION");
    });
  }

  @Test
  public void testValues_ReturnAllEnums() {
    BulkUploadActorOperation[] ops = BulkUploadActorOperation.values();
    assertTrue(ops.length > 0);
  }

  // =============================================
  // Comparison Tests
  // =============================================

  @Test
  public void testComparison_SameEnumValue() {
    BulkUploadActorOperation op1 = BulkUploadActorOperation.USER_BULK_UPLOAD;
    BulkUploadActorOperation op2 = BulkUploadActorOperation.USER_BULK_UPLOAD;
    assertEquals(op1, op2);
    assertTrue(op1 == op2);
  }

  @Test
  public void testComparison_DifferentEnumValue() {
    BulkUploadActorOperation op1 = BulkUploadActorOperation.USER_BULK_UPLOAD;
    BulkUploadActorOperation op2 = BulkUploadActorOperation.ORG_BULK_UPLOAD;
    assertNotEquals(op1, op2);
    assertFalse(op1 == op2);
  }

  // =============================================
  // toString Tests
  // =============================================

  @Test
  public void testToString_ContainsEnumName() {
    String str = BulkUploadActorOperation.USER_BULK_UPLOAD.toString();
    assertTrue(str.contains("USER_BULK_UPLOAD"));
  }
}
