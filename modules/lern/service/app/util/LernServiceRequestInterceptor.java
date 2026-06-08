package util;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.commons.lang3.StringUtils;
import org.sunbird.auth.verifier.AccessTokenValidator;
import org.sunbird.keys.JsonKey;
import org.sunbird.logging.LoggerUtil;
import org.sunbird.request.HeaderParam;
import play.mvc.Http;
import org.sunbird.common.ProjectUtil;

/**
 * LernServiceRequestInterceptor serves as the global request interceptor for the unified
 * Lern service, consolidating authentication and authorization logic from UserOrg, LMS,
 * and Notification modules.
 *
 * It manages:
 * <ul>
 *   <li>Validation of user and client access tokens.</li>
 *   <li>Mapping of restricted URIs that require mandatory authentication.</li>
 *   <li>Management of public routes that bypass header validation.</li>
 *   <li>Identification of 'requested-for' user contexts in managed-user operations.</li>
 * </ul>
 */
public class LernServiceRequestInterceptor {

  private static final LoggerUtil logger = new LoggerUtil(LernServiceRequestInterceptor.class);

  /**
   * List of URIs that require mandatory authentication, regardless of global settings.
   */
  public static List<String> restrictedUriList = null;

  /**
   * Map of URIs that are exempt from standard authentication/header checks (public routes).
   */
  private static final ConcurrentHashMap<String, Short> apiHeaderIgnoreMap = new ConcurrentHashMap<>();

  private LernServiceRequestInterceptor() {}

  static {
    restrictedUriList = new ArrayList<>();
    // From UserOrg
    restrictedUriList.add("/v1/user/update");
    restrictedUriList.add("/v1/note/create");
    restrictedUriList.add("/v1/note/update");
    restrictedUriList.add("/v1/note/search");
    restrictedUriList.add("/v1/note/read");
    restrictedUriList.add("/v1/note/delete");
    restrictedUriList.add("/v1/user/feed");
    // From LMS
    restrictedUriList.add("/v1/content/state/update");

    // ---------------------------
    short var = 1;
    // From UserOrg
    apiHeaderIgnoreMap.put("/v1/user/create", var);
    apiHeaderIgnoreMap.put("/v2/user/create", var);
    apiHeaderIgnoreMap.put("/v2/org/search", var);
    apiHeaderIgnoreMap.put("/v2/org/preferences/read", var);
    apiHeaderIgnoreMap.put("/v3/user/create", var);
    apiHeaderIgnoreMap.put("/v1/user/signup", var);
    apiHeaderIgnoreMap.put("/v1/org/create", var);
    apiHeaderIgnoreMap.put("/v1/system/settings/set", var);
    apiHeaderIgnoreMap.put("/v1/org/update/encryptionkey", var);
    apiHeaderIgnoreMap.put("/v2/org/preferences/create", var);
    apiHeaderIgnoreMap.put("/v2/org/preferences/update", var);
    apiHeaderIgnoreMap.put("/v1/org/assign/key", var);
    apiHeaderIgnoreMap.put("/v2/user/signup", var);
    apiHeaderIgnoreMap.put("/v1/ssouser/create", var);
    apiHeaderIgnoreMap.put("/v1/org/search", var);
    apiHeaderIgnoreMap.put("/service/health", var);
    apiHeaderIgnoreMap.put("/health", var);
    apiHeaderIgnoreMap.put("/v1/notification/email", var);
    apiHeaderIgnoreMap.put("/v2/notification", var);
    apiHeaderIgnoreMap.put("/v1/data/sync", var);
    apiHeaderIgnoreMap.put("/v1/file/upload", var);
    apiHeaderIgnoreMap.put("/v1/user/getuser", var);
    apiHeaderIgnoreMap.put("/v1/org/read", var);
    apiHeaderIgnoreMap.put("/v1/location/create", var);
    apiHeaderIgnoreMap.put("/v1/location/update", var);
    apiHeaderIgnoreMap.put("/v1/location/search", var);
    apiHeaderIgnoreMap.put("/v1/location/delete", var);
    apiHeaderIgnoreMap.put("/v1/otp/generate", var);
    apiHeaderIgnoreMap.put("/v1/otp/verify", var);
    apiHeaderIgnoreMap.put("/v2/otp/generate", var);
    apiHeaderIgnoreMap.put("/v2/otp/verify", var);
    apiHeaderIgnoreMap.put("/v1/user/get/email", var);
    apiHeaderIgnoreMap.put("/v1/user/get/phone", var);
    apiHeaderIgnoreMap.put("/v1/system/settings/get", var);
    apiHeaderIgnoreMap.put("/v1/system/settings/list", var);
    apiHeaderIgnoreMap.put("/private/user/v1/search", var);
    apiHeaderIgnoreMap.put("/private/user/v1/migrate", var);
    apiHeaderIgnoreMap.put("/private/user/v1/identifier/freeup", var);
    apiHeaderIgnoreMap.put("/private/user/v1/password/reset", var);
    apiHeaderIgnoreMap.put("/v1/user/exists/email", var);
    apiHeaderIgnoreMap.put("/v1/user/exists/phone", var);
    apiHeaderIgnoreMap.put("/v1/role/read", var);
    apiHeaderIgnoreMap.put("/v1/user/role/read", var);
    apiHeaderIgnoreMap.put("/private/user/v1/lookup", var);
    apiHeaderIgnoreMap.put("/private/user/feed/v1/create", var);
    apiHeaderIgnoreMap.put("/private/v2/org/search", var);
    apiHeaderIgnoreMap.put("/private/v2/org/preferences/read", var);

    // From LMS
    apiHeaderIgnoreMap.put("/v1/page/assemble", var);
    apiHeaderIgnoreMap.put("/v1/dial/assemble", var);
    apiHeaderIgnoreMap.put("/v1/content/link", var);
    apiHeaderIgnoreMap.put("/v1/content/unlink", var);
    apiHeaderIgnoreMap.put("/v1/content/link/search", var);
    apiHeaderIgnoreMap.put("/v1/course/batch/search", var);
    apiHeaderIgnoreMap.put("/v1/cache/clear", var);
    apiHeaderIgnoreMap.put("/private/v1/course/batch/create", var);
    apiHeaderIgnoreMap.put("/v1/course/create", var);
    apiHeaderIgnoreMap.put("/v2/user/courses/list", var);
    apiHeaderIgnoreMap.put("/v1/collection/summary", var);

    // From Notification
    apiHeaderIgnoreMap.put("/v1/notification/otp/verify", var);
    apiHeaderIgnoreMap.put("/v1/notification/send/sync", var);
    apiHeaderIgnoreMap.put("/v2/notification/send", var);
    apiHeaderIgnoreMap.put("/v1/notification/send", var);

    // From Device Management (public endpoint - mobile devices before login)
    apiHeaderIgnoreMap.put("/v1/device/register", var);
  }

