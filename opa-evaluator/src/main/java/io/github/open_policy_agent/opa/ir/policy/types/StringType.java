package io.github.open_policy_agent.opa.ir.policy.types;

import java.util.Objects;

public class StringType implements Type {
  public static final String TypeMarker = "string";

  public StringType() {}

  @Override
  public String typeMarker() {
    return TypeMarker;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    StringType that = (StringType) o;
    return Objects.equals(that.typeMarker(), TypeMarker);
  }

  @Override
  public int hashCode() {
    return Objects.hash(TypeMarker);
  }

  @Override
  public String toString() {
    return "StringType{}";
  }
}
