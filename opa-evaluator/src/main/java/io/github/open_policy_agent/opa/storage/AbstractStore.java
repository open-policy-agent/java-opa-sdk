package io.github.open_policy_agent.opa.storage;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import io.github.open_policy_agent.opa.ast.types.RegoObject;
import io.github.open_policy_agent.opa.bundle.Bundle;

/**
 * Abstract base class for Store implementations that provides common bundle root validation.
 *
 * <p>This class handles:
 *
 * <ul>
 *   <li>Tracking bundle roots from manifests
 *   <li>Validating that bundle roots don't conflict
 *   <li>Bundle storage and retrieval
 * </ul>
 *
 * <p>Subclasses must implement {@link #writeData(String, String, RegoObject)} to handle the actual
 * data storage logic.
 */
public abstract class AbstractStore implements Store {

  private final Map<String, Bundle> bundles = new HashMap<>();
  private final Map<String, String> bundleRoots = new HashMap<>();

  @Override
  public synchronized void write(String id, Bundle bundle, RegoObject value) {
    // Extract and validate roots before writing
    String root = extractRoot(bundle);
    checkForConflictingRoots(id, root);

    bundles.put(id, bundle);
    bundleRoots.put(id, root);

    // Delegate actual data writing to subclass
    writeData(id, root, value);
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
   * Write bundle data to the store at the specified root path.
   *
   * <p>Implementations should:
   *
   * <ul>
   *   <li>If root is empty, merge value into the global data root
   *   <li>If root is non-empty, write value under that path (e.g., "example" → data.example)
   *   <li>Handle merging with existing data at the target location
   * </ul>
   *
   * @param bundleId the ID of the bundle being written
   * @param root the root path (empty string for global root)
   * @param value the data to write
   */
  protected abstract void writeData(String bundleId, String root, RegoObject value);

  /**
   * Extract the root path from a bundle's manifest.
   *
   * @param bundle the bundle to extract the root from
   * @return the root path (empty string for global root)
   */
  private String extractRoot(Bundle bundle) {
    if (bundle.manifest != null && bundle.manifest.get("roots") instanceof java.util.List) {
      java.util.List<?> roots = (java.util.List<?>) bundle.manifest.get("roots");
      if (!roots.isEmpty()) {
        return String.valueOf(roots.get(0));
      }
    }
    // No manifest or no roots field, default to empty root (global data root)
    return "";
  }

  /**
   * Check if the given root conflicts with any existing bundle roots.
   *
   * <p>Two roots conflict if:
   *
   * <ul>
   *   <li>They are exactly the same
   *   <li>Either root is empty (global root conflicts with all paths)
   *   <li>One is a prefix of the other (e.g., "example" and "example/foo")
   * </ul>
   *
   * @param bundleId the ID of the bundle being written
   * @param newRoot the root path of the new bundle
   * @throws ConflictingRootsException if a conflict is detected
   */
  private void checkForConflictingRoots(String bundleId, String newRoot) {
    for (Map.Entry<String, String> entry : bundleRoots.entrySet()) {
      String existingBundleId = entry.getKey();
      String existingRoot = entry.getValue();

      // Skip checking against self when updating the same bundle
      if (existingBundleId.equals(bundleId)) {
        continue;
      }

      // Check if roots conflict
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
