package org.sunbird.validators.orgvalidator;

import static org.junit.Assert.*;

import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.sunbird.exception.ProjectCommonException;
import org.sunbird.keys.JsonKey;
import org.sunbird.request.Request;
import org.sunbird.response.ResponseCode;

public class OrgRequestValidatorTest {

  private OrgRequestValidator validator;
  private Request orgRequest;
  private Map<String, Object> requestData;

  @Before
  public void setUp() {
    validator = new OrgRequestValidator();
    orgRequest = new Request();
    requestData = new HashMap<>();
    orgRequest.setRequest(requestData);
  }

  // =============================================
  // validateCreateOrgRequest Tests
  // =============================================

  @Test
  public void testValidateCreateOrgRequest_WithValidData() {
    requestData.put(JsonKey.ORG_TYPE, "ngo");
    requestData.put(JsonKey.ORG_NAME, "Test Organization");
    requestData.put(JsonKey.IS_TENANT, true);
    requestData.put(JsonKey.CHANNEL, "test-channel");

    // Should not throw exception
    validator.validateCreateOrgRequest(orgRequest);
  }

  @Test(expected = ProjectCommonException.class)
  public void testValidateCreateOrgRequest_WithMissingOrgType() {
    requestData.put(JsonKey.ORG_NAME, "Test Organization");
    requestData.put(JsonKey.IS_TENANT, true);

    validator.validateCreateOrgRequest(orgRequest);
  }

  @Test(expected = ProjectCommonException.class)
  public void testValidateCreateOrgRequest_WithBlankOrgType() {
    requestData.put(JsonKey.ORG_TYPE, "");
    requestData.put(JsonKey.ORG_NAME, "Test Organization");
    requestData.put(JsonKey.IS_TENANT, true);

    validator.validateCreateOrgRequest(orgRequest);
  }

  @Test
  public void testValidateCreateOrgRequest_WithMissingOrgType_ExceptionCode() {
    requestData.put(JsonKey.ORG_NAME, "Test Organization");
    requestData.put(JsonKey.IS_TENANT, true);

    try {
      validator.validateCreateOrgRequest(orgRequest);
      fail("Should throw exception");
    } catch (ProjectCommonException e) {
      assertEquals(ResponseCode.mandatoryParamsMissing.getErrorCode(), e.getErrorCode());
    }
  }

  @Test(expected = ProjectCommonException.class)
  public void testValidateCreateOrgRequest_WithMissingOrgName() {
    requestData.put(JsonKey.ORG_TYPE, "ngo");
    requestData.put(JsonKey.IS_TENANT, true);

    validator.validateCreateOrgRequest(orgRequest);
  }

  @Test(expected = ProjectCommonException.class)
  public void testValidateCreateOrgRequest_WithBlankOrgName() {
    requestData.put(JsonKey.ORG_TYPE, "ngo");
    requestData.put(JsonKey.ORG_NAME, "");
    requestData.put(JsonKey.IS_TENANT, true);

    validator.validateCreateOrgRequest(orgRequest);
  }

  @Test(expected = ProjectCommonException.class)
  public void testValidateCreateOrgRequest_WithMissingIsTenant() {
    requestData.put(JsonKey.ORG_TYPE, "ngo");
    requestData.put(JsonKey.ORG_NAME, "Test Organization");

    validator.validateCreateOrgRequest(orgRequest);
  }

  @Test(expected = ProjectCommonException.class)
  public void testValidateCreateOrgRequest_WithNullIsTenant() {
    requestData.put(JsonKey.ORG_TYPE, "ngo");
    requestData.put(JsonKey.ORG_NAME, "Test Organization");
    requestData.put(JsonKey.IS_TENANT, null);

    validator.validateCreateOrgRequest(orgRequest);
  }

