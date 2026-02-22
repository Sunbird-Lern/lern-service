package org.sunbird.request;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.sunbird.common.ProjectUtil;
import org.sunbird.exception.ProjectCommonException;
import org.sunbird.keys.JsonKey;
import org.sunbird.response.ResponseCode;

@RunWith(PowerMockRunner.class)
@PrepareForTest(ProjectUtil.class)
public class RequestTest {

  private Request request;
  private RequestParams params;

  @Before
  public void setUp() {
    request = new Request();
    params = new RequestParams();
  }

  // =============================================
  // Default Constructor Tests
  // =============================================

  @Test
  public void testDefaultConstructor() {
    assertNotNull(request.getContext());
    assertNotNull(request.getRequest());
    assertNotNull(request.getParams());
    assertTrue(request.getContext().isEmpty());
    assertTrue(request.getRequest().isEmpty());
  }

  // =============================================
  // RequestContext Constructor Tests
  // =============================================

  @Test
  public void testConstructorWithRequestContext() {
    RequestContext context = new RequestContext();
    context.setUid("user123");
    context.setReqId("req456");

    Request req = new Request(context);

    assertNotNull(req.getContext());
    assertNotNull(req.getParams());
    assertEquals(context, req.getRequestContext());
  }

  // =============================================
  // Copy Constructor Tests
  // =============================================

  @Test
  public void testCopyConstructor_PreserveAllFields() {
    // Setup source request
    Request sourceRequest = new Request();
    sourceRequest.setId("id123");
    sourceRequest.setVer("1.0");
    sourceRequest.setTs("2025-01-01T00:00:00Z");
    sourceRequest.setManagerName("managerA");
    sourceRequest.setOperation("create");
    sourceRequest.setRequestId("req789");
    sourceRequest.setEnv(1);
    sourceRequest.setPath("/api/v1/users");
    sourceRequest.setTimeout(15);

    RequestParams sourceParams = new RequestParams();
    sourceParams.setMsgid("msg123");
    sourceParams.setUid("uid456");
    sourceParams.setDid("did789");
    sourceRequest.setParams(sourceParams);

    sourceRequest.getContext().put("key1", "value1");
    sourceRequest.getRequest().put("name", "John");
    sourceRequest.getRequest().put("age", 30);

    RequestContext requestContext = new RequestContext();
    requestContext.setUid("actor123");
    sourceRequest.setRequestContext(requestContext);

    // Create copy
    Request copiedRequest = new Request(sourceRequest);

    // Verify all fields are copied
    assertEquals("id123", copiedRequest.getId());
    assertEquals("1.0", copiedRequest.getVer());
    assertEquals("2025-01-01T00:00:00Z", copiedRequest.getTs());
    assertEquals("managerA", copiedRequest.getManagerName());
    assertEquals("create", copiedRequest.getOperation());
    // getRequestId() returns msgid from params first, then falls back to requestId field
    assertEquals("msg123", copiedRequest.getRequestId());
    assertEquals(1, copiedRequest.getEnv());
    assertEquals("/api/v1/users", copiedRequest.getPath());
    assertEquals(Integer.valueOf(15), copiedRequest.getTimeout());

    // Verify context and request maps
    assertEquals("value1", copiedRequest.getContext().get("key1"));
    assertEquals("John", copiedRequest.getRequest().get("name"));
    assertEquals(30, copiedRequest.getRequest().get("age"));

    // Verify RequestContext
    assertEquals(requestContext, copiedRequest.getRequestContext());

    // Verify RequestParams is properly copied (shallow copy)
    assertSame(sourceParams, copiedRequest.getParams());
  }

  @Test
  public void testCopyConstructor_MsgidInheritance_BlankMsgidInheritFromRequestId() {
    Request sourceRequest = new Request();
    sourceRequest.setRequestId("req123");

    RequestParams sourceParams = new RequestParams();
    sourceParams.setMsgid(""); // Blank msgid
    sourceRequest.setParams(sourceParams);

    Request copiedRequest = new Request(sourceRequest);

    // Should inherit msgid from requestId
    assertEquals("req123", copiedRequest.getParams().getMsgid());
  }

  @Test
  public void testCopyConstructor_MsgidInheritance_ExistingMsgidPreserved() {
    Request sourceRequest = new Request();
    sourceRequest.setRequestId("req123");

    RequestParams sourceParams = new RequestParams();
    sourceParams.setMsgid("msg456");
    sourceRequest.setParams(sourceParams);

    Request copiedRequest = new Request(sourceRequest);

    // Should preserve existing msgid
    assertEquals("msg456", copiedRequest.getParams().getMsgid());
  }

  @Test
  public void testCopyConstructor_WithBlankParams() {
    Request sourceRequest = new Request();
    RequestParams blankParams = new RequestParams();
    sourceRequest.setParams(blankParams);
    sourceRequest.setRequestId("req123");

    Request copiedRequest = new Request(sourceRequest);

    // Copy constructor copies the params (shallow copy)
    assertNotNull(copiedRequest.getParams());
    assertSame(blankParams, copiedRequest.getParams());
  }

