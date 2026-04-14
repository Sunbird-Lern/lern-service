package controllers;

import static util.Common.createResponseParamObj;
import static util.PrintEntryExitLog.printEntryLog;
import static util.PrintEntryExitLog.printExitLogOnFailure;
import static util.PrintEntryExitLog.printExitLogOnSuccessResponse;

import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.ActorSelection;
import org.apache.pekko.pattern.PatternsCS;
import org.apache.pekko.util.Timeout;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import javax.inject.Inject;
import modules.ApplicationStart;
import modules.OnRequestHandler;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpHeaders;
import org.sunbird.exception.BaseException;
import org.sunbird.exception.ProjectCommonException;
import org.sunbird.response.ResponseCode;
import org.sunbird.keys.JsonKey;
import org.sunbird.keys.SunbirdKey;
import org.sunbird.logging.LoggerUtil;
import org.sunbird.Application;
import org.sunbird.operations.userorg.ActorOperations;
import org.sunbird.request.HeaderParam;
import org.sunbird.request.RequestContext;
import org.sunbird.response.ClientErrorResponse;
import org.sunbird.response.Response;
import org.sunbird.response.ResponseParams;
import org.sunbird.telemetry.util.TelemetryEvents;
import org.sunbird.telemetry.util.TelemetryWriter;
import org.sunbird.common.ProjectUtil;
import play.libs.Json;
import play.libs.concurrent.HttpExecutionContext;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Http.Request;
import play.mvc.Result;
import play.mvc.Results;
import util.Attrs;
import util.Common;
import util.AuthenticationHelper;
import validators.RequestValidatorFunction;

/**
 * Unified BaseController for Monolithic Service.
 * resolving NoSuchMethodError and classpath collisions.
 */
public class BaseController extends Controller {

  protected static final LoggerUtil logger = new LoggerUtil(BaseController.class);
  private static final ObjectMapper objectMapper = new ObjectMapper();
  public static final int PEKKO_WAIT_TIME = 30;
  private static final String version = "v1";
  protected Timeout timeout = new Timeout(PEKKO_WAIT_TIME, TimeUnit.SECONDS);
  private static final String debugEnabled = "false";
  public static final String NOTIFICATION_DELIVERY_MODE = "notification-delivery-mode";

  @Inject public HttpExecutionContext httpExecutionContext;

  private org.sunbird.request.Request initRequest(
      org.sunbird.request.Request request, String operation, Request httpRequest) {
    request.setOperation(operation);

    String requestId = Common.getFromRequest(httpRequest, Attrs.REQUEST_ID);
    if (StringUtils.isBlank(requestId)) {
        requestId = httpRequest.attrs().getOptional(Attrs.REQUEST_ID).orElse(null);
    }
    request.setRequestId(requestId);
    request.getParams().setMsgid(requestId);
    request.setEnv(getEnvironment());
    
    // RequestContext handling (LMS style)
    request.setRequestContext(getRequestContext(httpRequest, request));

    request.getContext().put(JsonKey.REQUESTED_BY, httpRequest.attrs().getOptional(Attrs.USER_ID).orElse(null));
    request.getRequest().put(JsonKey.REQUESTED_BY, httpRequest.attrs().getOptional(Attrs.USER_ID).orElse(null));
    
    if (StringUtils.isNotBlank(httpRequest.attrs().getOptional(Attrs.REQUESTED_FOR).orElse(null)))
      request.getContext().put(SunbirdKey.REQUESTED_FOR, httpRequest.attrs().get(Attrs.REQUESTED_FOR));
    
    request.getContext().put(JsonKey.X_AUTH_TOKEN, httpRequest.attrs().getOptional(Attrs.X_AUTH_TOKEN).orElse(""));
    
    // UserOrg specific context
    request.getContext().put(JsonKey.MANAGED_FOR, httpRequest.attrs().getOptional(Attrs.MANAGED_FOR).orElse(null));
    Optional<String> manageToken = httpRequest.header(HeaderParam.X_Authenticated_For.getName());
    String managedToken = manageToken.isPresent() ? manageToken.get() : "";
    request.getContext().put(JsonKey.MANAGED_TOKEN, managedToken);

    request = transformUserId(request);
    return request;
  }

