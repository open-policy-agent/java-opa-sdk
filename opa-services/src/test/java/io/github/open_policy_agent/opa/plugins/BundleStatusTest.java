package io.github.open_policy_agent.opa.plugins;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.open_policy_agent.opa.config.Config;
import io.github.open_policy_agent.opa.plugins.DiscoveryTestSupport.RecordingLogger;
import io.github.open_policy_agent.opa.storage.InMem;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;

class BundleStatusTest {

  @TempDir Path tempDir;

  @Test
  void aFailingActivationListenerLeavesTheBundleNotActivated() throws Exception {
    DiscoveryTestSupport.write(tempDir, "a.tar.gz", DiscoveryTestSupport.bundle("a", "rev-1"));
    final Config config =
        new Config()
            .setServices(
                Map.of(
                    "svc",
                    new Config.ServiceConfig().setName("svc").setUrl(tempDir.toUri().toString())))
            .setBundles(
                Map.of("a", new Config.BundleConfig().setService("svc").setResource("a.tar.gz")));
    final PluginManager manager =
        new PluginManager.Builder()
            .withId("test-opa")
            .withStore(new InMem())
            .withConfig(config)
            .withLogger(new RecordingLogger())
            .build();
    manager.registerBundleActivationListener(
        name -> {
          throw new IllegalStateException("listener failed");
        });

    final BundlePlugin plugin = (BundlePlugin) new BundlePlugin().initialize(manager);
    plugin.start();
    try {
      JsonNode status = manager.bundleStatuses().get("a").toJson();
      for (int i = 0; i < 50 && !status.has("code"); i++) {
        Thread.sleep(100);
        status = manager.bundleStatuses().get("a").toJson();
      }

      assertTrue(status.has("code"), status.toString());
      assertEquals("0001-01-01T00:00:00Z", status.path("last_successful_activation").asString());
    } finally {
      plugin.stop();
    }
  }

  private static final String REQUEST_TIMER = "timer_bundle_request_ns";
  private static final String LOAD_TIMER = "timer_rego_load_bundles_ns";

  private static Set<String> timers(BundleStatus status) {
    return timersOf(status.toJson());
  }

  private static Set<String> timersOf(JsonNode status) {
    final Set<String> names = new TreeSet<>();
    status.path("metrics").propertyNames().forEach(names::add);
    return names;
  }

  @Test
  void aNewPollKeepsThePreviousTimersUntilTheServerAnswers() {
    final BundleStatus status = new BundleStatus("a", true);
    status.request();
    status.responded();
    status.loadStarted();
    status.activated("rev-1");
    assertEquals(Set.of(REQUEST_TIMER, LOAD_TIMER), timers(status));

    status.request();
    assertEquals(Set.of(REQUEST_TIMER, LOAD_TIMER), timers(status));

    status.responded();
    assertEquals(Set.of(REQUEST_TIMER), timers(status));
  }

  @Test
  void activatingRecordsTheLoadTimerWithTheActivation() {
    final BundleStatus status = new BundleStatus("a", true);
    status.request();
    status.responded();
    status.loadStarted();

    status.activated("rev-1");

    assertEquals(Set.of(REQUEST_TIMER, LOAD_TIMER), timers(status));
  }

  @Test
  void aFailedLoadStillRecordsTheLoadTimer() {
    final BundleStatus status = new BundleStatus("a", true);
    status.request();
    status.responded();
    status.loadStarted();

    status.failed(new IllegalStateException("bad bundle"));

    assertEquals(Set.of(REQUEST_TIMER, LOAD_TIMER), timers(status));
  }

  @Test
  void aFailureThatNeverReachedTheServerHasNoTimers() {
    final BundleStatus status = new BundleStatus("a", true);
    status.request();

    status.failed(new IOException("service not found"));

    assertEquals(Set.of(), timers(status));
  }

  @Test
  void aFileBundleReportsBothTimers() throws Exception {
    DiscoveryTestSupport.write(tempDir, "a.tar.gz", DiscoveryTestSupport.bundle("a", "rev-1"));
    final Config config =
        new Config()
            .setServices(
                Map.of(
                    "svc",
                    new Config.ServiceConfig().setName("svc").setUrl(tempDir.toUri().toString())))
            .setBundles(
                Map.of("a", new Config.BundleConfig().setService("svc").setResource("a.tar.gz")));
    final PluginManager manager =
        new PluginManager.Builder()
            .withId("test-opa")
            .withStore(new InMem())
            .withConfig(config)
            .withLogger(new RecordingLogger())
            .build();

    final BundlePlugin plugin = (BundlePlugin) new BundlePlugin().initialize(manager);
    plugin.start();
    try {
      JsonNode status = manager.bundleStatuses().get("a").toJson();
      for (int i = 0; i < 50 && !status.path("metrics").has(LOAD_TIMER); i++) {
        Thread.sleep(100);
        status = manager.bundleStatuses().get("a").toJson();
      }

      assertEquals(Set.of(REQUEST_TIMER, LOAD_TIMER), timersOf(status));
    } finally {
      plugin.stop();
    }
  }

  @Test
  void aFailedPollAfterASuccessfulOneHasNoLoadTimer() {
    final BundleStatus status = new BundleStatus("a", true);
    status.request();
    status.responded();
    status.loadStarted();
    status.activated("rev-1");

    status.request();
    status.responded();
    status.failed(new BundleDownloader.HttpStatusException(500));

    assertEquals(Set.of(REQUEST_TIMER), timers(status));
  }

  @Test
  void timersMeasureTheTimeBetweenTheirMarks() throws Exception {
    final BundleStatus status = new BundleStatus("a", true);
    status.request();
    Thread.sleep(20);
    status.responded();
    status.loadStarted();
    Thread.sleep(20);
    status.activated("rev-1");

    final JsonNode metrics = status.toJson().path("metrics");

    assertTrue(metrics.path(REQUEST_TIMER).asLong() >= 20_000_000L, metrics.toString());
    assertTrue(metrics.path(LOAD_TIMER).asLong() >= 20_000_000L, metrics.toString());
  }
}
