package io.github.open_policy_agent.opa.ir.vals;

import java.util.Objects;

/** Local represents a plan-scoped variable. */
public class LocalVal implements Val {
  private String type;

  private int value;

  public LocalVal() {}

  public LocalVal(String type, int value) {
    this.type = type;
    this.value = value;
  }

  public LocalVal(int value) {
    this.type = "local";
    this.value = value;
  }

  @Override
  public String typeHint() {
    return "local";
  }

  @Override
  public String toString() {
    return String.format("Local<%d>", value);
  }

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  public int getValue() {
    return value;
  }

  public void setValue(int value) {
    this.value = value;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    LocalVal localVal = (LocalVal) o;

    if (value != localVal.value) return false;
    return Objects.equals(type, localVal.type);
  }

  @Override
  public int hashCode() {
    int result = type != null ? type.hashCode() : 0;
    result = 31 * result + value;
    return result;
  }
}
