package org.sunbird.learner.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.Unirest;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.sunbird.common.ElasticSearchHelper;
import org.sunbird.exception.ProjectCommonException;
import org.sunbird.common.factory.EsClientFactory;
import org.sunbird.common.inf.ElasticSearchService;
import org.sunbird.response.Response;
import org.sunbird.keys.JsonKey;
import org.sunbird.logging.LoggerUtil;
import org.sunbird.common.ProjectUtil;
import org.sunbird.common.ProjectUtil.EsType;
import org.sunbird.request.RequestContext;
import org.sunbird.response.ResponseCode;
import org.sunbird.learner.constants.CourseJsonKey;
import org.sunbird.models.course.batch.CourseBatch;
import scala.concurrent.Future;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

import static org.apache.http.HttpHeaders.AUTHORIZATION;

public class CourseBatchUtil {
  private static ElasticSearchService esUtil = EsClientFactory.getInstance();
  private static ObjectMapper mapper = new ObjectMapper();
  private static LoggerUtil logger = new LoggerUtil(CourseBatchUtil.class);
  private static final List<String> changeInDateFormat = JsonKey.CHANGE_IN_DATE_FORMAT;
  private static final List<String> changeInSimpleDateFormat = JsonKey.CHANGE_IN_SIMPLE_DATE_FORMAT;
  private static final List<String> changeInDateFormatAll = JsonKey.CHANGE_IN_DATE_FORMAT_ALL;
  private static final List<String> setEndOfDay = JsonKey.SET_END_OF_DAY;

  private CourseBatchUtil() {}

  public static void syncCourseBatchForeground(RequestContext requestContext, String uniqueId, Map<String, Object> req) {
    logger.info(requestContext, "CourseBatchManagementActor: syncCourseBatchForeground called for course batch ID = " + uniqueId);
    req.put(JsonKey.ID, uniqueId);
    req.put(JsonKey.IDENTIFIER, uniqueId);
    Future<String> esResponseF = esUtil.save(ProjectUtil.EsType.courseBatch.getTypeName(), uniqueId, req, requestContext);
    String esResponse = (String) ElasticSearchHelper.getResponseFromFuture(esResponseF);
    logger.info(requestContext, "CourseBatchManagementActor::syncCourseBatchForeground: Sync response for course batch ID = "
            + uniqueId + " received response = " + esResponse);
  }

  public static Map<String, Object> validateCourseBatch(RequestContext requestContext, String courseId, String batchId) {
    Future<Map<String, Object>> resultF = esUtil.getDataByIdentifier(EsType.courseBatch.getTypeName(), batchId, requestContext);
    Map<String, Object> result = (Map<String, Object>) ElasticSearchHelper.getResponseFromFuture(resultF);
    if (MapUtils.isEmpty(result)) {
      ProjectCommonException.throwClientErrorException(ResponseCode.CLIENT_ERROR, "No such batchId exists");
    }
    if (StringUtils.isNotBlank(courseId)
        && !StringUtils.equals(courseId, (String) result.get(JsonKey.COURSE_ID))) {
      ProjectCommonException.throwClientErrorException(ResponseCode.CLIENT_ERROR, "batchId is not linked with courseId");
    }
    return result;
  }

  public static Map<String, Object> validateTemplate(RequestContext requestContext, String templateId) {
    Response templateResponse = getTemplate(requestContext, templateId);
    if (templateResponse == null
        || MapUtils.isEmpty(templateResponse.getResult())
        || !(templateResponse.getResult().containsKey(JsonKey.CONTENT) || templateResponse.getResult().containsKey("certificate"))) {
      ProjectCommonException.throwClientErrorException(ResponseCode.CLIENT_ERROR, "Invalid template Id: " + templateId);
    }
    Map<String, Object> template = templateResponse.getResult().containsKey(JsonKey.CONTENT) ?
                    (Map<String, Object>) templateResponse.getResult().getOrDefault(JsonKey.CONTENT, new HashMap<>()) :
                    (Map<String, Object>) ((Map<String, Object>) templateResponse.getResult().getOrDefault("certificate", new HashMap<>())).getOrDefault(JsonKey.TEMPLATE, new HashMap<>());

    if (MapUtils.isEmpty(template) || !templateId.equals(template.get(JsonKey.IDENTIFIER))) {
      ProjectCommonException.throwClientErrorException(ResponseCode.CLIENT_ERROR, "Invalid template Id: " + templateId);
    }
    return template;
  }

