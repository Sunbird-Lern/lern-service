package controllers.sync;

import org.apache.pekko.actor.ActorRef;
import com.fasterxml.jackson.databind.JsonNode;
import controllers.BaseController;
import java.util.HashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import javax.inject.Inject;
import javax.inject.Named;
import org.sunbird.exception.ProjectCommonException;
import org.sunbird.keys.JsonKey;
import org.sunbird.operations.userorg.ActorOperations;
import org.sunbird.request.Request;
import org.sunbird.validators.RequestValidator;
import play.mvc.Http;
import play.mvc.Result;
import util.Attrs;
import util.Common;

/**
 * Unified SyncController for Monolithic Service.
 * Merges UserOrg and LMS sync logic by routing to the appropriate actor based on objectType.
 */
public class SyncController extends BaseController {

  @Inject
  @Named("es_sync_actor")
  private ActorRef userOrgSyncActor;

  @Inject
  @Named("es-sync-actor")
  private ActorRef lmsSyncActor;

  /**
   * This method will do data Sync from Cassandra db to Elasticsearch.
   * Routes to UserOrg EsSyncActor for 'user' and 'organisation' types.
   * Routes to LMS EsSyncActor for 'batch' and 'user_course' types.
   *
   * @return CompletionStage<Result>
   */
  public CompletionStage<Result> sync(Http.Request httpRequest) {
    Request reqObj = new Request();
    try {
      JsonNode requestData = httpRequest.body().asJson();
      reqObj = (Request) mapper.RequestMapper.mapRequest(requestData, Request.class);
      RequestValidator.validateSyncRequest(reqObj);
      
      String objectType = (String) reqObj.getRequest().get(JsonKey.OBJECT_TYPE);
      
      reqObj.setOperation(ActorOperations.SYNC.getValue());
      // Handle potential attribute name differences between UserOrg and LMS base controllers
      String requestId = Common.getFromRequest(httpRequest, Attrs.REQUEST_ID);
      if (requestId == null) {
          requestId = httpRequest.attrs().getOptional(Attrs.REQUEST_ID).orElse(null);
      }
      reqObj.setRequestId(requestId);
      
      reqObj.getRequest().put(JsonKey.CREATED_BY, httpRequest.attrs().getOptional(Attrs.USER_ID).orElse(null));
      reqObj.setEnv(getEnvironment());
      
      // Standard Sunbird sync request wrapper
      HashMap<String, Object> map = new HashMap<>();
      map.put(JsonKey.DATA, reqObj.getRequest());
      reqObj.setRequest(map);
      
      setContextAndPrintEntryLog(httpRequest, reqObj);
      
      if (JsonKey.USER.equalsIgnoreCase(objectType) || JsonKey.ORGANISATION.equalsIgnoreCase(objectType)) {
          logger.info(reqObj.getRequestContext(), "SyncController: Routing to UserOrg Sync Actor for type: " + objectType);
          return actorResponseHandler(userOrgSyncActor, reqObj, timeout, null, httpRequest);
      } else {
          logger.info(reqObj.getRequestContext(), "SyncController: Routing to LMS Sync Actor for type: " + objectType);
          return actorResponseHandler(lmsSyncActor, reqObj, timeout, null, httpRequest);
      }
      
    } catch (Exception e) {
        logger.error("SyncController: Exception occurred: " + e.getMessage(), e);
        return CompletableFuture.completedFuture(createCommonExceptionResponse(e, httpRequest));
    }
  }
}
