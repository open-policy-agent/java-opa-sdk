package io.github.open_policy_agent.opa.ast.types;

import java.util.*;
import java.util.stream.Stream;

public class RegoObject implements RegoValue {
  private final Map<RegoValue, RegoValue> value;

  public RegoObject() {
    this.value = new LinkedHashMap<>();
  }

  public RegoObject(Map<RegoValue, RegoValue> properties) {
    this.value = new LinkedHashMap<>(properties);
  }

  public RegoValue getProperty(String property) {
    return value.get(new RegoString(property));
  }

  public RegoValue getProperty(RegoValue property) {
    return value.get(property);
  }

  public boolean hasProperty(RegoValue property) {
    return value.containsKey(property);
  }

  /** Get the internal map with RegoValue keys. This is used for runtime operations. */
  public Map<RegoValue, RegoValue> getProperties() {
    return value;
  }

  public RegoValue setProp(RegoValue property, RegoValue value) {
    return this.value.put(property, value);
  }

  /**
   * Programmatic setter used by callers that want to add a property by string name. The
   * {@code opa-jackson} {@code RegoValueModule} converts JSON values directly during
   * deserialization, so this method no longer needs to dispatch on a generic {@link Object}.
   */
  public void setProperty(String name, RegoValue value) {
    this.value.put(new RegoString(name), value);
  }

  public Stream<Map.Entry<RegoValue, RegoValue>> stream() {
    return value.entrySet().stream();
  }

  @Override
  public int length() {
    return value.size();
  }

  @Override
  public Object nativeValue() {
    Map<Object, Object> nativeMap = new LinkedHashMap<>();
    for (Map.Entry<RegoValue, RegoValue> entry : value.entrySet()) {
      nativeMap.put(entry.getKey().nativeValue(), entry.getValue().nativeValue());
    }
    return nativeMap;
  }

  /**
   * Asymmetric recursive union of two objects. Conflicts are resolved by choosing the value from
   * the right-hand object (other). When both values are objects, they are recursively merged.
   *
   * @param other the right-hand object (wins on conflicts)
   * @return a new merged object
   */
  public RegoObject merge(RegoObject other) {
    RegoObject result = new RegoObject();

    // Start with all properties from this (left object)
    result.value.putAll(this.value);

    // Merge in properties from other (right object)
    for (Map.Entry<RegoValue, RegoValue> entry : other.value.entrySet()) {
      RegoValue key = entry.getKey();
      RegoValue rightValue = entry.getValue();
      RegoValue leftValue = result.value.get(key);

      if (leftValue == null) {
        // Key only exists in right object - add it
        result.value.put(key, rightValue);
      } else if (leftValue instanceof RegoObject && rightValue instanceof RegoObject) {
        // Both values are objects - recursively merge them
        RegoObject mergedValue = ((RegoObject) leftValue).merge((RegoObject) rightValue);
        result.value.put(key, mergedValue);
      } else {
        // Conflict: right object wins
        result.value.put(key, rightValue);
      }
    }

    return result;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    RegoObject that = (RegoObject) o;
    return Objects.equals(value, that.value);
  }

  @Override
  public int hashCode() {
    return Objects.hash(value);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("{");
    boolean first = true;
    for (Map.Entry<RegoValue, RegoValue> entry : value.entrySet()) {
      if (!first) {
        sb.append(",");
      }
      first = false;

      // Format the key - all keys need quotes in JSON
      RegoValue key = entry.getKey();
      if (key instanceof RegoString) {
        sb.append("\"").append(escapeJsonString(((RegoString) key).getValue())).append("\"");
      } else if (key instanceof RegoNumber) {
        sb.append("\"").append(key).append("\"");
      } else {
        // For complex types (objects, arrays, etc.), output as escaped JSON string
        sb.append("\"").append(escapeJsonString(key.toString())).append("\"");
      }

      sb.append(":");
      sb.append(entry.getValue().toString());
    }
    sb.append("}");
    return sb.toString();
  }

  private String escapeJsonString(String s) {
    StringBuilder sb = new StringBuilder();
    for (char c : s.toCharArray()) {
      switch (c) {
        case '"':
          sb.append("\\\"");
          break;
        case '\\':
          sb.append("\\\\");
          break;
        case '\b':
          sb.append("\\b");
          break;
        case '\f':
          sb.append("\\f");
          break;
        case '\n':
          sb.append("\\n");
          break;
        case '\r':
          sb.append("\\r");
          break;
        case '\t':
          sb.append("\\t");
          break;
        default:
          if (c < 0x20 || c == 0x7F) {
            sb.append(String.format("\\u%04x", (int) c));
          } else {
            sb.append(c);
          }
          break;
      }
    }
    return sb.toString();
  }

  @Override
  public String getTypeName() {
    return "object";
  }
}
