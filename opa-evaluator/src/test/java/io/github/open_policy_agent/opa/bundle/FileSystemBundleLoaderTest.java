package io.github.open_policy_agent.opa.bundle;

import io.github.open_policy_agent.opa.ast.types.RegoObject;
import io.github.open_policy_agent.opa.rego.Engine;
import io.github.open_policy_agent.opa.storage.InMem;
import io.github.open_policy_agent.opa.storage.Store;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

class FileSystemBundleLoaderTest {

  private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new io.github.open_policy_agent.opa.jackson.RegoValueModule());

  private Path testResource(String name) {
    return Paths.get(
        Objects.requireNonNull(
                getClass().getClassLoader().getResource("engine/testdata/" + name))
            .getPath());
  }

  @Test
  void load_withPlanAndData(@TempDir Path dir) throws IOException {
    Files.copy(testResource("authz-policy.json"), dir.resolve("plan.json"));
    Files.copy(testResource("authz-data.json"), dir.resolve("data.json"));

    Store store = new InMem();
    FileSystemBundleLoader loader = new FileSystemBundleLoader("test", dir);
    Bundle bundle = loader.load(store);

    assertNotNull(bundle.irPolicy);
    assertNotNull(store.getBundles().get("test"));
    assertNotNull(store.currentData());
  }

  @Test
  void load_withPlanOnly(@TempDir Path dir) throws IOException {
    Files.copy(testResource("authz-policy.json"), dir.resolve("plan.json"));

    Store store = new InMem();
    Bundle bundle = new FileSystemBundleLoader("test", dir).load(store);

    assertNotNull(bundle.irPolicy);
    assertNotNull(store.getBundles().get("test"));
  }

  @Test
  void load_withDataOnly(@TempDir Path dir) throws IOException {
    Files.copy(testResource("authz-data.json"), dir.resolve("data.json"));

    Store store = new InMem();
    Bundle bundle = new FileSystemBundleLoader("test", dir).load(store);

    assertNull(bundle.irPolicy);
    assertNotNull(store.getBundles().get("test"));
  }

  @Test
  void load_withManifest(@TempDir Path dir) throws IOException {
    Files.copy(testResource("authz-policy.json"), dir.resolve("plan.json"));
    Files.write(
        dir.resolve(".manifest"),
        "{\"revision\":\"abc123\",\"roots\":[\"authz\"]}".getBytes(StandardCharsets.UTF_8));

    Store store = new InMem();
    Bundle bundle = new FileSystemBundleLoader("test", dir).load(store);

    assertNotNull(bundle.manifest);
    assertEquals("abc123", bundle.manifest.get("revision"));
  }

  @Test
  void load_withRegoFiles(@TempDir Path dir) throws IOException {
    Files.copy(testResource("authz-policy.json"), dir.resolve("plan.json"));
    Files.copy(testResource("authz-policy.rego"), dir.resolve("policy.rego"));

    Store store = new InMem();
    Bundle bundle = new FileSystemBundleLoader("test", dir).load(store);

    assertEquals(1, bundle.rego.size());
    assertTrue(bundle.rego.containsKey("policy.rego"));
    assertTrue(bundle.rego.get("policy.rego").contains("package authz"));
  }

  @Test
  void load_withRegoInSubdirectories(@TempDir Path dir) throws IOException {
    Files.copy(testResource("authz-policy.json"), dir.resolve("plan.json"));

    // root-level rego
    Files.copy(testResource("authz-policy.rego"), dir.resolve("root.rego"));

    // one level deep
    Path policies = dir.resolve("policies");
    Files.createDirectories(policies);
    Files.copy(testResource("authz-policy.rego"), policies.resolve("allow.rego"));

    // two levels deep
    Path nested = policies.resolve("admin");
    Files.createDirectories(nested);
    Files.copy(testResource("authz-policy.rego"), nested.resolve("admin.rego"));

    // three levels deep
    Path deep = nested.resolve("super");
    Files.createDirectories(deep);
    Files.copy(testResource("authz-policy.rego"), deep.resolve("super.rego"));

    Store store = new InMem();
    Bundle bundle = new FileSystemBundleLoader("test", dir).load(store);

    assertEquals(4, bundle.rego.size());
    assertTrue(bundle.rego.containsKey("root.rego"));
    assertTrue(bundle.rego.containsKey("policies/allow.rego"));
    assertTrue(bundle.rego.containsKey("policies/admin/admin.rego"));
    assertTrue(bundle.rego.containsKey("policies/admin/super/super.rego"));
  }

  @Test
  void load_withoutPlanOrData_throws(@TempDir Path dir) {
    FileSystemBundleLoader loader = new FileSystemBundleLoader("test", dir);
    Store store = new InMem();

    IllegalArgumentException ex =
        assertThrows(IllegalArgumentException.class, () -> loader.load(store));
    assertTrue(ex.getMessage().contains("plan.json and/or data.json"));
  }

  @Test
  void load_nonExistentDirectory_throws() {
    Path noSuchDir = Paths.get("/no/such/directory");
    FileSystemBundleLoader loader = new FileSystemBundleLoader("test", noSuchDir);
    Store store = new InMem();

    IllegalArgumentException ex =
        assertThrows(IllegalArgumentException.class, () -> loader.load(store));
    assertTrue(ex.getMessage().contains("does not exist"));
  }

  @Test
  void load_withNestedDataFiles(@TempDir Path dir) throws IOException {
    Files.copy(testResource("authz-policy.json"), dir.resolve("plan.json"));

    // root data.json
    Files.write(dir.resolve("data.json"), "{\"root_key\":\"root_val\"}".getBytes(StandardCharsets.UTF_8));

    // one level deep
    Path roles = dir.resolve("roles");
    Files.createDirectories(roles);
    Files.write(roles.resolve("data.json"), "{\"admin\":true}".getBytes(StandardCharsets.UTF_8));

    // two levels deep
    Path usersPerms = dir.resolve("users").resolve("permissions");
    Files.createDirectories(usersPerms);
    Files.write(usersPerms.resolve("data.json"), "{\"read\":true}".getBytes(StandardCharsets.UTF_8));

    Store store = new InMem();
    new FileSystemBundleLoader("test", dir).load(store);

    RegoObject data = store.currentData();
    // root-level key
    assertNotNull(data.getProperty("root_key"));
    // roles/data.json -> data.roles.admin
    RegoObject rolesData = (RegoObject) data.getProperty("roles");
    assertNotNull(rolesData);
    assertNotNull(rolesData.getProperty("admin"));
    // users/permissions/data.json -> data.users.permissions.read
    RegoObject usersData = (RegoObject) data.getProperty("users");
    assertNotNull(usersData);
    RegoObject permsData = (RegoObject) usersData.getProperty("permissions");
    assertNotNull(permsData);
    assertNotNull(permsData.getProperty("read"));
  }

  @Test
  void load_withDataOnlyInSubdirectory(@TempDir Path dir) throws IOException {
    // No root data.json — only nested. Should still count as a valid bundle.
    Path sub = dir.resolve("config");
    Files.createDirectories(sub);
    Files.write(sub.resolve("data.json"), "{\"enabled\":true}".getBytes(StandardCharsets.UTF_8));

    Store store = new InMem();
    Bundle bundle = new FileSystemBundleLoader("test", dir).load(store);

    assertNull(bundle.irPolicy);
    RegoObject data = store.currentData();
    RegoObject configData = (RegoObject) data.getProperty("config");
    assertNotNull(configData);
    assertNotNull(configData.getProperty("enabled"));
  }

  @Test
  void load_integration_engineEvaluates(@TempDir Path dir) throws IOException {
    Files.copy(testResource("authz-policy.json"), dir.resolve("plan.json"));
    Files.copy(testResource("authz-data.json"), dir.resolve("data.json"));

    Engine engine =
        new Engine.Builder()
            .withBundleLoader(new FileSystemBundleLoader("authz/allow", dir))
            .withEntrypoint("authz/allow")
            .build();

    JsonNode input =
        MAPPER.readTree(
            "{\"user\":{\"id\":\"alice\",\"groups\":[]}}");
    List<JsonNode> results =
        io.github.open_policy_agent.opa.rego.JsonNodeBridge.eval(
            engine.prepareForEvaluation().build(), input);

    assertEquals(1, results.size());
    assertTrue(results.get(0).has("result"));
    assertTrue(results.get(0).get("result").asBoolean());
  }

  @Test
  void load_integration_nestedDataEvaluates(@TempDir Path dir) throws IOException {
    // The authz policy reads data.groups[group].privileged.
    // Put groups data in groups/data.json instead of root data.json to verify
    // nested data files are merged into the correct path in the data tree.
    Files.copy(testResource("authz-policy.json"), dir.resolve("plan.json"));

    Path groupsDir = dir.resolve("groups");
    Files.createDirectories(groupsDir);
    Files.write(
        groupsDir.resolve("data.json"),
        "{\"super\":{\"privileged\":true}}".getBytes(StandardCharsets.UTF_8));

    Engine engine =
        new Engine.Builder()
            .withBundleLoader(new FileSystemBundleLoader("authz/allow", dir))
            .withEntrypoint("authz/allow")
            .build();

    // User "bob" is not in the allow-list, but is in the "super" group
    JsonNode input =
        MAPPER.readTree(
            "{\"user\":{\"id\":\"bob\",\"groups\":[\"super\"]}}");
    List<JsonNode> results =
        io.github.open_policy_agent.opa.rego.JsonNodeBridge.eval(
            engine.prepareForEvaluation().build(), input);

    assertEquals(1, results.size());
    assertTrue(results.get(0).get("result").asBoolean(),
        "Policy should allow via data.groups.super.privileged from nested data.json");
  }
}