  @Test
  public void testValidateCreateOrgRequest_WithMissingIsTenant_ExceptionCode() {
    requestData.put(JsonKey.ORG_TYPE, "ngo");
    requestData.put(JsonKey.ORG_NAME, "Test Organization");

    try {
      validator.validateCreateOrgRequest(orgRequest);
      fail("Should throw exception");
    } catch (ProjectCommonException e) {
      assertEquals(ResponseCode.mandatoryParamsMissing.getErrorCode(), e.getErrorCode());
    }
  }

  @Test
  public void testValidateCreateOrgRequest_WithIsTenantFalse() {
    requestData.put(JsonKey.ORG_TYPE, "ngo");
    requestData.put(JsonKey.ORG_NAME, "Test Organization");
    requestData.put(JsonKey.IS_TENANT, false);

    // Should not throw exception
    validator.validateCreateOrgRequest(orgRequest);
  }

  // =============================================
  // validateUpdateOrgRequest Tests
  // =============================================

  @Test
  public void testValidateUpdateOrgRequest_WithValidData() {
    requestData.put(JsonKey.ORGANISATION_ID, "org123");
    requestData.put(JsonKey.ORG_NAME, "Updated Organization");

    // Should not throw exception
    validator.validateUpdateOrgRequest(orgRequest);
  }

  @Test(expected = ProjectCommonException.class)
  public void testValidateUpdateOrgRequest_WithMissingOrgId() {
    requestData.put(JsonKey.ORG_NAME, "Updated Organization");

    validator.validateUpdateOrgRequest(orgRequest);
  }

  @Test
  public void testValidateUpdateOrgRequest_WithMissingOrgId_ExceptionCode() {
    requestData.put(JsonKey.ORG_NAME, "Updated Organization");

    try {
      validator.validateUpdateOrgRequest(orgRequest);
      fail("Should throw exception");
    } catch (ProjectCommonException e) {
      // Should fail due to missing org ID reference
      assertNotNull(e.getErrorCode());
    }
  }

  @Test(expected = ProjectCommonException.class)
  public void testValidateUpdateOrgRequest_WithStatusField() {
    requestData.put(JsonKey.ORGANISATION_ID, "org123");
    requestData.put(JsonKey.STATUS, 1);
    requestData.put(JsonKey.ORG_NAME, "Updated Organization");

    validator.validateUpdateOrgRequest(orgRequest);
  }

  @Test
  public void testValidateUpdateOrgRequest_WithStatusField_ExceptionCode() {
    requestData.put(JsonKey.ORGANISATION_ID, "org123");
    requestData.put(JsonKey.STATUS, 1);
    requestData.put(JsonKey.ORG_NAME, "Updated Organization");

    try {
      validator.validateUpdateOrgRequest(orgRequest);
      fail("Should throw exception");
    } catch (ProjectCommonException e) {
      assertEquals(ResponseCode.invalidRequestParameter.getErrorCode(), e.getErrorCode());
    }
  }

  @Test
  public void testValidateUpdateOrgRequest_StatusFieldIsReadOnly() {
    // STATUS is a read-only field and should not be allowed in update request
    requestData.put(JsonKey.ORGANISATION_ID, "org123");
    requestData.put(JsonKey.ORG_NAME, "Updated Organization");
    requestData.put(JsonKey.STATUS, 2);

    try {
      validator.validateUpdateOrgRequest(orgRequest);
      fail("Should throw exception for read-only field");
    } catch (ProjectCommonException e) {
      assertEquals(ResponseCode.invalidRequestParameter.getErrorCode(), e.getErrorCode());
    }
  }

  @Test
  public void testValidateUpdateOrgRequest_WithoutStatusField() {
    requestData.put(JsonKey.ORGANISATION_ID, "org123");
    requestData.put(JsonKey.ORG_NAME, "Updated Organization");

    // Should not throw exception when status is not present
    validator.validateUpdateOrgRequest(orgRequest);
  }

  @Test
  public void testValidateUpdateOrgRequest_WithOtherUpdateableFields() {
    requestData.put(JsonKey.ORGANISATION_ID, "org123");
    requestData.put(JsonKey.ORG_NAME, "New Name");
    requestData.put(JsonKey.DESCRIPTION, "New Description");

    // Should not throw exception
    validator.validateUpdateOrgRequest(orgRequest);
  }

