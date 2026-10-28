package io.github.open_policy_agent.opa.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.open_policy_agent.opa.ast.types.RegoObject;
import io.github.open_policy_agent.opa.bundle.Bundle;
import io.github.open_policy_agent.opa.ir.policy.Policy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * A store that only implements {@code writeData} keeps compiling and gets each root's slice of the
 * bundle's data. Without an override of {@code replaceData} it only merges, so a reload does not
 * remove data.
 */
class AbstractStoreSubclassTest {

  private static final ObjectMapper MAPPER =
      JsonMapper.builder()
          .addModule(new io.github.open_policy_agent.opa.jackson.RegoValueModule())
          .build();

  /** Implements nothing but the one method the previous API required. */
  private static final class MinimalStore extends AbstractStore {
    final List<String> writes = new ArrayList<>();
    final List<String> removals = new ArrayList<>();

    @Override
    protected void writeData(String bundleId, String root, RegoObject value) {
      writes.add(bundleId + ":" + root + "=" + value);
    }

    @Override
    protected void removeData(List<String> roots) {
      removals.add(String.join(",", roots));
    }

    @Override
    public RegoObject currentData() {
      return new RegoObject();
    }

    @Override
    public Policy getIrPolicyForEntrypoint(String entrypoint) {
      return null;
    }
  }

  private static RegoObject json(String text) {
    return MAPPER.readValue(text.replace('\'', '"'), RegoObject.class);
  }

  private static Bundle bundle(String... roots) {
    final Map<String, Object> manifest = new HashMap<>();
    manifest.put("roots", List.of(roots));
    return new Bundle.Builder().withManifest(manifest).build();
  }

  @Test
  void writeDataGetsTheSliceAtEachRoot() {
    final MinimalStore store = new MinimalStore();

    store.write("b", bundle("authz", "b/c"), json("{'authz':{'x':1},'b':{'c':{'y':1}},'other':1}"));

    assertEquals(List.of("b:authz={\"x\":1}", "b:b/c={\"y\":1}"), store.writes);
  }

  @Test
  void anEmptyRootGetsTheWholeValue() {
    final MinimalStore store = new MinimalStore();

    store.write("b", new Bundle.Builder().build(), json("{'x':1}"));

    assertEquals(List.of("b:={\"x\":1}"), store.writes);
  }

  @Test
  void aRootWithoutDataIsNotWritten() {
    final MinimalStore store = new MinimalStore();

    store.write("b", bundle("authz", "gone"), json("{'authz':{'x':1}}"));

    assertEquals(List.of("b:authz={\"x\":1}"), store.writes);
  }

  @Test
  void theDefaultReplaceDataAsksToRemoveThePreviousAndNewRootsBeforeWriting() {
    final MinimalStore store = new MinimalStore();

    store.write("b", bundle("old"), json("{'old':{'x':1}}"));
    store.write("b", bundle("new"), json("{'new':{'y':1}}"));

    assertEquals(List.of("old", "old,new"), store.removals);
    assertEquals(List.of("b:old={\"x\":1}", "b:new={\"y\":1}"), store.writes);
  }
}
