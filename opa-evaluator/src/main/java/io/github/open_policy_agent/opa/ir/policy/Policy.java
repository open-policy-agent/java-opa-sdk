package io.github.open_policy_agent.opa.ir.policy;

import java.util.Objects;

/** Represents a planned policy query. */
public class Policy {
  // Field is named "staticField" because "static" is a Java reserved word.
  // Accessors are named getStatic / setStatic so Jackson auto-detects the JSON "static" key.
  private Static staticField;

  private Plans plans;

  private Funcs funcs;

  public Policy(Static staticField, Plans plans, Funcs funcs) {
    this.staticField = staticField;
    this.plans = plans;
    this.funcs = funcs;
  }

  public Policy() {}

  @Override
  public String toString() {
    return "Policy{" + "staticField=" + staticField + ", plans=" + plans + ", funcs=" + funcs + '}';
  }

  public Static getStatic() {
    return staticField;
  }

  public void setStatic(Static staticField) {
    this.staticField = staticField;
  }

  public Plans getPlans() {
    return plans;
  }

  public void setPlans(Plans plans) {
    this.plans = plans;
  }

  public Funcs getFuncs() {
    return funcs;
  }

  public void setFuncs(Funcs funcs) {
    this.funcs = funcs;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    Policy policy = (Policy) o;

    if (!Objects.equals(staticField, policy.staticField)) return false;
    if (!Objects.equals(plans, policy.plans)) return false;
    return Objects.equals(funcs, policy.funcs);
  }

  @Override
  public int hashCode() {
    int result = staticField != null ? staticField.hashCode() : 0;
    result = 31 * result + (plans != null ? plans.hashCode() : 0);
    result = 31 * result + (funcs != null ? funcs.hashCode() : 0);
    return result;
  }
}
