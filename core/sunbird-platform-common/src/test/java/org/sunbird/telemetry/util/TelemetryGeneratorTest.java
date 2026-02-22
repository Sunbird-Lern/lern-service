package org.sunbird.telemetry.util;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.powermock.reflect.Whitebox;
import org.sunbird.common.ProjectUtil;
import org.sunbird.keys.JsonKey;
import org.sunbird.telemetry.dto.Context;
import org.sunbird.telemetry.dto.Producer;

public class TelemetryGeneratorTest {

  private static Map<String, Object> context;
  private static Map<String, Object> rollup;

  @Before
  public void setUp() throws Exception {
    context = new HashMap<String, Object>();
    rollup = new HashMap<String, Object>();
    context.put(JsonKey.ACTOR_TYPE, "consumer");
    context.put(JsonKey.PDATA_PID, "learning-service");
    context.put(JsonKey.ACTOR_ID, "X-Consumer-ID");
    context.put(JsonKey.REQUEST_ID, "8e27cbf5-e299-43b0-bca7-8347f7e5abcf");
    context.put(JsonKey.CHANNEL, "ORG_001");
    context.put(JsonKey.PDATA_VERSION, "1.15");
    context.put(JsonKey.ENV, "User");
    context.put(JsonKey.DEVICE_ID, "postman");
  }

  @Test
  public void testGetContextWithoutRollUp()
      throws InvocationTargetException, IllegalAccessException {
    Method method = Whitebox.getMethod(TelemetryGenerator.class, "getContext", Map.class);
    Context ctx = (Context) method.invoke(null, context);
    assertEquals("postman", ctx.getDid());
    assertEquals("ORG_001", ctx.getChannel());
    assertEquals("User", ctx.getEnv());
  }

  @Test
  public void testGetContextWithRollUp() throws InvocationTargetException, IllegalAccessException {
    rollup.put("id", "1");
    context.put(JsonKey.ROLLUP, rollup);
    Method method = Whitebox.getMethod(TelemetryGenerator.class, "getContext", Map.class);
    Context ctx = (Context) method.invoke(null, context);
    assertTrue(rollup.equals(ctx.getRollup()));
  }

  @Test
  public void testRemoveAttributes() throws InvocationTargetException, IllegalAccessException {
    Method method =
        Whitebox.getMethod(TelemetryGenerator.class, "removeAttributes", Map.class, String[].class);
    String[] removableProperty = {JsonKey.DEVICE_ID};
    method.invoke(null, context, (Object) removableProperty);
    assertFalse(context.containsKey(JsonKey.DEVICE_ID));
  }

  @Test()
  public void testGetProducerWithContextNull()
      throws InvocationTargetException, IllegalAccessException {
    Map<String, Object> nullContext = null;
    Method method = Whitebox.getMethod(TelemetryGenerator.class, "getProducer", Map.class);
    Producer producer = (Producer) method.invoke(null, nullContext);
    assertEquals("", producer.getId());
    assertEquals("", producer.getPid());
  }

  @Test
  public void testGetProducerWithAppId() throws InvocationTargetException, IllegalAccessException {
    context.put(JsonKey.APP_ID, "random");
    Method method = Whitebox.getMethod(TelemetryGenerator.class, "getProducer", Map.class);
    Producer producer = (Producer) method.invoke(null, context);
    assertEquals("random", producer.getId());
  }

  @Test
  public void testGetProducerWithoutAppId()
      throws InvocationTargetException, IllegalAccessException {
    context.put(JsonKey.PDATA_ID, "local.sunbird.learning.service");
    Method method = Whitebox.getMethod(TelemetryGenerator.class, "getProducer", Map.class);
    Producer producer = (Producer) method.invoke(null, context);
    assertEquals("local.sunbird.learning.service", producer.getId());
  }

