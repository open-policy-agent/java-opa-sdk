package io.github.open_policy_agent.opa.rego;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.open_policy_agent.opa.ast.builtin.BuiltinError;
import io.github.open_policy_agent.opa.ast.builtin.BuiltinRegistry;
import io.github.open_policy_agent.opa.ast.types.RegoObject;
import io.github.open_policy_agent.opa.bundle.Bundle;
import io.github.open_policy_agent.opa.ir.EvalConflictError;
import io.github.open_policy_agent.opa.ir.PolicyReader;
import io.github.open_policy_agent.opa.ir.policy.Policy;
import io.github.open_policy_agent.opa.jackson.RegoValueModule;
import io.github.open_policy_agent.opa.storage.InMem;
import io.github.open_policy_agent.opa.storage.Store;
import io.github.open_policy_agent.opa.ir.Location;
import io.github.open_policy_agent.opa.profiling.SimpleStatementProfiler;
import io.github.open_policy_agent.opa.tracing.BufferedQueryTracer;
import io.github.open_policy_agent.opa.tracing.Profiler;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;

/**
 * A rule whose only parameters are input and data gives the same answer every time it is called
 * within one evaluation, so the evaluator runs its body once. The plan is built by {@code opa build
 * -t plan} from the policy described in each test, and expected values are what {@code opa eval}
 * (OPA 1.21.1) prints. The body of {@code helper} calls {@code abs}, which is counted here.
 */
class RuleResultCacheTest {

  private static final JsonMapper MAPPER = JsonMapper.builder().addModule(new RegoValueModule()).build();
  private static final Map<String, Object> INPUT =
      Map.of(
          "x",
          -3,
          "items",
          List.of(1, 2, 3, 2),
          "map",
          Map.of("a", 1, "b", 2),
          "pairs",
          List.of(List.of("a", 1), List.of("a", 2)),
          "zero",
          0);

  private static final String ALL =
      "{\"array_build\":[[1,2,3,2,9],[1,2,3,2],[2,3,2]],\"loop_with\":[[2,2],[4,4],[6,6]],"
          + "\"nd\":[true,true,true],\"nested\":16,"
          + "\"object_union\":[{\"a\":1,\"b\":2,\"c\":3},{\"a\":1,\"b\":2},{\"a\":1,\"b\":2,\"d\":4}],"
          + "\"refs\":[6,6,6],\"set_ops\":[[1,2,3,7],[2,3],[2,3]],\"set_union\":[3,4,3],"
          + "\"undefined_rule\":0,\"undefined_twice\":[0,0],\"with_data\":[1,5,1],\"with_input\":[20],"
          + "\"with_input_between\":[6,14,6]}";

  private static Policy policy;
  private static Policy morePolicy;

  private final AtomicInteger absCalls = new AtomicInteger();

  @BeforeAll
  static void loadPolicy() throws IOException {
    final PolicyReader reader = ServiceLoader.load(PolicyReader.class).findFirst().orElseThrow();
    policy =
        reader.read(
            Objects.requireNonNull(
                RuleResultCacheTest.class.getClassLoader().getResourceAsStream("engine/testdata/rule-cache-plan.json")));
    morePolicy =
        reader.read(
            Objects.requireNonNull(
                RuleResultCacheTest.class
                    .getClassLoader()
                    .getResourceAsStream("engine/testdata/rule-cache-more-plan.json")));
  }

  private Engine engine() throws IOException {
    final Store store = new InMem();
    store.write(
        "rc/all",
        new Bundle.Builder().withIrPolicy(policy).build(),
        MAPPER.readValue("{\"rc_data\":{\"v\":1}}", RegoObject.class));
    final BuiltinRegistry real = BuiltinRegistry.allCapabilities();
    return new Engine.Builder()
        .withStore(store)
        .withEntrypoint("rc/all")
        .withBuiltin(
            "abs",
            (ctx, args) -> {
              absCalls.incrementAndGet();
              return real.getBuiltIn("abs").apply(ctx, args);
            })
        .build();
  }

  private Engine.PreparedQuery query(String rule) throws IOException {
    return engine().prepareForEvaluation().withEntrypoint("rc/" + rule).build();
  }

  /** Numbers as plain decimals and maps in key order, so 3.0 and 3 compare like the values they are. */
  private static Object canonical(Object value) {
    if (value instanceof Map) {
      final Map<String, Object> sorted = new TreeMap<>();
      ((Map<?, ?>) value).forEach((k, v) -> sorted.put(String.valueOf(k), canonical(v)));
      return sorted;
    }
    if (value instanceof List) {
      final List<Object> list = new ArrayList<>();
      ((List<?>) value).forEach(v -> list.add(canonical(v)));
      return list;
    }
    if (value instanceof Number) {
      return new BigDecimal(value.toString()).stripTrailingZeros().toPlainString();
    }
    return value;
  }

  private static Object expected(String json) {
    return canonical(MAPPER.readValue("{\"result\":" + json + "}", Object.class));
  }

