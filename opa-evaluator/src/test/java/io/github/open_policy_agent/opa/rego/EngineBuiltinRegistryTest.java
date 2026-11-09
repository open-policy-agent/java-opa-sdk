package io.github.open_policy_agent.opa.rego;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.open_policy_agent.opa.ast.builtin.BuiltinRegistry;
import io.github.open_policy_agent.opa.ast.types.RegoBoolean;
import io.github.open_policy_agent.opa.ast.types.RegoObject;
import io.github.open_policy_agent.opa.ast.types.RegoValue;
import io.github.open_policy_agent.opa.bundle.Bundle;
import io.github.open_policy_agent.opa.ir.PolicyReader;
import io.github.open_policy_agent.opa.ir.policy.Policy;
import io.github.open_policy_agent.opa.storage.InMem;
import io.github.open_policy_agent.opa.storage.Store;
import java.io.IOException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/**
 * The authz plan calls {@code internal.member_2}. Replacing that builtin on the engine shows which
 * registry a decision really used.
 */
class EngineBuiltinRegistryTest {

  private static final String ENTRYPOINT = "authz/allow";
  private static final Map<String, Object> NOBODY = Map.of("user", Map.of("id", "nobody", "groups", List.of()));

  private static Policy policy;

  private final AtomicInteger calls = new AtomicInteger();
  private final Set<BuiltinRegistry> seenRegistries = Collections.newSetFromMap(new IdentityHashMap<>());

  @BeforeAll
  static void loadPolicy() throws IOException {
    final PolicyReader reader = ServiceLoader.load(PolicyReader.class).findFirst().orElseThrow();
    policy =
        reader.read(
            Objects.requireNonNull(
                    EngineBuiltinRegistryTest.class
                        .getClassLoader()
                        .getResourceAsStream("engine/testdata/authz-policy.json")));
  }

  private Engine engineWithMemberAlwaysTrue() throws IOException {
    final Store store = new InMem();
    store.write(
        ENTRYPOINT,
        new Bundle.Builder().withIrPolicy(policy).build(),
        JsonMapper.shared().readValue("{}", RegoObject.class));
    return new Engine.Builder()
        .withStore(store)
        .withEntrypoint(ENTRYPOINT)
        .withBuiltin(
            "internal.member_2",
            (ctx, args) -> {
              calls.incrementAndGet();
              seenRegistries.add(ctx.builtinRegistry);
              return RegoBoolean.TRUE;
            })
        .build();
  }

  private Engine engineWithoutCustomBuiltins() throws IOException {
    final Store store = new InMem();
    store.write(
        ENTRYPOINT,
        new Bundle.Builder().withIrPolicy(policy).build(),
        JsonMapper.shared().readValue("{}", RegoObject.class));
    return new Engine.Builder().withStore(store).withEntrypoint(ENTRYPOINT).build();
  }

  private static EvaluationContext contextWithoutARegistry() {
    return new EvaluationContext.Builder().withEntrypoint(ENTRYPOINT).build();
  }

  @Test
  void aContextWithoutARegistryUsesTheEnginesBuiltins() throws IOException {
    final Engine engine = engineWithMemberAlwaysTrue();

    final List<Object> results = engine.evaluate(contextWithoutARegistry(), NOBODY);

    assertTrue(calls.get() > 0, "the engine's replacement builtin was never called");
    assertFalse(results.isEmpty());
  }

  @Test
  void theTypedOverloadUsesTheEnginesBuiltinsToo() throws IOException {
    final Engine engine = engineWithMemberAlwaysTrue();

    engine.evaluate(contextWithoutARegistry(), NOBODY, Object.class);

    assertTrue(calls.get() > 0, "the engine's replacement builtin was never called");
  }

  @Test
  void everyDecisionSeesTheSameRegistryInsteadOfBuildingOne() throws IOException {
    final Engine engine = engineWithMemberAlwaysTrue();

    for (int i = 0; i < 3; i++) {
      engine.evaluate(contextWithoutARegistry(), NOBODY);
    }

    assertEquals(1, seenRegistries.size());
  }

  @Test
  void aRegistryTheCallerPutInTheContextIsNotReplaced() throws IOException {
    final Engine engine = engineWithMemberAlwaysTrue();
    final BuiltinRegistry callers = BuiltinRegistry.allCapabilities();
    final EvaluationContext ctx =
        new EvaluationContext.Builder().withEntrypoint(ENTRYPOINT).withBuiltinRegistry(callers).build();

    engine.evaluate(ctx, NOBODY);

    assertEquals(0, calls.get());
    assertSame(callers, ctx.builtinRegistry);
  }

  @Test
  void aContextReusedAcrossEnginesUsesEachEnginesOwnBuiltins() throws IOException {
    final Engine replacing = engineWithMemberAlwaysTrue();
    final Engine plain = engineWithoutCustomBuiltins();
    final EvaluationContext shared = contextWithoutARegistry();

    replacing.evaluate(shared, NOBODY);
    final int callsAfterFirst = calls.get();
    final List<Object> plainResult = plain.evaluate(shared, NOBODY);

    assertEquals(callsAfterFirst, calls.get(), "the first engine's builtin leaked into the second");
    assertEquals(false, ((Map<?, ?>) plainResult.get(0)).get("result"));
  }

  @Test
  void anEvaluatorWithoutARegistryFallsBackToAllCapabilities() {
    final io.github.open_policy_agent.opa.ir.Evaluator evaluator =
        new io.github.open_policy_agent.opa.ir.Evaluator.Builder().withPolicy(policy).build();
    final RegoValue input = JsonMapper.shared().readValue("{\"user\":{\"id\":\"alice\",\"groups\":[]}}", RegoObject.class);

    final RegoValue[] results = evaluator.evaluate(contextWithoutARegistry(), input, new RegoObject());

    assertEquals(1, results.length);
  }
}
