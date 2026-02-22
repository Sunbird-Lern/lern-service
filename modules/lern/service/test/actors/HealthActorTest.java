package actors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import java.util.HashMap;

import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.actor.Props;
import org.apache.pekko.testkit.TestActorRef;
import org.apache.pekko.testkit.javadsl.TestKit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

import org.sunbird.cache.util.RedisCacheUtil;
import org.sunbird.cassandra.CassandraOperation;
import org.sunbird.common.ProjectUtil;
import org.sunbird.common.PropertiesCache;
import org.sunbird.common.factory.EsClientFactory;
import org.sunbird.common.inf.ElasticSearchService;
import org.sunbird.helper.ServiceFactory;
import org.sunbird.http.HttpUtil;
import org.sunbird.keys.JsonKey;
import org.sunbird.request.Request;
import org.sunbird.response.Response;
import org.sunbird.util.Util;

public class HealthActorTest {

    private ActorSystem system;
    
    @Before
    public void setup() {
        system = ActorSystem.create();
    }
    
    @After
    public void teardown() {
        TestKit.shutdownActorSystem(system);
        system = null;
    }

    @Test
    public void testHealthCheck_AllHealthy() {
        new TestKit(system) {{
            CassandraOperation cassandraMock = mock(CassandraOperation.class);
            when(cassandraMock.getRecordsWithLimit(any(), any(), any(), any(), anyInt(), any())).thenReturn(new Response());
            
            ElasticSearchService esMock = mock(ElasticSearchService.class);
            when(esMock.healthCheck()).thenReturn(scala.concurrent.Future$.MODULE$.successful(true));
            
            PropertiesCache propsMock = mock(PropertiesCache.class);
            when(propsMock.getProperty(anyString())).thenReturn("test-value");
            
            try (MockedStatic<ServiceFactory> sfMock = mockStatic(ServiceFactory.class);
                 MockedStatic<EsClientFactory> efMock = mockStatic(EsClientFactory.class);
                 MockedStatic<PropertiesCache> pcMock = mockStatic(PropertiesCache.class);
                 MockedStatic<HttpUtil> huMock = mockStatic(HttpUtil.class);
                 MockedStatic<ProjectUtil> puMock = mockStatic(ProjectUtil.class);
                 MockedConstruction<RedisCacheUtil> rcMock = mockConstruction(RedisCacheUtil.class, 
                     (mock, context) -> {
                         when(mock.checkConnection()).thenReturn(true);
                     })) {
                 
                sfMock.when(ServiceFactory::getInstance).thenReturn(cassandraMock);
                efMock.when(() -> EsClientFactory.getInstance(JsonKey.REST)).thenReturn(esMock);
                pcMock.when(PropertiesCache::getInstance).thenReturn(propsMock);
                
                huMock.when(() -> HttpUtil.sendPostRequest(anyString(), anyString(), anyMap())).thenReturn("OK");
                puMock.when(() -> ProjectUtil.getConfigValue(anyString())).thenReturn("test-config");
                puMock.when(() -> ProjectUtil.createCheckResponse(anyString(), anyBoolean(), any())).thenCallRealMethod();

                try (MockedStatic<org.sunbird.common.ElasticSearchHelper> esHelperMock = mockStatic(org.sunbird.common.ElasticSearchHelper.class)) {
                    esHelperMock.when(() -> org.sunbird.common.ElasticSearchHelper.getResponseFromFuture(any())).thenReturn(true);
                    
                    // Create actor within mocked scope so final fields are initialized with mocks
                    final TestActorRef<HealthActor> subject = TestActorRef.create(system, Props.create(HealthActor.class));

                    Request req = new Request();
                    req.setOperation("health");
                    
                    subject.tell(req, getRef());
                    
                    Response res = expectMsgClass(duration("10 seconds"), Response.class);
                    assertNotNull(res);
                    
                    Map<String, Object> result = (Map<String, Object>) res.getResult().get(JsonKey.RESPONSE);
                    assertTrue((boolean) result.get(JsonKey.Healthy));
                    assertEquals("Lern Service Health Check", result.get(JsonKey.NAME));
                }
            }
        }};
    }
    
    @Test
    public void testHealthCheck_Unhealthy() {
        new TestKit(system) {{
            CassandraOperation cassandraMock = mock(CassandraOperation.class);
            when(cassandraMock.getRecordsWithLimit(any(), any(), any(), any(), anyInt(), any())).thenThrow(new RuntimeException("DB down"));
            
            ElasticSearchService esMock = mock(ElasticSearchService.class);
            when(esMock.healthCheck()).thenReturn(scala.concurrent.Future$.MODULE$.successful(false));
            
            PropertiesCache propsMock = mock(PropertiesCache.class);
            when(propsMock.getProperty(anyString())).thenReturn("test-value");
            
            try (MockedStatic<ServiceFactory> sfMock = mockStatic(ServiceFactory.class);
                 MockedStatic<EsClientFactory> efMock = mockStatic(EsClientFactory.class);
                 MockedStatic<PropertiesCache> pcMock = mockStatic(PropertiesCache.class);
                 MockedStatic<HttpUtil> huMock = mockStatic(HttpUtil.class);
                 MockedStatic<ProjectUtil> puMock = mockStatic(ProjectUtil.class);
                 MockedConstruction<RedisCacheUtil> rcMock = mockConstruction(RedisCacheUtil.class, 
                     (mock, context) -> when(mock.checkConnection()).thenReturn(false))) {
                 
                sfMock.when(ServiceFactory::getInstance).thenReturn(cassandraMock);
                efMock.when(() -> EsClientFactory.getInstance(JsonKey.REST)).thenReturn(esMock);
                pcMock.when(PropertiesCache::getInstance).thenReturn(propsMock);
                
                huMock.when(() -> HttpUtil.sendPostRequest(anyString(), anyString(), anyMap())).thenReturn("ERROR");
                puMock.when(() -> ProjectUtil.getConfigValue(anyString())).thenReturn("test-config");
                puMock.when(() -> ProjectUtil.createCheckResponse(anyString(), anyBoolean(), any())).thenCallRealMethod();

                try (MockedStatic<org.sunbird.common.ElasticSearchHelper> esHelperMock = mockStatic(org.sunbird.common.ElasticSearchHelper.class)) {
                    esHelperMock.when(() -> org.sunbird.common.ElasticSearchHelper.getResponseFromFuture(any())).thenReturn(false);

                    final TestActorRef<HealthActor> subject = TestActorRef.create(system, Props.create(HealthActor.class));

                    Request req = new Request();
                    req.setOperation("health");
                    
                    subject.tell(req, getRef());
                    
                    Response res = expectMsgClass(duration("10 seconds"), Response.class);
                    assertNotNull(res);
                    
                    Map<String, Object> result = (Map<String, Object>) res.getResult().get(JsonKey.RESPONSE);
                    assertFalse((boolean) result.get(JsonKey.Healthy));
                    List<Map<String, Object>> checks = (List<Map<String, Object>>) result.get(JsonKey.CHECKS);
                    boolean foundError = false;
                    for(Map<String, Object> check : checks) {
                         if (check.get("err") != null && !((String)check.get("err")).isEmpty()) {
                              foundError = true;
                         }
                    }
                    assertTrue(foundError);
                }
            }
        }};
    }
}
