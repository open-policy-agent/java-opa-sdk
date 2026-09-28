package io.github.open_policy_agent.opa.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import io.github.open_policy_agent.opa.ast.types.RegoObject;
import io.github.open_policy_agent.opa.bundle.Bundle;
import io.github.open_policy_agent.opa.bundle.Manifest;

/** Unit tests for the manifest fields a Store reads: {@code default_decision} and {@code roots}. */
class ManifestStoreTest {

  private Store store;

  @BeforeEach
  void setUp() {
    store = new InMem();
  }

  private static Bundle bundle(Map<String, Object> manifest) {
    Bundle.Builder builder = new Bundle.Builder();
    if (manifest != null) {
      builder.withManifest(Manifest.fromMap(manifest));
    }
    return builder.build();
  }

  @Test
  void defaultEntrypointIsEmptyWithoutAManifest() {
    // A bundle without a manifest owns the global root, so it has to be alone in the store.
    store.write("no-manifest", bundle(null), new RegoObject());
    assertEquals("", store.getDefaultEntrypoint());
  }

  @Test
  void defaultEntrypointComesFromTheFirstManifestThatDeclaresOne() {
    store.write("no-decision", bundle(Map.of("roots", List.of("a"))), new RegoObject());
    assertEquals("", store.getDefaultEntrypoint());

    store.write(
        "decision",
        bundle(Map.of("roots", List.of("b"), "default_decision", "authz/allow")),
        new RegoObject());
    assertEquals("authz/allow", store.getDefaultEntrypoint());
  }

  @Test
  void defaultEntrypointSkipsNullDecision() {
    Map<String, Object> manifest = new HashMap<>();
    manifest.put("roots", List.of("a"));
    manifest.put("default_decision", null);
    store.write("null-decision", bundle(manifest), new RegoObject());
    assertEquals("", store.getDefaultEntrypoint());

    store.write(
        "decision",
        bundle(Map.of("roots", List.of("b"), "default_decision", "authz/allow")),
        new RegoObject());
    assertEquals("authz/allow", store.getDefaultEntrypoint());
  }

  @Test
  void explicitlyEmptyRootsCurrentlyClaimTheGlobalRoot() {
    store.write("scoped", bundle(Map.of("roots", List.of("example"))), new RegoObject());

    // The store currently treats an empty roots list as the global root; OPA treats [] as owning no roots.
    assertThrows(
        ConflictingRootsException.class,
        () -> store.write("global", bundle(Map.of("roots", List.of())), new RegoObject()));
  }
}