  @Test
  void aRuleReferencedThreeTimesRunsItsBodyOnce() throws IOException {
    final List<Object> results = query("refs").eval(INPUT);

    assertEquals(List.of(expected("[6,6,6]")), canonical(results));
    assertEquals(1, absCalls.get());
  }

  @Test
  void everyKindOfResultMatchesOpa() throws IOException {
    final Engine.PreparedQuery query = query("all");

    for (int round = 0; round < 2; round++) {
      assertEquals(List.of(expected(ALL)), canonical(query.eval(INPUT)), "round " + round);
    }
  }

  @Test
  void aWithOverrideIsADifferentKeyFromTheRootValues() throws IOException {
    final List<Object> results = query("with_input_between").eval(INPUT);

    assertEquals(List.of(expected("[6,14,6]")), canonical(results));
    assertEquals(2, absCalls.get(), "once for the real input, once for the patched one");
  }

  @Test
  void aWithInsideALoopCachesNothingForTheOverriddenInput() throws IOException {
    final List<Object> results = query("loop_with").eval(INPUT);

    assertEquals(List.of(expected("[[2,2],[4,4],[6,6]]")), canonical(results));
    assertEquals(6, absCalls.get(), "each overridden call runs the body");
  }

  @Test
  void anUndefinedResultIsCachedToo() throws IOException {
    final List<Object> results = query("undefined_twice").eval(INPUT);

    assertEquals(List.of(expected("[0,0]")), canonical(results));
    assertEquals(1, absCalls.get());
  }

  @Test
  void theCacheLivesForOneEvaluation() throws IOException {
    final Engine.PreparedQuery query = query("refs");

    query.eval(INPUT);
    query.eval(INPUT);

    assertEquals(2, absCalls.get());
  }

  @Test
  void aTracerBypassesTheCache() throws IOException {
    final BufferedQueryTracer tracer = new BufferedQueryTracer();
    final Engine.PreparedQuery query = engine().prepareForEvaluation().withEntrypoint("rc/refs").withTracer(tracer).build();

    query.eval(INPUT);

    assertEquals(3, absCalls.get());
    assertTrue(tracer.getEvents().size() > 0);
  }

  @Test
  void aStrictBuiltinErrorInARuleSurfacesOnEveryEvaluation() throws IOException {
    final Engine.PreparedQuery strict =
        engine().prepareForEvaluation().withEntrypoint("rc/use_bad").withStrictBuiltinErrors().build();

    assertThrows(BuiltinError.class, () -> strict.eval(INPUT));
    assertThrows(BuiltinError.class, () -> strict.eval(INPUT));
  }

  @Test
  void withoutStrictModeABuiltinErrorIsUndefinedEveryTime() throws IOException {
    final Engine.PreparedQuery lenient = query("use_bad");

    assertTrue(lenient.eval(INPUT).isEmpty());
    assertTrue(lenient.eval(INPUT).isEmpty());
  }

  @Test
  void aConflictErrorInARuleSurfacesOnEveryEvaluation() throws IOException {
    final Engine.PreparedQuery query = query("use_conflict");

    assertThrows(EvalConflictError.class, () -> query.eval(INPUT));
    assertThrows(EvalConflictError.class, () -> query.eval(INPUT));
  }

  @Test
  void theOptionTurnsTheCacheOffOnAPreparedQuery() throws IOException {
    final Engine.PreparedQuery query =
        engine().prepareForEvaluation().withEntrypoint("rc/refs").withoutRuleResultCache().build();

    final List<Object> results = query.eval(INPUT);

    assertEquals(List.of(expected("[6,6,6]")), canonical(results));
    assertEquals(3, absCalls.get());
  }

  @Test
  void withTheOptionOffEveryKindOfResultStillMatchesOpa() throws IOException {
    final Engine.PreparedQuery query =
        engine().prepareForEvaluation().withEntrypoint("rc/all").withoutRuleResultCache().build();

    assertEquals(List.of(expected(ALL)), canonical(query.eval(INPUT)));
  }

  @Test
  void theCacheAppliesToAnEvaluationContextBuiltByHand() throws IOException {
    final Engine engine = engine();
    final Object input = INPUT;

    final List<Object> cached =
        engine.evaluate(new EvaluationContext.Builder().withEntrypoint("rc/refs").build(), input);
    final int cachedCalls = absCalls.getAndSet(0);
    final List<Object> uncached =
        engine.evaluate(
            new EvaluationContext.Builder().withEntrypoint("rc/refs").withoutRuleResultCache().build(), input);

    assertEquals(canonical(cached), canonical(uncached));
    assertEquals(1, cachedCalls);
    assertEquals(3, absCalls.get());
  }

  @Test
  void aProfilerOrAStatementProfilerAlsoBypassesTheCache() throws IOException {
    engine()
        .prepareForEvaluation()
        .withEntrypoint("rc/refs")
        .withProfiler(
            new Profiler() {
              @Override
              public void addStart() {}

              @Override
              public void addEntry(Location location, long duration) {}
            })
        .build()
        .eval(INPUT);
    assertEquals(3, absCalls.getAndSet(0));

    engine()
        .prepareForEvaluation()
        .withEntrypoint("rc/refs")
        .withStatementProfiler(new SimpleStatementProfiler())
        .build()
        .eval(INPUT);
    assertEquals(3, absCalls.get());
  }

