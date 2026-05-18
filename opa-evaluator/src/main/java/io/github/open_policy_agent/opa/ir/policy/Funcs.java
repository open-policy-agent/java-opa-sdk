package io.github.open_policy_agent.opa.ir.policy;

import java.util.List;
import java.util.Objects;

/** Funcs represents a collection of planned functions to include in the policy. */
public class Funcs {
  private List<Func> funcs;

  public Funcs(List<Func> funcs) {
    this.funcs = funcs;
  }

  public Funcs() {}

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    Funcs funcs1 = (Funcs) o;

    return Objects.equals(funcs, funcs1.funcs);
  }

  @Override
  public int hashCode() {
    return funcs != null ? funcs.hashCode() : 0;
  }

  public List<Func> getFuncs() {
    return funcs;
  }

  public void setFuncs(List<Func> funcs) {
    this.funcs = funcs;
  }

  @Override
  public String toString() {
    return "Funcs{" + "funcs=" + funcs + '}';
  }
}
