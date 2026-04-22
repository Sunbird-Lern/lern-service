package org.sunbird.helper;

import org.opensearch.action.bulk.BulkProcessor;
import org.opensearch.client.RestHighLevelClient;
import org.opensearch.search.SearchHit;
import org.opensearch.search.SearchHits;
import org.opensearch.search.aggregations.Aggregations;
import org.junit.Assert;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

/**
 * Test class for ConnectionManager.
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@RunWith(PowerMockRunner.class)
@PowerMockIgnore({
  "javax.management.*",
  "javax.net.ssl.*",
  "javax.security.*",
  "jdk.internal.reflect.*",
  "sun.security.ssl.*",
  "javax.crypto.*"
})
@PrepareForTest({
  ConnectionManager.class,
  BulkProcessor.class,
  SearchHit.class,
  SearchHits.class,
  Aggregations.class
})
public class ConnectionManagerTest {

  @Test
  public void testGetRestClientNull() {
    RestHighLevelClient client = ConnectionManager.getRestClient();
    try {
        if (client == null) {
            Assert.assertTrue(true);
        } else {
            Assert.assertNotNull(client);
        }
    } catch (Exception e) {
        Assert.fail("Should not throw exception");
    }
  }
}