package org.sunbird.http;

import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.sunbird.common.ProjectUtil;
import org.sunbird.keys.JsonKey;
import org.sunbird.response.HttpUtilResponse;
import org.sunbird.logging.LoggerUtil;
import org.sunbird.response.ResponseCode;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Utility class to handle external HTTP calls.
 * Provides methods for GET, POST, and PATCH requests using Unirest.
 */
public class HttpUtil {

  public static LoggerUtil logger = new LoggerUtil(HttpUtil.class);
  // Per host: enough for the actors that call search at once (32 enrolment + 4 viewer-summary +
  // 4 QR-code download). Unirest's own default is 20 per host, which queues enrolment list calls.
  static final int DEFAULT_UNIREST_MAX_TOTAL_CONNECTIONS = 200;
  static final int DEFAULT_UNIREST_MAX_CONNECTIONS_PER_ROUTE = 40;

  private HttpUtil() {}

  /**
   * Sets the Unirest connection pool size from config (unirest_max_total_connections,
   * unirest_max_connections_per_route), falling back to 200 total / 40 per host for a missing or
   * invalid value. Unirest has one client per JVM, so this applies to every Unirest call in the
   * service; call it once at startup, before the first request.
   */
  public static void configureUnirestConnectionPool() {
    int maxTotal = positiveConfigValue(JsonKey.UNIREST_MAX_TOTAL_CONNECTIONS, DEFAULT_UNIREST_MAX_TOTAL_CONNECTIONS);
    int maxPerRoute = positiveConfigValue(JsonKey.UNIREST_MAX_CONNECTIONS_PER_ROUTE, DEFAULT_UNIREST_MAX_CONNECTIONS_PER_ROUTE);
    if (maxPerRoute > maxTotal) {
      logger.warn("HttpUtil:configureUnirestConnectionPool: per-host " + maxPerRoute + " exceeds total " + maxTotal
          + ", using defaults");
      maxTotal = DEFAULT_UNIREST_MAX_TOTAL_CONNECTIONS;
      maxPerRoute = DEFAULT_UNIREST_MAX_CONNECTIONS_PER_ROUTE;
    }
    Unirest.setConcurrency(maxTotal, maxPerRoute);
    logger.info("HttpUtil:configureUnirestConnectionPool: total=" + maxTotal + ", perRoute=" + maxPerRoute);
  }

  private static int positiveConfigValue(String key, int defaultValue) {
    String value = ProjectUtil.getConfigValue(key);
    if (StringUtils.isBlank(value)) {
      return defaultValue;
    }
    int parsed = NumberUtils.toInt(value.trim(), 0);
    if (parsed <= 0) {
      logger.warn("HttpUtil:configureUnirestConnectionPool: invalid " + key + "=" + value + ", using " + defaultValue);
      return defaultValue;
    }
    return parsed;
  }

  /**
   * Makes an HTTP request using GET method to the specified URL.
   *
   * @param requestURL the URL of the remote server
   * @param headers the Map &lt;String,String&gt; containing request headers
   * @return The response body as a String, or an empty string if the call fails with a non-200 status
   * @throws UnirestException thrown if any error occurred during the request
   */
  public static String sendGetRequest(String requestURL, Map<String, String> headers)
          throws UnirestException {
    long startTime = System.currentTimeMillis();
    HttpResponse<String> httpResponse = Unirest.get(requestURL).headers(headers).asString();
    if(200 == httpResponse.getStatus()) {
      long stopTime = System.currentTimeMillis();
      long elapsedTime = stopTime - startTime;
      logger.info("HttpUtil:sendGetRequest: Execution finished for URL: " + requestURL + ", duration: " + elapsedTime + " ms");
      return httpResponse.getBody();
    } else {
      logger.error("HttpUtil:sendGetRequest: Failed for URL: " + requestURL + ", Status: " + httpResponse.getStatus() + ", Response: " + httpResponse.getBody(), null);
      return "";
    }
  }

  /**
   * Makes an HTTP request using POST method to the specified URL.
   *
   * @param requestURL the URL of the remote server
   * @param params A map containing POST data in form of key-value pairs
   * @param headers the Map &lt;String,String&gt; containing request headers
   * @return The response body as a String
   * @throws Exception thrown if any error occurred during the request
   */
  public static String sendPostRequest(
      String requestURL, Map<String, String> params, Map<String, String> headers)
      throws Exception {
    long startTime = System.currentTimeMillis();
    HttpResponse<String> httpResponse = Unirest.post(requestURL).headers(headers).body(params).asString();
    String str = httpResponse.getBody();
    long stopTime = System.currentTimeMillis();
    long elapsedTime = stopTime - startTime;
    logger.info(
        "HttpUtil:sendPostRequest: Execution finished for URL: "
            + requestURL
            + ", Duration: "
            + elapsedTime
            + " ms");
    return str;
  }


