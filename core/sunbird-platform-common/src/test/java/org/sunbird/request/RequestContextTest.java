package org.sunbird.request;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;

public class RequestContextTest {

  private RequestContext requestContext;

  @Before
  public void setUp() {
    requestContext = new RequestContext();
  }

  // =============================================
  // Default Constructor Tests
  // =============================================

  @Test
  public void testDefaultConstructor() {
    RequestContext context = new RequestContext();

    assertNull(context.getUid());
    assertNull(context.getDid());
    assertNull(context.getSid());
    assertNull(context.getAppId());
    assertNull(context.getAppVer());
    assertNull(context.getReqId());
    assertNull(context.getSource());
    assertNull(context.getDebugEnabled());
    assertNull(context.getOp());
    assertNull(context.getChannel());
    assertNull(context.getEnv());
    assertNotNull(context.getContextMap());
    assertNotNull(context.getTelemetryContext());
    assertNotNull(context.getPdata());
    assertTrue(context.getContextMap().isEmpty());
    assertTrue(context.getTelemetryContext().isEmpty());
    assertTrue(context.getPdata().isEmpty());
  }

  // =============================================
  // UserOrg Constructor Tests (9 args)
  // =============================================

  @Test
  public void testUserOrgConstructor_AllParametersSet() {
    String uid = "user123";
    String did = "device456";
    String sid = "session789";
    String appId = "app001";
    String appVer = "1.0.0";
    String reqId = "req123";
    String source = "mobile";
    String debugEnabled = "true";
    String op = "createUser";

    RequestContext context =
        new RequestContext(uid, did, sid, appId, appVer, reqId, source, debugEnabled, op);

    // Verify all fields are set
    assertEquals(uid, context.getUid());
    assertEquals(did, context.getDid());
    assertEquals(sid, context.getSid());
    assertEquals(appId, context.getAppId());
    assertEquals(appVer, context.getAppVer());
    assertEquals(reqId, context.getReqId());
    assertEquals(source, context.getSource());
    assertEquals(debugEnabled, context.getDebugEnabled());
    assertEquals(op, context.getOp());
  }

  @Test
  public void testUserOrgConstructor_ContextMapPopulation() {
    String uid = "user123";
    String did = "device456";
    String sid = "session789";
    String appId = "app001";
    String appVer = "1.0.0";
    String reqId = "req123";
    String source = "mobile";
    String debugEnabled = "true";
    String op = "createUser";

    RequestContext context =
        new RequestContext(uid, did, sid, appId, appVer, reqId, source, debugEnabled, op);

    // Verify contextMap is populated with correct internal keys
    assertEquals(uid, context.getContextMap().get("uid"));
    assertEquals(did, context.getContextMap().get("did"));
    assertEquals(sid, context.getContextMap().get("sid"));
    assertEquals(appId, context.getContextMap().get("appId"));
    assertEquals(appVer, context.getContextMap().get("appVer"));
    assertEquals(reqId, context.getContextMap().get("reqId"));
    assertEquals(source, context.getContextMap().get("source"));
    assertEquals(op, context.getContextMap().get("op"));
  }

  @Test
  public void testUserOrgConstructor_NullValues() {
    RequestContext context = new RequestContext(null, null, null, null, null, null, null, null, null);

    assertNull(context.getUid());
    assertNull(context.getContextMap().get("uid"));
    assertNotNull(context.getContextMap()); // Map itself should exist
  }

  // =============================================
  // LMS Constructor Tests (8 args)
  // =============================================

  @Test
  public void testLmsConstructor_AllParametersSet() {
    String channel = "channel001";
    String pdataId = "pdata_id_001";
    String env = "production";
    String did = "device456";
    String sid = "session789";
    String pid = "producer123";
    String pver = "2.0";

    List<Object> cdata = new ArrayList<>();
    cdata.add("correlation1");
    cdata.add("correlation2");

    RequestContext context = new RequestContext(channel, pdataId, env, did, sid, pid, pver, cdata);

    // Verify all fields are set
    assertEquals(channel, context.getChannel());
    assertEquals(env, context.getEnv());
    assertEquals(did, context.getDid());
    assertEquals(sid, context.getSid());
  }

  @Test
  public void testLmsConstructor_PdataPopulation() {
    String channel = "channel001";
    String pdataId = "pdata_id_001";
    String env = "production";
    String did = "device456";
    String sid = "session789";
    String pid = "producer123";
    String pver = "2.0";

    RequestContext context = new RequestContext(channel, pdataId, env, did, sid, pid, pver, null);

    // Verify pdata is populated
    assertEquals(pdataId, context.getPdata().get("id"));
    assertEquals(pid, context.getPdata().get("pid"));
    assertEquals(pver, context.getPdata().get("ver"));
  }

