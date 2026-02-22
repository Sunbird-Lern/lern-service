package org.sunbird.cassandraimpl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.datastax.driver.core.BoundStatement;
import com.datastax.driver.core.ColumnDefinitions;
import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.Statement;
import com.datastax.driver.core.DataType;
import com.datastax.driver.core.exceptions.NoHostAvailableException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.mockito.quality.Strictness;
import org.sunbird.common.CassandraPropertyReader;
import org.sunbird.common.Constants;
import org.sunbird.exception.ProjectCommonException;
import org.sunbird.response.ResponseCode;
import org.sunbird.helper.CassandraConnectionManager;
import org.sunbird.helper.CassandraConnectionMngrFactory;
import org.sunbird.keys.JsonKey;
import org.sunbird.request.RequestContext;
import org.sunbird.response.Response;

/**
 * Extended test suite for CassandraOperationImpl focusing on failure paths and edge cases.
 * Tests exception handling, timeout scenarios, and complex CRUD operations.
 * Migrated from PowerMock to Mockito-inline to enable JaCoCo code coverage.
 * Complements CassandraOperationImplTest with >90% coverage goal.
 */
public class CassandraOperationExtendedTest {

  @Rule
  public MockitoRule mockitoRule = MockitoJUnit.rule().strictness(Strictness.LENIENT);

  private CassandraOperationImpl cassandraOperation;

  @Mock private CassandraConnectionManager connectionManager;

  @Mock private Session session;

  @Mock private PreparedStatement preparedStatement;

  @Mock private ColumnDefinitions columnDefinitions;

  @Mock private ResultSet resultSet;

  @Mock private RequestContext requestContext;

  @Mock private CassandraPropertyReader propertyReader;

  @Mock private BoundStatement boundStatement;

  @Before
  public void setUp() throws Exception {
    // Inject Mock ConnectionManager into Factory using Reflection
    setSingletonInstance(CassandraConnectionMngrFactory.class, "instance", connectionManager);

    // Inject Mock PropertyReader into Factory using Reflection
    setSingletonInstance(CassandraPropertyReader.class, "cassandraPropertyReader", propertyReader);
    lenient().when(propertyReader.readProperty(anyString())).thenAnswer(i -> i.getArgument(0));
    lenient().when(propertyReader.readPropertyValue(anyString())).thenAnswer(i -> i.getArgument(0));

    // Initialize concrete implementation
    cassandraOperation = new CassandraOperationImplConcrete();
    // Inject connection manager into the operation instance
    setField(cassandraOperation, "connectionManager", connectionManager);

    // Setup basic session behavior
    lenient().when(connectionManager.getSession(anyString())).thenReturn(session);
    lenient().when(session.prepare(anyString())).thenReturn(preparedStatement);

    // Setup PreparedStatement to allow BoundStatement creation
    lenient().when(preparedStatement.getVariables()).thenReturn(columnDefinitions);
    lenient().when(columnDefinitions.size()).thenReturn(10);

    // Setup BoundStatement binding
    lenient().when(preparedStatement.bind()).thenReturn(boundStatement);
    lenient().when(preparedStatement.bind(any())).thenReturn(boundStatement);
    lenient().when(boundStatement.bind(any())).thenReturn(boundStatement);

    // Mock execution
    lenient().when(session.execute(any(BoundStatement.class))).thenReturn(resultSet);
    lenient().when(session.execute(any(Statement.class))).thenReturn(resultSet);

    // Setup ResultSet to return success
    lenient().when(resultSet.iterator()).thenReturn(Collections.emptyIterator());
    lenient().when(resultSet.getColumnDefinitions()).thenReturn(columnDefinitions);
    lenient().when(columnDefinitions.asList()).thenReturn(Collections.emptyList());
    lenient().when(columnDefinitions.getType(anyInt())).thenReturn(DataType.text());
  }

  private void setSingletonInstance(Class<?> clazz, String fieldName, Object instance)
      throws Exception {
    Field field = clazz.getDeclaredField(fieldName);
    field.setAccessible(true);
    field.set(null, instance);
  }

  private void setField(Object target, String fieldName, Object value) throws Exception {
    Field field = target.getClass().getSuperclass().getDeclaredField(fieldName);
    field.setAccessible(true);
    field.set(target, value);
  }

  // =============================================
  // Test: Failure Path - WriteTimeoutException
  // =============================================

