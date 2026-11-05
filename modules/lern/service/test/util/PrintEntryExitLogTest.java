package util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.sunbird.keys.JsonKey;
import org.sunbird.request.Request;
import org.sunbird.response.Response;
import org.sunbird.response.ResponseCode;
import org.sunbird.response.ResponseParams;

public class PrintEntryExitLogTest {

  private static Request request() {
    Request request = new Request();
    Map<String, Object> context = new HashMap<>();
    context.put(JsonKey.URL, "/v1/user/courses/list/user-1");
    request.setContext(context);
    return request;
  }

  private static Response response() {
    Response response = new Response();
    response.put(JsonKey.COURSES, "user data that must not reach the log by default");
    ResponseParams params = new ResponseParams();
    params.setStatus("SUCCESSFUL");
    response.setParams(params);
    response.setResponseCode(ResponseCode.OK);
    return response;
  }

  @Test
  public void exitLogParamsWithoutResponseBodyKeepOnlyParams() {
    List<Map<String, Object>> params =
        PrintEntryExitLog.getExitLogParams(request(), response(), false);
    assertEquals(1, params.size());
    assertFalse(params.get(0).containsKey(JsonKey.COURSES));
    assertEquals(ResponseCode.OK.getResponseCode(), params.get(0).get(JsonKey.RESPONSE_CODE));
  }

  @Test
  public void exitLogParamsWithResponseBodyIncludeResult() {
    List<Map<String, Object>> params =
        PrintEntryExitLog.getExitLogParams(request(), response(), true);
    assertEquals(2, params.size());
    assertTrue(params.get(0).containsKey(JsonKey.COURSES));
    assertEquals(ResponseCode.OK.getResponseCode(), params.get(1).get(JsonKey.RESPONSE_CODE));
  }

  @Test
  public void exitLogParamsForNullResponseAreEmpty() {
    assertTrue(PrintEntryExitLog.getExitLogParams(request(), null, false).isEmpty());
  }
}