  @Test
  public void testLmsConstructor_ContextMapPopulation_WithoutCdata() {
    String channel = "channel001";
    String pdataId = "pdata_id_001";
    String env = "production";
    String did = "device456";
    String sid = "session789";
    String pid = "producer123";
    String pver = "2.0";

    RequestContext context = new RequestContext(channel, pdataId, env, did, sid, pid, pver, null);

    // Verify contextMap is populated with correct internal keys
    assertEquals(channel, context.getContextMap().get("channel"));
    assertEquals(env, context.getContextMap().get("env"));
    assertEquals(did, context.getContextMap().get("did"));
    assertEquals(sid, context.getContextMap().get("sid"));
    assertNotNull(context.getContextMap().get("pdata"));
    assertFalse(context.getContextMap().containsKey("cdata")); // cdata should not be present
  }

  @Test
  public void testLmsConstructor_ContextMapPopulation_WithCdata() {
    String channel = "channel001";
    String pdataId = "pdata_id_001";
    String env = "production";
    String did = "device456";
    String sid = "session789";
    String pid = "producer123";
    String pver = "2.0";

    List<Object> cdata = new ArrayList<>();
    cdata.add("correlation1");
    cdata.add("correlation2");

    RequestContext context = new RequestContext(channel, pdataId, env, did, sid, pid, pver, cdata);

    // Verify cdata is included in contextMap
    assertEquals(cdata, context.getContextMap().get("cdata"));
  }

  @Test
  public void testLmsConstructor_NullValues() {
    RequestContext context = new RequestContext(null, null, null, null, null, null, null, null);

    assertNull(context.getChannel());
    assertNull(context.getDid());
    assertNotNull(context.getPdata());
    assertNotNull(context.getContextMap());
  }

  // =============================================
  // JSON Aliasing Tests (reqId / requestId)
  // =============================================

  @Test
  public void testJsonAliasing_GetReqId() {
    requestContext.setReqId("req123");
    assertEquals("req123", requestContext.getReqId());
  }

  @Test
  public void testJsonAliasing_SetReqId() {
    requestContext.setReqId("req456");
    assertEquals("req456", requestContext.getReqId());
  }

  @Test
  public void testJsonAliasing_GetRequestId_AliasMethod() {
    requestContext.setReqId("req789");
    // getRequestId() is alias for getReqId()
    assertEquals("req789", requestContext.getRequestId());
  }

  @Test
  public void testJsonAliasing_SetRequestId_AliasMethod() {
    requestContext.setRequestId("req999");
    // Both methods should return the same value
    assertEquals("req999", requestContext.getReqId());
    assertEquals("req999", requestContext.getRequestId());
  }

  @Test
  public void testJsonAliasing_SameInternalField() {
    // Both setReqId and setRequestId should modify the same internal field
    requestContext.setReqId("req111");
    assertEquals("req111", requestContext.getRequestId());

    requestContext.setRequestId("req222");
    assertEquals("req222", requestContext.getReqId());
  }

  // =============================================
  // Common Field Tests
  // =============================================

  @Test
  public void testCommonFields_Uid() {
    requestContext.setUid("user123");
    assertEquals("user123", requestContext.getUid());
  }

  @Test
  public void testCommonFields_Did() {
    requestContext.setDid("device456");
    assertEquals("device456", requestContext.getDid());
  }

  @Test
  public void testCommonFields_Sid() {
    requestContext.setSid("session789");
    assertEquals("session789", requestContext.getSid());
  }

  @Test
  public void testCommonFields_DebugEnabled() {
    requestContext.setDebugEnabled("true");
    assertEquals("true", requestContext.getDebugEnabled());
  }

  @Test
  public void testCommonFields_Op() {
    requestContext.setOp("createUser");
    assertEquals("createUser", requestContext.getOp());
  }

  // =============================================
  // UserOrg Specific Field Tests
  // =============================================

  @Test
  public void testUserOrgFields_AppId() {
    requestContext.setAppId("app001");
    assertEquals("app001", requestContext.getAppId());
  }

  @Test
  public void testUserOrgFields_AppVer() {
    requestContext.setAppVer("1.0.0");
    assertEquals("1.0.0", requestContext.getAppVer());
  }

  @Test
  public void testUserOrgFields_Source() {
    requestContext.setSource("mobile");
    assertEquals("mobile", requestContext.getSource());
  }

  @Test
  public void testUserOrgFields_TelemetryContext() {
    Map<String, Object> telemetryCtx = new HashMap<>();
    telemetryCtx.put("eventId", "evt123");
    requestContext.setTelemetryContext(telemetryCtx);

    assertEquals(telemetryCtx, requestContext.getTelemetryContext());
    assertEquals("evt123", requestContext.getTelemetryContext().get("eventId"));
  }

  // =============================================
  // LMS Specific Field Tests
  // =============================================

  @Test
  public void testLmsFields_Channel() {
    requestContext.setChannel("channel001");
    assertEquals("channel001", requestContext.getChannel());
  }

  @Test
  public void testLmsFields_Env() {
    requestContext.setEnv("production");
    assertEquals("production", requestContext.getEnv());
  }