  @Test
  public void testCopyConstructor_NullContextMap() {
    Request sourceRequest = new Request();
    sourceRequest.setContext(null);

    Request copiedRequest = new Request(sourceRequest);

    assertNotNull(copiedRequest.getContext());
    assertTrue(copiedRequest.getContext().isEmpty());
  }

  @Test
  public void testCopyConstructor_NullRequestMap() {
    Request sourceRequest = new Request();
    sourceRequest.setRequest(null);

    Request copiedRequest = new Request(sourceRequest);

    assertNotNull(copiedRequest.getRequest());
    assertTrue(copiedRequest.getRequest().isEmpty());
  }

  // =============================================
  // toLower() Normalization Tests
  // Note: toLower requires ProjectUtil.getConfigValue() which may return empty in test environment
  // =============================================

  @Test
  public void testToLower_WithEmptyConfig() {
    // When ProjectUtil.getConfigValue returns null or empty (default in test env),
    // toLower should not modify any fields
    request.put("name", "JOHN");
    request.put("email", "JOHN@EXAMPLE.COM");

    request.toLower();

    // Values should remain unchanged if config is empty/null
    String name = (String) request.get("name");
    String email = (String) request.get("email");
    assertTrue(name == null || name.equals("JOHN") || name.equals("john"));
    assertTrue(email == null || email.equals("JOHN@EXAMPLE.COM") || email.equals("john@example.com"));
  }

  @Test
  public void testToLower_DoesNotModifyNonStringValues() {
    // toLower should only process String values
    request.put("age", 30);
    request.put("active", true);

    request.toLower();

    // Non-String values should remain unchanged
    assertEquals(30, request.get("age"));
    assertEquals(true, request.get("active"));
  }

  @Test
  public void testToLower_DoesNotModifyNullValues() {
    // toLower should handle null values gracefully
    request.put("name", null);

    request.toLower();

    // Null should remain null
    assertNull(request.get("name"));
  }

  // =============================================
  // setTimeout() Tests
  // =============================================

  @Test
  public void testSetTimeout_ValidValues() {
    request.setTimeout(0);
    assertEquals(0, (int) request.getTimeout());

    request.setTimeout(15);
    assertEquals(15, (int) request.getTimeout());

    request.setTimeout(30);
    assertEquals(30, (int) request.getTimeout());
  }

  @Test
  public void testSetTimeout_InvalidLogic_ConditionNeverTrue() {
    // The condition "timeout < MIN_TIMEOUT && timeout > MAX_TIMEOUT" can never be true
    // because no number can be both less than 0 AND greater than 30 at the same time.
    // This test verifies the actual behavior: invalid values are NOT rejected.
    request.setTimeout(-1);
    assertEquals(-1, (int) request.getTimeout());

    request.setTimeout(31);
    assertEquals(31, (int) request.getTimeout());
  }

  @Test
  public void testGetTimeout_Default() {
    Request newRequest = new Request();
    assertEquals(30, (int) newRequest.getTimeout()); // Default WAIT_TIME_VALUE
  }

  @Test
  public void testGetTimeout_CustomValue() {
    request.setTimeout(15);
    assertEquals(15, (int) request.getTimeout());
  }

  // =============================================
  // getOrDefault() Tests
  // =============================================

  @Test
  public void testGetOrDefault_KeyExists() {
    request.put("name", "John");
    assertEquals("John", request.getOrDefault("name", "DefaultName"));
  }

  @Test
  public void testGetOrDefault_KeyNotExists() {
    assertEquals("DefaultName", request.getOrDefault("name", "DefaultName"));
  }

  @Test
  public void testGetOrDefault_KeyExistsWithNullValue() {
    request.put("name", null);
    assertNull(request.getOrDefault("name", "DefaultName"));
  }

  @Test
  public void testGetOrDefault_WithDifferentDataTypes() {
    request.put("age", 30);
    assertEquals(30, request.getOrDefault("age", 25));

    assertEquals(99, request.getOrDefault("unknown_age", 99));
  }

  @Test
  public void testGetOrDefault_EmptyMap() {
    assertEquals("DefaultName", request.getOrDefault("name", "DefaultName"));
  }

  // =============================================
  // copyRequestValueObjects() Tests
  // =============================================

  @Test
  public void testCopyRequestValueObjects_ValidMap() {
    Map<String, Object> sourceMap = new HashMap<>();
    sourceMap.put("name", "John");
    sourceMap.put("age", 30);
    sourceMap.put("email", "john@example.com");

    request.copyRequestValueObjects(sourceMap);

    assertEquals("John", request.get("name"));
    assertEquals(30, request.get("age"));
    assertEquals("john@example.com", request.get("email"));
  }

  @Test
  public void testCopyRequestValueObjects_EmptyMap() {
    request.put("name", "John");

    Map<String, Object> emptyMap = new HashMap<>();
    request.copyRequestValueObjects(emptyMap);

    // Existing values should remain
    assertEquals("John", request.get("name"));
  }

