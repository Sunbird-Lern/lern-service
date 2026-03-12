package validators;

import org.junit.Test;
import org.sunbird.exception.ProjectCommonException;
import org.sunbird.request.Request;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.*;

/**
 * Unit tests for ObservabilityReportRequestValidator.
 *
 * Validates:
 *   1. Missing reportId throws ProjectCommonException.
 *   2. Blank reportId throws ProjectCommonException.
 *   3. Valid reportId with no transform passes.
 *   4. Valid reportId with a List transform passes.
 *   5. Non-List transform (e.g. a String) throws ProjectCommonException.
 */
public class ObservabilityReportRequestValidatorTest {

    private final ObservabilityReportRequestValidator validator =
        new ObservabilityReportRequestValidator();

    // ---- helper -----------------------------------------------------------------

    private Request requestWith(String reportId, Object transform) {
        Request req = new Request();
        if (reportId != null) req.getRequest().put("reportId", reportId);
        if (transform != null) req.getRequest().put("transform", transform);
        return req;
    }

    // ---- reportId validation ----------------------------------------------------

    @Test(expected = ProjectCommonException.class)
    public void validate_throwsWhenReportIdMissing() {
        // No "reportId" key in request at all
        validator.validate(new Request());
    }

    @Test(expected = ProjectCommonException.class)
    public void validate_throwsWhenReportIdIsBlank() {
        validator.validate(requestWith("   ", null));
    }

    @Test(expected = ProjectCommonException.class)
    public void validate_throwsWhenReportIdIsEmptyString() {
        validator.validate(requestWith("", null));
    }

    @Test
    public void validate_passesWhenReportIdIsPresentAndNoTransform() {
        // Should not throw
        validator.validate(requestWith("active_users_weekly", null));
    }

    // ---- transform validation ---------------------------------------------------

    @Test
    public void validate_passesWhenTransformIsAList() {
        validator.validate(requestWith(
            "active_users_weekly",
            Arrays.asList("userid", "courseid")
        ));
    }

    @Test
    public void validate_passesWhenTransformIsAnEmptyList() {
        validator.validate(requestWith(
            "active_users_weekly",
            Collections.emptyList()
        ));
    }

    @Test(expected = ProjectCommonException.class)
    public void validate_throwsWhenTransformIsAString() {
        validator.validate(requestWith("active_users_weekly", "userid"));
    }

    @Test(expected = ProjectCommonException.class)
    public void validate_throwsWhenTransformIsAnInteger() {
        validator.validate(requestWith("active_users_weekly", 42));
    }

    @Test(expected = ProjectCommonException.class)
    public void validate_throwsWhenTransformIsAMap() {
        validator.validate(requestWith("active_users_weekly",
            Collections.singletonMap("field", "userid")));
    }

    // ---- error message checks ---------------------------------------------------

    @Test
    public void validate_errorMessageMentionsReportIdWhenMissing() {
        try {
            validator.validate(new Request());
            fail("Expected ProjectCommonException");
        } catch (ProjectCommonException e) {
            assertTrue("Error message should mention 'reportId'",
                e.getMessage().toLowerCase().contains("reportid"));
        }
    }

    @Test
    public void validate_errorMessageMentionsTransformWhenInvalid() {
        try {
            validator.validate(requestWith("active_users_weekly", "not-a-list"));
            fail("Expected ProjectCommonException");
        } catch (ProjectCommonException e) {
            assertTrue("Error message should mention 'transform'",
                e.getMessage().toLowerCase().contains("transform"));
        }
    }
}