  @Test
  public void testAudit() {
    Map<String, Object> params = new HashMap<>();
    Map<String, Object> targetObject = new HashMap<>();
    targetObject.put(JsonKey.ID, "targetId");
    targetObject.put(JsonKey.TYPE, "User");
    params.put(JsonKey.TARGET_OBJECT, targetObject);
    
    Map<String, Object> props = new HashMap<>();
    props.put("name", "test");
    params.put(JsonKey.PROPS, props);
    
    String event = TelemetryGenerator.audit(context, params);
    assertNotNull(event);
    assertTrue(event.contains("AUDIT"));
  }

  @Test
  public void testSearch() {
    Map<String, Object> params = new HashMap<>();
    params.put(JsonKey.TYPE, "User");
    params.put(JsonKey.QUERY, "test query");
    params.put(JsonKey.SIZE, 10);
    
    String event = TelemetryGenerator.search(context, params);
    assertNotNull(event);
    assertTrue(event.contains("SEARCH"));
  }

  @Test
  public void testLog() {
    Map<String, Object> params = new HashMap<>();
    params.put(JsonKey.LOG_TYPE, "info");
    params.put(JsonKey.LOG_LEVEL, "LOW");
    params.put(JsonKey.MESSAGE, "test log message");
    
    String event = TelemetryGenerator.log(context, params);
    assertNotNull(event);
    assertTrue(event.contains("LOG"));
  }

  @Test
  public void testError() {
    try (MockedStatic<ProjectUtil> mockedProjectUtil = Mockito.mockStatic(ProjectUtil.class)) {
        mockedProjectUtil.when(() -> ProjectUtil.getConfigValue(anyString())).thenReturn("100");
        mockedProjectUtil.when(() -> ProjectUtil.getFirstNCharacterString(anyString(), anyInt())).thenReturn("stacktrace");

        Map<String, Object> params = new HashMap<>();
        params.put(JsonKey.ERROR, "error code");
        params.put(JsonKey.ERR_TYPE, "system");
        params.put(JsonKey.STACKTRACE, "full stacktrace");
        
        String event = TelemetryGenerator.error(context, params);
        assertNotNull(event);
        assertTrue(event.contains("ERROR"));
    }
  }

  @Test
  public void testValidateRequestFailure() throws InvocationTargetException, IllegalAccessException {
    Method method = Whitebox.getMethod(TelemetryGenerator.class, "validateRequest", Map.class, Map.class);
    boolean result = (boolean) method.invoke(null, null, new HashMap<>());
    assertFalse(result);
  }

  @Test
  public void testGetPropsDeeplyNested() throws InvocationTargetException, IllegalAccessException {
    Map<String, Object> map = new HashMap<>();
    Map<String, Object> level1 = new HashMap<>();
    Map<String, Object> level2 = new HashMap<>();
    Map<String, Object> level3 = new HashMap<>();
    Map<String, Object> level4 = new HashMap<>();
    Map<String, Object> level5 = new HashMap<>();
    
    level5.put("key", "value");
    level5.put("nullKey", null);
    level4.put("level5", level5);
    level4.put("emptyMap", new HashMap<>());
    level3.put("level4", level4);
    level2.put("level3", level3);
    level1.put("level2", level2);
    map.put("level1", level1);
    
    Method method = Whitebox.getMethod(TelemetryGenerator.class, "getProps", Map.class);
    List<String> props = (List<String>) method.invoke(null, map);
    
    assertTrue(props.contains("level1.level2.level3.level4.level5.key"));
    assertTrue(props.contains("level1.level2.level3.level4.level5.nullKey"));
    assertFalse(props.contains("level1.level2.level3.level4.emptyMap"));
  }

  @Test
  public void testGetPropsWithNonStringKeys() throws InvocationTargetException, IllegalAccessException {
    Map rawMap = new HashMap();
    rawMap.put(123, "integerKey");
    rawMap.put("stringKey", "value");
    
    Method method = Whitebox.getMethod(TelemetryGenerator.class, "getProps", Map.class);
    List<String> props = (List<String>) method.invoke(null, rawMap);
    assertTrue(props.isEmpty());
  }

