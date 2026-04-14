package modules;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.typesafe.config.ConfigFactory;
import controllers.BaseController;
import org.apache.commons.lang3.StringUtils;
import org.sunbird.cache.platform.Platform;
import org.sunbird.exception.ProjectCommonException;
import org.sunbird.response.Response;
import org.sunbird.keys.JsonKey;
import org.sunbird.logging.LoggerUtil;
import org.sunbird.common.ProjectUtil;
import org.sunbird.request.HeaderParam;
import org.sunbird.response.ResponseCode;
import org.sunbird.util.DataCacheHandler;
import org.sunbird.utils.JsonUtil;
import play.http.ActionCreator;
import play.libs.Json;
import play.mvc.Action;
import play.mvc.Http;
import play.mvc.Result;
import play.mvc.Results;
import util.Attrs;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

public class OnRequestHandler implements ActionCreator {

    private ObjectMapper mapper = new ObjectMapper();
    public static boolean isServiceHealthy = true;
    private final List<String> USER_UNAUTH_STATES = Arrays.asList(JsonKey.UNAUTHORIZED, JsonKey.ANONYMOUS);
    public LoggerUtil logger = new LoggerUtil(this.getClass());
    private static final List<String> clientAppHeaderKeys = Platform.getStringList("request_headers_logging", Arrays.asList("x-app-id", "x-device-id", "x-channel-id"));

    @Override
    public Action createAction(Http.Request request, Method actionMethod) {
        Optional<String> optionalMessageId = request.header(JsonKey.MESSAGE_ID);
        String messageId;
        if (optionalMessageId.isPresent()) {
            messageId = optionalMessageId.get();
        } else {
            UUID uuid = UUID.randomUUID();
            messageId = uuid.toString();
        }

        return new Action.Simple() {
            @Override
            public CompletionStage<Result> call(Http.Request request) {
                CompletionStage<Result> result = checkForServiceHealth(request);
                if (result != null) {
                    return result;
                }

                // Authenticate the request via the unified interceptor which handles both
                // standard user tokens and managed-user (X-Authenticated-For) tokens internally.
                Map userAuthentication;
                if (ConfigFactory.load().getBoolean(JsonKey.AUTH_ENABLED)) {
                    userAuthentication = util.LernServiceRequestInterceptor.verifyRequestData(request, new HashMap<>());
                } else {
                    userAuthentication = new HashMap<>();
                    userAuthentication.put(JsonKey.USER_ID, JsonKey.ANONYMOUS);
                    userAuthentication.put(JsonKey.MANAGED_FOR, null);
                }

                String message = (String) userAuthentication.get(JsonKey.USER_ID);
                String managedFor = (String) userAuthentication.get(JsonKey.MANAGED_FOR);

                String loggingHeaders = getLoggingHeaders(request);
                request = request.addAttr(Attrs.X_LOGGING_HEADERS, loggingHeaders);

                // Set managed-user attributes when the interceptor resolved a valid managed user.
                if (managedFor != null && !USER_UNAUTH_STATES.contains(managedFor)) {
                    request = request.addAttr(Attrs.REQUESTED_FOR, managedFor);
                    request = request.addAttr(Attrs.MANAGED_FOR, managedFor);
                    request = request.addAttr(utils.module.Attrs.MANAGED_FOR, managedFor);
                }

                request = intializeRequestInfo(request, message, messageId);
                request = request.addAttr(Attrs.X_AUTH_TOKEN, request.header(HeaderParam.X_Authenticated_User_Token.getName()).orElse(""));

                if (!USER_UNAUTH_STATES.contains(message)) {
                    request = request.addAttr(Attrs.USER_ID, message);
                    request = request.addAttr(utils.module.Attrs.USERID, message);
                    request = request.addAttr(Attrs.IS_AUTH_REQ, "false");
                    for (String uri : util.LernServiceRequestInterceptor.restrictedUriList) {
                        if (request.path().contains(uri)) {
                            request = request.addAttr(Attrs.IS_AUTH_REQ, "true");
                            break;
                        }
                    }
                    result = delegate.call(request);
                } else if (JsonKey.UNAUTHORIZED.equals(message)) {
                    result = onDataValidationError(request, JsonKey.UNAUTHORIZED, ResponseCode.UNAUTHORIZED.getResponseCode());
                } else {
                    result = delegate.call(request);
                }
                return result.thenApply(res -> res.withHeader("Access-Control-Allow-Origin", "*"));
            }
        };
    }

    public CompletionStage<Result> onDataValidationError(Http.Request request, String errorMessage, int responseCode) {
        logger.info("Data error found--" + errorMessage);
        ResponseCode code = ResponseCode.getResponse(errorMessage);
        ResponseCode headerCode = ResponseCode.CLIENT_ERROR;
        Response resp = BaseController.createFailureResponse(request, code, headerCode);
        return CompletableFuture.completedFuture(Results.status(responseCode, Json.toJson(resp)));
    }

