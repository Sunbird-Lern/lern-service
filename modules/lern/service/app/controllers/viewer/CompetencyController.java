package controllers.viewer;

import controllers.BaseController;
import org.apache.pekko.actor.ActorRef;
import org.sunbird.keys.JsonKey;
import org.sunbird.request.Request;
import play.mvc.Http;
import play.mvc.Result;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Competency passbook, gap and evidence APIs -> competency-actor (bound in
 * LernServiceActorStartModule). Thin monolith copy of the viewer module's controller: the
 * viewer service ships as part of this app, so its routes must be declared here too.
 *
 * Learner-facing operations take the user id from the auth token, never the body, so one learner
 * cannot read or alter another's passbook.
 *
 * The viewer module's copy of this controller also exposes six /private admin operations
 * (evidence import/revoke, reproject, cache invalidate, expiry sweep, admin position update).
 * They are deliberately NOT mirrored here: LernServiceRequestInterceptor skips token validation
 * for any path containing "private", so routing them in this app would publish six
 * unauthenticated passbook writes to anything with in-cluster network access.
 */
public class CompetencyController extends BaseController {

    @Inject
    @Named("competency-actor")
    private ActorRef competencyActor;

    public CompletionStage<Result> passbookRead(Http.Request httpRequest) {
        return self("passbookRead", httpRequest);
    }

    public CompletionStage<Result> gapRead(Http.Request httpRequest) {
        return self("gapRead", httpRequest);
    }

    public CompletionStage<Result> recommend(Http.Request httpRequest) {
        return self("recommend", httpRequest);
    }

    /**
     * Learner sets their own target positions. currentPosition is dropped here: who a learner
     * reports as is an assignment, not a preference, so it is only settable through /private.
     */
    public CompletionStage<Result> positionUpdate(Http.Request httpRequest) {
        try {
            Request request = createAndInitRequest("positionUpdate", httpRequest.body().asJson(), httpRequest);
            request.getRequest().put(JsonKey.USER_ID, authUserId(request));
            request.getRequest().remove("currentPosition");
            request.getRequest().put("source", "SELF");
            return actorResponseHandler(competencyActor, request, timeout, null, httpRequest);
        } catch (Exception e) {
            return CompletableFuture.completedFuture(createCommonExceptionResponse(e, httpRequest));
        }
    }

    public CompletionStage<Result> frameworkRead(String frameworkId, Http.Request httpRequest) {
        try {
            Request request = createAndInitRequest("frameworkRead", httpRequest);
            request.getRequest().put("frameworkId", frameworkId);
            return actorResponseHandler(competencyActor, request, timeout, null, httpRequest);
        } catch (Exception e) {
            return CompletableFuture.completedFuture(createCommonExceptionResponse(e, httpRequest));
        }
    }

    // ---- plumbing -----------------------------------------------------------------------------

    /** userId comes from the token, so the body cannot name a different learner. */
    private CompletionStage<Result> self(String operation, Http.Request httpRequest) {
        try {
            Request request = httpRequest.body().asJson() != null
                ? createAndInitRequest(operation, httpRequest.body().asJson(), httpRequest)
                : createAndInitRequest(operation, httpRequest);
            request.getRequest().put(JsonKey.USER_ID, authUserId(request));
            return actorResponseHandler(competencyActor, request, timeout, null, httpRequest);
        } catch (Exception e) {
            return CompletableFuture.completedFuture(createCommonExceptionResponse(e, httpRequest));
        }
    }



    private String authUserId(Request request) {
        return (String) request.getContext().getOrDefault(
            JsonKey.REQUESTED_FOR, request.getContext().get(JsonKey.REQUESTED_BY));
    }
}
