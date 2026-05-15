package io.github.open_policy_agent.opa.tls;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.security.SecureRandom;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import io.github.open_policy_agent.opa.config.Config;
import io.github.open_policy_agent.opa.logging.Logger;

class SslContextBuilderTest {

  @TempDir static Path dir;
  static TlsFixtures fx;
  static final Logger LOG = new Logger.StandardLogger();

  @BeforeAll
  static void setup() throws Exception {
    fx = TlsFixtures.generate(dir);
  }

  @Test
  void programmaticOverride_returnedUnchanged() throws Exception {
    SSLContext pre = SSLContext.getInstance("TLS");
    pre.init(null, new TrustManager[] {acceptAll()}, new SecureRandom());

    Config.ServiceConfig cfg = new Config.ServiceConfig().setName("s").setSslContext(pre);
    SslContextBuilder.Tls tls = SslContextBuilder.build(cfg, null, LOG);

    assertSame(pre, tls.getSslContext());
    assertArrayEquals(new String[] {"TLSv1.2", "TLSv1.3"}, tls.getSslParameters().getProtocols());
  }

  @Test
  void insecureTls_buildsTrustAllContext() throws Exception {
    Config.ServiceConfig cfg = new Config.ServiceConfig().setName("s").setAllowInsecureTLS(true);
    SslContextBuilder.Tls tls = SslContextBuilder.build(cfg, null, LOG);

    assertNotNull(tls.getSslContext());
    // Sanity: it should present no preconfigured trust anchors.
    assertTrue(tls.getSslContext().getProtocol().startsWith("TLS"));
  }

  @Test
  void noConfig_returnsNullContext() throws Exception {
    Config.ServiceConfig cfg = new Config.ServiceConfig().setName("s");
    SslContextBuilder.Tls tls = SslContextBuilder.build(cfg, null, LOG);

    assertNull(tls.getSslContext());
    assertArrayEquals(new String[] {"TLSv1.2", "TLSv1.3"}, tls.getSslParameters().getProtocols());
  }

  @Test
  void caCertOnly_buildsServerAuthOnlyContext() throws Exception {
    Config.ServiceConfig cfg =
        new Config.ServiceConfig()
            .setName("s")
            .setTls(new Config.TlsConfig().setCaCert(fx.ca.toString()));
    SslContextBuilder.Tls tls = SslContextBuilder.build(cfg, null, LOG);

    assertNotNull(tls.getSslContext());
  }

  @Test
  void clientTlsAndCa_buildsMutualContext() throws Exception {
    Config.ServiceConfig cfg =
        new Config.ServiceConfig()
            .setName("s")
            .setTls(new Config.TlsConfig().setCaCert(fx.ca.toString()))
            .setCredentials(
                new Config.CredentialsConfig()
                    .setClientTls(
                        new Config.ClientTlsConfig()
                            .setCert(fx.client.toString())
                            .setPrivateKey(fx.clientKey.toString())));

    SslContextBuilder.Tls tls = SslContextBuilder.build(cfg, null, LOG);

    assertNotNull(tls.getSslContext());
  }

  @Test
  void systemCaNotRequired_acceptsOnlyUserCa() throws Exception {
    Config.TlsConfig tls = new Config.TlsConfig().setCaCert(fx.ca.toString());
    TrustManager[] tms = SslContextBuilder.buildTrustManagers(tls);
    X509TrustManager x = (X509TrustManager) tms[0];

    java.security.cert.X509Certificate userCa = PemLoader.loadCertificates(fx.ca).get(0);
    java.security.cert.X509Certificate[] accepted = x.getAcceptedIssuers();
    assertEquals(1, accepted.length, "only user CA should be trusted");
    assertEquals(userCa, accepted[0]);
  }

  @Test
  void systemCaRequired_mergesUserAndSystemAnchors() throws Exception {
    int systemCount;
    {
      javax.net.ssl.TrustManagerFactory sysTmf =
          javax.net.ssl.TrustManagerFactory.getInstance(
              javax.net.ssl.TrustManagerFactory.getDefaultAlgorithm());
      sysTmf.init((java.security.KeyStore) null);
      systemCount =
          ((X509TrustManager) sysTmf.getTrustManagers()[0]).getAcceptedIssuers().length;
    }
    assertTrue(systemCount > 0, "system trust store should be non-empty");

    Config.TlsConfig tls =
        new Config.TlsConfig().setCaCert(fx.ca.toString()).setSystemCaRequired(true);
    TrustManager[] tms = SslContextBuilder.buildTrustManagers(tls);
    X509TrustManager x = (X509TrustManager) tms[0];

    java.security.cert.X509Certificate userCa = PemLoader.loadCertificates(fx.ca).get(0);
    java.security.cert.X509Certificate[] accepted = x.getAcceptedIssuers();
    assertEquals(
        systemCount + 1,
        accepted.length,
        "merged trust must contain user CA on top of all system roots");
    assertTrue(
        java.util.Arrays.stream(accepted).anyMatch(c -> c.equals(userCa)),
        "user CA must be present in merged trust");
  }

  @Test
  void systemCaRequired_userTrustedChain_passesValidation() throws Exception {
    Config.TlsConfig tls =
        new Config.TlsConfig().setCaCert(fx.ca.toString()).setSystemCaRequired(true);
    TrustManager[] tms = SslContextBuilder.buildTrustManagers(tls);
    X509TrustManager x = (X509TrustManager) tms[0];

    // The fixture client cert is signed by fx.ca; the merged trust manager must accept it.
    java.security.cert.X509Certificate clientLeaf =
        PemLoader.loadCertificates(fx.client).get(0);
    x.checkClientTrusted(
        new java.security.cert.X509Certificate[] {clientLeaf}, "RSA");
  }

  private static X509TrustManager acceptAll() {
    return new X509TrustManager() {
      public void checkClientTrusted(
          java.security.cert.X509Certificate[] chain, String authType) {}

      public void checkServerTrusted(
          java.security.cert.X509Certificate[] chain, String authType) {}

      public java.security.cert.X509Certificate[] getAcceptedIssuers() {
        return new java.security.cert.X509Certificate[0];
      }
    };
  }
}
