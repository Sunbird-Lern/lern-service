package org.sunbird.operations.lms;

import static org.junit.Assert.*;

import org.junit.Test;

public class LocationActorOperationTest {

  // =============================================
  // getValue() Tests
  // =============================================

  @Test
  public void testGetValue_CreateLocation() {
    assertEquals("createLocation", LocationActorOperation.CREATE_LOCATION.getValue());
  }

  @Test
  public void testGetValue_UpdateLocation() {
    assertEquals("updateLocation", LocationActorOperation.UPDATE_LOCATION.getValue());
  }

  @Test
  public void testGetValue_DeleteLocation() {
    assertEquals("deleteLocation", LocationActorOperation.DELETE_LOCATION.getValue());
  }

  @Test
  public void testGetValue_SearchLocation() {
    assertEquals("searchLocation", LocationActorOperation.SEARCH_LOCATION.getValue());
  }

  @Test
  public void testGetValue_GetRelatedLocationIds() {
    assertEquals("getRelatedLocationIds", LocationActorOperation.GET_RELATED_LOCATION_IDS.getValue());
  }

  @Test
  public void testGetValue_ReadLocationType() {
    assertEquals("readLocationType", LocationActorOperation.READ_LOCATION_TYPE.getValue());
  }

  @Test
  public void testGetValue_UpsertLocationToEs() {
    assertEquals("upsertLocationDataToES", LocationActorOperation.UPSERT_LOCATION_TO_ES.getValue());
  }

  @Test
  public void testGetValue_DeleteLocationFromEs() {
    assertEquals("deleteLocationDataFromES", LocationActorOperation.DELETE_LOCATION_FROM_ES.getValue());
  }

  // =============================================
  // All Operations Have Values
  // =============================================

  @Test
  public void testAllOperationsHaveValues() {
    for (LocationActorOperation operation : LocationActorOperation.values()) {
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
    for (LocationActorOperation operation : LocationActorOperation.values()) {
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
    for (LocationActorOperation operation : LocationActorOperation.values()) {
      assertTrue("Duplicate operation value found: " + operation.getValue(),
          values.add(operation.getValue()));
    }
  }

  @Test
  public void testEnumNames_Unique() {
    java.util.Set<String> names = new java.util.HashSet<>();
    for (LocationActorOperation operation : LocationActorOperation.values()) {
      assertTrue("Duplicate operation name found: " + operation.name(),
          names.add(operation.name()));
    }
  }

  // =============================================
  // Enum Count Tests
  // =============================================

  @Test
  public void testEnumCount_MoreThanZero() {
    assertTrue("LocationActorOperation should have at least one value",
        LocationActorOperation.values().length > 0);
  }

  // =============================================
  // LocationActorOperation Specific Operations
  // =============================================

  @Test
  public void testLocationOperations_AllPresent() {
    assertNotNull(LocationActorOperation.CREATE_LOCATION);
    assertNotNull(LocationActorOperation.UPDATE_LOCATION);
    assertNotNull(LocationActorOperation.DELETE_LOCATION);
    assertNotNull(LocationActorOperation.SEARCH_LOCATION);
  }

  // =============================================
  // valueOf Tests
  // =============================================

  @Test
  public void testValueOf_ValidEnumName() {
    LocationActorOperation op = LocationActorOperation.valueOf("CREATE_LOCATION");
    assertEquals(LocationActorOperation.CREATE_LOCATION, op);
  }

  @Test
  public void testValueOf_InvalidEnumName_ThrowsException() {
    assertThrows(IllegalArgumentException.class, () -> {
      LocationActorOperation.valueOf("INVALID_OPERATION");
    });
  }

  @Test
  public void testValues_ReturnAllEnums() {
    LocationActorOperation[] ops = LocationActorOperation.values();
    assertTrue(ops.length > 0);
  }

  // =============================================
  // Comparison Tests
  // =============================================

  @Test
  public void testComparison_SameEnumValue() {
    LocationActorOperation op1 = LocationActorOperation.CREATE_LOCATION;
    LocationActorOperation op2 = LocationActorOperation.CREATE_LOCATION;
    assertEquals(op1, op2);
    assertTrue(op1 == op2);
  }

  @Test
  public void testComparison_DifferentEnumValue() {
    LocationActorOperation op1 = LocationActorOperation.CREATE_LOCATION;
    LocationActorOperation op2 = LocationActorOperation.UPDATE_LOCATION;
    assertNotEquals(op1, op2);
    assertFalse(op1 == op2);
  }

  // =============================================
  // toString Tests
  // =============================================

  @Test
  public void testToString_ContainsEnumName() {
    String str = LocationActorOperation.CREATE_LOCATION.toString();
    assertTrue(str.contains("CREATE_LOCATION"));
  }

  // =============================================
  // CRUD Operation Coverage
  // =============================================

  @Test
  public void testCrud_Create() {
    assertNotNull(LocationActorOperation.CREATE_LOCATION);
    assertEquals("createLocation", LocationActorOperation.CREATE_LOCATION.getValue());
  }

  @Test
  public void testCrud_Read() {
    assertNotNull(LocationActorOperation.SEARCH_LOCATION);
    assertEquals("searchLocation", LocationActorOperation.SEARCH_LOCATION.getValue());
  }

  @Test
  public void testCrud_Update() {
    assertNotNull(LocationActorOperation.UPDATE_LOCATION);
    assertEquals("updateLocation", LocationActorOperation.UPDATE_LOCATION.getValue());
  }

  @Test
  public void testCrud_Delete() {
    assertNotNull(LocationActorOperation.DELETE_LOCATION);
    assertEquals("deleteLocation", LocationActorOperation.DELETE_LOCATION.getValue());
  }
}
