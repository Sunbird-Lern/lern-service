package org.sunbird.actor.core;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Test suite for RouterException.
 * Covers: constructor, message storage, RuntimeException inheritance
 */
public class RouterExceptionTest {

  /**
   * Test that RouterException constructor stores the message.
   */
  @Test
  public void testRouterExceptionConstructorStoresMessage() {
    String message = "Invalid router invocation";
    RouterException exception = new RouterException(message);

    assertEquals("Exception message should match constructor argument", message, exception.getMessage());
  }

  /**
   * Test that RouterException is a RuntimeException.
   */
  @Test
  public void testRouterExceptionIsRuntimeException() {
    RouterException exception = new RouterException("test");
    assertTrue("RouterException should be instance of RuntimeException", exception instanceof RuntimeException);
  }

  /**
   * Test that RouterException can be thrown and caught.
   */
  @Test(expected = RouterException.class)
  public void testRouterExceptionCanBeThrown() {
    throw new RouterException("test exception");
  }
}
