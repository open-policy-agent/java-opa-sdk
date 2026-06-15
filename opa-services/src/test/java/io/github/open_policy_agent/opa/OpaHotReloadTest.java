package io.github.open_policy_agent.opa;

import io.github.open_policy_agent.opa.ast.types.RegoObject;
import io.github.open_policy_agent.opa.bundle.Bundle;
import io.github.open_policy_agent.opa.config.Config;
import io.github.open_policy_agent.opa.ir.PolicyReader;
import io.github.open_policy_agent.opa.ir.policy.Policy;
import io.github.open_policy_agent.opa.logging.Logger;
import io.github.open_policy_agent.opa.plugins.PluginManager;
import io.github.open_policy_agent.opa.rego.Engine;
import io.github.open_policy_agent.opa.rego.EvaluationContext;
import io.github.open_policy_agent.opa.storage.InMem;
import io.github.open_policy_agent.opa.storage.Store;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Integration test for hot policy reload via the BundleActivationListener mechanism.
 *
 * <p>Simulates the full notification chain: store.write() -> notifyBundleActivation() ->
 * engine.refresh() without requiring actual bundle polling.
 */
class OpaHotReloadTest {

  private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new io.github.open_policy_agent.opa.jackson.RegoValueModule());
  private static final PolicyReader POLICY_READER =
      ServiceLoader.load(PolicyReader.class).findFirst().orElseThrow();

  private static final String ENTRYPOINT = "test/allow";

  /**
   * Minimal IR plan that returns {@code {"result": true}}. Simulates a policy that allows.
   */
  private static final String ALLOW_PLAN =
      "{\"static\":{\"strings\":[{\"value\":\"result\"}],\"files\":[]},"
          + "\"plans\":{\"plans\":[{\"name\":\"test/allow\",\"blocks\":["
          + "{\"stmts\":["
          + "{\"type\":\"MakeObjectStmt\",\"stmt\":{\"target\":2}},"
          + "{\"type\":\"AssignVarStmt\",\"stmt\":{\"source\":{\"type\":\"bool\",\"value\":true},\"target\":3}},"
          + "{\"type\":\"ObjectInsertStmt\",\"stmt\":{\"key\":{\"type\":\"string_index\",\"value\":0},\"value\":{\"type\":\"local\",\"value\":3},\"object\":2}},"
          + "{\"type\":\"ResultSetAddStmt\",\"stmt\":{\"value\":2}}"
          + "]}"
          + "]}]},"
          + "\"funcs\":{\"funcs\":[]}}";

  /**
   * Minimal IR plan that returns {@code {"result": false}}. Simulates a deny policy.
   */
  private static final String DENY_PLAN =
      "{\"static\":{\"strings\":[{\"value\":\"result\"}],\"files\":[]},"
          + "\"plans\":{\"plans\":[{\"name\":\"test/allow\",\"blocks\":["
          + "{\"stmts\":["
          + "{\"type\":\"MakeObjectStmt\",\"stmt\":{\"target\":2}},"
          + "{\"type\":\"AssignVarStmt\",\"stmt\":{\"source\":{\"type\":\"bool\",\"value\":false},\"target\":3}},"
          + "{\"type\":\"ObjectInsertStmt\",\"stmt\":{\"key\":{\"type\":\"string_index\",\"value\":0},\"value\":{\"type\":\"local\",\"value\":3},\"object\":2}},"
          + "{\"type\":\"ResultSetAddStmt\",\"stmt\":{\"value\":2}}"
          + "]}"
          + "]}]},"
          + "\"funcs\":{\"funcs\":[]}}";

  private static Policy denyPolicy() throws IOException {
    return POLICY_READER.read(new ByteArrayInputStream(DENY_PLAN.getBytes()));
  }

  private static Policy allowPolicy() throws IOException {
    return POLICY_READER.read(new ByteArrayInputStream(ALLOW_PLAN.getBytes()));
  }

  /** Bridge from Engine's POJO results to JsonNode for test assertions. */
  private static List<JsonNode> evalJson(Engine engine, EvaluationContext ctx, JsonNode input) {
    Object pojoInput = MAPPER.convertValue(input, Object.class);
    List<Object> raw = engine.evaluate(ctx, pojoInput);
    List<JsonNode> out = new ArrayList<>(raw.size());
    for (Object o : raw) {
      out.add(MAPPER.valueToTree(o));
    }
    return out;
  }

  @Test
  void bundleActivationListener_triggersEngineRefresh() throws IOException {
    Store store = new InMem();
    Bundle bundle = new Bundle.Builder().withIrPolicy(allowPolicy()).build();
    store.write(ENTRYPOINT, bundle, new RegoObject());

    Engine engine = new Engine.Builder().withStore(store).withEntrypoint(ENTRYPOINT).build();

    Logger logger = mock(Logger.class);
    PluginManager manager =
        new PluginManager.Builder()
            .withId("test-opa")
            .withStore(store)
            .withConfig(new Config())
            .withLogger(logger)
            .build();

    manager.registerBundleActivationListener(
        (bundleName) -> engine.refresh());

    // Verify initial: allow policy produces results
    JsonNode input = MAPPER.readTree("{}");
    EvaluationContext ctx =
        new EvaluationContext.Builder().withStore(store).withEntrypoint(ENTRYPOINT).build();
    List<JsonNode> results = evalJson(engine, ctx, input);
    assertNotNull(results);
    assertFalse(results.isEmpty(), "allow policy should produce results");

    // Simulate a bundle hot reload: write deny policy + notify
    Bundle denyBundle = new Bundle.Builder().withIrPolicy(denyPolicy()).build();
    store.write(ENTRYPOINT, denyBundle, new RegoObject());
    manager.notifyBundleActivation("test-bundle");

    EvaluationContext ctx2 =
        new EvaluationContext.Builder().withStore(store).withEntrypoint(ENTRYPOINT).build();
    List<JsonNode> refreshedResults = evalJson(engine, ctx2, input);
    assertFalse(
        refreshedResults.get(0).get("result").asBoolean(),
        "after bundle activation, deny policy should return false");
  }

  @Test
  void bundleActivationListener_policyChangeRequiresRefresh_dataIsLive() throws IOException {
    Store store = new InMem();
    Bundle bundle = new Bundle.Builder().withIrPolicy(allowPolicy()).build();
    RegoObject data = MAPPER.readValue("{\"key\":\"original\"}", RegoObject.class);
    store.write(ENTRYPOINT, bundle, data);

    Engine engine = new Engine.Builder().withStore(store).withEntrypoint(ENTRYPOINT).build();

    Logger logger = mock(Logger.class);
    PluginManager manager =
        new PluginManager.Builder()
            .withId("test-opa")
            .withStore(store)
            .withConfig(new Config())
            .withLogger(logger)
            .build();

    manager.registerBundleActivationListener(
        (bundleName) -> engine.refresh());

    // Verify initial behavior: allow
    JsonNode input = MAPPER.readTree("{}");
    EvaluationContext ctx =
        new EvaluationContext.Builder().withStore(store).withEntrypoint(ENTRYPOINT).build();
    List<JsonNode> results = evalJson(engine, ctx, input);
    assertTrue(results.get(0).get("result").asBoolean(), "allow policy should return true");

    RegoObject newData = MAPPER.readValue("{\"key\":\"updated\"}", RegoObject.class);
    store.write(ENTRYPOINT, bundle, newData);
    // NOT calling manager.notifyBundleActivation() -- data should still be visible

    EvaluationContext ctx2 =
        new EvaluationContext.Builder().withStore(store).withEntrypoint(ENTRYPOINT).build();
    List<JsonNode> dataResults = evalJson(engine, ctx2, input);
    assertTrue(
        dataResults.get(0).get("result").asBoolean(),
        "data changes are live -- policy still allows (same allow policy, updated data)");

    // Now update policy via notification -- deny policy takes effect
    Bundle denyBundle = new Bundle.Builder().withIrPolicy(denyPolicy()).build();
    store.write(ENTRYPOINT, denyBundle, newData);
    manager.notifyBundleActivation("test-bundle");

    EvaluationContext ctx3 =
        new EvaluationContext.Builder().withStore(store).withEntrypoint(ENTRYPOINT).build();
    List<JsonNode> policyResults = evalJson(engine, ctx3, input);
    assertFalse(
        policyResults.get(0).get("result").asBoolean(),
        "policy change requires notification/refresh to take effect");
  }
}
