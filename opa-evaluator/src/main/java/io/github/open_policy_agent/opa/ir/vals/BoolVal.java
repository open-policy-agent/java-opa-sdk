package io.github.open_policy_agent.opa.ir.vals;

import java.util.Objects;

/** Bool represents a constant boolean. */
public class BoolVal implements Val {
  private String type;

  private boolean value;

  public BoolVal() {}

  public BoolVal(String type, boolean value) {
    this.type = type;
    this.value = value;
  }

  public BoolVal(boolean value) {
    this.type = "bool";
    this.value = value;
  }

  @Override
  public String typeHint() {
    return "bool";
  }

  @Override
  public String toString() {
    return String.format("Bool<%s>", value);
  }

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  public boolean isValue() {
    return value;
  }

  public void setValue(boolean value) {
    this.value = value;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    BoolVal boolVal = (BoolVal) o;

    if (value != boolVal.value) return false;
    return Objects.equals(type, boolVal.type);
  }

  @Override
  public int hashCode() {
    int result = type != null ? type.hashCode() : 0;
    result = 31 * result + (value ? 1 : 0);
    return result;
  }
}