  private static Response getTemplate(RequestContext requestContext, String templateId) {
    Response response = null;
    String responseBody = null;
    try {
      responseBody = readTemplate(requestContext, templateId);
      response = mapper.readValue(responseBody, Response.class);
      if (!ResponseCode.OK.equals(response.getResponseCode())) {
        throw new ProjectCommonException(
            response.getResponseCode().name(),
            response.getParams().getErrmsg(),
            response.getResponseCode().getResponseCode());
      }
    } catch (ProjectCommonException e) {
      logger.error(requestContext, "CourseBatchUtil:getResponse ProjectCommonException:"
              + "Request , Status : " + e.getCode()
              + " " + e.getMessage() + ",Response Body :"
              + responseBody, e);
      throw e;
    } catch (Exception e) {
      e.printStackTrace();
      logger.error(requestContext, "CourseBatchUtil:getResponse occurred with error message = "
              + e.getMessage()
              + ", Response Body : " + responseBody,
          e);
      ProjectCommonException.throwServerErrorException(ResponseCode.SERVER_ERROR, "Exception while validating template with cert service");
    }
    return response;
  }

  private static Map<String, String> getdefaultHeaders() {
    Map<String, String> headers = new HashMap<>();
    headers.put(AUTHORIZATION, JsonKey.BEARER + ProjectUtil.getConfigValue(JsonKey.SUNBIRD_AUTHORIZATION));
    headers.put("Content-Type", "application/json");
    return headers;
  }

  private static String readTemplate(RequestContext requestContext, String templateId) throws Exception {
    String templateRelativeUrl = ProjectUtil.getConfigValue("sunbird_cert_template_url");
    String certTemplateReadUrl = ProjectUtil.getConfigValue("sunbird_cert_template_read_url");
    String contentServiceBaseUrl = ProjectUtil.getConfigValue(JsonKey.CONTENT_SERVICE_BASE_URL);
    String certServiceBaseUrl = ProjectUtil.getConfigValue("sunbird_cert_service_base_url");
    HttpResponse<String> httpResponse = null;
    httpResponse = templateReadResponse(requestContext, contentServiceBaseUrl, templateRelativeUrl, templateId);

    if (httpResponse.getStatus() == 404) {
      //asset read is not found then read from the cert/v1/read api
      httpResponse = templateReadResponse(requestContext, certServiceBaseUrl, certTemplateReadUrl, templateId);
      if (httpResponse.getStatus() == 404)
        ProjectCommonException.throwClientErrorException(ResponseCode.RESOURCE_NOT_FOUND, "Given cert template not found: " + templateId);
    }
    if (StringUtils.isBlank(httpResponse.getBody())) {
      ProjectCommonException.throwServerErrorException(ResponseCode.SERVER_ERROR, ResponseCode.errorProcessingRequest.getErrorMessage());
    }
    return httpResponse.getBody();
  }

  private static HttpResponse<String> templateReadResponse(RequestContext requestContext, String baseUrl, String templateRelativeUrl, String templateId) throws Exception {
    String certTempUrl = getTemplateUrl(requestContext, baseUrl, templateRelativeUrl, templateId);
    HttpResponse<String> httpResponse = null;
    httpResponse = Unirest.get(certTempUrl).headers(getdefaultHeaders()).asString();
    logger.info(requestContext, "CourseBatchUtil:getResponse Response Status : " + httpResponse.getStatus());
    return httpResponse;
  }

  private static String getTemplateUrl(RequestContext requestContext, String baseUrl, String templateRelativeUrl, String templateId) {
    String certTempUrl = baseUrl + templateRelativeUrl + "/" + templateId + "?fields=certType,artifactUrl,issuer,signatoryList,name,data";
    logger.info(requestContext, "CourseBatchUtil:getTemplate certTempUrl : " + certTempUrl);
    return certTempUrl;
  }

