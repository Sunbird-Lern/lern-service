package actors;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.ws.rs.core.MediaType;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpHeaders;
import org.sunbird.actor.core.ActorConfig;
import org.sunbird.actor.core.BaseActor;
import org.sunbird.cache.util.RedisCacheUtil;
import org.sunbird.cassandra.CassandraOperation;
import org.sunbird.common.ElasticSearchHelper;
import org.sunbird.common.ProjectUtil;
import org.sunbird.common.PropertiesCache;
import org.sunbird.common.factory.EsClientFactory;
import org.sunbird.common.inf.ElasticSearchService;
import org.sunbird.helper.ServiceFactory;
import org.sunbird.http.HttpUtil;
import org.sunbird.keys.JsonKey;
import org.sunbird.logging.LoggerUtil;
import org.sunbird.operations.userorg.ActorOperations;
import org.sunbird.request.Request;
import org.sunbird.response.Response;
import org.sunbird.util.Util;
import scala.concurrent.Future;

/**
 * HealthActor is responsible for checking the health status of various components
 * in the Lern Service, including Cassandra, Elasticsearch, Redis, and external services.
 * It consolidates health check logic from LMS, UserOrg, and Notification services.
 */
@ActorConfig(
    tasks = {"health", "healthCheck", "checkHealth", "actor", "cassandra", "es", "ekstep"},
    asyncTasks = {}
)
public class HealthActor extends BaseActor {

    private final LoggerUtil logger = new LoggerUtil(HealthActor.class);
    private final CassandraOperation cassandraOperation = ServiceFactory.getInstance();
    private final ElasticSearchService esUtil = EsClientFactory.getInstance(JsonKey.REST);
    private final RedisCacheUtil redisCacheUtil;

    /**
     * Default constructor for HealthActor.
     * Initializes RedisCacheUtil for connectivity checks.
     */
    public HealthActor() {
        this.redisCacheUtil = new RedisCacheUtil();
    }

    /**
     * Entry point for message processing. Routes the health check request to the appropriate handler.
     * Supports component-specific health checks (cassandra, es, actor, ekstep) as well as full checks.
     *
     * @param request The incoming Request object.
     * @throws Throwable If any error occurs during message routing or processing.
     */
    @Override
    public void onReceive(Request request) throws Throwable {
        if (request instanceof Request) {
            String operation = request.getOperation();
            if (ActorOperations.CASSANDRA.getValue().equalsIgnoreCase(operation)) {
                checkCassandraHealth();
            } else if (ActorOperations.ES.getValue().equalsIgnoreCase(operation)) {
                checkEsHealth();
            } else if (ActorOperations.ACTOR.getValue().equalsIgnoreCase(operation)) {
                checkActorHealth();
            } else if (ActorOperations.EKSTEP.getValue().equalsIgnoreCase(operation)) {
                checkEkStepHealth();
            } else {
                // Default: full health check for "healthCheck", "health", "checkHealth"
                checkAllComponentHealth(request);
            }
        } else {
            onReceiveUnsupportedOperation();
        }
    }

