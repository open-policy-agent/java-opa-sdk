package io.github.open_policy_agent.opa;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import io.github.open_policy_agent.opa.plugins.DiscoveryTestSupport;
import java.io.IOException;
import java.io.StringReader;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Pins the shape of the {@code discovery} and {@code bundles} entries in the status report: the
 * fields of OPA's bundle status, populated from what the downloaders actually saw. One loopback
 * server plays the control plane (bundles and discovery) and the status service.
 */
class OpaStatusShapeTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final String ZERO_TIME = "0001-01-01T00:00:00Z";
  private static final String RFC3339_UTC = "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?Z";
  private static final List<String> SUCCESS_TIMES =
      List.of("last_successful_request", "last_successful_download", "last_successful_activation");
  private static final List<String> ALL_TIMES =
      List.of("last_request", "last_successful_request", "last_successful_download", "last_successful_activation");
  private static final String POLL = "    polling:\n      min_delay_seconds: 1\n      max_delay_seconds: 1\n";
  private static final String DISCOVERY =
      "discovery:\n  service: svc\n  resource: discovery.tar.gz\n  polling:\n"
          + "    min_delay_seconds: 1\n    max_delay_seconds: 1\n";

  private final Map<String, byte[]> bodies = new ConcurrentHashMap<>();
  private final Map<String, Integer> failures = new ConcurrentHashMap<>();
  private final List<String> uploads = new CopyOnWriteArrayList<>();
  private HttpServer server;
  private Opa opa;

  @BeforeEach
  void startServer() throws IOException {
    server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
    server.createContext(
        "/",
        exchange -> {
          final String path = exchange.getRequestURI().getPath();
          final byte[] request = exchange.getRequestBody().readAllBytes();
          final Integer failure = failures.get(path);
          final byte[] body = bodies.get(path);
          if ("/status".equals(path)) {
            uploads.add(new String(request));
            exchange.sendResponseHeaders(200, -1);
          } else if (failure != null) {
            exchange.sendResponseHeaders(failure, -1);
          } else if (body != null) {
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
          } else {
            exchange.sendResponseHeaders(404, -1);
          }
          exchange.close();
        });
    server.start();
  }

  @AfterEach
  void stop() {
    if (opa != null) {
      opa.close();
    }
    server.stop(0);
  }

  // ---- fixtures ----

  private static byte[] bundle(String root, String revision) throws IOException {
    final Map<String, String> entries = new LinkedHashMap<>();
    entries.put("/data.json", "{}");
    entries.put(
        "/plan.json",
        "{\"static\":{\"strings\":[],\"files\":[]},\"plans\":{\"plans\":[{\"name\":\""
            + root
            + "/main\",\"blocks\":[]}]},\"funcs\":{\"funcs\":[]}}");
    entries.put("/.manifest", "{\"revision\":\"" + revision + "\",\"roots\":[\"" + root + "\"]}");
    return DiscoveryTestSupport.tarGz(entries);
  }

  private static byte[] discoveryBundle(String dataJson, String revision) throws IOException {
    final Map<String, String> entries = new LinkedHashMap<>();
    entries.put("data.json", dataJson);
    entries.put(".manifest", "{\"revision\":\"" + revision + "\"}");
    return DiscoveryTestSupport.tarGz(entries);
  }

  private static String bundleYaml(String name, String resource) {
    return "bundles:\n  " + name + ":\n    service: svc\n    resource: " + resource + "\n" + POLL;
  }

  private void start(String yaml, boolean waitForPlugins) {
    final String base =
        "services:\n  svc:\n    url: http://localhost:"
            + server.getAddress().getPort()
            + "\nstatus:\n  service: svc\n  min_delay_seconds: 1\n  max_delay_seconds: 1\n";
    opa =
        new Opa.Builder()
            .withConfig(new StringReader(base + yaml))
            .withWaitForPlugins(waitForPlugins)
            .build();
  }

  private JsonNode startWithBundleA(String revision) throws Exception {
    bodies.put("/bundles/a.tar.gz", bundle("a", revision));
    start(bundleYaml("a", "bundles/a.tar.gz"), false);
    return awaitBundle("a", OpaStatusShapeTest::activated);
  }

  private JsonNode startWithDiscovery(String bootYaml, String dataJson) throws Exception {
    bodies.put("/discovery.tar.gz", discoveryBundle(dataJson, "disc-1"));
    bodies.put("/bundles/a.tar.gz", bundle("a", "rev-1"));
    start(bootYaml + DISCOVERY + bundleYaml("a", "bundles/a.tar.gz"), true);
    return awaitDiscovery(OpaStatusShapeTest::activated);
  }

  // ---- reading the report ----

  private JsonNode awaitReport(Predicate<JsonNode> condition) throws Exception {
    final long deadline = System.currentTimeMillis() + 12000;
    while (System.currentTimeMillis() < deadline) {
      for (int i = uploads.size() - 1; i >= 0; i--) {
        final JsonNode report = MAPPER.readTree(uploads.get(i));
        if (condition.test(report)) {
          return report;
        }
      }
      Thread.sleep(100);
    }
    throw new AssertionError(
        "no matching status report; last: " + (uploads.isEmpty() ? "none" : uploads.get(uploads.size() - 1)));
  }

  private JsonNode awaitBundle(String name, Predicate<JsonNode> condition) throws Exception {
    return awaitReport(r -> condition.test(r.path("bundles").path(name))).path("bundles").path(name);
  }

  private JsonNode awaitDiscovery(Predicate<JsonNode> condition) throws Exception {
    return awaitReport(r -> condition.test(r.path("discovery"))).path("discovery");
  }

  private static boolean activated(JsonNode status) {
    return status.has("last_successful_activation")
        && !ZERO_TIME.equals(status.path("last_successful_activation").asString());
  }

  private static Instant time(JsonNode status, String field) {
    return Instant.parse(status.path(field).asString());
  }

  private static void assertRfc3339(JsonNode status, List<String> fields) {
    for (String field : fields) {
      assertTrue(status.path(field).asString().matches(RFC3339_UTC), field + ": " + status);
    }
  }

  // ---- discovery ----

  @Test
  void discoveryStatusCarriesNameRevisionTimestampsAndOverriddenKeys() throws Exception {
    final JsonNode discovery =
        startWithDiscovery(
            "default_decision: boot/decision\n", "{\"default_decision\":\"discovered/decision\"}");

    assertEquals("discovery", discovery.path("name").asString());
    assertEquals("disc-1", discovery.path("active_revision").asString());
    assertRfc3339(discovery, ALL_TIMES);
    assertEquals(
        "Keys in the discovered configuration overridden by boot configuration: default_decision",
        discovery.path("message").asString());
    assertFalse(discovery.has("code"), discovery.toString());
  }

  @Test
  void aRegoDiscoveryUpdateSetsTheErrorAndKeepsTheActiveRevision() throws Exception {
    final JsonNode before = startWithDiscovery("", "{}");

    final Map<String, String> entries = new LinkedHashMap<>();
    entries.put("data.json", "{}");
    entries.put("policy.rego", "package discovery\n");
    entries.put(".manifest", "{\"revision\":\"disc-2\"}");
    bodies.put("/discovery.tar.gz", DiscoveryTestSupport.tarGz(entries));
    final JsonNode after = awaitDiscovery(d -> d.has("code"));

    assertEquals("bundle_error", after.path("code").asString());
    assertTrue(after.path("message").asString().contains(".rego"), after.toString());
    assertEquals("disc-1", after.path("active_revision").asString());
    assertEquals(before.path("last_successful_activation"), after.path("last_successful_activation"));
  }

  // ---- bundles ----

  @Test
  void aBundleEntryHasOpaFieldsAndKeepsTheLegacyOnes() throws Exception {
    final JsonNode entry = startWithBundleA("rev-1");

    assertEquals("a", entry.path("name").asString());
    assertEquals("rev-1", entry.path("active_revision").asString());
    assertEquals("snapshot", entry.path("type").asString());
    assertEquals(bodies.get("/bundles/a.tar.gz").length, entry.path("size").asInt());
    assertRfc3339(entry, ALL_TIMES);
    assertEquals("rev-1", entry.path("revision").asString());
    assertTrue(entry.path("active").asBoolean());
    assertFalse(entry.has("code"), entry.toString());
  }

  @Test
  void aBundleWithoutARevisionOmitsActiveRevision() throws Exception {
    assertFalse(startWithBundleA("").has("active_revision"));
  }

  @Test
  void pollsAdvanceTheRequestAndSuccessTimestamps() throws Exception {
    final JsonNode first = startWithBundleA("rev-1");

    final JsonNode later =
        awaitBundle("a", b -> activated(b) && time(b, "last_request").isAfter(time(first, "last_request")));

    for (String field : SUCCESS_TIMES) {
      assertTrue(time(later, field).isAfter(time(first, field)), field + ": " + first + " -> " + later);
    }
  }

  @Test
  void aFailingDownloadSetsTheErrorAndDoesNotAdvanceTheSuccessTimestamps() throws Exception {
    final JsonNode ok = startWithBundleA("rev-1");

    failures.put("/bundles/a.tar.gz", 500);
    final JsonNode failed = awaitBundle("a", b -> b.has("code"));

    assertEquals("bundle_error", failed.path("code").asString());
    assertEquals(500, failed.path("http_code").asInt());
    assertTrue(failed.path("message").asString().contains("500"), failed.toString());
    assertFalse(failed.has("errors"), failed.toString());
    assertEquals("rev-1", failed.path("active_revision").asString());
    for (String field : SUCCESS_TIMES) {
      assertEquals(ok.path(field), failed.path(field), field);
    }
    assertTrue(time(failed, "last_request").isAfter(time(ok, "last_request")));

    failures.remove("/bundles/a.tar.gz");
    final JsonNode recovered = awaitBundle("a", b -> activated(b) && !b.has("code"));

    assertFalse(recovered.has("message"), recovered.toString());
    assertFalse(recovered.has("http_code"), recovered.toString());
  }

  @Test
  void zeroTimesAreEmittedLikeGoDoes() throws Exception {
    failures.put("/bundles/a.tar.gz", 500);
    start(bundleYaml("a", "bundles/a.tar.gz"), false);

    final JsonNode entry = awaitBundle("a", b -> b.has("last_request"));

    for (String field : SUCCESS_TIMES) {
      assertEquals(ZERO_TIME, entry.path(field).asString(), field + ": " + entry);
    }
    assertRfc3339(entry, List.of("last_request"));
    assertNotEquals(ZERO_TIME, entry.path("last_request").asString());
  }

  @Test
  void twoBundlesReportIndependently() throws Exception {
    bodies.put("/bundles/a.tar.gz", bundle("a", "rev-a"));
    failures.put("/bundles/b.tar.gz", 500);
    start(
        bundleYaml("a", "bundles/a.tar.gz") + "  b:\n    service: svc\n    resource: bundles/b.tar.gz\n" + POLL,
        false);

    final JsonNode bundles =
        awaitReport(r -> activated(r.path("bundles").path("a")) && r.path("bundles").path("b").has("code"))
            .path("bundles");

    assertEquals("rev-a", bundles.path("a").path("active_revision").asString());
    assertFalse(bundles.path("a").has("code"), bundles.toString());
    assertEquals("bundle_error", bundles.path("b").path("code").asString());
    assertEquals(ZERO_TIME, bundles.path("b").path("last_successful_activation").asString());
  }
}
