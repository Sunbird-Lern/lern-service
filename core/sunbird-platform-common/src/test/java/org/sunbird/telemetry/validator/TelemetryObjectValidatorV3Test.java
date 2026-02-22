package org.sunbird.telemetry.validator;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Assert;
import org.junit.Test;
import org.sunbird.keys.JsonKey;
import org.sunbird.logging.ProjectLogger;
import org.sunbird.telemetry.dto.Actor;
import org.sunbird.telemetry.dto.Context;
import org.sunbird.telemetry.dto.Telemetry;
import org.sunbird.telemetry.util.TelemetryEvents;

/**
 * Test class for TelemetryObjectValidatorV3.
 */
public class TelemetryObjectValidatorV3Test {

  private TelemetryObjectValidatorV3 validatorV3 = new TelemetryObjectValidatorV3();
  private ObjectMapper mapper = new ObjectMapper();

  @Test
  public void testAuditWithValidData() {
    Telemetry telemetry = createBaseTelemetry(TelemetryEvents.AUDIT.getName());
    Map<String, Object> auditEdata = new HashMap<>();
    List<String> props = new ArrayList<>();
    props.add("username");
    auditEdata.put(JsonKey.PROPS, props);
    telemetry.setEdata(auditEdata);

    boolean result = false;
    try {
      result = validatorV3.validateAudit(mapper.writeValueAsString(telemetry));
    } catch (JsonProcessingException e) {
      ProjectLogger.log(e.getMessage(), e);
    }
    Assert.assertTrue(result);
  }

  @Test
  public void testAuditWithoutActor() {
    Telemetry telemetry = createBaseTelemetry(TelemetryEvents.AUDIT.getName());
    telemetry.setActor(null);
    Map<String, Object> auditEdata = new HashMap<>();
    telemetry.setEdata(auditEdata);

    boolean result = true;
    try {
      result = validatorV3.validateAudit(mapper.writeValueAsString(telemetry));
    } catch (JsonProcessingException e) {
      ProjectLogger.log(e.getMessage(), e);
    }
    Assert.assertFalse(result);
  }

  @Test
  public void testAuditWithoutChannel() {
    Telemetry telemetry = createBaseTelemetry(TelemetryEvents.AUDIT.getName());
    telemetry.getContext().setChannel(null);
    Map<String, Object> auditEdata = new HashMap<>();
    telemetry.setEdata(auditEdata);

    boolean result = true;
    try {
      result = validatorV3.validateAudit(mapper.writeValueAsString(telemetry));
    } catch (JsonProcessingException e) {
      ProjectLogger.log(e.getMessage(), e);
    }
    Assert.assertFalse(result);
  }

  @Test
  public void testAuditWithoutEnv() {
    Telemetry telemetry = createBaseTelemetry(TelemetryEvents.AUDIT.getName());
    telemetry.getContext().setEnv(null);
    Map<String, Object> auditEdata = new HashMap<>();
    telemetry.setEdata(auditEdata);

    boolean result = true;
    try {
      result = validatorV3.validateAudit(mapper.writeValueAsString(telemetry));
    } catch (JsonProcessingException e) {
      ProjectLogger.log(e.getMessage(), e);
    }
    Assert.assertFalse(result);
  }

  @Test
  public void testAuditWithoutEData() {
    Telemetry telemetry = createBaseTelemetry(TelemetryEvents.AUDIT.getName());
    telemetry.setEdata(null);

    boolean result = true;
    try {
      result = validatorV3.validateAudit(mapper.writeValueAsString(telemetry));
    } catch (JsonProcessingException e) {
      ProjectLogger.log(e.getMessage(), e);
    }
    Assert.assertFalse(result);
  }

  @Test
  public void testSearchWithValidData() {
    Telemetry telemetry = createBaseTelemetry(TelemetryEvents.SEARCH.getName());
    Map<String, Object> searchEdata = new HashMap<>();
    searchEdata.put(JsonKey.TYPE, "user");
    searchEdata.put(JsonKey.QUERY, "{\"filters\":{\"lastName\": \"Test\"}}");
    searchEdata.put(JsonKey.SIZE, 10L);
    searchEdata.put(JsonKey.TOPN, new ArrayList<>());
    telemetry.setEdata(searchEdata);

    boolean result = false;
    try {
      result = validatorV3.validateSearch(mapper.writeValueAsString(telemetry));
    } catch (JsonProcessingException e) {
      ProjectLogger.log(e.getMessage(), e);
    }
    Assert.assertTrue(result);
  }

