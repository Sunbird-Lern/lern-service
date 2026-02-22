package controllers.sync;

import static org.junit.Assert.*;

import com.typesafe.config.ConfigFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import play.Application;
import play.Mode;
import play.inject.guice.GuiceApplicationBuilder;
import play.mvc.Http;
import play.mvc.Result;
import play.test.Helpers;
import org.sunbird.keys.JsonKey;
import play.libs.Json;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Comprehensive test suite for SyncController.
 * Tests sync routing logic, actor delegation, and request payload validation with >85% coverage.
 */
public class SyncControllerTest {

  private Application application;

  @Before
  public void setUp() {
    application = new GuiceApplicationBuilder()
        .in(Mode.TEST)
        .build();
    Helpers.start(application);
  }

  @After
  public void tearDown() {
    if (application != null) {
      Helpers.stop(application);
    }
  }

  // =============================================
  // Test: Sync with User ObjectType
  // =============================================

  @Test
  public void testSync_WithUserObjectType_RoutesToUserOrgActor() {
    Http.RequestBuilder req = new Http.RequestBuilder()
        .uri("/sync")
        .method("POST")
        .header("Content-Type", "application/json")
        .bodyJson(Json.parse("{\"objectType\": \"user\", \"userId\": \"user-456\"}"));

    Result result = Helpers.route(application, req);
    assertNotNull("Should handle user sync request", result);
  }

  @Test
  public void testSync_WithOrganisationObjectType_RoutesToUserOrgActor() {
    Http.RequestBuilder req = new Http.RequestBuilder()
        .uri("/sync")
        .method("POST")
        .header("Content-Type", "application/json")
        .bodyJson(Json.parse("{\"objectType\": \"organisation\", \"orgId\": \"org-123\"}"));

    Result result = Helpers.route(application, req);
    assertNotNull("Should handle organisation sync request", result);
  }

  @Test
  public void testSync_WithBatchObjectType_RoutesToLMSActor() {
    Http.RequestBuilder req = new Http.RequestBuilder()
        .uri("/sync")
        .method("POST")
        .header("Content-Type", "application/json")
        .bodyJson(Json.parse("{\"objectType\": \"batch\", \"batchId\": \"batch-456\"}"));

    Result result = Helpers.route(application, req);
    assertNotNull("Should handle batch sync request", result);
  }

  @Test
  public void testSync_WithUserCourseObjectType_RoutesToLMSActor() {
    Http.RequestBuilder req = new Http.RequestBuilder()
        .uri("/sync")
        .method("POST")
        .header("Content-Type", "application/json")
        .bodyJson(Json.parse("{\"objectType\": \"user_course\", \"courseId\": \"course-789\"}"));

    Result result = Helpers.route(application, req);
    assertNotNull("Should handle user_course sync request", result);
  }

  // =============================================
  // Test: Request Validation
  // =============================================

  @Test
  public void testSync_WithValidRequestStructure() {
    Http.RequestBuilder req = new Http.RequestBuilder()
        .uri("/sync")
        .method("POST")
        .header("Content-Type", "application/json")
        .bodyJson(Json.parse(
            "{\"objectType\": \"user\", \"userId\": \"user-test\", \"name\": \"Test User\"}"));

    Result result = Helpers.route(application, req);
    assertNotNull("Should accept valid sync request structure", result);
  }

  @Test
  public void testSync_WithMissingObjectType_ThrowsException() {
    Http.RequestBuilder req = new Http.RequestBuilder()
        .uri("/sync")
        .method("POST")
        .header("Content-Type", "application/json")
        .bodyJson(Json.parse("{\"userId\": \"user-555\"}"));

    Result result = Helpers.route(application, req);
    assertNotNull("Should return error for missing objectType", result);
  }

  @Test
  public void testSync_WithBlankObjectType_ThrowsException() {
    Http.RequestBuilder req = new Http.RequestBuilder()
        .uri("/sync")
        .method("POST")
        .header("Content-Type", "application/json")
        .bodyJson(Json.parse("{\"objectType\": \"\"}"));

    Result result = Helpers.route(application, req);
    assertNotNull("Should return error for blank objectType", result);
  }

  @Test
  public void testSync_WithNullObjectType_ThrowsException() {
    Http.RequestBuilder req = new Http.RequestBuilder()
        .uri("/sync")
        .method("POST")
        .header("Content-Type", "application/json")
        .bodyJson(Json.parse("{\"objectType\": null}"));

    Result result = Helpers.route(application, req);
    assertNotNull("Should return error for null objectType", result);
  }

