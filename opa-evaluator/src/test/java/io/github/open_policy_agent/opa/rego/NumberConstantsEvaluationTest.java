package io.github.open_policy_agent.opa.rego;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.open_policy_agent.opa.ast.types.RegoObject;
import io.github.open_policy_agent.opa.bundle.Bundle;
import io.github.open_policy_agent.opa.ir.PolicyReader;
import io.github.open_policy_agent.opa.ir.policy.Policy;
import io.github.open_policy_agent.opa.jackson.RegoValueModule;
import io.github.open_policy_agent.opa.storage.InMem;
import io.github.open_policy_agent.opa.storage.Store;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/**
 * Evaluates a plan whose rules hold every kind of number constant. The expected output is what
 * {@code opa eval} (OPA 1.21.1) prints for the same policy (data.nums.result).
 */
class NumberConstantsEvaluationTest {

  private static final String ENTRYPOINT = "nums/result";
  private static final JsonMapper MAPPER = JsonMapper.builder().addModule(new RegoValueModule()).build();
  private static final String ALL =
      "{\"big\":[9223372036854775807,9223372036854775808,123456789012345678901234567890],"
          + "\"decimals\":[1.5,0.5,-0.5,0.25],"
          + "\"ints\":[0,42,-7,2147483647,2147483648,-2147483649],"
          + "\"sum\":3.75,"
          + "\"whole\":[3.0,-42.0,1e3]}";

  private static Policy policy;

  @BeforeAll
  static void loadPolicy() throws IOException {
    final PolicyReader reader = ServiceLoader.load(PolicyReader.class).findFirst().orElseThrow();
    policy =
        reader.read(
            Objects.requireNonNull(
                NumberConstantsEvaluationTest.class
                    .getClassLoader()
                    .getResourceAsStream("engine/testdata/number-constants-plan.json")));
  }

  private static Engine.PreparedQuery query() throws IOException {
    final Store store = new InMem();
    store.write(
        ENTRYPOINT, new Bundle.Builder().withIrPolicy(policy).build(), MAPPER.readValue("{}", RegoObject.class));
    return new Engine.Builder()
        .withStore(store)
        .withEntrypoint(ENTRYPOINT)
        .build()
        .prepareForEvaluation()
        .build();
  }

  /** Numbers as plain decimals, so 3.0 and 3 compare equal like the values they are. */
  private static Object canonical(Object value) {
    if (value instanceof Map) {
      final Map<String, Object> sorted = new TreeMap<>();
      ((Map<?, ?>) value).forEach((k, v) -> sorted.put(String.valueOf(k), canonical(v)));
      return new LinkedHashMap<>(sorted);
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

  private static Object expected(boolean above, boolean below) throws IOException {
    final Object parsed =
        MAPPER.readValue("{\"result\":{\"above\":" + above + ",\"below\":" + below + ",\"all\":" + ALL + "}}", Object.class);
    return canonical(parsed);
  }

  @Test
  void everyKindOfNumberConstantEvaluatesToWhatOpaReturns() throws IOException {
    final Engine.PreparedQuery query = query();

    for (int round = 0; round < 3; round++) {
      assertEquals(List.of(expected(false, true)), canonical(query.eval(Map.of("x", 0.4))), "x = 0.4");
      assertEquals(List.of(expected(false, true)), canonical(query.eval(Map.of("x", 0.5))), "x = 0.5");
      assertEquals(List.of(expected(true, true)), canonical(query.eval(Map.of("x", 0.6))), "x = 0.6");
      assertEquals(List.of(expected(true, false)), canonical(query.eval(Map.of("x", 1e30))), "x = 1e30");
    }
  }

  @Test
  void oneQueryUsedFromManyThreadsAtOnceAlwaysAnswersCorrectly() throws Exception {
    final Engine.PreparedQuery query = query();
    final Object high = canonical(query.eval(Map.of("x", 0.6)));
    final Object low = canonical(query.eval(Map.of("x", 0.4)));
    final ExecutorService pool = Executors.newFixedThreadPool(8);
    try {
      final List<Future<Boolean>> runs = new ArrayList<>();
      for (int t = 0; t < 8; t++) {
        runs.add(
            pool.submit(
                () -> {
                  boolean ok = true;
                  for (int i = 0; i < 200; i++) {
                    ok &= high.equals(canonical(query.eval(Map.of("x", 0.6))));
                    ok &= low.equals(canonical(query.eval(Map.of("x", 0.4))));
                  }
                  return ok;
                }));
      }
      for (Future<Boolean> run : runs) {
        assertTrue(run.get(), "a thread saw a different answer");
      }
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void twoQueriesOnTheSamePlanDoNotInterfere() throws IOException {
    final Engine.PreparedQuery first = query();
    final Engine.PreparedQuery second = query();

    assertEquals(canonical(first.eval(Map.of("x", 0.6))), canonical(second.eval(Map.of("x", 0.6))));
    assertEquals(canonical(first.eval(Map.of("x", 0.4))), canonical(second.eval(Map.of("x", 0.4))));
  }
}
