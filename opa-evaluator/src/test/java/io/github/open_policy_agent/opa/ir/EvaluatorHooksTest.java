package io.github.open_policy_agent.opa.ir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.open_policy_agent.opa.ast.types.RegoObject;
import io.github.open_policy_agent.opa.bundle.Bundle;
import io.github.open_policy_agent.opa.ir.policy.Policy;
import io.github.open_policy_agent.opa.ir.stmts.Stmt;
import io.github.open_policy_agent.opa.jackson.RegoValueModule;
import io.github.open_policy_agent.opa.profiling.NoOpStatementProfiler;
import io.github.open_policy_agent.opa.profiling.StatementProfiler;
import io.github.open_policy_agent.opa.rego.Engine;
import io.github.open_policy_agent.opa.rego.EvaluationContext;
import io.github.open_policy_agent.opa.storage.InMem;
import io.github.open_policy_agent.opa.storage.Store;
import io.github.open_policy_agent.opa.tracing.BufferedQueryTracer;
import io.github.open_policy_agent.opa.tracing.Event;
import io.github.open_policy_agent.opa.tracing.Profiler;
import io.github.open_policy_agent.opa.tracing.QueryTracer;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceLoader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import tools.jackson.databind.json.JsonMapper;

/**
 * What the evaluator reports to a tracer, a {@link Profiler} (coverage, durations) and a {@link
 * StatementProfiler}, and in which order, for every statement it runs.
 */
class EvaluatorHooksTest {

  private static final String ENTRYPOINT = "authz/allow";
  private static final JsonMapper MAPPER = JsonMapper.builder().addModule(new RegoValueModule()).build();

  private static Policy policy;

  @BeforeAll
  static void loadPolicy() throws IOException {
    final PolicyReader reader = ServiceLoader.load(PolicyReader.class).findFirst().orElseThrow();
    policy =
        reader.read(
            Objects.requireNonNull(
                EvaluatorHooksTest.class.getClassLoader().getResourceAsStream("engine/testdata/authz-policy.json")));
  }

  private enum Hook {
    TRACER("T:ENTER", "T:EXIT"),
    PROFILER("P:start", "P:entry"),
    STATEMENT_PROFILER("S:start", "S:stop");

    private final String startTag;
    private final String endTag;

    Hook(String startTag, String endTag) {
      this.startTag = startTag;
      this.endTag = endTag;
    }
  }

  /** Every callback, in the order it arrived, as a short tag. */
  private final List<String> log = Collections.synchronizedList(new ArrayList<>());

  private final List<Long> durations = Collections.synchronizedList(new ArrayList<>());

  private static Map<String, Object> input(String id) {
    return input(id, "admin");
  }

  private static Map<String, Object> input(String id, String group) {
    return Map.of("user", Map.of("id", id, "groups", List.of(group)));
  }

  private static Engine engine() throws IOException {
    final Store store = new InMem();
    store.write(
        ENTRYPOINT,
        new Bundle.Builder().withIrPolicy(policy).build(),
        MAPPER.readValue("{\"groups\":{\"admin\":{\"privileged\":true}}}", RegoObject.class));
    return new Engine.Builder().withStore(store).withEntrypoint(ENTRYPOINT).build();
  }

  private Engine.PreparedQuery query(Hook... hooks) throws IOException {
    final Engine.PreparedQuery.Builder builder = engine().prepareForEvaluation();
    for (Hook hook : hooks) {
      switch (hook) {
        case TRACER:
          builder.withTracer(
              new QueryTracer() {
                @Override
                public void TraceEvent(Event event) {
                  log.add(
                      (event instanceof StatementEvent
                              ? "T:" + event.getOp() + ":" + ((StatementEvent) event).getStatement()
                              : "B:" + event.getOp()));
                }

                @Override
                public List<Event> getEvents() {
                  return List.of();
                }
              });
          break;
        case PROFILER:
          builder.withProfiler(
              new Profiler() {
                @Override
                public void addStart() {
                  log.add("P:start");
                }

                @Override
                public void addEntry(Location location, long duration) {
                  log.add("P:entry");
                  durations.add(duration);
                }
              });
          break;
        case STATEMENT_PROFILER:
          builder.withStatementProfiler(
              new StatementProfiler() {
                @Override
                public void startStatement(Stmt stmt) {
                  log.add("S:start:" + stmt.getClass().getSimpleName());
                }

                @Override
                public void stopStatement(Stmt stmt, long durationNanos) {
                  log.add("S:stop:" + stmt.getClass().getSimpleName());
                  durations.add(durationNanos);
                }

                @Override
                public Map<String, StatementSummary> getStatementSummaries() {
                  return Map.of();
                }
              });
          break;
        default:
          throw new AssertionError("hook not handled: " + hook);
      }
    }
    return builder.build();
  }