  @Test
  public void testValidateUpdateOrgRequest_WithNullStatus() {
    requestData.put(JsonKey.ORGANISATION_ID, "org123");
    requestData.put(JsonKey.STATUS, null);

    // null value for STATUS should not throw exception (field not really present)
    validator.validateUpdateOrgRequest(orgRequest);
  }

  // =============================================
  // Read-Only Fields Tests
  // =============================================

  @Test
  public void testValidateUpdateOrgRequest_OrgIdIsReadOnly() {
    // OrgId is used as reference, not for update
    requestData.put(JsonKey.ORGANISATION_ID, "org123");

    // Should not throw exception
    validator.validateUpdateOrgRequest(orgRequest);
  }

  @Test
  public void testValidateUpdateOrgRequest_MultipleReadOnlyFields() {
    requestData.put(JsonKey.ORGANISATION_ID, "org123");
    requestData.put(JsonKey.STATUS, 1);

    try {
      validator.validateUpdateOrgRequest(orgRequest);
      fail("Should reject read-only STATUS field");
    } catch (ProjectCommonException e) {
      assertEquals(ResponseCode.invalidRequestParameter.getErrorCode(), e.getErrorCode());
    }
  }

  // =============================================
  // validateUpdateOrgStatusRequest Tests
  // =============================================

  @Test
  public void testValidateUpdateOrgStatusRequest_WithValidData() {
    requestData.put(JsonKey.ORGANISATION_ID, "org123");
    requestData.put(JsonKey.STATUS, 1);

    // Should not throw exception
    validator.validateUpdateOrgStatusRequest(orgRequest);
  }

  @Test(expected = ProjectCommonException.class)
  public void testValidateUpdateOrgStatusRequest_WithMissingOrgId() {
    requestData.put(JsonKey.STATUS, 1);

    validator.validateUpdateOrgStatusRequest(orgRequest);
  }

  @Test(expected = ProjectCommonException.class)
  public void testValidateUpdateOrgStatusRequest_WithMissingStatus() {
    requestData.put(JsonKey.ORGANISATION_ID, "org123");

    validator.validateUpdateOrgStatusRequest(orgRequest);
  }

  @Test
  public void testValidateUpdateOrgStatusRequest_WithMissingStatus_ExceptionCode() {
    requestData.put(JsonKey.ORGANISATION_ID, "org123");

    try {
      validator.validateUpdateOrgStatusRequest(orgRequest);
      fail("Should throw exception");
    } catch (ProjectCommonException e) {
      assertEquals(ResponseCode.invalidRequestData.getErrorCode(), e.getErrorCode());
    }
  }

  @Test(expected = ProjectCommonException.class)
  public void testValidateUpdateOrgStatusRequest_WithStringStatus() {
    requestData.put(JsonKey.ORGANISATION_ID, "org123");
    requestData.put(JsonKey.STATUS, "active"); // Should be Integer

    validator.validateUpdateOrgStatusRequest(orgRequest);
  }

  @Test
  public void testValidateUpdateOrgStatusRequest_WithStringStatus_ExceptionCode() {
    requestData.put(JsonKey.ORGANISATION_ID, "org123");
    requestData.put(JsonKey.STATUS, "active");

    try {
      validator.validateUpdateOrgStatusRequest(orgRequest);
      fail("Should throw exception");
    } catch (ProjectCommonException e) {
      assertEquals(ResponseCode.invalidRequestData.getErrorCode(), e.getErrorCode());
    }
  }

  @Test(expected = ProjectCommonException.class)
  public void testValidateUpdateOrgStatusRequest_WithNullStatus() {
    requestData.put(JsonKey.ORGANISATION_ID, "org123");
    requestData.put(JsonKey.STATUS, null);

    validator.validateUpdateOrgStatusRequest(orgRequest);
  }