  // Method will change the date variables into text with valid format
  public static Map<String, Object> esCourseMapping(CourseBatch courseBatch, String pattern) throws Exception {
    SimpleDateFormat dateFormat = ProjectUtil.getDateFormatter(pattern);
    SimpleDateFormat dateTimeFormat = ProjectUtil.getDateFormatter();
    dateFormat.setTimeZone(TimeZone.getTimeZone(ProjectUtil.getConfigValue(JsonKey.SUNBIRD_TIMEZONE)));
    dateTimeFormat.setTimeZone(TimeZone.getTimeZone(ProjectUtil.getConfigValue(JsonKey.SUNBIRD_TIMEZONE)));

    // Create UTC formatter for storing dates in ISO 8601 format in ES
    SimpleDateFormat utcDateTimeFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
    utcDateTimeFormat.setTimeZone(TimeZone.getTimeZone("UTC"));

    Map<String, Object> esCourseMap = mapper.convertValue(courseBatch, Map.class);

    changeInDateFormat.forEach(key -> {
      if (null != esCourseMap.get(key)) {
        Object value = esCourseMap.get(key);
        Date dateValue;
        if (value instanceof Date) {
          dateValue = (Date) value;
        } else if (value instanceof Long) {
          dateValue = new Date((Long) value);
        } else {
          logger.error("CourseBatchUtil:esCourseMapping: Unexpected date type for key " + key + ": " + value.getClass().getName());
          esCourseMap.put(key, null);
          return;
        }
        esCourseMap.put(key, dateTimeFormat.format(dateValue));
      } else {
        esCourseMap.put(key, null);
      }
    });

    // Format date-only fields with ISO 8601 UTC timestamps
    changeInSimpleDateFormat.forEach(key -> {
      if (null != esCourseMap.get(key)) {
        try {
          Date dateValue;
          Object value = esCourseMap.get(key);
          // Handle both Date objects and Long timestamps
          if (value instanceof Date) {
            dateValue = (Date) value;
          } else if (value instanceof Long) {
            dateValue = new Date((Long) value);
          } else {
            logger.error("CourseBatchUtil:esCourseMapping: Unexpected date type for key " + key + ": " + value.getClass().getName());
            esCourseMap.put(key, null);
            return;
          }
          // Format in local timezone first to normalize
          String formatted = dateTimeFormat.format(dateValue);
          Date normalized = dateTimeFormat.parse(formatted);
          // Apply end-of-day for endDate and enrollmentEndDate fields
          Date finalDate = setEndOfDay(key, normalized, dateFormat);
          // Format as ISO 8601 UTC string for ES
          esCourseMap.put(key, utcDateTimeFormat.format(finalDate));
        } catch (ParseException e) {
          logger.error("CourseBatchUtil:esCourseMapping: Error formatting date for key " + key + ": " + e.getMessage(), e);
          esCourseMap.put(key, null);
        }
      } else {
        esCourseMap.put(key, null);
      }
    });

    esCourseMap.put(CourseJsonKey.CERTIFICATE_TEMPLATES_COLUMN, courseBatch.getCertTemplates());
    return esCourseMap;
  }

  // Method will change the timestamp (Long) into date with valid format
  public static Map<String, Object> cassandraCourseMapping(CourseBatch courseBatch, String pattern) {
    SimpleDateFormat dateFormat = ProjectUtil.getDateFormatter(pattern);
    SimpleDateFormat dateTimeFormat = ProjectUtil.getDateFormatter();
    dateFormat.setTimeZone(TimeZone.getTimeZone(ProjectUtil.getConfigValue(JsonKey.SUNBIRD_TIMEZONE)));
    dateTimeFormat.setTimeZone(TimeZone.getTimeZone(ProjectUtil.getConfigValue(JsonKey.SUNBIRD_TIMEZONE)));
    Map<String, Object> courseBatchMap = mapper.convertValue(courseBatch, Map.class);
    changeInDateFormatAll.forEach(key -> {
      try {
        if (courseBatchMap.containsKey(key))
          courseBatchMap.put(key, setEndOfDay(key, dateTimeFormat.parse(dateTimeFormat.format(courseBatchMap.get(key))), dateFormat));
      } catch (ParseException e) {
        logger.error("CourseBatchUtil:cassandraCourseMapping: Exception occurred with message = " + e.getMessage(), e);
      }
    });
    return courseBatchMap;
  }

