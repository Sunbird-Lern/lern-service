package org.sunbird.telemetry.util;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.powermock.reflect.Whitebox;
import org.sunbird.keys.JsonKey;
import org.sunbird.request.Request;
import org.sunbird.telemetry.collector.TelemetryDataAssembler;
import org.sunbird.telemetry.validator.TelemetryObjectValidator;

public class TelemetryWriterTest {

  @Mock
  private TelemetryDataAssembler assembler;

  @Mock
  private TelemetryObjectValidator validator;

  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);
    Whitebox.setInternalState(TelemetryWriter.class, "telemetryDataAssembler", assembler);
    Whitebox.setInternalState(TelemetryWriter.class, "telemetryObjectValidator", validator);
  }

  @Test
  public void testWriteAudit() {
    Request request = new Request();
    request.put(JsonKey.TELEMETRY_EVENT_TYPE, TelemetryEvents.AUDIT.getName());
    request.put(JsonKey.CONTEXT, new HashMap<String, Object>());
    request.put(JsonKey.TARGET_OBJECT, new HashMap<String, Object>());
    request.put(JsonKey.CORRELATED_OBJECTS, new ArrayList<Map<String, Object>>());
    
    Map<String, Object> params = new HashMap<>();
    params.put(JsonKey.PROPS, new HashMap<String, Object>());
    request.put(JsonKey.PARAMS, params);

    when(assembler.audit(any(), any())).thenReturn("audit telemetry");
    when(validator.validateAudit(anyString())).thenReturn(true);

    TelemetryWriter.write(request);

    verify(assembler, atLeastOnce()).audit(any(), any());
    verify(validator, atLeastOnce()).validateAudit(anyString());
  }

  @Test
  public void testWriteSearch() {
    Request request = new Request();
    request.put(JsonKey.TELEMETRY_EVENT_TYPE, TelemetryEvents.SEARCH.getName());
    request.put(JsonKey.CONTEXT, new HashMap<String, Object>());
    request.put(JsonKey.PARAMS, new HashMap<String, Object>());

    when(assembler.search(any(), any())).thenReturn("search telemetry");
    when(validator.validateSearch(anyString())).thenReturn(true);

    TelemetryWriter.write(request);

    verify(assembler, atLeastOnce()).search(any(), any());
    verify(validator, atLeastOnce()).validateSearch(anyString());
  }

  @Test
  public void testWriteLog() {
    Request request = new Request();
    request.put(JsonKey.TELEMETRY_EVENT_TYPE, TelemetryEvents.LOG.getName());
    request.put(JsonKey.CONTEXT, new HashMap<String, Object>());
    
    Map<String, Object> params = new HashMap<>();
    request.put(JsonKey.PARAMS, params);

    when(assembler.log(any(), any())).thenReturn("log telemetry");
    when(validator.validateLog(anyString())).thenReturn(true);

    TelemetryWriter.write(request);

    verify(assembler, atLeastOnce()).log(any(), any());
    verify(validator, atLeastOnce()).validateLog(anyString());
  }

  @Test
  public void testWriteError() {
    Request request = new Request();
    request.put(JsonKey.TELEMETRY_EVENT_TYPE, TelemetryEvents.ERROR.getName());
    request.put(JsonKey.CONTEXT, new HashMap<String, Object>());
    request.put(JsonKey.PARAMS, new HashMap<String, Object>());

    when(assembler.error(any(), any())).thenReturn("error telemetry");
    when(validator.validateError(anyString())).thenReturn(true);

    TelemetryWriter.write(request);

    verify(assembler, atLeastOnce()).error(any(), any());
    verify(validator, atLeastOnce()).validateError(anyString());
  }

  @Test
  public void testWriteException() {
    Request request = null; 
    TelemetryWriter.write(request);
  }
}