  @Test
  public void testSearchWithoutQuerySize() {
    Telemetry telemetry = createBaseTelemetry(TelemetryEvents.SEARCH.getName());
    Map<String, Object> searchEdata = new HashMap<>();
    searchEdata.put(JsonKey.TYPE, "user");
    telemetry.setEdata(searchEdata);

    boolean result = true;
    try {
      result = validatorV3.validateSearch(mapper.writeValueAsString(telemetry));
    } catch (JsonProcessingException e) {
      ProjectLogger.log(e.getMessage(), e);
    }
    Assert.assertFalse(result);
  }

  @Test
  public void testLogWithValidData() {
    Telemetry telemetry = createBaseTelemetry(TelemetryEvents.LOG.getName());
    Map<String, Object> logEdata = new HashMap<>();
    logEdata.put(JsonKey.TYPE, "info");
    logEdata.put(JsonKey.LEVEL, JsonKey.API_ACCESS);
    logEdata.put(JsonKey.MESSAGE, "Test message");
    telemetry.setEdata(logEdata);

    boolean result = false;
    try {
      result = validatorV3.validateLog(mapper.writeValueAsString(telemetry));
    } catch (JsonProcessingException e) {
      ProjectLogger.log(e.getMessage(), e);
    }
    Assert.assertTrue(result);
  }

  @Test
  public void testLogWithoutLogLevelType() {
    Telemetry telemetry = createBaseTelemetry(TelemetryEvents.LOG.getName());
    Map<String, Object> logEdata = new HashMap<>();
    logEdata.put(JsonKey.MESSAGE, "");
    telemetry.setEdata(logEdata);

    boolean result = true;
    try {
      result = validatorV3.validateLog(mapper.writeValueAsString(telemetry));
    } catch (JsonProcessingException e) {
      ProjectLogger.log(e.getMessage(), e);
    }
    Assert.assertFalse(result);
  }

  @Test
  public void testErrorWithValidData() {
    Telemetry telemetry = createBaseTelemetry(TelemetryEvents.ERROR.getName());
    Map<String, Object> errorEdata = new HashMap<>();
    errorEdata.put(JsonKey.ERROR, "invalid user");
    errorEdata.put(JsonKey.ERR_TYPE, JsonKey.API_ACCESS);
    errorEdata.put(JsonKey.STACKTRACE, "error msg");
    telemetry.setEdata(errorEdata);

    boolean result = false;
    try {
      result = validatorV3.validateError(mapper.writeValueAsString(telemetry));
    } catch (JsonProcessingException e) {
      ProjectLogger.log(e.getMessage(), e);
    }
    Assert.assertTrue(result);
  }

  @Test
  public void testErrorWithoutErrorTypeStackTrace() {
    Telemetry telemetry = createBaseTelemetry(TelemetryEvents.ERROR.getName());
    Map<String, Object> errorEdata = new HashMap<>();
    telemetry.setEdata(errorEdata);

    boolean result = true;
    try {
      result = validatorV3.validateError(mapper.writeValueAsString(telemetry));
    } catch (JsonProcessingException e) {
      ProjectLogger.log(e.getMessage(), e);
    }
    Assert.assertFalse(result);
  }

  @Test
  public void testInvalidJson() {
    boolean result = validatorV3.validateAudit("invalid json");
    Assert.assertFalse(result);
  }

  @Test
  public void testMissingBasics() {
    Telemetry telemetry = new Telemetry();
    // mid, ver represent default values in Telemetry DTO
    telemetry.setEid(""); 
    
    boolean result = true;
    try {
      result = validatorV3.validateAudit(mapper.writeValueAsString(telemetry));
    } catch (JsonProcessingException e) {
      ProjectLogger.log(e.getMessage(), e);
    }
    Assert.assertFalse(result);
  }

  @Test
  public void testGetInstance() {
    Assert.assertNotNull(TelemetryObjectValidatorV3.getInstance());
  }

  private Telemetry createBaseTelemetry(String eid) {
    Telemetry telemetry = new Telemetry();
    telemetry.setEid(eid);
    telemetry.setMid("dummy msg id");
    telemetry.setVer("3.0");

    Actor actor = new Actor();
    actor.setId("1");
    actor.setType(JsonKey.USER);
    telemetry.setActor(actor);

    Context context = new Context();
    context.setEnv(JsonKey.ORGANISATION);
    context.setChannel("channel");
    telemetry.setContext(context);

    return telemetry;
  }
}
