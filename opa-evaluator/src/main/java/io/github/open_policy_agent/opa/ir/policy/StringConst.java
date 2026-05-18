package io.github.open_policy_agent.opa.ir.policy;

import java.util.Objects;

public class StringConst {
  private String value;

  public StringConst(String value) {
    this.value = value;
  }

  public StringConst() {}

  public String getValue() {
    return value;
  }

  public void setValue(String value) {
    this.value = value;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    StringConst that = (StringConst) o;

    return Objects.equals(value, that.value);
  }

  @Override
  public int hashCode() {
    return value != null ? value.hashCode() : 0;
  }
}
