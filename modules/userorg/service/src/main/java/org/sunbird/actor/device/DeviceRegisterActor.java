package org.sunbird.actor.device;

import org.sunbird.actor.core.BaseActor;
import org.sunbird.request.Request;
import org.sunbird.response.Response;
import org.sunbird.service.device.DeviceRegisterService;
import org.sunbird.service.device.impl.DeviceRegisterServiceImpl;
import org.sunbird.telemetry.dto.TelemetryEnvKey;
import org.sunbird.util.Util;

/**
 * Pekko actor for device registration and FCM token management.
 *
 * <p>Handles asynchronous device registration requests and delegates to the service layer for
 * persistence and event publishing. Each device registration updates or creates a device profile
 * in YugabyteSQL and publishes a notification event to Kafka for downstream processing.
 *
 * <p>Supported operations:
 * <ul>
 *   <li>{@code registerDevice} - Register or update a device profile with FCM token and metadata.
 * </ul>
 *
 * @see DeviceRegisterService
 * @see DeviceRegisterServiceImpl
 */
public class DeviceRegisterActor extends BaseActor {

  private DeviceRegisterService deviceRegisterService;

  /**
   * Constructs a DeviceRegisterActor with default DeviceRegisterServiceImpl.
   * Used in production when the actor is instantiated by the Pekko framework.
   */
  public DeviceRegisterActor() {
    this.deviceRegisterService = new DeviceRegisterServiceImpl();
  }

  /**
   * Constructs a DeviceRegisterActor with a provided service for dependency injection.
   * Used in testing to mock the service layer.
   *
   * @param service the DeviceRegisterService implementation to use
   */
  DeviceRegisterActor(DeviceRegisterService service) {
    this.deviceRegisterService = service;
  }

  /**
   * Receives and routes incoming device registration requests.
   *
   * <p>Initializes request context with telemetry metadata, routes the operation to the
   * appropriate handler, and sends the response back to the sender.
   *
   * @param request the incoming request containing operation type and device data
   * @throws Throwable if an error occurs during request processing
   */
  @Override
  public void onReceive(Request request) throws Throwable {
    Util.initializeContext(request, TelemetryEnvKey.USER);
    String operation = request.getOperation();

    switch (operation) {
      case "registerDevice":
        registerDevice(request);
        break;
      default:
        onReceiveUnsupportedOperation();
    }
  }

  /**
   * Registers or updates a device profile with FCM token.
   *
   * <p>Delegates to the service layer to:
   * <ul>
   *   <li>Validate device metadata (deviceId, ip_addr, user_agent, fcmToken)
   *   <li>Upsert device profile in YugabyteSQL
   *   <li>Publish device registration event to Kafka
   * </ul>
   *
   * The response is sent back to the sender asynchronously.
   *
   * @param request the device registration request containing deviceId and profile data
   * @throws Throwable if service layer processing fails
   */
  private void registerDevice(Request request) throws Throwable {
    logger.info(request.getRequestContext(), "DeviceRegisterActor:registerDevice: method called.");
    Response response = deviceRegisterService.registerDevice(request);
    sender().tell(response, self());
  }
}
