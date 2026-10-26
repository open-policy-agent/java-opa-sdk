package io.github.open_policy_agent.opa.plugins;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Merges the boot configuration over a discovered configuration the way OPA does: maps merge
 * recursively, any other boot value (arrays included) replaces the discovered one, and a few
 * sections are fixed by the boot config.
 */
final class DiscoveryMerge {

  /** The merged config tree and the dotted paths of discovered values that boot replaced. */
  static final class Result {
    private final ObjectNode tree;
    private final List<String> overriddenKeys;

    private Result(ObjectNode tree, List<String> overriddenKeys) {
      this.tree = tree;
      this.overriddenKeys = overriddenKeys;
    }

    ObjectNode tree() {
      return tree;
    }

    List<String> overriddenKeys() {
      return overriddenKeys;
    }
  }

  private DiscoveryMerge() {}

  /**
   * @param boot the boot config tree
   * @param discovered the discovered config tree
   * @param discoveryService name of the service discovery downloads from
   * @throws IllegalStateException if either tree is not an object, or discovered config redefines
   *     the discovery service
   */
  static Result merge(JsonNode boot, JsonNode discovered, String discoveryService) {
    if (!boot.isObject() || !discovered.isObject()) {
      throw new IllegalStateException("Configuration must be an object");
    }
    final ObjectNode bootTree = normalize(boot);
    final ObjectNode merged = normalize(discovered);

    final JsonNode discoveredService = merged.path("services").path(discoveryService);
    if (!discoveredService.isMissingNode()
        && !discoveredService.equals(bootTree.path("services").path(discoveryService))) {
      throw new IllegalStateException("updates to the discovery service are not allowed");
    }

    merged.remove("discovery");

    final List<String> overridden = new ArrayList<>();
    overlay(merged, bootTree, "", overridden);
    return new Result(merged, overridden);
  }

  private static void overlay(
      ObjectNode dest, ObjectNode src, String prefix, List<String> overridden) {
    for (Map.Entry<String, JsonNode> entry : src.properties()) {
      final String key = entry.getKey();
      final JsonNode value = entry.getValue();
      final JsonNode current = dest.get(key);
      final String fullKey = prefix.isEmpty() ? key : prefix + "." + key;

      if (current == null) {
        dest.set(key, value);
      } else if (current.isObject() && value.isObject()) {
        overlay((ObjectNode) current, (ObjectNode) value, fullKey, overridden);
      } else {
        if (!current.equals(value)) {
          overridden.add(fullKey);
        }
        dest.set(key, value);
      }
    }
  }

  private static ObjectNode normalize(JsonNode tree) {
    final JsonNode raw = tree.deepCopy();
    final ObjectNode copy = (ObjectNode) stripNulls(raw);
    final JsonNode services = copy.get("services");
    final ObjectNode byName = copy.objectNode();
    if (services != null && services.isArray()) {
      for (JsonNode service : services) {
        byName.set(service.path("name").asString(""), service);
      }
      copy.set("services", byName);
    }
    final JsonNode normalized = copy.get("services");
    if (normalized != null && normalized.isObject()) {
      for (JsonNode service : normalized) {
        if (service.isObject()) {
          ((ObjectNode) service).remove("name");
        }
      }
    }
    return copy;
  }

  static <T extends JsonNode> T stripNulls(T tree) {
    if (tree.isObject()) {
      final Iterator<Map.Entry<String, JsonNode>> fields = tree.properties().iterator();
      while (fields.hasNext()) {
        final JsonNode value = fields.next().getValue();
        if (value.isNull()) {
          fields.remove();
        } else {
          stripNulls(value);
        }
      }
    }
    return tree;
  }
}
