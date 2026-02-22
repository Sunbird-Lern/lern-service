package org.sunbird.exception;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.sunbird.message.ResponseCode;
import org.sunbird.message.IResponseMessage;

public class ValidationExceptionTest {

  @Test
  public void testInvalidRequestData() {
    ValidationException.InvalidRequestData ex = new ValidationException.InvalidRequestData();
    assertEquals(IResponseMessage.INVALID_REQUESTED_DATA, ex.getCode());
    assertEquals(400, ex.getResponseCode());
  }

  @Test
  public void testMandatoryParamMissing() {
    ValidationException.MandatoryParamMissing ex = new ValidationException.MandatoryParamMissing("param1", "parent");
    assertEquals(IResponseMessage.Key.MANDATORY_PARAMETER_MISSING, ex.getCode());
    // Checking against our dummy properties file value: MANDATORY_PARAMETER_MISSING=Mandatory parameter {0} is missing
    assertEquals("Mandatory parameter param1 is missing", ex.getMessage());
    assertEquals(400, ex.getResponseCode());
  }

  @Test
  public void testMandatoryParamMissingWithResponseCode() {
      ResponseCode code = ResponseCode.mandatoryParameterMissing;
      ValidationException.MandatoryParamMissing ex = new ValidationException.MandatoryParamMissing("param1", "parent", code);
      assertEquals(code.getErrorCode(), ex.getCode());
      assertEquals(400, ex.getResponseCode());
  }

  @Test
  public void testParamDataTypeError() {
    ValidationException.ParamDataTypeError ex = new ValidationException.ParamDataTypeError("param1", "string");
    assertEquals(IResponseMessage.INVALID_REQUESTED_DATA, ex.getCode());
    // Current implementation uses the key as the message format without localization or placeholders
    assertEquals(IResponseMessage.DATA_TYPE_ERROR, ex.getMessage());
    assertEquals(400, ex.getResponseCode());
  }

  @Test
  public void testInvalidParamValue() {
    ValidationException.InvalidParamValue ex = new ValidationException.InvalidParamValue("val", "param1");
    assertEquals(IResponseMessage.Key.INVALID_PARAMETER_VALUE, ex.getCode());
    // Checking against our dummy properties file value
    // INVALID_PARAMETER_VALUE=Invalid value {0} for parameter {1}
    assertEquals("Invalid value val for parameter param1", ex.getMessage());
    assertEquals(400, ex.getResponseCode());
  }
}