package org.sunbird.response;

import java.util.Map;
import org.sunbird.response.ResponseParams;

/**
 * DC-05: Fluent builder for Response objects.
 * 
 * Eliminates the repeated 5-line pattern:
 *   new Response() + setVer() + setId() + setTs() + setParams()
 * found 419+ times across the codebase.
 * 
 * Example usage:
 *   // Before (verbose):
 *   Response response = new Response();
 *   response.setVer("v1");
 *   response.setId("response.id");
 *   response.setTs(TimeStamp.now());
 *   response.setParams(params);
 *   response.put("data", value);
 * 
 *   // After (fluent):
 *   Response response = ResponseBuilder.create()
 *       .withVersion("v1")
 *       .withId("response.id")
 *       .withData("data", value)
 *       .build();
 * 
 *   // Or quick static methods:
 *   Response okResponse = ResponseBuilder.ok();
 *   Response errorResponse = ResponseBuilder.error("ERR_001", "Something went wrong");
 */
public class ResponseBuilder {

    private Response response;

    /**
     * Private constructor to enforce fluent API via static factory methods.
     */
    private ResponseBuilder() {
        this.response = new Response();
    }

    /**
     * Creates a new ResponseBuilder instance.
     * Use this for building custom responses with full control over fields.
     * 
     * @return A new ResponseBuilder instance
     */
    public static ResponseBuilder create() {
        return new ResponseBuilder();
    }

    /**
     * Returns a pre-built successful Response with minimal setup.
     * Suitable for simple success responses without additional data.
     * 
     * @return A Response object with default settings
     */
    public static Response ok() {
        return create().build();
    }

    /**
     * Returns a pre-built successful Response with a single data entry.
     * Convenience method for responses with one key-value pair.
     * 
     * @param key The result map key
     * @param value The result map value
     * @return A Response object with the provided data
     */
    public static Response ok(String key, Object value) {
        Response resp = create().build();
        resp.put(key, value);
        return resp;
    }

    /**
     * Returns a pre-built error Response with error code and message.
     * 
     * @param errorCode The error code (e.g., "ERR_001")
     * @param message The user-facing error message
     * @return A Response object with error parameters set
     */
    public static Response error(String errorCode, String message) {
        ResponseBuilder builder = create();
        ResponseParams params = new ResponseParams();
        params.setStatus(ResponseParams.StatusType.FAILED.toString());
        params.setErr(errorCode);
        params.setErrmsg(message);
        builder.response.setParams(params);
        return builder.build();
    }

    /**
     * Sets the API version string.
     * 
     * @param version The API version (e.g., "v1")
     * @return This builder for method chaining
     */
    public ResponseBuilder withVersion(String version) {
        response.setVer(version);
        return this;
    }

    /**
     * Sets the response ID.
     * 
     * @param id The response identifier
     * @return This builder for method chaining
     */
    public ResponseBuilder withId(String id) {
        response.setId(id);
        return this;
    }

    /**
     * Adds a single key-value pair to the result map.
     * 
     * @param key The result map key
     * @param value The result map value
     * @return This builder for method chaining
     */
    public ResponseBuilder withData(String key, Object value) {
        response.put(key, value);
        return this;
    }

    /**
     * Adds multiple key-value pairs from a map to the result map.
     * 
     * @param data A map of entries to add to the response
     * @return This builder for method chaining
     */
    public ResponseBuilder withData(Map<String, Object> data) {
        data.forEach((k, v) -> response.put(k, v));
        return this;
    }

    /**
     * Sets the response parameters (status, error codes, messages, etc.)
     * 
     * @param params The ResponseParams object
     * @return This builder for method chaining
     */
    public ResponseBuilder withParams(ResponseParams params) {
        response.setParams(params);
        return this;
    }

    /**
     * Sets the response code enum.
     * 
     * @param code The ResponseCode enum value
     * @return This builder for method chaining
     */
    public ResponseBuilder withResponseCode(ResponseCode code) {
        response.setResponseCode(code);
        return this;
    }

    /**
     * Builds and returns the Response object.
     * This is the terminal operation of the fluent API.
     * 
     * @return The constructed Response object
     */
    public Response build() {
        return response;
    }
}
