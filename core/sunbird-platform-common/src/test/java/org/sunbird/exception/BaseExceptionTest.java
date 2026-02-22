package org.sunbird.exception;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class BaseExceptionTest {

  @Test
  public void testConstructorWithCodeMessageResponse() {
    BaseException ex = new BaseException("CODE", "Message", 400);
    assertEquals("CODE", ex.getCode());
    assertEquals("Message", ex.getMessage());
    assertEquals(400, ex.getResponseCode());
  }

  @Test
  public void testConstructorWithCodeMessage() {
    BaseException ex = new BaseException("CODE", "Message");
    assertEquals("CODE", ex.getCode());
    assertEquals("Message", ex.getMessage());
    assertEquals(0, ex.getResponseCode());
  }

  @Test
  public void testCopyConstructor() {
    BaseException original = new BaseException("CODE", "Message", 400);
    BaseException copy = new BaseException(original);
    assertEquals("CODE", copy.getCode());
    assertEquals("Message", copy.getMessage());
    assertEquals(400, copy.getResponseCode());
  }

  @Test
  public void testSetters() {
    BaseException ex = new BaseException("C", "M", 0);
    ex.setCode("NEW");
    ex.setMessage("NEW_M");
    ex.setResponseCode(500);
    assertEquals("NEW", ex.getCode());
    assertEquals("NEW_M", ex.getMessage());
    assertEquals(500, ex.getResponseCode());
  }
}