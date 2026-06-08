package org.sunbird.service.device;

import org.sunbird.request.Request;
import org.sunbird.response.Response;

/**
 * Service interface for device registration.
 * Handles business logic: profile building, DB write, Kafka publish.
 */
public interface DeviceRegisterService {

  /**
   * Register or update a device profile.
   *
   * @param request The device registration request containing deviceId, fcmToken, dspec, etc.
   * @return Response with success message
   * @throws Exception if processing fails
   */
  Response registerDevice(Request request) throws Exception;
}
