package io.github.open_policy_agent.opa.plugins;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.GZIPOutputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import io.github.open_policy_agent.opa.config.Config;
import io.github.open_policy_agent.opa.logging.Logger;
import io.github.open_policy_agent.opa.storage.InMem;
import io.github.open_policy_agent.opa.storage.Store;

class BundleDownloaderSecurityTest {

  private HttpServer server;
  private Store store;
  private ExecutorService serverExecutor;
  private ScheduledExecutorService scheduler;

  @AfterEach
  void tearDown() {
    if (scheduler != null) {
      scheduler.shutdownNow();
    }
    if (server != null) {
      server.stop(0);
    }
    if (serverExecutor != null) {
      serverExecutor.shutdownNow();
    }
  }

  @Test
  void download_wrongContentType_fails() throws Exception {
    byte[] bundleData = createValidBundle();
    server = startServer(exchange -> {
      exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
      exchange.sendResponseHeaders(200, bundleData.length);
      exchange.getResponseBody().write(bundleData);
      exchange.close();
    });

    ExecutionException ex = assertThrows(ExecutionException.class,
        () -> runBundleDownload(server.getAddress().getPort(), 512 * 1024 * 1024L)
            .get(10, TimeUnit.SECONDS));
    assertTrue(ex.getCause().getMessage().contains("Content-Type"));
  }

  @Test
  void download_missingContentType_succeeds() throws Exception {
    byte[] bundleData = createValidBundle();
    server = startServer(exchange -> {
      exchange.sendResponseHeaders(200, bundleData.length);
      exchange.getResponseBody().write(bundleData);
      exchange.close();
    });

    runBundleDownload(server.getAddress().getPort(), 512 * 1024 * 1024L)
        .get(10, TimeUnit.SECONDS);
    assertNotNull(store.getBundles().get("authz"));
  }

  @Test
  void download_alternateContentType_succeeds() throws Exception {
    byte[] bundleData = createValidBundle();
    server = startServer(exchange -> {
      exchange.getResponseHeaders().add("Content-Type", "application/gzip");
      exchange.sendResponseHeaders(200, bundleData.length);
      exchange.getResponseBody().write(bundleData);
      exchange.close();
    });

    runBundleDownload(server.getAddress().getPort(), 512 * 1024 * 1024L)
        .get(10, TimeUnit.SECONDS);
    assertNotNull(store.getBundles().get("authz"));
  }

  @Test
  void download_contentTypeWithParametersAndCasing_succeeds() throws Exception {
    byte[] bundleData = createValidBundle();
    server = startServer(exchange -> {
      exchange.getResponseHeaders()
          .add("Content-Type", "Application/VND.OpenPolicyAgent.Bundles; charset=utf-8");
      exchange.sendResponseHeaders(200, bundleData.length);
      exchange.getResponseBody().write(bundleData);
      exchange.close();
    });

    runBundleDownload(server.getAddress().getPort(), 512 * 1024 * 1024L)
        .get(10, TimeUnit.SECONDS);
    assertNotNull(store.getBundles().get("authz"));
  }

  @Test
  void download_contentLengthExceedsLimit_rejectsUpfront() throws Exception {
    byte[] bundleData = createValidBundle();
    server = startServer(exchange -> {
      exchange.getResponseHeaders().add("Content-Type", "application/vnd.openpolicyagent.bundles");
      exchange.getResponseHeaders().add("Content-Length", String.valueOf(bundleData.length));
      exchange.sendResponseHeaders(200, bundleData.length);
      exchange.getResponseBody().write(bundleData);
      exchange.close();
    });

    long smallLimit = bundleData.length - 1;
    ExecutionException ex = assertThrows(ExecutionException.class,
        () -> runBundleDownload(server.getAddress().getPort(), smallLimit)
            .get(10, TimeUnit.SECONDS));
    assertTrue(ex.getCause().getMessage().contains("Content-Length"));
    assertTrue(ex.getCause().getMessage().contains("exceeds limit"));
  }

  @Test
  void download_bodyExceedsLimitMidStream_fails() throws Exception {
    byte[] bundleData = createValidBundle();
    server = startServer(exchange -> {
      exchange.getResponseHeaders().add("Content-Type", "application/vnd.openpolicyagent.bundles");
      exchange.sendResponseHeaders(200, bundleData.length);
      exchange.getResponseBody().write(bundleData);
      exchange.close();
    });

    long smallLimit = bundleData.length - 1;
    ExecutionException ex = assertThrows(ExecutionException.class,
        () -> runBundleDownload(server.getAddress().getPort(), smallLimit)
            .get(10, TimeUnit.SECONDS));
    assertTrue(ex.getCause().getMessage().contains("exceeds limit"));
  }