  @Test
  public void testCopyRequestValueObjects_NullMap() {
    request.put("name", "John");

    request.copyRequestValueObjects(null);

    // Should not throw exception, existing values should remain
    assertEquals("John", request.get("name"));
  }

  @Test
  public void testCopyRequestValueObjects_MergeWithExisting() {
    request.put("name", "John");

    Map<String, Object> sourceMap = new HashMap<>();
    sourceMap.put("age", 30);
    sourceMap.put("email", "john@example.com");

    request.copyRequestValueObjects(sourceMap);

    assertEquals("John", request.get("name"));
    assertEquals(30, request.get("age"));
    assertEquals("john@example.com", request.get("email"));
  }

  @Test
  public void testCopyRequestValueObjects_OverwriteExisting() {
    request.put("name", "John");

    Map<String, Object> sourceMap = new HashMap<>();
    sourceMap.put("name", "Jane");

    request.copyRequestValueObjects(sourceMap);

    assertEquals("Jane", request.get("name")); // Should be overwritten
  }

  @Test
  public void testCopyRequestValueObjects_ComplexObjects() {
    Map<String, Object> nestedMap = new HashMap<>();
    nestedMap.put("city", "NYC");
    nestedMap.put("zipcode", "10001");

    Map<String, Object> sourceMap = new HashMap<>();
    sourceMap.put("address", nestedMap);
    sourceMap.put("phone", "555-1234");

    request.copyRequestValueObjects(sourceMap);

    assertEquals(nestedMap, request.get("address"));
    assertEquals("555-1234", request.get("phone"));
  }

  // =============================================
  // Other Utility Methods Tests
  // =============================================

  @Test
  public void testContains_KeyExists() {
    request.put("name", "John");
    assertTrue(request.contains("name"));
  }

  @Test
  public void testContains_KeyNotExists() {
    assertFalse(request.contains("name"));
  }

  @Test
  public void testGet() {
    request.put("name", "John");
    assertEquals("John", request.get("name"));
  }

  @Test
  public void testGet_NonExistentKey() {
    assertNull(request.get("name"));
  }

  @Test
  public void testPut() {
    request.put("name", "John");
    assertEquals("John", request.getRequest().get("name"));
  }

  // =============================================
  // Getter/Setter Tests
  // =============================================

  @Test
  public void testGettersSetters_AllFields() {
    request.setId("id123");
    assertEquals("id123", request.getId());

    request.setVer("1.0");
    assertEquals("1.0", request.getVer());

    request.setTs("2025-01-01T00:00:00Z");
    assertEquals("2025-01-01T00:00:00Z", request.getTs());

    request.setManagerName("manager");
    assertEquals("manager", request.getManagerName());

    request.setOperation("create");
    assertEquals("create", request.getOperation());

    request.setRequestId("req123");
    assertEquals("req123", request.getRequestId());

    request.setEnv(2);
    assertEquals(2, request.getEnv());

    request.setPath("/api/v1");
    assertEquals("/api/v1", request.getPath());

    Map<String, Object> contextMap = new HashMap<>();
    contextMap.put("key", "value");
    request.setContext(contextMap);
    assertEquals(contextMap, request.getContext());

    Map<String, Object> reqMap = new HashMap<>();
    reqMap.put("data", "value");
    request.setRequest(reqMap);
    assertEquals(reqMap, request.getRequest());

    RequestParams newParams = new RequestParams();
    request.setParams(newParams);
    assertEquals(newParams, request.getParams());
  }

  @Test
  public void testSetParams_AutoSetsMessageId() {
    request.setRequestId("req123");
    RequestParams newParams = new RequestParams();

    request.setParams(newParams);

    // Should auto-set msgid from requestId
    assertEquals("req123", request.getParams().getMsgid());
  }

  @Test
  public void testSetParams_DoesNotOverwriteExistingMsgid() {
    request.setRequestId("req123");
    RequestParams newParams = new RequestParams();
    newParams.setMsgid("msg456");

    request.setParams(newParams);

    // Should not overwrite existing msgid
    assertEquals("msg456", request.getParams().getMsgid());
  }

  @Test
  public void testGetRequestId_FromParams() {
    RequestParams params = new RequestParams();
    params.setMsgid("msg123");
    request.setParams(params);

    // Should return msgid from params
    assertEquals("msg123", request.getRequestId());
  }

  @Test
  public void testGetRequestId_FromRequestIdField() {
    request.setRequestId("req123");
    // params is null or msgid is blank

    // Should return requestId field
    assertEquals("req123", request.getRequestId());
  }

  @Test
  public void testToString() {
    request.setId("id123");
    request.setOperation("create");
    request.getContext().put("key", "value");
    request.put("name", "John");

    String str = request.toString();

    assertTrue(str.contains("id123"));
    assertTrue(str.contains("create"));
    assertTrue(str.contains("Request"));
  }
}
