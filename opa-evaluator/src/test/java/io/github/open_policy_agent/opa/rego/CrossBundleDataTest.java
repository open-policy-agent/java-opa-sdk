package io.github.open_policy_agent.opa.rego;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceLoader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import io.github.open_policy_agent.opa.ast.types.RegoObject;
import io.github.open_policy_agent.opa.bundle.Bundle;
import io.github.open_policy_agent.opa.ir.PolicyReader;
import io.github.open_policy_agent.opa.ir.policy.Policy;
import io.github.open_policy_agent.opa.storage.InMem;
import io.github.open_policy_agent.opa.storage.Store;

/**
 * E2E tests verifying that a policy from one bundle can reference data provided by a different
 * bundle via the shared store.
 *
 * <p>Uses the authz policy ({@code data.groups[group].privileged}) with two bundles:
 *
 * <ul>
 *   <li>Data bundle (root "groups"): supplies group privilege data
 *   <li>Policy bundle (root "authz"): carries the authz/allow IR plan
 * </ul>
 */
class CrossBundleDataTest {

  private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new io.github.open_policy_agent.opa.jackson.RegoValueModule());
  private static final PolicyReader POLICY_READER =
      ServiceLoader.load(PolicyReader.class).findFirst().orElseThrow();
  private static final String ENTRYPOINT = "authz/allow";

  private static Policy authzPolicy;

  @BeforeAll
  static void loadPolicy() throws IOException {
    File policyFile =
        new File(
            Objects.requireNonNull(
                    CrossBundleDataTest.class
                        .getClassLoader()
                        .getResource("engine/testdata/authz-policy.json"))
                .getFile());
    authzPolicy = POLICY_READER.read(Files.newInputStream(policyFile.toPath()));
  }

  private static Bundle createBundleWithRoot(String root) {
    Map<String, Object> manifest = new HashMap<>();
    manifest.put("roots", List.of(root));
    return new Bundle.Builder().withManifest(manifest).build();
  }

  private static Bundle createPolicyBundleWithRoot(String root) {
    Map<String, Object> manifest = new HashMap<>();
    manifest.put("roots", List.of(root));
    return new Bundle.Builder().withIrPolicy(authzPolicy).withManifest(manifest).build();
  }

  private static JsonNode bobInGroup(String group) throws IOException {
    return MAPPER.readTree(
        String.format("{\"user\":{\"id\":\"bob\",\"groups\":[\"%s\"]}}", group));
  }

  private static boolean resultBoolean(List<JsonNode> results) {
    assertNotNull(results);
    assertFalse(results.isEmpty(), "result set should not be empty");
    JsonNode first = results.get(0);
    assertTrue(first.has("result"), "result should have 'result' key");
    return first.get("result").asBoolean();
  }

  @Test
  void policyFromOneBundleCanReferenceDataFromAnother() throws IOException {
    Store store = new InMem();

    // Data bundle: root "groups", provides {"super": {"privileged": true}}
    Bundle dataBundle = createBundleWithRoot("groups");
    RegoObject groupsData =
        MAPPER.readValue("{\"super\":{\"privileged\":true}}", RegoObject.class);
    store.write("data-bundle", dataBundle, groupsData);

    // Policy bundle: root "authz", carries the authz/allow plan, no data
    Bundle policyBundle = createPolicyBundleWithRoot("authz");
    store.write("policy-bundle", policyBundle, new RegoObject());

    Engine engine = new Engine.Builder().withStore(store).withEntrypoint(ENTRYPOINT).build();
    Engine.PreparedQuery pq = engine.prepareForEvaluation().build();

    List<JsonNode> results = JsonNodeBridge.eval(pq, bobInGroup("super"));

    assertTrue(
        resultBoolean(results),
        "bob should be allowed — policy from policy-bundle references data.groups from data-bundle");
  }

  @Test
  void policyDeniedWhenCrossBundleDataMissing() throws IOException {
    Store store = new InMem();

    // Data bundle: root "groups", provides empty data (no privileged groups)
    Bundle dataBundle = createBundleWithRoot("groups");
    store.write("data-bundle", dataBundle, new RegoObject());

    // Policy bundle: root "authz", carries the authz/allow plan
    Bundle policyBundle = createPolicyBundleWithRoot("authz");
    store.write("policy-bundle", policyBundle, new RegoObject());

    Engine engine = new Engine.Builder().withStore(store).withEntrypoint(ENTRYPOINT).build();
    Engine.PreparedQuery pq = engine.prepareForEvaluation().build();

    List<JsonNode> results = JsonNodeBridge.eval(pq, bobInGroup("super"));

    assertFalse(
        resultBoolean(results),
        "bob should be denied — data-bundle has no privileged group entries");
  }

  @Test
  void crossBundleDataUpdatesAreVisibleLive() throws IOException {
    Store store = new InMem();

    // Data bundle: initially no privileged groups
    Bundle dataBundle = createBundleWithRoot("groups");
    store.write("data-bundle", dataBundle, new RegoObject());

    // Policy bundle
    Bundle policyBundle = createPolicyBundleWithRoot("authz");
    store.write("policy-bundle", policyBundle, new RegoObject());

    Engine engine = new Engine.Builder().withStore(store).withEntrypoint(ENTRYPOINT).build();
    Engine.PreparedQuery pq = engine.prepareForEvaluation().build();

    // Initially denied
    assertFalse(resultBoolean(JsonNodeBridge.eval(pq, bobInGroup("super"))), "bob should be denied initially");

    // Update data bundle with privileged group — no engine refresh needed
    RegoObject updatedData =
        MAPPER.readValue("{\"super\":{\"privileged\":true}}", RegoObject.class);
    store.write("data-bundle", dataBundle, updatedData);

    // Data changes are live
    assertTrue(
        resultBoolean(JsonNodeBridge.eval(pq, bobInGroup("super"))),
        "bob should be allowed after data-bundle update — cross-bundle data is live");
  }
}
