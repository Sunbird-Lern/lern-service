package org.sunbird.dao.device;

import java.util.Map;

/**
 * Data Access Object interface for device profiles.
 * Handles UPSERT operations to YugabyteSQL database.
 */
public interface DeviceProfileDao {

  /**
   * UPSERT device profile to database.
   * On conflict (device_id exists): updates all fields except first_access (preserved).
   *
   * @param profile Map containing device profile fields
   * @throws Exception if database operation fails
   */
  void upsert(Map<String, Object> profile) throws Exception;
}
