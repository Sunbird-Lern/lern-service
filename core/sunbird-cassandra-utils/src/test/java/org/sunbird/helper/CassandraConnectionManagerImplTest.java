package org.sunbird.helper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.Host;
import com.datastax.driver.core.Metadata;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import org.junit.Before;
import org.junit.Test;

public class CassandraConnectionManagerImplTest {

  private CassandraConnectionManagerImpl connectionManager;

  @Before
  public void setUp() {
    connectionManager = new CassandraConnectionManagerImpl();
  }

  private void setStaticField(String fieldName, Object value) throws Exception {
    Field field = CassandraConnectionManagerImpl.class.getDeclaredField(fieldName);
    field.setAccessible(true);
    field.set(null, value);
  }

  private Object getStaticField(String fieldName) throws Exception {
    Field field = CassandraConnectionManagerImpl.class.getDeclaredField(fieldName);
    field.setAccessible(true);
    return field.get(null);
  }

  @Test
  public void testIsClusterUnreachable_NullCluster() throws Exception {
    setStaticField("cluster", null);
    assertTrue(connectionManager.isClusterUnreachable());
  }

  @Test
  public void testIsClusterUnreachable_ClosedCluster() throws Exception {
    Cluster cluster = mock(Cluster.class);
    when(cluster.isClosed()).thenReturn(true);
    setStaticField("cluster", cluster);

    assertTrue(connectionManager.isClusterUnreachable());
  }

  @Test
  public void testIsClusterUnreachable_EmptyHosts() throws Exception {
    Cluster cluster = mock(Cluster.class);
    Metadata metadata = mock(Metadata.class);
    when(cluster.isClosed()).thenReturn(false);
    when(cluster.getMetadata()).thenReturn(metadata);
    when(metadata.getAllHosts()).thenReturn(new java.util.HashSet<>());
    
    setStaticField("cluster", cluster);

    assertFalse(connectionManager.isClusterUnreachable());
  }

  @Test
  public void testIsClusterUnreachable_AllHostsDown() throws Exception {
    Cluster cluster = mock(Cluster.class);
    Metadata metadata = mock(Metadata.class);
    Host host1 = mock(Host.class);
    Host host2 = mock(Host.class);

    when(cluster.isClosed()).thenReturn(false);
    when(cluster.getMetadata()).thenReturn(metadata);
    when(metadata.getAllHosts()).thenReturn(new java.util.HashSet<>(Arrays.asList(host1, host2)));
    
    when(host1.isUp()).thenReturn(false);
    when(host2.isUp()).thenReturn(false);
    
    setStaticField("cluster", cluster);

    assertTrue(connectionManager.isClusterUnreachable());
  }

  @Test
  public void testIsClusterUnreachable_OneHostUp() throws Exception {
    Cluster cluster = mock(Cluster.class);
    Metadata metadata = mock(Metadata.class);
    Host host1 = mock(Host.class);
    Host host2 = mock(Host.class);

    when(cluster.isClosed()).thenReturn(false);
    when(cluster.getMetadata()).thenReturn(metadata);
    when(metadata.getAllHosts()).thenReturn(new java.util.HashSet<>(Arrays.asList(host1, host2)));
    
    when(host1.isUp()).thenReturn(false);
    when(host2.isUp()).thenReturn(true);
    
    setStaticField("cluster", cluster);

    assertFalse(connectionManager.isClusterUnreachable());
  }

  @Test
  public void testReconnect_CooldownActive() throws Exception {
    // Set lastReconnectionTime to current time
    long now = System.currentTimeMillis();
    setStaticField("lastReconnectionTime", now);
    setStaticField("contactPoints", new String[]{"127.0.0.1"});
    
    long timeBefore = (long) getStaticField("lastReconnectionTime");
    
    // Call reconnect, it should skip due to cooldown (60 seconds)
    connectionManager.reconnect();
    
    long timeAfter = (long) getStaticField("lastReconnectionTime");
    
    // Time should remain exactly the same
    assertEquals(timeBefore, timeAfter);
  }
}