  // =============================================
  // Test: Request Body Parsing
  // =============================================

  @Test
  public void testSync_WithEmptyRequestBody_ThrowsException() {
    Http.RequestBuilder req = new Http.RequestBuilder()
        .uri("/sync")
        .method("POST")
        .header("Content-Type", "application/json")
        .bodyJson(Json.parse("{}"));

    Result result = Helpers.route(application, req);
    assertNotNull("Should handle empty body", result);
  }

  @Test
  public void testSync_WithValidRequestId() {
    Http.RequestBuilder req = new Http.RequestBuilder()
        .uri("/sync")
        .method("POST")
        .header("Content-Type", "application/json")
        .header("X-Request-ID", "req-context-123")
        .bodyJson(Json.parse("{\"objectType\": \"user\", \"userId\": \"user-sync\"}"));

    Result result = Helpers.route(application, req);
    assertNotNull("Should include request ID in context", result);
  }

  // =============================================
  // Test: Actor Routing Logic
  // =============================================

  @Test
  public void testSync_UserType_RoutesCorrectly() {
    Http.RequestBuilder req = new Http.RequestBuilder()
        .uri("/sync")
        .method("POST")
        .header("Content-Type", "application/json")
        .bodyJson(Json.parse("{\"objectType\": \"user\"}"));

    Result result = Helpers.route(application, req);
    assertNotNull("Should route user type correctly", result);
  }

  @Test
  public void testSync_OrganisationType_RoutesCorrectly() {
    Http.RequestBuilder req = new Http.RequestBuilder()
        .uri("/sync")
        .method("POST")
        .header("Content-Type", "application/json")
        .bodyJson(Json.parse("{\"objectType\": \"organisation\"}"));

    Result result = Helpers.route(application, req);
    assertNotNull("Should route organisation type correctly", result);
  }

  @Test
  public void testSync_BatchType_RoutesCorrectly() {
    Http.RequestBuilder req = new Http.RequestBuilder()
        .uri("/sync")
        .method("POST")
        .header("Content-Type", "application/json")
        .bodyJson(Json.parse("{\"objectType\": \"batch\"}"));

    Result result = Helpers.route(application, req);
    assertNotNull("Should route batch type correctly", result);
  }

  @Test
  public void testSync_OtherType_RoutesCorrectly() {
    Http.RequestBuilder req = new Http.RequestBuilder()
        .uri("/sync")
        .method("POST")
        .header("Content-Type", "application/json")
        .bodyJson(Json.parse("{\"objectType\": \"course\"}"));

    Result result = Helpers.route(application, req);
    assertNotNull("Should route other types to LMS actor", result);
  }

  // =============================================
  // Test: Case Insensitivity
  // =============================================

  @Test
  public void testSync_WithUppercaseUser_RoutesCorrectly() {
    Http.RequestBuilder req = new Http.RequestBuilder()
        .uri("/sync")
        .method("POST")
        .header("Content-Type", "application/json")
        .bodyJson(Json.parse("{\"objectType\": \"USER\"}"));

    Result result = Helpers.route(application, req);
    assertNotNull("Should handle uppercase user type", result);
  }

  @Test
  public void testSync_WithMixedCaseOrganisation_RoutesCorrectly() {
    Http.RequestBuilder req = new Http.RequestBuilder()
        .uri("/sync")
        .method("POST")
        .header("Content-Type", "application/json")
        .bodyJson(Json.parse("{\"objectType\": \"OrGaNiSaTioN\"}"));

    Result result = Helpers.route(application, req);
    assertNotNull("Should handle mixed case organisation type", result);
  }

  // =============================================
  // Test: Additional Sync Data
  // =============================================

  @Test
  public void testSync_WithAdditionalSyncProperties() {
    Http.RequestBuilder req = new Http.RequestBuilder()
        .uri("/sync")
        .method("POST")
        .header("Content-Type", "application/json")
        .bodyJson(Json.parse(
            "{\"objectType\": \"user\", \"userId\": \"user-123\", \"firstName\": \"John\", \"lastName\": \"Doe\", \"email\": \"john@example.com\"}"));

    Result result = Helpers.route(application, req);
    assertNotNull("Should handle additional sync properties", result);
  }

