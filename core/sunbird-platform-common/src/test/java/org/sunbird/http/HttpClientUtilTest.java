package org.sunbird.http;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.apache.http.HttpEntity;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPatch;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.sunbird.request.RequestContext;

public class HttpClientUtilTest {

  private MockedStatic<HttpClients> httpClientsMockedStatic;
  private MockedStatic<EntityUtils> entityUtilsMockedStatic;
  private CloseableHttpClient httpClientMock;
  private CloseableHttpResponse httpResponseMock;
  private StatusLine statusLineMock;
  private HttpEntity httpEntityMock;
  private HttpClientBuilder httpClientBuilderMock;

  @Before
  public void setUp() throws Exception {
    resetSingleton();

    httpClientsMockedStatic = Mockito.mockStatic(HttpClients.class);
    entityUtilsMockedStatic = Mockito.mockStatic(EntityUtils.class);

    httpClientMock = mock(CloseableHttpClient.class);
    httpResponseMock = mock(CloseableHttpResponse.class);
    statusLineMock = mock(StatusLine.class);
    httpEntityMock = mock(HttpEntity.class);
    httpClientBuilderMock = mock(HttpClientBuilder.class);

    httpClientsMockedStatic.when(HttpClients::custom).thenReturn(httpClientBuilderMock);
    when(httpClientBuilderMock.setConnectionManager(any())).thenReturn(httpClientBuilderMock);
    when(httpClientBuilderMock.useSystemProperties()).thenReturn(httpClientBuilderMock);
    when(httpClientBuilderMock.setKeepAliveStrategy(any())).thenReturn(httpClientBuilderMock);
    when(httpClientBuilderMock.build()).thenReturn(httpClientMock);
  }

  @After
  public void tearDown() throws Exception {
    httpClientsMockedStatic.close();
    entityUtilsMockedStatic.close();
    resetSingleton();
  }

  private void resetSingleton() throws Exception {
    Field instance = HttpClientUtil.class.getDeclaredField("httpClientUtil");
    instance.setAccessible(true);
    instance.set(null, null);

    Field client = HttpClientUtil.class.getDeclaredField("httpclient");
    client.setAccessible(true);
    client.set(null, null);
  }

  private Map<String, String> getHeaders() {
    Map<String, String> headers = new HashMap<>();
    headers.put("Content-Type", "application/json");
    headers.put("Authorization", "Bearer token");
    return headers;
  }

  private RequestContext getContext() {
    RequestContext context = new RequestContext();
    context.setReqId("req-id");
    return context;
  }

  @Test
  public void testGetSuccess() throws IOException {
    String url = "http://localhost:8080/api/v1/user";
    String responseBody = "{\"id\":\"123\", \"name\":\"test\"}";

    when(httpClientMock.execute(any(HttpGet.class))).thenReturn(httpResponseMock);
    when(httpResponseMock.getStatusLine()).thenReturn(statusLineMock);
    when(statusLineMock.getStatusCode()).thenReturn(200);
    when(httpResponseMock.getEntity()).thenReturn(httpEntityMock);
    entityUtilsMockedStatic.when(() -> EntityUtils.toByteArray(httpEntityMock)).thenReturn(responseBody.getBytes(StandardCharsets.UTF_8));

    // Trigger initialization
    HttpClientUtil.getInstance();

    String result = HttpClientUtil.get(url, getHeaders(), getContext());
    assertEquals(responseBody, result);
  }

  @Test
  public void testGetFailure() throws IOException {
    String url = "http://localhost:8080/api/v1/user";
    String errorBody = "{\"error\":\"Not Found\"}";

    when(httpClientMock.execute(any(HttpGet.class))).thenReturn(httpResponseMock);
    when(httpResponseMock.getStatusLine()).thenReturn(statusLineMock);
    when(statusLineMock.getStatusCode()).thenReturn(404);
    when(statusLineMock.getReasonPhrase()).thenReturn("Not Found");
    when(httpResponseMock.getEntity()).thenReturn(httpEntityMock);
    entityUtilsMockedStatic.when(() -> EntityUtils.toByteArray(httpEntityMock)).thenReturn(errorBody.getBytes(StandardCharsets.UTF_8));

    HttpClientUtil.getInstance();
    String result = HttpClientUtil.get(url, getHeaders(), getContext());
    assertEquals("", result);
  }

  @Test
  public void testGetException() throws IOException {
    String url = "http://localhost:8080/api/v1/user";

    when(httpClientMock.execute(any(HttpGet.class))).thenThrow(new IOException("Connection refused"));

    HttpClientUtil.getInstance();
    String result = HttpClientUtil.get(url, getHeaders(), getContext());
    assertEquals("", result);
  }

  @Test
  public void testPostSuccess() throws IOException {
    String url = "http://localhost:8080/api/v1/user";
    String requestBody = "{\"name\":\"test\"}";
    String responseBody = "{\"id\":\"123\", \"name\":\"test\"}";

    when(httpClientMock.execute(any(HttpPost.class))).thenReturn(httpResponseMock);
    when(httpResponseMock.getStatusLine()).thenReturn(statusLineMock);
    when(statusLineMock.getStatusCode()).thenReturn(201);
    when(httpResponseMock.getEntity()).thenReturn(httpEntityMock);
    entityUtilsMockedStatic.when(() -> EntityUtils.toByteArray(httpEntityMock)).thenReturn(responseBody.getBytes(StandardCharsets.UTF_8));

    HttpClientUtil.getInstance();
    String result = HttpClientUtil.post(url, requestBody, getHeaders(), getContext());
    assertEquals(responseBody, result);
  }

