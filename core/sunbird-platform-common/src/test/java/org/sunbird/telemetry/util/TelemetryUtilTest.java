package org.sunbird.telemetry.util;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.sunbird.keys.JsonKey;
import org.sunbird.request.Request;

public class TelemetryUtilTest {

  @Test
  public void testGenerateTargetObject() {
    Map<String, Object> target = TelemetryUtil.generateTargetObject("id", "type", "current", "prev");
    assertEquals("id", target.get(JsonKey.ID));
    assertEquals("Type", target.get(JsonKey.TYPE));
    assertEquals("current", target.get(JsonKey.CURRENT_STATE));
    assertEquals("prev", target.get(JsonKey.PREV_STATE));
  }

  @Test
  public void testGenerateTelemetryRequest() {
    Map<String, Object> target = new HashMap<>();
    List<Map<String, Object>> correlated = new ArrayList<>();
    Map<String, Object> params = new HashMap<>();
    Map<String, Object> context = new HashMap<>();
    
    Map<String, Object> requestMap = TelemetryUtil.generateTelemetryRequest(
        target, correlated, "AUDIT", params, context);
    
    assertEquals(target, requestMap.get(JsonKey.TARGET_OBJECT));
    assertEquals(correlated, requestMap.get(JsonKey.CORRELATED_OBJECTS));
    assertEquals("AUDIT", requestMap.get(JsonKey.TELEMETRY_EVENT_TYPE));
    assertEquals(params, requestMap.get(JsonKey.PARAMS));
    assertEquals(context, requestMap.get(JsonKey.CONTEXT));
  }

  @Test
  public void testGenerateCorrelatedObject() {
    List<Map<String, Object>> list = new ArrayList<>();
    TelemetryUtil.generateCorrelatedObject("id", "type", "relation", list);
    
    assertEquals(1, list.size());
    Map<String, Object> obj = list.get(0);
    assertEquals("id", obj.get(JsonKey.ID));
    assertEquals("Type", obj.get(JsonKey.TYPE));
    assertEquals("relation", obj.get(JsonKey.RELATION));
  }

  @Test
  public void testAddTargetObjectRollUp() {
    Map<String, String> rollup = new HashMap<>();
    rollup.put("l1", "v1");
    Map<String, Object> target = new HashMap<>();
    
    TelemetryUtil.addTargetObjectRollUp(rollup, target);
    assertEquals(rollup, target.get(JsonKey.ROLLUP));
  }

  @Test
  public void testTelemetryProcessingCall() {
    try (MockedStatic<TelemetryWriter> mockedWriter = Mockito.mockStatic(TelemetryWriter.class)) {
        Map<String, Object> request = new HashMap<>();
        Map<String, Object> target = new HashMap<>();
        List<Map<String, Object>> correlated = new ArrayList<>();
        Map<String, Object> context = new HashMap<>();
        
        TelemetryUtil.telemetryProcessingCall(request, target, correlated, context);
        
        mockedWriter.verify(() -> TelemetryWriter.write(any(Request.class)));
    }
  }

  @Test
  public void testTelemetryProcessingCallWithType() {
    try (MockedStatic<TelemetryWriter> mockedWriter = Mockito.mockStatic(TelemetryWriter.class)) {
        Map<String, Object> request = new HashMap<>();
        Map<String, Object> target = new HashMap<>();
        List<Map<String, Object>> correlated = new ArrayList<>();
        Map<String, Object> context = new HashMap<>();
        
        TelemetryUtil.telemetryProcessingCall("type", request, target, correlated, context);
        
        mockedWriter.verify(() -> TelemetryWriter.write(any(Request.class)));
    }
  }
}
