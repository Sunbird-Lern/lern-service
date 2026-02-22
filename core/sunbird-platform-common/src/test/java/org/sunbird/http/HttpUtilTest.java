package org.sunbird.http;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import com.mashape.unirest.request.GetRequest;
import com.mashape.unirest.request.HttpRequestWithBody;
import com.mashape.unirest.request.body.RequestBodyEntity;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.sunbird.response.HttpUtilResponse;
import org.sunbird.response.ResponseCode;

public class HttpUtilTest {

  private MockedStatic<Unirest> unirestMockedStatic;
  private GetRequest getRequestMock;
  private HttpRequestWithBody httpRequestWithBodyMock;
  private RequestBodyEntity requestBodyEntityMock;
  private HttpResponse<String> httpResponseMock;

  @Before
  @SuppressWarnings("unchecked")
  public void setUp() {
    unirestMockedStatic = Mockito.mockStatic(Unirest.class);
    getRequestMock = mock(GetRequest.class);
    httpRequestWithBodyMock = mock(HttpRequestWithBody.class);
    requestBodyEntityMock = mock(RequestBodyEntity.class);
    httpResponseMock = mock(HttpResponse.class);
  }

  @After
  public void tearDown() {
    unirestMockedStatic.close();
  }

  private Map<String, String> getHeaders() {
    Map<String, String> headers = new HashMap<>();
    headers.put("Content-Type", "application/json");
    return headers;
  }

  @Test
  public void testSendGetRequestSuccess() throws UnirestException {
    String url = "http://localhost:8080/api/v1/user";
    String responseBody = "{\"id\":\"123\"}";

    unirestMockedStatic.when(() -> Unirest.get(anyString())).thenReturn(getRequestMock);
    when(getRequestMock.headers(anyMap())).thenReturn(getRequestMock);
    when(getRequestMock.asString()).thenReturn(httpResponseMock);
    when(httpResponseMock.getStatus()).thenReturn(200);
    when(httpResponseMock.getBody()).thenReturn(responseBody);

    String result = HttpUtil.sendGetRequest(url, getHeaders());
    assertEquals(responseBody, result);
  }

  @Test
  public void testSendGetRequestFailure() throws UnirestException {
    String url = "http://localhost:8080/api/v1/user";

    unirestMockedStatic.when(() -> Unirest.get(anyString())).thenReturn(getRequestMock);
    when(getRequestMock.headers(anyMap())).thenReturn(getRequestMock);
    when(getRequestMock.asString()).thenReturn(httpResponseMock);
    when(httpResponseMock.getStatus()).thenReturn(404);
    when(httpResponseMock.getBody()).thenReturn("Not Found");

    String result = HttpUtil.sendGetRequest(url, getHeaders());
    assertEquals("", result);
  }

  @Test
  public void testSendPostRequestMapSuccess() throws Exception {
    String url = "http://localhost:8080/api/v1/user";
    Map<String, String> params = new HashMap<>();
    params.put("key", "value");
    String responseBody = "{\"success\":true}";

    unirestMockedStatic.when(() -> Unirest.post(anyString())).thenReturn(httpRequestWithBodyMock);
    when(httpRequestWithBodyMock.headers(anyMap())).thenReturn(httpRequestWithBodyMock);
    when(httpRequestWithBodyMock.body(anyMap())).thenReturn(requestBodyEntityMock);
    when(requestBodyEntityMock.asString()).thenReturn(httpResponseMock);
    when(httpResponseMock.getBody()).thenReturn(responseBody);

    String result = HttpUtil.sendPostRequest(url, params, getHeaders());
    assertEquals(responseBody, result);
  }

  @Test
  public void testSendPostRequestStringSuccess() throws Exception {
    String url = "http://localhost:8080/api/v1/user";
    String params = "{\"key\":\"value\"}";
    String responseBody = "{\"success\":true}";

    unirestMockedStatic.when(() -> Unirest.post(anyString())).thenReturn(httpRequestWithBodyMock);
    when(httpRequestWithBodyMock.headers(anyMap())).thenReturn(httpRequestWithBodyMock);
    when(httpRequestWithBodyMock.body(anyString())).thenReturn(requestBodyEntityMock);
    when(requestBodyEntityMock.asString()).thenReturn(httpResponseMock);
    when(httpResponseMock.getBody()).thenReturn(responseBody);

    String result = HttpUtil.sendPostRequest(url, params, getHeaders());
    assertEquals(responseBody, result);
  }

