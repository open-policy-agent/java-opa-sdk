package io.github.open_policy_agent.opa.ir;

import java.util.Objects;
import io.github.open_policy_agent.opa.ir.vals.Val;

/** Operand represents a value that a statement operates on. */
public class Operand {
  private Val value;

  public Operand(Val value) {
    this.value = value;
  }

  public Operand() {}

  public Val getValue() {
    return value;
  }

  public void setValue(Val value) {
    this.value = value;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    Operand operand = (Operand) o;

    return Objects.equals(value, operand.value);
  }

  @Override
  public int hashCode() {
    return value != null ? value.hashCode() : 0;
  }

  @Override
  public String toString() {
    return "Operand{" + "value=" + value + '}';
  }
}
