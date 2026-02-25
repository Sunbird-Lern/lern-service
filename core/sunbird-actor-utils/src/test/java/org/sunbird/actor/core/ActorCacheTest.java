package org.sunbird.actor.core;

import org.apache.pekko.actor.ActorRef;
import org.junit.Before;
import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;

/**
 * Test suite for ActorCache.
 * Covers: getActorCache(), getActorRef()
 */
public class ActorCacheTest {

  /**
   * Test that getActorCache() returns a non-null Map.
   */
  @Test
  public void testGetActorCacheReturnsNonNullMap() {
    Map<String, ActorRef> cache = ActorCache.getActorCache();
    assertNotNull("ActorCache should return a non-null map", cache);
  }

  /**
   * Test that getActorCache() returns the same Map instance on multiple calls.
   */
  @Test
  public void testGetActorCacheReturnsSameMapInstance() {
    Map<String, ActorRef> cache1 = ActorCache.getActorCache();
    Map<String, ActorRef> cache2 = ActorCache.getActorCache();
    assertSame("ActorCache should return the same map instance", cache1, cache2);
  }

  /**
   * Test that getActorRef() returns null for an unknown operation.
   */
  @Test
  public void testGetActorRefReturnsNullForUnknownKey() {
    ActorRef result = ActorCache.getActorRef("unknownOperation");
    assertNull("ActorCache should return null for unknown operation", result);
  }

  /**
   * Test that getActorRef() returns the actor after manual put into cache.
   */
  @Test
  public void testGetActorRefReturnsActorAfterManualPut() {
    // Setup: mock an ActorRef
    ActorRef mockActor = mock(ActorRef.class);
    String operation = "testOperation";

    // Action: put the mock actor into the cache
    ActorCache.getActorCache().put(operation, mockActor);

    // Assert: getActorRef should return the same mock actor
    ActorRef result = ActorCache.getActorRef(operation);
    assertSame("ActorCache should return the cached actor", mockActor, result);

    // Cleanup: remove from cache for test isolation
    ActorCache.getActorCache().remove(operation);
  }
}