  /**
   * Makes an HTTP request using POST method to the specified URL with a String body.
   *
   * @param requestURL the URL of the remote server
   * @param params String payload data
   * @param headers the Map &lt;String,String&gt; containing request headers
   * @return The response body as a String
   * @throws Exception thrown if any error occurred during the request
   */
  public static String sendPostRequest(
      String requestURL, String params, Map<String, String> headers) throws Exception {
    long startTime = System.currentTimeMillis();
    HttpResponse<String> httpResponse = Unirest.post(requestURL).headers(headers).body(params).asString();
    String str = httpResponse.getBody();
    long stopTime = System.currentTimeMillis();
    long elapsedTime = stopTime - startTime;
    logger.info(
        "HttpUtil:sendPostRequest: Execution finished for URL: "
            + requestURL
            + ", Duration: "
            + elapsedTime
            + " ms");
    return str;
  }


  /**
   * Makes an HTTP request using POST method to the specified URL and returns a comprehensive response object.
   *
   * @param requestURL the URL of the remote server
   * @param params The request body as a String
   * @param headers the Map &lt;String,String&gt; containing request headers
   * @return HttpUtilResponse containing status code and body
   * @throws IOException thrown if any I/O error occurred
   */
  public static HttpUtilResponse doPostRequest(
      String requestURL, String params, Map<String, String> headers) throws IOException {
    long startTime = System.currentTimeMillis();
    HttpUtilResponse response = new HttpUtilResponse();
    try {
      HttpResponse<String> httpResponse = Unirest.post(requestURL).headers(headers).body(params).asString();
      response = new HttpUtilResponse(httpResponse.getBody(), httpResponse.getStatus());
    } catch (Exception ex) {
      logger.error("HttpUtil:doPostRequest: Exception occurred while reading response body for URL: " + requestURL, ex);
    }
    long stopTime = System.currentTimeMillis();
    long elapsedTime = stopTime - startTime;
    logger.info(
        "HttpUtil:doPostRequest: Execution finished for URL: "
            + requestURL
            + ", Duration: "
            + elapsedTime
            + " ms");
    return response;
  }

  /**
   * Makes an HTTP request using PATCH method to the specified URL.
   *
   * @param requestURL the URL of the remote server
   * @param params The request body as a String
   * @param headers the Map &lt;String,String&gt; containing request headers
   * @return ResponseCode string success or Failure string
   */
  public static String sendPatchRequest(
      String requestURL, String params, Map<String, String> headers) {
    long startTime = System.currentTimeMillis();
    logger.info(
        "HttpUtil:sendPatchRequest: Started for URL: "
            + requestURL
            + " with params: "
            + params);

    try {
      HttpResponse<String> httpResponse = Unirest.patch(requestURL).headers(headers).body(params).asString();
      
      if (ResponseCode.OK.getResponseCode() == httpResponse.getStatus()) {
        long stopTime = System.currentTimeMillis();
        long elapsedTime = stopTime - startTime;
        logger.info(
                "HttpUtil:sendPatchRequest: Success for URL: "
                + requestURL
                + ", Status: "
                + httpResponse.getStatus()
                + ", Duration: "
                + elapsedTime
                + " ms");
        return ResponseCode.success.getErrorCode();
      }
      long stopTime = System.currentTimeMillis();
      long elapsedTime = stopTime - startTime;
      logger.info(
              "HttpUtil:sendPatchRequest: Failed for URL: "
              + requestURL
              + ", Status: "
              + httpResponse.getStatus()
              + ", Duration: "
              + elapsedTime
              + " ms");
      return "Failure";
    } catch (Exception e) {
      logger.error("HttpUtil:sendPatchRequest: Exception for URL: " + requestURL, e);
    }
    long stopTime = System.currentTimeMillis();
    long elapsedTime = stopTime - startTime;
    logger.info(
        "HttpUtil:sendPatchRequest: Ended with failure for URL: "
            + requestURL
            + ", Duration: "
            + elapsedTime
            + " ms");
    return "Failure";
  }


  /**
   * Helper method to construct headers.
   * Adds Content-Type: application/json by default.
   *
   * @param input Additional headers map
   * @return A new Map containing all headers
   * @throws Exception if an error occurs
   */
  public static Map<String, String> getHeader(Map<String, String> input) throws Exception {
    Map<String, String> headers = new HashMap<>();
    headers.put("Content-Type", "application/json");
    if (MapUtils.isNotEmpty(input)) {
      headers.putAll(input);
    }
    return headers;
  }
}