  private RequestContext getRequestContext(Http.Request httpRequest, org.sunbird.request.Request request) {
    try {
        // Try to get from attributes first (UserOrg/LMS common pattern)
        String contextStr = Common.getFromRequest(httpRequest, Attrs.CONTEXT);
        if (StringUtils.isNotBlank(contextStr)) {
            Map<String, Object> requestInfo = objectMapper.readValue(contextStr, new TypeReference<>() {});
            Map<String, Object> context = (Map<String, Object>) requestInfo.get(JsonKey.CONTEXT);
            RequestContext requestContext = new RequestContext(
                (String) context.get(JsonKey.ACTOR_ID),
                (String) context.get(JsonKey.DEVICE_ID),
                (String) context.get(JsonKey.X_Session_ID),
                (String) context.get(JsonKey.APP_ID),
                (String) context.get(JsonKey.X_APP_VERSION),
                (String) context.get(JsonKey.X_REQUEST_ID),
                (String) context.get(JsonKey.X_Source),
                (String) ((context.get(JsonKey.X_TRACE_ENABLED) != null) ? context.get(JsonKey.X_TRACE_ENABLED) : debugEnabled),
                request.getOperation());
            requestContext.setActorId((String) context.get(JsonKey.ACTOR_ID));
            requestContext.setActorType((String) context.get(JsonKey.ACTOR_TYPE));
            requestContext.setTelemetryContext(requestInfo);
            return requestContext;
        }
    } catch (Exception e) {
        logger.error("Error creating RequestContext from attributes", e);
    }

    // Fallback to manual creation
    RequestContext requestContext = new RequestContext(
            JsonKey.SERVICE_NAME,
            JsonKey.PRODUCER_NAME,
            request.getContext().getOrDefault(JsonKey.ENV, "").toString(),
            httpRequest.header(JsonKey.X_DEVICE_ID).orElse(null),
            httpRequest.header(JsonKey.X_SESSION_ID).orElse(null),
            JsonKey.PID,JsonKey.P_VERSION, null);
    requestContext.setActorId(httpRequest.attrs().getOptional(Attrs.ACTOR_ID).orElse(null));
    requestContext.setActorType(httpRequest.attrs().getOptional(Attrs.ACTOR_TYPE).orElse(null));
    requestContext.setRequestId(httpRequest.attrs().getOptional(Attrs.REQUEST_ID).orElse(null));
    return requestContext;
  }

  protected org.sunbird.request.Request createAndInitRequest(
      String operation, JsonNode requestBodyJson, Request httpRequest) throws Exception {
    org.sunbird.request.Request request =
        (org.sunbird.request.Request)
            mapper.RequestMapper.mapRequest(requestBodyJson, org.sunbird.request.Request.class);
    return initRequest(request, operation, httpRequest);
  }

  protected org.sunbird.request.Request createAndInitRequest(
      String operation, Request httpRequest) {
    org.sunbird.request.Request request = new org.sunbird.request.Request();
    return initRequest(request, operation, httpRequest);
  }

  // Overloads for BaseController compatibility
  
  protected CompletionStage<Result> handleRequest(
      ActorRef actorRef, String operation, Http.Request httpRequest) {
    return handleRequest(actorRef, operation, null, null, null, null, false, httpRequest);
  }

  protected CompletionStage<Result> handleRequest(
      ActorRef actorRef, String operation, JsonNode requestBodyJson, Request httpRequest) {
    return handleRequest(actorRef, operation, requestBodyJson, null, null, null, true, httpRequest);
  }

  protected CompletionStage<Result> handleRequest(
      ActorRef actorRef,
      String operation,
      java.util.function.Function requestValidatorFn,
      Request httpRequest) {
    return handleRequest(
        actorRef, operation, null, requestValidatorFn, null, null, false, httpRequest);
  }

  protected CompletionStage<Result> handleRequest(
      ActorRef actorRef,
      String operation,
      JsonNode requestBodyJson,
      Function requestValidatorFn,
      Request httpRequest) {
    return handleRequest(
        actorRef, operation, requestBodyJson, requestValidatorFn, null, null, true, httpRequest);
  }

  protected CompletionStage<Result> handleRequest(
      ActorRef actorRef,
      String operation,
      String pathId,
      String pathVariable,
      Request httpRequest) {
    return handleRequest(actorRef, operation, null, null, pathId, pathVariable, false, httpRequest);
  }