  @Test
  public void testLmsFields_Pdata() {
    Map<String, Object> pdata = new HashMap<>();
    pdata.put("id", "producer1");
    pdata.put("pid", "parent_producer");
    pdata.put("ver", "1.0");

    requestContext.setPdata(pdata);

    assertEquals(pdata, requestContext.getPdata());
    assertEquals("producer1", requestContext.getPdata().get("id"));
    assertEquals("parent_producer", requestContext.getPdata().get("pid"));
    assertEquals("1.0", requestContext.getPdata().get("ver"));
  }

  // =============================================
  // Notification / LMS Common Attributes Tests
  // =============================================

  @Test
  public void testActorFields_ActorId() {
    requestContext.setActorId("actor123");
    assertEquals("actor123", requestContext.getActorId());
  }

  @Test
  public void testActorFields_ActorType() {
    requestContext.setActorType("Consumer");
    assertEquals("Consumer", requestContext.getActorType());
  }

  @Test
  public void testActorFields_LoggerLevel() {
    requestContext.setLoggerLevel("DEBUG");
    assertEquals("DEBUG", requestContext.getLoggerLevel());
  }

  // =============================================
  // Context Map Tests
  // =============================================

  @Test
  public void testContextMap_SetAndGet() {
    Map<String, Object> contextMap = new HashMap<>();
    contextMap.put("key1", "value1");
    contextMap.put("key2", "value2");

    requestContext.setContextMap(contextMap);

    assertEquals(contextMap, requestContext.getContextMap());
    assertEquals("value1", requestContext.getContextMap().get("key1"));
    assertEquals("value2", requestContext.getContextMap().get("key2"));
  }

  @Test
  public void testContextMap_InitializedAsEmpty() {
    RequestContext context = new RequestContext();
    assertNotNull(context.getContextMap());
    assertTrue(context.getContextMap().isEmpty());
  }

  @Test
  public void testContextMap_MutableAfterConstruction() {
    requestContext.getContextMap().put("dynamic_key", "dynamic_value");
    assertEquals("dynamic_value", requestContext.getContextMap().get("dynamic_key"));
  }

  // =============================================
  // Telemetry Context Tests
  // =============================================

  @Test
  public void testTelemetryContext_SetAndGet() {
    Map<String, Object> telemetryContext = new HashMap<>();
    telemetryContext.put("eventId", "evt123");
    telemetryContext.put("timestamp", System.currentTimeMillis());

    requestContext.setTelemetryContext(telemetryContext);

    assertEquals(telemetryContext, requestContext.getTelemetryContext());
  }

  @Test
  public void testTelemetryContext_InitializedAsEmpty() {
    RequestContext context = new RequestContext();
    assertNotNull(context.getTelemetryContext());
    assertTrue(context.getTelemetryContext().isEmpty());
  }

  // =============================================
  // Integration Tests
  // =============================================

  @Test
  public void testIntegration_UserOrgConstructor_CompleteWorkflow() {
    RequestContext context = new RequestContext("user123", "device456", "session789", "app001",
        "1.0.0", "req123", "mobile", "true", "createUser");

    // Verify all UserOrg specific fields
    assertEquals("user123", context.getUid());
    assertEquals("app001", context.getAppId());
    assertEquals("mobile", context.getSource());

    // Verify contextMap has all values
    assertEquals(8, context.getContextMap().size());
    assertEquals("req123", context.getContextMap().get("reqId"));
  }

  @Test
  public void testIntegration_LmsConstructor_CompleteWorkflow() {
    List<Object> cdata = new ArrayList<>();
    cdata.add("corr1");

    RequestContext context = new RequestContext("channel001", "pdata_id", "prod", "device456",
        "session789", "producer123", "2.0", cdata);

    // Verify all LMS specific fields
    assertEquals("channel001", context.getChannel());
    assertEquals("prod", context.getEnv());
    assertEquals("producer123", context.getPdata().get("pid"));

    // Verify contextMap has pdata and cdata
    assertNotNull(context.getContextMap().get("pdata"));
    assertNotNull(context.getContextMap().get("cdata"));
  }

  @Test
  public void testIntegration_MixedConstructorUsage() {
    // Create with default constructor
    RequestContext context = new RequestContext();

    // Set UserOrg-style fields
    context.setUid("user123");
    context.setAppId("app001");

    // Set LMS-style fields
    context.setChannel("channel001");
    context.setEnv("production");

    // Set common actor fields
    context.setActorId("actor123");

    // Verify all types of fields coexist
    assertEquals("user123", context.getUid());
    assertEquals("app001", context.getAppId());
    assertEquals("channel001", context.getChannel());
    assertEquals("production", context.getEnv());
    assertEquals("actor123", context.getActorId());
  }

  @Test
  public void testIntegration_ContextAndTelemetryContextIndependence() {
    Map<String, Object> contextMap = new HashMap<>();
    contextMap.put("key", "context_value");

    Map<String, Object> telemetryContext = new HashMap<>();
    telemetryContext.put("key", "telemetry_value");

    requestContext.setContextMap(contextMap);
    requestContext.setTelemetryContext(telemetryContext);

    // Should be independent
    assertEquals("context_value", requestContext.getContextMap().get("key"));
    assertEquals("telemetry_value", requestContext.getTelemetryContext().get("key"));
  }
}