    /**
     * Performs comprehensive health checks for all fundamental components (Cassandra, 
     * Elasticsearch, Redis, and external Content Service) and aggregates the results 
     * into a single Response object.
     *
     * @param request The health check request.
     */
    private void checkAllComponentHealth(Request request) {
        boolean isAllHealthy = true;
        Map<String, Object> finalResponseMap = new HashMap<>();
        List<Map<String, Object>> responseList = new ArrayList<>();

        // 1. Cassandra Health Check
        try {
            // Attempt to read from a standard table (e.g., ROLE) to verify DB connectivity
            Util.DbInfo orgTypeDbInfo = Util.dbInfoMap.get(JsonKey.ROLE);
            
            // Fallback to a default keyspace if specific table info isn't available
            String keyspace = (orgTypeDbInfo != null) ? orgTypeDbInfo.getKeySpace() : 
                               ProjectUtil.getConfigValue(JsonKey.SUNBIRD_KEYSPACE);
            
            String table = (orgTypeDbInfo != null) ? orgTypeDbInfo.getTableName() : "user_org";

            cassandraOperation.getRecordsWithLimit(keyspace, table, null, null, 1, null);
            responseList.add(ProjectUtil.createCheckResponse(JsonKey.CASSANDRA_SERVICE, false, null));
        } catch (Exception e) {
            responseList.add(ProjectUtil.createCheckResponse(JsonKey.CASSANDRA_SERVICE, true, e));
            isAllHealthy = false;
            logger.error("HealthActor: Cassandra health check failed. Triggering reconnection...", e);
            org.sunbird.helper.CassandraConnectionMngrFactory.getInstance().reconnect();
        }

        // 2. Elasticsearch Health Check
        try {
            Future<Boolean> responseF = esUtil.healthCheck();
            boolean response = (boolean) ElasticSearchHelper.getResponseFromFuture(responseF);
            responseList.add(ProjectUtil.createCheckResponse(JsonKey.ES_SERVICE, !response, null));
            if (!response) {
                isAllHealthy = false;
            }
        } catch (Exception e) {
            responseList.add(ProjectUtil.createCheckResponse(JsonKey.ES_SERVICE, true, e));
            isAllHealthy = false;
            logger.error("HealthActor: Elasticsearch health check failed", e);
        }

        // 3. Redis Health Check
        try {
            boolean redisHealth = redisCacheUtil.checkConnection();
            responseList.add(ProjectUtil.createCheckResponse(JsonKey.REDIS_SERVICE, !redisHealth, null));
            if (!redisHealth) {
                isAllHealthy = false;
            }
        } catch (Exception e) {
            responseList.add(ProjectUtil.createCheckResponse(JsonKey.REDIS_SERVICE, true, e));
            isAllHealthy = false;
            logger.error("HealthActor: Redis health check failed", e);
        }

        // 4. Content Service (EKStep) Health Check
        try {
            if (checkContentServiceHealth()) {
                responseList.add(ProjectUtil.createCheckResponse(JsonKey.EKSTEP_SERVICE, false, null));
            } else {
                responseList.add(ProjectUtil.createCheckResponse(JsonKey.EKSTEP_SERVICE, true, null));
                isAllHealthy = false;
            }
        } catch (Exception e) {
            responseList.add(ProjectUtil.createCheckResponse(JsonKey.EKSTEP_SERVICE, true, e));
            isAllHealthy = false;
            logger.error("HealthActor: Content Service health check failed", e);
        }

        // Construct Final Response
        finalResponseMap.put(JsonKey.CHECKS, responseList);
        finalResponseMap.put(JsonKey.NAME, "Unified Lern Service Health Check");
        finalResponseMap.put(JsonKey.Healthy, isAllHealthy);

        Response response = new Response();
        response.getResult().put(JsonKey.RESPONSE, finalResponseMap);
        sender().tell(response, self());
    }

    /**
     * Verifies the connectivity and status of the Content Service.
     * It attempts a simple search operation as a heartbeat check.
     *
     * @return true if the Content Service is operational and returns a valid response, false otherwise.
     */
    private boolean checkContentServiceHealth() {
        try {
            String searchBaseUrl = ProjectUtil.getConfigValue(JsonKey.SEARCH_SERVICE_API_BASE_URL);
            String contentSearchUrl = PropertiesCache.getInstance().getProperty(JsonKey.EKSTEP_CONTENT_SEARCH_URL);

            if (StringUtils.isBlank(searchBaseUrl) || StringUtils.isBlank(contentSearchUrl)) {
                 logger.info("HealthActor: Content service URLs not configured, skipping check.");
                 return true; // Treat as healthy if not configured to avoid false alarms
            }

            String body = "{\"request\":{\"filters\":{\"identifier\":\"test\"}}}";
            Map<String, String> headers = new HashMap<>();

            // Set Authorization Header
            String authKey = System.getenv(JsonKey.EKSTEP_AUTHORIZATION);
            if (StringUtils.isBlank(authKey)) {
                authKey = PropertiesCache.getInstance().getProperty(JsonKey.EKSTEP_AUTHORIZATION);
            }

            headers.put(JsonKey.AUTHORIZATION, JsonKey.BEARER + authKey);
            headers.put(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON);
            headers.put(HttpHeaders.ACCEPT_ENCODING, "UTF-8");

            String response = HttpUtil.sendPostRequest(searchBaseUrl + contentSearchUrl, body, headers);
            return response != null && response.contains("OK");
        } catch (Exception e) {
            logger.error("HealthActor: Error checking Content Service health", e);
            return false;
        }
    }