  /**
   * Extracts the user ID for whom the operation is being requested.
   * This is typically found in the request body for POST/PATCH or as a path parameter.
   *
   * @param request The HTTP request object.
   * @return The user ID if found, null otherwise.
   */
  private static String getUserRequestedFor(Http.Request request) {
    String requestedForUserID = null;
    JsonNode jsonBody = request.body().asJson();
    try {
      if (!(jsonBody == null) && !(jsonBody.get(JsonKey.REQUEST) == null)) {
        if (!(jsonBody.get(JsonKey.REQUEST).get(JsonKey.USER_ID) == null)) {
          requestedForUserID = jsonBody.get(JsonKey.REQUEST).get(JsonKey.USER_ID).asText();
        }
      } else {
        String uuidSegment = null;
        Path path = Paths.get(request.uri());
        if (request.queryString().isEmpty()) {
          uuidSegment = path.getFileName().toString();
        } else {
          String[] queryPath = path.getFileName().toString().split("\\?");
          uuidSegment = queryPath[0];
        }
        try {
          if (StringUtils.isNotEmpty(uuidSegment) && ProjectUtil.validateUUID(uuidSegment)) {
            requestedForUserID = UUID.fromString(uuidSegment).toString();
          }
        } catch (IllegalArgumentException iae) {
          logger.error("Perhaps this is another API, like search that doesn't carry user id.", iae);
        }
      }
    } catch (Exception e) {
      logger.error("Likely a possibility? " + request.uri(), e);
    }
    return requestedForUserID;
  }

