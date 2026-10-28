package io.github.open_policy_agent.opa.storage;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import io.github.open_policy_agent.opa.ast.types.RegoObject;
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
 * Subclasses implement {@link #replaceData(List, List, RegoObject)}.
 */
public abstract class AbstractStore implements Store {

  private final Map<String, Bundle> bundles = new HashMap<>();
  private final Map<String, List<String>> bundleRoots = new HashMap<>();

  @Override
  public synchronized void write(String id, Bundle bundle, RegoObject value) {
    final List<String> roots = extractRoots(bundle);
    checkForConflictingRoots(id, roots);

    final List<String> previousRoots = bundleRoots.getOrDefault(id, List.of());
    bundles.put(id, bundle);
    bundleRoots.put(id, roots);

    replaceData(previousRoots, roots, value);
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
   * Replace the data a bundle owns.
   *
   * <p>Implementations must remove everything under {@code previousRoots} and {@code roots}, then
   * write, for each of {@code roots}, the part of {@code value} at that path. {@code value} is keyed
   * from the data root, so a root {@code "authz"} takes {@code value.authz} and writes it at {@code
   * data.authz}. An empty root owns the whole document. Readers of the current data must see
   * either the old or the new data, never a mix.
   *
   * @param previousRoots the roots the bundle id was last written with, empty for a new id
   * @param roots the roots of the bundle being written
   * @param value the bundle's data, keyed from the data root
   */
  protected abstract void replaceData(List<String> previousRoots, List<String> roots, RegoObject value);

  /**
   * The roots from the manifest, without surrounding slashes. Only a manifest without {@code roots}
   * owns the whole document; an empty list owns nothing, as in OPA.
   */
  private static List<String> extractRoots(Bundle bundle) {
    if (bundle.manifest != null && bundle.manifest.get("roots") instanceof List<?>) {
      return ((List<?>) bundle.manifest.get("roots"))
          .stream().map(root -> String.valueOf(root).replaceAll("^/+|/+$", "")).toList();
    }
    return List.of("");
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
