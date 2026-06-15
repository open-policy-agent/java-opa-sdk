package io.github.open_policy_agent.opa.profiling;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import io.github.open_policy_agent.opa.ir.stmts.CallStmt;
import io.github.open_policy_agent.opa.ir.stmts.NopStmt;
import io.github.open_policy_agent.opa.ir.stmts.Stmt;
import io.github.open_policy_agent.opa.profiling.StatementProfiler.StatementSummary;

class SimpleStatementProfilerTest {

  private SimpleStatementProfiler profiler;

  @BeforeEach
  void setUp() {
    profiler = new SimpleStatementProfiler();
  }

  static Stream<Arguments> recordingScenarios() {
    return Stream.of(
        Arguments.of(
            "single statement",
            (Consumer<SimpleStatementProfiler>)
                p -> {
                  NopStmt nop = new NopStmt(0, 1, 5);
                  p.startStatement(nop);
                  p.stopStatement(nop, 100L);
                },
            Map.of("NopStmt", expected(1, 100L))),
        Arguments.of(
            "repeated statement accumulates count and duration",
            (Consumer<SimpleStatementProfiler>)
                p -> {
                  NopStmt nop = new NopStmt(0, 1, 5);
                  p.startStatement(nop);
                  p.stopStatement(nop, 30L);
                  p.startStatement(nop);
                  p.stopStatement(nop, 70L);
                  p.startStatement(nop);
                  p.stopStatement(nop, 50L);
                },
            Map.of("NopStmt", expected(3, 150L))),
        Arguments.of(
            "CallStmt is keyed by function name",
            (Consumer<SimpleStatementProfiler>)
                p -> {
                  CallStmt call = new CallStmt("my.func", List.of(), 0);
                  p.startStatement(call);
                  p.stopStatement(call, 42L);
                },
            Map.of("my.func", expected(1, 42L))),
        Arguments.of(
            "CallStmts group by function name",
            (Consumer<SimpleStatementProfiler>)
                p -> {
                  CallStmt foo1 = new CallStmt("foo", List.of(), 0);
                  CallStmt foo2 = new CallStmt("foo", List.of(), 0);
                  CallStmt bar = new CallStmt("bar", List.of(), 0);
                  p.startStatement(foo1);
                  p.stopStatement(foo1, 10L);
                  p.startStatement(foo2);
                  p.stopStatement(foo2, 20L);
                  p.startStatement(bar);
                  p.stopStatement(bar, 5L);
                },
            Map.of(
                "foo", expected(2, 30L),
                "bar", expected(1, 5L))),
        Arguments.of(
            // Parent (Nop) takes 100ns total; child (Call f) takes 60ns.
            // Parent's exclusive time should be 40ns.
            "nested child time subtracts from parent",
            (Consumer<SimpleStatementProfiler>)
                p -> {
                  NopStmt parent = new NopStmt(0, 1, 5);
                  CallStmt child = new CallStmt("f", List.of(), 0);
                  p.startStatement(parent);
                  p.startStatement(child);
                  p.stopStatement(child, 60L);
                  p.stopStatement(parent, 100L);
                },
            Map.of(
                "NopStmt", expected(1, 40L),
                "f", expected(1, 60L))),
        Arguments.of(
            // Parent takes 100ns, with two sequential children of 30ns and 40ns.
            // Parent's exclusive time should be 100 - 30 - 40 = 30ns.
            "sibling children both subtract from parent",
            (Consumer<SimpleStatementProfiler>)
                p -> {
                  NopStmt parent = new NopStmt(0, 1, 5);
                  CallStmt a = new CallStmt("a", List.of(), 0);
                  CallStmt b = new CallStmt("b", List.of(), 0);
                  p.startStatement(parent);
                  p.startStatement(a);
                  p.stopStatement(a, 30L);
                  p.startStatement(b);
                  p.stopStatement(b, 40L);
                  p.stopStatement(parent, 100L);
                },
            Map.of(
                "NopStmt", expected(1, 30L),
                "a", expected(1, 30L),
                "b", expected(1, 40L))),
        Arguments.of(
            // grandparent (Nop) -> parent (Call p) -> child (Call c).
            // Child of 20ns subtracts from parent only; grandparent unchanged by child.
            "deeply nested only immediate parent is adjusted",
            (Consumer<SimpleStatementProfiler>)
                p -> {
                  NopStmt grandparent = new NopStmt(0, 1, 5);
                  CallStmt parent = new CallStmt("p", List.of(), 0);
                  CallStmt child = new CallStmt("c", List.of(), 0);
                  p.startStatement(grandparent);
                  p.startStatement(parent);
                  p.startStatement(child);
                  p.stopStatement(child, 20L);
                  p.stopStatement(parent, 80L);
                  p.stopStatement(grandparent, 200L);
                },
            Map.of(
                "NopStmt", expected(1, 120L), // 200 - 80
                "p", expected(1, 60L), // 80 - 20
                "c", expected(1, 20L))));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("recordingScenarios")
  void recordingScenarios_produceExpectedSummaries(
      String name, Consumer<SimpleStatementProfiler> recording, Map<String, long[]> expected) {
    recording.accept(profiler);

    Map<String, StatementSummary> summaries = profiler.getStatementSummaries();
    assertEquals(expected.keySet(), summaries.keySet(), "summary keys");
    expected.forEach(
        (key, exp) -> {
          StatementSummary actual = summaries.get(key);
          assertEquals(exp[0], actual.getCount(), "count for " + key);
          assertEquals(Duration.ofNanos(exp[1]), actual.getDuration(), "duration for " + key);
        });
  }

  static Stream<Arguments> mismatchedStopCases() {
    return Stream.of(
        Arguments.of(
            "different statement types",
            (Stmt) new NopStmt(0, 1, 5),
            (Stmt) new CallStmt("f", List.of(), 0)),
        Arguments.of(
            "different function names",
            (Stmt) new CallStmt("foo", List.of(), 0),
            (Stmt) new CallStmt("bar", List.of(), 0)));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("mismatchedStopCases")
  void stopStatement_withMismatchedStmt_throws(String name, Stmt started, Stmt stopped) {
    profiler.startStatement(started);

    assertThrows(IllegalStateException.class, () -> profiler.stopStatement(stopped, 10L));
  }

  @Test
  void getStatementSummaries_isUnmodifiable() {
    NopStmt stmt = new NopStmt(0, 1, 5);
    profiler.startStatement(stmt);
    profiler.stopStatement(stmt, 1L);

    Map<String, StatementSummary> summaries = profiler.getStatementSummaries();
    assertThrows(
        UnsupportedOperationException.class,
        () -> summaries.put("x", new StatementSummary("x")));
  }

  @Test
  void getStatementSummaries_reflectsLiveProfilerState() {
    // The unmodifiable view is a *view*, not a snapshot — later starts show up.
    Map<String, StatementSummary> view = profiler.getStatementSummaries();
    assertTrue(view.isEmpty());

    NopStmt stmt = new NopStmt(0, 1, 5);
    profiler.startStatement(stmt);
    profiler.stopStatement(stmt, 1L);

    assertEquals(1, view.size());
  }

  @Test
  void repeatedStarts_reuseSameSummaryInstance() {
    NopStmt stmt = new NopStmt(0, 1, 5);
    profiler.startStatement(stmt);
    profiler.stopStatement(stmt, 1L);
    StatementSummary first = profiler.getStatementSummaries().get("NopStmt");

    profiler.startStatement(stmt);
    profiler.stopStatement(stmt, 1L);
    StatementSummary second = profiler.getStatementSummaries().get("NopStmt");

    assertSame(first, second);
  }

  private static long[] expected(long count, long nanos) {
    return new long[] {count, nanos};
  }
}