  @Test
  public void testPostFailure() throws IOException {
    String url = "http://localhost:8080/api/v1/user";
    String requestBody = "{\"name\":\"test\"}";

    when(httpClientMock.execute(any(HttpPost.class))).thenReturn(httpResponseMock);
    when(httpResponseMock.getStatusLine()).thenReturn(statusLineMock);
    when(statusLineMock.getStatusCode()).thenReturn(400); // Failure
    when(httpResponseMock.getEntity()).thenReturn(httpEntityMock);
    // Even if entity is returned, it should return empty string on failure in this util
    entityUtilsMockedStatic.when(() -> EntityUtils.toByteArray(httpEntityMock)).thenReturn("Error".getBytes(StandardCharsets.UTF_8));

    HttpClientUtil.getInstance();
    String result = HttpClientUtil.post(url, requestBody, getHeaders(), getContext());
    assertEquals("", result);
  }

  @Test
  public void testPostFormDataSuccess() throws IOException {
    String url = "http://localhost:8080/api/v1/user";
    Map<String, String> params = new HashMap<>();
    params.put("key", "value");
    String responseBody = "{\"success\":true}";

    when(httpClientMock.execute(any(HttpPost.class))).thenReturn(httpResponseMock);
    when(httpResponseMock.getStatusLine()).thenReturn(statusLineMock);
    when(statusLineMock.getStatusCode()).thenReturn(200);
    when(httpResponseMock.getEntity()).thenReturn(httpEntityMock);
    entityUtilsMockedStatic.when(() -> EntityUtils.toByteArray(httpEntityMock)).thenReturn(responseBody.getBytes(StandardCharsets.UTF_8));

    HttpClientUtil.getInstance();
    String result = HttpClientUtil.postFormData(url, params, getHeaders(), getContext());
    assertEquals(responseBody, result);
  }

  @Test
  public void testPostFormDataException() throws IOException {
      String url = "http://localhost:8080/api/v1/user";
      Map<String, String> params = new HashMap<>();
      params.put("key", "value");

      when(httpClientMock.execute(any(HttpPost.class))).thenThrow(new RuntimeException("Error"));

      HttpClientUtil.getInstance();
      String result = HttpClientUtil.postFormData(url, params, getHeaders(), getContext());
      assertEquals("", result);
  }

  @Test
  public void testPatchSuccess() throws IOException {
    String url = "http://localhost:8080/api/v1/user/123";
    String requestBody = "{\"name\":\"updated\"}";
    String responseBody = "{\"id\":\"123\", \"name\":\"updated\"}";

    when(httpClientMock.execute(any(HttpPatch.class))).thenReturn(httpResponseMock);
    when(httpResponseMock.getStatusLine()).thenReturn(statusLineMock);
    when(statusLineMock.getStatusCode()).thenReturn(200);
    when(httpResponseMock.getEntity()).thenReturn(httpEntityMock);
    entityUtilsMockedStatic.when(() -> EntityUtils.toByteArray(httpEntityMock)).thenReturn(responseBody.getBytes(StandardCharsets.UTF_8));

    HttpClientUtil.getInstance();
    String result = HttpClientUtil.patch(url, requestBody, getHeaders(), getContext());
    assertEquals(responseBody, result);
  }

  @Test
  public void testPatchFailure() throws IOException {
      String url = "http://localhost:8080/api/v1/user/123";
      String requestBody = "{\"name\":\"updated\"}";

      when(httpClientMock.execute(any(HttpPatch.class))).thenReturn(httpResponseMock);
      when(httpResponseMock.getStatusLine()).thenReturn(statusLineMock);
      when(statusLineMock.getStatusCode()).thenReturn(500);
      when(httpResponseMock.getEntity()).thenReturn(httpEntityMock);
      entityUtilsMockedStatic.when(() -> EntityUtils.toByteArray(httpEntityMock)).thenReturn("Server Error".getBytes(StandardCharsets.UTF_8));

      HttpClientUtil.getInstance();
      String result = HttpClientUtil.patch(url, requestBody, getHeaders(), getContext());
      assertEquals("", result);
  }

  @Test
  public void testDeleteSuccess() throws IOException {
    String url = "http://localhost:8080/api/v1/user/123";
    String responseBody = "{\"success\":true}";

    when(httpClientMock.execute(any(HttpDelete.class))).thenReturn(httpResponseMock);
    when(httpResponseMock.getStatusLine()).thenReturn(statusLineMock);
    when(statusLineMock.getStatusCode()).thenReturn(200);
    when(httpResponseMock.getEntity()).thenReturn(httpEntityMock);
    entityUtilsMockedStatic.when(() -> EntityUtils.toByteArray(httpEntityMock)).thenReturn(responseBody.getBytes(StandardCharsets.UTF_8));

    HttpClientUtil.getInstance();
    String result = HttpClientUtil.delete(url, getHeaders(), getContext());
    assertEquals(responseBody, result);
  }

  @Test
  public void testDeleteException() throws IOException {
    String url = "http://localhost:8080/api/v1/user/123";

    when(httpClientMock.execute(any(HttpDelete.class))).thenThrow(new IOException("Fail"));

    HttpClientUtil.getInstance();
    String result = HttpClientUtil.delete(url, getHeaders(), getContext());
    assertEquals("", result);
  }
}