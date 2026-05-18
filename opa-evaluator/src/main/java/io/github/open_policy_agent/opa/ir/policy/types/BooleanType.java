package io.github.open_policy_agent.opa.ir.policy.types;

import java.util.Objects;

public class BooleanType implements Type {
  public static final String TypeMarker = "boolean";

  public BooleanType() {}

  @Override
  public String typeMarker() {
    return TypeMarker;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    BooleanType that = (BooleanType) o;
    return Objects.equals(that.typeMarker(), TypeMarker);
  }

  @Override
  public int hashCode() {
    return Objects.hash(TypeMarker);
  }

  @Override
  public String toString() {
    return "BooleanType{}";
  }
}