  protected CompletionStage<Result> handleRequest(
      ActorRef actorRef,
      String operation,
      String pathId,
      String pathVariable,
      boolean isJsonBodyRequired,
      Request httpRequest) {
    return handleRequest(
        actorRef, operation, null, null, pathId, pathVariable, isJsonBodyRequired, httpRequest);
  }

  protected CompletionStage<Result> handleRequest(
      ActorRef actorRef,
      String operation,
      JsonNode requestBodyJson,
      Function requestValidatorFn,
      Map<String, String> headers,
      Request httpRequest) {
    return handleRequest(
        actorRef, operation, requestBodyJson, requestValidatorFn, null, null, headers, true, httpRequest);
  }

  protected CompletionStage<Result> handleRequest(
          ActorRef actorRef,
          String operation,
          Function requestValidatorFn,
          Map<String, String> headers,
          Request httpRequest) {
    return handleRequest(
            actorRef, operation, null, requestValidatorFn, null, null, headers, false, httpRequest);
  }

  protected CompletionStage<Result> handleRequest(
      ActorRef actorRef,
      String operation,
      Function requestValidatorFn,
      String pathId,
      String pathVariable,
      Request httpRequest) {
    return handleRequest(
        actorRef, operation, null, requestValidatorFn, pathId, pathVariable, false, httpRequest);
  }

  protected CompletionStage<Result> handleRequest(
      ActorRef actorRef,
      String operation,
      JsonNode requestBodyJson,
      Function requestValidatorFn,
      String pathId,
      String pathVariable,
      Request httpRequest) {
    return handleRequest(
        actorRef, operation, requestBodyJson, requestValidatorFn, pathId, pathVariable, true, httpRequest);
  }

  // The 8-parameter overload causing NoSuchMethodError
  protected CompletionStage<Result> handleRequest(
      ActorRef actorRef,
      String operation,
      JsonNode requestBodyJson,
      java.util.function.Function requestValidatorFn,
      String pathId,
      String pathVariable,
      boolean isJsonBodyRequired,
      Request httpRequest) {
    return handleRequest(
        actorRef, operation, requestBodyJson, requestValidatorFn, pathId, pathVariable, null, isJsonBodyRequired, httpRequest);
  }

  // Final internal Master handleRequest
  protected CompletionStage<Result> handleRequest(
      ActorRef actorRef,
      String operation,
      JsonNode requestBodyJson,
      Function requestValidatorFn,
      String pathId,
      String pathVariable,
      Map<String, String> headers,
      boolean isJsonBodyRequired,
      Request httpRequest) {
    org.sunbird.request.Request request = null;
    try {
      if (!isJsonBodyRequired) {
        request = createAndInitRequest(operation, httpRequest);
      } else {
        request = createAndInitRequest(operation, requestBodyJson, httpRequest);
      }
      if (pathId != null) {
        request.getRequest().put(pathVariable, pathId);
        request.getContext().put(pathVariable, pathId);
      }
      if (headers != null) request.getContext().put(JsonKey.HEADER, headers);

      setContextAndPrintEntryLog(httpRequest, request);
      if (requestValidatorFn != null) requestValidatorFn.apply(request);
      
      return actorResponseHandler(actorRef, request, timeout, null, httpRequest);
    } catch (Exception e) {
      logger.error("BaseController:handleRequest error", e);
      return CompletableFuture.completedFuture(createCommonExceptionResponse(e, httpRequest));
    }
  }

  // Notification Service handleRequest
  public CompletionStage<Result> handleRequest(
          org.sunbird.request.Request request , validators.RequestValidatorFunction validatorFunction, String operation, play.mvc.Http.Request req) {
    try {
      if (validatorFunction != null) {
        validatorFunction.apply(request);
      }
      List<String> list = req.getHeaders().toMap().get(NOTIFICATION_DELIVERY_MODE);
      if (CollectionUtils.isNotEmpty(list)) {
        request.setManagerName(list.get(0));
      }
      return new controllers.ResponseHandler().handleRequest(request, httpExecutionContext, operation, req);
    } catch (Exception ex) {
      return CompletableFuture.completedFuture(controllers.ResponseHandler.handleFailureResponse(request, ex, httpExecutionContext, req));
    }
  }

  protected ActorRef getActorRef(String operation) throws BaseException {
    return Application.getInstance().getActorRef(operation);
  }

