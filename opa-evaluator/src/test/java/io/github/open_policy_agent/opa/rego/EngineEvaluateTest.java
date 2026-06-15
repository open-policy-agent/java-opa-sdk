package io.github.open_policy_agent.opa.rego;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Objects;
import java.util.ServiceLoader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import io.github.open_policy_agent.opa.ir.PolicyReader;
import io.github.open_policy_agent.opa.ast.types.RegoObject;
import io.github.open_policy_agent.opa.bundle.Bundle;
import io.github.open_policy_agent.opa.ir.policy.Policy;
import io.github.open_policy_agent.opa.storage.InMem;
import io.github.open_policy_agent.opa.storage.Store;

/**
 * Tests for {@link Engine#evaluate} and {@link Engine.PreparedQuery#eval} using a realistic authz
 * policy.
 *
 * <p>The test policy implements:
 *
 * <pre>{@code
 * package authz
 *
 * default allow = false
 *
 * allow if {
 *     input.user.id in {"alice", "kurt"}
 * }
 *
 * allow if {
 *     some group in input.user.groups
 *     data.groups[group].privileged
 * }
 * }</pre>
 */
class EngineEvaluateTest {

  private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new io.github.open_policy_agent.opa.jackson.RegoValueModule());
  private static final PolicyReader POLICY_READER =
      ServiceLoader.load(PolicyReader.class).findFirst().orElseThrow();
  private static final String ENTRYPOINT = "authz/allow";

  private static Policy policy;

  @BeforeAll
  static void loadPolicy() throws IOException {
    File policyFile =
        new File(
            Objects.requireNonNull(
                    EngineEvaluateTest.class
                        .getClassLoader()
                        .getResource("engine/testdata/authz-policy.json"))
                .getFile());
    policy = POLICY_READER.read(Files.newInputStream(policyFile.toPath()));
  }

  private static Engine buildEngine(String dataJson) throws IOException {
    Store store = new InMem();
    RegoObject data = MAPPER.readValue(dataJson, RegoObject.class);
    Bundle bundle = new Bundle.Builder().withIrPolicy(policy).build();
    store.write(ENTRYPOINT, bundle, data);

    return new Engine.Builder().withStore(store).withEntrypoint(ENTRYPOINT).build();
  }

  private static JsonNode input(String id, String... groups) throws IOException {
    StringBuilder sb = new StringBuilder();
    sb.append("{\"user\":{\"id\":\"").append(id).append("\",\"groups\":[");
    for (int i = 0; i < groups.length; i++) {
      if (i > 0) sb.append(",");
      sb.append("\"").append(groups[i]).append("\"");
    }
    sb.append("]}}");
    return MAPPER.readTree(sb.toString());
  }

  private static boolean resultBoolean(List<JsonNode> results) {
    assertNotNull(results);
    assertFalse(results.isEmpty(), "result set should not be empty");
    JsonNode first = results.get(0);
    assertTrue(first.has("result"), "result should have 'result' key");
    return first.get("result").asBoolean();
  }

  @Nested
  class DirectEvaluate {

    @Test
    void allowedByUserId_alice() throws IOException {
      Engine engine = buildEngine("{}");
      EvaluationContext ctx = new EvaluationContext.Builder().withEntrypoint(ENTRYPOINT).build();

      List<JsonNode> results = JsonNodeBridge.eval(engine, ctx, input("alice"));

      assertTrue(resultBoolean(results));
    }

    @Test
    void allowedByUserId_kurt() throws IOException {
      Engine engine = buildEngine("{}");
      EvaluationContext ctx = new EvaluationContext.Builder().withEntrypoint(ENTRYPOINT).build();

      List<JsonNode> results = JsonNodeBridge.eval(engine, ctx, input("kurt"));

      assertTrue(resultBoolean(results));
    }

    @Test
    void allowedByPrivilegedGroup() throws IOException {
      Engine engine = buildEngine("{\"groups\":{\"admin\":{\"privileged\":true}}}");
      EvaluationContext ctx = new EvaluationContext.Builder().withEntrypoint(ENTRYPOINT).build();

      List<JsonNode> results = JsonNodeBridge.eval(engine, ctx, input("bob", "admin"));

      assertTrue(resultBoolean(results));
    }

    @Test
    void deniedForUnknownUser() throws IOException {
      Engine engine = buildEngine("{}");
      EvaluationContext ctx = new EvaluationContext.Builder().withEntrypoint(ENTRYPOINT).build();

      List<JsonNode> results = JsonNodeBridge.eval(engine, ctx, input("bob"));

      assertFalse(resultBoolean(results));
    }

    @Test
    void deniedWhenGroupNotPrivileged() throws IOException {
      Engine engine = buildEngine("{\"groups\":{\"basic\":{}}}");
      EvaluationContext ctx = new EvaluationContext.Builder().withEntrypoint(ENTRYPOINT).build();

      List<JsonNode> results = JsonNodeBridge.eval(engine, ctx, input("bob", "basic"));

      assertFalse(resultBoolean(results));
    }

    @Test
    void allowedByBothUserIdAndGroup() throws IOException {
      Engine engine = buildEngine("{\"groups\":{\"admin\":{\"privileged\":true}}}");
      EvaluationContext ctx = new EvaluationContext.Builder().withEntrypoint(ENTRYPOINT).build();

      List<JsonNode> results = JsonNodeBridge.eval(engine, ctx, input("alice", "admin"));

      assertTrue(resultBoolean(results));
    }
  }

  @Nested
  class PreparedQueryEval {

    @Test
    void allowedByUserId() throws IOException {
      Engine engine = buildEngine("{}");
      Engine.PreparedQuery pq = engine.prepareForEvaluation().build();

      List<JsonNode> results = JsonNodeBridge.eval(pq, input("alice"));

      assertTrue(resultBoolean(results));
    }

    @Test
    void deniedForUnknownUser() throws IOException {
      Engine engine = buildEngine("{}");
      Engine.PreparedQuery pq = engine.prepareForEvaluation().build();

      List<JsonNode> results = JsonNodeBridge.eval(pq, input("bob"));

      assertFalse(resultBoolean(results));
    }

    @Test
    void allowedByPrivilegedGroup() throws IOException {
      Engine engine =
          buildEngine("{\"groups\":{\"super\":{\"privileged\":true}}}");
      Engine.PreparedQuery pq = engine.prepareForEvaluation().build();

      List<JsonNode> results = JsonNodeBridge.eval(pq, input("bob", "super"));

      assertTrue(resultBoolean(results));
    }

    @Test
    void deniedWhenGroupNotPrivileged() throws IOException {
      Engine engine = buildEngine("{\"groups\":{\"basic\":{}}}");
      Engine.PreparedQuery pq = engine.prepareForEvaluation().build();

      List<JsonNode> results = JsonNodeBridge.eval(pq, input("bob", "basic"));

      assertFalse(resultBoolean(results));
    }

    @Test
    void matchesCliTestData() throws IOException {
      // Uses the same input/data files the CLI module uses
      JsonNode inputJson =
          MAPPER.readTree(
              new File(
                  Objects.requireNonNull(
                          getClass()
                              .getClassLoader()
                              .getResource("engine/testdata/authz-input.json"))
                      .getFile()));
      JsonNode dataJson =
          MAPPER.readTree(
              new File(
                  Objects.requireNonNull(
                          getClass()
                              .getClassLoader()
                              .getResource("engine/testdata/authz-data.json"))
                      .getFile()));

      Store store = new InMem();
      RegoObject data = MAPPER.treeToValue(dataJson, RegoObject.class);
      Bundle bundle = new Bundle.Builder().withIrPolicy(policy).build();
      store.write(ENTRYPOINT, bundle, data);
      Engine engine = new Engine.Builder().withStore(store).withEntrypoint(ENTRYPOINT).build();
      Engine.PreparedQuery pq = engine.prepareForEvaluation().build();

      List<JsonNode> results = JsonNodeBridge.eval(pq, inputJson);

      // CLI input has id=alicex (not alice/kurt) but groups=["super"]
      // data has groups.super.privileged=true, so it should be allowed via group rule
      assertTrue(resultBoolean(results));
    }
  }

  @Nested
  class PojoEval {

    @Test
    void preparedQuery_pojoInputAndOutput() throws IOException {
      Engine engine = buildEngine("{}");
      Engine.PreparedQuery pq = engine.prepareForEvaluation().build();

      AuthzInput pojoInput = new AuthzInput();
      pojoInput.setUser(new AuthzInput.User("alice", List.of()));

      // After unwrapping the "result" key, the authz policy returns a boolean
      List<Boolean> results = pq.eval(pojoInput, Boolean.class);

      assertNotNull(results);
      assertFalse(results.isEmpty());
      assertTrue(results.get(0));
    }

    @Test
    void preparedQuery_pojoInputDenied() throws IOException {
      Engine engine = buildEngine("{}");
      Engine.PreparedQuery pq = engine.prepareForEvaluation().build();

      AuthzInput pojoInput = new AuthzInput();
      pojoInput.setUser(new AuthzInput.User("bob", List.of()));

      List<Boolean> results = pq.eval(pojoInput, Boolean.class);

      assertNotNull(results);
      assertFalse(results.isEmpty());
      assertFalse(results.get(0));
    }

    @Test
    void directEvaluate_pojoInputAndOutput() throws IOException {
      Engine engine = buildEngine("{}");
      EvaluationContext ctx = new EvaluationContext.Builder().withEntrypoint(ENTRYPOINT).build();

      AuthzInput pojoInput = new AuthzInput();
      pojoInput.setUser(new AuthzInput.User("alice", List.of()));

      List<Boolean> results = engine.evaluate(ctx, pojoInput, Boolean.class);

      assertNotNull(results);
      assertFalse(results.isEmpty());
      assertTrue(results.get(0));
    }

    @Test
    void preparedQuery_pojoInputWithGroups() throws IOException {
      Engine engine =
          buildEngine("{\"groups\":{\"admin\":{\"privileged\":true}}}");
      Engine.PreparedQuery pq = engine.prepareForEvaluation().build();

      AuthzInput pojoInput = new AuthzInput();
      pojoInput.setUser(new AuthzInput.User("bob", List.of("admin")));

      List<Boolean> results = pq.eval(pojoInput, Boolean.class);

      assertNotNull(results);
      assertFalse(results.isEmpty());
      assertTrue(results.get(0));
    }
  }

  public static class AuthzInput {
    private User user;

    public AuthzInput() {}

    public User getUser() {
      return user;
    }

    public void setUser(User user) {
      this.user = user;
    }

    public static class User {
      private String id;
      private List<String> groups;

      public User() {}

      public User(String id, List<String> groups) {
        this.id = id;
        this.groups = groups;
      }

      public String getId() {
        return id;
      }

      public void setId(String id) {
        this.id = id;
      }

      public List<String> getGroups() {
        return groups;
      }

      public void setGroups(List<String> groups) {
        this.groups = groups;
      }
    }
  }

  /**
   * Tests {@link Engine.PreparedQuery#eval(Object, Class)} against a policy that returns a
   * structured object so the result type isn't just a primitive.
   */
  @Nested
  class StructuredResultEval {

    private static final String DECISION_ENTRYPOINT = "authz/decision";

    private Engine buildDecisionEngine() throws IOException {
      File policyFile =
          new File(
              Objects.requireNonNull(
                      EngineEvaluateTest.class
                          .getClassLoader()
                          .getResource("engine/testdata/decision-policy.json"))
                  .getFile());
      Policy decisionPolicy = POLICY_READER.read(Files.newInputStream(policyFile.toPath()));
      Store store = new InMem();
      Bundle bundle = new Bundle.Builder().withIrPolicy(decisionPolicy).build();
      store.write(DECISION_ENTRYPOINT, bundle, new RegoObject());
      return new Engine.Builder().withStore(store).withEntrypoint(DECISION_ENTRYPOINT).build();
    }

    @Test
    void preparedQuery_pojoResultType_aliceAllowed() throws IOException {
      Engine engine = buildDecisionEngine();
      Engine.PreparedQuery pq = engine.prepareForEvaluation().build();

      AuthzInput input = new AuthzInput();
      input.setUser(new AuthzInput.User("alice", List.of()));

      List<Decision> results = pq.eval(input, Decision.class);

      assertNotNull(results);
      assertFalse(results.isEmpty());
      Decision decision = results.get(0);
      assertTrue(decision.isAllowed());
      assertTrue("alice".equals(decision.getUserId()));
      assertTrue("matched-user-id".equals(decision.getReason()));
    }

    @Test
    void preparedQuery_pojoResultType_unknownUserDenied() throws IOException {
      Engine engine = buildDecisionEngine();
      Engine.PreparedQuery pq = engine.prepareForEvaluation().build();

      AuthzInput input = new AuthzInput();
      input.setUser(new AuthzInput.User("eve", List.of()));

      List<Decision> results = pq.eval(input, Decision.class);

      assertNotNull(results);
      assertFalse(results.isEmpty());
      Decision decision = results.get(0);
      assertFalse(decision.isAllowed());
      assertTrue("eve".equals(decision.getUserId()));
      assertTrue("denied".equals(decision.getReason()));
    }

    @Test
    void directEvaluate_pojoResultType_aliceAllowed() throws IOException {
      Engine engine = buildDecisionEngine();
      EvaluationContext ctx =
          new EvaluationContext.Builder().withEntrypoint(DECISION_ENTRYPOINT).build();

      AuthzInput input = new AuthzInput();
      input.setUser(new AuthzInput.User("alice", List.of()));

      List<Decision> results = engine.evaluate(ctx, input, Decision.class);

      assertNotNull(results);
      assertFalse(results.isEmpty());
      assertTrue(results.get(0).isAllowed());
    }
  }

  public static class Decision {
    private boolean allowed;

    @com.fasterxml.jackson.annotation.JsonProperty("user_id")
    private String userId;

    private String reason;

    public Decision() {}

    public boolean isAllowed() {
      return allowed;
    }

    public void setAllowed(boolean allowed) {
      this.allowed = allowed;
    }

    public String getUserId() {
      return userId;
    }

    public void setUserId(String userId) {
      this.userId = userId;
    }

    public String getReason() {
      return reason;
    }

    public void setReason(String reason) {
      this.reason = reason;
    }
  }
}