    private Http.Request intializeRequestInfo(Http.Request request, String userId, String requestId) {
        try {
            String actionMethod = request.method();
            String url = request.uri();
            String methodName = actionMethod;
            long startTime = System.currentTimeMillis();
            String signType = "";
            String source = "";
            if (request.body() != null && request.body().asJson() != null) {
                JsonNode requestNode = request.body().asJson().get("params");
                if (requestNode != null && requestNode.get(JsonKey.SIGNUP_TYPE) != null) {
                    signType = requestNode.get(JsonKey.SIGNUP_TYPE).asText();
                }
                if (requestNode != null && requestNode.get(JsonKey.REQUEST_SOURCE) != null) {
                    source = requestNode.get(JsonKey.REQUEST_SOURCE).asText();
                }
            }
            Map<String, Object> reqContext = new HashMap<>();
            request = request.addAttr(Attrs.SIGNUP_TYPE, signType);
            reqContext.put(JsonKey.SIGNUP_TYPE, signType);
            request = request.addAttr(Attrs.REQUEST_SOURCE, source);
            reqContext.put(JsonKey.REQUEST_SOURCE, source);

            Optional<String> optionalChannel = request.header(HeaderParam.CHANNEL_ID.getName());
            String channel;
            if (optionalChannel.isPresent()) {
                channel = optionalChannel.get();
            } else {
                String sunbirdDefaultChannel = ProjectUtil.getConfigValue(JsonKey.SUNBIRD_DEFAULT_CHANNEL);
                channel = (StringUtils.isNotEmpty(sunbirdDefaultChannel)) ? sunbirdDefaultChannel : JsonKey.DEFAULT_ROOT_ORG_ID;
            }
            reqContext.put(JsonKey.CHANNEL, channel);
            request = request.addAttr(Attrs.CHANNEL, channel);
            reqContext.put(JsonKey.ENV, getEnv(request));
            reqContext.put(JsonKey.REQUEST_ID, requestId);
            reqContext.put(JsonKey.REQUEST_TYPE, JsonKey.API_CALL);
            reqContext.put(JsonKey.REQUEST_MESSAGE_ID, requestId);

            // Telemetry producer data (populated at startup by DataCacheHandler)
            Map<String, Object> telemetryPdata = DataCacheHandler.getTelemetryPdata();
            if (telemetryPdata != null && !telemetryPdata.isEmpty()) {
                reqContext.putAll(telemetryPdata);
            }

            Optional<String> optionalAppId = request.header(HeaderParam.X_APP_ID.getName());
            if (optionalAppId.isPresent()) {
                request = request.addAttr(Attrs.APP_ID, optionalAppId.get());
                reqContext.put(JsonKey.APP_ID, optionalAppId.get());
            }

            Optional<String> optionalDeviceId = request.header(HeaderParam.X_Device_ID.getName());
            if (optionalDeviceId.isPresent()) {
                request = request.addAttr(Attrs.DEVICE_ID, optionalDeviceId.get());
                reqContext.put(JsonKey.DEVICE_ID, optionalDeviceId.get());
            }

            Optional<String> optionalSessionId = request.header(HeaderParam.X_Session_ID.getName());
            if (optionalSessionId.isPresent()) {
                reqContext.put(JsonKey.X_Session_ID, optionalSessionId.get());
            }

            Optional<String> optionalAppVersion = request.header(HeaderParam.X_APP_VERSION.getName());
            if (optionalAppVersion.isPresent()) {
                reqContext.put(JsonKey.X_APP_VERSION, optionalAppVersion.get());
            }

            Optional<String> optionalTraceEnabled = request.header(HeaderParam.X_TRACE_ENABLED.getName());
            if (optionalTraceEnabled.isPresent()) {
                reqContext.put(JsonKey.X_TRACE_ENABLED, optionalTraceEnabled.get());
            }

            // X_REQUEST_ID: prefer the inbound tracing header; fall back to generated messageId.
            Optional<String> optionalTraceId = request.header(HeaderParam.X_REQUEST_ID.getName());
            if (optionalTraceId.isPresent()) {
                reqContext.put(JsonKey.X_REQUEST_ID, optionalTraceId.get());
                request = request.addAttr(Attrs.X_REQUEST_ID, optionalTraceId.get());
                request = request.addAttr(utils.module.Attrs.X_REQUEST_ID, optionalTraceId.get());
            } else {
                reqContext.put(JsonKey.X_REQUEST_ID, requestId);
                request = request.addAttr(Attrs.X_REQUEST_ID, requestId);
                request = request.addAttr(utils.module.Attrs.X_REQUEST_ID, requestId);
            }

            if (!USER_UNAUTH_STATES.contains(userId)) {
                reqContext.put(JsonKey.ACTOR_ID, userId);
                reqContext.put(JsonKey.ACTOR_TYPE, StringUtils.capitalize(JsonKey.USER));
                request = request.addAttr(Attrs.ACTOR_ID, userId);
                request = request.addAttr(Attrs.ACTOR_TYPE, JsonKey.USER);
            } else {
                Optional<String> optionalConsumerId = request.header(HeaderParam.X_Consumer_ID.getName());
                String consumerId = optionalConsumerId.orElse(JsonKey.DEFAULT_CONSUMER_ID);
                reqContext.put(JsonKey.ACTOR_ID, consumerId);
                reqContext.put(JsonKey.ACTOR_TYPE, StringUtils.capitalize(JsonKey.CONSUMER));
                request = request.addAttr(Attrs.ACTOR_ID, consumerId);
                request = request.addAttr(Attrs.ACTOR_TYPE, JsonKey.CONSUMER);
            }

            Map<String, Object> map = new HashMap<>();
            map.put(JsonKey.CONTEXT, reqContext);
            Map<String, Object> additionalInfo = new HashMap<>();
            additionalInfo.put(JsonKey.URL, url);
            additionalInfo.put(JsonKey.METHOD, methodName);
            additionalInfo.put(JsonKey.START_TIME, startTime);
            map.put(JsonKey.ADDITIONAL_INFO, additionalInfo);

            if (StringUtils.isBlank(requestId)) {
                requestId = JsonKey.DEFAULT_CONSUMER_ID;
            }
            request = request.addAttr(Attrs.REQUEST_ID, requestId);
            request = request.addAttr(Attrs.CONTEXT, mapper.writeValueAsString(map));
            request = request.addAttr(utils.module.Attrs.CONTEXT, mapper.writeValueAsString(map));
        } catch (Exception e) {
            ProjectCommonException.throwServerErrorException(ResponseCode.SERVER_ERROR, e.getMessage());
        }
        return request;
    }

