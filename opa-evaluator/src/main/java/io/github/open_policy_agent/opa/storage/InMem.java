package io.github.open_policy_agent.opa.storage;

import io.github.open_policy_agent.opa.ast.types.RegoObject;
import io.github.open_policy_agent.opa.ast.types.RegoString;
import io.github.open_policy_agent.opa.ast.types.RegoValue;
import io.github.open_policy_agent.opa.ir.policy.Policy;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * In-memory implementation of the Store interface. The data document is never modified in place: a
 * write builds a new tree that shares the untouched parts and swaps it in, so a reader holding
 * {@link #currentData()} keeps seeing the data it got.
 */
public class InMem extends AbstractStore {

  private volatile RegoObject data = new RegoObject();

  @Override
  protected void replaceData(
      String bundleId, List<String> previousRoots, List<String> roots, RegoObject value) {
    final Set<String> owned = new LinkedHashSet<>(previousRoots);
    owned.addAll(roots);

    RegoObject next = data;
    for (String root : owned) {
      next = without(next, segments(root), 0);
    }
    for (String root : roots) {
      final RegoValue node = lookup(value, segments(root));
      if (node != null) {
        next = (RegoObject) with(next, segments(root), 0, node);
      }
    }
    data = next;
  }

  @Override
  protected void writeData(String bundleId, String root, RegoObject value) {
    data = (RegoObject) with(data, segments(root), 0, value);
  }

  private static RegoObject without(RegoObject object, String[] path, int depth) {
    if (path.length == 0) {
      return new RegoObject();
    }
    final RegoString key = new RegoString(path[depth]);
    final RegoValue child = object.getProperty(key);
    if (child == null) {
      return object;
    }
    if (depth == path.length - 1) {
      final RegoObject copy = new RegoObject();
      object.getProperties().forEach((k, v) -> {
        if (!k.equals(key)) {
          copy.setProp(k, v);
        }
      });
      return copy;
    }
    if (!(child instanceof RegoObject)) {
      return object;
    }
    return copyWith(object, key, without((RegoObject) child, path, depth + 1));
  }

  private static RegoValue with(RegoValue current, String[] path, int depth, RegoValue node) {
    if (depth == path.length) {
      return node;
    }
    final RegoObject object = current instanceof RegoObject ? (RegoObject) current : new RegoObject();
    final RegoString key = new RegoString(path[depth]);
    return copyWith(object, key, with(object.getProperty(key), path, depth + 1, node));
  }

  private static RegoObject copyWith(RegoObject object, RegoString key, RegoValue value) {
    final RegoObject copy = new RegoObject();
    object.getProperties().forEach(copy::setProp);
    copy.setProp(key, value);
    return copy;
  }

  @Override
  public Policy getIrPolicyForEntrypoint(String entrypoint) {
    // Spec says that for a blank entrypoint you should return the first plan, but with multiple
    // bundles "first" is a little random, so there might need to be some clarification here
    return getBundles().values().stream()
        .filter(b -> b.irPolicy != null)
        .filter(
            b ->
                b.irPolicy.getPlans().getPlans().stream()
                    .anyMatch(p -> p.getName().equals(entrypoint)))
        .findFirst()
        .map(b -> b.irPolicy)
        .orElseGet(
            () ->
                getBundles().values().stream()
                    .filter(b -> b.irPolicy != null)
                    .map(b -> b.irPolicy)
                    .findFirst()
                    .orElse(null));
  }

  @Override
  public RegoObject currentData() {
    return data;
  }
}
