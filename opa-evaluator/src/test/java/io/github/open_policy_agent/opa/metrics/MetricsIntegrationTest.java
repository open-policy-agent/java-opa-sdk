package io.github.open_policy_agent.opa.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import io.github.open_policy_agent.opa.ast.types.RegoObject;
import io.github.open_policy_agent.opa.bundle.Bundle;
import io.github.open_policy_agent.opa.ir.PolicyReader;
import io.github.open_policy_agent.opa.ir.policy.Policy;
import io.github.open_policy_agent.opa.metrics.Metrics.Metric;
import io.github.open_policy_agent.opa.metrics.Metrics.Timer;
import io.github.open_policy_agent.opa.rego.Engine;
import io.github.open_policy_agent.opa.storage.InMem;
import io.github.open_policy_agent.opa.storage.Store;

/**
 * End-to-end integration test for {@link Metrics} and {@link MetricsPrinter}, exercising the full
 * evaluator via {@link Engine.PreparedQuery} so the timer hooks fired by {@code Engine} are
 * actually populated.
 */
class MetricsIntegrationTest {

  private static final ObjectMapper MAPPER =
      new ObjectMapper().registerModule(new io.github.open_policy_agent.opa.jackson.RegoValueModule());
  private static final PolicyReader POLICY_READER =
      ServiceLoader.load(PolicyReader.class).findFirst().orElseThrow();
  private static final String ENTRYPOINT = "authz/allow";

  // The Engine wires these timers around every prepared-query evaluation.
  private static final Set<String> EXPECTED_TIMER_KEYS =
      Set.of("rego_query_eval", "rego_parse_pojo_input", "rego_marshal_pojo_results");

  private static Policy policy;

  @BeforeAll
  static void loadPolicy() throws IOException {
    File policyFile =
        new File(
            Objects.requireNonNull(
                    MetricsIntegrationTest.class
                        .getClassLoader()
                        .getResource("engine/testdata/authz-policy.json"))
                .getFile());
    policy = POLICY_READER.read(Files.newInputStream(policyFile.toPath()));
  }

  private static Engine buildEngine(String dataJson) throws IOException {
    Store store = new InMem();
    RegoObject data = MAPPER.readValue(dataJson, RegoObject.class);
    Bundle bundle = new Bundle.Builder().withIrPolicy(policy).build();
    store.write(ENTRYPOINT, bundle, data);

    return new Engine.Builder().withStore(store).withEntrypoint(ENTRYPOINT).build();
  }

  private static Map<String, Object> input(String id, String... groups) {
    return Map.of("user", Map.of("id", id, "groups", List.of(groups)));
  }

  @Test
  void preparedQuery_recordsExpectedTimersFromRealEvaluation() throws IOException {
    Engine engine = buildEngine("{\"groups\":{\"admin\":{\"privileged\":true}}}");
    SimpleMetrics metrics = new SimpleMetrics();
    Engine.PreparedQuery pq =
        engine.prepareForEvaluation().withMetrics(metrics).build();

    List<Boolean> results = pq.eval(input("bob", "admin"), Boolean.class);

    assertTrue(results.get(0), "bob/admin should be allowed via privileged group");

    Map<String, Metric> all = metrics.all();
    assertFalse(all.isEmpty(), "expected at least one metric recorded");
    EXPECTED_TIMER_KEYS.forEach(
        key -> {
          Metric m = all.get(key);
          assertNotNull(m, "missing expected timer: " + key + "; saw " + all.keySet());
          assertTrue(m instanceof Timer, key + " was not a Timer: " + m.getClass());
          assertFalse(
              ((Timer) m).value().isNegative(), key + " duration should be non-negative");
        });
  }

  @Test
  void metricsPrinter_overPreparedQueryEvaluation_emitsBoxedTableWithTimerRows() throws IOException {
    Engine engine = buildEngine("{}");
    SimpleMetrics metrics = new SimpleMetrics();
    Engine.PreparedQuery pq =
        engine.prepareForEvaluation().withMetrics(metrics).build();
    pq.eval(input("alice"), Boolean.class);

    String table = MetricsPrinter.metricsToString(metrics);

    // Box-drawn structure: top, header, separator, ..., bottom.
    assertTrue(table.startsWith("┌"), "table should start with top border:\n" + table);
    assertTrue(table.contains("Metric"), "table should contain the Metric header:\n" + table);
    assertTrue(table.contains("Value"), "table should contain the Value header:\n" + table);
    assertTrue(
        table.trim().endsWith("┘"), "table should end with bottom border:\n" + table);

    // Every Engine-emitted timer should appear with the timer_<key>_ns naming convention.
    EXPECTED_TIMER_KEYS.forEach(
        key ->
            assertTrue(
                table.contains("timer_" + key + "_ns"),
                "expected timer_" + key + "_ns row in:\n" + table));

    // Each data row must respect the box width set by the widest cell.
    int boxWidth = table.indexOf('\n');
    table
        .lines()
        .filter(l -> !l.isEmpty())
        .forEach(
            line ->
                assertEquals(
                    boxWidth,
                    line.length(),
                    "row width mismatch for line: '" + line + "' (expected " + boxWidth + ")"));
  }

  @Test
  void preparedQuery_repeatedEvaluations_accumulateTimerCallsButRetainNamesOnce() throws IOException {
    Engine engine = buildEngine("{}");
    SimpleMetrics metrics = new SimpleMetrics();
    Engine.PreparedQuery pq =
        engine.prepareForEvaluation().withMetrics(metrics).build();

    pq.eval(input("alice"), Boolean.class);
    Set<String> firstKeys = Set.copyOf(metrics.all().keySet());
    pq.eval(input("alice"), Boolean.class);
    pq.eval(input("alice"), Boolean.class);

    // SimpleMetrics keeps one Timer per key across evaluations — repeated evals must not register
    // duplicate keys.
    assertEquals(firstKeys, metrics.all().keySet());
  }

  @Test
  void simpleMetricsClearRemovesRecordedTimers() throws IOException {
    Engine engine = buildEngine("{}");
    SimpleMetrics metrics = new SimpleMetrics();
    Engine.PreparedQuery pq =
            engine.prepareForEvaluation().withMetrics(metrics).build();

    pq.eval(input("alice"), Boolean.class);

    assertFalse(metrics.all().isEmpty(), "expected metrics to be recorded before clear");

    metrics.clear();

    assertTrue(metrics.all().isEmpty(), "expected metrics to be empty after clear");
  }
}
