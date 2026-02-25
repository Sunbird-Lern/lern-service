package org.sunbird.common;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.sunbird.common.ProjectUtil.AssessmentResult;
import org.sunbird.exception.ProjectCommonException;
import org.sunbird.http.HttpUtil;
import org.sunbird.keys.JsonKey;
import org.sunbird.request.Request;
import org.sunbird.request.RequestContext;
import org.sunbird.response.ResponseCode;
import org.apache.velocity.VelocityContext;

/**
 * Unit tests for {@link ProjectUtil} class.
 * Covers utility methods for string manipulation, date formatting,
 * validation, and configuration handling.
 */
public class ProjectUtilTest {

  @Before
  public void setUp() {
    // No setup needed for PropertiesCache, using real singleton
  }

  @After
  public void tearDown() {
    // Cleanup if needed
  }

  /**
   * Verifies that {@link ProjectUtil#isStringNullOREmpty(String)} correctly identifies
   * null or empty strings (including those with only whitespace).
   */
  @Test
  public void testIsStringNullOREmpty() {
    assertTrue(ProjectUtil.isStringNullOREmpty(null));
    assertTrue(ProjectUtil.isStringNullOREmpty(""));
    assertTrue(ProjectUtil.isStringNullOREmpty("   "));
    assertFalse(ProjectUtil.isStringNullOREmpty("valid"));
  }

  /**
   * Verifies that {@link ProjectUtil#getFormattedDate()} returns a non-null formatted date string.
   */
  @Test
  public void testGetFormattedDate() {
    String date = ProjectUtil.getFormattedDate();
    assertNotNull(date);
  }

  /**
   * Verifies that {@link ProjectUtil#getTimeStamp()} returns a non-null timestamp.
   */
  @Test
  public void testGetTimeStamp() {
    Date date = ProjectUtil.getTimeStamp();
    assertNotNull(date);
  }

  /**
   * Verifies that {@link ProjectUtil#formatDate(Date)} formats a date correctly
   * and returns null for null input.
   */
  @Test
  public void testFormatDate() {
    Date now = new Date();
    String formatted = ProjectUtil.formatDate(now);
    assertNotNull(formatted);
    assertNull(ProjectUtil.formatDate(null));
  }

  /**
   * Verifies that {@link ProjectUtil#isEmailvalid(String)} correctly validates email addresses.
   */
  @Test
  public void testIsEmailValid() {
    assertTrue(ProjectUtil.isEmailvalid("test@example.com"));
    assertTrue(ProjectUtil.isEmailvalid("test.user@example.co.in"));
    assertFalse(ProjectUtil.isEmailvalid("invalid-email"));
    assertFalse(ProjectUtil.isEmailvalid(null));
    assertFalse(ProjectUtil.isEmailvalid(""));
  }

  /**
   * Verifies that {@link ProjectUtil#createAuthToken(String, String)} generates a non-null token.
   */
  @Test
  public void testCreateAuthToken() {
    String token = ProjectUtil.createAuthToken("user", "web");
    assertNotNull(token);
  }

  /**
   * Verifies that {@link ProjectUtil#getUniqueIdFromTimestamp(int)} generates a non-null ID.
   */
  @Test
  public void testGetUniqueIdFromTimestamp() {
    String id = ProjectUtil.getUniqueIdFromTimestamp(1);
    assertNotNull(id);
  }

  /**
   * Verifies that {@link ProjectUtil#generateUniqueId()} generates a non-null unique ID.
   */
  @Test
  public void testGenerateUniqueId() {
    String id = ProjectUtil.generateUniqueId();
    assertNotNull(id);
  }

  /**
   * Verifies that {@link ProjectUtil#generateRandomPassword()} generates a password of correct length.
   */
  @Test
  public void testGenerateRandomPassword() {
    String password = ProjectUtil.generateRandomPassword();
    assertNotNull(password);
    assertEquals(9, password.length());
  }

  /**
   * Verifies that {@link ProjectUtil#validatePhoneNumber(String)} correctly validates basic phone number formats.
   */
  @Test
  public void testValidatePhoneNumber() {
    assertTrue(ProjectUtil.validatePhoneNumber("1234567890"));
    assertTrue(ProjectUtil.validatePhoneNumber("123-456-7890"));
    assertFalse(ProjectUtil.validatePhoneNumber("12345"));
  }

  /**
   * Verifies that {@link ProjectUtil#validatePhone(String, String)} validates phone numbers with country code logic.
   */
  @Test
  public void testValidatePhone() {
    assertTrue(ProjectUtil.validatePhone("9876543210", "91"));
    assertFalse(ProjectUtil.validatePhone("123", "91"));
  }

