package controllers;

import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.pattern.PatternsCS;
import org.apache.pekko.util.Timeout;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import play.mvc.Results;
import org.sunbird.request.Request;
import org.sunbird.response.Response;
import org.sunbird.keys.JsonKey;
import org.sunbird.operations.userorg.ActorOperations;
import org.sunbird.common.ProjectUtil;
import util.Attrs;
import util.Common;
import modules.SignalHandler;
import org.sunbird.exception.ProjectCommonException;
import org.sunbird.response.ResponseCode;
import org.sunbird.logging.LoggerUtil;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

/**
 * Controller to handle health check requests for the Lern service.
 * It provides endpoints to verify the global health of the service and its
 * dependencies (Cassandra, Elasticsearch, etc.) as well as specific service-level health.
 */
public class HealthController extends Controller {
    private static LoggerUtil logger = new LoggerUtil(HealthController.class);
    
    /**
     * Handles system signals for graceful shutdown.
     */
    @Inject
    private SignalHandler signalHandler;

    /**
     * Refers to the Actor responsible for executing deep health checks across components.
     */
    @Inject
    @Named("HealthActor")
    private ActorRef healthActor;

    /**
     * List of supported health check categories.
     */
    private static List<String> list = new ArrayList<>();
    static {
        list.add("service");
        list.add("actor");
        list.add("cassandra");
        list.add("es");
    }

    /**
     * Executes a global health check for the entire service.
     * It delegates the verification of sub-components (DB, ES, Redis) to the HealthActor.
     *
     * @param httpRequest The incoming HTTP request.
     * @return A CompletionStage containing the health check results in a Result object.
     */
    public CompletionStage<Result> health(Http.Request httpRequest) {
        try {
            handleSigTerm();
            Request reqObj = new Request();
            reqObj.setOperation(ActorOperations.HEALTH_CHECK.getValue());
            reqObj.setRequestId(Common.getFromRequest(httpRequest, Attrs.REQUEST_ID));
            reqObj.getRequest().put(JsonKey.CREATED_BY, Common.getFromRequest(httpRequest, Attrs.USER_ID));
            reqObj.setEnv(getEnvironment());
            
            return actorResponseHandler(healthActor, reqObj, new Timeout(10, TimeUnit.SECONDS), httpRequest);
        } catch (Exception e) {
            logger.error("HealthController:health: Exception occurred", e);
            return CompletableFuture.completedFuture(createErrorResponse(e, httpRequest));
        }
    }

    /**
     * Provides category-specific health information or a basic service heartbeat.
     *
     * @param val         The health category (e.g., "service", "cassandra", "es").
     * @param httpRequest The incoming HTTP request.
     * @return A CompletionStage containing the specific health result.
     */
    public CompletionStage<Result> serviceHealth(String val, Http.Request httpRequest) {
        if (list.contains(val) && !JsonKey.SERVICE.equalsIgnoreCase(val)) {
            return health(httpRequest);
        } else {
            try {
                handleSigTerm();
                Map<String, Object> finalResponseMap = new HashMap<>();
                List<Map<String, Object>> responseList = new ArrayList<>();
                responseList.add(ProjectUtil.createCheckResponse(JsonKey.LEARNER_SERVICE, false, null));
                finalResponseMap.put(JsonKey.CHECKS, responseList);
                finalResponseMap.put(JsonKey.NAME, "Lern Service health");
                finalResponseMap.put(JsonKey.Healthy, true);
                
                Response response = new Response();
                response.getResult().put(JsonKey.RESPONSE, finalResponseMap);
                response.setId("api.lern.service.health");
                response.setVer("1.0");
                response.setTs(Common.getFromRequest(httpRequest, Attrs.REQUEST_ID));
                return CompletableFuture.completedFuture(ok(play.libs.Json.toJson(response)));
            } catch (Exception e) {
                logger.error("HealthController:serviceHealth: Exception occurred", e);
                return CompletableFuture.completedFuture(createErrorResponse(e, httpRequest));
            }
        }
    }

    /**
     * Checks if the service is currently shutting down and prevents fulfillment
     * of health checks if a termination signal has been received.
     */
    private void handleSigTerm() {
        if (signalHandler.isShuttingDown()) {
            throw new ProjectCommonException(
                ResponseCode.serviceUnAvailable,
                ResponseCode.serviceUnAvailable.getErrorMessage(),
                ResponseCode.SERVICE_UNAVAILABLE.getResponseCode());
        }
    }

    /**
     * Creates a standardized error response for health check failures.
     *
     * @param e       The exception that occurred.
     * @param request The original HTTP request.
     * @return A Play Result containing the error response.
     */
    private Result createErrorResponse(Exception e, Http.Request request) {
        Response response = new Response();
        response.setResponseCode(ResponseCode.SERVER_ERROR);
        response.setId("api.lern.service.health.error");
        response.setVer("1.0");
        response.setTs(Common.getFromRequest(request, Attrs.REQUEST_ID));
        return internalServerError(play.libs.Json.toJson(response));
    }

    /**
     * Helper method to process asynchronous responses from the HealthActor.
     *
     * @param actorRef The target actor (HealthActor).
     * @param request  The request object sent to the actor.
     * @param timeout  The execution timeout.
     * @param httpReq  The original HTTP request context.
     * @return A CompletionStage transforming the actor's response into a Play Result.
     */
    private CompletionStage<Result> actorResponseHandler(ActorRef actorRef, Request request, Timeout timeout, Http.Request httpReq) {
        return PatternsCS.ask(actorRef, request, timeout).thenApplyAsync(result -> {
            if (result instanceof Response) {
                Response response = (Response) result;
                response.setId("api.lern.service.health");
                response.setVer("1.0");
                response.setTs(ProjectUtil.getFormattedDate());
                return ok(play.libs.Json.toJson(response));
            }
            return internalServerError();
        });
    }

    /**
     * Determines the execution environment.
     *
     * @return The environment value (defaults to dev).
     */
    private int getEnvironment() {
        return ProjectUtil.Environment.dev.getValue();
    }
}
