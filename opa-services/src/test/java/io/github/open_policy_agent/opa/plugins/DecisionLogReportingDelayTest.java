package io.github.open_policy_agent.opa.plugins;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.open_policy_agent.opa.config.Config;
import io.github.open_policy_agent.opa.plugins.DiscoveryTestSupport.RecordingLogger;
import io.github.open_policy_agent.opa.storage.InMem;
import java.util.Collections;
import java.util.Set;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Pins OPA's {@code decision_logs.reporting.min_delay_seconds}/{@code max_delay_seconds}
 * semantics. The top-level {@code decision_logs.min_delay_seconds}/{@code max_delay_seconds} are a
 * Java-only extension kept as a deprecated fallback.
 *
 * <p>Resolved delays are observed through real 1s timers, as in {@link DecisionLogPluginTest}.
 */
class DecisionLogReportingDelayTest {

  private static final String FLUSHED = "Flushed 1 decision log events";

  private final RecordingLogger logger = new RecordingLogger();
  private final ObjectMapper mapper = new ObjectMapper();

  private static Config.DecisionLogsConfig logs() {
    return new Config.DecisionLogsConfig().setService("test-service");
  }

  /** Sets the deprecated top-level delays the way a config file does. */
  private static Config.DecisionLogsConfig topLevel(Integer min, Integer max) {
    return JsonMapper.shared()
        .readValue(
            "{\"service\":\"test-service\",\"min_delay_seconds\":"
                + min
                + ",\"max_delay_seconds\":"
                + max
                + "}",
            Config.DecisionLogsConfig.class);
  }

  private static Config.ReportingConfig reporting(Integer min, Integer max) {
    return new Config.ReportingConfig().setMinDelaySeconds(min).setMaxDelaySeconds(max);
  }

  private PluginManager manager(Config.DecisionLogsConfig decisionLogs) {
    final Config config = new Config();
    config.setServices(
        Collections.singletonMap(
            "test-service",
            new Config.ServiceConfig().setName("test-service").setUrl("https://example.test")));
    config.setDecisionLogs(decisionLogs);
    return new PluginManager.Builder()
        .withId("test-opa")
        .withStore(new InMem())
        .withConfig(config)
        .withLogger(logger)
        .build();
  }

  private Set<String> validate(Config.DecisionLogsConfig decisionLogs) {
    return new DecisionLogPlugin().validate(manager(decisionLogs));
  }

  private boolean flushesWithin(Config.DecisionLogsConfig decisionLogs, long millis)
      throws Exception {
    final DecisionLogPlugin plugin =
        (DecisionLogPlugin) new DecisionLogPlugin().initialize(manager(decisionLogs));
    plugin.start();
    try {
      final JsonNode input = mapper.createObjectNode().put("user", "eve");
      final JsonNode result = mapper.createObjectNode().put("allow", true);
      plugin
          .getDecisionLogs()
          .logDecision("decision-a", input, result, "data.authz.allow", null, 0, null, null);
      final long deadline = System.currentTimeMillis() + millis;
      while (System.currentTimeMillis() < deadline) {
        if (logger.contains(FLUSHED)) {
          return true;
        }
        Thread.sleep(50);
      }
      return logger.contains(FLUSHED);
    } finally {
      plugin.stop();
    }
  }

  @Test
  void reportingDelaysDriveTheFlushSchedule() throws Exception {
    assertTrue(flushesWithin(logs().setReporting(reporting(1, 1)), 2500));
  }

  @Test
  void topLevelDelaysAreUsedWhenReportingIsAbsent() throws Exception {
    assertTrue(flushesWithin(topLevel(1, 1), 2500));
  }

  @Test
  void reportingWinsOverTopLevelWhenBothAreSet() throws Exception {
    final Config.DecisionLogsConfig both =
        topLevel(300, 600).setReporting(reporting(1, 1));

    assertTrue(flushesWithin(both, 2500));
  }

  @Test
  void reportingWinsEvenWhenItIsLonger() throws Exception {
    final Config.DecisionLogsConfig both =
        topLevel(1, 1).setReporting(reporting(300, 600));

    assertFalse(flushesWithin(both, 2000));
  }

  @Test
  void reportingWithOnlyMinIsAValidationError() {
    final Set<String> errors = validate(logs().setReporting(reporting(10, null)));

    assertTrue(
        errors.stream()
            .anyMatch(e -> e.contains("reporting") && e.contains("missing") && e.contains("max_delay_seconds")),
        errors::toString);
  }

  @Test
  void reportingWithOnlyMaxIsAValidationError() {
    final Set<String> errors = validate(logs().setReporting(reporting(null, 700)));

    assertTrue(
        errors.stream()
            .anyMatch(e -> e.contains("reporting") && e.contains("missing") && e.contains("min_delay_seconds")),
        errors::toString);
  }

  @Test
  void reportingMaxBelowMinIsAValidationError() {
    final Set<String> errors = validate(logs().setReporting(reporting(10, 5)));

    assertFalse(errors.isEmpty());
  }

  @Test
  void reportingWithBothDelaysIsValid() {
    assertTrue(validate(logs().setReporting(reporting(10, 20))).isEmpty());
  }

  private DecisionLogPlugin initialized(Config.DecisionLogsConfig decisionLogs) {
    return (DecisionLogPlugin) new DecisionLogPlugin().initialize(manager(decisionLogs));
  }

  @Test
  void delaysDefaultTo300And600() {
    final DecisionLogPlugin plugin = initialized(logs());

    assertEquals(300, plugin.resolvedMinDelaySeconds());
    assertEquals(600, plugin.resolvedMaxDelaySeconds());
  }

  @Test
  void reportingDelaysAreResolvedVerbatim() {
    final DecisionLogPlugin plugin = initialized(logs().setReporting(reporting(7, 9)));

    assertEquals(7, plugin.resolvedMinDelaySeconds());
    assertEquals(9, plugin.resolvedMaxDelaySeconds());
  }

  @Test
  void topLevelMaxDefaultsToTwiceTheMinWhenOnlyMinIsSet() {
    final DecisionLogPlugin plugin = initialized(topLevel(10, null));

    assertEquals(10, plugin.resolvedMinDelaySeconds());
    assertEquals(20, plugin.resolvedMaxDelaySeconds());
  }

  @Test
  void aBufferSizeLimitBelowOneIsAValidationError() {
    for (int limit : new int[] {0, -5}) {
      final Set<String> errors =
          validate(logs().setReporting(new Config.ReportingConfig().setBufferSizeLimitEvents(limit)));

      assertTrue(
          errors.stream().anyMatch(e -> e.contains("buffer_size_limit_events") && e.contains("higher than 0")),
          errors::toString);
    }
  }
}
