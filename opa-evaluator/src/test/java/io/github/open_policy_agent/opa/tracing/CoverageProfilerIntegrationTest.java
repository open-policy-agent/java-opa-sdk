package io.github.open_policy_agent.opa.tracing;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.Set;
import org.junit.jupiter.api.Test;
import io.github.open_policy_agent.opa.ast.types.RegoObject;
import io.github.open_policy_agent.opa.ir.Evaluator;
import io.github.open_policy_agent.opa.ir.PolicyReader;
import io.github.open_policy_agent.opa.ir.policy.Policy;
import io.github.open_policy_agent.opa.rego.EvaluationContext;

/**
 * End-to-end integration tests for {@link CoverageProfiler}, exercising the full evaluator with
 * a real IR fixture so the recording hook in
 * {@link io.github.open_policy_agent.opa.rego.EvaluationContext#traceExitEvent} is covered.
 */
class CoverageProfilerIntegrationTest {

  private static PolicyReader policyReader =
      ServiceLoader.load(PolicyReader.class).findFirst().orElseThrow();

  private Policy loadPolicy(String resourcePath) throws IOException {
    File jsonFile =
        new File(
            Objects.requireNonNull(getClass().getClassLoader().getResource(resourcePath))
                .getFile());
    return policyReader.read(Files.newInputStream(jsonFile.toPath()));
  }

  @Test
  void coverageProfiler_recordsLocationsFromRealEvaluation() throws IOException {
    Policy policy = loadPolicy("ir/testdata/policy-verify-BreakStmt.json");

    CoverageProfiler coverage = new CoverageProfiler();
    Evaluator evaluator = new Evaluator.Builder().withPolicy(policy).build();
    EvaluationContext ctx =
        new EvaluationContext.Builder().withEntrypoint("policy").withProfiler(coverage).build();

    evaluator.evaluate(ctx, new RegoObject(), new RegoObject());

    Map<Integer, Set<Integer>> covered = coverage.getCoveredLines();
    assertFalse(covered.isEmpty(), "expected at least one covered file");
    Set<Integer> rowsInFile0 = covered.get(0);
    assertNotNull(rowsInFile0, "expected coverage in file index 0");
    assertFalse(rowsInFile0.isEmpty(), "expected at least one covered row in file 0");
  }

  @Test
  void coverageAndDurationProfilers_canBeRegisteredTogether() throws IOException {
    Policy policy = loadPolicy("ir/testdata/policy-verify-BreakStmt.json");

    CoverageProfiler coverage = new CoverageProfiler();
    DurationProfiler timing = new DurationProfiler();
    Evaluator evaluator = new Evaluator.Builder().withPolicy(policy).build();
    EvaluationContext ctx =
        new EvaluationContext.Builder()
            .withEntrypoint("policy")
            .withProfiler(coverage)
            .withProfiler(timing)
            .build();

    evaluator.evaluate(ctx, new RegoObject(), new RegoObject());

    assertFalse(coverage.getCoveredLines().isEmpty(), "coverage profiler recorded nothing");
    assertFalse(timing.getDurations().isEmpty(), "duration profiler recorded nothing");
  }

  @Test
  void evaluationWithNoProfilers_succeeds() throws IOException {
    // Regression: the previously-singular `Profiler profiler` field is now a List. Verify the
    // empty-list default keeps evaluation working when no profiler is registered.
    Policy policy = loadPolicy("ir/testdata/policy-verify-BreakStmt.json");

    Evaluator evaluator = new Evaluator.Builder().withPolicy(policy).build();
    EvaluationContext ctx = new EvaluationContext.Builder().withEntrypoint("policy").build();

    // No assertion needed — the test passes if evaluate() does not throw.
    evaluator.evaluate(ctx, new RegoObject(), new RegoObject());
  }
}
