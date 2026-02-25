package util;

import org.sunbird.keys.JsonKey;
import play.libs.typedmap.TypedKey;

/**
 * Attrs contains a set of TypedKey constants used for accessing request attributes
 * in a type-safe manner within the Play Framework application.
 * These keys map to various common data elements extracted from request headers,
 * tokens, or context.
 */
public class Attrs {
    /** Key for the unique identifier of the user making the request. */
    public static final TypedKey<String> USER_ID = TypedKey.<String>create(JsonKey.USER_ID);

    /** Key indicating if the request is authenticated with a master key. */
    public static final TypedKey<String> AUTH_WITH_MASTER_KEY = TypedKey.<String>create(JsonKey.AUTH_WITH_MASTER_KEY);

    /** Key for the unique request identifier used for tracking and logging. */
    public static final TypedKey<String> REQUEST_ID = TypedKey.<String>create(JsonKey.REQUEST_ID);

    /** Key for the request context information. */
    public static final TypedKey<String> CONTEXT = TypedKey.<String>create(JsonKey.CONTEXT);

    /** Key for the user ID on whose behalf the request is made. */
    public static final TypedKey<String> REQUESTED_FOR = TypedKey.<String>create(JsonKey.REQUESTED_FOR);

    /** Key indicating if authentication is required for the request. */
    public static final TypedKey<String> IS_AUTH_REQ = TypedKey.<String>create(JsonKey.IS_AUTH_REQ);

    /** Key for the type of signup (e.g., self, google, etc.). */
    public static final TypedKey<String> SIGNUP_TYPE = TypedKey.<String>create(JsonKey.SIGNUP_TYPE);

    /** Key for the source of the request (e.g., mobile, web, portal). */
    public static final TypedKey<String> REQUEST_SOURCE = TypedKey.<String>create(JsonKey.REQUEST_SOURCE);

    /** Key for the channel (organization/tenant) identifier. */
    public static final TypedKey<String> CHANNEL = TypedKey.<String>create(JsonKey.CHANNEL);

    /** Key for the application identifier. */
    public static final TypedKey<String> APP_ID = TypedKey.<String>create(JsonKey.APP_ID);

    /** Key for the unique device identifier. */
    public static final TypedKey<String> DEVICE_ID = TypedKey.<String>create(JsonKey.DEVICE_ID);

    /** Key for the actor (user/system) identifier in the request. */
    public static final TypedKey<String> ACTOR_ID = TypedKey.<String>create(JsonKey.ACTOR_ID);

    /** Key for the type of actor (e.g., User, System). */
    public static final TypedKey<String> ACTOR_TYPE = TypedKey.<String>create(JsonKey.ACTOR_TYPE);

    /** Key for the authentication token (X-Authenticated-User-Token). */
    public static final TypedKey<String> X_AUTH_TOKEN = TypedKey.<String>create(JsonKey.X_AUTH_TOKEN);

    /** Key for additional logging headers. */
    public static final TypedKey<String> X_LOGGING_HEADERS = TypedKey.<String>create(JsonKey.X_LOGGING_HEADERS);

    /** Key for the ID of the user being managed (for managed user operations). */
    public static final TypedKey<String> MANAGED_FOR = TypedKey.<String>create(JsonKey.MANAGED_FOR);

    /** Key for the external request ID. */
    public static final TypedKey<String> X_REQUEST_ID = TypedKey.<String>create(JsonKey.X_REQUEST_ID);
}
