package io.github.open_policy_agent.opa.plugins;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import io.github.open_policy_agent.opa.config.Config;
import io.github.open_policy_agent.opa.plugins.DiscoveryTestSupport.RecordingLogger;
import io.github.open_policy_agent.opa.storage.InMem;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Decision log delivery: the final batch is delivered on stop, and a batch that was not accepted
 * stays buffered for the next flush. A loopback server plays the decision log service.
 */
class DecisionLogReliabilityTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final RecordingLogger logger = new RecordingLogger();
  private final List<String> accepted = new CopyOnWriteArrayList<>();
  private final List<Integer> responses = new CopyOnWriteArrayList<>();
  private final AtomicInteger status = new AtomicInteger(200);
  private final AtomicInteger completed = new AtomicInteger();
  private final CountDownLatch arrived = new CountDownLatch(1);
  private volatile long delayMillis;
  private volatile CountDownLatch hold;

  private HttpServer server;
  private int port;
  private ServicePlugin services;
  private DecisionLogPlugin plugin;

  @BeforeEach
  void startServer() throws IOException {
    server = newServer(0);
    port = server.getAddress().getPort();
  }

  @AfterEach
  void tearDown() {
    if (hold != null) {
      hold.countDown();
    }
    if (plugin != null) {
      plugin.stop();
    }
    if (services != null) {
      services.stop();
    }
    server.stop(0);
  }

  private HttpServer newServer(int bindPort) throws IOException {
    final HttpServer created = HttpServer.create(new InetSocketAddress("localhost", bindPort), 0);
    created.createContext(
        "/logs",
        exchange -> {
          final String body = new String(exchange.getRequestBody().readAllBytes());
          arrived.countDown();
          try {
            if (hold != null) {
              hold.await();
            }
            Thread.sleep(delayMillis);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
          final int code = status.get();
          if (code / 100 == 2) {
            accepted.add(body);
          }
          responses.add(code);
          exchange.sendResponseHeaders(code, -1);
          exchange.close();
          completed.incrementAndGet();
        });
    created.start();
    return created;
  }

  private void startPlugin(Integer bufferLimit, int timeoutSeconds, boolean withService) {
    final Config config = new Config();
    config.setServices(
        Collections.singletonMap(
            "svc",
            new Config.ServiceConfig()
                .setName("svc")
                .setUrl("http://localhost:" + port)
                .setResponseHeaderTimeoutSeconds(timeoutSeconds)));
    final Config.DecisionLogsConfig logs = new Config.DecisionLogsConfig().setResource("/logs");
    if (withService) {
      logs.setService("svc");
    } else {
      logs.setConsole(true);
    }
    if (bufferLimit != null) {
      logs.setReporting(new Config.ReportingConfig().setBufferSizeLimitEvents(bufferLimit));
    }
    config.setDecisionLogs(logs);
    final PluginManager manager =
        new PluginManager.Builder()
            .withId("test-opa")
            .withStore(new InMem())
            .withConfig(config)
            .withLogger(logger)
            .build();
    services = (ServicePlugin) new ServicePlugin().initialize(manager);
    manager.registerPlugin("services", services);
    services.start();
    plugin = (DecisionLogPlugin) new DecisionLogPlugin().initialize(manager);
    manager.registerPlugin("decision_logs", plugin);
    plugin.start();
  }

  private void log(String... ids) {
    final JsonNode input = MAPPER.createObjectNode().put("user", "eve");
    final JsonNode result = MAPPER.createObjectNode().put("allow", true);
    for (String id : ids) {
      plugin.logDecision(id, input, result, "data.authz.allow", 0, null, null);
    }
  }

  /** The decision ids in the batches the server accepted, in arrival order. */
  private List<String> acceptedIds() {
    final List<String> ids = new ArrayList<>();
    for (String body : accepted) {
      for (JsonNode event : MAPPER.readTree(body)) {
        ids.add(event.path("decision_id").asString());
      }
    }
    return ids;
  }

  /** Waits until the server has stopped answering requests, so a status switch cannot race one. */
  private void settle() throws InterruptedException {
    int seen = -1;
    final long deadline = System.currentTimeMillis() + 5000;
    while (System.currentTimeMillis() < deadline && seen != responses.size()) {
      seen = responses.size();
      Thread.sleep(300);
    }
  }

  private void awaitAccepted(int events) throws InterruptedException {
    final long deadline = System.currentTimeMillis() + 5000;
    while (System.currentTimeMillis() < deadline && acceptedIds().size() < events) {
      Thread.sleep(50);
    }
  }

  // ---- stop() ----

  @Test
  void stopWaitsForTheFinalBatchToBeDelivered() {
    delayMillis = 500;
    startPlugin(null, 5, true);
    log("d1");

    plugin.stop();

    assertEquals(1, completed.get(), "the server must have answered before stop() returned");
    assertEquals(List.of("d1"), acceptedIds());
  }

  @Test
  void stopIsBoundedWhenTheServerNeverResponds() {
    hold = new CountDownLatch(1);
    startPlugin(null, 1, true);
    log("d1");

    final long start = System.nanoTime();
    plugin.stop();
    final long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

    assertTrue(elapsedMillis < 4000, "stop() took " + elapsedMillis + "ms");
    assertTrue(
        logger.lines().stream().anyMatch(line -> line.startsWith("WARN") && line.contains("decision log")),
        logger.lines()::toString);
  }

  @Test
  void stopWithoutAServiceStillReturnsAndLogsToTheConsole() {
    startPlugin(null, 5, false);
    log("d1");

    plugin.stop();

    assertTrue(logger.contains("Decision: "), logger.lines()::toString);
    assertTrue(accepted.isEmpty());
  }

  // ---- retry ----

  @Test
  void aFailedPeriodicUploadKeepsTheEventsForTheNextFlush() throws Exception {
    status.set(500);
    startPlugin(null, 5, true);
    log("d1", "d2", "d3");

    plugin.flush();
    settle();
    assertTrue(acceptedIds().isEmpty());

    status.set(200);
    plugin.flush();
    awaitAccepted(3);

    assertEquals(List.of("d1", "d2", "d3"), acceptedIds());
    plugin.flush();
    settle();
    assertEquals(2, responses.size(), "nothing is left to send after the retry succeeded");
  }

  @Test
  void aTransportFailureKeepsTheEventsForTheNextFlush() throws Exception {
    startPlugin(null, 2, true);
    server.stop(0);
    log("d1", "d2");

    plugin.flush();
    Thread.sleep(500);

    server = restartOn(port);
    plugin.flush();
    awaitAccepted(2);
    assertEquals(List.of("d1", "d2"), acceptedIds());
  }

  private HttpServer restartOn(int bindPort) throws Exception {
    IOException last = null;
    for (int attempt = 0; attempt < 20; attempt++) {
      try {
        return newServer(bindPort);
      } catch (IOException e) {
        last = e;
        Thread.sleep(100);
      }
    }
    throw last;
  }

  @Test
  void aFailedUploadIsNotLoggedAsSent() throws Exception {
    status.set(500);
    startPlugin(null, 5, true);
    log("d1");

    plugin.flush();
    settle();

    assertFalse(logger.contains("Sent 1 decision logs"), logger.lines()::toString);
    assertTrue(logger.contains("500"), logger.lines()::toString);
  }

  @Test
  void anAcceptedUploadIsLoggedAsSentAndEmptiesTheBuffer() throws Exception {
    startPlugin(null, 5, true);
    log("d1", "d2");

    plugin.flush();
    awaitAccepted(2);
    plugin.flush();
    settle();

    assertTrue(logger.contains("Sent 2 decision logs"), logger.lines()::toString);
    assertEquals(List.of("d1", "d2"), acceptedIds());
    assertEquals(1, responses.size());
  }

  @Test
  void theBufferLimitStillAppliesWhileUploadsKeepFailing() throws Exception {
    status.set(500);
    startPlugin(3, 5, true);
    log("d1", "d2", "d3", "d4", "d5", "d6", "d7", "d8", "d9", "d10");
    settle();

    status.set(200);
    plugin.flush();
    awaitAccepted(3);

    assertEquals(List.of("d8", "d9", "d10"), acceptedIds());
  }

  // ---- concurrency ----

  @Test
  void aFlushThatRacesAnotherFlushNeitherLosesNorDuplicatesEvents() throws Exception {
    hold = new CountDownLatch(1);
    startPlugin(null, 10, true);
    log("d1", "d2", "d3");
    final Thread first = new Thread(() -> plugin.flush());
    first.start();
    assertTrue(arrived.await(5, TimeUnit.SECONDS), "the first upload must reach the server");

    log("d4", "d5");
    final Thread second = new Thread(() -> plugin.flush());
    second.start();
    hold.countDown();
    first.join(10000);
    second.join(10000);
    plugin.stop();
    awaitAccepted(5);

    final List<String> ids = acceptedIds();
    Collections.sort(ids);
    assertEquals(List.of("d1", "d2", "d3", "d4", "d5"), ids);
  }
}
