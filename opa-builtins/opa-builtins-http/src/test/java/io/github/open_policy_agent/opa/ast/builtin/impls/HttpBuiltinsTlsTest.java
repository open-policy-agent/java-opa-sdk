package io.github.open_policy_agent.opa.ast.builtin.impls;

import static io.github.open_policy_agent.opa.ast.builtin.impls.HttpTestSupport.get;
import static io.github.open_policy_agent.opa.ast.builtin.impls.HttpTestSupport.intValue;
import static io.github.open_policy_agent.opa.ast.builtin.impls.HttpTestSupport.newContext;
import static io.github.open_policy_agent.opa.ast.builtin.impls.HttpTestSupport.obj;
import static io.github.open_policy_agent.opa.ast.builtin.impls.HttpTestSupport.respond;
import static io.github.open_policy_agent.opa.ast.builtin.impls.HttpTestSupport.send;
import static io.github.open_policy_agent.opa.ast.builtin.impls.HttpTestSupport.text;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsParameters;
import com.sun.net.httpserver.HttpsServer;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.concurrent.Executors;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManagerFactory;
import io.github.open_policy_agent.opa.ast.builtin.BuiltinError;
import io.github.open_policy_agent.opa.ast.types.RegoObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("http.send TLS options")
class HttpBuiltinsTlsTest {