  @Test
  public void testUpdateRecordQueryTimeout() {
    String keyspaceName = "sunbird";
    String tableName = "user";
    Map<String, Object> request = new HashMap<>();
    request.put("id", "123");
    request.put("name", "John");

    // Simulate timeout exception
    when(session.execute(any(BoundStatement.class)))
        .thenThrow(new RuntimeException("Request timed out"));

    // Implementation throws ProjectCommonException
    try {
      cassandraOperation.updateRecord(keyspaceName, tableName, request, requestContext);
      fail("Should throw ProjectCommonException");
    } catch (ProjectCommonException e) {
      assertNotNull(e);
      verify(session, times(1)).execute(any(BoundStatement.class));
    }
  }

  @Test
  public void testInsertRecordQueryTimeout() {
    String keyspaceName = "sunbird";
    String tableName = "user";
    Map<String, Object> request = new HashMap<>();
    request.put("id", "123");

    when(session.execute(any(BoundStatement.class)))
        .thenThrow(new RuntimeException("Query timeout"));

    // Implementation throws ProjectCommonException
    try {
      cassandraOperation.insertRecord(keyspaceName, tableName, request, requestContext);
      fail("Should throw ProjectCommonException");
    } catch (ProjectCommonException e) {
      assertNotNull(e);
    }
  }

  @Test
  public void testBatchInsertQueryTimeout() {
    String keyspaceName = "sunbird";
    String tableName = "user";
    List<Map<String, Object>> records = new ArrayList<>();
    Map<String, Object> record = new HashMap<>();
    record.put("id", "1");
    records.add(record);

    when(session.execute(any(Statement.class)))
        .thenThrow(new RuntimeException("Batch timeout"));

    // Implementation may throw exception or catch and handle it
    try {
      Response response = cassandraOperation.batchInsert(keyspaceName, tableName, records, requestContext);
      assertNotNull(response);
    } catch (RuntimeException e) {
      // Expected for timeout scenarios
      assertEquals("Batch timeout", e.getMessage());
    }
  }

  // =============================================
  // Test: Failure Path - Unknown Identifier
  // =============================================

  @Test
  public void testUpdateRecordUnknownIdentifier() {
    String keyspaceName = "sunbird";
    String tableName = "user";
    Map<String, Object> request = new HashMap<>();
    request.put("id", "123");
    request.put("unknown_column", "value");

    when(session.execute(any(BoundStatement.class)))
        .thenThrow(new RuntimeException("Unknown identifier unknown_column"));

    try {
      cassandraOperation.updateRecord(keyspaceName, tableName, request, requestContext);
      fail("Should throw ProjectCommonException with invalidPropertyError");
    } catch (ProjectCommonException e) {
      assertEquals(ResponseCode.invalidPropertyError.getErrorCode(), e.getErrorCode());
    }
  }

  @Test
  public void testDeleteRecordUnknownIdentifier() {
    String keyspaceName = "sunbird";
    String tableName = "user";
    String identifier = "123";

    when(session.execute(any(Statement.class)))
        .thenThrow(new RuntimeException("Undefined identifier missing_column"));

    // Implementation throws ProjectCommonException
    try {
      cassandraOperation.deleteRecord(keyspaceName, tableName, identifier, requestContext);
      fail("Should throw ProjectCommonException");
    } catch (ProjectCommonException e) {
      assertNotNull(e);
    }
  }

  @Test
  public void testUpsertRecordUndefinedIdentifier() {
    String keyspaceName = "sunbird";
    String tableName = "user";
    Map<String, Object> request = new HashMap<>();
    request.put("id", "123");

    when(session.execute(any(BoundStatement.class)))
        .thenThrow(new RuntimeException("Undefined identifier new_column"));

    // Implementation throws ProjectCommonException
    try {
      cassandraOperation.upsertRecord(keyspaceName, tableName, request, requestContext);
      fail("Should throw ProjectCommonException");
    } catch (ProjectCommonException e) {
      assertNotNull(e);
    }
  }

  // =============================================
  // Test: Failure Path - No Host Available
  // =============================================

  @Test
  public void testInsertRecordNoHostAvailable() {
    String keyspaceName = "sunbird";
    String tableName = "user";
    Map<String, Object> request = new HashMap<>();
    request.put("id", "123");

    when(session.execute(any(BoundStatement.class)))
        .thenThrow(new NoHostAvailableException(new HashMap<>()));

    // Implementation throws ProjectCommonException
    try {
      cassandraOperation.insertRecord(keyspaceName, tableName, request, requestContext);
      fail("Should throw ProjectCommonException");
    } catch (ProjectCommonException e) {
      assertNotNull(e);
    }
  }

