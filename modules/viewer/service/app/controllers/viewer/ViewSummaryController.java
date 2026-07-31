package controllers.viewer;

import controllers.BaseController;
import org.apache.pekko.actor.ActorRef;
import org.sunbird.request.Request;
import play.mvc.Http;
import play.mvc.Result;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Summary APIs. Dispatches to ViewerSummaryActor (viewer-summary-actor).
 *   POST   /v1/summary/read
 *   GET    /v1/summary/list/:userId
 *   DELETE /v1/summary/delete/:userId
 */
public class ViewSummaryController extends BaseController {

    private final ActorRef viewerSummaryActor;

    @Inject
    public ViewSummaryController(@Named("viewer-summary-actor") ActorRef viewerSummaryActor) {
        this.viewerSummaryActor = viewerSummaryActor;
    }

    public CompletionStage<Result> summaryRead(Http.Request httpRequest) {
        return dispatchBody("summaryRead", httpRequest);
    }

    public CompletionStage<Result> summaryList(String userId, Http.Request httpRequest) {
        try {
            Request request = createAndInitRequest("summaryList", httpRequest);
            request.getRequest().put("userId", userId);
            return actorResponseHandler(viewerSummaryActor, request, timeout, null, httpRequest);
        } catch (Exception e) {
            return CompletableFuture.completedFuture(createCommonExceptionResponse(e, httpRequest));
        }
    }

    public CompletionStage<Result> summaryDownload(String userId, Http.Request httpRequest) {
        try {
            Request request = createAndInitRequest("summaryDownload", httpRequest);
            request.getRequest().put("userId", userId);
            String[] fmt = httpRequest.queryString().getOrDefault("format", new String[]{"json"});
            request.getRequest().put("format", fmt.length > 0 ? fmt[0] : "json");
            return actorResponseHandler(viewerSummaryActor, request, timeout, null, httpRequest);
        } catch (Exception e) {
            return CompletableFuture.completedFuture(createCommonExceptionResponse(e, httpRequest));
        }
    }

    public CompletionStage<Result> summaryDelete(String userId, Http.Request httpRequest) {
        try {
            Request request = httpRequest.body().asJson() != null
                ? createAndInitRequest("summaryDelete", httpRequest.body().asJson(), httpRequest)
                : createAndInitRequest("summaryDelete", httpRequest);
            request.getRequest().put("userId", userId);
            return actorResponseHandler(viewerSummaryActor, request, timeout, null, httpRequest);
        } catch (Exception e) {
            return CompletableFuture.completedFuture(createCommonExceptionResponse(e, httpRequest));
        }
    }

    private CompletionStage<Result> dispatchBody(String operation, Http.Request httpRequest) {
        try {
            Request request = createAndInitRequest(operation, httpRequest.body().asJson(), httpRequest);
            return actorResponseHandler(viewerSummaryActor, request, timeout, null, httpRequest);
        } catch (Exception e) {
            return CompletableFuture.completedFuture(createCommonExceptionResponse(e, httpRequest));
        }
    }
}