  /**
   * Verifies that {@link ProjectUtil#validateCountryCode(String)} validates country codes.
   */
  @Test
  public void testValidateCountryCode() {
    assertTrue(ProjectUtil.validateCountryCode("+91"));
    assertTrue(ProjectUtil.validateCountryCode("91"));
    assertFalse(ProjectUtil.validateCountryCode("invalid"));
  }

  /**
   * Verifies that {@link ProjectUtil#validateUUID(String)} validates UUID strings.
   */
  @Test
  public void testValidateUUID() {
    assertTrue(ProjectUtil.validateUUID("550e8400-e29b-41d4-a716-446655440000"));
    assertFalse(ProjectUtil.validateUUID("invalid-uuid"));
  }

  /**
   * Verifies that {@link ProjectUtil#isDateValidFormat(String, String)} validates date strings against a format.
   */
  @Test
  public void testIsDateValidFormat() {
    assertTrue(ProjectUtil.isDateValidFormat("yyyy-MM-dd", "2023-10-27"));
    assertFalse(ProjectUtil.isDateValidFormat("yyyy-MM-dd", "27-10-2023"));
    assertFalse(ProjectUtil.isDateValidFormat("yyyy-MM-dd", "invalid"));
  }

  /**
   * Verifies that {@link ProjectUtil#isUrlvalid(String)} validates URLs.
   */
  @Test
  public void testIsUrlValid() {
      assertTrue(ProjectUtil.isUrlvalid("http://google.com"));
      assertTrue(ProjectUtil.isUrlvalid("https://google.com"));
      assertFalse(ProjectUtil.isUrlvalid("ftp://google.com"));
      assertFalse(ProjectUtil.isUrlvalid("invalid-url"));
  }

  /**
   * Verifies that {@link ProjectUtil#calculatePercentage(double, double)} calculates percentage correctly.
   */
  @Test
  public void testCalculatePercentage() {
    assertEquals(50.0, ProjectUtil.calculatePercentage(50, 100), 0.01);
    assertEquals(0.0, ProjectUtil.calculatePercentage(0, 100), 0.01);
  }

  /**
   * Verifies that {@link ProjectUtil#calcualteAssessmentResult(double)} returns the correct grade based on percentage.
   */
  @Test
  public void testCalcualteAssessmentResult() {
    assertEquals(AssessmentResult.gradeA, ProjectUtil.calcualteAssessmentResult(100));
    assertEquals(AssessmentResult.gradeA, ProjectUtil.calcualteAssessmentResult(90));
    assertEquals(AssessmentResult.gradeB, ProjectUtil.calcualteAssessmentResult(80));
    assertEquals(AssessmentResult.gradeC, ProjectUtil.calcualteAssessmentResult(70));
    assertEquals(AssessmentResult.gradeD, ProjectUtil.calcualteAssessmentResult(60));
    assertEquals(AssessmentResult.gradeE, ProjectUtil.calcualteAssessmentResult(50));
    assertEquals(AssessmentResult.gradeF, ProjectUtil.calcualteAssessmentResult(40));
  }

  /**
   * Verifies that {@link ProjectUtil#isNull(Object)} and {@link ProjectUtil#isNotNull(Object)} behave as expected.
   */
  @Test
  public void testIsNullAndIsNotNull() {
    assertTrue(ProjectUtil.isNull(null));
    assertFalse(ProjectUtil.isNull(new Object()));
    assertTrue(ProjectUtil.isNotNull(new Object()));
    assertFalse(ProjectUtil.isNotNull(null));
  }

  /**
   * Verifies that {@link ProjectUtil#formatMessage(String, Object...)} formats messages correctly.
   */
  @Test
  public void testFormatMessage() {
    String msg = ProjectUtil.formatMessage("Hello {0}", "World");
    assertEquals("Hello World", msg);
  }

  /**
   * Verifies that {@link ProjectUtil#isNotEmptyStringArray(String[])} returns correct boolean based on array content.
   * Note: The logic implies returning true only if ALL elements are empty/null (based on previous analysis/implementation),
   * or possibly false if ANY is non-empty. This test confirms the current implementation behavior.
   */
  @Test
  public void testIsNotEmptyStringArray() {
    assertFalse(ProjectUtil.isNotEmptyStringArray(new String[]{"val"}));
    assertTrue(ProjectUtil.isNotEmptyStringArray(new String[]{""}));
    assertTrue(ProjectUtil.isNotEmptyStringArray(new String[]{null}));
    assertFalse(ProjectUtil.isNotEmptyStringArray(new String[]{"a", ""}));
    assertTrue(ProjectUtil.isNotEmptyStringArray(new String[]{"", null}));
  }

