package io.github.open_policy_agent.opa.rego;

import io.github.open_policy_agent.opa.ast.types.RegoObject;
import io.github.open_policy_agent.opa.bundle.Bundle;
import io.github.open_policy_agent.opa.ir.PolicyReader;
import io.github.open_policy_agent.opa.ir.policy.Policy;
import io.github.open_policy_agent.opa.storage.InMem;
import io.github.open_policy_agent.opa.storage.Store;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Objects;
import java.util.ServiceLoader;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Engine policy hot-reload behavior.
 *
 * <p>Demonstrates that after a store is updated with a new bundle (new policy), the Engine picks up
 * the new policy after calling {@link Engine#refresh()}.
 */
class EngineHotReloadTest {

  private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new io.github.open_policy_agent.opa.jackson.RegoValueModule());
  private static final PolicyReader POLICY_READER =
      ServiceLoader.load(PolicyReader.class).findFirst().orElseThrow();
  private static final String ENTRYPOINT = "authz/allow";

  private static Policy authzPolicy;

  /**
   * Minimal IR plan that returns {@code {"result": false}}. This is equivalent to
   * {@code default allow := false} with no rules that could make it true.
   */
  private static final String DENY_PLAN_TEMPLATE =
      "{\"static\":{\"strings\":[{\"value\":\"result\"}],\"files\":[]},"
          + "\"plans\":{\"plans\":[{\"name\":\"%s\",\"blocks\":["
          + "{\"stmts\":["
          + "{\"type\":\"MakeObjectStmt\",\"stmt\":{\"target\":2}},"
          + "{\"type\":\"AssignVarStmt\",\"stmt\":{\"source\":{\"type\":\"bool\",\"value\":false},\"target\":3}},"
          + "{\"type\":\"ObjectInsertStmt\",\"stmt\":{\"key\":{\"type\":\"string_index\",\"value\":0},\"value\":{\"type\":\"local\",\"value\":3},\"object\":2}},"
          + "{\"type\":\"ResultSetAddStmt\",\"stmt\":{\"value\":2}}"
          + "]}"
          + "]}]},"
          + "\"funcs\":{\"funcs\":[]}}";

  @BeforeAll
  static void loadPolicy() throws IOException {
    File policyFile =
        new File(
            Objects.requireNonNull(
                    EngineHotReloadTest.class
                        .getClassLoader()
                        .getResource("engine/testdata/authz-policy.json"))
                .getFile());
    authzPolicy = POLICY_READER.read(Files.newInputStream(policyFile.toPath()));
  }

  private static Policy denyPolicy() throws IOException {
    String json = String.format(DENY_PLAN_TEMPLATE, ENTRYPOINT);
    return POLICY_READER.read(new ByteArrayInputStream(json.getBytes()));
  }

  private static JsonNode aliceInput() throws IOException {
    return MAPPER.readTree("{\"user\":{\"id\":\"alice\",\"groups\":[]}}");
  }

  private static boolean resultBoolean(List<JsonNode> results) {
    assertNotNull(results);
    assertFalse(results.isEmpty(), "result set should not be empty");
    JsonNode first = results.get(0);
    assertTrue(first.has("result"), "result should have 'result' key");
    return first.get("result").asBoolean();
  }

  @Test
  void evaluate_doesNotPickUpNewPolicy_beforeRefresh() throws IOException {
    Store store = new InMem();
    Bundle bundle = new Bundle.Builder().withIrPolicy(authzPolicy).build();
    store.write(ENTRYPOINT, bundle, new RegoObject());

    Engine engine = new Engine.Builder().withStore(store).withEntrypoint(ENTRYPOINT).build();

    EvaluationContext ctx =
        new EvaluationContext.Builder().withStore(store).withEntrypoint(ENTRYPOINT).build();
    List<JsonNode> results = JsonNodeBridge.eval(engine, ctx, aliceInput());
    assertTrue(resultBoolean(results), "alice should be allowed by the original policy");

    // Now update the store with a deny policy (default allow := false)
    Bundle denyBundle = new Bundle.Builder().withIrPolicy(denyPolicy()).build();
    store.write(ENTRYPOINT, denyBundle, new RegoObject());

    EvaluationContext ctx2 =
        new EvaluationContext.Builder().withStore(store).withEntrypoint(ENTRYPOINT).build();
    List<JsonNode> staleResults = JsonNodeBridge.eval(engine, ctx2, aliceInput());

    assertTrue(
        resultBoolean(staleResults),
        "BUG: engine should be stale and still allow alice without refresh");
  }

  @Test
  void refresh_picksUpNewPolicy() throws IOException {
    Store store = new InMem();
    Bundle bundle = new Bundle.Builder().withIrPolicy(authzPolicy).build();
    store.write(ENTRYPOINT, bundle, new RegoObject());

    Engine engine = new Engine.Builder().withStore(store).withEntrypoint(ENTRYPOINT).build();

    EvaluationContext ctx =
        new EvaluationContext.Builder().withStore(store).withEntrypoint(ENTRYPOINT).build();
    List<JsonNode> results = JsonNodeBridge.eval(engine, ctx, aliceInput());
    assertTrue(resultBoolean(results), "alice should be allowed by the original policy");

    // Update the store with a deny policy (default allow := false)
    Bundle denyBundle = new Bundle.Builder().withIrPolicy(denyPolicy()).build();
    store.write(ENTRYPOINT, denyBundle, new RegoObject());

    engine.refresh();

    EvaluationContext ctx2 =
        new EvaluationContext.Builder().withStore(store).withEntrypoint(ENTRYPOINT).build();
    List<JsonNode> refreshedResults = JsonNodeBridge.eval(engine, ctx2, aliceInput());
    assertFalse(
        resultBoolean(refreshedResults), "after refresh, deny policy should return false");
  }

  @Test
  void dataChanges_areVisibleWithoutRefresh() throws IOException {
    Store store = new InMem();
    Bundle bundle = new Bundle.Builder().withIrPolicy(authzPolicy).build();
    store.write(ENTRYPOINT, bundle, new RegoObject());

    Engine engine = new Engine.Builder().withStore(store).withEntrypoint(ENTRYPOINT).build();

    // Bob is NOT allowed (no privileged groups in data)
    JsonNode bobInput = MAPPER.readTree("{\"user\":{\"id\":\"bob\",\"groups\":[\"super\"]}}");
    EvaluationContext ctx =
        new EvaluationContext.Builder().withStore(store).withEntrypoint(ENTRYPOINT).build();
    List<JsonNode> results = JsonNodeBridge.eval(engine, ctx, bobInput);
    assertFalse(resultBoolean(results), "bob should be denied with no privileged groups");

    // Update ONLY data (same policy) -- add privileged group
    RegoObject newData =
        MAPPER.readValue("{\"groups\":{\"super\":{\"privileged\":true}}}", RegoObject.class);
    store.write(ENTRYPOINT, bundle, newData);

    // Evaluate WITHOUT calling refresh() -- data should be visible immediately
    EvaluationContext ctx2 =
        new EvaluationContext.Builder().withStore(store).withEntrypoint(ENTRYPOINT).build();
    List<JsonNode> liveResults = JsonNodeBridge.eval(engine, ctx2, bobInput);
    assertTrue(
        resultBoolean(liveResults),
        "data changes should be visible without refresh (live from store)");
  }

  @Test
  void refresh_picksUpPolicyChange_dataAlreadyLive() throws IOException {
    Store store = new InMem();
    Bundle bundle = new Bundle.Builder().withIrPolicy(authzPolicy).build();
    RegoObject data =
        MAPPER.readValue("{\"groups\":{\"super\":{\"privileged\":true}}}", RegoObject.class);
    store.write(ENTRYPOINT, bundle, data);

    Engine engine = new Engine.Builder().withStore(store).withEntrypoint(ENTRYPOINT).build();

    // Bob in "super" group should be allowed (group is privileged in data)
    JsonNode bobInput = MAPPER.readTree("{\"user\":{\"id\":\"bob\",\"groups\":[\"super\"]}}");
    EvaluationContext ctx =
        new EvaluationContext.Builder().withStore(store).withEntrypoint(ENTRYPOINT).build();
    List<JsonNode> results = JsonNodeBridge.eval(engine, ctx, bobInput);
    assertTrue(resultBoolean(results), "bob should be allowed via privileged group");

    // Update store with deny policy (data doesn't matter -- policy always returns false)
    Bundle newBundle = new Bundle.Builder().withIrPolicy(denyPolicy()).build();
    store.write(ENTRYPOINT, newBundle, data);

    // Without refresh, old policy still allows bob
    EvaluationContext ctx2 =
        new EvaluationContext.Builder().withStore(store).withEntrypoint(ENTRYPOINT).build();
    List<JsonNode> staleResults = JsonNodeBridge.eval(engine, ctx2, bobInput);
    assertTrue(resultBoolean(staleResults), "policy should be stale without refresh");

    // After refresh, deny policy takes effect
    engine.refresh();

    EvaluationContext ctx3 =
        new EvaluationContext.Builder().withStore(store).withEntrypoint(ENTRYPOINT).build();
    List<JsonNode> refreshedResults = JsonNodeBridge.eval(engine, ctx3, bobInput);
    assertFalse(
        resultBoolean(refreshedResults),
        "after refresh, deny policy should return false regardless of data");
  }

  // ---------------------------------------------------------------------------
  // PreparedQuery hot-reload behavior
  // ---------------------------------------------------------------------------

  @Test
  void preparedQuery_dataChanges_areVisibleWithoutRefresh() throws IOException {
    Store store = new InMem();
    Bundle bundle = new Bundle.Builder().withIrPolicy(authzPolicy).build();
    store.write(ENTRYPOINT, bundle, new RegoObject());

    Engine engine = new Engine.Builder().withStore(store).withEntrypoint(ENTRYPOINT).build();
    Engine.PreparedQuery pq = engine.prepareForEvaluation().build();

    // Bob is denied (no privileged groups)
    JsonNode bobInput = MAPPER.readTree("{\"user\":{\"id\":\"bob\",\"groups\":[\"super\"]}}");
    assertFalse(resultBoolean(JsonNodeBridge.eval(pq, bobInput)), "bob should be denied with no data");

    // Update data only -- add privileged group
    RegoObject newData =
        MAPPER.readValue("{\"groups\":{\"super\":{\"privileged\":true}}}", RegoObject.class);
    store.write(ENTRYPOINT, bundle, newData);

    // PreparedQuery picks up data changes without refresh (data is live from store)
    assertTrue(
        resultBoolean(JsonNodeBridge.eval(pq, bobInput)),
        "PreparedQuery should see live data changes without refresh");
  }

  @Test
  void preparedQuery_retainsOldPolicy_afterRefresh() throws IOException {
    Store store = new InMem();
    Bundle bundle = new Bundle.Builder().withIrPolicy(authzPolicy).build();
    store.write(ENTRYPOINT, bundle, new RegoObject());

    Engine engine = new Engine.Builder().withStore(store).withEntrypoint(ENTRYPOINT).build();
    Engine.PreparedQuery pq = engine.prepareForEvaluation().build();

    assertTrue(resultBoolean(JsonNodeBridge.eval(pq, aliceInput())), "alice allowed initially");

    // Update store with deny policy + refresh engine
    Bundle denyBundle = new Bundle.Builder().withIrPolicy(denyPolicy()).build();
    store.write(ENTRYPOINT, denyBundle, new RegoObject());
    engine.refresh();

    // The OLD PreparedQuery still uses its warmed plan from the original policy
    assertTrue(
        resultBoolean(JsonNodeBridge.eval(pq, aliceInput())),
        "existing PreparedQuery retains old policy even after engine.refresh()");
  }

  @Test
  void preparedQuery_picksUpNewPolicy_afterRePreparation() throws IOException {
    Store store = new InMem();
    Bundle bundle = new Bundle.Builder().withIrPolicy(authzPolicy).build();
    store.write(ENTRYPOINT, bundle, new RegoObject());

    Engine engine = new Engine.Builder().withStore(store).withEntrypoint(ENTRYPOINT).build();
    Engine.PreparedQuery oldPq = engine.prepareForEvaluation().build();

    assertTrue(resultBoolean(JsonNodeBridge.eval(oldPq, aliceInput())), "alice allowed initially");

    // Update store with deny policy + refresh engine
    Bundle denyBundle = new Bundle.Builder().withIrPolicy(denyPolicy()).build();
    store.write(ENTRYPOINT, denyBundle, new RegoObject());
    engine.refresh();

    // Re-prepare to pick up the new policy
    Engine.PreparedQuery newPq = engine.prepareForEvaluation().build();

    assertFalse(
        resultBoolean(JsonNodeBridge.eval(newPq, aliceInput())),
        "re-prepared PreparedQuery should use the new deny policy");
  }
}