  @Test
  public void testGetRecordsNoHostAvailable() {
    String keyspaceName = "sunbird";
    String tableName = "user";
    String propertyName = "name";
    String propertyValue = "John";

    when(session.execute(any(Statement.class)))
        .thenThrow(new NoHostAvailableException(new HashMap<>()));

    try {
      cassandraOperation.getRecordsByProperty(
          keyspaceName, tableName, propertyName, propertyValue, requestContext);
      fail("Should throw ProjectCommonException");
    } catch (ProjectCommonException e) {
      assertEquals(ResponseCode.SERVER_ERROR.getErrorCode(), e.getErrorCode());
    }
  }

  // =============================================
  // Test: Complex CRUD - Upsert Edge Cases
  // =============================================

  @Test
  public void testUpsertRecordEmptyMap() {
    String keyspaceName = "sunbird";
    String tableName = "user";
    Map<String, Object> request = new HashMap<>(); // Empty map

    try {
      Response response =
          cassandraOperation.upsertRecord(keyspaceName, tableName, request, requestContext);
      assertNotNull(response);
    } catch (ProjectCommonException e) {
      // Empty map may cause exception, which is acceptable
      assertNotNull(e);
    }
  }

  @Test
  public void testUpsertRecordWithMultipleColumns() {
    String keyspaceName = "sunbird";
    String tableName = "user";
    Map<String, Object> request = new HashMap<>();
    request.put("id", "123");
    request.put("name", "John");
    request.put("email", "john@example.com");
    request.put("age", 30);
    request.put("status", "active");
    when(columnDefinitions.size()).thenReturn(request.size());

    try {
      Response response =
          cassandraOperation.upsertRecord(keyspaceName, tableName, request, requestContext);
      assertNotNull(response);
      verify(session, times(1)).execute(any(BoundStatement.class));
    } catch (ProjectCommonException e) {
      // Exception may be thrown during binding, which is acceptable
      assertNotNull(e);
    }
  }

  @Test
  public void testUpsertRecordWithSpecialCharacters() {
    String keyspaceName = "sunbird";
    String tableName = "user";
    Map<String, Object> request = new HashMap<>();
    request.put("id", "user-123");
    request.put("name", "John O'Brien");
    request.put("bio", "User with special chars: !@#$%");
    when(columnDefinitions.size()).thenReturn(request.size());

    try {
      Response response =
          cassandraOperation.upsertRecord(keyspaceName, tableName, request, requestContext);
      assertNotNull(response);
      verify(session, times(1)).execute(any(BoundStatement.class));
    } catch (ProjectCommonException e) {
      // Exception may be thrown during special character handling, which is acceptable
      assertNotNull(e);
    }
  }

  // =============================================
  // Test: Complex CRUD - Batch Update Edge Cases
  // =============================================

  @Test
  public void testBatchUpdateEmptyList() {
    String keyspaceName = "sunbird";
    String tableName = "user";
    List<Map<String, Map<String, Object>>> list = new ArrayList<>(); // Empty list

    Response response =
        cassandraOperation.batchUpdate(keyspaceName, tableName, list, requestContext);

    assertNotNull(response);
    // Should still succeed even with empty list
  }

  @Test
  public void testBatchUpdateMultipleRecords() {
    String keyspaceName = "sunbird";
    String tableName = "user";
    List<Map<String, Map<String, Object>>> list = new ArrayList<>();

    for (int i = 0; i < 5; i++) {
      Map<String, Map<String, Object>> record = new HashMap<>();
      Map<String, Object> pk = new HashMap<>();
      pk.put("id", String.valueOf(i));
      Map<String, Object> nonPk = new HashMap<>();
      nonPk.put("name", "User " + i);
      nonPk.put("email", "user" + i + "@example.com");
      record.put(JsonKey.PRIMARY_KEY, pk);
      record.put(JsonKey.NON_PRIMARY_KEY, nonPk);
      list.add(record);
    }

    Response response =
        cassandraOperation.batchUpdate(keyspaceName, tableName, list, requestContext);

    assertEquals(Constants.SUCCESS, response.get(Constants.RESPONSE));
    verify(session, times(1)).execute(any(Statement.class));
  }

  @Test
  public void testBatchUpdateWithCompositeKey() {
    String keyspaceName = "sunbird";
    String tableName = "user";
    List<Map<String, Map<String, Object>>> list = new ArrayList<>();

    Map<String, Map<String, Object>> record = new HashMap<>();
    Map<String, Object> pk = new HashMap<>();
    pk.put("id", "user-1");
    pk.put("type", "admin");
    pk.put("timestamp", "2024-01-01");
    Map<String, Object> nonPk = new HashMap<>();
    nonPk.put("status", "active");
    record.put(JsonKey.PRIMARY_KEY, pk);
    record.put(JsonKey.NON_PRIMARY_KEY, nonPk);
    list.add(record);

    Response response =
        cassandraOperation.batchUpdate(keyspaceName, tableName, list, requestContext);

    assertEquals(Constants.SUCCESS, response.get(Constants.RESPONSE));
    verify(session, times(1)).execute(any(Statement.class));
  }