  /**
   * Verifies that {@link ProjectUtil#convertMapToJsonString(List)} converts a list of maps to a JSON string.
   */
  @Test
  public void testConvertMapToJsonString() {
    List<Map<String, Object>> list = new ArrayList<>();
    Map<String, Object> map = new HashMap<>();
    map.put("key", "value");
    list.add(map);
    String json = ProjectUtil.convertMapToJsonString(list);
    assertNotNull(json);
    assertTrue(json.contains("key"));
    assertTrue(json.contains("value"));
  }

  /**
   * Verifies that {@link ProjectUtil#removeUnwantedFields(Map, String...)} removes specified keys from a map.
   */
  @Test
  public void testRemoveUnwantedFields() {
    Map<String, Object> map = new HashMap<>();
    map.put("a", 1);
    map.put("b", 2);
    ProjectUtil.removeUnwantedFields(map, "a");
    assertFalse(map.containsKey("a"));
    assertTrue(map.containsKey("b"));
  }

  /**
   * Verifies that {@link ProjectUtil#convertJsonStringToMap(String)} parses a JSON string into a map.
   * @throws IOException if parsing fails.
   */
  @Test
  public void testConvertJsonStringToMap() throws IOException {
    String json = "{\"key\":\"value\"}";
    Map<String, Object> map = ProjectUtil.convertJsonStringToMap(json);
    assertNotNull(map);
    assertEquals("value", map.get("key"));
  }

  /**
   * Verifies that {@link ProjectUtil#convertToRequestPojo(Request, Class)} converts a Request object to the target POJO type.
   */
  @Test
  public void testConvertToRequestPojo() {
    Request request = new Request();
    Map<String, Object> map = new HashMap<>();
    map.put("name", "test");
    request.setRequest(map);

    Map result = ProjectUtil.convertToRequestPojo(request, Map.class);
    assertNotNull(result);
    assertEquals("test", result.get("name"));
  }

  /**
   * Verifies that {@link ProjectUtil#getDateRange(int)} returns a map with start and end dates.
   */
  @Test
  public void testGetDateRange() {
    Map<String, String> range = ProjectUtil.getDateRange(7);
    assertNotNull(range);
    assertTrue(range.containsKey("startDate"));
    assertTrue(range.containsKey("endDate"));

    Map<String, String> emptyRange = ProjectUtil.getDateRange(0);
    assertTrue(emptyRange.isEmpty());
  }

  /**
   * Verifies that {@link ProjectUtil#getFirstNCharacterString(String, int)} truncates string correctly.
   */
  @Test
  public void testGetFirstNCharacterString() {
    assertEquals("abc", ProjectUtil.getFirstNCharacterString("abcdef", 3));
    assertEquals("ab", ProjectUtil.getFirstNCharacterString("ab", 3));
    assertEquals("", ProjectUtil.getFirstNCharacterString("", 3));
    assertEquals("", ProjectUtil.getFirstNCharacterString(null, 3));
  }

  /**
   * Verifies that {@link ProjectUtil#createAndThrowServerError()} throws a {@link ProjectCommonException}.
   */
  @Test(expected = ProjectCommonException.class)
  public void testCreateAndThrowServerError() {
    ProjectUtil.createAndThrowServerError();
  }

  /**
   * Verifies that {@link ProjectUtil#createServerError(ResponseCode)} creates a {@link ProjectCommonException} with SERVER_ERROR code.
   */
  @Test
  public void testCreateServerError() {
    ProjectCommonException e = ProjectUtil.createServerError(ResponseCode.SERVER_ERROR);
    assertNotNull(e);
    assertEquals(ResponseCode.SERVER_ERROR.getErrorCode(), e.getCode());
  }

  /**
   * Verifies that {@link ProjectUtil#createAndThrowInvalidUserDataException()} throws a {@link ProjectCommonException}.
   */
  @Test(expected = ProjectCommonException.class)
  public void testCreateAndThrowInvalidUserDataException() {
    ProjectUtil.createAndThrowInvalidUserDataException();
  }

  /**
   * Verifies that {@link ProjectUtil#createClientException(ResponseCode)} creates a {@link ProjectCommonException} with CLIENT_ERROR code.
   */
  @Test
  public void testCreateClientException() {
    ProjectCommonException e = ProjectUtil.createClientException(ResponseCode.CLIENT_ERROR);
    assertNotNull(e);
    assertEquals(ResponseCode.CLIENT_ERROR.getErrorCode(), e.getCode());
  }

