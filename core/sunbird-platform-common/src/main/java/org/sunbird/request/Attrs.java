package org.sunbird.request;

import play.libs.typedmap.TypedKey;
import org.sunbird.keys.JsonKey;

/**
 * DC-02: Canonical Request Attribute Constants
 * 
 * Consolidates Play Framework TypedKey attributes from across all service modules.
 * This is the single source of truth for request-scoped attributes in Play Framework.
 * 
 * Migration notes:
 * - Consolidated from userorg/controller/app/util/Attrs.java
 * - Consolidated from lms/service/app/util/Attrs.java (note: uses REQUEST_ID vs X_REQUEST_ID inconsistency)
 * - Consolidated from notification/service/app/utils/module/Attrs.java (note: uses USERID vs USER_ID)
 * 
 * All deprecated implementations point here. Services should import from this canonical location.
 */
public class Attrs {
  
  /**
   * ============================================================
   * REQUEST/CONTEXT ATTRIBUTES (DC-02)
   * ============================================================
   */
  
  /** User identifier in the request context */
  public static final TypedKey<String> USER_ID = 
      TypedKey.<String>create(JsonKey.USER_ID);
  
  /** Request context object */
  public static final TypedKey<String> CONTEXT = 
      TypedKey.<String>create(JsonKey.CONTEXT);
  
  /** User ID for whom the request is managed (delegation) */
  public static final TypedKey<String> MANAGED_FOR = 
      TypedKey.<String>create(JsonKey.MANAGED_FOR);
  
  /**
   * Request ID for tracking.
   * Note: Alias inconsistency exists - some services use X_REQUEST_ID, others REQUEST_ID
   * DC-02: Standardized to X_REQUEST_ID for HTTP header consistency
   */
  public static final TypedKey<String> X_REQUEST_ID = 
      TypedKey.<String>create(JsonKey.X_REQUEST_ID);
  
  /** Timestamp when request processing started */
  public static final TypedKey<String> START_TIME = 
      TypedKey.<String>create(JsonKey.START_TIME);
  
  /**
   * REQUEST_ID is an alias for X_REQUEST_ID used in some services.
   * DC-02 Note: This is a backward-compatibility alias. Prefer X_REQUEST_ID.
   * @deprecated Use {@link #X_REQUEST_ID} instead
   */
  @Deprecated
  public static final TypedKey<String> REQUEST_ID = 
      TypedKey.<String>create(JsonKey.REQUEST_ID);

  /**
   * ============================================================
   * AUTHENTICATION ATTRIBUTES (DC-02)
   * ============================================================
   */
  
  /** Whether authentication with master key was used */
  public static final TypedKey<String> AUTH_WITH_MASTER_KEY = 
      TypedKey.<String>create(JsonKey.AUTH_WITH_MASTER_KEY);
  
  /** Whether authentication is required for this request */
  public static final TypedKey<String> IS_AUTH_REQ = 
      TypedKey.<String>create(JsonKey.IS_AUTH_REQ);
  
  /** X-Auth-Token from request header */
  public static final TypedKey<String> X_AUTH_TOKEN = 
      TypedKey.<String>create(JsonKey.X_AUTH_TOKEN);
  
  /**
   * ============================================================
   * REQUEST METADATA ATTRIBUTES (DC-02)
   * ============================================================
   */
  
  /** Actor ID initiating the request */
  public static final TypedKey<String> ACTOR_ID = 
      TypedKey.<String>create(JsonKey.ACTOR_ID);
  
  /** Type of actor (e.g., SYSTEM, USER) */
  public static final TypedKey<String> ACTOR_TYPE = 
      TypedKey.<String>create(JsonKey.ACTOR_TYPE);
  
  /** User requested for (for delegation scenarios) */
  public static final TypedKey<String> REQUESTED_FOR = 
      TypedKey.<String>create(JsonKey.REQUESTED_FOR);
  
  /** Application ID from request */
  public static final TypedKey<String> APP_ID = 
      TypedKey.<String>create(JsonKey.APP_ID);
  
  /** Device ID from request */
  public static final TypedKey<String> DEVICE_ID = 
      TypedKey.<String>create(JsonKey.DEVICE_ID);
  
  /**
   * ============================================================
   * CLIENT INFORMATION ATTRIBUTES (DC-02)
   * ============================================================
   */
  
  /** Channel identifier from request */
  public static final TypedKey<String> CHANNEL = 
      TypedKey.<String>create(JsonKey.CHANNEL);
  
  /** Request source (e.g., web, mobile) */
  public static final TypedKey<String> REQUEST_SOURCE = 
      TypedKey.<String>create(JsonKey.REQUEST_SOURCE);
  
  /** Signup type (e.g., SELF_SIGNUP, SYSTEM_SIGNUP) */
  public static final TypedKey<String> SIGNUP_TYPE = 
      TypedKey.<String>create(JsonKey.SIGNUP_TYPE);
  
  /** Logging headers for distributed tracing */
  public static final TypedKey<String> X_LOGGING_HEADERS = 
      TypedKey.<String>create(JsonKey.X_LOGGING_HEADERS);
  
  // Private constructor to prevent instantiation
  private Attrs() {
    throw new AssertionError("Cannot instantiate utility class");
  }
}
