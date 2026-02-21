package org.sunbird.actorutil.systemsettings.impl;

import org.apache.pekko.actor.ActorRef;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sunbird.actorutil.InterServiceCommunication;
import org.sunbird.actorutil.InterServiceCommunicationFactory;
import org.sunbird.actorutil.systemsettings.SystemSettingClient;
import org.sunbird.exception.ProjectCommonException;
import org.sunbird.response.Response;
import org.sunbird.operations.lms.ActorOperations;
import org.sunbird.keys.JsonKey;
import org.sunbird.request.Request;
import org.sunbird.response.ResponseCode;
import org.sunbird.models.systemsetting.SystemSetting;

public class SystemSettingClientImpl implements SystemSettingClient {
  private static final Logger logger = LoggerFactory.getLogger(SystemSettingClientImpl.class);

  private static InterServiceCommunication interServiceCommunication =
      InterServiceCommunicationFactory.getInstance();
  private static SystemSettingClient systemSettingClient = null;
  public static SystemSettingClient getInstance() {
    if (null == systemSettingClient) {
      systemSettingClient = new SystemSettingClientImpl();
    }
    return systemSettingClient;
  }

  @Override
  public SystemSetting getSystemSettingByField(ActorRef actorRef, String field) {
    logger.info("SystemSettingClientImpl:getSystemSettingByField: field is {}", field);
    SystemSetting systemSetting = getSystemSetting(actorRef, JsonKey.FIELD, field);
    return systemSetting;
  }

  @Override
  public <T> T getSystemSettingByFieldAndKey(
      ActorRef actorRef, String field, String key, TypeReference typeReference) {
    SystemSetting systemSetting = getSystemSettingByField(actorRef, field);
    ObjectMapper objectMapper = new ObjectMapper();
    if (systemSetting != null) {
      try {
        Map<String, Object> valueMap = objectMapper.readValue(systemSetting.getValue(), Map.class);
        String[] keys = key.split("\\.");
        int numKeys = keys.length;
        for (int i = 0; i < numKeys - 1; i++) {
          valueMap = objectMapper.convertValue(valueMap.get(keys[i]), Map.class);
        }
        return (T)objectMapper.convertValue(valueMap.get(keys[numKeys - 1]), typeReference);
      } catch (Exception e) {
        logger.error("SystemSettingClientImpl:getSystemSettingByFieldAndKey: Exception occurred with error message = {}", 
            e.getMessage(), e);
      }
    }
    return null;
  }

  private SystemSetting getSystemSetting(ActorRef actorRef, String param, Object value) {
    logger.debug("SystemSettingClientImpl: getSystemSetting called");
    Request request = new Request();
    Map<String, Object> map = new HashMap<>();
    map.put(param, value);
    request.setContext(map);
    request.setOperation(ActorOperations.GET_SYSTEM_SETTING.getValue());
    Object obj = interServiceCommunication.getResponse(actorRef, request);

    if (obj instanceof Response) {
      Response responseObj = (Response) obj;
      return (SystemSetting) responseObj.getResult().get(JsonKey.RESPONSE);
    } else if (obj instanceof ProjectCommonException) {
      throw (ProjectCommonException) obj;
    } else {
      throw new ProjectCommonException(
          ResponseCode.SERVER_ERROR.getErrorCode(),
          ResponseCode.SERVER_ERROR.getErrorMessage(),
          ResponseCode.SERVER_ERROR.getResponseCode());
    }
  }
}
