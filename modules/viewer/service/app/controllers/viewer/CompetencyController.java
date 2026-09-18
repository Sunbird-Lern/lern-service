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
 * Skill profile, gap and evidence APIs -> CompetencyActor (competency-actor).
 *
 * Learner-facing operations take the user id from the auth token, never the body, so one learner
 * cannot read or alter another's profile. Operations that act on somebody else, or on the whole
 * framework, are exposed only under /private and carry the target user in a distinct body key.
 */
public class CompetencyController extends BaseController {

    private final ActorRef competencyActor;

    @Inject
    public CompetencyController(@Named("competency-actor") ActorRef competencyActor) {
        this.competencyActor = competencyActor;
    }

    public CompletionStage<Result> profileRead(Http.Request httpRequest) {
        return self("profileRead", httpRequest);
    }

    public CompletionStage<Result> gapRead(Http.Request httpRequest) {
        return self("gapRead", httpRequest);
    }

    public CompletionStage<Result> recommend(Http.Request httpRequest) {
        return self("recommend", httpRequest);
    }

    /**
     * Learner sets their own target roles. currentRole is dropped here: the role a learner holds
     * is an assignment, not a preference, so it is only settable through /private.
     */
    public CompletionStage<Result> roleUpdate(Http.Request httpRequest) {
        try {
            Request request = createAndInitRequest("roleUpdate", httpRequest.body().asJson(), httpRequest);
            request.getRequest().put(JsonKey.USER_ID, authUserId(request));
            request.getRequest().remove("currentRole");
            request.getRequest().put("source", "SELF");
            return actorResponseHandler(competencyActor, request, timeout, null, httpRequest);
        } catch (Exception e) {
            return CompletableFuture.completedFuture(createCommonExceptionResponse(e, httpRequest));
        }
    }

    /** Authoring-time check on a programme. Not learner-scoped: it reads content, not a profile. */
    public CompletionStage<Result> coverageRead(Http.Request httpRequest) {
        return body("coverageRead", httpRequest);
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

    // ---- role authoring -----------------------------------------------------------------------
    //
    // The role -> skill map is authored here, not in the Knowlg framework. These are ordinary
    // authenticated routes and deliberately NOT under /private: isRequestPrivate() skips token
    // validation for any path containing "private", and these requirements drive every learner's
    // readiness. Restricting them to admins belongs in Kong, which is where this platform
    // authorises.

    /** Roles as authored, RETIRED included. Distinct from frameworkRead, which serves learners. */
    public CompletionStage<Result> roleDefRead(Http.Request httpRequest) {
        return body("roleDefRead", httpRequest);
    }

    /** Replaces one role's requirement set. `skills` is the FULL set; anything absent is removed. */
    public CompletionStage<Result> roleUpsert(Http.Request httpRequest) {
        return body("roleUpsert", httpRequest);
    }

    /** Retires a role. Never deletes: learner assignments and LP targetRole hold the code. */
    public CompletionStage<Result> roleRetire(Http.Request httpRequest) {
        return body("roleRetire", httpRequest);
    }

    /** Admin assigns a learner's current role. Target user is `assignUserId`, not the token. */
    public CompletionStage<Result> roleAssign(Http.Request httpRequest) {
        return body("roleAssign", httpRequest);
    }

    /** The whole authoring matrix. `dryRun: true` reports the diff without writing. */
    public CompletionStage<Result> roleImport(Http.Request httpRequest) {
        return body("roleImport", httpRequest);
    }

    // ---- privileged ---------------------------------------------------------------------------

    public CompletionStage<Result> privateRoleUpdate(Http.Request httpRequest) {
        return privileged("roleUpdate", httpRequest, "roleUserId", JsonKey.USER_ID);
    }

    public CompletionStage<Result> evidenceImport(Http.Request httpRequest) {
        return body("evidenceImport", httpRequest);
    }

    public CompletionStage<Result> evidenceRevoke(Http.Request httpRequest) {
        return body("evidenceRevoke", httpRequest);
    }

    public CompletionStage<Result> reproject(Http.Request httpRequest) {
        return body("reproject", httpRequest);
    }

    public CompletionStage<Result> cacheInvalidate(Http.Request httpRequest) {
        return body("cacheInvalidate", httpRequest);
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

    /** Body-driven operation; the target user is a named body key, validated in the actor. */
    private CompletionStage<Result> body(String operation, Http.Request httpRequest) {
        try {
            Request request = httpRequest.body().asJson() != null
                ? createAndInitRequest(operation, httpRequest.body().asJson(), httpRequest)
                : createAndInitRequest(operation, httpRequest);
            return actorResponseHandler(competencyActor, request, timeout, null, httpRequest);
        } catch (Exception e) {
            return CompletableFuture.completedFuture(createCommonExceptionResponse(e, httpRequest));
        }
    }

    /** Copies a body key onto another key before dispatch. */
    private CompletionStage<Result> privileged(String operation, Http.Request httpRequest,
                                               String fromKey, String toKey) {
        try {
            Request request = createAndInitRequest(operation, httpRequest.body().asJson(), httpRequest);
            Object target = request.getRequest().get(fromKey);
            if (target != null) request.getRequest().put(toKey, target);
            request.getRequest().put("source", "HRMS");
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
