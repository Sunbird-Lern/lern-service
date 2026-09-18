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
        return adminOnly("roleDefRead", httpRequest);
    }

    /** Replaces one role's requirement set. `skills` is the FULL set; anything absent is removed. */
    public CompletionStage<Result> roleUpsert(Http.Request httpRequest) {
        return adminOnly("roleUpsert", httpRequest);
    }

    /** Retires a role. Never deletes: learner assignments and LP targetRole hold the code. */
    public CompletionStage<Result> roleRetire(Http.Request httpRequest) {
        return adminOnly("roleRetire", httpRequest);
    }

    /** Admin assigns a learner's current role. Target user is `assignUserId`, not the token. */
    public CompletionStage<Result> roleAssign(Http.Request httpRequest) {
        return adminOnly("roleAssign", httpRequest);
    }

    /** The whole authoring matrix. `dryRun: true` reports the diff without writing. */
    public CompletionStage<Result> roleImport(Http.Request httpRequest) {
        return adminOnly("roleImport", httpRequest);
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

    // ---- authorisation -------------------------------------------------------------------------

    /** Platform roles that may author competency data. */
    private static final java.util.Set<String> COMPETENCY_ADMIN_ROLES =
        new java.util.HashSet<>(java.util.Arrays.asList("ORG_ADMIN", "SYSTEM_ADMIN"));

    /**
     * Admin gate for the role-authoring operations.
     *
     * WHY THIS IS HERE AND NOT ONLY AT THE GATEWAY: the portal authenticates to Kong with ONE
     * shared consumer for every logged-in user (BFF getBearerToken -> KONG_LOGGEDIN_FALLBACK_TOKEN
     * or the session's kong token), so a Kong ACL cannot tell an admin from a learner - it only
     * separates authenticated traffic from anonymous. The caller's real identity is in the user
     * token, which is where the decision belongs.
     *
     * These five operations write the role -> skill map and other learners' assigned roles. Without
     * this, any logged-in user could rewrite what their own role requires and read 100% ready.
     */
    private boolean isCompetencyAdmin(Http.Request httpRequest) {
        try {
            String token = httpRequest.header(org.sunbird.keys.JsonKey.X_AUTHENTICATED_USER_TOKEN)
                .orElse(null);
            if (token == null) return false;
            java.util.Map<String, Object> claims =
                org.sunbird.auth.verifier.AccessTokenValidator.validateToken(token);
            Object roles = claims == null ? null : claims.get("roles");
            if (!(roles instanceof java.util.List)) return false;
            for (Object r : (java.util.List<?>) roles) {
                if (r instanceof java.util.Map) {
                    Object name = ((java.util.Map<?, ?>) r).get("role");
                    if (name != null && COMPETENCY_ADMIN_ROLES.contains(name.toString())) return true;
                }
            }
            return false;
        } catch (Exception e) {
            // A token we cannot read is not an admin token.
            return false;
        }
    }

    /** Runs `operation` only for a competency admin; otherwise 401/UNAUTHORIZED_USER. */
    private CompletionStage<Result> adminOnly(String operation, Http.Request httpRequest) {
        if (!isCompetencyAdmin(httpRequest)) {
            return CompletableFuture.completedFuture(createCommonExceptionResponse(
                new org.sunbird.exception.ProjectCommonException(
                    org.sunbird.message.ResponseCode.unAuthorized.getErrorCode(),
                    "Competency role authoring requires an organisation administrator.",
                    org.sunbird.message.ResponseCode.UNAUTHORIZED.getResponseCode()),
                httpRequest));
        }
        return body(operation, httpRequest);
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
