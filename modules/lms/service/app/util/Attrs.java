package util;

/**
 * DC-02: DEPRECATED - Use org.sunbird.request.Attrs instead
 * 
 * This class is kept for backward compatibility during migration.
 * All new code should import from: org.sunbird.request.Attrs
 * 
 * The unified request attribute constants are now maintained in the core module
 * at core/sunbird-platform-common/src/main/java/org/sunbird/request/Attrs.java
 * 
 * Migration instructions:
 * 1. Replace: import util.Attrs;
 *    With: import org.sunbird.request.Attrs;
 * 2. All constant names are the same (standardized to underscore convention)
 * 3. No logic changes needed, just update imports
 * 
 * Note on naming: This module previously used REQUEST_ID; core uses X_REQUEST_ID
 * for HTTP header consistency. Both are deprecated aliases for the same header.
 */
@Deprecated(since = "1.0", forRemoval = true)
public class Attrs {
  // This class is deprecated. Import from org.sunbird.request.Attrs instead.
  private Attrs() {
    throw new AssertionError("Use org.sunbird.request.Attrs instead");
  }
}
