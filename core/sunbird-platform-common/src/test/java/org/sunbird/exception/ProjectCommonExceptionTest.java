package org.sunbird.exception;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import org.junit.Test;
import org.sunbird.response.ResponseCode;
import org.sunbird.keys.JsonKey;

public class ProjectCommonExceptionTest {

  @Test
  public void testConstructorWithResponseCode() {
    ResponseCode code = ResponseCode.CLIENT_ERROR;
    String message = "Client Error Occurred";
    int responseCode = 400;

    ProjectCommonException exception = new ProjectCommonException(code, message, responseCode);

    assertEquals(code.getErrorCode(), exception.getErrorCode());
    assertEquals(message, exception.getMessage());
    assertEquals(responseCode, exception.getErrorResponseCode());
    assertEquals(code, exception.getResponseCodeEnum());
  }

  @Test
  public void testConstructorWithStringCode() {
    String errorCode = "ERR_CUSTOM";
    String message = "Custom Error";
    int responseCode = 418;

    ProjectCommonException exception = new ProjectCommonException(errorCode, message, responseCode);

    assertEquals(errorCode, exception.getErrorCode());
    assertEquals(message, exception.getMessage());
    assertEquals(responseCode, exception.getErrorResponseCode());
    assertNull(exception.getResponseCodeEnum());
  }

  @Test
  public void testConstructorWithPlaceholder() {
    ResponseCode code = ResponseCode.CLIENT_ERROR;
    String messagePattern = "Error in {0}";
    int responseCode = 400;
    String placeholder = "module";

    ProjectCommonException exception = new ProjectCommonException(code, messagePattern, responseCode, placeholder);

    assertEquals(code.getErrorCode(), exception.getErrorCode());
    assertEquals("Error in module", exception.getMessage());
    assertEquals(responseCode, exception.getErrorResponseCode());
    assertEquals(code, exception.getResponseCodeEnum());
  }

  @Test
  public void testWrapperConstructor() {
    ProjectCommonException original = new ProjectCommonException(ResponseCode.CLIENT_ERROR, "Original Error", 400);
    String actorOperation = "create";

    ProjectCommonException wrapper = new ProjectCommonException(original, actorOperation);

    String expectedErrorCode = JsonKey.USER_ORG_SERVICE_PREFIX + actorOperation + original.getErrorCode();
    assertEquals(expectedErrorCode, wrapper.getErrorCode());
    assertEquals(original.getMessage(), wrapper.getMessage());
    assertEquals(original.getErrorResponseCode(), wrapper.getErrorResponseCode());
    assertEquals(original.getResponseCodeEnum(), wrapper.getResponseCodeEnum());
  }

  @Test
  public void testSetters() {
    ProjectCommonException exception = new ProjectCommonException("CODE", "Message", 500);

    exception.setCode("NEW_CODE");
    assertEquals("NEW_CODE", exception.getErrorCode());
    assertEquals("NEW_CODE", exception.getCode()); // Alias

    exception.setMessage("New Message");
    assertEquals("New Message", exception.getMessage());
    assertEquals("New Message", exception.getErrorMessage()); // Alias

    exception.setResponseCode(404);
    assertEquals(404, exception.getErrorResponseCode());

    exception.setErrorResponseCode(403);
    assertEquals(403, exception.getErrorResponseCode());

    exception.setResponseCodeEnum(ResponseCode.OK);
    assertEquals(ResponseCode.OK, exception.getResponseCodeEnum());
    assertEquals(ResponseCode.OK, exception.getResponseCode()); // Alias
  }

  @Test
  public void testThrowClientErrorException() {
    try {
      ProjectCommonException.throwClientErrorException(ResponseCode.CLIENT_ERROR, "Custom Message");
      fail("Should have thrown exception");
    } catch (ProjectCommonException e) {
      assertEquals(ResponseCode.CLIENT_ERROR.getErrorCode(), e.getErrorCode());
      assertEquals("Custom Message", e.getMessage());
      assertEquals(ResponseCode.CLIENT_ERROR.getResponseCode(), e.getErrorResponseCode());
    }
  }

  @Test
  public void testThrowClientErrorExceptionDefaultMessage() {
      try {
        ProjectCommonException.throwClientErrorException(ResponseCode.CLIENT_ERROR);
        fail("Should have thrown exception");
      } catch (ProjectCommonException e) {
        // ResponseCode.CLIENT_ERROR has no default message
        assertEquals(ResponseCode.CLIENT_ERROR.getErrorCode(), e.getErrorCode());
        assertNull(e.getMessage());
        assertEquals(ResponseCode.CLIENT_ERROR.getResponseCode(), e.getErrorResponseCode());
      }
    }

  @Test
  public void testThrowResourceNotFoundException() {
    try {
      ProjectCommonException.throwResourceNotFoundException();
      fail("Should have thrown exception");
    } catch (ProjectCommonException e) {
      assertEquals(ResponseCode.resourceNotFound.getErrorCode(), e.getErrorCode());
      assertEquals(ResponseCode.RESOURCE_NOT_FOUND.getResponseCode(), e.getErrorResponseCode());
    }
  }

  @Test
  public void testThrowResourceNotFoundExceptionWithCustomMessage() {
    try {
      ProjectCommonException.throwResourceNotFoundException(ResponseCode.resourceNotFound, "Not found custom");
      fail("Should have thrown exception");
    } catch (ProjectCommonException e) {
      assertEquals(ResponseCode.resourceNotFound.getErrorCode(), e.getErrorCode());
      assertEquals("Not found custom", e.getMessage());
      assertEquals(ResponseCode.RESOURCE_NOT_FOUND.getResponseCode(), e.getErrorResponseCode());
    }
  }

  @Test
  public void testThrowServerErrorException() {
    try {
      ProjectCommonException.throwServerErrorException(ResponseCode.SERVER_ERROR, "Server Fail");
      fail("Should have thrown exception");
    } catch (ProjectCommonException e) {
      assertEquals(ResponseCode.SERVER_ERROR.getErrorCode(), e.getErrorCode());
      assertEquals("Server Fail", e.getMessage());
      assertEquals(ResponseCode.SERVER_ERROR.getResponseCode(), e.getErrorResponseCode());
    }
  }

  @Test
  public void testThrowServerErrorExceptionDefaultMessage() {
      try {
        ProjectCommonException.throwServerErrorException(ResponseCode.SERVER_ERROR);
        fail("Should have thrown exception");
      } catch (ProjectCommonException e) {
        // ResponseCode.SERVER_ERROR has no default message
        assertEquals(ResponseCode.SERVER_ERROR.getErrorCode(), e.getErrorCode());
        assertNull(e.getMessage());
        assertEquals(ResponseCode.SERVER_ERROR.getResponseCode(), e.getErrorResponseCode());
      }
    }

  @Test
  public void testThrowUnauthorizedErrorException() {
    try {
      ProjectCommonException.throwUnauthorizedErrorException();
      fail("Should have thrown exception");
    } catch (ProjectCommonException e) {
      assertEquals(ResponseCode.unAuthorized.getErrorCode(), e.getErrorCode());
      assertEquals(ResponseCode.UNAUTHORIZED.getResponseCode(), e.getErrorResponseCode());
    }
  }

  @Test
  public void testToString() {
      ProjectCommonException exception = new ProjectCommonException("ERR_CODE", "Error Message", 400);
      String toString = exception.toString();
      assertEquals("ERR_CODE: Error Message", toString);
  }
}