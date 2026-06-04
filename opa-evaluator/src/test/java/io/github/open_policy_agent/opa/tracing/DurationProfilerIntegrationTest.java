package io.github.open_policy_agent.opa.tracing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Duration;
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
import io.github.open_policy_agent.opa.rego.Engine;
import io.github.open_policy_agent.opa.storage.InMem;
import io.github.open_policy_agent.opa.storage.Store;
import io.github.open_policy_agent.opa.tracing.DurationProfiler.EvalTotal;
import io.github.open_policy_agent.opa.tracing.DurationProfiler.Loc;

/**
 * End-to-end integration test for {@link DurationProfiler}, exercising the full evaluator via
 * {@link Engine} and {@link Engine.PreparedQuery} so the {@code addStart} / {@code addEntry} hooks
 * fired from {@code EvaluationContext.traceEnterEvent}/{@code traceExitEvent} are covered.
 */
class DurationProfilerIntegrationTest {

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
                    DurationProfilerIntegrationTest.class
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
  void preparedQuery_recordsDurationsFromRealEvaluation() throws IOException {
    Engine engine = buildEngine("{\"groups\":{\"admin\":{\"privileged\":true}}}");
    DurationProfiler profiler = new DurationProfiler();
    Engine.PreparedQuery pq =
        engine.prepareForEvaluation().withProfiler(profiler).build();

    List<Boolean> results = pq.eval(input("bob", "admin"), Boolean.class);

    assertTrue(results.get(0), "bob/admin should be allowed via privileged group");

    Map<Loc, EvalTotal> durations = profiler.getDurations();
    assertFalse(durations.isEmpty(), "expected at least one location summary");

    durations.forEach(
        (loc, total) -> {
          assertTrue(total.getCount() > 0, "count not > 0 for " + loc);
          // Real wall-clock evaluations are expected to take a non-negative amount of time per
          // location. Negative durations would indicate that exclusive-time backoff over-subtracted.
          assertFalse(
              total.getTotalDuration().isNegative(), "duration negative for " + loc);
        });
  }

  @Test
  void preparedQuery_repeatedEvaluations_accumulateCounts() throws IOException {
    Engine engine = buildEngine("{}");
    DurationProfiler profiler = new DurationProfiler();
    Engine.PreparedQuery pq =
        engine.prepareForEvaluation().withProfiler(profiler).build();

    pq.eval(input("alice"), Boolean.class);
    Map<Loc, Integer> firstCounts = countsByLoc(profiler.getDurations());

    pq.eval(input("alice"), Boolean.class);
    pq.eval(input("alice"), Boolean.class);
    Map<Loc, Integer> finalCounts = countsByLoc(profiler.getDurations());

    assertNotNull(firstCounts);
    assertFalse(firstCounts.isEmpty());
    // After three identical evaluations every recorded location should have run 3x its first-eval
    // count — the profiler is shared across all prepared-query calls and must not reset.
    firstCounts.forEach(
        (loc, count) ->
            assertEquals(count * 3, finalCounts.get(loc), "count for " + loc + " did not triple"));
  }

  @Test
  void preparedQuery_durationsAreNonZeroAfterEvaluation() throws IOException {
    // Sanity check that real evaluations actually accrue measurable time somewhere — guards against
    // the profiler silently zeroing every location via overzealous backoff.
    Engine engine = buildEngine("{\"groups\":{\"admin\":{\"privileged\":true}}}");
    DurationProfiler profiler = new DurationProfiler();
    Engine.PreparedQuery pq =
        engine.prepareForEvaluation().withProfiler(profiler).build();

    pq.eval(input("bob", "admin"), Boolean.class);

    Duration totalRecorded =
        profiler.getDurations().values().stream()
            .map(EvalTotal::getTotalDuration)
            .reduce(Duration.ZERO, Duration::plus);
    assertFalse(totalRecorded.isZero(), "expected some non-zero recorded duration");
  }

  private static Map<Loc, Integer> countsByLoc(Map<Loc, EvalTotal> durations) {
    return durations.entrySet().stream()
        .collect(
            java.util.stream.Collectors.toMap(
                Map.Entry::getKey, e -> e.getValue().getCount()));
  }
}