  public CompletionStage<Result> handleLogRequest(Http.Request req) {
    startTrace("handleLogRequest");
    Response response = new Response();
    org.sunbird.request.Request request = null;
    try {
      request = (org.sunbird.request.Request) mapper.RequestMapper.mapRequest(req.body().asJson(), org.sunbird.request.Request.class);
    } catch (Exception ex) {
      return CompletableFuture.completedFuture(
      controllers.ResponseHandler.handleFailureResponse(request, ex, httpExecutionContext, req ));
    }
    return CompletableFuture.completedFuture(
            controllers.ResponseHandler.handleSuccessResponse(request, response, httpExecutionContext, req));
  }

  protected void setContextAndPrintEntryLog(Request httpRequest, org.sunbird.request.Request request) {
    setContextData(httpRequest, request);
    printEntryLog(request);
  }

  public void setContextData(Http.Request httpReq, org.sunbird.request.Request reqObj) {
    try {
      String context = Common.getFromRequest(httpReq, Attrs.CONTEXT);
      if (StringUtils.isNotBlank(context)) {
          Map<String, Object> requestInfo = objectMapper.readValue(context, new TypeReference<>() {});
          reqObj.getContext().putAll((Map<String, Object>) requestInfo.get(JsonKey.CONTEXT));
          reqObj.getContext().putAll((Map<String, Object>) requestInfo.get(JsonKey.ADDITIONAL_INFO));
      }
      reqObj.setRequestId(Common.getFromRequest(httpReq, Attrs.REQUEST_ID));
    } catch (Exception ex) {
      logger.error("Error setting context data", ex);
    }
  }

  public CompletionStage<Result> actorResponseHandler(
      Object actorRef,
      org.sunbird.request.Request request,
      Timeout timeout,
      String responseKey,
      Request httpReq) {
    setContextData(httpReq, request);
    Function<Object, Result> function =
        result -> {
          if (ActorOperations.HEALTH_CHECK.getValue().equals(request.getOperation())) {
            setGlobalHealthFlag(result);
          }
          if (result instanceof Response) {
            Response response = (Response) result;
            if (ResponseCode.OK.getResponseCode() == (response.getResponseCode().getResponseCode())) {
              Result reslt = createCommonResponse(response, responseKey, httpReq);
              printExitLogOnSuccessResponse(request, response);
              return reslt;
            } else if (ResponseCode.CLIENT_ERROR.getResponseCode() == (response.getResponseCode().getResponseCode())) {
               ProjectCommonException exception = new ProjectCommonException(
                      ((ClientErrorResponse) response).getException(),
                      ActorOperations.getOperationCodeByActorOperation(request.getOperation()));
              ((ClientErrorResponse) response).setException(exception);
              Result reslt = createClientErrorResponse(httpReq, (ClientErrorResponse) response);
              printExitLogOnFailure(request, ((ClientErrorResponse) response).getException());
              return reslt;
            }
          } 
          
          if (result instanceof ProjectCommonException) {
              Result reslt = createCommonExceptionResponse((ProjectCommonException) result, httpReq);
              printExitLogOnFailure(request, (ProjectCommonException) result);
              return reslt;
          } else if (result instanceof File) {
              return createFileDownloadResponse((File) result);
          } else {
              Result reslt = createCommonExceptionResponse(new Exception(), httpReq);
              printExitLogOnFailure(request, null);
              return reslt;
          }
        };

    if (actorRef instanceof ActorRef) {
      return PatternsCS.ask((ActorRef) actorRef, request, timeout).thenApplyAsync(function);
    } else {
      return PatternsCS.ask((ActorSelection) actorRef, request, timeout).thenApplyAsync(function);
    }
  }

  public Result createCommonResponse(Object response, String key, Request request) {
    Response courseResponse = (Response) response;
    if (!StringUtils.isBlank(key)) {
      Object value = courseResponse.getResult().get(JsonKey.RESPONSE);
      courseResponse.getResult().remove(JsonKey.RESPONSE);
      courseResponse.getResult().put(key, value);
    }
    return BaseController.createSuccessResponse(request, courseResponse);
  }

  public static Result createSuccessResponse(Request request, Response response) {
    response.setVer(getApiVersion(request.path()));
    response.setId(getApiResponseId(request));
    response.setTs(ProjectUtil.getFormattedDate());
    ResponseCode code = ResponseCode.success;
    code.setResponseCode(ResponseCode.OK.getResponseCode());
    response.setParams(createResponseParamObj(code, null, Common.getFromRequest(request, Attrs.REQUEST_ID)));
    
    logTelemetry(response, request);
    return Results.ok(Json.toJson(response));
  }

