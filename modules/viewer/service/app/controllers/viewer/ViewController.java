package controllers.viewer;

import controllers.BaseController;
import org.apache.pekko.actor.ActorRef;
import org.sunbird.request.Request;
import play.mvc.Http;
import play.mvc.Result;

import play.libs.Json;
import play.mvc.Results;
import com.fasterxml.jackson.databind.node.ObjectNode;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Granular view lifecycle APIs. Dispatches to ViewConsumptionActor (view-consumption-actor).
 *   POST /v1/view/start  -> viewStart
 *   POST /v1/view/update -> viewUpdate
 *   POST /v1/view/end    -> viewEnd
 */
public class ViewController extends BaseController {

    private final ActorRef viewConsumptionActor;

    @Inject
    public ViewController(@Named("view-consumption-actor") ActorRef viewConsumptionActor) {
        this.viewConsumptionActor = viewConsumptionActor;
    }

    public CompletionStage<Result> viewStart(Http.Request httpRequest) {
        return dispatch("viewStart", httpRequest);
    }

    public CompletionStage<Result> viewUpdate(Http.Request httpRequest) {
        return dispatch("viewUpdate", httpRequest);
    }

    public CompletionStage<Result> viewEnd(Http.Request httpRequest) {
        return dispatch("viewEnd", httpRequest);
    }

    public CompletionStage<Result> viewRead(Http.Request httpRequest) {
        return dispatch("viewRead", httpRequest);
    }

    public CompletionStage<Result> assessmentSubmit(Http.Request httpRequest) {
        return dispatch("viewAssess", httpRequest);
    }

    public CompletionStage<Result> assessmentRead(Http.Request httpRequest) {
        return dispatch("assessmentRead", httpRequest);
    }

    public Result health(Http.Request httpRequest) {
        ObjectNode json = Json.newObject();
        json.put("healthy", true);
        return Results.ok(json);
    }

    public Result preflight(String all) {
        return Results.ok();
    }

    private CompletionStage<Result> dispatch(String operation, Http.Request httpRequest) {
        try {
            Request request = createAndInitRequest(operation, httpRequest.body().asJson(), httpRequest);
            return actorResponseHandler(viewConsumptionActor, request, timeout, null, httpRequest);
        } catch (Exception e) {
            return CompletableFuture.completedFuture(createCommonExceptionResponse(e, httpRequest));
        }
    }
}
