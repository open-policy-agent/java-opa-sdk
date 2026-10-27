package io.github.open_policy_agent.opa.plugins;

import io.github.open_policy_agent.opa.config.Config;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
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
    private final Set<String> unchangedServices;

    private Result(ObjectNode tree, List<String> overriddenKeys, Set<String> unchangedServices) {
      this.tree = tree;
      this.overriddenKeys = overriddenKeys;
      this.unchangedServices = unchangedServices;
    }

    ObjectNode tree() {
      return tree;
    }

    List<String> overriddenKeys() {
      return overriddenKeys;
    }

    Set<String> unchangedServices() {
      return unchangedServices;
    }
  }

  private static final Comparator<JsonNode> BY_VALUE =
      (a, b) ->
          a.isNumber() && b.isNumber()
              ? a.decimalValue().compareTo(b.decimalValue())
              : (a.equals(b) ? 0 : 1);

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
        && !sameService(discoveredService, bootTree.path("services").path(discoveryService))) {
      throw new IllegalStateException("updates to the discovery service are not allowed");
    }

    merged.remove("discovery");

    final List<String> overridden = new ArrayList<>();
    overlay(merged, bootTree, "", overridden);
    final Set<String> unchanged = new HashSet<>();
    for (Map.Entry<String, JsonNode> service : merged.path("services").properties()) {
      if (sameJson(service.getValue(), bootTree.path("services").path(service.getKey()))) {
        unchanged.add(service.getKey());
      }
    }
    return new Result(merged, overridden, unchanged);
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
        if (!sameJson(current, value)) {
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
    if (services != null && services.isArray()) {
      final ObjectNode byName = copy.objectNode();
      for (JsonNode service : services) {
        final String name = service.path("name").asString("");
        if (name.isEmpty()) {
          throw new IllegalStateException(
              "Service name is required when using array format for services configuration");
        }
        byName.set(name, service);
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

  /** Compares two service definitions once both have the defaults filled in. */
  private static boolean sameService(JsonNode discovered, JsonNode boot) {
    return !boot.isMissingNode() && sameJson(withServiceDefaults(discovered), withServiceDefaults(boot));
  }

  private static JsonNode withServiceDefaults(JsonNode service) {
    final JsonMapper mapper = JsonMapper.shared();
    final Config.ServiceConfig typed = mapper.treeToValue(service, Config.ServiceConfig.class);
    return stripNulls(mapper.<JsonNode>valueToTree(typed));
  }

  /** JSON equality that compares numbers by value, so an int and a long (or 2 and 2.0) match. */
  static boolean sameJson(JsonNode a, JsonNode b) {
    return a.equals(BY_VALUE, b);
  }

  /** Removes object fields whose value is null, recursively, including inside arrays. */
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
    } else if (tree.isArray()) {
      for (JsonNode element : tree) {
        stripNulls(element);
      }
    }
    return tree;
  }

  /**
   * Removes from {@code tree} the fields whose value equals the one in {@code defaults}, and objects
   * left empty by that, so that only values somebody set remain.
   */
  static ObjectNode pruneDefaults(ObjectNode tree, JsonNode defaults) {
    final Iterator<Map.Entry<String, JsonNode>> fields = tree.properties().iterator();
    while (fields.hasNext()) {
      final Map.Entry<String, JsonNode> field = fields.next();
      final JsonNode value = field.getValue();
      final JsonNode defaultValue = defaults.path(field.getKey());
      if (value.isObject() && defaultValue.isObject()) {
        pruneDefaults((ObjectNode) value, defaultValue);
        if (value.isEmpty()) {
          fields.remove();
        }
      } else if (sameJson(value, defaultValue)) {
        fields.remove();
      }
    }
    return tree;
  }
}