  public Result createCommonExceptionResponse(Exception e, Request request) {
    ProjectCommonException exception = (e instanceof ProjectCommonException) ? (ProjectCommonException) e :
          new ProjectCommonException(ResponseCode.serverError, ResponseCode.serverError.getErrorMessage(), ResponseCode.SERVER_ERROR.getResponseCode());
    
    generateExceptionTelemetry(request, exception);
    return Results.status(exception.getErrorResponseCode(), Json.toJson(createResponseOnException(request, exception)));
  }

  public static Response createResponseOnException(Request request, ProjectCommonException exception) {
    Response response = new Response();
    response.setVer(getApiVersion(request.path()));
    response.setId(getApiResponseId(request));
    response.setTs(ProjectUtil.getFormattedDate());
    response.setResponseCode(ResponseCode.getResponseCodeByCode(exception.getErrorResponseCode()));
    ResponseCode code = exception.getResponseCode();
    if (code == null) code = ResponseCode.SERVER_ERROR;
    
    response.setParams(createResponseParamObj(code, exception.getMessage(), Common.getFromRequest(request, Attrs.REQUEST_ID)));
    return response;
  }

  public static Response createResponseOnException(String path, String method, ProjectCommonException exception) {
    Response response = new Response();
    response.setVer(getApiVersion(path));
    response.setId(getApiResponseId(path, method));
    response.setTs(ProjectUtil.getFormattedDate());
    response.setResponseCode(exception.getResponseCode() != null ? exception.getResponseCode() : ResponseCode.getResponseCodeByCode(exception.getErrorResponseCode()));
    ResponseCode code = exception.getResponseCode();
    response.setParams(createResponseParamObj(code, exception.getMessage(), null));
    return response;
  }

  public static Response createFailureResponse(Request request, ResponseCode code, ResponseCode headerCode) {
    Response response = new Response();
    response.setId(getApiResponseId(request));
    response.setVer(getApiVersion(request.path()));
    response.setTs(ProjectUtil.getFormattedDate());
    response.setResponseCode(headerCode);
    response.setParams(createResponseParamObj(code, null, Common.getFromRequest(request, Attrs.REQUEST_ID)));
    return response;
  }

  private static String getApiResponseId(String path, String method) {
    return getResponseId(path);
  }

  private Result createClientErrorResponse(Request httpReq, ClientErrorResponse response) {
    generateExceptionTelemetry(httpReq, response.getException());
    Response responseObj = createResponseOnException(httpReq, response.getException());
    responseObj.getResult().putAll(response.getResult());
    return Results.status(response.getException().getErrorResponseCode(), Json.toJson(responseObj));
  }

  public static String getApiVersion(String request) {
    return request.split("[/]")[1];
  }

  private static String getApiResponseId(Request request) {
    String val = "";
    if (request != null) {
      String path = request.path();
      if (request.method().equalsIgnoreCase(ProjectUtil.Method.GET.name())) {
        val = getResponseId(path);
        if (StringUtils.isBlank(val)) {
          String[] splitedpath = path.split("[/]");
          path = removeLastValue(splitedpath);
          val = getResponseId(path);
        }
      } else {
        val = getResponseId(path);
      }
    }
    return val;
  }

  public static String getResponseId(String requestPath) {
    String path = requestPath;
    final String ver = "/" + version;
    final String ver2 = "/" + JsonKey.VERSION_2;
    final String ver3 = "/" + JsonKey.VERSION_3;
    final String ver4 = "/" + JsonKey.VERSION_4;
    final String ver5 = "/" + JsonKey.VERSION_5;
    final String privateVersion = "/" + JsonKey.PRIVATE;
    path = path.trim();
    String respId = "";
    if (path.startsWith(ver)
        || path.startsWith(ver2)
        || path.startsWith(ver3)
        || path.startsWith(ver4)
        || path.startsWith(ver5)) {
      String requestUrl = (path.split("\\?"))[0];
      if (requestUrl.contains(ver)) {
        requestUrl = requestUrl.replaceFirst(ver, "api");
      } else if (requestUrl.contains(ver2)) {
        requestUrl = requestUrl.replaceFirst(ver2, "api");
      } else if (requestUrl.contains(ver3)) {
        requestUrl = requestUrl.replaceFirst(ver3, "api");
      } else if (requestUrl.contains(ver4)) {
        requestUrl = requestUrl.replaceFirst(ver4, "api");
      } else if (requestUrl.contains(ver5)) {
        requestUrl = requestUrl.replaceFirst(ver5, "api");
      }
      String[] list = requestUrl.split("/");
      List<String> segments = new ArrayList<>();
      for (String s : list) {
        if (StringUtils.isNotBlank(s)) {
          segments.add(s);
        }
      }
      respId = String.join(".", segments);
    } else {
      if ("/health".equalsIgnoreCase(path)) {
        respId = "api.all.health";
      } else if (path.startsWith(privateVersion)) {
        String[] list = path.split("/");
        List<String> segments = new ArrayList<>();
        for (String s : list) {
          if (StringUtils.isNotBlank(s)) {
            segments.add(s);
          }
        }
        respId = String.join(".", segments);
      }
    }
    return respId;
  }

