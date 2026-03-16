package validators;

import org.apache.commons.lang3.StringUtils;
import org.sunbird.exception.ProjectCommonException;
import org.sunbird.message.ResponseCode;
import org.sunbird.request.Request;

import java.util.List;

/**
 * Validates incoming requests for the Observability Report API.
 *
 * generateReport: reportId is mandatory; transform (if present) must be a JSON array.
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

        Object transform = request.getRequest().get("transform");
        if (transform != null) {
            if (!(transform instanceof List)) {
                throw new ProjectCommonException(
                    ResponseCode.invalidRequestData.getErrorCode(),
                    "'transform' must be a JSON array of strings (e.g. [\"userid\", \"courseid\"])",
                    ResponseCode.CLIENT_ERROR.getResponseCode()
                );
            }
            List<?> transformList = (List<?>) transform;
            if (transformList.size() > 10) {
                throw new ProjectCommonException(
                    ResponseCode.invalidRequestData.getErrorCode(),
                    "'transform' list must not exceed 10 entries",
                    ResponseCode.CLIENT_ERROR.getResponseCode()
                );
            }
            for (Object item : transformList) {
                if (!(item instanceof String) || StringUtils.isBlank((String) item)) {
                    throw new ProjectCommonException(
                        ResponseCode.invalidRequestData.getErrorCode(),
                        "'transform' list must contain non-empty strings only",
                        ResponseCode.CLIENT_ERROR.getResponseCode()
                    );
                }
            }
        }
    }
}
