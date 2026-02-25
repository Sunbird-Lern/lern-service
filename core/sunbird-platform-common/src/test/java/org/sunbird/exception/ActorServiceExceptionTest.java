package org.sunbird.exception;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class ActorServiceExceptionTest {

  @Test
  public void testInvalidOperationName() {
    ActorServiceException.InvalidOperationName ex = new ActorServiceException.InvalidOperationName("CODE", "Msg", 400);
    assertEquals("CODE", ex.getCode());
    assertEquals("Msg", ex.getMessage());
    assertEquals(400, ex.getResponseCode());
  }

  @Test
  public void testInvalidRequestTimeout() {
    ActorServiceException.InvalidRequestTimeout ex = new ActorServiceException.InvalidRequestTimeout("CODE", "Msg", 408);
    assertEquals("CODE", ex.getCode());
    assertEquals("Msg", ex.getMessage());
    assertEquals(408, ex.getResponseCode());
  }

  @Test
  public void testInvalidRequestData() {
    ActorServiceException.InvalidRequestData ex = new ActorServiceException.InvalidRequestData("CODE", "Msg", 400);
    assertEquals("CODE", ex.getCode());
    assertEquals("Msg", ex.getMessage());
    assertEquals(400, ex.getResponseCode());
  }
}