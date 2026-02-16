package util;

import static util.Common.createResponseParamObj;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.SerializationUtils;
import org.apache.commons.lang3.StringUtils;
import org.sunbird.datasecurity.DataMaskingService;
import org.sunbird.datasecurity.impl.DefaultDataMaskServiceImpl;
import org.sunbird.datasecurity.impl.LogMaskServiceImpl;
import org.sunbird.exception.ProjectCommonException;
import org.sunbird.response.ResponseCode;
import org.sunbird.keys.JsonKey;
import org.sunbird.logging.LoggerUtil;
import org.sunbird.operations.userorg.ActorOperations;
import org.sunbird.request.Request;
import org.sunbird.response.Response;
import org.sunbird.response.ResponseParams;
import org.sunbird.logging.EntryExitLogEvent;
import org.sunbird.common.ProjectUtil;

/**
 * Utility class to handle entry and exit logging for the Lern service.
 * It provides standardized logging for requests and responses, including
 * specialized logic for masking sensitive data (PII) like email, phone, and OTP.
 *
 * This ensures that logs are both informative for debugging and compliant
 * with data privacy standards.
 */
public class PrintEntryExitLog {

  private static final LoggerUtil logger = new LoggerUtil(PrintEntryExitLog.class);
  private static final LogMaskServiceImpl logMaskService = new LogMaskServiceImpl();
  private static final DataMaskingService service = new DefaultDataMaskServiceImpl();
  private static final ObjectMapper objectMapper = new ObjectMapper();

  /**
   * Logs entry information for a given request.
   * Clones the request map and applies masking to sensitive attributes (like OTP)
   * before writing to the log.
   *
   * @param request The operation request to log.
   */
  public static void printEntryLog(Request request) {
    try {
      EntryExitLogEvent entryLogEvent = getLogEvent(request, "ENTRY");
      List<Map<String, Object>> params = new ArrayList<>();
      Map<String, Object> reqMap = request.getRequest();
      Map<String, Object> newReqMap = SerializationUtils.clone(new HashMap<>(reqMap));
      String url = (String) request.getContext().get(JsonKey.URL);
      if (url.contains("otp")) {
        if (MapUtils.isNotEmpty(newReqMap)) {
          maskOtpAttributes(newReqMap);
        }
      }
      params.add(newReqMap);
      entryLogEvent.setEdataParams(params);
      logger.info(request.getRequestContext(), maskPIIData(entryLogEvent.toString()));
    } catch (Exception ex) {
      logger.error("Exception occurred while logging entry log", ex);
    }
  }

  /**
   * Logs exit information for a successful response.
   * Excludes health check operations and ensures PII data in the response result
   * is properly handled or masked if necessary.
   *
   * @param request  The original request.
   * @param response The successful response result.
   */
  public static void printExitLogOnSuccessResponse(
      org.sunbird.request.Request request, Response response) {
    try {
      if (ActorOperations.HEALTH_CHECK.getValue().equalsIgnoreCase(request.getOperation())) {
        return;
      }
      EntryExitLogEvent exitLogEvent = getLogEvent(request, "EXIT");
      String url = (String) request.getContext().get(JsonKey.URL);
      List<Map<String, Object>> params = new ArrayList<>();
      if (null != response) {
        if (MapUtils.isNotEmpty(response.getResult())) {
          if (null != url && url.equalsIgnoreCase("/private/user/v1/lookup")) {
            if (CollectionUtils.isNotEmpty(
                (List<Map<String, Object>>) response.getResult().get(JsonKey.RESPONSE))) {
              List<Map<String, Object>> resList =
                  (List<Map<String, Object>>) response.getResult().get(JsonKey.RESPONSE);
              params.add(resList.get(0));
            }
          } else {
            Map<String, Object> resMap = response.getResult();
            Map<String, Object> newRespMap = new HashMap<>();
            newRespMap.putAll(resMap);
            params.add(newRespMap);
          }
        }

        if (null != response.getParams()) {
          Map<String, Object> resParam = new HashMap<>();
          resParam.putAll(objectMapper.convertValue(response.getParams(), Map.class));
          resParam.put(JsonKey.RESPONSE_CODE, response.getResponseCode().getResponseCode());
          params.add(resParam);
        }
      }
      exitLogEvent.setEdataParams(params);
      logger.info(request.getRequestContext(), maskPIIData(exitLogEvent.toString()));
    } catch (Exception ex) {
      logger.error("Exception occurred while logging exit log", ex);
    }
  }

