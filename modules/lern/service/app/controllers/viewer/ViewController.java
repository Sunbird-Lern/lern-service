package controllers.viewer;

import controllers.BaseController;
import org.apache.commons.lang3.StringUtils;
import org.apache.pekko.actor.ActorRef;
import org.sunbird.keys.JsonKey;
import org.sunbird.request.Request;
import play.mvc.Http;
import play.mvc.Result;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

// view lifecycle APIs -> view-consumption-actor (bound in LernServiceActorStartModule)
public class ViewController extends BaseController {

    @Inject
    @Named("view-consumption-actor")
    private ActorRef viewConsumptionActor;

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

    private CompletionStage<Result> dispatch(String operation, Http.Request httpRequest) {
        try {
            Request request = createAndInitRequest(operation, httpRequest.body().asJson(), httpRequest);
            // Derive the acting userId from the auth token — never trust a client-supplied userId.
            String userId = (String) request.getContext().getOrDefault(JsonKey.REQUESTED_FOR, request.getContext().get(JsonKey.REQUESTED_BY));
            request.getRequest().put(JsonKey.USER_ID, userId);
            // optional ?context=all (view.read) -> read all contents irrespective of context (type:"contextall")
            httpRequest.queryString("context").filter(StringUtils::isNotBlank)
                .ifPresent(ctx -> request.getRequest().put("context", ctx));
            return actorResponseHandler(viewConsumptionActor, request, timeout, null, httpRequest);
        } catch (Exception e) {
            return CompletableFuture.completedFuture(createCommonExceptionResponse(e, httpRequest));
        }
    }
}
