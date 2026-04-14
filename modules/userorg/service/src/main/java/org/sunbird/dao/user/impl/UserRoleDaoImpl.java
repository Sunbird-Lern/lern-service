package org.sunbird.dao.user.impl;

import java.util.*;
import org.apache.commons.lang3.StringUtils;
import org.sunbird.cassandra.CassandraOperation;
import org.sunbird.common.CassandraUtil;
import org.sunbird.common.ElasticSearchHelper;
import org.sunbird.common.factory.EsClientFactory;
import org.sunbird.common.inf.ElasticSearchService;
import org.sunbird.dao.user.UserRoleDao;
import org.sunbird.helper.ServiceFactory;
import org.sunbird.keys.JsonKey;
import org.sunbird.logging.LoggerUtil;
import org.sunbird.request.RequestContext;
import org.sunbird.response.Response;
import org.sunbird.common.ProjectUtil;
import java.util.stream.Collectors;
import scala.concurrent.Future;

public final class UserRoleDaoImpl implements UserRoleDao {

  private final LoggerUtil logger = new LoggerUtil(UserRoleDaoImpl.class);
  private static final String TABLE_NAME = JsonKey.USER_ROLES;
  private final CassandraOperation cassandraOperation = ServiceFactory.getInstance();
  private final ElasticSearchService esService = EsClientFactory.getInstance(JsonKey.REST);

  private static UserRoleDaoImpl instance;

  private UserRoleDaoImpl() {}

  public static UserRoleDao getInstance() {
    if (instance == null) {
      instance = new UserRoleDaoImpl();
    }
    return instance;
  }

  @Override
  public Response assignUserRole(List<Map<String, Object>> userRoleMap, RequestContext context) {
    List<Map<String, Object>> translatedList =
        userRoleMap
            .stream()
            .map(m -> CassandraUtil.changeCassandraColumnMapping(m))
            .collect(Collectors.toList());
    return cassandraOperation.batchInsert(
        ProjectUtil.getConfigValue(JsonKey.SUNBIRD_KEYSPACE), TABLE_NAME, translatedList, context);
  }

  @Override
  public Response updateRoleScope(List<Map<String, Object>> userRoleMap, RequestContext context) {
    Response result = null;
    for (Map<String, Object> dataMap : userRoleMap) {
      Map<String, Object> compositeKey = new LinkedHashMap<>(2);
      compositeKey.put(JsonKey.USER_ID, dataMap.remove(JsonKey.USER_ID));
      compositeKey.put(JsonKey.ROLE, dataMap.remove(JsonKey.ROLE));
      result =
          cassandraOperation.updateRecord(
              ProjectUtil.getConfigValue(JsonKey.SUNBIRD_KEYSPACE),
              TABLE_NAME,
              CassandraUtil.changeCassandraColumnMapping(dataMap),
              CassandraUtil.changeCassandraColumnMapping(compositeKey),
              context);
    }
    return result;
  }

  @Override
  public void deleteUserRole(List<Map<String, String>> userRoleMap, RequestContext context) {
    for (Map<String, String> dataMap : userRoleMap) {
      Map<String, Object> translatedMap =
          CassandraUtil.changeCassandraColumnMapping(new HashMap<>(dataMap));
      Map<String, String> finalDeleteMap = new HashMap<>();
      translatedMap.forEach((k, v) -> finalDeleteMap.put(k, (String) v));
      cassandraOperation.deleteRecord(
          ProjectUtil.getConfigValue(JsonKey.SUNBIRD_KEYSPACE), TABLE_NAME, finalDeleteMap, context);
    }
  }

  @Override
  public List<Map<String, Object>> getUserRoles(
      String userId, String role, RequestContext context) {
    Map compositeKeyMap = new HashMap<String, Object>();
    compositeKeyMap.put(JsonKey.USER_ID, userId);
    if (StringUtils.isNotEmpty(role)) {
      compositeKeyMap.put(JsonKey.ROLE, role);
    }
    Response existingRecord =
        cassandraOperation.getRecordById(
            ProjectUtil.getConfigValue(JsonKey.SUNBIRD_KEYSPACE),
            TABLE_NAME,
            CassandraUtil.changeCassandraColumnMapping(compositeKeyMap),
            context);
    List<Map<String, Object>> responseList =
        (List<Map<String, Object>>) existingRecord.get(JsonKey.RESPONSE);

    return responseList;
  }

  @Override
  public boolean updateUserRoleToES(
      String identifier, Map<String, Object> data, RequestContext context) {
    Future<Boolean> responseF =
        esService.update(ProjectUtil.EsType.user.getTypeName(), identifier, data, context);
    if ((boolean) ElasticSearchHelper.getResponseFromFuture(responseF)) {
      return true;
    }
    logger.info(
        context,
        "UserRoleDaoImpl:updateUserRoleToES:unable to save the user role data to ES with identifier "
            + identifier);
    return false;
  }
}