    /**
     * Performs a Cassandra-only health check.
     * Returns the result for Cassandra connectivity.
     */
    private void checkCassandraHealth() {
        Map<String, Object> finalResponseMap = new HashMap<>();
        List<Map<String, Object>> responseList = new ArrayList<>();
        boolean isHealthy = true;

        try {
            Util.DbInfo orgTypeDbInfo = Util.dbInfoMap.get(JsonKey.ROLE);
            String keyspace = (orgTypeDbInfo != null) ? orgTypeDbInfo.getKeySpace() :
                               ProjectUtil.getConfigValue(JsonKey.SUNBIRD_KEYSPACE);
            String table = (orgTypeDbInfo != null) ? orgTypeDbInfo.getTableName() : "user_org";

            cassandraOperation.getRecordsWithLimit(keyspace, table, null, null, 1, null);
            responseList.add(ProjectUtil.createCheckResponse(JsonKey.CASSANDRA_SERVICE, false, null));
        } catch (Exception e) {
            responseList.add(ProjectUtil.createCheckResponse(JsonKey.CASSANDRA_SERVICE, true, e));
            isHealthy = false;
            logger.error("HealthActor:checkCassandraHealth: Cassandra health check failed", e);
        }

        finalResponseMap.put(JsonKey.CHECKS, responseList);
        finalResponseMap.put(JsonKey.NAME, "Cassandra Health Check");
        finalResponseMap.put(JsonKey.Healthy, isHealthy);

        Response response = new Response();
        response.getResult().put(JsonKey.RESPONSE, finalResponseMap);
        sender().tell(response, self());
    }

    /**
     * Performs an Elasticsearch-only health check.
     * Returns the result for Elasticsearch connectivity.
     */
    private void checkEsHealth() {
        Map<String, Object> finalResponseMap = new HashMap<>();
        List<Map<String, Object>> responseList = new ArrayList<>();
        boolean isHealthy = true;

        try {
            Future<Boolean> responseF = esUtil.healthCheck();
            boolean response = (boolean) ElasticSearchHelper.getResponseFromFuture(responseF);
            responseList.add(ProjectUtil.createCheckResponse(JsonKey.ES_SERVICE, !response, null));
            if (!response) {
                isHealthy = false;
            }
        } catch (Exception e) {
            responseList.add(ProjectUtil.createCheckResponse(JsonKey.ES_SERVICE, true, e));
            isHealthy = false;
            logger.error("HealthActor:checkEsHealth: Elasticsearch health check failed", e);
        }

        finalResponseMap.put(JsonKey.CHECKS, responseList);
        finalResponseMap.put(JsonKey.NAME, "Elasticsearch Health Check");
        finalResponseMap.put(JsonKey.Healthy, isHealthy);

        Response response = new Response();
        response.getResult().put(JsonKey.RESPONSE, finalResponseMap);
        sender().tell(response, self());
    }

    /**
     * Performs an actor-only health check.
     * Simply confirms that the actor is responsive (by virtue of handling this request).
     */
    private void checkActorHealth() {
        Map<String, Object> finalResponseMap = new HashMap<>();
        List<Map<String, Object>> responseList = new ArrayList<>();

        responseList.add(ProjectUtil.createCheckResponse(JsonKey.ACTOR_SERVICE, false, null));

        finalResponseMap.put(JsonKey.CHECKS, responseList);
        finalResponseMap.put(JsonKey.NAME, "Actor Health Check");
        finalResponseMap.put(JsonKey.Healthy, true);

        Response response = new Response();
        response.getResult().put(JsonKey.RESPONSE, finalResponseMap);
        sender().tell(response, self());
    }

    /**
     * Performs a Content Service (EKStep)-only health check.
     * Returns the result for Content Service connectivity.
     */
    private void checkEkStepHealth() {
        Map<String, Object> finalResponseMap = new HashMap<>();
        List<Map<String, Object>> responseList = new ArrayList<>();
        boolean isHealthy = true;

        try {
            if (checkContentServiceHealth()) {
                responseList.add(ProjectUtil.createCheckResponse(JsonKey.EKSTEP_SERVICE, false, null));
            } else {
                responseList.add(ProjectUtil.createCheckResponse(JsonKey.EKSTEP_SERVICE, true, null));
                isHealthy = false;
            }
        } catch (Exception e) {
            responseList.add(ProjectUtil.createCheckResponse(JsonKey.EKSTEP_SERVICE, true, e));
            isHealthy = false;
            logger.error("HealthActor:checkEkStepHealth: Content Service health check failed", e);
        }

        finalResponseMap.put(JsonKey.CHECKS, responseList);
        finalResponseMap.put(JsonKey.NAME, "Content Service (EKStep) Health Check");
        finalResponseMap.put(JsonKey.Healthy, isHealthy);

        Response response = new Response();
        response.getResult().put(JsonKey.RESPONSE, finalResponseMap);
        sender().tell(response, self());
    }
}
