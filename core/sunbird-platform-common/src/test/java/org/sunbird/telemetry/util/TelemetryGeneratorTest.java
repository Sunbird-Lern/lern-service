package org.sunbird.telemetry.util;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
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

  @AfterClass
  public static void tearDown() throws Exception {
    if (context != null) {
      context.clear();
    }
  }
}
