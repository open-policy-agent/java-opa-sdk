package io.github.open_policy_agent.opa.plugins;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsParameters;
import com.sun.net.httpserver.HttpsServer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.GZIPOutputStream;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManagerFactory;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import io.github.open_policy_agent.opa.config.Config;
import io.github.open_policy_agent.opa.logging.Logger;
import io.github.open_policy_agent.opa.storage.InMem;
import io.github.open_policy_agent.opa.storage.Store;
import io.github.open_policy_agent.opa.tls.PemLoader;
import io.github.open_policy_agent.opa.tls.TlsFixtures;

/**
 * Integration test exercising an mTLS bundle download end-to-end: the JDK's {@link HttpsServer}
 * requires a valid client certificate, and the {@link BundlePlugin} must present one via the new
 * {@code credentials.client_tls} config.
 */
class BundlePluginMtlsIT {

  @TempDir static Path fixtureDir;
  static TlsFixtures fx;
  static byte[] bundleData;

  private HttpsServer server;
  private final AtomicReference<String> observedAuthHeader = new AtomicReference<>();

  @BeforeAll
  static void setupFixtures() throws Exception {
    fx = TlsFixtures.generate(fixtureDir);
    bundleData = createValidBundle();
  }

  @AfterEach
  void tearDown() {
    if (server != null) {
      server.stop(0);
    }
  }

  @Test
  void mtls_validClient_downloadsBundle() throws Exception {
    int port = startServer(true);

    Config.ServiceConfig service =
        new Config.ServiceConfig()
            .setName("test-service")
            .setUrl("https://localhost:" + port)
            .setTls(new Config.TlsConfig().setCaCert(fx.ca.toString()))
            .setCredentials(
                new Config.CredentialsConfig()
                    .setClientTls(
                        new Config.ClientTlsConfig()
                            .setCert(fx.client.toString())
                            .setPrivateKey(fx.clientKey.toString())));

    runBundleDownload(
        service,
        future -> assertDoesNotThrow(() -> future.get(10, TimeUnit.SECONDS)));
  }

  @Test
  void mtls_missingClientCert_failsHandshake() throws Exception {
    int port = startServer(true);

    Config.ServiceConfig service =
        new Config.ServiceConfig()
            .setName("test-service")
            .setUrl("https://localhost:" + port)
            .setTls(new Config.TlsConfig().setCaCert(fx.ca.toString()));
    // No credentials.client_tls → handshake should fail because server requires client auth.

    runBundleDownload(
        service,
        future -> {
          ExecutionException e =
              assertThrows(
                  ExecutionException.class, () -> future.get(10, TimeUnit.SECONDS));
          // Check the top-level cause, not the whole chain. TLS/network failures propagate
          // directly as IOException/SSLException (from HttpClient.send), while bundle-parse
          // failures wrap the underlying IOException in a RuntimeException thrown from
          // activateBundle. The direct-cause check distinguishes the two cleanly.
          Throwable cause = e.getCause();
          assertTrue(
              cause instanceof javax.net.ssl.SSLException
                  || cause instanceof java.io.IOException,
              "expected SSLException or IOException as direct cause but got: "
                  + (cause == null
                      ? "null"
                      : cause.getClass().getSimpleName() + ": " + cause.getMessage()));
        });
  }

  @Test
  void bearerToken_reachesBundleDownload() throws Exception {
    int port = startServer(false);

    Config.ServiceConfig service =
        new Config.ServiceConfig()
            .setName("test-service")
            .setUrl("https://localhost:" + port)
            .setTls(new Config.TlsConfig().setCaCert(fx.ca.toString()))
            .setCredentials(
                new Config.CredentialsConfig()
                    .setBearer(new Config.BearerConfig().setToken("let-me-in")));

    runBundleDownload(
        service,
        future -> {
          assertDoesNotThrow(() -> future.get(10, TimeUnit.SECONDS));
          assertTrue(
              "Bearer let-me-in".equals(observedAuthHeader.get()),
              "bundle download should carry the service's bearer token; saw: "
                  + observedAuthHeader.get());
        });
  }

