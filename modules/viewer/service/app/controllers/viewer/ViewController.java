package controllers.viewer;

import controllers.BaseController;
import org.apache.commons.lang3.StringUtils;
import org.apache.pekko.actor.ActorRef;
import org.sunbird.exception.ProjectCommonException;
import org.sunbird.keys.JsonKey;
import org.sunbird.message.ResponseCode;
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

    public Result health(Http.Request httpRequest) {
        ObjectNode json = Json.newObject();
        json.put("healthy", true);
        return Results.ok(json);
    }

    private CompletionStage<Result> dispatch(String operation, Http.Request httpRequest) {
        try {
            Request request = createAndInitRequest(operation, httpRequest.body().asJson(), httpRequest);
            // userId is derived from the auth token, never the client body
            String userId = (String) request.getContext().getOrDefault(JsonKey.REQUESTED_FOR, request.getContext().get(JsonKey.REQUESTED_BY));
            request.getRequest().put(JsonKey.USER_ID, userId);
            validate(operation, request);
            return actorResponseHandler(viewConsumptionActor, request, timeout, null, httpRequest);
        } catch (Exception e) {
            return CompletableFuture.completedFuture(createCommonExceptionResponse(e, httpRequest));
        }
    }

    // Reject requests missing keys the actor unconditionally dereferences, so callers get a 400 with the
    // offending field instead of an opaque 500/NPE. Contract is courseId/batchId/contentId; userId is
    // derived from the auth token above, so it is no longer a client-supplied mandatory field.
    private void validate(String operation, Request request) {
        switch (operation) {
            case "viewStart": case "viewUpdate": case "viewEnd":
                requireNonBlank(request, "courseId", "batchId", "contentId");
                break;
            case "viewRead":
                requireNonBlank(request, "courseId", "batchId");
                break;
            default: // no mandatory fields
        }
    }

    private void requireNonBlank(Request request, String... keys) {
        for (String key : keys) {
            if (StringUtils.isBlank((String) request.getRequest().get(key))) {
                throw new ProjectCommonException(
                    ResponseCode.mandatoryParameterMissing.getErrorCode(),
                    ResponseCode.mandatoryParameterMissing.getErrorMessage() + " " + key,
                    ResponseCode.CLIENT_ERROR.getResponseCode());
            }
        }
    }
}
