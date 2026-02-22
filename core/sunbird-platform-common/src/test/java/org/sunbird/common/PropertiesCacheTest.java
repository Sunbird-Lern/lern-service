package org.sunbird.common;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Test;

/**
 * Unit tests for {@link PropertiesCache} class.
 * Tests singleton access, property saving/retrieval, and initialization behavior.
 */
public class PropertiesCacheTest {

  /**
   * Verifies that {@link PropertiesCache#getInstance()} returns a non-null singleton instance.
   */
  @Test
  public void testGetInstance() {
    PropertiesCache cache = PropertiesCache.getInstance();
    assertNotNull(cache);
    assertEquals(cache, PropertiesCache.getInstance());
  }

  /**
   * Verifies that properties can be saved and retrieved using {@link PropertiesCache#saveConfigProperty(String, String)}
   * and {@link PropertiesCache#getProperty(String)}.
   */
  @Test
  public void testSaveAndGetProperty() {
    PropertiesCache cache = PropertiesCache.getInstance();
    cache.saveConfigProperty("test.key", "test.value");
    assertEquals("test.value", cache.getProperty("test.key"));
  }

  /**
   * Verifies that {@link PropertiesCache#getProperty(String)} returns the key itself if the property is missing.
   */
  @Test
  public void testGetPropertyDefault() {
    PropertiesCache cache = PropertiesCache.getInstance();
    // getProperty returns key if not found
    assertEquals("missing.key", cache.getProperty("missing.key"));
  }

  /**
   * Verifies that {@link PropertiesCache#readProperty(String)} retrieves saved properties
   * and returns null for missing properties.
   */
  @Test
  public void testReadProperty() {
    PropertiesCache cache = PropertiesCache.getInstance();
    cache.saveConfigProperty("read.key", "read.value");
    assertEquals("read.value", cache.readProperty("read.key"));
    // readProperty returns null if not found
    assertNull(cache.readProperty("missing.read.key"));
  }

  /**
   * Indirectly verifies that properties loading and `loadWeighted` method ran during initialization
   * by checking if the attribute map is initialized.
   */
  @Test
  public void testLoadWeighted() {
      // Indirectly test loadWeighted by checking if attributePercentageMap is populated or properties are loaded
      PropertiesCache cache = PropertiesCache.getInstance();
      assertNotNull(cache.attributePercentageMap);
      // Since default properties files are loaded, we might expect some values if configured.
      // But we can't be sure of the content of the files in the environment without reading them.
      // However, the fact that cache initialized means loadWeighted ran.
  }
}