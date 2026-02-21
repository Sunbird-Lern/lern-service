package utils.module;

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
 * 1. Replace: import utils.module.Attrs;
 *    With: import org.sunbird.request.Attrs;
 * 2. Note: This module used USERID constant; core standardizes to USER_ID
 *    Update all occurrences: Attrs.USERID -> Attrs.USER_ID
 * 3. All other constant names are identical
 */
@Deprecated(since = "1.0", forRemoval = true)
public class Attrs {
  // This class is deprecated. Import from org.sunbird.request.Attrs instead.
  private Attrs() {
    throw new AssertionError("Use org.sunbird.request.Attrs instead");
  }
}
