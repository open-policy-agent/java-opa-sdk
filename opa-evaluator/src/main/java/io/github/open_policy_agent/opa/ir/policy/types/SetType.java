package io.github.open_policy_agent.opa.ir.policy.types;

import java.util.Objects;

public class SetType implements Type {
  public static final String TypeMarker = "set";

  private Type of;

  public SetType() {}

  public SetType(Type of) {
    this.of = of;
  }

  @Override
  public String typeMarker() {
    return TypeMarker;
  }

  public Type getOf() {
    return of;
  }

  public void setOf(Type of) {
    this.of = of;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    SetType setType = (SetType) o;

    return Objects.equals(of, setType.of);
  }

  @Override
  public int hashCode() {
    return of != null ? of.hashCode() : 0;
  }

  @Override
  public String toString() {
    return "SetType{" + "of=" + of + '}';
  }
}
