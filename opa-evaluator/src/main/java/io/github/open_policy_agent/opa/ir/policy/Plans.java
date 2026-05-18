package io.github.open_policy_agent.opa.ir.policy;

import java.util.*;

/** Represents a collection of named query plans to expose in the policy. */
public class Plans {
  private List<Plan> plans;

  private final Map<String, Plan> planMap;

  public Plans() {
    this.planMap = new TreeMap<>();
  }

  public Plans(List<Plan> plans) {
    this();
    setPlans(plans);
  }

  public List<Plan> getPlans() {
    return plans;
  }

  public void setPlans(List<Plan> plans) {
    this.plans = plans;
    if (plans == null) {
      return;
    }

    for (Plan plan : plans) {
      planMap.put(plan.getName(), plan);
    }
  }

  public Plan getPlanByName(String name) {
    if ("".equals(name)) {
      // The spec says to just use the first plan if no plan name is specified.
      return planMap.values().iterator().next();
    }
    return planMap.get(name);
  }

  @Override
  public String toString() {
    return "Plans{" + "plans=" + plans + '}';
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    Plans plans1 = (Plans) o;

    if (!Objects.equals(plans, plans1.plans)) return false;
    return Objects.equals(planMap, plans1.planMap);
  }

  @Override
  public int hashCode() {
    int result = plans != null ? plans.hashCode() : 0;
    result = 31 * result + (planMap != null ? planMap.hashCode() : 0);
    return result;
  }
}
