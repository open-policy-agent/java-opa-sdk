package io.github.open_policy_agent.opa.rego;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import io.github.open_policy_agent.opa.ir.Location;
import io.github.open_policy_agent.opa.ir.stmts.NopStmt;
import io.github.open_policy_agent.opa.tracing.Profiler;

/**
 * Verifies that {@link EvaluationContext} dispatches to multiple registered {@link Profiler}s in
 * registration order. Exercises the public {@code traceEnterEvent} / {@code traceExitEvent}
 * methods directly so the test does not depend on a full IR fixture.
 */
class EvaluationContextProfilerTest {

  /** Records the sequence of (profilerId, callback) tuples it receives. */
  private static final class RecordingProfiler implements Profiler {
    private final String id;
    private final List<String> log;

    RecordingProfiler(String id, List<String> sharedLog) {
      this.id = id;
      this.log = sharedLog;
    }

    @Override
    public void addStart() {
      log.add(id + ":start");
    }

    @Override
    public void addEntry(Location location, long duration) {
      log.add(id + ":entry");
    }
  }

  static Stream<Arguments> dispatchCases() {
    return Stream.of(
        Arguments.of("no profilers registered", List.of(), List.of()),
        Arguments.of(
            "single profiler",
            List.of("a"),
            List.of("a:start", "a:entry")),
        Arguments.of(
            "multiple profilers preserve registration order",
            List.of("a", "b", "c"),
            List.of("a:start", "b:start", "c:start", "a:entry", "b:entry", "c:entry")));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("dispatchCases")
  void traceEvents_dispatchToRegisteredProfilersInOrder(
      String name, List<String> profilerIds, List<String> expectedLog) {
    List<String> log = new ArrayList<>();
    EvaluationContext.Builder builder = new EvaluationContext.Builder();
    profilerIds.forEach(id -> builder.withProfiler(new RecordingProfiler(id, log)));
    EvaluationContext ctx = builder.build();

    NopStmt stmt = new NopStmt(0, 1, 7);
    ctx.traceEnterEvent(stmt, 0, 0);
    ctx.traceExitEvent(stmt, 0, 0, 42L);

    assertEquals(expectedLog, log);
  }

  @Test
  void registeredProfilersList_isImmutableSnapshot() {
    List<String> log = new ArrayList<>();
    EvaluationContext.Builder builder =
        new EvaluationContext.Builder().withProfiler(new RecordingProfiler("a", log));

    EvaluationContext ctx = builder.build();

    // Adding more profilers to the builder after build() must not affect the built context.
    builder.withProfiler(new RecordingProfiler("late", log));

    NopStmt stmt = new NopStmt(0, 1, 7);
    ctx.traceEnterEvent(stmt, 0, 0);

    assertTrue(log.stream().noneMatch(s -> s.startsWith("late")), "late profiler must not fire");
    assertEquals(List.of("a:start"), log);
  }
}
