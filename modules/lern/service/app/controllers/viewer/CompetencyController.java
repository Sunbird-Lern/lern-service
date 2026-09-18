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
 * Monolith copy of the viewer module's CompetencyController. The deployed app is this monolith, so
 * anything not mirrored here 404s no matter what modules/viewer/service/conf/routes declares.
 *
 * WHAT IS DELIBERATELY NOT MIRRORED: the /private operations - privateRoleUpdate, evidenceImport,
 * evidenceRevoke, reproject and cacheInvalidate. LernServiceRequestInterceptor.isRequestPrivate()
 * exempts any path containing "private" from token validation, so exposing them here would put
 * unauthenticated writes to learner evidence on the deployed surface. They stay in the viewer
 * module, which is not internet-facing.
 *
 * The role authoring operations below ARE mirrored: they are ordinary authenticated routes, not
 * /private ones, and without them the role -> skill map cannot be authored at all. Restricting them
 * to admins is a Kong concern, which is where this platform authorises.
 */
public class CompetencyController extends BaseController {

    private final ActorRef competencyActor;

    @Inject
    public CompetencyController(@Named("competency-actor") ActorRef competencyActor) {
        this.competencyActor = competencyActor;
    }

    // ---- learner-facing -------------------------------------------------------------------------

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
     * Learner sets their own target roles. currentRole is dropped: the role a learner holds is an
     * assignment, not a preference, and is only settable through the viewer module's /private route.
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

    // ---- role authoring -------------------------------------------------------------------------

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

    // ---- plumbing -------------------------------------------------------------------------------

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

    private String authUserId(Request request) {
        return (String) request.getContext().getOrDefault(
            JsonKey.REQUESTED_FOR, request.getContext().get(JsonKey.REQUESTED_BY));
    }
}
