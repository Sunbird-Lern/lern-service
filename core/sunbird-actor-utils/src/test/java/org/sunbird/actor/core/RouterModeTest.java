package org.sunbird.actor.core;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Test suite for RouterMode enum.
 * Covers: enum values, names, and valueOf
 */
public class RouterModeTest {

  /**
   * Test that RouterMode enum has exactly three values.
   */
  @Test
  public void testRouterModeValuesCount() {
    RouterMode[] values = RouterMode.values();
    assertEquals("RouterMode should have 3 values", 3, values.length);
  }

  /**
   * Test RouterMode.OFF enum constant.
   */
  @Test
  public void testRouterModeOff() {
    RouterMode mode = RouterMode.OFF;
    assertNotNull(mode);
    assertEquals("OFF", mode.name());
  }

  /**
   * Test RouterMode.LOCAL enum constant.
   */
  @Test
  public void testRouterModeLocal() {
    RouterMode mode = RouterMode.LOCAL;
    assertNotNull(mode);
    assertEquals("LOCAL", mode.name());
  }

  /**
   * Test RouterMode.REMOTE enum constant.
   */
  @Test
  public void testRouterModeRemote() {
    RouterMode mode = RouterMode.REMOTE;
    assertNotNull(mode);
    assertEquals("REMOTE", mode.name());
  }

  /**
   * Test valueOf method for RouterMode enum.
   */
  @Test
  public void testRouterModeValueOf() {
    assertEquals("valueOf(\"LOCAL\") should return LOCAL", RouterMode.LOCAL, RouterMode.valueOf("LOCAL"));
    assertEquals("valueOf(\"OFF\") should return OFF", RouterMode.OFF, RouterMode.valueOf("OFF"));
    assertEquals("valueOf(\"REMOTE\") should return REMOTE", RouterMode.REMOTE, RouterMode.valueOf("REMOTE"));
  }
}