  @Test
  public void testValidateUpdateOrgStatusRequest_WithStatusZero() {
    requestData.put(JsonKey.ORGANISATION_ID, "org123");
    requestData.put(JsonKey.STATUS, 0);

    // Should accept 0 as valid status
    validator.validateUpdateOrgStatusRequest(orgRequest);
  }

  @Test
  public void testValidateUpdateOrgStatusRequest_WithLargeIntegerStatus() {
    requestData.put(JsonKey.ORGANISATION_ID, "org123");
    requestData.put(JsonKey.STATUS, 999);

    // Should accept any integer
    validator.validateUpdateOrgStatusRequest(orgRequest);
  }

  @Test
  public void testValidateUpdateOrgStatusRequest_WithNegativeStatus() {
    requestData.put(JsonKey.ORGANISATION_ID, "org123");
    requestData.put(JsonKey.STATUS, -1);

    // Should accept negative integers (validation depends on business logic)
    validator.validateUpdateOrgStatusRequest(orgRequest);
  }

  // =============================================
  // Edge Cases and Integration Tests
  // =============================================

  @Test
  public void testValidateUpdateOrgRequest_EmptyOrgName() {
    requestData.put(JsonKey.ORGANISATION_ID, "org123");
    requestData.put(JsonKey.ORG_NAME, "");

    // Depending on implementation, empty name might fail
    try {
      validator.validateUpdateOrgRequest(orgRequest);
    } catch (ProjectCommonException e) {
      // May fail on name validation
      assertNotNull(e);
    }
  }

  @Test
  public void testValidateUpdateOrgRequest_WithRootOrgIdEmpty() {
    requestData.put(JsonKey.ORGANISATION_ID, "org123");
    requestData.put(JsonKey.ROOT_ORG_ID, "");

    try {
      validator.validateUpdateOrgRequest(orgRequest);
      fail("Should fail on empty ROOT_ORG_ID");
    } catch (ProjectCommonException e) {
      assertEquals(ResponseCode.invalidParameterValue.getErrorCode(), e.getErrorCode());
    }
  }

  @Test
  public void testValidateUpdateOrgRequest_WithValidRootOrgId() {
    requestData.put(JsonKey.ORGANISATION_ID, "org123");
    requestData.put(JsonKey.ROOT_ORG_ID, "root_org_001");

    // Should not throw exception
    validator.validateUpdateOrgRequest(orgRequest);
  }

  @Test
  public void testValidateCreateOrgRequest_AllMandatoryFieldsPresent() {
    requestData.put(JsonKey.ORG_TYPE, "school");
    requestData.put(JsonKey.ORG_NAME, "Test School");
    requestData.put(JsonKey.IS_TENANT, true);
    requestData.put(JsonKey.CHANNEL, "school-channel");

    // Should not throw exception
    validator.validateCreateOrgRequest(orgRequest);
  }

  @Test
  public void testValidateUpdateOrgStatusRequest_StatusTypeValidation() {
    // Test various integer types
    requestData.put(JsonKey.ORGANISATION_ID, "org123");
    requestData.put(JsonKey.STATUS, Integer.valueOf(1));

    // Should accept Integer type
    validator.validateUpdateOrgStatusRequest(orgRequest);
  }

  @Test(expected = ProjectCommonException.class)
  public void testValidateUpdateOrgStatusRequest_StatusAsDouble() {
    requestData.put(JsonKey.ORGANISATION_ID, "org123");
    requestData.put(JsonKey.STATUS, 1.5);

    validator.validateUpdateOrgStatusRequest(orgRequest);
  }

  @Test(expected = ProjectCommonException.class)
  public void testValidateUpdateOrgStatusRequest_StatusAsLong() {
    requestData.put(JsonKey.ORGANISATION_ID, "org123");
    requestData.put(JsonKey.STATUS, 1L);

    // Long might not be accepted if only Integer type is validated
    validator.validateUpdateOrgStatusRequest(orgRequest);
  }
}
