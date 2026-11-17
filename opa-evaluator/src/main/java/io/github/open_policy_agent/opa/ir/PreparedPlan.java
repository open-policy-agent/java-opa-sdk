package io.github.open_policy_agent.opa.ir;

import java.util.ArrayList;
import java.util.Map;
import java.util.stream.Collectors;
import io.github.open_policy_agent.opa.ir.policy.Func;
import io.github.open_policy_agent.opa.ir.policy.Plan;
import io.github.open_policy_agent.opa.ir.policy.Policy;

/**
 * Pre-computed plan data for optimized evaluation.
 *
 * <p>This class holds pre-computed values that would otherwise need to be calculated on every
 * evaluation. By computing these values once during query preparation, we can significantly reduce
 * per-evaluation overhead.
 *
 * <p>Cached values include:
 *
 * <ul>
 *   <li>Plan reference (eliminates map lookup)
 *   <li>Frame capacity (eliminates addition)
 *   <li>Functions-by-path map (eliminates stream processing)
 *   <li>Static strings reference
 *   <li>Function registry reference
 * </ul>
 */
public class PreparedPlan {
  private final Plan plan;
  private final int frameCapacity;
  private final Map<String, Func> functionsByPath;
  private final ArrayList<String> staticStrings;
  private final NumberConstants numberConstants;
  private final Map<String, Func> funcRegistry;

  private PreparedPlan(Builder builder) {
    this.plan = builder.plan;
    this.frameCapacity = builder.frameCapacity;
    this.functionsByPath = builder.functionsByPath;
    this.staticStrings = builder.staticStrings;
    this.numberConstants = builder.numberConstants;
    this.funcRegistry = builder.funcRegistry;
  }

  public Plan getPlan() {
    return plan;
  }

  public int getFrameCapacity() {
    return frameCapacity;
  }

  public Map<String, Func> getFunctionsByPath() {
    return functionsByPath;
  }

  public ArrayList<String> getStaticStrings() {
    return staticStrings;
  }

  NumberConstants getNumberConstants() {
    return numberConstants;
  }

  public Map<String, Func> getFuncRegistry() {
    return funcRegistry;
  }

  public static class Builder {
    private Plan plan;
    private int frameCapacity;
    private Map<String, Func> functionsByPath;
    private ArrayList<String> staticStrings;
    private NumberConstants numberConstants;
    private Map<String, Func> funcRegistry;

    /**
     * Warm up the plan for the specified entrypoint using data from the evaluator.
     *
     * @param entrypoint the entrypoint to prepare (e.g., "example/allow")
     * @param evaluator the evaluator containing the policy and functions
     * @return this builder
     */
    public Builder warmUp(String entrypoint, Evaluator evaluator) {
      Policy policy = evaluator.getPolicy();

      // Pre-lookup the plan
      Plan plan = policy.getPlans().getPlanByName(entrypoint);
      if (plan == null) {
        throw new PolicyNotFoundException(entrypoint)
            .withContext("availablePlans", policy.getPlans().getPlans());
      }
      this.plan = plan;

      // Pre-compute frame capacity (minimum 2 for input and data at indices 0 and 1)
      int capacity = plan.getMaxLocals() + 1;
      this.frameCapacity = Math.max(capacity, 2);

      // Get references to static data
      this.funcRegistry = evaluator.getFuncRegistry();
      this.staticStrings = evaluator.getStaticStrings();
      this.numberConstants = evaluator.getNumberConstants();

      // Pre-compute functionsByPath map (this is the expensive operation)
      if (this.funcRegistry != null) {
        this.functionsByPath =
            this.funcRegistry.values().stream()
                .filter(f -> f.getPath() != null)
                .collect(Collectors.toMap(f -> String.join(".", f.getPath()), f -> f));
      }

      return this;
    }

    public PreparedPlan build() {
      return new PreparedPlan(this);
    }
  }
}
