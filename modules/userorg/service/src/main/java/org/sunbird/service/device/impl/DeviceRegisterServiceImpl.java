package org.sunbird.service.device.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;
import org.sunbird.common.ProjectUtil;
import org.sunbird.dao.device.DeviceProfileDao;
import org.sunbird.dao.device.impl.DeviceProfileDaoImpl;
import org.sunbird.kafka.KafkaClient;
import org.sunbird.logging.LoggerUtil;
import org.sunbird.request.Request;
import org.sunbird.response.Response;
import org.sunbird.service.device.DeviceRegisterService;

/**
 * Implementation of DeviceRegisterService.
 * Orchestrates device profile registration: validation, DB write, Kafka publish.
 */
public class DeviceRegisterServiceImpl implements DeviceRegisterService {

  private static final LoggerUtil log = new LoggerUtil(DeviceRegisterServiceImpl.class);
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private final DeviceProfileDao dao;

  public DeviceRegisterServiceImpl() {
    this.dao = new DeviceProfileDaoImpl();
  }

  /**
   * Package-private constructor for testing with mocked DAO.
   */
  DeviceRegisterServiceImpl(DeviceProfileDao dao) {
    this.dao = dao;
  }

  @Override
  public Response registerDevice(Request request) throws Exception {
    Map<String, Object> req = request.getRequest();

    String deviceId = (String) req.get("deviceId");
    String fcmToken = (String) req.get("fcmToken");
    String producerId = (String) req.get("producer");
    String ipAddr = (String) req.get("ip_addr");
    Long firstAccess = toLong(req.get("first_access"));
    Object dspec = req.get("dspec");
    Object userDeclaredLocation = req.get("userDeclaredLocation");
    String userAgent = (String) req.get("user_agent");
    Object uaspec = parseUaSpec(userAgent);

    long now = System.currentTimeMillis();

    // 1. Build DeviceProfile map for DB
    Map<String, Object> profile = new HashMap<>();
    profile.put("device_id", deviceId);
    profile.put("fcm_token", fcmToken);
    profile.put("producer_id", producerId);
    profile.put("api_last_updated_on", now);
    profile.put("first_access", firstAccess != null ? firstAccess : now);
    profile.put("last_access", now);
    profile.put(
        "device_spec", dspec != null ? MAPPER.writeValueAsString(dspec) : null);
    profile.put("uaspec", uaspec != null ? MAPPER.writeValueAsString(uaspec) : null);

    // Geo fields — null for now (Option A: skip geo-resolution)
    profile.put("country_code", null);
    profile.put("country", null);
    profile.put("state_code", null);
    profile.put("state", null);
    profile.put("city", null);
    profile.put("state_custom", null);
    profile.put("state_code_custom", null);
    profile.put("district_custom", null);

    // user-declared location
    if (userDeclaredLocation instanceof Map) {
      Map<?, ?> loc = (Map<?, ?>) userDeclaredLocation;
      profile.put("user_declared_state", loc.get("state"));
      profile.put("user_declared_district", loc.get("district"));
      profile.put("user_declared_on", now);
    }

    // 2. UPSERT to DB (synchronous, in actor thread)
    dao.upsert(profile);
    log.info("DeviceRegisterServiceImpl: Device registered - deviceId=" + deviceId);

    // 3. Publish Kafka event (best-effort, non-blocking failure)
    publishKafkaEvent(profile, now);

    // 4. Build response
    Response response = new Response();
    response.put("message", "Device registered successfully");
    return response;
  }

  /**
   * Publish device profile event to Kafka.
   * Failure is logged but does not fail the API response.
   */
  private void publishKafkaEvent(Map<String, Object> profile, long now) {
    String kafkaEnabled = ProjectUtil.getConfigValue("device_profile_kafka_enabled");
    if (!"true".equalsIgnoreCase(kafkaEnabled)) {
      return;
    }

    try {
      // Build the flat JSON event (same format as Obsrv)
      Map<String, Object> event = new HashMap<>(profile);

      String topic = ProjectUtil.getConfigValue("kafka_topics_deviceprofile");
      String payload = MAPPER.writeValueAsString(event);
      KafkaClient.send(payload, topic);

      log.info(
          "DeviceRegisterServiceImpl: Kafka event published for deviceId=" + profile.get("device_id"));
    } catch (Exception ex) {
      // Kafka failure must NOT fail the API response
      log.error("DeviceRegisterServiceImpl: Failed to publish Kafka event", ex);
    }
  }

  private Long toLong(Object val) {
    if (val instanceof Number) {
      return ((Number) val).longValue();
    }
    return null;
  }

  /**
   * Minimal user agent parsing.
   * Stores raw UA string. Can be enhanced with ua-parser library later.
   */
  private Map<String, String> parseUaSpec(String userAgent) {
    if (userAgent == null || userAgent.isEmpty()) {
      return null;
    }
    Map<String, String> ua = new HashMap<>();
    ua.put("raw", userAgent);
    return ua;
  }
}