  /**
   * Logs exit information when an operation fails with an exception.
   * Standardizes the error response parameters for logging.
   *
   * @param request   The original request.
   * @param exception The exception that caused the failure.
   */
  public static void printExitLogOnFailure(
      org.sunbird.request.Request request, ProjectCommonException exception) {
    try {
      EntryExitLogEvent exitLogEvent = getLogEvent(request, "EXIT");
      String requestId = request.getRequestContext().getReqId();
      List<Map<String, Object>> params = new ArrayList<>();
      if (null == exception) {
        exception =
            new ProjectCommonException(
                ResponseCode.serverError,
                ResponseCode.serverError.getErrorMessage(),
                ResponseCode.SERVER_ERROR.getResponseCode());
      }

      ResponseCode code = exception.getResponseCodeEnum();
      if (code == null) {
        code = ResponseCode.SERVER_ERROR;
      }
      ResponseParams responseParams =
          createResponseParamObj(code, exception.getMessage(), requestId);
      if (responseParams != null) {
        responseParams.setErr(exception.getErrorCode());
        if (!StringUtils.isBlank(responseParams.getErrmsg())
            && responseParams.getErrmsg().contains("{0}")) {
          responseParams.setErrmsg(exception.getMessage());
        }
      }
      if (null != responseParams) {
        Map<String, Object> resParam = new HashMap<>();
        resParam.putAll(objectMapper.convertValue(responseParams, Map.class));
        resParam.put(JsonKey.RESPONSE_CODE, exception.getErrorResponseCode());
        params.add(resParam);
      }
      exitLogEvent.setEdataParams(params);
      logger.info(request.getRequestContext(), exitLogEvent.toString());
    } catch (Exception ex) {
      logger.error("Exception occurred while logging exit log", ex);
    }
  }

  /**
   * Creates a standardized log event object for entry or exit.
   *
   * @param request The request context.
   * @param logType The type of log event ("ENTRY" or "EXIT").
   * @return An EntryExitLogEvent populated with request metadata.
   */
  private static EntryExitLogEvent getLogEvent(Request request, String logType) {
    EntryExitLogEvent entryLogEvent = new EntryExitLogEvent();
    entryLogEvent.setEid("LOG");
    String url = (String) request.getContext().get(JsonKey.URL);
    String entryLogMsg =
        logType
            + " LOG: method : "
            + request.getContext().get(JsonKey.METHOD)
            + ", url: "
            + maskPIIData(url)
            + " , For Operation : "
            + request.getOperation();
    String requestId =
        request.getRequestContext() != null ? request.getRequestContext().getReqId() : "";
    entryLogEvent.setEdata("system", "trace", requestId, entryLogMsg, null);
    return entryLogEvent;
  }

  /**
   * Scans a string for Personally Identifiable Information (PII) like emails and phone numbers
   * and replaces them with masked versions.
   *
   * @param logString The raw string representation of the log event.
   * @return The log string with sensitive data masked.
   */
  private static String maskPIIData(String logString) {
    if (StringUtils.isBlank(logString)) {
      return logString;
    }
    try {
      StringBuilder builder = new StringBuilder(logString);
      // Mask Email
      StringBuilder emailRegex = new StringBuilder(ProjectUtil.EMAIL_PATTERN);
      emailRegex.deleteCharAt(emailRegex.length() - 1);
      emailRegex.deleteCharAt(0);
      String EMAIL_PATTERN = emailRegex.toString();
      Pattern emailPattern = Pattern.compile(EMAIL_PATTERN);
      Matcher emailMatcher = emailPattern.matcher(logString);
      while (emailMatcher.find()) {
        String tempStr = emailMatcher.group();
        builder.replace(emailMatcher.start(), emailMatcher.end(), service.maskEmail(tempStr));
      }
      // Mask Phone
      String PHONE_PATTERN = "[0-9]{10}";
      Pattern phonePattern = Pattern.compile(PHONE_PATTERN);
      Matcher phoneMatcher = phonePattern.matcher(logString);
      while (phoneMatcher.find()) {
        String tempStr = phoneMatcher.group();
        if (ProjectUtil.validatePhone(tempStr, "")) {
          builder.replace(phoneMatcher.start(), phoneMatcher.end(), service.maskPhone(tempStr));
        }
      }
      return builder.toString();
    } catch (Exception ex) {
      logger.error("Exception occurred while masking PII data", ex);
    }
    return logString;
  }

  /**
   * Mask the OTP attribute within a given request map.
   *
   * @param otpReqMap The map containing request parameters.
   */
  private static void maskOtpAttributes(Map<String, Object> otpReqMap) {
    String otp = (String) otpReqMap.get(JsonKey.OTP);
    if (StringUtils.isNotBlank(otp)) {
      otpReqMap.put(JsonKey.OTP, logMaskService.maskOTP(otp));
    }
  }
}