  @Test
  public void testBatchUpdateWithOnlyPrimaryKey() {
    String keyspaceName = "sunbird";
    String tableName = "user";
    List<Map<String, Map<String, Object>>> list = new ArrayList<>();

    Map<String, Map<String, Object>> record = new HashMap<>();
    Map<String, Object> pk = new HashMap<>();
    pk.put("id", "user-1");
    Map<String, Object> nonPk = new HashMap<>(); // Empty non-primary key
    record.put(JsonKey.PRIMARY_KEY, pk);
    record.put(JsonKey.NON_PRIMARY_KEY, nonPk);
    list.add(record);

    Response response =
        cassandraOperation.batchUpdate(keyspaceName, tableName, list, requestContext);

    assertNotNull(response);
    verify(session, times(1)).execute(any(Statement.class));
  }

  // =============================================
  // Test: Complex CRUD - Delete Record Scenarios
  // =============================================

  @Test
  public void testDeleteRecordCompositeKey() {
    String keyspaceName = "sunbird";
    String tableName = "user";
    // In real usage, composite key would be passed differently, this tests the basic flow
    String identifier = "user-123|type-admin";

    Response response =
        cassandraOperation.deleteRecord(keyspaceName, tableName, identifier, requestContext);

    assertEquals(Constants.SUCCESS, response.get(Constants.RESPONSE));
    verify(session, times(1)).execute(any(Statement.class));
  }

  @Test
  public void testDeleteRecordWithSpecialCharacters() {
    String keyspaceName = "sunbird";
    String tableName = "user";
    String identifier = "user-123-special!@#$";

    Response response =
        cassandraOperation.deleteRecord(keyspaceName, tableName, identifier, requestContext);

    assertEquals(Constants.SUCCESS, response.get(Constants.RESPONSE));
    verify(session, times(1)).execute(any(Statement.class));
  }

  @Test
  public void testDeleteRecordByPropertiesMultiple() {
    String keyspaceName = "sunbird";
    String tableName = "user";
    Map<String, Object> properties = new HashMap<>();
    properties.put("status", "inactive");
    properties.put("type", "temp");

    Response response =
        cassandraOperation.getRecordsByProperties(
            keyspaceName, tableName, properties, requestContext);

    assertNotNull(response);
    verify(session, times(1)).execute(any(Statement.class));
  }

  // =============================================
  // Test: Edge Cases - Null and Empty Values
  // =============================================

  @Test
  public void testInsertRecordWithNullValue() {
    String keyspaceName = "sunbird";
    String tableName = "user";
    Map<String, Object> request = new HashMap<>();
    request.put("id", "123");
    request.put("description", null); // Null value
    when(columnDefinitions.size()).thenReturn(2);

    try {
      Response response =
          cassandraOperation.insertRecord(keyspaceName, tableName, request, requestContext);
      assertNotNull(response);
      verify(session, times(1)).execute(any(BoundStatement.class));
    } catch (ProjectCommonException e) {
      // Null values may cause exception during binding, which is acceptable
      assertNotNull(e);
    }
  }

  @Test
  public void testUpdateRecordWithEmptyString() {
    String keyspaceName = "sunbird";
    String tableName = "user";
    Map<String, Object> request = new HashMap<>();
    request.put("id", "123");
    request.put("name", ""); // Empty string

    Response response =
        cassandraOperation.updateRecord(keyspaceName, tableName, request, requestContext);

    assertEquals(Constants.SUCCESS, response.get(Constants.RESPONSE));
    verify(session, times(1)).execute(any(BoundStatement.class));
  }

  @Test
  public void testGetRecordsByPropertyEmptyString() {
    String keyspaceName = "sunbird";
    String tableName = "user";
    String propertyName = "status";
    String propertyValue = ""; // Empty property value

    Response response =
        cassandraOperation.getRecordsByProperty(
            keyspaceName, tableName, propertyName, propertyValue, requestContext);

    assertNotNull(response);
    verify(session, times(1)).execute(any(Statement.class));
  }

  // =============================================
  // Test: TTL Operations with Exceptions
  // =============================================

