package org.sunbird.dao.device.impl;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Map;
import org.sunbird.dao.device.DeviceProfileDao;
import org.sunbird.db.PostgreSQLConnectionManager;
import org.sunbird.logging.LoggerUtil;

/**
 * Implementation of DeviceProfileDao.
 * Handles UPSERT of device profiles to lern_device_profile table in Sunbird YugabyteSQL.
 */
public class DeviceProfileDaoImpl implements DeviceProfileDao {

  private static final LoggerUtil log = new LoggerUtil(DeviceProfileDaoImpl.class);

  /**
   * UPSERT SQL: INSERT with ON CONFLICT (device_id) DO UPDATE SET.
   * Preserves first_access on conflict (not in UPDATE clause).
   * Uses COALESCE for user_declared_* fields to only overwrite if new value is non-null.
   * Converts epoch-ms timestamps to PostgreSQL timestamptz with to_timestamp(?/1000.0).
   */
  private static final String UPSERT_SQL =
      "INSERT INTO lern_device_profile "
          + "(device_id, fcm_token, producer_id, api_last_updated_on, first_access, last_access, "
          + " device_spec, uaspec, user_declared_state, user_declared_district, user_declared_on, updated_date) "
          + "VALUES (?, ?, ?, to_timestamp(? / 1000.0), to_timestamp(? / 1000.0), to_timestamp(? / 1000.0), "
          + "        ?::json, ?::json, ?, ?, to_timestamp(? / 1000.0), now()) "
          + "ON CONFLICT (device_id) DO UPDATE SET "
          + "  fcm_token             = EXCLUDED.fcm_token, "
          + "  producer_id           = EXCLUDED.producer_id, "
          + "  api_last_updated_on   = EXCLUDED.api_last_updated_on, "
          + "  last_access           = EXCLUDED.last_access, "
          + "  device_spec           = EXCLUDED.device_spec, "
          + "  uaspec                = EXCLUDED.uaspec, "
          + "  user_declared_state   = COALESCE(EXCLUDED.user_declared_state, lern_device_profile.user_declared_state), "
          + "  user_declared_district= COALESCE(EXCLUDED.user_declared_district, lern_device_profile.user_declared_district), "
          + "  user_declared_on      = COALESCE(EXCLUDED.user_declared_on, lern_device_profile.user_declared_on), "
          + "  updated_date          = now()";

  @Override
  public void upsert(Map<String, Object> profile) throws Exception {
    try (Connection conn = PostgreSQLConnectionManager.getInstance().getConnection();
        PreparedStatement stmt = conn.prepareStatement(UPSERT_SQL)) {

      long now = System.currentTimeMillis();
      Long firstAccess =
          profile.get("first_access") != null
              ? ((Number) profile.get("first_access")).longValue()
              : now;
      Long lastAccess = now;
      Long userDeclaredOn =
          profile.get("user_declared_on") != null
              ? ((Number) profile.get("user_declared_on")).longValue()
              : null;

      stmt.setString(1, (String) profile.get("device_id"));
      stmt.setString(2, (String) profile.get("fcm_token"));
      stmt.setString(3, (String) profile.get("producer_id"));
      stmt.setLong(4, now); // api_last_updated_on
      stmt.setLong(5, firstAccess); // first_access
      stmt.setLong(6, lastAccess); // last_access
      stmt.setString(7, (String) profile.get("device_spec")); // JSON string
      stmt.setString(8, (String) profile.get("uaspec")); // JSON string
      stmt.setString(9, (String) profile.get("user_declared_state"));
      stmt.setString(10, (String) profile.get("user_declared_district"));
      stmt.setObject(11, userDeclaredOn); // nullable Long

      stmt.executeUpdate();
      log.info("DeviceProfileDaoImpl: UPSERT successful for deviceId=" + profile.get("device_id"));

    } catch (SQLException ex) {
      log.error("DeviceProfileDaoImpl: UPSERT failed for deviceId=" + profile.get("device_id"), ex);
      throw ex;
    }
  }
}