  @Test
  public void testAuditParameterLifecycle() {
    Map<String, Object> params = new HashMap<>();
    Map<String, Object> targetObject = new HashMap<>();
    targetObject.put(JsonKey.ID, "targetId");
    targetObject.put(JsonKey.TYPE, "User");
    params.put(JsonKey.TARGET_OBJECT, targetObject);
    
    Map<String, Object> correlatedObject = new HashMap<>();
    correlatedObject.put(JsonKey.ID, "cdataId");
    correlatedObject.put(JsonKey.TYPE, "Content");
    params.put(JsonKey.CORRELATED_OBJECTS, new ArrayList<>(Arrays.asList(correlatedObject)));
    
    Map<String, Object> props = new HashMap<>();
    props.put("name", "test");
    params.put(JsonKey.PROPS, props);
    params.put(JsonKey.TYPE, "audit-type");
    
    String event = TelemetryGenerator.audit(context, params);
    assertNotNull(event);
    
    // Verify targetObject and correlatedObjects are not in edata but are in the event
    assertTrue(event.contains("\"object\":{\"id\":\"targetId\",\"type\":\"User\"}"));
    assertTrue(event.contains("\"cdata\":[{\"id\":\"cdataId\",\"type\":\"Content\"}"));
    
    // Verify edata contains expected fields
    assertTrue(event.contains("\"edata\":{"));
    assertTrue(event.contains("\"props\":[\"name\"]"));
    assertTrue(event.contains("\"type\":\"audit-type\""));
    
    // Verify no duplication of targetObject in edata
    assertFalse(event.contains("\"edata\":{.*\"targetObject\""));
  }

  @Test(expected = Exception.class)
  public void testSetCorrelatedDataWithInvalidInput() throws Exception {
    Method method = Whitebox.getMethod(TelemetryGenerator.class, "setCorrelatedDataToContext", Object.class, Context.class);
    Context eventContext = new Context();

    Map<String, Object> singleMap = new HashMap<>();
    try {
        method.invoke(null, singleMap, eventContext);
    } catch (InvocationTargetException e) {
        throw (Exception) e.getCause();
    }
  }

  @Test
  public void testSetCorrelatedDataWithEmptyList() throws Exception {
    Method method = Whitebox.getMethod(TelemetryGenerator.class, "setCorrelatedDataToContext", Object.class, Context.class);
    Context eventContext = new Context();
    
    method.invoke(null, new ArrayList<>(), eventContext);
    assertTrue(eventContext.getCdata().isEmpty());
  }

  @Test(expected = Exception.class)
  public void testSetCorrelatedDataWithListContainingNull() throws Exception {
    Method method = Whitebox.getMethod(TelemetryGenerator.class, "setCorrelatedDataToContext", Object.class, Context.class);
    Context eventContext = new Context();
    
    ArrayList<Object> list = new ArrayList<>();
    list.add(null);
    try {
        method.invoke(null, list, eventContext);
    } catch (InvocationTargetException e) {
        throw (Exception) e.getCause();
    }
  }

  @Test
  public void testSearchRobustness() {
    Map<String, Object> params = new HashMap<>();
    params.put(JsonKey.TYPE, "User");
    params.put(JsonKey.QUERY, "");
    
    Map<String, Object> filters = new HashMap<>();
    filters.put("status", "active");
    filters.put("roles", Arrays.asList("admin", "editor"));
    params.put(JsonKey.FILTERS, filters);
    
    params.put(JsonKey.SIZE, 0);
    params.put(JsonKey.TOPN, new ArrayList<>());
    
    String event = TelemetryGenerator.search(context, params);
    assertNotNull(event);
    assertTrue(event.contains("\"size\":0"));
    assertTrue(event.contains("\"topn\":[]"));
    assertTrue(event.contains("\"filters\":{\"roles\":[\"admin\",\"editor\"],\"status\":\"active\"}"));
  }

  @AfterClass
  public static void tearDown() throws Exception {
    if (context != null) {
      context.clear();
    }
  }
}