  private Engine.PreparedQuery moreQuery(String rule, PrintHook printHook, boolean ruleResultCache) throws IOException {
    final Store store = new InMem();
    store.write("rcx/all", new Bundle.Builder().withIrPolicy(morePolicy).build(), new RegoObject());
    final BuiltinRegistry real = BuiltinRegistry.allCapabilities();
    final Engine engine =
        new Engine.Builder()
            .withStore(store)
            .withEntrypoint("rcx/nested")
            .withBuiltin(
                "abs",
                (ctx, args) -> {
                  absCalls.incrementAndGet();
                  return real.getBuiltIn("abs").apply(ctx, args);
                })
            .build();
    final Engine.PreparedQuery.Builder builder = engine.prepareForEvaluation().withEntrypoint("rcx/" + rule);
    if (printHook != null) {
      builder.withPrintHook(printHook);
    }
    if (!ruleResultCache) {
      builder.withoutRuleResultCache();
    }
    return builder.build();
  }

  // Expected values from opa eval (OPA 1.21.1) with input {"x": -3}.
  @Test
  void aCachedRuleCallingAnotherCachedRuleRunsEachBodyOnce() throws IOException {
    final List<Object> results = moreQuery("nested", null, true).eval(INPUT);

    assertEquals(List.of(expected("[6,6,6]")), canonical(results));
    assertEquals(1, absCalls.get());
  }

  @Test
  void aDynamicReferenceToRulesGivesTheSameAnswer() throws IOException {
    final List<Object> results = moreQuery("by_name", null, true).eval(INPUT);

    assertEquals(List.of(expected("[3,6]")), canonical(results));
  }

  @Test
  void aPrintInACachedRulePrintsOncePerEvaluation() throws IOException {
    final List<String> printed = new ArrayList<>();

    final List<Object> results = moreQuery("with_print", printed::add, true).eval(INPUT);

    assertEquals(List.of(expected("[3,3,3]")), canonical(results));
    assertEquals(List.of("cached rule body"), printed);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void aNullInputGivesAnUndefinedAnswer(boolean ruleResultCache) throws IOException {
    assertTrue(moreQuery("nested", null, ruleResultCache).eval(null).isEmpty());
  }

  static Stream<Arguments> mutationCases() {
    return Stream.of(
        Arguments.of(
            "mut_twice",
            "[{\"k\":{\"n\":1},\"list\":[1,2]},{\"k\":{\"n\":1},\"list\":[1,2],\"z\":1},{\"k\":{\"n\":1},\"list\":[1,2]},{\"k\":{\"n\":1},\"list\":[1,2],\"z\":1}]"),
        Arguments.of("mut_sets", "[[1,2,3,99],[1,2,3,99],4,[1,2,3,99]]"),
        Arguments.of("mut_refs", "[{\"a\":{\"1\":1,\"2\":1,\"3\":1,\"b\":2},\"c\":{\"k\":{\"n\":1},\"list\":[1,2]}},{\"a\":{\"1\":1,\"2\":1,\"3\":1,\"b\":2},\"c\":{\"k\":{\"n\":1},\"list\":[1,2]}},{\"1\":1,\"2\":1,\"3\":1,\"b\":2},{\"a\":{\"1\":1,\"2\":1,\"3\":1,\"b\":2},\"c\":{\"k\":{\"n\":1},\"list\":[1,2]}}]"),
        Arguments.of("mut_objs", "[{\"a\":1,\"b\":2,\"extra\":{\"deep\":[1,2]}},{\"a\":1,\"b\":2,\"extra\":{\"deep\":[1,2]}},{\"deep\":[1,2]},{\"a\":1,\"b\":2,\"extra\":{\"deep\":[1,2]}}]"),
        Arguments.of("mut_loop", "[{\"b\":{\"k\":{\"n\":1},\"list\":[1,2]},\"s\":[1,2,3,99],\"x\":1},{\"b\":{\"k\":{\"n\":1},\"list\":[1,2]},\"s\":[1,2,3,99],\"x\":2},{\"b\":{\"k\":{\"n\":1},\"list\":[1,2]},\"s\":[1,2,3,99],\"x\":3},{\"b\":{\"k\":{\"n\":1},\"list\":[1,2]},\"s\":[1,2,3,99],\"x\":2}]"));
  }

  // A rule's result is shared by every reference to it, so nothing may change it in place: each
  // reference sees what opa eval (OPA 1.21.1) prints, with the cache on and off.
  @ParameterizedTest
  @MethodSource("mutationCases")
  void aSharedRuleResultIsNeverChangedByLaterUses(String rule, String json) throws IOException {
    final Object expected = expected(json);

    assertEquals(List.of(expected), canonical(moreQuery(rule, null, true).eval(INPUT)));
    assertEquals(List.of(expected), canonical(moreQuery(rule, null, false).eval(INPUT)));
  }
}
