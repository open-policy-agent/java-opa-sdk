package io.github.open_policy_agent.opa.ir.policy.types;

import java.util.List;
import java.util.Objects;

public class AnyType implements Type {
  public static final String TypeMarker = "any";

  private List<Type> of;

  public AnyType() {}

  public AnyType(List<Type> of) {
    this.of = of;
  }

  @Override
  public String typeMarker() {
    return TypeMarker;
  }

  public List<Type> getOf() {
    return of;
  }

  public void setOf(List<Type> of) {
    this.of = of;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    AnyType anyType = (AnyType) o;

    return Objects.equals(of, anyType.of);
  }

  @Override
  public int hashCode() {
    return of != null ? of.hashCode() : 0;
  }

  @Override
  public String toString() {
    return "AnyType{" + "of=" + of + '}';
  }
}
