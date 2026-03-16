package controllers.observability;

import controllers.BaseController;
import org.apache.pekko.actor.ActorRef;
import play.mvc.Http;
import play.mvc.Result;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.concurrent.CompletionStage;

/**
 * Controller for Standard Observability Reports API.
 *
 * Routes:
 *   POST /v1/observability/reports        - generate report data
 *   GET  /v1/observability/reports/list   - list available reports
 */
public class ObservabilityReportController extends BaseController {

    @Inject
    @Named("observability-report-actor")
    private ActorRef observabilityReportActorRef;

    /**
     * POST /v1/observability/reports
     * Body: { "request": { "reportId": "...", "filters": { ... } } }
     */
    public CompletionStage<Result> generateReport(Http.Request httpRequest) {
        return handleRequest(
            observabilityReportActorRef,
            "generateReport",
            httpRequest.body().asJson(),
            req -> {
                org.sunbird.request.Request request = (org.sunbird.request.Request) req;
                new validators.ObservabilityReportRequestValidator().validate(request);
                return null;
            },
            httpRequest
        );
    }

    /**
     * GET /v1/observability/reports/list
     */
    public CompletionStage<Result> listReports(Http.Request httpRequest) {
        return handleRequest(
            observabilityReportActorRef,
            "listReports",
            httpRequest
        );
    }
}
