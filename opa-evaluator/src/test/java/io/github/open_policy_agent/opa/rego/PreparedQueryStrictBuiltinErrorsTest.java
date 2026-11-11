package io.github.open_policy_agent.opa.rego;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.open_policy_agent.opa.ast.builtin.BuiltinError;
import io.github.open_policy_agent.opa.ast.types.RegoObject;
import io.github.open_policy_agent.opa.bundle.Bundle;
import io.github.open_policy_agent.opa.ir.PolicyReader;
import io.github.open_policy_agent.opa.ir.policy.Policy;
import io.github.open_policy_agent.opa.storage.InMem;
import io.github.open_policy_agent.opa.storage.Store;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** The authz plan calls {@code internal.member_2}, which the engine replaces with a failing one. */
class PreparedQueryStrictBuiltinErrorsTest {

  private static final String ENTRYPOINT = "authz/allow";
  private static final Map<String, Object> NOBODY = Map.of("user", Map.of("id", "nobody", "groups", List.of()));

  private static Policy policy;

  @BeforeAll
  static void loadPolicy() throws IOException {
    final PolicyReader reader = ServiceLoader.load(PolicyReader.class).findFirst().orElseThrow();
    policy =
        reader.read(
            Objects.requireNonNull(
                PreparedQueryStrictBuiltinErrorsTest.class
                    .getClassLoader()
                    .getResourceAsStream("engine/testdata/authz-policy.json")));
  }

  private static Engine.Builder engineBuilder() throws IOException {
    final Store store = new InMem();
    store.write(
        ENTRYPOINT,
        new Bundle.Builder().withIrPolicy(policy).build(),
        new RegoObject());
    return new Engine.Builder().withStore(store).withEntrypoint(ENTRYPOINT);
  }

  private final AtomicInteger builtinCalls = new AtomicInteger();

  private Engine engineWhereMemberFails(RuntimeException failure) throws IOException {
    return engineBuilder()
        .withBuiltin(
            "internal.member_2",
            (ctx, args) -> {
              builtinCalls.incrementAndGet();
              throw failure;
            })
        .build();
  }

  private Engine engineWithFailingBuiltin() throws IOException {
    return engineWhereMemberFails(new BuiltinError("boom"));
  }

  @Test
  void aBuiltinErrorIsUndefinedByDefault() throws IOException {
    final Engine.PreparedQuery query = engineWithFailingBuiltin().prepareForEvaluation().build();

    assertEquals(Map.of("result", false), query.eval(NOBODY).get(0));
    assertTrue(builtinCalls.get() > 0, "the failing builtin never ran");
  }

  @Test
  void strictnessBelongsToOneQuery() throws IOException {
    final Engine engine = engineWithFailingBuiltin();
    final Engine.PreparedQuery lenient = engine.prepareForEvaluation().build();
    final Engine.PreparedQuery strict = engine.prepareForEvaluation().withStrictBuiltinErrors().build();

    assertThrows(BuiltinError.class, () -> strict.eval(NOBODY));
    assertEquals(Map.of("result", false), lenient.eval(NOBODY).get(0));
  }

  @Test
  void aStrictQueryRaisesATypeErrorToo() throws IOException {
    final Engine.PreparedQuery query =
        engineWhereMemberFails(new TypeError("x")).prepareForEvaluation().withStrictBuiltinErrors().build();

    final TypeError error = assertThrows(TypeError.class, () -> query.eval(NOBODY));

    assertTrue(error.getMessage().contains("internal.member_2"), error.getMessage());
  }

  @Test
  void aStrictQueryRaisesTheBuiltinError() throws IOException {
    final Engine.PreparedQuery query =
        engineWithFailingBuiltin().prepareForEvaluation().withStrictBuiltinErrors().build();

    final BuiltinError error = assertThrows(BuiltinError.class, () -> query.eval(NOBODY));

    assertTrue(error.getMessage().contains("internal.member_2"), error.getMessage());
  }

  @Test
  void theTypedEvalIsStrictToo() throws IOException {
    final Engine.PreparedQuery query =
        engineWithFailingBuiltin().prepareForEvaluation().withStrictBuiltinErrors().build();

    final BuiltinError error = assertThrows(BuiltinError.class, () -> query.eval(NOBODY, Object.class));

    assertTrue(error.getMessage().contains("internal.member_2"), error.getMessage());
  }

  @Test
  void aStrictQueryStillReturnsResultsWhenNoBuiltinFails() throws IOException {
    final Engine.PreparedQuery query =
        engineBuilder().build().prepareForEvaluation().withStrictBuiltinErrors().build();

    final List<Object> results = query.eval(Map.of("user", Map.of("id", "alice", "groups", List.of())));

    assertEquals(Map.of("result", true), results.get(0));
  }
}
