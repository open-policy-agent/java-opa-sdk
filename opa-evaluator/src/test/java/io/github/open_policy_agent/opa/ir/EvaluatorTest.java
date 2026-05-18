package io.github.open_policy_agent.opa.ir;

import io.github.open_policy_agent.opa.ast.types.RegoObject;
import io.github.open_policy_agent.opa.ast.types.RegoValue;
import io.github.open_policy_agent.opa.ir.policy.Block;
import io.github.open_policy_agent.opa.ir.policy.Func;
import io.github.open_policy_agent.opa.ir.policy.Funcs;
import io.github.open_policy_agent.opa.ir.policy.Plan;
import io.github.open_policy_agent.opa.ir.policy.Plans;
import io.github.open_policy_agent.opa.ir.policy.Policy;
import io.github.open_policy_agent.opa.ir.policy.Static;
import io.github.open_policy_agent.opa.ir.policy.StringConst;
import io.github.open_policy_agent.opa.rego.EvaluationContext;
import io.github.open_policy_agent.opa.tracing.BufferedQueryTracer;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class EvaluatorTest {
  private static final PolicyReader policyReader =
      ServiceLoader.load(PolicyReader.class).findFirst().orElseThrow();
  private static final ObjectMapper objectMapper = new ObjectMapper().registerModule(new io.github.open_policy_agent.opa.jackson.RegoValueModule());

  @Test
  void evaluate_BreakStmt_IndexZero() throws IOException {
    File jsonFile =
        new File(
            Objects.requireNonNull(
                    getClass()
                        .getClassLoader()
                        .getResource("ir/testdata/policy-verify-BreakStmt.json"))
                .getFile());

    Policy policy = policyReader.read(Files.newInputStream(jsonFile.toPath()));
    BufferedQueryTracer tracer = new BufferedQueryTracer();
    Evaluator evaluator = new Evaluator.Builder().withPolicy(policy).build();
    EvaluationContext ctx =
        new EvaluationContext.Builder().withEntrypoint("policy").withTracer(tracer).build();
    evaluator.evaluate(ctx, new RegoObject(), new RegoObject());

    List<StatementEvent> golden =
        loadExpectedEventsFromGoldenFile("ir/testdata/evaluate_BreakStmt_IndexZero.golden");
    assertEquals(golden, statementEvents(tracer));
  }

  @Test
  void evaluate_BreakStmt_IndexNonZero() throws IOException {
    File jsonFile =
        new File(
            Objects.requireNonNull(
                    getClass()
                        .getClassLoader()
                        .getResource("ir/testdata/policy-verify-BreakStmt-non-zero-index.json"))
                .getFile());

    Policy policy = policyReader.read(Files.newInputStream(jsonFile.toPath()));

    BufferedQueryTracer tracer = new BufferedQueryTracer();
    Evaluator evaluator = new Evaluator.Builder().withPolicy(policy).build();
    EvaluationContext ctx =
        new EvaluationContext.Builder().withEntrypoint("policy").withTracer(tracer).build();
    evaluator.evaluate(ctx, new RegoObject(), new RegoObject());

    List<StatementEvent> golden =
        loadExpectedEventsFromGoldenFile("ir/testdata/evaluate_BreakStmt_IndexNonZero.golden");
    assertEquals(golden, statementEvents(tracer));
  }

  @Test
  void evaluate_BreakStmt_IndexGreaterThanOne() throws IOException {
    File jsonFile =
        new File(
            Objects.requireNonNull(
                    getClass()
                        .getClassLoader()
                        .getResource(
                            "ir/testdata/policy-verify-BreakStmt-greater-than-one-index.json"))
                .getFile());

    Policy policy = policyReader.read(Files.newInputStream(jsonFile.toPath()));
    BufferedQueryTracer tracer = new BufferedQueryTracer();

    Evaluator evaluator = new Evaluator.Builder().withPolicy(policy).build();
    EvaluationContext ctx =
        new EvaluationContext.Builder().withEntrypoint("policy").withTracer(tracer).build();
    evaluator.evaluate(ctx, new RegoObject(), new RegoObject());

    List<StatementEvent> golden =
        loadExpectedEventsFromGoldenFile(
            "ir/testdata/evaluate_BreakStmt_IndexGreaterThanOne.golden");
    assertEquals(golden, statementEvents(tracer));
  }

  private List<StatementEvent> statementEvents(BufferedQueryTracer tracer) {
    return tracer.getEvents().stream()
        .filter(StatementEvent.class::isInstance)
        .map(StatementEvent.class::cast)
        .collect(Collectors.toList());
  }

  private List<StatementEvent> loadExpectedEventsFromGoldenFile(String filePath)
      throws IOException {
    List<StatementEvent> events = new ArrayList<>();
    try (BufferedReader reader =
        new BufferedReader(
            new InputStreamReader(
                Objects.requireNonNull(
                    getClass().getClassLoader().getResourceAsStream(filePath))))) {
      String line;
      while ((line = reader.readLine()) != null) {
        events.add(StatementEvent.fromString(line));
      }
    }
    return events;
  }

  @Test
  void preparedPlan_warmUp_findsEntrypoint() {
    // Setup a policy with an entrypoint plan
    Block block = new Block(List.of());
    Plan entrypointPlan = new Plan("test_entrypoint", List.of(block));
    Plans plans = new Plans(List.of(entrypointPlan));
    Policy policy = new Policy(null, plans, null);
    Evaluator evaluator = new Evaluator.Builder().withPolicy(policy).build();

    // Warm up with the entrypoint
    PreparedPlan preparedPlan =
        new PreparedPlan.Builder()
            .warmUp("test_entrypoint", evaluator)
            .build();

    // Verify the entrypoint plan was used
    assertNotNull(preparedPlan.getPlan());
    assertEquals("test_entrypoint", preparedPlan.getPlan().getName());
  }

  @Test
  void preparedPlan_warmUp_throwsWhenPlanNotFound() {
    // Setup a policy with a plan
    Block block = new Block(List.of());
    Plan plan = new Plan("some_plan", List.of(block));
    Plans plans = new Plans(List.of(plan));
    Policy policy = new Policy(null, plans, null);
    Evaluator evaluator = new Evaluator.Builder().withPolicy(policy).build();

    // Warm up with an entrypoint that doesn't exist
    PreparedPlan.Builder builder = new PreparedPlan.Builder();

    PolicyNotFoundException exception =
        assertThrows(
            PolicyNotFoundException.class,
            () -> builder.warmUp("nonexistent_entrypoint", evaluator));

    // Verify exception message contains the entrypoint
    assertTrue(exception.getMessage().contains("nonexistent_entrypoint"));
  }

  @Test
  void preparedPlan_warmUp_computesFrameCapacity() {
    // Create blocks - frame capacity is computed from the block's maxLocal
    Block block = new Block(List.of());
    Plan plan = new Plan("test_entrypoint", List.of(block));
    Plans plans = new Plans(List.of(plan));
    Policy policy = new Policy(null, plans, null);
    Evaluator evaluator = new Evaluator.Builder().withPolicy(policy).build();

    // Warm up the plan
    PreparedPlan preparedPlan =
        new PreparedPlan.Builder().warmUp("test_entrypoint", evaluator).build();

    // Verify frame capacity is Math.max(maxLocals + 1, 2)
    // For an empty block, maxLocal is -1, so capacity is 0, but minimum is 2 for input and data
    int expectedCapacity = Math.max(plan.getMaxLocals() + 1, 2);
    assertEquals(expectedCapacity, preparedPlan.getFrameCapacity());
  }

  @Test
  void preparedPlan_warmUp_buildsFunctionsByPathMap() {
    // Setup functions with paths
    Func func1 = new Func();
    func1.setName("func1");
    func1.setPath(List.of("data", "test", "func1"));

    Func func2 = new Func();
    func2.setName("func2");
    func2.setPath(List.of("data", "test", "func2"));

    Func func3 = new Func();
    func3.setName("func3");
    // No path set

    // Setup policy with functions
    Block block = new Block(List.of());
    Plan plan = new Plan("test_entrypoint", List.of(block));
    Plans plans = new Plans(List.of(plan));
    Funcs funcs = new Funcs(List.of(func1, func2, func3));
    Policy policy = new Policy(null, plans, funcs);
    Evaluator evaluator = new Evaluator.Builder().withPolicy(policy).build();

    // Warm up the plan
    PreparedPlan preparedPlan =
        new PreparedPlan.Builder().warmUp("test_entrypoint", evaluator).build();

    // Verify functionsByPath map was built correctly
    Map<String, Func> functionsByPath = preparedPlan.getFunctionsByPath();
    assertNotNull(functionsByPath);
    assertEquals(2, functionsByPath.size()); // Only func1 and func2 have paths
    assertTrue(functionsByPath.containsKey("data.test.func1"));
    assertTrue(functionsByPath.containsKey("data.test.func2"));
    assertEquals(func1, functionsByPath.get("data.test.func1"));
    assertEquals(func2, functionsByPath.get("data.test.func2"));
  }

  @Test
  void preparedPlan_warmUp_cachesStaticStrings() {
    // Setup static strings
    List<StringConst> stringConsts =
        List.of(new StringConst("str1"), new StringConst("str2"), new StringConst("str3"));
    Static staticData = new Static(stringConsts, null, null);

    // Setup policy with static strings
    Block block = new Block(List.of());
    Plan plan = new Plan("test_entrypoint", List.of(block));
    Plans plans = new Plans(List.of(plan));
    Policy policy = new Policy(staticData, plans, null);
    Evaluator evaluator = new Evaluator.Builder().withPolicy(policy).build();

    // Warm up the plan
    PreparedPlan preparedPlan =
        new PreparedPlan.Builder().warmUp("test_entrypoint", evaluator).build();

    // Verify static strings reference was cached
    assertNotNull(preparedPlan.getStaticStrings());
    assertEquals(3, preparedPlan.getStaticStrings().size());
    assertEquals("str1", preparedPlan.getStaticStrings().get(0));
    assertEquals("str2", preparedPlan.getStaticStrings().get(1));
    assertEquals("str3", preparedPlan.getStaticStrings().get(2));
  }

  @Test
  void preparedPlan_warmUp_cachesFuncRegistry() {
    // Setup function registry
    Func func1 = new Func();
    func1.setName("func1");
    Funcs funcs = new Funcs(List.of(func1));

    // Setup policy with functions
    Block block = new Block(List.of());
    Plan plan = new Plan("test_entrypoint", List.of(block));
    Plans plans = new Plans(List.of(plan));
    Policy policy = new Policy(null, plans, funcs);
    Evaluator evaluator = new Evaluator.Builder().withPolicy(policy).build();

    // Warm up the plan
    PreparedPlan preparedPlan =
        new PreparedPlan.Builder().warmUp("test_entrypoint", evaluator).build();

    // Verify function registry reference was cached
    assertNotNull(preparedPlan.getFuncRegistry());
    assertTrue(preparedPlan.getFuncRegistry().containsKey("func1"));
    assertEquals(func1, preparedPlan.getFuncRegistry().get("func1"));
  }

  @Test
  void evaluate_withPreparedPlan_producesSameResultAsRegularEvaluate() throws IOException {
    // Load a real policy from test data
    File jsonFile =
        new File(
            Objects.requireNonNull(
                    getClass()
                        .getClassLoader()
                        .getResource("ir/testdata/policy-verify-BreakStmt.json"))
                .getFile());

    Policy policy = policyReader.read(Files.newInputStream(jsonFile.toPath()));
    Evaluator evaluator = new Evaluator.Builder().withPolicy(policy).build();
    EvaluationContext ctx = new EvaluationContext.Builder().withEntrypoint("policy").build();

    RegoObject input = new RegoObject();
    RegoObject data = new RegoObject();

    // Evaluate with regular method
    RegoValue[] regularResult = evaluator.evaluate(ctx, input, data);

    // Warm up plan and evaluate with PreparedPlan
    PreparedPlan preparedPlan = new PreparedPlan.Builder().warmUp("policy", evaluator).build();
    RegoValue[] preparedResult = evaluator.evaluate(preparedPlan, ctx, input, data);

    // Results should be identical
    assertEquals(regularResult.length, preparedResult.length);
    for (int i = 0; i < regularResult.length; i++) {
      assertEquals(
          objectMapper.writeValueAsString(regularResult[i]),
          objectMapper.writeValueAsString(preparedResult[i]));
    }
  }

  @Test
  void evaluator_builder_throwsWhenPolicyIsNull() {
    Evaluator.Builder builder = new Evaluator.Builder();

    PolicyNotFoundException exception =
        assertThrows(PolicyNotFoundException.class, builder::build);

    assertTrue(exception.getMessage().contains("null"));
  }

  @Test
  void evaluator_builder_buildsFuncRegistryFromPolicy() {
    // Setup policy with functions
    Func func1 = new Func();
    func1.setName("func1");
    Func func2 = new Func();
    func2.setName("func2");
    Funcs funcs = new Funcs(List.of(func1, func2));

    Block block = new Block(List.of());
    Plan plan = new Plan("test_entrypoint", List.of(block));
    Plans plans = new Plans(List.of(plan));
    Policy policy = new Policy(null, plans, funcs);

    Evaluator evaluator = new Evaluator.Builder().withPolicy(policy).build();

    // Verify funcRegistry was built correctly
    assertNotNull(evaluator.getFuncRegistry());
    assertEquals(2, evaluator.getFuncRegistry().size());
    assertTrue(evaluator.getFuncRegistry().containsKey("func1"));
    assertTrue(evaluator.getFuncRegistry().containsKey("func2"));
  }

  @Test
  void evaluator_builder_buildsStaticStringsFromPolicy() {
    // Setup policy with static strings
    List<StringConst> stringConsts =
        List.of(new StringConst("str1"), new StringConst("str2"), new StringConst("str3"));
    Static staticData = new Static(stringConsts, null, null);

    Block block = new Block(List.of());
    Plan plan = new Plan("test_entrypoint", List.of(block));
    Plans plans = new Plans(List.of(plan));
    Policy policy = new Policy(staticData, plans, null);

    Evaluator evaluator = new Evaluator.Builder().withPolicy(policy).build();

    // Verify staticStrings was built correctly
    assertNotNull(evaluator.getStaticStrings());
    assertEquals(3, evaluator.getStaticStrings().size());
    assertEquals("str1", evaluator.getStaticStrings().get(0));
    assertEquals("str2", evaluator.getStaticStrings().get(1));
    assertEquals("str3", evaluator.getStaticStrings().get(2));
  }

  @Test
  void evaluator_evaluate_throwsWhenPlanNotFound() {
    Block block = new Block(List.of());
    Plan plan = new Plan("existing_plan", List.of(block));
    Plans plans = new Plans(List.of(plan));
    Policy policy = new Policy(null, plans, null);
    Evaluator evaluator = new Evaluator.Builder().withPolicy(policy).build();

    EvaluationContext ctx =
        new EvaluationContext.Builder()
            .withEntrypoint("nonexistent_plan")
            .build();

    PolicyNotFoundException exception =
        assertThrows(
            PolicyNotFoundException.class,
            () -> evaluator.evaluate(ctx, new RegoObject(), new RegoObject()));

    assertTrue(exception.getMessage().contains("nonexistent_plan"));
  }

  @Test
  void evaluator_evaluate_usesEntrypointWhenQueryNotFound() {
    Block block = new Block(List.of());
    Plan entrypointPlan = new Plan("my_entrypoint", List.of(block));
    Plans plans = new Plans(List.of(entrypointPlan));
    Policy policy = new Policy(null, plans, null);
    Evaluator evaluator = new Evaluator.Builder().withPolicy(policy).build();

    EvaluationContext ctx =
        new EvaluationContext.Builder()
            .withEntrypoint("my_entrypoint")
            .build();

    // Should not throw - uses entrypoint as fallback
    RegoValue[] result = evaluator.evaluate(ctx, new RegoObject(), new RegoObject());

    assertNotNull(result);
  }

  @Test
  void preparedPlan_warmUp_handlesNullFuncRegistry() {
    // Setup policy without functions
    Block block = new Block(List.of());
    Plan plan = new Plan("test_entrypoint", List.of(block));
    Plans plans = new Plans(List.of(plan));
    Policy policy = new Policy(null, plans, null);
    Evaluator evaluator = new Evaluator.Builder().withPolicy(policy).build();

    // Warm up the plan
    PreparedPlan preparedPlan =
        new PreparedPlan.Builder().warmUp("test_entrypoint", evaluator).build();

    // Should handle null funcRegistry gracefully
    assertNotNull(preparedPlan);
    // functionsByPath should be null or empty when funcRegistry is null
    if (preparedPlan.getFunctionsByPath() != null) {
      assertTrue(preparedPlan.getFunctionsByPath().isEmpty());
    }
  }

  @Test
  void preparedPlan_warmUp_handlesEmptyStaticStrings() {
    // Setup policy without static strings
    Block block = new Block(List.of());
    Plan plan = new Plan("test_entrypoint", List.of(block));
    Plans plans = new Plans(List.of(plan));
    Policy policy = new Policy(null, plans, null);
    Evaluator evaluator = new Evaluator.Builder().withPolicy(policy).build();

    // Warm up the plan
    PreparedPlan preparedPlan =
        new PreparedPlan.Builder().warmUp("test_entrypoint", evaluator).build();

    // Should handle empty static strings gracefully
    assertNotNull(preparedPlan);
    assertNotNull(preparedPlan.getStaticStrings());
    assertTrue(preparedPlan.getStaticStrings().isEmpty());
  }
}
