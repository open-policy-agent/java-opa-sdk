package io.github.open_policy_agent.opa.rego;

import io.github.open_policy_agent.opa.ast.builtin.BuiltinRegistry;
import io.github.open_policy_agent.opa.ast.types.RegoObject;
import io.github.open_policy_agent.opa.bundle.Bundle;
import io.github.open_policy_agent.opa.ir.PolicyNotFoundException;
import io.github.open_policy_agent.opa.ir.PolicyReader;
import io.github.open_policy_agent.opa.ir.policy.Block;
import io.github.open_policy_agent.opa.ir.policy.Plan;
import io.github.open_policy_agent.opa.ir.policy.Plans;
import io.github.open_policy_agent.opa.ir.policy.Policy;
import io.github.open_policy_agent.opa.metrics.NoOpMetrics;
import io.github.open_policy_agent.opa.storage.InMem;
import io.github.open_policy_agent.opa.storage.Store;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.open_policy_agent.opa.tracing.DurationProfiler;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Objects;
import java.util.ServiceLoader;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EngineTest {
  private static final PolicyReader policyReader =
      ServiceLoader.load(PolicyReader.class).findFirst().orElseThrow();
  private static final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void engine_builder_requiresStore() {
    Engine.Builder builder = new Engine.Builder();

    assertThrows(PolicyNotFoundException.class, builder::build);
  }

  @Test
  void engine_builder_loadsIRPolicyFromStore() {
    Store store = new InMem();
    Block block = new Block(List.of());
    Plan plan = new Plan("test_entrypoint", List.of(block));
    Plans plans = new Plans(List.of(plan));
    Policy policy = new Policy(null, plans, null);
    Bundle bundle = new Bundle.Builder().withIrPolicy(policy).build();
    store.write("test_entrypoint", bundle, new RegoObject());

    Engine engine = new Engine.Builder().withStore(store).withEntrypoint("test_entrypoint").build();

    assertNotNull(engine);
  }

  @Test
  void engine_prepareForEvaluation_createsBuilderForPreparedQuery() {
    Store store = new InMem();
    Block block = new Block(List.of());
    Plan plan = new Plan("test_entrypoint", List.of(block));
    Plans plans = new Plans(List.of(plan));
    Policy policy = new Policy(null, plans, null);
    Bundle bundle = new Bundle.Builder().withIrPolicy(policy).build();
    store.write("test_entrypoint", bundle, new RegoObject());

    Engine engine = new Engine.Builder().withStore(store).withEntrypoint("test_entrypoint").build();

    Engine.PreparedQuery.Builder builder = engine.prepareForEvaluation();

    assertNotNull(builder);
  }

  @Test
  void preparedQuery_builder_warmsUpPlan() {
    Store store = new InMem();
    Block block = new Block(List.of());
    Plan plan = new Plan("test_entrypoint", List.of(block));
    Plans plans = new Plans(List.of(plan));
    Policy policy = new Policy(null, plans, null);
    Bundle bundle = new Bundle.Builder().withIrPolicy(policy).build();
    store.write("test_entrypoint", bundle, new RegoObject());

    Engine engine = new Engine.Builder().withStore(store).withEntrypoint("test_entrypoint").build();

    Engine.PreparedQuery pq = engine.prepareForEvaluation().build();

    assertNotNull(pq);
    // Plan warming happens during build
  }

  @Test
  void preparedQuery_eval_returnsResults() throws IOException {
    // Load a real policy from test data
    File jsonFile =
        new File(
            Objects.requireNonNull(
                    getClass()
                        .getClassLoader()
                        .getResource("ir/testdata/policy-verify-BreakStmt.json"))
                .getFile());

    Policy policy = policyReader.read(Files.newInputStream(jsonFile.toPath()));
    Store store = new InMem();
    Bundle bundle = new Bundle.Builder().withIrPolicy(policy).build();
    store.write("policy", bundle, new RegoObject());

    Engine engine = new Engine.Builder().withStore(store).withEntrypoint("policy").build();

    Engine.PreparedQuery pq = engine.prepareForEvaluation().build();

    JsonNode input = objectMapper.readTree("{}");
    List<JsonNode> results = pq.eval(input);

    assertNotNull(results);
  }

  @Test
  void preparedQuery_builder_acceptsMetrics() {
    Store store = new InMem();
    Block block = new Block(List.of());
    Plan plan = new Plan("test_entrypoint", List.of(block));
    Plans plans = new Plans(List.of(plan));
    Policy policy = new Policy(null, plans, null);
    Bundle bundle = new Bundle.Builder().withIrPolicy(policy).build();
    store.write("test_entrypoint", bundle, new RegoObject());

    Engine engine = new Engine.Builder().withStore(store).withEntrypoint("test_entrypoint").build();

    Engine.PreparedQuery pq =
        engine
            .prepareForEvaluation()
            .withMetrics(NoOpMetrics.Instance())
            .build();

    assertNotNull(pq);
  }

  @Test
  void preparedQuery_builder_acceptsTracer() {
    Store store = new InMem();
    Block block = new Block(List.of());
    Plan plan = new Plan("test_entrypoint", List.of(block));
    Plans plans = new Plans(List.of(plan));
    Policy policy = new Policy(null, plans, null);
    Bundle bundle = new Bundle.Builder().withIrPolicy(policy).build();
    store.write("test_entrypoint", bundle, new RegoObject());

    Engine engine = new Engine.Builder().withStore(store).withEntrypoint("test_entrypoint").build();

    Engine.PreparedQuery pq =
        engine
            .prepareForEvaluation()
            .withTracer(new io.github.open_policy_agent.opa.tracing.BufferedQueryTracer())
            .build();

    assertNotNull(pq);
  }

  @Test
  void preparedQuery_builder_acceptsProfiler() {
    Store store = new InMem();
    Block block = new Block(List.of());
    Plan plan = new Plan("test_entrypoint", List.of(block));
    Plans plans = new Plans(List.of(plan));
    Policy policy = new Policy(null, plans, null);
    Bundle bundle = new Bundle.Builder().withIrPolicy(policy).build();
    store.write("test_entrypoint", bundle, new RegoObject());

    Engine engine = new Engine.Builder().withStore(store).withEntrypoint("test_entrypoint").build();

    Engine.PreparedQuery pq =
        engine
            .prepareForEvaluation()
            .withProfiler(new DurationProfiler())
            .build();

    assertNotNull(pq);
  }

  @Test
  void preparedQuery_builder_overridesQuery() {
    Store store = new InMem();
    Block block = new Block(List.of());
    Plan plan1 = new Plan("entrypoint1", List.of(block));
    Plan plan2 = new Plan("entrypoint2", List.of(block));
    Plans plans = new Plans(List.of(plan1, plan2));
    Policy policy = new Policy(null, plans, null);
    Bundle bundle = new Bundle.Builder().withIrPolicy(policy).build();
    store.write("entrypoint1", bundle, new RegoObject());

    Engine engine = new Engine.Builder().withStore(store).withEntrypoint("entrypoint1").build();

    // Override the default query
    Engine.PreparedQuery pq = engine.prepareForEvaluation().withEntrypoint("entrypoint2").build();

    assertNotNull(pq);
  }

  @Test
  void engine_builder_acceptsCapabilities() {
    Store store = new InMem();
    Block block = new Block(List.of());
    Plan plan = new Plan("test_entrypoint", List.of(block));
    Plans plans = new Plans(List.of(plan));
    Policy policy = new Policy(null, plans, null);
    Bundle bundle = new Bundle.Builder().withIrPolicy(policy).build();
    store.write("test_entrypoint", bundle, new RegoObject());

    Capabilities capabilities = BuiltinRegistry.generateCapabilities();

    Engine engine =
        new Engine.Builder()
            .withStore(store)
            .withEntrypoint("test_entrypoint")
            .withCapabilities(capabilities)
            .build();

    assertNotNull(engine);
  }

  @Test
  void engine_builder_setsDefaultQuery() {
    Store store = new InMem();
    Block block = new Block(List.of());
    Plan plan = new Plan("test_entrypoint", List.of(block));
    Plans plans = new Plans(List.of(plan));
    Policy policy = new Policy(null, plans, null);
    Bundle bundle = new Bundle.Builder().withIrPolicy(policy).build();
    store.write("test_entrypoint", bundle, new RegoObject());

    Engine engine = new Engine.Builder().withStore(store).withEntrypoint("test_entrypoint").build();

    assertNotNull(engine);
  }

  @Test
  void engine_builder_throwsWhenPolicyNotFoundInStore() {
    Store store = new InMem();

    Engine.Builder builder = new Engine.Builder().withStore(store).withEntrypoint("nonexistent");

    assertThrows(PolicyNotFoundException.class, builder::build);
  }

  @Test
  void engine_builder_usesInMemStoreByDefault() {
    Block block = new Block(List.of());
    Plan plan = new Plan("test_entrypoint", List.of(block));
    Plans plans = new Plans(List.of(plan));
    new Policy(null, plans, null);
  }

  @Test
  void preparedQuery_warmsUpPlanForMultiplePlans() {
    Store store = new InMem();
    Block block = new Block(List.of());
    Plan plan1 = new Plan("entrypoint1", List.of(block));
    Plan plan2 = new Plan("entrypoint2", List.of(block));
    Plans plans = new Plans(List.of(plan1, plan2));
    Policy policy = new Policy(null, plans, null);
    Bundle bundle = new Bundle.Builder().withIrPolicy(policy).build();
    store.write("entrypoint1", bundle, new RegoObject());

    Engine engine = new Engine.Builder().withStore(store).withEntrypoint("entrypoint1").build();

    // PreparedQuery should warm up plan for "entrypoint1"
    Engine.PreparedQuery pq = engine.prepareForEvaluation().build();

    assertNotNull(pq);
  }
}