  @Test
  void download_validBundleWithinLimit_succeeds() throws Exception {
    byte[] bundleData = createValidBundle();
    server = startServer(exchange -> {
      exchange.getResponseHeaders().add("Content-Type", "application/vnd.openpolicyagent.bundles");
      exchange.sendResponseHeaders(200, bundleData.length);
      exchange.getResponseBody().write(bundleData);
      exchange.close();
    });

    runBundleDownload(server.getAddress().getPort(), 512 * 1024 * 1024L)
        .get(10, TimeUnit.SECONDS);
    assertNotNull(store.getBundles().get("authz"));
  }

  @Test
  void download_validBundleWithContentLengthWithinLimit_succeeds() throws Exception {
    byte[] bundleData = createValidBundle();
    server = startServer(exchange -> {
      exchange.getResponseHeaders().add("Content-Type", "application/vnd.openpolicyagent.bundles");
      exchange.getResponseHeaders().add("Content-Length", String.valueOf(bundleData.length));
      exchange.sendResponseHeaders(200, bundleData.length);
      exchange.getResponseBody().write(bundleData);
      exchange.close();
    });

    runBundleDownload(server.getAddress().getPort(), 512 * 1024 * 1024L)
        .get(10, TimeUnit.SECONDS);
    assertNotNull(store.getBundles().get("authz"));
  }

  @Test
  void download_notModified_succeeds() throws Exception {
    server = startServer(exchange -> {
      exchange.sendResponseHeaders(304, -1);
      exchange.close();
    });

    runBundleDownload(server.getAddress().getPort(), 512 * 1024 * 1024L)
        .get(10, TimeUnit.SECONDS);
    assertNull(store.getBundles().get("authz"));
  }

  @Test
  void download_serverError_fails() throws Exception {
    server = startServer(exchange -> {
      exchange.sendResponseHeaders(500, -1);
      exchange.close();
    });

    ExecutionException ex = assertThrows(ExecutionException.class,
        () -> runBundleDownload(server.getAddress().getPort(), 512 * 1024 * 1024L)
            .get(10, TimeUnit.SECONDS));
    assertTrue(ex.getCause().getMessage().contains("500"));
  }

  @Test
  void download_notFound_fails() throws Exception {
    server = startServer(exchange -> {
      exchange.sendResponseHeaders(404, -1);
      exchange.close();
    });

    ExecutionException ex = assertThrows(ExecutionException.class,
        () -> runBundleDownload(server.getAddress().getPort(), 512 * 1024 * 1024L)
            .get(10, TimeUnit.SECONDS));
    assertTrue(ex.getCause().getMessage().contains("404"));
  }

  @Test
  void download_maxSizeBytesAbove2GB_doesNotOOM() throws Exception {
    byte[] bundleData = createValidBundle();
    server = startServer(exchange -> {
      exchange.getResponseHeaders().add("Content-Type", "application/vnd.openpolicyagent.bundles");
      exchange.sendResponseHeaders(200, bundleData.length);
      exchange.getResponseBody().write(bundleData);
      exchange.close();
    });

    runBundleDownload(server.getAddress().getPort(), (long) Integer.MAX_VALUE + 1)
        .get(10, TimeUnit.SECONDS);
    assertNotNull(store.getBundles().get("authz"));
  }

  @Test
  void download_largeResponseExceedsLimit_failsFast() throws Exception {
    int chunkSize = 64 * 1024;  // 64 KB per chunk
    int totalChunks = 20;       // 1.28 MB total
    int totalSize = chunkSize * totalChunks;
    long limit = 10 * 1024;     // 10 KB limit

    server = startServer(exchange -> {
      exchange.getResponseHeaders().add("Content-Type", "application/vnd.openpolicyagent.bundles");
      exchange.sendResponseHeaders(200, totalSize);
      byte[] chunk = new byte[chunkSize];
      for (int i = 0; i < totalChunks; i++) {
        exchange.getResponseBody().write(chunk);
        exchange.getResponseBody().flush();
      }
      exchange.close();
    });

    ExecutionException ex = assertThrows(ExecutionException.class,
        () -> runBundleDownload(server.getAddress().getPort(), limit)
            .get(5, TimeUnit.SECONDS));
    assertTrue(ex.getCause().getMessage().contains("exceeds limit"));
  }

