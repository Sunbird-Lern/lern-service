package org.sunbird.exception;

import static org.junit.Assert.assertEquals;
import org.junit.Test;
import org.sunbird.message.ResponseCode;

public class AuthorizationExceptionTest {

  @Test
  public void testNotAuthorized() {
    ResponseCode code = ResponseCode.unAuthorized;
    AuthorizationException.NotAuthorized ex = new AuthorizationException.NotAuthorized(code);
    assertEquals(code.getErrorCode(), ex.getCode());
    assertEquals(code.getErrorMessage(), ex.getMessage());
    assertEquals(401, ex.getResponseCode());
  }
}