  /**
   * Verifies the authentication data in the incoming request.
   * Performs validation of either user access tokens or client tokens.
   *
   * @param request The HTTP request to verify.
   * @param requestContext Contextual information for audit and validation.
   * @return A map containing authentication results, including verified user ID and/or managed-for ID.
   */
  public static Map verifyRequestData(Http.Request request, Map<String, Object> requestContext) {
    Map userAuthentication = new HashMap<String, String>();
    userAuthentication.put(JsonKey.USER_ID, JsonKey.UNAUTHORIZED);
    userAuthentication.put(JsonKey.MANAGED_FOR, null);

    String clientId = JsonKey.UNAUTHORIZED;
    String managedForId = null;
    Optional<String> accessToken = request.header(HeaderParam.X_Authenticated_User_Token.getName());
    Optional<String> authClientToken = request.header(HeaderParam.X_Authenticated_Client_Token.getName());
    Optional<String> authClientId = request.header(HeaderParam.X_Authenticated_Client_Id.getName());

    if (!isRequestInExcludeList(request.path()) && !isRequestPrivate(request.path())) {
      // The API must be invoked with either access token or client token.
      if (accessToken.isPresent()) {
        // This is to handle Mobile App expired token for content state update API.
        if (StringUtils.contains(request.path(), "v1/content/state/update")) {
          clientId = AccessTokenValidator.verifyUserToken(accessToken.get(), false);
        } else {
          clientId = AccessTokenValidator.verifyUserToken(accessToken.get(), requestContext);
        }

        if (!JsonKey.USER_UNAUTH_STATES.contains(clientId)) {
          String requestedForUserID = getUserRequestedFor(request);
          if (StringUtils.isNotEmpty(requestedForUserID) && !requestedForUserID.equals(clientId)) {
            Optional<String> forTokenHeader = request.header(HeaderParam.X_Authenticated_For.getName());
            String managedAccessToken = forTokenHeader.isPresent() ? forTokenHeader.get() : "";
            if (StringUtils.isNotEmpty(managedAccessToken)) {
              String managedFor = AccessTokenValidator.verifyManagedUserToken(managedAccessToken, clientId, requestedForUserID, requestContext);
              if (!JsonKey.USER_UNAUTH_STATES.contains(managedFor)) {
                managedForId = managedFor;
              } else {
                clientId = JsonKey.UNAUTHORIZED;
              }
            }
          } else {
            logger.debug("Ignoring x-authenticated-for token...");
          }
        }
        userAuthentication.put(JsonKey.USER_ID, clientId);
        userAuthentication.put(JsonKey.MANAGED_FOR, managedForId);
      } else if (authClientToken.isPresent() && authClientId.isPresent()) {
        // Client Token verification (from LMS/UserOrg)
        clientId = util.AuthenticationHelper.verifyClientAccessToken(authClientId.get(), authClientToken.get());
        if (!JsonKey.UNAUTHORIZED.equals(clientId)) {
          request = request.addAttr(util.Attrs.AUTH_WITH_MASTER_KEY, Boolean.toString(true));
        }
      } else {
        logger.info("Token not present in request: " + request.getHeaders().toMap());
      }
    } else {
      if (accessToken.isPresent()) {
        String clientAccessTokenId = null;
        try {
          // This is to handle Mobile App expired token for content state update API.
          if (StringUtils.contains(request.path(), "v1/content/state/update")) {
            clientAccessTokenId = AccessTokenValidator.verifyUserToken(accessToken.get(), false);
          } else {
            clientAccessTokenId = AccessTokenValidator.verifyUserToken(accessToken.get(), requestContext);
          }
          if (JsonKey.UNAUTHORIZED.equalsIgnoreCase(clientAccessTokenId)) {
            clientAccessTokenId = null;
          }
        } catch (Exception ex) {
          logger.error(ex.getMessage(), ex);
          clientAccessTokenId = null;
        }
        userAuthentication.put(JsonKey.USER_ID, StringUtils.isNotBlank(clientAccessTokenId) ? clientAccessTokenId : JsonKey.ANONYMOUS);
      } else {
        userAuthentication.put(JsonKey.USER_ID, JsonKey.ANONYMOUS);
      }
    }
    return userAuthentication;
  }

  /**
   * Checks if a request path is considered private.
   *
   * @param path The URL path.
   * @return true if the path contains the 'private' segment, false otherwise.
   */
  private static boolean isRequestPrivate(String path) {
    return path.contains(JsonKey.PRIVATE);
  }

  /**
   * Checks if the given URL is in the exclusion list for mandatory header/auth validation.
   *
   * @param requestUrl The URL to check.
   * @return true if the URL is exempt, false otherwise.
   */
  public static boolean isRequestInExcludeList(String requestUrl) {
    boolean resp = false;
    if (!StringUtils.isBlank(requestUrl)) {
      if (apiHeaderIgnoreMap.containsKey(requestUrl)) {
        resp = true;
      } else {
        String[] splitPath = requestUrl.split("[/]");
        String urlWithoutPathParam = removeLastValue(splitPath);
        if (apiHeaderIgnoreMap.containsKey(urlWithoutPathParam)) {
          resp = true;
        }
      }
    }
    return resp;
  }

  /**
   * Helper method to reconstruct a URL path without its final segment.
   *
   * @param splitPath An array of path segments.
   * @return The reconstructed path string.
   */
  private static String removeLastValue(String splitPath[]) {
    StringBuilder builder = new StringBuilder();
    if (splitPath != null && splitPath.length > 0) {
      for (int i = 1; i < splitPath.length - 1; i++) {
        builder.append("/" + splitPath[i]);
      }
    }
    return builder.toString();
  }
}
