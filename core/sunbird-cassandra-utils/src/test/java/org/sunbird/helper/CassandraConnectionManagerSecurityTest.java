package org.sunbird.helper;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.PlainTextAuthProvider;
import java.net.URL;
import javax.net.ssl.SSLContext;
import org.junit.Test;

public class CassandraConnectionManagerSecurityTest {

  private static String resource(String name) throws Exception {
    URL url = CassandraConnectionManagerSecurityTest.class.getClassLoader().getResource(name);
    return new java.io.File(url.toURI()).getAbsolutePath();
  }

  private static Cluster.Builder builder() {
    return Cluster.builder().addContactPoint("127.0.0.1");
  }

  @Test
  public void applySecurity_defaultsLeaveBuilderUntouched() {
    Cluster.Builder b = builder();
    CassandraConnectionManagerImpl.applySecurity(b, null, null, false, null);

    assertNull(b.getConfiguration().getProtocolOptions().getSSLOptions());
    assertTrue(
        b.getConfiguration().getProtocolOptions().getAuthProvider()
            == com.datastax.driver.core.AuthProvider.NONE);
  }

  @Test
  public void applySecurity_blankUserSkipsCredentials() {
    Cluster.Builder b = builder();
    CassandraConnectionManagerImpl.applySecurity(b, "  ", "pwd", false, null);

    assertTrue(
        b.getConfiguration().getProtocolOptions().getAuthProvider()
            == com.datastax.driver.core.AuthProvider.NONE);
  }

  @Test
  public void applySecurity_setsCredentials() {
    Cluster.Builder b = builder();
    CassandraConnectionManagerImpl.applySecurity(b, "admin", "secret", false, null);

    assertTrue(
        b.getConfiguration().getProtocolOptions().getAuthProvider()
            instanceof PlainTextAuthProvider);
  }

  @Test
  public void applySecurity_setsSslOptions() throws Exception {
    Cluster.Builder b = builder();
    CassandraConnectionManagerImpl.applySecurity(b, null, null, true, resource("test-root.crt"));

    assertNotNull(b.getConfiguration().getProtocolOptions().getSSLOptions());
  }

  @Test(expected = IllegalStateException.class)
  public void applySecurity_sslWithoutCertFails() {
    CassandraConnectionManagerImpl.applySecurity(builder(), null, null, true, "");
  }

  @Test
  public void buildSslContext_loadsSingleCertificate() throws Exception {
    SSLContext ctx = CassandraConnectionManagerImpl.buildSslContext(resource("test-root.crt"));
    assertNotNull(ctx);
  }

  @Test
  public void buildSslContext_loadsCertificateBundle() throws Exception {
    SSLContext ctx = CassandraConnectionManagerImpl.buildSslContext(resource("test-bundle.crt"));
    assertNotNull(ctx);
  }

  @Test(expected = IllegalStateException.class)
  public void buildSslContext_missingFileFails() {
    CassandraConnectionManagerImpl.buildSslContext("/does/not/exist.crt");
  }

  @Test(expected = IllegalStateException.class)
  public void buildSslContext_blankPathFails() {
    CassandraConnectionManagerImpl.buildSslContext(null);
  }
}