  private static String resource(String name) throws IOException {
    try (InputStream in = HttpBuiltinsTlsTest.class.getResourceAsStream("/tls/" + name)) {
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  private static String resourcePath(String name) throws Exception {
    return Paths.get(HttpBuiltinsTlsTest.class.getResource("/tls/" + name).toURI()).toString();
  }

  /** An HTTPS server presenting the test server certificate, optionally demanding a client certificate. */
  private static final class TlsServer implements AutoCloseable {
    private final HttpsServer server;

    TlsServer(boolean requireClientCert) throws Exception {
      KeyStore keys = KeyStore.getInstance("PKCS12");
      try (InputStream in = HttpBuiltinsTlsTest.class.getResourceAsStream("/tls/server.p12")) {
        keys.load(in, "changeit".toCharArray());
      }
      KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
      kmf.init(keys, "changeit".toCharArray());

      KeyStore trusted = KeyStore.getInstance("PKCS12");
      trusted.load(null, null);
      trusted.setCertificateEntry(
          "ca", (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(HttpBuiltinsTlsTest.class.getResourceAsStream("/tls/ca.pem")));
      TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
      tmf.init(trusted);

      SSLContext context = SSLContext.getInstance("TLS");
      context.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);

      server = HttpsServer.create(new InetSocketAddress(0), 0);
      server.setHttpsConfigurator(
          new HttpsConfigurator(context) {
            @Override
            public void configure(HttpsParameters params) {
              SSLParameters parameters = context.getDefaultSSLParameters();
              parameters.setNeedClientAuth(requireClientCert);
              params.setSSLParameters(parameters);
            }
          });
      server.setExecutor(Executors.newCachedThreadPool());
      server.createContext("/", exchange -> respond(exchange, 200, "text/plain", "secure"));
      server.start();
    }

    String url() {
      return "https://127.0.0.1:" + server.getAddress().getPort() + "/";
    }

    @Override
    public void close() {
      server.stop(0);
    }
  }

  private static RegoObject request(String url, Object... extra) {
    Object[] kv = new Object[4 + extra.length];
    kv[0] = "method";
    kv[1] = "get";
    kv[2] = "url";
    kv[3] = url;
    System.arraycopy(extra, 0, kv, 4, extra.length);
    return obj(kv);
  }

  private static void assertSecure(String url, Object... tlsParameters) {
    assertEquals("secure", text(get(send(newContext(), request(url, tlsParameters)), "raw_body")));
  }

  @Test
  @DisplayName("rejects a server certificate the JDK does not trust")
  void untrustedServer() throws Exception {
    try (TlsServer server = new TlsServer(false)) {
      BuiltinError e = assertThrows(BuiltinError.class, () -> send(newContext(), request(server.url())));
      assertTrue(e.getMessage().contains("tls:"), e.getMessage());

      RegoObject withSystemCerts = request(server.url(), "tls_use_system_certs", true, "raise_error", false);
      assertEquals(0, intValue(get(send(newContext(), withSystemCerts), "status_code")));
    }
  }

  @Test
  @DisplayName("trusts a CA given inline, with escaped newlines, or as a file")
  void trustsConfiguredCa() throws Exception {
    try (TlsServer server = new TlsServer(false)) {
      assertSecure(server.url(), "tls_ca_cert", resource("ca.pem"));
      assertSecure(server.url(), "tls_ca_cert", resource("ca.pem").replace("\n", "\\n"));
      assertSecure(server.url(), "tls_ca_cert_file", resourcePath("ca.pem"));
      assertSecure(server.url(), "tls_ca_cert", resource("ca.pem"), "tls_use_system_certs", true);
    }
  }

  @Test
  @DisplayName("does not trust a server signed by a different CA")
  void wrongCa() throws Exception {
    try (TlsServer server = new TlsServer(false)) {
      RegoObject wrong = request(server.url(), "tls_ca_cert", resource("other-ca.pem"), "raise_error", false);
      RegoObject result = asObject(send(newContext(), wrong));
      assertEquals("eval_http_send_network_error", text(get(get(result, "error"), "code")));
    }
  }

  @Test
  @DisplayName("skips verification when asked to")
  void insecureSkipVerify() throws Exception {
    try (TlsServer server = new TlsServer(false)) {
      assertSecure(server.url(), "tls_insecure_skip_verify", true);
    }
  }

  @Test
  @DisplayName("presents a client certificate from PKCS#8, PKCS#1 and SEC1 keys")
  void clientCertificates() throws Exception {
    try (TlsServer server = new TlsServer(true)) {
      String ca = resource("ca.pem");
      for (String[] pair :
          new String[][] {
            {"client-rsa.pem", "client-rsa-pkcs8.key"},
            {"client-rsa.pem", "client-rsa-pkcs1.key"},
            {"client-ec.pem", "client-ec-pkcs8.key"},
            {"client-ec.pem", "client-ec-sec1.key"}
          }) {
        assertSecure(server.url(), "tls_ca_cert", ca, "tls_client_cert", resource(pair[0]), "tls_client_key", resource(pair[1]));
        assertSecure(
            server.url(), "tls_ca_cert", ca, "tls_client_cert_file", resourcePath(pair[0]), "tls_client_key_file", resourcePath(pair[1]));
      }
    }
  }

  @Test
  @DisplayName("fails the handshake when the server wants a client certificate and none is sent")
  void missingClientCertificate() throws Exception {
    try (TlsServer server = new TlsServer(true)) {
      RegoObject noCert = request(server.url(), "tls_ca_cert", resource("ca.pem"), "raise_error", false);
      assertEquals(0, intValue(get(send(newContext(), noCert), "status_code")));
    }
  }

  @Test
  @DisplayName("reports unusable TLS material")
  void badTlsMaterial() throws Exception {
    String url = "https://127.0.0.1:1/";
    assertMessage("could not append certificates", request(url, "tls_ca_cert", "not a certificate"));
    assertMessage("open /does/not/exist.pem: no such file or directory", request(url, "tls_ca_cert_file", "/does/not/exist.pem"));
    assertMessage("could not append CA certificates from \"" + resourcePath("README.md") + "\"", request(url, "tls_ca_cert_file", resourcePath("README.md")));
    assertMessage("open /does/not/exist.pem: no such file or directory", request(url, "tls_client_cert_file", "/does/not/exist.pem", "tls_client_key_file", "/does/not/exist.key"));
    assertMessage("tls: failed to find any PEM data in certificate input", request(url, "tls_client_cert", "junk", "tls_client_key", "junk"));
    assertMessage(
        "tls: failed to find any PEM data in key input", request(url, "tls_client_cert", resource("client-rsa.pem"), "tls_client_key", "junk"));
    assertMessage("could not add CA certificates from envvar \"OPA_SDK_TEST_SURELY_UNSET\"", request(url, "tls_ca_cert_env_variable", "OPA_SDK_TEST_SURELY_UNSET"));
  }

  private static void assertMessage(String expected, RegoObject request) {
    BuiltinError e = assertThrows(BuiltinError.class, () -> send(newContext(), request));
    assertTrue(e.getMessage().contains(expected), "expected <" + expected + "> in <" + e.getMessage() + ">");
  }

  private static RegoObject asObject(io.github.open_policy_agent.opa.ast.types.RegoValue value) {
    return (RegoObject) value;
  }
}
