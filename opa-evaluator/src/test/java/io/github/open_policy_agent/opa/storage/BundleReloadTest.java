package io.github.open_policy_agent.opa.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.open_policy_agent.opa.ast.types.RegoObject;
import io.github.open_policy_agent.opa.ast.types.RegoValue;
import io.github.open_policy_agent.opa.bundle.Bundle;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Writing a bundle id again replaces what the earlier revision wrote under the bundle's roots and
 * leaves the data of other bundles alone. Bundle data is keyed from the data root; the manifest
 * roots declare what the bundle owns. JSON uses single quotes for readability.
 */
class BundleReloadTest {

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

  private void write(String id, Bundle bundle, String data) {
    store.write(id, bundle, json(data));
  }

  // ---- roots "" (or none): the bundle owns the whole document ----

  @Test
  void aKeyDroppedFromTheNewRevisionIsRemoved() {
    write("b", new Bundle.Builder().build(), "{'admins':{'alice':true,'bob':true}}");

    write("b", new Bundle.Builder().build(), "{'admins':{'alice':true}}");

    assertNotNull(at("admins", "alice"));
    assertNull(at("admins", "bob"));
  }

  @Test
  void aNestedKeyDroppedFromTheNewRevisionIsRemoved() {
    write("b", bundle(""), "{'a':{'b':{'c':1,'d':2}}}");

    write("b", bundle(""), "{'a':{'b':{'c':1}}}");

    assertNotNull(at("a", "b", "c"));
    assertNull(at("a", "b", "d"));
  }

  @Test
  void aRevisionWithNoDataRemovesTheOldData() {
    write("b", bundle(""), "{'admins':{'alice':true}}");

    write("b", bundle(""), "{}");

    assertEquals(0, store.currentData().length());
  }

  // ---- rooted bundles ----

  @Test
  void reloadingOneBundleLeavesTheDataOfAnotherIntact() {
    write("a", bundle("a"), "{'a':{'x':1,'gone':true}}");
    write("b", bundle("b"), "{'b':{'y':1}}");

    write("a", bundle("a"), "{'a':{'x':2}}");

    assertEquals("2", String.valueOf(at("a", "x")));
    assertNull(at("a", "gone"));
    assertEquals("1", String.valueOf(at("b", "y")));
  }

  @Test
  void aNewRootSetRemovesTheDataUnderTheOldRoots() {
    write("b", bundle("old"), "{'old':{'x':1}}");

    write("b", bundle("new"), "{'new':{'y':1}}");

    assertNull(at("old"));
    assertEquals("1", String.valueOf(at("new", "y")));
  }

  @Test
  void everyRootOfTheBundleIsReplaced() {
    write("b", bundle("a", "b"), "{'a':{'x':1},'b':{'y':1}}");

    write("b", bundle("a", "b"), "{'a':{'x':2}}");

    assertEquals("2", String.valueOf(at("a", "x")));
    assertNull(at("b"));
  }

  // ---- readers ----

  @Test
  void aReadersSnapshotIsNotChangedByALaterWrite() {
    write("b", bundle("a"), "{'a':{'x':1}}");
    final RegoObject snapshot = store.currentData();
    final String before = snapshot.toString();

    write("b", bundle("a"), "{'a':{'x':2}}");

    assertEquals(before, snapshot.toString());
    assertNotEquals(before, store.currentData().toString());
  }

  @Test
  void aConcurrentReaderNeverSeesASnapshotChange() throws Exception {
    write("b", bundle("a"), "{'a':{'x':0,'y':0}}");
    final CountDownLatch start = new CountDownLatch(1);
    final AtomicReference<Throwable> failure = new AtomicReference<>();
    final int revisions = 2000;

    final Thread reader =
        new Thread(
            () -> {
              try {
                start.await();
                for (int i = 0; i < revisions * 4; i++) {
                  final RegoObject snapshot = store.currentData();
                  final String first = snapshot.toString();
                  Thread.yield();
                  assertEquals(first, snapshot.toString());
                }
              } catch (Throwable t) {
                failure.compareAndSet(null, t);
              }
            });
    reader.start();
    start.countDown();
    for (int i = 1; i <= revisions; i++) {
      write("b", bundle("a"), "{'a':{'x':" + i + ",'y':" + i + "}}");
    }
    reader.join();

    assertTrue(failure.get() == null, String.valueOf(failure.get()));
  }
}
