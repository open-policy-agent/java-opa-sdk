package io.github.open_policy_agent.opa.ir.policy.types;

import java.util.Objects;

public class NumberType implements Type {
  public static final String TypeMarker = "number";

  public NumberType() {}

  @Override
  public String typeMarker() {
    return TypeMarker;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    NumberType that = (NumberType) o;
    return Objects.equals(that.typeMarker(), TypeMarker);
  }

  @Override
  public int hashCode() {
    return Objects.hash(TypeMarker);
  }

  @Override
  public String toString() {
    return "NumberType{}";
  }
}
