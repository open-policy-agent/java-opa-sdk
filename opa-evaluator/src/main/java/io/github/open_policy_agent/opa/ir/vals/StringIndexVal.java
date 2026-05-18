package io.github.open_policy_agent.opa.ir.vals;

import java.util.Objects;

/**
 * StringIndex represents the index into the plan's list of constant strings of a constant string.
 */
public class StringIndexVal implements Val {
  private String type;

  private int value;

  public StringIndexVal() {}

  public StringIndexVal(String type, int value) {
    this.type = type;
    this.value = value;
  }

  public StringIndexVal(int value) {
    this.type = "string_index";
    this.value = value;
  }

  @Override
  public String typeHint() {
    return "string_index";
  }

  @Override
  public String toString() {
    return String.format("String<%d>", value);
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

    StringIndexVal that = (StringIndexVal) o;

    if (value != that.value) return false;
    return Objects.equals(type, that.type);
  }

  @Override
  public int hashCode() {
    int result = type != null ? type.hashCode() : 0;
    result = 31 * result + value;
    return result;
  }
}