  @Test
  void download_connectionDroppedMidStream_fails() throws Exception {
    byte[] bundleData = createValidBundle();
    server = startServer(exchange -> {
      exchange.getResponseHeaders().add("Content-Type", "application/vnd.openpolicyagent.bundles");
      exchange.sendResponseHeaders(200, bundleData.length * 2L);
      exchange.getResponseBody().write(bundleData);
      exchange.getResponseBody().flush();
      exchange.close();
    });

    assertThrows(ExecutionException.class,
        () -> runBundleDownload(server.getAddress().getPort(), 512 * 1024 * 1024L)
            .get(10, TimeUnit.SECONDS));
  }

  private CompletableFuture<Void> runBundleDownload(int port, long maxSizeBytes) {
    Config config = new Config();
    Config.ServiceConfig service =
        new Config.ServiceConfig()
            .setName("test-service")
            .setUrl("http://localhost:" + port);
    config.setServices(Collections.singletonMap("test-service", service));
    Config.BundleConfig bundleCfg =
        new Config.BundleConfig()
            .setService("test-service")
            .setResource("/bundle.tar.gz")
            .setMaxSizeBytes(maxSizeBytes);
    config.setBundles(Collections.singletonMap("authz", bundleCfg));

    Logger logger = new Logger.StandardLogger();
    this.store = new InMem();
    PluginManager manager =
        new PluginManager.Builder()
            .withId("test")
            .withStore(store)
            .withConfig(config)
            .withLogger(logger)
            .build();

    BundlePlugin bundlePlugin = (BundlePlugin) new BundlePlugin().initialize(manager);
    manager.registerPlugin("bundles", bundlePlugin);
    bundlePlugin.start();

    return bundlePlugin.getBundle("authz").getInitialActivation()
        .whenComplete((v, t) -> bundlePlugin.stop());
  }

  @Test
  void download_chunkedBodyExceedsLimit_failsMidStream() throws Exception {
    int chunkSize = 64 * 1024;   // 64 KB per chunk
    int totalChunks = 20;        // 1.28 MB total
    long limit = 10 * 1024;      // 10 KB limit

    int port = startRawChunkedServer(chunkSize, totalChunks);

    ExecutionException ex = assertThrows(ExecutionException.class,
        () -> runBundleDownload(port, limit).get(10, TimeUnit.SECONDS));
    assertTrue(ex.getCause().getMessage().contains("exceeds limit"));
  }

  @Test
  void polling_serverAcceptsButNeverReplies_timesOutAndKeepsPolling() throws Exception {
    CountDownLatch connections = new CountDownLatch(2);
    ServerSocket silentServer = new ServerSocket(0);
    int port = silentServer.getLocalPort();

    Thread accepter = new Thread(() -> {
      try {
        while (!Thread.currentThread().isInterrupted()) {
          silentServer.accept();
          connections.countDown();
        }
      } catch (IOException ignored) {
      }
    });
    accepter.setDaemon(true);
    accepter.start();

    try {
      Config config = new Config();
      config.setServices(Collections.singletonMap("test-service",
          new Config.ServiceConfig()
              .setName("test-service")
              .setUrl("http://localhost:" + port)
              .setResponseHeaderTimeoutSeconds(1)));
      config.setBundles(Collections.singletonMap("authz",
          new Config.BundleConfig()
              .setService("test-service")
              .setResource("/bundle.tar.gz")
              .setPolling(
                  new Config.PollingConfig().setMinDelaySeconds(0).setMaxDelaySeconds(0))));

      Logger logger = new Logger.StandardLogger();
      PluginManager manager =
          new PluginManager.Builder()
              .withId("test")
              .withStore(new InMem())
              .withConfig(config)
              .withLogger(logger)
              .build();

      ServicePlugin servicePlugin = (ServicePlugin) new ServicePlugin().initialize(manager);
      manager.registerPlugin("services", servicePlugin);
      servicePlugin.start();

      BundlePlugin bundlePlugin = (BundlePlugin) new BundlePlugin().initialize(manager);
      manager.registerPlugin("bundles", bundlePlugin);
      try {
        bundlePlugin.start();

        assertTrue(
            connections.await(10, TimeUnit.SECONDS),
            "polling stopped after a stalled request — expected the request timeout to fail the "
                + "exchange so the poll chain retries");
      } finally {
        bundlePlugin.stop();
      }
    } finally {
      silentServer.close();
      accepter.interrupt();
    }
  }

