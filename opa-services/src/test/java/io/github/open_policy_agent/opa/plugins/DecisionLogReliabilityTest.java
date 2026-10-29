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
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
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
  private final List<String> requestBodies = new CopyOnWriteArrayList<>();
  private final Queue<Integer> scripted = new ConcurrentLinkedQueue<>();
  private volatile Long uploadSizeLimit;
  private volatile String resource = "/logs";
  private volatile Integer delaySeconds;
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
  private PluginManager manager;

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
          requestBodies.add(body);
          arrived.countDown();
          try {
            if (hold != null) {
              hold.await();
            }
            Thread.sleep(delayMillis);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
          final Integer next = scripted.poll();
          final int code = next != null ? next : status.get();
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
    final Config.DecisionLogsConfig logs = new Config.DecisionLogsConfig().setResource(resource);
    if (withService) {
      logs.setService("svc");
    } else {
      logs.setConsole(true);
    }
    if (bufferLimit != null || uploadSizeLimit != null || delaySeconds != null) {
      final Config.ReportingConfig reporting = new Config.ReportingConfig();
      if (delaySeconds != null) {
        reporting.setMinDelaySeconds(delaySeconds);
        reporting.setMaxDelaySeconds(delaySeconds);
      }
      if (bufferLimit != null) {
        reporting.setBufferSizeLimitEvents(bufferLimit);
      }
      if (uploadSizeLimit != null) {
        reporting.setUploadSizeLimitBytes(uploadSizeLimit);
      }
      logs.setReporting(reporting);
    }
    config.setDecisionLogs(logs);
    manager =
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

  private static int events(String body) {
    return MAPPER.readTree(body).size();
  }

  @Test
  void anUploadIsSplitIntoChunksOfAtMostTheUploadSizeLimit() throws Exception {
    uploadSizeLimit = 700L;
    startPlugin(null, 5, true);
    log("d1", "d2", "d3", "d4", "d5", "d6");

    plugin.flush();
    awaitAccepted(6);

    assertTrue(requestBodies.size() > 1, "expected several chunks, got " + requestBodies.size());
    for (String body : requestBodies) {
      assertTrue(body.length() <= 700 || events(body) == 1, "chunk of " + body.length() + " bytes");
    }
    assertEquals(List.of("d1", "d2", "d3", "d4", "d5", "d6"), acceptedIds());
  }

  @Test
  void aChunkThatFailsKeepsItAndEverythingAfterItInOrder() throws Exception {
    uploadSizeLimit = 700L;
    scripted.add(200);
    scripted.add(500);
    startPlugin(null, 5, true);
    log("d1", "d2", "d3", "d4", "d5", "d6");

    plugin.flush();
    settle();
    final List<String> firstChunk = acceptedIds();
    assertFalse(firstChunk.isEmpty());
    assertTrue(firstChunk.size() < 6);

    plugin.flush();
    awaitAccepted(6);

    assertEquals(List.of("d1", "d2", "d3", "d4", "d5", "d6"), acceptedIds());
  }

  @Test
  void reachingTheBufferLimitDoesNotBlockTheLoggingThread() throws Exception {
    delayMillis = 1500;
    startPlugin(2, 5, true);
    log("d1");

    final long start = System.nanoTime();
    log("d2");
    final long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

    assertTrue(elapsedMillis < 500, "logDecision blocked for " + elapsedMillis + "ms");
    awaitAccepted(2);
    assertEquals(List.of("d1", "d2"), acceptedIds());
  }

  @Test
  void anUploadThatThrowsKeepsTheEventsBuffered() {
    resource = "/bad path";
    startPlugin(null, 5, true);
    log("d1", "d2");

    plugin.flush();

    assertEquals(2, plugin.getDecisionLogs().pending());
  }

  @Test
  void aFailedUploadIsRetriedAfterABackoffInsteadOfTheNextFullInterval() throws Exception {
    status.set(500);
    delaySeconds = 4;
    startPlugin(null, 5, true);
    log("d1");

    assertTrue(arrived.await(8, TimeUnit.SECONDS), "the first flush never ran");
    final long failedAt = System.nanoTime();
    settle();
    status.set(200);
    awaitAccepted(1);

    final long retryMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - failedAt);
    assertEquals(List.of("d1"), acceptedIds());
    assertTrue(retryMillis < 3000, "retried after " + retryMillis + "ms");
  }

  @Test
  void retryDelaysDoubleFromOneSecondAndStopAtTheMaxDelay() {
    assertEquals(1, DecisionLogPlugin.retryDelaySeconds(0, 600));
    assertEquals(2, DecisionLogPlugin.retryDelaySeconds(1, 600));
    assertEquals(8, DecisionLogPlugin.retryDelaySeconds(3, 600));
    assertEquals(600, DecisionLogPlugin.retryDelaySeconds(30, 600));
    assertEquals(5, DecisionLogPlugin.retryDelaySeconds(10, 5));
  }

  @Test
  void aFullBufferThatCannotBeUploadedWarnsOnceNotPerDecision() {
    status.set(500);
    startPlugin(3, 5, true);

    log("d1", "d2", "d3", "d4", "d5", "d6", "d7", "d8", "d9", "d10");

    final long warnings = logger.lines().stream().filter(l -> l.startsWith("WARN") && l.contains("Dropped")).count();
    assertTrue(warnings <= 1, logger.lines()::toString);
    assertEquals(3, plugin.getDecisionLogs().pending());
  }

  @Test
  void aBatchIsKeptWhenSomethingOutsideTheUploadThrows() {
    startPlugin(null, 5, true);
    final ServicePlugin broken = Mockito.mock(ServicePlugin.class);
    Mockito.when(broken.getService("svc")).thenThrow(new IllegalStateException("boom"));
    manager.registerPlugin("services", broken);
    log("d1", "d2");

    plugin.flush();

    assertEquals(2, plugin.getDecisionLogs().pending());
  }
}
