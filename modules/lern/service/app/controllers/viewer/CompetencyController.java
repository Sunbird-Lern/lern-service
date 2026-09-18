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
