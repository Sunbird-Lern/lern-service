package controllers.viewer;

import controllers.BaseController;
import org.apache.pekko.actor.ActorRef;
import org.sunbird.exception.ProjectCommonException;
import org.sunbird.keys.JsonKey;
import org.sunbird.request.Request;
import org.sunbird.response.ResponseCode;
import play.mvc.Http;
import play.mvc.Result;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Viewer resync API (monolith wiring) — recomputes a learner's collection roll-up from
 * user_content_consumption. Idempotent (recompute-from-source): backfill, drift repair, or a
 * recompute after a collection is republished. Mirrors the legacy POST /v1/activity/agg, but targets
 * the viewer aggregator (viewer-aggregator-actor, op "aggregate"). Runs on the userId-hashed pool, so
 * a resync serialises with any live /v1/view/end for that learner — no race with real-time roll-ups.
 *   POST /v1/view/agg   { request: { userId, courseId, batchId } }
 */
public class ViewAggregateController extends BaseController {

    @Inject
    @Named("viewer-aggregator-actor")
    private ActorRef viewerAggregatorActor;

    public CompletionStage<Result> agg(Http.Request httpRequest) {
        try {
            Request request = createAndInitRequest("aggregate", httpRequest.body().asJson(), httpRequest);
            validate(request);
            return actorResponseHandler(viewerAggregatorActor, request, timeout, null, httpRequest);
        } catch (Exception e) {
            return CompletableFuture.completedFuture(createCommonExceptionResponse(e, httpRequest));
        }
    }

    private void validate(Request request) {
        String userId = (String) request.get(JsonKey.USER_ID);
        Object courseId = request.get(JsonKey.COURSE_ID);
        if (userId == null || userId.trim().isEmpty()
                || courseId == null || courseId.toString().trim().isEmpty()) {
            throw new ProjectCommonException(
                ResponseCode.mandatoryParamsMissing.getErrorCode(),
                "userId and courseId are mandatory",
                ResponseCode.CLIENT_ERROR.getResponseCode());
        }
    }
}
