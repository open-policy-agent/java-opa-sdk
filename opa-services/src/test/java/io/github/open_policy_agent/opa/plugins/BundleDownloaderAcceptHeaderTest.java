package io.github.open_policy_agent.opa.plugins;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPOutputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import io.github.open_policy_agent.opa.config.Config;
import io.github.open_policy_agent.opa.logging.Logger;
import io.github.open_policy_agent.opa.storage.InMem;

/**
 * Tests the {@code Accept} header sent on bundle downloads: the q-weighted IR-first default, and
 * the operator's ability to override it via a service's {@code headers}.
 */
class BundleDownloaderAcceptHeaderTest {

  private HttpServer server;
  private ServicePlugin servicePlugin;

  /** Request headers seen by the test server, one entry per request, in arrival order. */
  private final List<Map<String, List<String>>> requests = new CopyOnWriteArrayList<>();

  @AfterEach
  void tearDown() {
    if (servicePlugin != null) {
      servicePlugin.stop();
    }
    if (server != null) {
      server.stop(0);
    }
  }

  @Test
  void defaultAcceptHeader_prefersIrWithQWeightedFallbacks() {
    assertEquals(
        "application/vnd.openpolicyagent.bundle.ir.v1+gzip;q=1.0, "
            + "application/vnd.openpolicyagent.bundles;q=0.9, "
            + "application/gzip;q=0.8, "
            + "*/*;q=0.1",
        BundleDownloader.DEFAULT_ACCEPT_HEADER);
  }

  @Test
  void download_sendsDefaultAcceptHeader() throws Exception {
    download(null);

    assertEquals(
        Collections.singletonList(BundleDownloader.DEFAULT_ACCEPT_HEADER),
        accept(0),
        "expected exactly one Accept header carrying the default");
  }

  @Test
  void download_serviceHeaderAccept_overridesDefault() throws Exception {
    download(Collections.singletonMap("Accept", "application/gzip"));

    assertEquals(Collections.singletonList("application/gzip"), accept(0));
  }

  @Test
  void download_serviceHeaderLowercaseAccept_overridesDefault() throws Exception {
    // HTTP header names are case-insensitive, so a lowercase override must replace — not
    // duplicate — the default.
    download(Collections.singletonMap("accept", "application/gzip"));

    assertEquals(Collections.singletonList("application/gzip"), accept(0));
  }

  @Test
  void download_unrelatedServiceHeader_keepsDefaultAccept() throws Exception {
    download(Collections.singletonMap("X-Custom", "custom-value"));

    assertEquals(Collections.singletonList(BundleDownloader.DEFAULT_ACCEPT_HEADER), accept(0));
    assertEquals(
        Collections.singletonList("custom-value"), requests.get(0).get("X-Custom"));
  }

  @Test
  void download_conditionalRequest_sendsAcceptAlongsideIfNoneMatch() throws Exception {
    // Second poll carries If-None-Match from the first response's ETag; Accept must survive.
    Downloader downloader = download(null);
    downloader.downloadBundle().get(10, TimeUnit.SECONDS);

    assertEquals(2, requests.size(), "expected a second, conditional request");
    assertEquals(Collections.singletonList("\"rev-1\""), requests.get(1).get("If-None-Match"));
    assertEquals(Collections.singletonList(BundleDownloader.DEFAULT_ACCEPT_HEADER), accept(1));
  }

  @Test
  void download_irContentType_isAccepted() throws Exception {
    // The IR type the default Accept asks for must also be allowed on the way back, or a server
    // that honors the preference would be rejected as an unexpected Content-Type.
    Downloader downloader = download(null);

    assertTrue(downloader.activated, "IR-typed bundle response should have been activated");
  }

  private List<String> accept(int requestIndex) {
    return requests.get(requestIndex).get("Accept");
  }

  /**
   * Runs a single download against a server that answers with an IR-typed bundle and an ETag,
   * then 304s any conditional follow-up. Returns the downloader so a test can poll again.
   */
  private Downloader download(Map<String, String> serviceHeaders) throws Exception {
    byte[] bundleData = createValidBundle();

    server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
    server.createContext("/bundle.tar.gz", exchange -> {
      Map<String, List<String>> seen = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
      exchange.getRequestHeaders().forEach((k, v) -> seen.put(k, new ArrayList<>(v)));
      requests.add(seen);

      if (exchange.getRequestHeaders().containsKey("If-None-Match")) {
        exchange.sendResponseHeaders(304, -1);
        exchange.close();
        return;
      }

      exchange.getResponseHeaders()
          .add("Content-Type", "application/vnd.openpolicyagent.bundle.ir.v1+gzip");
      exchange.getResponseHeaders().add("ETag", "\"rev-1\"");
      exchange.sendResponseHeaders(200, bundleData.length);
      exchange.getResponseBody().write(bundleData);
      exchange.close();
    });
    server.start();

    Config.ServiceConfig serviceConfig =
        new Config.ServiceConfig()
            .setName("test-service")
            .setUrl("http://localhost:" + server.getAddress().getPort());
    serviceConfig.setHeaders(serviceHeaders);

    Config config = new Config();
    config.setServices(Collections.singletonMap("test-service", serviceConfig));

    PluginManager manager =
        new PluginManager.Builder()
            .withId("test")
            .withStore(new InMem())
            .withConfig(config)
            .withLogger(new Logger.StandardLogger())
            .build();

    servicePlugin = (ServicePlugin) new ServicePlugin().initialize(manager);
    ServicePlugin.Service svc = servicePlugin.getService("test-service");
    assertNotNull(svc, "service should have been initialized");

    Downloader downloader = new Downloader(manager, svc);
    downloader.setService("test-service").setResource("/bundle.tar.gz");
    downloader.downloadBundle().get(10, TimeUnit.SECONDS);

    return downloader;
  }

  /** Records that activation happened; the bundle contents are irrelevant to these tests. */
  private static final class Downloader extends BundleDownloader {
    private volatile boolean activated;

    Downloader(PluginManager manager, ServicePlugin.Service authService) {
      super("authz", manager, authService);
    }

    @Override
    protected void activateBundle(byte[] bundleData) {
      this.activated = true;
    }
  }

  private static byte[] createValidBundle() throws IOException {
    ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
    try (GZIPOutputStream gzipOut = new GZIPOutputStream(byteOut);
        TarArchiveOutputStream tarOut = new TarArchiveOutputStream(gzipOut)) {
      String planJson =
          "{\"static\":{\"strings\":[],\"files\":[]},"
              + "\"plans\":{\"plans\":[{\"name\":\"main/main\",\"blocks\":[]}]},"
              + "\"funcs\":{\"funcs\":[]}}";
      byte[] planBytes = planJson.getBytes();
      TarArchiveEntry plan = new TarArchiveEntry("plan.json");
      plan.setSize(planBytes.length);
      tarOut.putArchiveEntry(plan);
      tarOut.write(planBytes);
      tarOut.closeArchiveEntry();
      tarOut.finish();
    }
    return byteOut.toByteArray();
  }
}
