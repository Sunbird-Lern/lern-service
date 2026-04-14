package org.sunbird.helper;

import com.datastax.driver.core.Session;
import java.util.List;

/**
 * Interface for Cassandra connection manager. Implementations include Standalone and Embedded
 * Cassandra connection managers.
 */
public interface CassandraConnectionManager {

  /**
   * Initializes the Cassandra connection with the provided hosts.
   *
   * @param hosts Array of host IP addresses or hostnames.
   */
  void createConnection(String[] hosts);

  /**
   * Retrieves the Cassandra session object for the specified keyspace.
   *
   * @param keyspaceName The name of the keyspace.
   * @return The Cassandra Session object.
   */
  Session getSession(String keyspaceName);

  /**
   * Retrieves the list of table names in the specified keyspace.
   *
   * @param keyspaceName The name of the keyspace.
   * @return A list of table names.
   */
  List<String> getTableList(String keyspaceName);

  /**
   * Forces a reconnection to the cluster by re-resolving contact points.
   * Useful in environments like Kubernetes where pod IPs change.
   */
  void reconnect();

  /**
   * Checks if the Cassandra cluster is currently unreachable (down).
   *
   * @return true if cluster is unreachable.
   */
  boolean isClusterUnreachable();
}
