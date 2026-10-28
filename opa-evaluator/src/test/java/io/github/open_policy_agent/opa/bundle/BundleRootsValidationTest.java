package io.github.open_policy_agent.opa.bundle;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.open_policy_agent.opa.storage.InMem;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * Data must lie within the manifest roots, or the load fails, as in OPA. An object that is only a
 * prefix of a root is allowed. JSON uses single quotes for readability.
 */
class BundleRootsValidationTest {

  private static ByteArrayInputStream stream(String json) {
    return new ByteArrayInputStream(json.replace('\'', '"').getBytes(StandardCharsets.UTF_8));
  }

  /** Loads a bundle with the given manifest (null for none) and data files (path, json pairs). */
  private static void load(String manifest, String... pathsAndData) throws IOException {
    final BundleAssembler assembler = new BundleAssembler();
    if (manifest != null) {
      assembler.loadManifest(stream(manifest));
    }
    for (int i = 0; i < pathsAndData.length; i += 2) {
      assembler.loadData(pathsAndData[i], stream(pathsAndData[i + 1]));
    }
    assembler.finish("b", new InMem());
  }

  private static void assertRejected(String expected, String manifest, String... pathsAndData) {
    final IllegalArgumentException e =
        assertThrows(IllegalArgumentException.class, () -> load(manifest, pathsAndData));
    assertEquals(
        expected + " (hint: check bundle directory structure)", e.getMessage());
  }

  @Test
  void dataInsideTheRootsLoads() {
    assertDoesNotThrow(
        () -> load("{'roots':['authz']}", "authz", "{'admins':[1]}", "authz/deeper", "{'x':1}"));
  }

  @Test
  void anObjectThatIsOnlyAPrefixOfARootIsAllowed() {
    assertDoesNotThrow(() -> load("{'roots':['a/b']}", "a/b", "{'y':1}"));
  }

  @Test
  void dataOutsideTheRootsFails() {
    assertRejected(
        "manifest roots [authz] do not permit data at path '/other'",
        "{'roots':['authz']}",
        "authz",
        "{'ok':1}",
        "other",
        "{'x':1}");
  }

  @Test
  void aLeafAtAPrefixPathFails() {
    assertRejected(
        "manifest roots [a b] do not permit data at path '/c'",
        "{'roots':['a','b']}",
        "",
        "{'c':1}");
  }

  @Test
  void emptyRootsWithDataFail() {
    assertRejected("manifest roots [] do not permit data at path '/'", "{'roots':[]}", "", "{'x':1}");
  }

  @Test
  void emptyRootsWithoutDataFailToo() {
    final IllegalArgumentException e =
        assertThrows(
            IllegalArgumentException.class,
            () -> {
              final BundleAssembler assembler = new BundleAssembler();
              assembler.loadManifest(stream("{'roots':[]}"));
              assembler.loadData("", stream("{}"));
              assembler.finish("b", new InMem());
            });
    assertEquals(
        "manifest roots [] do not permit data at path '/' (hint: check bundle directory structure)",
        e.getMessage());
  }

  @Test
  void unrootedBundlesAreUnaffected() {
    assertDoesNotThrow(() -> load(null, "", "{'x':1}", "any/where", "{'y':1}"));
    assertDoesNotThrow(() -> load("{'roots':['']}", "", "{'x':1}", "any/where", "{'y':1}"));
    assertDoesNotThrow(() -> load("{'revision':'r'}", "", "{'x':1}"));
  }
}