  // Method will add endOfDay (23:59:59:999) in endDate and enrollmentEndDate
  private static Date setEndOfDay(String key, Date value, SimpleDateFormat dateFormat) {
    try {
      if (setEndOfDay.contains(key)) {
        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone(ProjectUtil.getConfigValue(JsonKey.SUNBIRD_TIMEZONE)));
        cal.setTime(dateFormat.parse(dateFormat.format(value)));
        cal.set(Calendar.HOUR_OF_DAY, 23);
        cal.set(Calendar.MINUTE, 59);
        cal.set(Calendar.SECOND, 59);
        cal.set(Calendar.MILLISECOND, 999);
        return cal.getTime();
      }
    } catch (ParseException e) {
      logger.error("CourseBatchUtil:setEndOfDay: Exception occurred with message = " + e.getMessage(), e);
    }
    return value;
  }

  /**
   * Compute batch status from start and end dates.
   * Status derivation:
   *   - NOT_STARTED (0): today < startDate
   *   - STARTED (1): startDate <= today AND (endDate == null OR today <= endDate)
   *   - COMPLETED (2): endDate != null AND today > endDate
   *
   * @param startDate the batch start date
   * @param endDate the batch end date (may be null for ongoing batches)
   * @return batch status (0, 1, or 2)
   */
  private static Date getTodayMidnightDate() {
    TimeZone tz = TimeZone.getTimeZone(ProjectUtil.getConfigValue(JsonKey.SUNBIRD_TIMEZONE));
    Calendar todayCal = Calendar.getInstance(tz);
    todayCal.set(Calendar.HOUR_OF_DAY, 0);
    todayCal.set(Calendar.MINUTE, 0);
    todayCal.set(Calendar.SECOND, 0);
    todayCal.set(Calendar.MILLISECOND, 0);
    return todayCal.getTime();
  }

  public static String getTodayBoundaryUtc() {
    SimpleDateFormat utcFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
    utcFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
    return utcFormat.format(getTodayMidnightDate());
  }

  public static int computeBatchStatus(Date startDate, Date endDate) {
    Date today = getTodayMidnightDate();

    if (today.before(startDate)) {
      return ProjectUtil.ProgressStatus.NOT_STARTED.getValue();  // 0
    }

    if (endDate == null || !today.after(endDate)) {
      return ProjectUtil.ProgressStatus.STARTED.getValue();  // 1
    }

    return ProjectUtil.ProgressStatus.COMPLETED.getValue();  // 2
  }

  /**
   * Enrich a batch map with computed status from its dates.
   * Handles ISO 8601 date format from Elasticsearch.
   *
   * @param batchMap the batch data map (with startDate and endDate fields)
   */
  public static void enrichBatchStatusFromDates(Map<String, Object> batchMap) {
    if (MapUtils.isEmpty(batchMap)) {
      return;
    }
    try {
      // ES dates can be in multiple formats:
      // 1. ISO 8601 with time: "2026-03-31T18:29:59.999Z" (new format)
      // 2. Date only: "2026-03-31" (old format - represents local timezone date)
      TimeZone localTz = TimeZone.getTimeZone(ProjectUtil.getConfigValue(JsonKey.SUNBIRD_TIMEZONE));

      SimpleDateFormat isoFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
      isoFormat.setTimeZone(TimeZone.getTimeZone("UTC"));

      SimpleDateFormat dateOnlyFormat = new SimpleDateFormat("yyyy-MM-dd");
      dateOnlyFormat.setTimeZone(localTz);  // Old format uses local timezone

      Date startDate = null;
      Date endDate = null;

      if (batchMap.get(JsonKey.START_DATE) != null) {
        String startDateStr = batchMap.get(JsonKey.START_DATE).toString();
        startDate = parseEsDateForComparison(startDateStr, isoFormat, dateOnlyFormat, localTz);
      }

      if (batchMap.get(JsonKey.END_DATE) != null) {
        String endDateStr = batchMap.get(JsonKey.END_DATE).toString();
        endDate = parseEsDateForComparison(endDateStr, isoFormat, dateOnlyFormat, localTz);
      }

      if (startDate == null) {
        logger.warn("enrichBatchStatusFromDates: startDate missing, skipping status enrichment for batch: " + batchMap.get(JsonKey.ID));
        return;
      }
      int computedStatus = computeBatchStatus(startDate, endDate);
      batchMap.put(JsonKey.STATUS, computedStatus);
    } catch (ParseException e) {
      logger.error("enrichBatchStatusFromDates: date parse error - " + e.getMessage(), e);
    } catch (Exception e) {
      logger.error("enrichBatchStatusFromDates: unexpected error - " + e.getMessage(), e);
    }
  }

  private static Date parseEsDate(String dateStr, SimpleDateFormat isoFormat, SimpleDateFormat dateOnlyFormat) throws ParseException {
    try {
      // Try ISO 8601 format first
      return isoFormat.parse(dateStr);
    } catch (ParseException e) {
      // Fall back to date-only format
      return dateOnlyFormat.parse(dateStr);
    }
  }

  private static Date parseEsDateForComparison(String dateStr, SimpleDateFormat isoFormat, SimpleDateFormat dateOnlyFormat, TimeZone localTz) throws ParseException {
    try {
      // Try ISO 8601 format first (new format with UTC timestamp)
      Date parsedDate = isoFormat.parse(dateStr);
      // Convert UTC time to local timezone representation for comparison
      SimpleDateFormat localFormat = new SimpleDateFormat("yyyy-MM-dd");
      localFormat.setTimeZone(localTz);
      String localDateStr = localFormat.format(parsedDate);
      return localFormat.parse(localDateStr);
    } catch (ParseException e) {
      // Fall back to date-only format (old format - already in local timezone)
      return dateOnlyFormat.parse(dateStr);
    }
  }
}