  @Test
  public void testDoPostRequestSuccess() throws IOException, UnirestException {
    String url = "http://localhost:8080/api/v1/user";
    String params = "{\"key\":\"value\"}";
    String responseBody = "{\"success\":true}";

    unirestMockedStatic.when(() -> Unirest.post(anyString())).thenReturn(httpRequestWithBodyMock);
    when(httpRequestWithBodyMock.headers(anyMap())).thenReturn(httpRequestWithBodyMock);
    when(httpRequestWithBodyMock.body(anyString())).thenReturn(requestBodyEntityMock);
    when(requestBodyEntityMock.asString()).thenReturn(httpResponseMock);
    when(httpResponseMock.getBody()).thenReturn(responseBody);
    when(httpResponseMock.getStatus()).thenReturn(200);

    HttpUtilResponse response = HttpUtil.doPostRequest(url, params, getHeaders());
    assertNotNull(response);
    assertEquals(200, response.getStatusCode());
    assertEquals(responseBody, response.getBody());
  }

  @Test
  public void testDoPostRequestException() throws IOException, UnirestException {
    String url = "http://localhost:8080/api/v1/user";
    String params = "{\"key\":\"value\"}";

    unirestMockedStatic.when(() -> Unirest.post(anyString())).thenThrow(new RuntimeException("Connection Error"));

    HttpUtilResponse response = HttpUtil.doPostRequest(url, params, getHeaders());
    assertNotNull(response);
    assertEquals(0, response.getStatusCode()); // Default int value
    assertEquals(null, response.getBody());
  }

  @Test
  public void testSendPatchRequestSuccess() throws UnirestException {
    String url = "http://localhost:8080/api/v1/user";
    String params = "{\"key\":\"updated\"}";

    unirestMockedStatic.when(() -> Unirest.patch(anyString())).thenReturn(httpRequestWithBodyMock);
    when(httpRequestWithBodyMock.headers(anyMap())).thenReturn(httpRequestWithBodyMock);
    when(httpRequestWithBodyMock.body(anyString())).thenReturn(requestBodyEntityMock);
    when(requestBodyEntityMock.asString()).thenReturn(httpResponseMock);
    when(httpResponseMock.getStatus()).thenReturn(ResponseCode.OK.getResponseCode());

    String result = HttpUtil.sendPatchRequest(url, params, getHeaders());
    assertEquals(ResponseCode.success.getErrorCode(), result);
  }

  @Test
  public void testSendPatchRequestFailure() throws UnirestException {
    String url = "http://localhost:8080/api/v1/user";
    String params = "{\"key\":\"updated\"}";

    unirestMockedStatic.when(() -> Unirest.patch(anyString())).thenReturn(httpRequestWithBodyMock);
    when(httpRequestWithBodyMock.headers(anyMap())).thenReturn(httpRequestWithBodyMock);
    when(httpRequestWithBodyMock.body(anyString())).thenReturn(requestBodyEntityMock);
    when(requestBodyEntityMock.asString()).thenReturn(httpResponseMock);
    when(httpResponseMock.getStatus()).thenReturn(500);

    String result = HttpUtil.sendPatchRequest(url, params, getHeaders());
    assertEquals("Failure", result);
  }

  @Test
  public void testSendPatchRequestException() throws UnirestException {
    String url = "http://localhost:8080/api/v1/user";
    String params = "{\"key\":\"updated\"}";

    unirestMockedStatic.when(() -> Unirest.patch(anyString())).thenThrow(new RuntimeException("Error"));

    String result = HttpUtil.sendPatchRequest(url, params, getHeaders());
    assertEquals("Failure", result);
  }

  @Test
  public void testGetHeader() throws Exception {
    Map<String, String> input = new HashMap<>();
    input.put("key", "value");

    Map<String, String> headers = HttpUtil.getHeader(input);
    assertTrue(headers.containsKey("Content-Type"));
    assertEquals("application/json", headers.get("Content-Type"));
    assertTrue(headers.containsKey("key"));
    assertEquals("value", headers.get("key"));
  }

  @Test
  public void testGetHeaderNull() throws Exception {
      Map<String, String> headers = HttpUtil.getHeader(null);
      assertTrue(headers.containsKey("Content-Type"));
      assertEquals(1, headers.size());
  }
}