  /**
   * Verifies that {@link ProjectUtil#getConfigValue(String)} retrieves a configuration value.
   */
  @Test
  public void testGetConfigValue() {
    ProjectUtil.propertiesCache.saveConfigProperty("key", "value");
    String val = ProjectUtil.getConfigValue("key");
    assertEquals("value", val);
  }

  /**
   * Verifies that {@link ProjectUtil#createIndex()} creates a valid ElasticSearch index name.
   */
  @Test
  public void testCreateIndex() {
    String index = ProjectUtil.createIndex();
    assertNotNull(index);
    assertTrue(index.startsWith("telemetry.raw"));
  }

  /**
   * Verifies that {@link ProjectUtil#createCheckResponse(String, boolean, Exception)} generates a health check response map.
   */
  @Test
  public void testCreateCheckResponse() {
    Map<String, Object> response = ProjectUtil.createCheckResponse("service", false, null);
    assertTrue((Boolean) response.get(JsonKey.Healthy));

    response = ProjectUtil.createCheckResponse("service", true, new Exception("error"));
    assertFalse((Boolean) response.get(JsonKey.Healthy));
  }

  /**
   * Verifies that {@link ProjectUtil#getEkstepHeader()} returns headers including Authorization.
   */
  @Test
  public void testGetEkstepHeader() {
    ProjectUtil.propertiesCache.saveConfigProperty(JsonKey.EKSTEP_AUTHORIZATION, "auth");
    Map<String, String> header = ProjectUtil.getEkstepHeader();
    assertNotNull(header);
    assertTrue(header.containsKey(JsonKey.AUTHORIZATION));
  }

  /**
   * Verifies that {@link ProjectUtil#getLmsUserId(String)} extracts the user ID from a federated ID.
   */
  @Test
  public void testGetLmsUserId() {
    ProjectUtil.propertiesCache.saveConfigProperty(JsonKey.SUNBIRD_KEYCLOAK_USER_FEDERATION_PROVIDER_ID, "provider");
    String id = ProjectUtil.getLmsUserId("f:provider:user123");
    assertEquals("user123", id);

    assertEquals("other", ProjectUtil.getLmsUserId("other"));
  }

  /**
   * Verifies that {@link ProjectUtil#registertag(String, String, Map)} makes an HTTP POST request
   * by mocking the {@link HttpUtil} class.
   * @throws Exception if an error occurs.
   */
  @Test
  public void testRegisterTag() throws Exception {
    try (MockedStatic<HttpUtil> mockedHttpUtil = mockStatic(HttpUtil.class)) {
      ProjectUtil.propertiesCache.saveConfigProperty(JsonKey.EKSTEP_TAG_API_URL, "/tag");
      ProjectUtil.propertiesCache.saveConfigProperty(JsonKey.ANALYTICS_API_BASE_URL, "http://analytics");
      mockedHttpUtil.when(() -> HttpUtil.sendPostRequest(anyString(), anyString(), any())).thenReturn("success");

      String status = ProjectUtil.registertag("tagId", "{}", new HashMap<>());
      assertEquals("success", status);
    }
  }

  /**
   * Verifies that {@link ProjectUtil#getContext(Map)} creates a VelocityContext with expected values.
   */
  @Test
  public void testGetContext() {
    Map<String, Object> map = new HashMap<>();
    map.put(JsonKey.ACTION_URL, "url");
    map.put(JsonKey.NAME, "name");

    ProjectUtil.propertiesCache.saveConfigProperty(JsonKey.SUNBIRD_ALLOWED_LOGIN, "true");

    VelocityContext context = ProjectUtil.getContext(map);
    assertNotNull(context);
    assertEquals("url", context.get(JsonKey.ACTION_URL));
  }

  /**
   * Verifies that {@link ProjectUtil#setTraceIdInHeader(Map, RequestContext)} populates headers with trace information.
   */
  @Test
  public void testSetTraceIdInHeader() {
    RequestContext context = new RequestContext();
    context.setReqId("reqId");
    context.setDebugEnabled("true");

    Map<String, String> header = new HashMap<>();
    ProjectUtil.setTraceIdInHeader(header, context);

    assertEquals("reqId", header.get(JsonKey.X_REQUEST_ID));
    assertEquals("true", header.get(JsonKey.X_TRACE_ENABLED));
  }
}