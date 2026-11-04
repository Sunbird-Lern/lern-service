package org.sunbird.db;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class PostgreSQLConnectionManagerTest {

  @Test
  public void buildJdbcUrl_noSslKeepsPlainUrl() {
    assertEquals(
        "jdbc:postgresql://h:5433/sunbird",
        PostgreSQLConnectionManager.buildJdbcUrl("h", "5433", "sunbird", null, null));
    assertEquals(
        "jdbc:postgresql://h:5433/sunbird",
        PostgreSQLConnectionManager.buildJdbcUrl("h", "5433", "sunbird", "", "/c/root.crt"));
  }

  @Test
  public void buildJdbcUrl_sslModeOnly() {
    assertEquals(
        "jdbc:postgresql://h:5433/sunbird?sslmode=require",
        PostgreSQLConnectionManager.buildJdbcUrl("h", "5433", "sunbird", "require", null));
  }

  @Test
  public void buildJdbcUrl_sslModeWithRootCert() {
    assertEquals(
        "jdbc:postgresql://h:5433/sunbird?sslmode=verify-full&sslrootcert=/c/root.crt",
        PostgreSQLConnectionManager.buildJdbcUrl(
            "h", "5433", "sunbird", "verify-full", "/c/root.crt"));
  }
}