  @Test
  void polling_slowActivationExceedingInterval_neverRunsConcurrently() throws Exception {
    byte[] bundleData = createValidBundle();

    serverExecutor = Executors.newFixedThreadPool(8);
    server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
    server.setExecutor(serverExecutor);
    server.createContext("/bundle.tar.gz", exchange -> {
      exchange.getResponseHeaders().add("Content-Type", "application/vnd.openpolicyagent.bundles");
      exchange.sendResponseHeaders(200, bundleData.length);
      exchange.getResponseBody().write(bundleData);
      exchange.close();
    });
    server.start();

    Config config = new Config();
    config.setServices(Collections.singletonMap("test-service",
        new Config.ServiceConfig()
            .setName("test-service")
            .setUrl("http://localhost:" + server.getAddress().getPort())));

    Logger logger = new Logger.StandardLogger();
    PluginManager manager =
        new PluginManager.Builder()
            .withId("test")
            .withStore(new InMem())
            .withConfig(config)
            .withLogger(logger)
            .build();

    ConcurrencyTrackingDownloader downloader = (ConcurrencyTrackingDownloader) new ConcurrencyTrackingDownloader("authz", manager)
        .setService("test-service")
        .setResource("/bundle.tar.gz")
        .setPolling(new Config.PollingConfig().setMinDelaySeconds(0).setMaxDelaySeconds(0));

    scheduler = BundleDownloader.newPollScheduler("concurrency-test");
    downloader.startPolling(scheduler);

    // Let many poll cycles run back-to-back.
    Thread.sleep(800);
    scheduler.shutdownNow();
    scheduler.awaitTermination(2, TimeUnit.SECONDS);

    assertTrue(
        downloader.activations.get() > 1,
        "expected multiple poll cycles to have run, but only " + downloader.activations.get()
            + " did");
    assertEquals(
        1,
        downloader.maxInFlight.get(),
        "activateBundle ran concurrently — max simultaneous activations was "
            + downloader.maxInFlight.get());
  }

  /**
   * A BundleDownloader whose activation is deliberately slow and records whether any two
   * activations ever overlap, so a test can assert that polling stays serialized.
   */
  private static final class ConcurrencyTrackingDownloader extends BundleDownloader {
    final AtomicInteger inFlight = new AtomicInteger();
    final AtomicInteger maxInFlight = new AtomicInteger();
    final AtomicInteger activations = new AtomicInteger();

    ConcurrencyTrackingDownloader(String name, PluginManager manager) {
      super(name, manager, null);
    }

    @Override
    protected void activateBundle(byte[] bundleData) {
      int now = inFlight.incrementAndGet();
      maxInFlight.accumulateAndGet(now, Math::max);
      try {
        Thread.sleep(50);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      } finally {
        inFlight.decrementAndGet();
        activations.incrementAndGet();
      }
    }
  }

  private int startRawChunkedServer(int chunkSize, int totalChunks) throws IOException {
    ServerSocket serverSocket = new ServerSocket(0);
    int port = serverSocket.getLocalPort();

    Thread serverThread = new Thread(() -> {
      try (Socket conn = serverSocket.accept()) {
        InputStream in = conn.getInputStream();
        StringBuilder req = new StringBuilder();
        int b;
        while ((b = in.read()) != -1) {
          req.append((char) b);
          if (req.toString().endsWith("\r\n\r\n")) break;
        }

        OutputStream out = conn.getOutputStream();
        out.write("HTTP/1.1 200 OK\r\n".getBytes());
        out.write("Content-Type: application/vnd.openpolicyagent.bundles\r\n".getBytes());
        out.write("Transfer-Encoding: chunked\r\n".getBytes());
        out.write("\r\n".getBytes());

        byte[] chunk = new byte[chunkSize];
        for (int i = 0; i < totalChunks; i++) {
          out.write((Integer.toHexString(chunk.length) + "\r\n").getBytes());
          out.write(chunk);
          out.write("\r\n".getBytes());
          out.flush();
        }
        out.write("0\r\n\r\n".getBytes());
        out.flush();
      } catch (IOException ignored) {
      } finally {
        try { serverSocket.close(); } catch (IOException ignored) {}
      }
    });
    serverThread.setDaemon(true);
    serverThread.start();
    return port;
  }

  private HttpServer startServer(HttpHandler handler) throws IOException {
    HttpServer httpServer = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
    httpServer.createContext("/bundle.tar.gz", handler);
    httpServer.start();
    return httpServer;
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
