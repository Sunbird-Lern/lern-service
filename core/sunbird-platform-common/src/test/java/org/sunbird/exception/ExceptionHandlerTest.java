package org.sunbird.exception;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.junit.Test;
import org.sunbird.message.IResponseMessage;
import org.sunbird.message.ResponseCode;
import org.sunbird.request.Request;

public class ExceptionHandlerTest {

  @Test
  public void testHandleBaseException() {
    BaseException original = new BaseException("CODE", "Message", 400);
    try {
      ExceptionHandler.handleExceptions(new Request(), original);
      fail("Should have thrown BaseException");
    } catch (BaseException e) {
      assertEquals("CODE", e.getCode());
      assertEquals("Message", e.getMessage());
      assertEquals(400, e.getResponseCode());
    }
  }

  @Test
  public void testHandleGenericException() {
    Exception original = new Exception("Generic Error");
    try {
      ExceptionHandler.handleExceptions(new Request(), original);
      fail("Should have thrown BaseException");
    } catch (BaseException e) {
      // Expecting SERVER_ERROR
      assertEquals(IResponseMessage.SERVER_ERROR, e.getCode());
      assertEquals(IResponseMessage.SERVER_ERROR, e.getMessage());
      assertEquals(ResponseCode.SERVER_ERROR.getCode(), e.getResponseCode());
    }
  }
}