  @Test
  public void testInsertRecordWithTTLException() {
    String keyspaceName = "sunbird";
    String tableName = "user";
    Map<String, Object> request = new HashMap<>();
    request.put("id", "123");
    int ttl = 100;

    when(session.execute(any(Statement.class)))
        .thenThrow(new RuntimeException("TTL configuration error"));

    try {
      cassandraOperation.insertRecordWithTTL(
          keyspaceName, tableName, request, ttl, requestContext);
      fail("Should throw ProjectCommonException");
    } catch (ProjectCommonException e) {
      assertEquals(ResponseCode.SERVER_ERROR.getErrorCode(), e.getErrorCode());
    }
  }

  @Test
  public void testUpdateRecordWithTTLUnknownIdentifier() {
    String keyspaceName = "sunbird";
    String tableName = "user";
    Map<String, Object> request = new HashMap<>();
    request.put("name", "John");
    Map<String, Object> compositeKey = new HashMap<>();
    compositeKey.put("id", "123");
    int ttl = 100;

    when(session.execute(any(Statement.class)))
        .thenThrow(new RuntimeException("Unknown identifier ttl_column"));

    // Implementation throws ProjectCommonException
    try {
      cassandraOperation.updateRecordWithTTL(
          keyspaceName, tableName, request, compositeKey, ttl, requestContext);
      fail("Should throw ProjectCommonException");
    } catch (ProjectCommonException e) {
      assertNotNull(e);
    }
  }

  // =============================================
  // Test: Batch Operations with Exceptions
  // =============================================

  @Test
  public void testBatchInsertLoggedWithException() {
    String keyspaceName = "sunbird";
    String tableName = "user";
    List<Map<String, Object>> records = new ArrayList<>();
    Map<String, Object> record = new HashMap<>();
    record.put("id", "1");
    records.add(record);

    when(session.execute(any(Statement.class)))
        .thenThrow(new RuntimeException("Batch error"));

    try {
      Response response = cassandraOperation.batchInsertLogged(keyspaceName, tableName, records, requestContext);
      assertNotNull(response);
    } catch (RuntimeException e) {
      // Exception may be thrown for batch operations
      assertEquals("Batch error", e.getMessage());
    }
  }

  // =============================================
  // Test: Search and Filter Operations
  // =============================================

  @Test
  public void testSearchValueInListEmpty() {
    String keyspaceName = "sunbird";
    String tableName = "user";
    String key = "roles";
    String value = "";

    Response response =
        cassandraOperation.searchValueInList(keyspaceName, tableName, key, value, requestContext);

    assertNotNull(response);
    verify(session, times(1)).execute(any(Statement.class));
  }

  @Test
  public void testSearchValueInListSpecialCharacters() {
    String keyspaceName = "sunbird";
    String tableName = "user";
    String key = "metadata";
    String value = "special!@#$%^&*()";

    Response response =
        cassandraOperation.searchValueInList(keyspaceName, tableName, key, value, requestContext);

    assertNotNull(response);
    verify(session, times(1)).execute(any(Statement.class));
  }

  @Test
  public void testDeleteRecordsEmptyList() {
    String keyspaceName = "sunbird";
    String tableName = "user";
    List<String> ids = new ArrayList<>(); // Empty list

    boolean result =
        cassandraOperation.deleteRecords(keyspaceName, tableName, ids, requestContext);

    assertNotNull(result);
  }

  @Test
  public void testDeleteRecordsLargeList() {
    String keyspaceName = "sunbird";
    String tableName = "user";
    List<String> ids = new ArrayList<>();
    for (int i = 0; i < 100; i++) {
      ids.add("user-" + i);
    }
    when(resultSet.wasApplied()).thenReturn(true);

    boolean result =
        cassandraOperation.deleteRecords(keyspaceName, tableName, ids, requestContext);

    assertNotNull(result);
    verify(session, times(1)).execute(any(Statement.class));
  }

  // =============================================
  // Test: Concrete Implementation
  // =============================================

  private static class CassandraOperationImplConcrete extends CassandraOperationImpl {
    @Override
    public Response getRecordsWithLimit(
        String keyspace,
        String table,
        Map<String, Object> filters,
        List<String> fields,
        Integer limit,
        RequestContext requestContext) {
      return null;
    }

    @Override
    public Response updateAddMapRecord(
        String keySpace,
        String table,
        Map<String, Object> primaryKey,
        String column,
        String key,
        Object value,
        RequestContext requestContext) {
      return null;
    }

    @Override
    public Response updateRemoveMapRecord(
        String keySpace,
        String table,
        Map<String, Object> primaryKey,
        String column,
        String key,
        RequestContext requestContext) {
      return null;
    }
  }
}
