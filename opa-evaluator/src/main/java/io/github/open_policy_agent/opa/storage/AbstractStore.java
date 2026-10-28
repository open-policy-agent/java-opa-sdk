package io.github.open_policy_agent.opa.storage;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import io.github.open_policy_agent.opa.ast.types.RegoObject;
import io.github.open_policy_agent.opa.ast.types.RegoValue;
import io.github.open_policy_agent.opa.bundle.Bundle;

/**
 * Abstract base class for Store implementations that provides common bundle root handling.
 *
 * <p>This class handles:
 *
 * <ul>
 *   <li>Tracking all roots of each bundle from its manifest
 *   <li>Validating that the roots of different bundles don't conflict
 *   <li>Bundle storage and retrieval
 * </ul>
 *
 * <p>Bundle data is keyed from the data root. A bundle's roots declare what it owns: writing a
 * bundle id again replaces the data under its previous and new roots and leaves other data alone.
 * Subclasses implement {@link #writeData(String, String, RegoObject)}.
 */
public abstract class AbstractStore implements Store {

  private final Map<String, Bundle> bundles = new HashMap<>();
  private final Map<String, List<String>> bundleRoots = new HashMap<>();

  @Override
  public synchronized void write(String id, Bundle bundle, RegoObject value) {
    final List<String> roots = bundle.roots();
    checkForConflictingRoots(id, roots);

    final List<String> previousRoots = bundleRoots.getOrDefault(id, List.of());
    bundles.put(id, bundle);
    bundleRoots.put(id, roots);

    replaceData(id, previousRoots, roots, value);
  }

  @Override
  public synchronized Map<String, Bundle> getBundles() {
    return Collections.unmodifiableMap(bundles);
  }

  @Override
  public String getDefaultEntrypoint() {
    return bundles.values().stream()
        .filter(bundle -> bundle.manifest != null)
        .filter(bundle -> bundle.manifest.containsKey("default_decision"))
        .map(bundle -> String.valueOf(bundle.manifest.get("default_decision")))
        .findFirst()
        .orElse("");
  }

  /**
   * Replace the data a bundle owns: remove what its earlier revision wrote, then write the new
   * data. {@code value} is keyed from the data root, so a root {@code "authz"} takes {@code
   * value.authz} and writes it at {@code data.authz}; an empty root owns the whole document.
   *
   * <p>The default removes the previous and new roots with {@link #removeData(List)} and then calls
   * {@link #writeData(String, String, RegoObject)} once per root that has data. A subclass that
   * overrides neither {@code removeData} nor this method therefore only merges, and does not remove
   * data on reload. Override this method to replace the data atomically, as {@link InMem} does.
   *
   * @param bundleId the ID of the bundle being written
   * @param previousRoots the roots the bundle id was last written with, empty for a new id
   * @param roots the roots of the bundle being written
   * @param value the bundle's data, keyed from the data root
   */
  protected void replaceData(
      String bundleId, List<String> previousRoots, List<String> roots, RegoObject value) {
    final Set<String> owned = new LinkedHashSet<>(previousRoots);
    owned.addAll(roots);
    removeData(List.copyOf(owned));
    for (String root : roots) {
      final RegoValue slice = lookup(value, segments(root));
      if (slice instanceof RegoObject) {
        writeData(bundleId, root, (RegoObject) slice);
      }
    }
  }

  /** Remove the data at the given roots. Does nothing by default. */
  protected void removeData(List<String> roots) {}

  /**
   * Write bundle data to the store at the specified root path.
   *
   * @param bundleId the ID of the bundle being written
   * @param root the root path (empty string for the data root)
   * @param value the data to write at that path
   */
  protected abstract void writeData(String bundleId, String root, RegoObject value);

  /** The path segments of a root; none for the empty root. */
  protected static String[] segments(String root) {
    return root.isEmpty() ? new String[0] : root.split("/");
  }

  /** The value at {@code path} inside {@code value}, or null if there is none. */
  protected static RegoValue lookup(RegoValue value, String[] path) {
    RegoValue current = value;
    for (String segment : path) {
      if (!(current instanceof RegoObject)) {
        return null;
      }
      current = ((RegoObject) current).getProperty(segment);
      if (current == null) {
        return null;
      }
    }
    return current;
  }

  /**
   * Check that no root of the bundle conflicts with a root of another bundle.
   *
   * @throws ConflictingRootsException if a conflict is detected
   */
  private void checkForConflictingRoots(String bundleId, List<String> newRoots) {
    for (Map.Entry<String, List<String>> entry : bundleRoots.entrySet()) {
      final String existingBundleId = entry.getKey();

      // Skip checking against self when updating the same bundle
      if (existingBundleId.equals(bundleId)) {
        continue;
      }

      for (String newRoot : newRoots) {
        for (String existingRoot : entry.getValue()) {
          if (rootsConflict(newRoot, existingRoot)) {
            throw new ConflictingRootsException(
                    String.format(
                        "Bundle '%s' root '%s' conflicts with existing bundle '%s' root '%s'",
                        bundleId, newRoot, existingBundleId, existingRoot))
                .withContext("bundleId", bundleId)
                .withContext("newRoot", newRoot)
                .withContext("conflictingBundleId", existingBundleId)
                .withContext("conflictingRoot", existingRoot);
          }
        }
      }
    }
  }

  /**
   * Check if two root paths conflict.
   *
   * <p>Two roots conflict if:
   *
   * <ul>
   *   <li>They are exactly the same
   *   <li>Either root is empty (global root conflicts with all paths)
   *   <li>One is a prefix of the other with "/" separator
   * </ul>
   *
   * @param root1 first root path
   * @param root2 second root path
   * @return true if the roots conflict
   */
  private boolean rootsConflict(String root1, String root2) {
    // Exact match
    if (root1.equals(root2)) {
      return true;
    }

    // Empty root (global root) conflicts with everything
    if (root1.isEmpty() || root2.isEmpty()) {
      return true;
    }

    // Check if one is a prefix of the other with "/" separator
    // "example" and "example/foo" conflict
    // "example" and "example2" do NOT conflict
    return root1.startsWith(root2 + "/") || root2.startsWith(root1 + "/");
  }
}
