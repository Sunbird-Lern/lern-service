package org.sunbird.redis;

import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.redisson.api.RMap;
import org.redisson.api.RedissonClient;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@RunWith(PowerMockRunner.class)
@PrepareForTest({RedisConnectionManager.class, RedisCache.class})
public class RedisCacheTest {

    @Mock
    private RedissonClient mockClient;

    @Mock
    private RMap<String, String> mockMap;

    private RedisCache redisCache;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        PowerMockito.mockStatic(RedisConnectionManager.class);
        PowerMockito.when(RedisConnectionManager.getClient()).thenReturn(mockClient);
        
        when(mockClient.getMap(anyString())).thenReturn(mockMap);
        
        redisCache = new RedisCache();
    }

    @Test
    public void testRedisCacheConstructor_initializesWithClient() {
        assertNotNull("RedisCache should be initialized with a client", redisCache);
    }

    @Test
    public void testGet_withValidKey_returnsValue() {
        String mapName = "testMap";
        String key = "testKey";
        String expectedValue = "testValue";

        when(mockMap.get(key)).thenReturn(expectedValue);

        String result = redisCache.get(mapName, key);

        assertEquals("Should return the expected value", expectedValue, result);
        verify(mockClient, times(1)).getMap(mapName);
        verify(mockMap, times(1)).get(key);
    }

    @Test
    public void testGet_withNullKey_returnsNull() {
        String mapName = "testMap";
        String key = "nonExistentKey";

        when(mockMap.get(key)).thenReturn(null);

        String result = redisCache.get(mapName, key);

        assertNull("Should return null for non-existent key", result);
    }

    @Test
    public void testPut_withStringValue_succeeds() {
        String mapName = "testMap";
        String key = "testKey";
        String value = "testValue";

        when(mockMap.put(anyString(), anyString())).thenReturn(null);

        boolean result = redisCache.put(mapName, key, value);

        assertTrue("Put operation should succeed", result);
        verify(mockClient, times(1)).getMap(mapName);
    }

    @Test
    public void testClear_withValidMapName_succeeds() {
        String mapName = "testMap";

        when(mockMap.clear()).thenReturn(null);

        boolean result = redisCache.clear(mapName);

        assertTrue("Clear operation should succeed", result);
        verify(mockClient, times(1)).getMap(mapName);
        verify(mockMap, times(1)).clear();
    }

    @Test
    public void testClear_withException_returnsFalse() {
        String mapName = "testMap";

        when(mockClient.getMap(mapName)).thenThrow(new RuntimeException("Redis unavailable"));

        boolean result = redisCache.clear(mapName);

        assertFalse("Clear should return false on exception", result);
    }

    @Test
    public void testSetMapExpiry_withValidExpiry_returnsTrue() {
        String mapName = "testMap";
        long seconds = 3600;

        when(mockMap.expire(anyLong(), any())).thenReturn(true);

        boolean result = redisCache.setMapExpiry(mapName, seconds);

        assertTrue("SetMapExpiry should succeed", result);
        verify(mockMap, times(1)).expire(seconds, java.util.concurrent.TimeUnit.SECONDS);
    }
}
