package controllers.device;

import controllers.BaseController;
import java.util.concurrent.CompletionStage;
import javax.inject.Inject;
import javax.inject.Named;
import org.apache.pekko.actor.ActorRef;
import org.sunbird.request.Request;
import play.mvc.Http;
import play.mvc.Result;
import validators.DeviceRegisterRequestValidator;

/**
 * REST controller for device registration endpoint.
 * Handles POST /v1/device/register/:deviceId requests.
 */
public class DeviceController extends BaseController {

  @Inject 
  @Named("device_register_actor") private ActorRef deviceRegisterActorRef;

  /**
   * Register or update a device profile.
   * Extracts path param (deviceId) and headers (IP, User-Agent).
   * Delegates to Pekko actor via ask pattern.
   *
   * @param deviceId Unique device identifier (path param)
   * @param httpRequest HTTP request with body, headers
   * @return Async result (success or error response)
   */
  public CompletionStage<Result> registerDevice(String deviceId, Http.Request httpRequest) {
    return handleRequest(
        deviceRegisterActorRef,
        "registerDevice",
        httpRequest.body().asJson(),
        req -> {
          Request request = (Request) req;
          // Inject deviceId and headers into request
          request.getRequest().put("deviceId", deviceId);
          request.getRequest().put("ip_addr", resolveIp(httpRequest));
          request.getRequest().put("user_agent", httpRequest.header("User-Agent").orElse(""));
          // Validate
          new DeviceRegisterRequestValidator().validate(request);
          return null;
        },
        httpRequest);
  }

  /**
   * Resolve client IP from headers.
   * X-Real-IP (set by load balancer) takes precedence over remote address.
   *
   * @param req HTTP request
   * @return Client IP or empty string
   */
  private String resolveIp(Http.Request req) {
    return req.header("X-Real-IP").orElse(req.header("X-Forwarded-For").orElse(""));
  }
}