  private static long count(List<String> log, String prefix) {
    return log.stream().filter(s -> s.startsWith(prefix)).count();
  }

  // Statements run for "alice" with the authz plan, with 10 blocks and one break.
  private static final int STATEMENTS = 35;

  @Test
  void everyStatementReportsToAllHooksInTheSameOrder() throws IOException {
    query(Hook.TRACER, Hook.PROFILER, Hook.STATEMENT_PROFILER).eval(input("alice"));

    final List<String> open = new ArrayList<>();
    int statements = 0;
    int blocks = 0;
    for (int i = 0; i < log.size(); i++) {
      final String entry = log.get(i);
      if (entry.startsWith("S:start:")) {
        final String kind = entry.substring("S:start:".length());
        assertEquals("T:ENTER:" + kind, log.get(i + 1), "tracer enter follows the profiler start");
        assertEquals("P:start", log.get(i + 2), "profiler start follows the tracer enter");
        open.add(kind);
        statements++;
      } else if (entry.startsWith("S:stop:")) {
        final String kind = entry.substring("S:stop:".length());
        assertEquals(open.remove(open.size() - 1), kind, "statements end in reverse order of their start");
        assertEquals("T:EXIT:" + kind, log.get(i + 1), "tracer exit follows the profiler stop");
        assertEquals("P:entry", log.get(i + 2), "profiler entry follows the tracer exit");
      } else if ("B:ENTER".equals(entry)) {
        blocks++;
      }
    }

    assertTrue(open.isEmpty(), "every statement that started also stopped: " + open);
    assertEquals(STATEMENTS, statements);
    assertEquals(10, blocks);
    assertEquals(blocks, count(log, "B:EXIT"));
    assertEquals(1, count(log, "B:BREAK"));
  }

  @ParameterizedTest
  @EnumSource(Hook.class)
  void eachHookAloneStillSeesEveryStatement(Hook hook) throws IOException {
    query(hook).eval(input("alice"));

    assertEquals(STATEMENTS, count(log, hook.startTag));
    assertEquals(STATEMENTS, count(log, hook.endTag));
  }

  @Test
  void durationsReachTheProfilersAndAreNeverNegative() throws IOException {
    query(Hook.PROFILER, Hook.STATEMENT_PROFILER).eval(input("alice"));

    assertEquals(2 * STATEMENTS, durations.size());
    assertTrue(durations.stream().allMatch(d -> d >= 0), durations.toString());
  }

  @Test
  void theAnswerDoesNotDependOnWhatIsAttached() throws IOException {
    final Engine.PreparedQuery bare = query();
    final Engine.PreparedQuery observed = query(Hook.TRACER, Hook.PROFILER, Hook.STATEMENT_PROFILER);

    final Map<String, Map<String, Object>> inputs =
        Map.of(
            "allowed by id", input("alice"),
            "allowed by group", input("erin", "admin"),
            "denied", input("dave", "staff"));
    inputs.forEach((name, in) -> assertEquals(bare.eval(in), observed.eval(in), name));
    assertEquals(List.of(Map.of("result", false)), bare.eval(inputs.get("denied")));
  }

  @Test
  void theUnpreparedPathAnswersTheSameWithATracerAttached() throws IOException {
    final Engine engine = engine();

    for (Map<String, Object> in : List.of(input("alice"), input("dave", "staff"))) {
      final List<Object> bare =
          engine.evaluate(new EvaluationContext.Builder().withEntrypoint(ENTRYPOINT).build(), in);
      final List<Object> traced =
          engine.evaluate(
              new EvaluationContext.Builder()
                  .withEntrypoint(ENTRYPOINT)
                  .withTracer(new BufferedQueryTracer())
                  .build(),
              in);
      assertEquals(bare, traced);
    }
  }

  @Test
  void aSubclassOfTheNoOpStatementProfilerIsStillCalled() throws IOException {
    final Store store = new InMem();
    store.write(
        ENTRYPOINT,
        new Bundle.Builder().withIrPolicy(policy).build(),
        MAPPER.readValue("{}", RegoObject.class));
    final Engine.PreparedQuery query =
        new Engine.Builder()
            .withStore(store)
            .withEntrypoint(ENTRYPOINT)
            .build()
            .prepareForEvaluation()
            .withStatementProfiler(
                new NoOpStatementProfiler() {
                  @Override
                  public void startStatement(Stmt stmt) {
                    log.add("S:start");
                  }
                })
            .build();

    query.eval(input("alice"));

    assertTrue(count(log, "S:start") > 0, "the subclass never saw a statement");
  }
}