    private String getEnv(Http.Request request) {
        String uri = request.uri();
        String env;
        if (uri.startsWith("/v1/user")
                || uri.startsWith("/v2/user")
                || uri.startsWith("/v3/user")
                || uri.startsWith("/v4/user")
                || uri.startsWith("/v5/user")
                || uri.startsWith("/v1/ssouser")
                || uri.startsWith("/v1/manageduser")
                || uri.startsWith("/v2/manageduser")
                || uri.startsWith("/private/user")) {
            env = JsonKey.USER;
        } else if (uri.startsWith("/v1/org") || uri.startsWith("/v2/org")) {
            env = JsonKey.ORGANISATION;
        } else if (uri.startsWith("/v1/course") || uri.startsWith("/v1/batch")) {
            env = JsonKey.BATCH;
        } else if (uri.startsWith("/v1/notification") || uri.startsWith("/v2/notification")) {
            env = JsonKey.NOTIFICATION;
        } else if (uri.startsWith("/v1/role")) {
            env = JsonKey.ROLE;
        } else if (uri.startsWith("/v1/note")) {
            env = JsonKey.NOTE;
        } else if (uri.startsWith("/v1/location")) {
            env = JsonKey.LOCATION;
        } else if (uri.startsWith("/v1/otp") || uri.startsWith("/v2/otp")) {
            env = "otp";
        } else if (uri.startsWith("/v1/page")) {
            env = JsonKey.PAGE;
        } else if (uri.startsWith("/v1/dashboard")) {
            env = JsonKey.DASHBOARD;
        } else if (uri.startsWith("/v1/content")) {
            env = JsonKey.BATCH;
        } else {
            env = "miscellaneous";
        }
        return env;
    }

    public CompletionStage<Result> checkForServiceHealth(Http.Request request) {
        if (Boolean.parseBoolean((ProjectUtil.getConfigValue(JsonKey.SUNBIRD_HEALTH_CHECK_ENABLE))) && !request.path().endsWith(JsonKey.HEALTH)) {
            if (!isServiceHealthy) {
                ResponseCode headerCode = ResponseCode.SERVICE_UNAVAILABLE;
                Response resp = BaseController.createFailureResponse(request, headerCode, headerCode);
                return CompletableFuture.completedFuture(Results.status(ResponseCode.SERVICE_UNAVAILABLE.getResponseCode(), Json.toJson(resp)));
            }
        }
        return null;
    }

    protected String getLoggingHeaders(Http.Request httpRequest) {
        try {
            Map<String, List<String>> headers = httpRequest.getHeaders().toMap();
            Map<String, List<String>> filteredHeaders = headers.entrySet().stream()
                .filter(e -> clientAppHeaderKeys.contains(e.getKey().toLowerCase()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
            return JsonUtil.serialize(filteredHeaders);
        } catch (Exception e) {
            return "Exception in serializing headers= " + e.getMessage();
        }
    }
}
