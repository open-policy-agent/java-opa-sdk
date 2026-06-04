package io.github.open_policy_agent.opa.profiling;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceLoader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import io.github.open_policy_agent.opa.ast.types.RegoObject;
import io.github.open_policy_agent.opa.bundle.Bundle;
import io.github.open_policy_agent.opa.ir.PolicyReader;
import io.github.open_policy_agent.opa.ir.policy.Policy;
import io.github.open_policy_agent.opa.profiling.StatementProfiler.StatementSummary;
import io.github.open_policy_agent.opa.rego.Engine;
import io.github.open_policy_agent.opa.storage.InMem;
import io.github.open_policy_agent.opa.storage.Store;

/**
 * End-to-end integration test for {@link SimpleStatementProfiler}, exercising the full evaluator
 * via {@link Engine} and {@link Engine.PreparedQuery} so the start/stop hooks fired from
 * {@code Evaluator} are covered.
 */
class SimpleStatementProfilerIntegrationTest {

  private static final ObjectMapper MAPPER =
      new ObjectMapper().registerModule(new io.github.open_policy_agent.opa.jackson.RegoValueModule());
  private static final PolicyReader POLICY_READER =
      ServiceLoader.load(PolicyReader.class).findFirst().orElseThrow();
  private static final String ENTRYPOINT = "authz/allow";

  private static Policy policy;

  @BeforeAll
  static void loadPolicy() throws IOException {
    File policyFile =
        new File(
            Objects.requireNonNull(
                    SimpleStatementProfilerIntegrationTest.class
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
  void preparedQuery_recordsStatementSummariesFromRealEvaluation() throws IOException {
    Engine engine = buildEngine("{\"groups\":{\"admin\":{\"privileged\":true}}}");
    SimpleStatementProfiler profiler = new SimpleStatementProfiler();
    Engine.PreparedQuery pq =
        engine.prepareForEvaluation().withStatementProfiler(profiler).build();

    List<Boolean> results = pq.eval(input("bob", "admin"), Boolean.class);

    assertTrue(results.get(0), "bob/admin should be allowed via privileged group");

    Map<String, StatementSummary> summaries = profiler.getStatementSummaries();
    assertFalse(summaries.isEmpty(), "expected at least one statement summary");

    // Every recorded statement must have run at least once.
    summaries.values().forEach(s -> assertTrue(s.getCount() > 0, s.getName() + " count not > 0"));

    // The authz policy walks input.user.groups via a ScanStmt and dispatches a CallStmt to the
    // membership builtin and the rule body. Both should be visible after evaluation.
    assertTrue(summaries.containsKey("ScanStmt"), "expected ScanStmt; saw " + summaries.keySet());
    assertTrue(
        summaries.containsKey("internal.member_2"),
        "expected CallStmt to be keyed by function name; saw " + summaries.keySet());
    assertTrue(
        summaries.containsKey("g0.data.authz.allow"),
        "expected the rule call to be keyed by function name; saw " + summaries.keySet());

    // CallStmt naming bug regression: function-call summaries must not be lumped under the raw
    // type name "CallStmt".
    assertFalse(
        summaries.containsKey("CallStmt"),
        "CallStmts must be keyed by function name, not the type name");
  }

  @Test
  void preparedQuery_repeatedEvaluations_accumulateCounts() throws IOException {
    Engine engine = buildEngine("{}");
    SimpleStatementProfiler profiler = new SimpleStatementProfiler();
    Engine.PreparedQuery pq =
        engine.prepareForEvaluation().withStatementProfiler(profiler).build();

    pq.eval(input("alice"), Boolean.class);
    Map<String, Integer> firstCounts = countsByName(profiler.getStatementSummaries());

    pq.eval(input("alice"), Boolean.class);
    pq.eval(input("alice"), Boolean.class);
    Map<String, Integer> finalCounts = countsByName(profiler.getStatementSummaries());

    assertNotNull(firstCounts);
    assertFalse(firstCounts.isEmpty());
    // After three identical evaluations every recorded statement should have run 3x its first-eval
    // count — the profiler is shared across all prepared-query calls and must not reset.
    firstCounts.forEach(
        (name, count) ->
            assertEquals(
                count * 3, finalCounts.get(name), "count for " + name + " did not triple"));
  }

  private static Map<String, Integer> countsByName(Map<String, StatementSummary> summaries) {
    return summaries.entrySet().stream()
        .collect(
            java.util.stream.Collectors.toMap(
                Map.Entry::getKey, e -> e.getValue().getCount()));
  }
}
