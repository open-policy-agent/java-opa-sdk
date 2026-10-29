package io.github.open_policy_agent.opa.bundle;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.open_policy_agent.opa.storage.InMem;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Data must lie within the manifest roots, or the load fails, as in OPA. An object that is only a
 * prefix of a root is allowed. JSON uses single quotes for readability.
 */
class BundleRootsValidationTest {

  private static ByteArrayInputStream stream(String json) {
    return new ByteArrayInputStream(json.replace('\'', '"').getBytes(StandardCharsets.UTF_8));
  }

  /** Loads a bundle with the given manifest (null for none) and data files by path. */
  private static void load(String manifest, Map<String, String> dataFiles) throws IOException {
    final BundleAssembler assembler = new BundleAssembler();
    if (manifest != null) {
      assembler.loadManifest(stream(manifest));
    }
    for (Map.Entry<String, String> file : dataFiles.entrySet()) {
      assembler.loadData(file.getKey(), stream(file.getValue()));
    }
    assembler.finish("b", new InMem());
  }

  private static void assertRejected(String expected, String manifest, Map<String, String> dataFiles) {
    final IllegalArgumentException e =
        assertThrows(IllegalArgumentException.class, () -> load(manifest, dataFiles));
    assertEquals(expected + " (hint: check bundle directory structure)", e.getMessage());
  }

  @Test
  void dataInsideTheRootsLoads() {
    assertDoesNotThrow(
        () ->
            load(
                "{'roots':['authz']}",
                Map.of("authz", "{'admins':[1]}", "authz/deeper", "{'x':1}")));
  }

  @Test
  void anObjectThatIsOnlyAPrefixOfARootIsAllowed() {
    assertDoesNotThrow(() -> load("{'roots':['a/b']}", Map.of("a/b", "{'y':1}")));
  }

  @Test
  void dataOutsideTheRootsFails() {
    assertRejected(
        "manifest roots [authz] do not permit data at path '/other'",
        "{'roots':['authz']}",
        Map.of("authz", "{'ok':1}", "other", "{'x':1}"));
  }

  @Test
  void aLeafAtAPrefixPathFails() {
    assertRejected(
        "manifest roots [a b] do not permit data at path '/c'",
        "{'roots':['a','b']}",
        Map.of("", "{'c':1}"));
  }

  @Test
  void emptyRootsWithDataFail() {
    assertRejected(
        "manifest roots [] do not permit data at path '/'", "{'roots':[]}", Map.of("", "{'x':1}"));
  }

  @Test
  void emptyRootsWithoutDataFailToo() {
    assertRejected("manifest roots [] do not permit data at path '/'", "{'roots':[]}", Map.of("", "{}"));
  }

  @Test
  void unrootedBundlesAreUnaffected() {
    final Map<String, String> files = Map.of("", "{'x':1}", "any/where", "{'y':1}");

    assertDoesNotThrow(() -> load(null, files));
    assertDoesNotThrow(() -> load("{'roots':['']}", files));
    assertDoesNotThrow(() -> load("{'revision':'r'}", Map.of("", "{'x':1}")));
  }
}
