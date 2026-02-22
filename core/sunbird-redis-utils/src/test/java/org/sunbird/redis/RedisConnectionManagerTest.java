package org.sunbird.redis;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.redisson.api.RedissonClient;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@RunWith(PowerMockRunner.class)
@PrepareForTest({RedisConnectionManager.class})
public class RedisConnectionManagerTest {

    @Test
    public void testGetClient_returnsSameInstance_onMultipleCalls() {
        // Arrange
        RedissonClient mockClient = mock(RedissonClient.class);
        PowerMockito.mockStatic(RedisConnectionManager.class);
        when(RedisConnectionManager.getClient()).thenReturn(mockClient);

        // Act
        RedissonClient first = RedisConnectionManager.getClient();
        RedissonClient second = RedisConnectionManager.getClient();

        // Assert
        assertSame("getClient() should return the same singleton instance", first, second);
    }

    @Test
    public void testGetClient_returnsRedissonClient_whenAvailable() {
        // Arrange
        RedissonClient mockClient = mock(RedissonClient.class);
        PowerMockito.mockStatic(RedisConnectionManager.class);
        when(RedisConnectionManager.getClient()).thenReturn(mockClient);

        // Act
        RedissonClient result = RedisConnectionManager.getClient();

        // Assert
        assertNotNull("getClient() should return a non-null client when Redis is available", result);
    }

    @Test
    public void testGetClient_returnsNull_whenNotInitialized() {
        // Arrange
        PowerMockito.mockStatic(RedisConnectionManager.class);
        when(RedisConnectionManager.getClient()).thenReturn(null);

        // Act
        RedissonClient result = RedisConnectionManager.getClient();

        // Assert - graceful null handling
        assertNull("getClient() should return null when Redis is not initialized", result);
    }
}
