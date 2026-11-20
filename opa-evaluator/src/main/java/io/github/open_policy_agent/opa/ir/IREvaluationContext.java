package io.github.open_policy_agent.opa.ir;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import io.github.open_policy_agent.opa.ast.types.RegoValue;
import io.github.open_policy_agent.opa.ir.policy.Func;
import io.github.open_policy_agent.opa.rego.EvaluationContext;

public class IREvaluationContext extends EvaluationContext {
  Map<String, Func> functions;
  Map<String, Func> functionsByPath;
  ArrayList<String> staticStrings;
  NumberConstants numberConstants;
  private RegoValue rootInput;
  private RegoValue rootData;
  private Map<Func, RegoValue> ruleResults;

  // private constructor to enforce the use of the Builder
  private IREvaluationContext(Builder builder) {
    super(builder.evaluationContext);
    this.functions = builder.functions;
    this.functionsByPath = builder.functionsByPath;
    this.staticStrings = builder.staticStrings;
    this.numberConstants =
        builder.numberConstants != null
            ? builder.numberConstants
            : new NumberConstants(builder.staticStrings.size());
  }

  /** The input and data this evaluation started with; only calls on exactly these are cached. */
  void setRoots(RegoValue input, RegoValue data) {
    this.rootInput = input;
    this.rootData = data;
  }

  boolean cachesRuleResults() {
    return ruleResultCacheActive();
  }

  // Identity, not equality: a copy of the input made by `with` must not count as the evaluation's own.
  // Data is never null in a real evaluation, so unset roots never match.
  @SuppressWarnings("PMD.CompareObjectsWithEquals")
  boolean isRootCall(RegoValue input, RegoValue data) {
    return rootData != null && input == rootInput && data == rootData;
  }

  boolean hasCachedRuleResult(Func func) {
    return ruleResults != null && ruleResults.containsKey(func);
  }

  /** The cached result of a rule, {@code null} when it was undefined or is not cached yet. */
  RegoValue cachedRuleResult(Func func) {
    return ruleResults == null ? null : ruleResults.get(func);
  }

  void cacheRuleResult(Func func, RegoValue result) {
    if (ruleResults == null) {
      ruleResults = new IdentityHashMap<>();
    }
    ruleResults.put(func, result);
  }

  boolean statementHooks() {
    return hasStatementHooks();
  }

  public static class Builder {
    private EvaluationContext evaluationContext;
    private Map<String, Func> functions = new HashMap<>();
    private Map<String, Func> functionsByPath = new HashMap<>();
    private ArrayList<String> staticStrings = new ArrayList<>();
    private NumberConstants numberConstants;

    public Builder withContext(EvaluationContext evaluationContext) {
      this.evaluationContext = evaluationContext;
      return this;
    }

    public Builder withFunctions(Map<String, Func> functions) {
      this.functions = functions;
      return this;
    }

    public Builder withFunctionsByPath(Map<String, Func> functionsByPath) {
      this.functionsByPath = functionsByPath;
      return this;
    }

    public Builder withStaticStrings(ArrayList<String> staticStrings) {
      this.staticStrings = staticStrings;
      return this;
    }

    Builder withNumberConstants(NumberConstants numberConstants) {
      this.numberConstants = numberConstants;
      return this;
    }

    public IREvaluationContext build() {
      // Only build functionsByPath if not already provided
      if ((this.functionsByPath == null || this.functionsByPath.isEmpty())
          && this.functions != null) {
        this.functionsByPath =
            this.functions.values().stream()
                .filter(f -> f.getPath() != null)
                .collect(Collectors.toMap(f -> String.join(".", f.getPath()), f -> f));
      }
      // Ensure functionsByPath is never null
      if (this.functionsByPath == null) {
        this.functionsByPath = new HashMap<>();
      }
      return new IREvaluationContext(this);
    }
  }
}
