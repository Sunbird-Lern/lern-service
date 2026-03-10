package validators;

import org.apache.commons.lang3.StringUtils;
import org.sunbird.exception.ProjectCommonException;
import org.sunbird.message.ResponseCode;
import org.sunbird.request.Request;

/**
 * Validates incoming requests for the Observability Report API.
 *
 * generateReport: reportId is mandatory.
 * listReports:    no mandatory fields — no validator needed.
 */
public class ObservabilityReportRequestValidator {

    public void validate(Request request) {
        String reportId = (String) request.getRequest().get("reportId");
        if (StringUtils.isBlank(reportId)) {
            throw new ProjectCommonException(
                ResponseCode.mandatoryParameterMissing.getErrorCode(),
                ResponseCode.mandatoryParameterMissing.getErrorMessage() + " reportId",
                ResponseCode.CLIENT_ERROR.getResponseCode()
            );
        }
    }
}