  private void runBundleDownload(
      Config.ServiceConfig service, ThrowingConsumer<CompletableFuture<Void>> assertion)
      throws Exception {
    Config config = new Config();
    config.setServices(Collections.singletonMap(service.getName(), service));
    Config.BundleConfig bundleCfg =
        new Config.BundleConfig().setService(service.getName()).setResource("/bundles/authz.tar.gz");
    config.setBundles(Collections.singletonMap("authz", bundleCfg));

    Logger logger = new Logger.StandardLogger();
    Store store = new InMem();
    PluginManager manager =
        new PluginManager.Builder()
            .withId("it")
            .withStore(store)
            .withConfig(config)
            .withLogger(logger)
            .build();

    // Initialize services first so the bundle plugin can find the configured HttpClient.
    ServicePlugin servicePlugin = (ServicePlugin) new ServicePlugin().initialize(manager);
    manager.registerPlugin("services", servicePlugin);
    servicePlugin.start();

    BundlePlugin bundlePlugin = (BundlePlugin) new BundlePlugin().initialize(manager);
    manager.registerPlugin("bundles", bundlePlugin);
    bundlePlugin.start();

    // Wait on the bundle's own initialActivation future so the original exception (SSLException
    // etc.) is preserved. Going through the plugin-status listener would lose the cause.
    CompletableFuture<Void> activation = bundlePlugin.getBundle("authz").getInitialActivation();

    try {
      assertion.accept(activation);
    } finally {
      bundlePlugin.stop();
      servicePlugin.stop();
    }
  }

  private int startServer(boolean requireClientAuth) throws Exception {
    server = HttpsServer.create(new InetSocketAddress("localhost", 0), 0);
    server.setHttpsConfigurator(new Configurator(buildServerContext(), requireClientAuth));
    server.createContext(
        "/bundles/authz.tar.gz",
        exchange -> {
          observedAuthHeader.set(exchange.getRequestHeaders().getFirst("Authorization"));
          exchange.getResponseHeaders().add("Content-Type", "application/vnd.openpolicyagent.bundles");
          exchange.sendResponseHeaders(200, bundleData.length);
          exchange.getResponseBody().write(bundleData);
          exchange.close();
        });
    server.start();
    return server.getAddress().getPort();
  }

  private SSLContext buildServerContext() throws Exception {
    List<X509Certificate> chain = PemLoader.loadCertificates(fx.server);
    PrivateKey key = PemLoader.loadPrivateKey(fx.serverKey, null);

    char[] empty = new char[0];
    KeyStore ks = KeyStore.getInstance("PKCS12");
    ks.load(null, empty);
    ks.setKeyEntry("server", key, empty, chain.toArray(new X509Certificate[0]));

    KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
    kmf.init(ks, empty);

    List<X509Certificate> cas = PemLoader.loadCertificates(fx.ca);
    KeyStore ts = KeyStore.getInstance("PKCS12");
    ts.load(null, empty);
    for (int i = 0; i < cas.size(); i++) {
      ts.setCertificateEntry("ca-" + i, cas.get(i));
    }
    TrustManagerFactory tmf =
        TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
    tmf.init(ts);

    SSLContext ctx = SSLContext.getInstance("TLS");
    ctx.init(kmf.getKeyManagers(), tmf.getTrustManagers(), new SecureRandom());
    return ctx;
  }

  private static byte[] createValidBundle() throws IOException {
    ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
    try (GZIPOutputStream gzipOut = new GZIPOutputStream(byteOut);
        TarArchiveOutputStream tarOut = new TarArchiveOutputStream(gzipOut)) {
      String planJson =
          "{\"static\": {\"entrypoints\": [{\"path\": \"data/test/allow\", \"plan_compiled\": []}]}}";
      byte[] planBytes = planJson.getBytes();
      TarArchiveEntry plan = new TarArchiveEntry("plan.json");
      plan.setSize(planBytes.length);
      tarOut.putArchiveEntry(plan);
      tarOut.write(planBytes);
      tarOut.closeArchiveEntry();

      byte[] manifestBytes = "{\"revision\": \"test-123\"}".getBytes();
      TarArchiveEntry manifest = new TarArchiveEntry(".manifest");
      manifest.setSize(manifestBytes.length);
      tarOut.putArchiveEntry(manifest);
      tarOut.write(manifestBytes);
      tarOut.closeArchiveEntry();

      tarOut.finish();
    }
    return byteOut.toByteArray();
  }

  private static final class Configurator extends HttpsConfigurator {
    private final boolean requireClientAuth;

    Configurator(SSLContext ctx, boolean requireClientAuth) {
      super(ctx);
      this.requireClientAuth = requireClientAuth;
    }

    @Override
    public void configure(HttpsParameters params) {
      SSLContext ctx = getSSLContext();
      SSLEngine engine = ctx.createSSLEngine();
      SSLParameters sslParams = ctx.getDefaultSSLParameters();
      sslParams.setNeedClientAuth(requireClientAuth);
      sslParams.setCipherSuites(engine.getEnabledCipherSuites());
      sslParams.setProtocols(engine.getEnabledProtocols());
      params.setSSLParameters(sslParams);
    }
  }

  @FunctionalInterface
  private interface ThrowingConsumer<T> {
    void accept(T t) throws Exception;
  }

  // Silence an IDE warning; TimeoutException is thrown indirectly via future.get(..).
  @SuppressWarnings("unused")
  private static void touch(TimeoutException e) {}
}