  protected String getQueryString(Map<String, String[]> queryStringMap) {
    return queryStringMap
        .entrySet()
        .stream()
        .map(p -> p.getKey() + "=" + String.join(",", p.getValue()))
        .reduce((p1, p2) -> p1 + "&" + p2)
        .map(s -> "?" + s)
        .orElse("");
  }

  private static String removeLastValue(String splited[]) {
    StringBuilder builder = new StringBuilder();
    if (splited != null && splited.length > 0) {
      for (int i = 1; i < splited.length - 1; i++) {
        builder.append("/" + splited[i]);
      }
    }
    return builder.toString();
  }

  public static String getResponseSize(String response) throws UnsupportedEncodingException {
    if (StringUtils.isNotBlank(response)) {
      return response.getBytes("UTF-8").length + "";
    }
    return "0.0";
  }

  private static void logTelemetry(Response response, Request request) {
    // Telemetry logging placeholder
  }

  private void generateExceptionTelemetry(Request request, ProjectCommonException exception) {
    // Telemetry logging placeholder
  }

  public Result createFileDownloadResponse(File file) {
    return Results.ok(file)
        .withHeader(HttpHeaders.CONTENT_TYPE, "application/x-download")
        .withHeader("Content-disposition", "attachment; filename=" + file.getName());
  }

  public int getEnvironment() {
    if (ApplicationStart.env != null) return ApplicationStart.env.getValue();
    return ProjectUtil.Environment.dev.getValue();
  }

  @SuppressWarnings("unchecked")
  private void setGlobalHealthFlag(Object result) {
    if (result instanceof Response) {
      Response response = (Response) result;
      if (Boolean.parseBoolean(ProjectUtil.getConfigValue(JsonKey.SUNBIRD_HEALTH_CHECK_ENABLE))) {
          Map<String, Object> resp = (Map<String, Object>) response.getResult().get(JsonKey.RESPONSE);
          if (resp != null && resp.containsKey(JsonKey.Healthy)) {
              OnRequestHandler.isServiceHealthy = (boolean) resp.get(JsonKey.Healthy);
          }
      }
    }
  }

  public Map<String, String> getAllRequestHeaders(Request request) {
    Map<String, String> map = new HashMap<>();
    request.getHeaders().toMap().forEach((k, v) -> map.put(k, v.get(0)));
    return map;
  }

  public org.sunbird.request.Request transformUserId(org.sunbird.request.Request request) {
    if (request != null && request.getRequest() != null) {
      String id = (String) request.getRequest().get(JsonKey.ID);
      request.getRequest().put(JsonKey.ID, ProjectUtil.getLmsUserId(id));
      id = (String) request.getRequest().get(JsonKey.USER_ID);
      request.getRequest().put(JsonKey.USER_ID, ProjectUtil.getLmsUserId(id));
    }
    return request;
  }
  
  // Notification Service specific methods
  public long getTimeStamp() {
    return System.currentTimeMillis();
  }
  
  public void startTrace(String tag) {
    logger.info("Method call started: " + tag);
  }

  // LMS specific method
  protected CompletionStage<Result> handleSearchRequest(
      ActorRef actorRef,
      String operation,
      JsonNode requestBodyJson,
      java.util.function.Function requestValidatorFn,
      String pathId,
      String pathVariable,
      Map<String, String> headers,
      String esObjectType,
      Http.Request httpRequest) {
    return handleRequest(actorRef, operation, requestBodyJson, requestValidatorFn, pathId, pathVariable, headers, true, httpRequest);
  }
}
