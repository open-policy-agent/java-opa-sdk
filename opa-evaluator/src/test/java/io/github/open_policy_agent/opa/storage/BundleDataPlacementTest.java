package io.github.open_policy_agent.opa.storage;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.open_policy_agent.opa.ast.types.RegoObject;
import io.github.open_policy_agent.opa.ast.types.RegoValue;
import io.github.open_policy_agent.opa.bundle.Bundle;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Bundle data is already keyed from the data root, so it is written there; the manifest roots only
 * declare what the bundle owns. JSON uses single quotes for readability.
 */
class BundleDataPlacementTest {

  private static final ObjectMapper MAPPER =
      JsonMapper.builder()
          .addModule(new io.github.open_policy_agent.opa.jackson.RegoValueModule())
          .build();

  private final Store store = new InMem();

  private static RegoObject json(String text) {
    return MAPPER.readValue(text.replace('\'', '"'), RegoObject.class);
  }

  private static Bundle bundle(String... roots) {
    final Map<String, Object> manifest = new HashMap<>();
    manifest.put("roots", List.of(roots));
    return new Bundle.Builder().withManifest(manifest).build();
  }

  private RegoValue at(String... path) {
    RegoValue current = store.currentData();
    for (String key : path) {
      if (!(current instanceof RegoObject)) {
        return null;
      }
      current = ((RegoObject) current).getProperty(key);
    }
    return current;
  }

  @Test
  void rootedDataLandsAtTheDataRootNotUnderTheRoot() {
    store.write("b", bundle("authz"), json("{'authz':{'admins':['alice']}}"));

    assertNotNull(at("authz", "admins"));
    assertNull(at("authz", "authz"));
  }

  @Test
  void unrootedDataLandsAtTheDataRoot() {
    store.write("b", new Bundle.Builder().build(), json("{'admins':['alice']}"));

    assertNotNull(at("admins"));
  }

  @Test
  void aBundleWithTwoRootsHasDataUnderBoth() {
    store.write("b", bundle("a", "b/c"), json("{'a':{'x':1},'b':{'c':{'y':1}}}"));

    assertNotNull(at("a", "x"));
    assertNotNull(at("b", "c", "y"));
  }

  @Test
  void dataOutsideTheRootsIsNotWritten() {
    store.write("b", bundle("authz"), json("{'authz':{'x':1},'other':{'y':1}}"));

    assertNotNull(at("authz", "x"));
    assertNull(at("other"));
  }

  @Test
  void conflictDetectionUsesEveryRootOfBothBundles() {
    store.write("one", bundle("a", "b"), new RegoObject());

    assertThrows(
        ConflictingRootsException.class, () -> store.write("two", bundle("c", "b/x"), new RegoObject()));
    assertDoesNotThrow(() -> store.write("three", bundle("c", "d"), new RegoObject()));
  }

  @Test
  void anEmptyRootsListOwnsNothing() {
    store.write("one", bundle(), json("{'x':1}"));

    assertDoesNotThrow(() -> store.write("two", new Bundle.Builder().build(), json("{'y':1}")));
    assertNull(at("x"));
    assertNotNull(at("y"));
  }
}