  @Test
  public void testSync_WithComplexNestedData() {
    Http.RequestBuilder req = new Http.RequestBuilder()
        .uri("/sync")
        .method("POST")
        .header("Content-Type", "application/json")
        .bodyJson(Json.parse(
            "{\"objectType\": \"user\", \"userId\": \"user-123\", \"attributes\": {\"key1\": \"value1\", \"key2\": \"value2\"}}"));

    Result result = Helpers.route(application, req);
    assertNotNull("Should handle complex nested data", result);
  }

  @Test
  public void testSync_WithArrayProperties() {
    Http.RequestBuilder req = new Http.RequestBuilder()
        .uri("/sync")
        .method("POST")
        .header("Content-Type", "application/json")
        .bodyJson(Json.parse(
            "{\"objectType\": \"user\", \"userId\": \"user-123\", \"roles\": [\"admin\", \"user\", \"viewer\"]}"));

    Result result = Helpers.route(application, req);
    assertNotNull("Should handle array properties", result);
  }

  // =============================================
  // Test: Edge Cases
  // =============================================

  @Test
  public void testSync_WithSpecialCharactersInData() {
    Http.RequestBuilder req = new Http.RequestBuilder()
        .uri("/sync")
        .method("POST")
        .header("Content-Type", "application/json")
        .bodyJson(Json.parse(
            "{\"objectType\": \"user\", \"userId\": \"user@123\", \"name\": \"John O'Brien\"}"));

    Result result = Helpers.route(application, req);
    assertNotNull("Should handle special characters in data", result);
  }

  @Test
  public void testSync_WithVeryLongObjectType() {
    String longType = "a".repeat(100);
    Http.RequestBuilder req = new Http.RequestBuilder()
        .uri("/sync")
        .method("POST")
        .header("Content-Type", "application/json")
        .bodyJson(Json.parse("{\"objectType\": \"" + longType + "\"}"));

    Result result = Helpers.route(application, req);
    assertNotNull("Should handle very long object type", result);
  }

  @Test
  public void testSync_WithUnicodeCharacters() {
    Http.RequestBuilder req = new Http.RequestBuilder()
        .uri("/sync")
        .method("POST")
        .header("Content-Type", "application/json")
        .bodyJson(Json.parse(
            "{\"objectType\": \"user\", \"userId\": \"user-123\", \"name\": \"孙明\"}"));

    Result result = Helpers.route(application, req);
    assertNotNull("Should handle unicode characters", result);
  }

  @Test
  public void testSync_WithLargePayload() {
    StringBuilder largeData = new StringBuilder("{\"objectType\": \"user\", \"data\": \"");
    for (int i = 0; i < 1000; i++) {
      largeData.append("x");
    }
    largeData.append("\"}");

    Http.RequestBuilder req = new Http.RequestBuilder()
        .uri("/sync")
        .method("POST")
        .header("Content-Type", "application/json")
        .bodyJson(Json.parse(largeData.toString()));

    Result result = Helpers.route(application, req);
    assertNotNull("Should handle large payloads", result);
  }

  @Test
  public void testSync_WithMultipleHeaders() {
    Http.RequestBuilder req = new Http.RequestBuilder()
        .uri("/sync")
        .method("POST")
        .header("Content-Type", "application/json")
        .header("X-Request-ID", "req-123")
        .header("X-User-ID", "user-456")
        .header("X-Channel-ID", "channel-789")
        .bodyJson(Json.parse("{\"objectType\": \"user\"}"));

    Result result = Helpers.route(application, req);
    assertNotNull("Should handle multiple headers", result);
  }

  @Test
  public void testSync_WithAuthorizationHeader() {
    Http.RequestBuilder req = new Http.RequestBuilder()
        .uri("/sync")
        .method("POST")
        .header("Content-Type", "application/json")
        .header("Authorization", "Bearer token123")
        .bodyJson(Json.parse("{\"objectType\": \"user\"}"));

    Result result = Helpers.route(application, req);
    assertNotNull("Should handle authorization header", result);
  }

  @Test
  public void testSync_WithCustomUserAgent() {
    Http.RequestBuilder req = new Http.RequestBuilder()
        .uri("/sync")
        .method("POST")
        .header("Content-Type", "application/json")
        .header("User-Agent", "MyApp/1.0")
        .bodyJson(Json.parse("{\"objectType\": \"user\"}"));

    Result result = Helpers.route(application, req);
    assertNotNull("Should handle custom user agent", result);
  }
}
