package io.github.open_policy_agent.opa.ir;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import io.github.open_policy_agent.opa.ir.policy.Func;
import io.github.open_policy_agent.opa.rego.EvaluationContext;

public class IREvaluationContext extends EvaluationContext {
  Map<String, Func> functions;
  Map<String, Func> functionsByPath;
  ArrayList<String> staticStrings;

  // private constructor to enforce the use of the Builder
  private IREvaluationContext(Builder builder) {
    super(builder.evaluationContext);
    this.functions = builder.functions;
    this.functionsByPath = builder.functionsByPath;
    this.staticStrings = builder.staticStrings;
  }

  public static class Builder {
    private EvaluationContext evaluationContext;
    private Map<String, Func> functions = new HashMap<>();
    private Map<String, Func> functionsByPath = new HashMap<>();
    private ArrayList<String> staticStrings = new ArrayList<>();

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
