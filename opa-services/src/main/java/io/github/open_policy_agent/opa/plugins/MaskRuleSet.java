package io.github.open_policy_agent.opa.plugins;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Set of mask rules produced by the configured {@code mask_decision} policy, applied to a decision
 * log event before it is buffered or written to the console. A rule is either the shorthand string
 * form ({@code "/input/password"}, which removes the field) or the structured form
 * ({@code {"op": "upsert", "path": "/input/password", "value": x}}).
 *
 * <p>Mirrors {@code plugins/logs/mask.go} in OPA's Go implementation, including its silent skipping
 * of undefined paths. The one deliberate divergence is noted in {@link MaskRule#remove}.
 */
final class MaskRuleSet {

  private static final String OP_REMOVE = "remove";
  private static final String OP_UPSERT = "upsert";

  /** Event fields a mask rule is allowed to target. */
  private static final Set<String> TARGETS = Set.of("input", "result", "nd_builtin_cache");

  private final List<MaskRule> rules;

  private MaskRuleSet(List<MaskRule> rules) {
    this.rules = rules;
  }

  /**
   * Parse the value returned by the mask policy.
   *
   * @param value the mask decision, expected to be an array of rules
   * @return the parsed rule set
   * @throws IllegalArgumentException if the value is not an array of well-formed rules, which OPA
   *     Go treats as a masking failure that drops the event
   */
  static MaskRuleSet parse(JsonNode value) {
    if (value == null || !value.isArray()) {
      throw new IllegalArgumentException("unexpected rule format " + describe(value));
    }

    List<MaskRule> parsed = new ArrayList<>();
    for (JsonNode raw : value) {
      if (raw.isTextual()) {
        parsed.add(new MaskRule(OP_REMOVE, raw.textValue(), null));
      } else if (raw.isObject()) {
        parsed.add(new MaskRule(stringField(raw, "op"), stringField(raw, "path"), raw.get("value")));
      } else {
        throw new IllegalArgumentException("invalid mask rule format encountered: " + describe(raw));
      }
    }
    return new MaskRuleSet(parsed);
  }

  /**
   * Apply every rule to the event in order, skipping rules whose path is undefined. Modified event
   * fields are copied first, so the input and result nodes owned by the caller stay untouched.
   *
   * @param event the decision event to redact in place
   */
  void apply(ObjectNode event) {
    Set<String> copied = new HashSet<>();
    for (MaskRule rule : rules) {
      rule.apply(event, copied);
    }
  }

  // Like Go's getString: absent is "" (rejected downstream), present but unusable is an error.
  private static String stringField(JsonNode rule, String field) {
    JsonNode value = rule.get(field);
    if (value == null) {
      return "";
    }
    if (!value.isTextual() || value.textValue().isEmpty()) {
      throw new IllegalArgumentException("invalid \"" + field + "\" value: " + value);
    }
    return value.textValue();
  }

  private static String describe(JsonNode value) {
    return value == null ? "null" : value + " (" + value.getNodeType() + ")";
  }

  /** A single mask rule. */
  private static final class MaskRule {

    private final String op;
    private final String[] parts;
    private final JsonNode value;
    /** True when the rule targets a whole event field (e.g. {@code /input}). */
    private final boolean modifyFullObj;

    MaskRule(String op, String path, JsonNode value) {
      if (path.isEmpty()) {
        throw new IllegalArgumentException("mask must be non-empty");
      }
      if (!path.startsWith("/")) {
        throw new IllegalArgumentException("mask must be slash-prefixed");
      }

      String[] rawParts = path.substring(1).split("/", -1);
      if (!TARGETS.contains(rawParts[0])) {
        throw new IllegalArgumentException("mask prefix not allowed: " + rawParts[0]);
      }
      if (!OP_REMOVE.equals(op) && !OP_UPSERT.equals(op)) {
        throw new IllegalArgumentException("mask op is not supported: " + op);
      }

      this.op = op;
      this.value = value;
      this.parts = new String[rawParts.length];
      for (int i = 0; i < rawParts.length; i++) {
        // As in Go: an invalid escape rejects the rule, and the escaped form is what is used to
        // look segments up and to report the rule in erased/masked.
        checkEscaping(rawParts[i]);
        this.parts[i] = pathEscape(rawParts[i]);
      }
      this.modifyFullObj = rawParts.length == 1;
    }

    void apply(ObjectNode event, Set<String> copied) {
      String field = parts[0];
      JsonNode target = event.get(field);
      // An absent field is Go's nil Input/Result/NDBuiltinCache pointer; a present null one is
      // still a target for a whole-field rule.
      if (target == null) {
        return;
      }

      if (OP_REMOVE.equals(op)) {
        if (modifyFullObj) {
          event.remove(field);
        } else if (!remove(mutable(event, field, copied))) {
          return;
        }
        record(event, "erased");
        return;
      }

      if (modifyFullObj) {
        event.set(field, value == null ? NullNode.getInstance() : value);
      } else {
        JsonNode node = mutable(event, field, copied);
        // Go only upserts into an object; an array or scalar target is left alone.
        if (!node.isObject() || !upsert((ObjectNode) node)) {
          return;
        }
      }
      record(event, "masked");
    }

    // Returns false when the path is undefined, leaving the event untouched.
    private boolean remove(JsonNode root) {
      JsonNode node = root;
      for (int i = 1; i < parts.length - 1; i++) {
        node = child(node, parts[i]);
        if (node == null) {
          return false;
        }
      }

      String target = parts[parts.length - 1];
      if (node.isObject()) {
        return ((ObjectNode) node).remove(target) != null;
      }
      if (node.isArray()) {
        // Deliberate divergence: for an array-valued field ("/input/1" with an array input), Go
        // cannot write the shortened slice back through a nil parent and skips the rule.
        ArrayNode array = (ArrayNode) node;
        int index = index(target, array.size());
        if (index < 0) {
          return false;
        }
        array.remove(index);
        return true;
      }
      return false;
    }

    // Creates missing intermediate objects; returns false when the path cannot be created.
    private boolean upsert(ObjectNode root) {
      JsonNode node = root;
      for (int i = 1; i < parts.length - 1; i++) {
        if (node.isObject()) {
          ObjectNode object = (ObjectNode) node;
          JsonNode next = object.get(parts[i]);
          node = next != null ? next : object.putObject(parts[i]);
        } else if (node.isArray()) {
          // Go grows an undersized slice into a local copy that is never written back, so an
          // out-of-range index can never resolve to a usable node.
          int index = index(parts[i], ((ArrayNode) node).size());
          if (index < 0) {
            return false;
          }
          node = node.get(index);
        } else {
          return false;
        }
      }

      JsonNode inserted = value == null ? NullNode.getInstance() : value;
      String target = parts[parts.length - 1];
      if (node.isObject()) {
        ((ObjectNode) node).set(target, inserted);
        return true;
      }
      if (node.isArray()) {
        ArrayNode array = (ArrayNode) node;
        int index = index(target, array.size());
        if (index < 0) {
          return false;
        }
        array.set(index, inserted);
        return true;
      }
      return false;
    }

    // Copy the field on first modification so the caller's nodes are never mutated.
    private static JsonNode mutable(ObjectNode event, String field, Set<String> copied) {
      if (copied.add(field)) {
        JsonNode copy = event.get(field).deepCopy();
        event.set(field, copy);
        return copy;
      }
      return event.get(field);
    }

    private static JsonNode child(JsonNode node, String key) {
      if (node.isObject()) {
        return node.get(key);
      }
      if (node.isArray()) {
        int index = index(key, node.size());
        return index < 0 ? null : node.get(index);
      }
      return null;
    }

    private static int index(String key, int size) {
      int index;
      try {
        index = Integer.parseInt(key);
      } catch (NumberFormatException e) {
        return -1;
      }
      return (index < 0 || index >= size) ? -1 : index;
    }

    private void record(ObjectNode event, String field) {
      JsonNode existing = event.get(field);
      ArrayNode applied = existing instanceof ArrayNode ? (ArrayNode) existing : event.putArray(field);
      applied.add(path());
    }

    private String path() {
      return "/" + String.join("/", parts);
    }

    @Override
    public String toString() {
      return path();
    }
  }

  // Mirrors Go's url.PathUnescape validation: '%' must introduce two hex digits.
  private static void checkEscaping(String part) {
    int i = 0;
    while (i < part.length()) {
      if (part.charAt(i) == '%') {
        if (i + 2 >= part.length() || !isHex(part.charAt(i + 1)) || !isHex(part.charAt(i + 2))) {
          throw new IllegalArgumentException(
              "invalid URL escape \"" + part.substring(i, Math.min(i + 3, part.length())) + "\"");
        }
        i += 3;
      } else {
        i++;
      }
    }
  }

  private static boolean isHex(char c) {
    return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
  }

  // Mirrors Go's url.PathEscape: unreserved characters and the sub-delimiters a path segment may
  // carry are left alone, everything else is percent-encoded.
  private static String pathEscape(String part) {
    byte[] bytes = part.getBytes(StandardCharsets.UTF_8);
    StringBuilder escaped = new StringBuilder(bytes.length);
    for (byte b : bytes) {
      char c = (char) (b & 0xFF);
      if (shouldEscape(c)) {
        escaped.append('%').append(Character.toUpperCase(Character.forDigit((b >> 4) & 0xF, 16)));
        escaped.append(Character.toUpperCase(Character.forDigit(b & 0xF, 16)));
      } else {
        escaped.append(c);
      }
    }
    return escaped.toString();
  }

  private static boolean shouldEscape(char c) {
    if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')) {
      return false;
    }
    switch (c) {
      case '-':
      case '_':
      case '.':
      case '~':
      case '$':
      case '&':
      case '+':
      case ':':
      case '=':
      case '@':
        return false;
      default:
        return true;
    }
  }

  @Override
  public String toString() {
    return "MaskRuleSet" + rules;
  }
}
