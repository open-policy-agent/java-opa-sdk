package io.github.open_policy_agent.opa.ast.builtin.impls;

import java.util.Map;
import java.util.function.BiFunction;
import io.github.open_policy_agent.opa.ast.builtin.OpaBuiltin;
import io.github.open_policy_agent.opa.ast.builtin.OpaDynamic;
import io.github.open_policy_agent.opa.ast.builtin.OpaType;
import io.github.open_policy_agent.opa.ast.types.RegoObject;
import io.github.open_policy_agent.opa.ast.types.RegoValue;
import io.github.open_policy_agent.opa.rego.EvaluationContext;

public class OpaBuiltins {

  public static Map<String, BiFunction<EvaluationContext, RegoValue[], RegoValue>> builtins() {
    OpaBuiltins instance = new OpaBuiltins();
    return Map.of("opa.runtime", instance::runtime);
  }

  @OpaBuiltin(
      name = "opa.runtime",
      description = "Returns runtime environment and configuration metadata.",
      categories = {"opa"},
      args = {},
      result =
          @OpaType(
              type = "object",
              name = "output",
              description =
                  "includes a `config` key if OPA was started with a configuration file; an `env` key containing the environment variables that the OPA process was started with; includes `version` and `commit` keys containing the version and build commit of OPA.",
              dynamic = @OpaDynamic(keyType = "string", valueType = "any")),
      nondeterministic = true)
  public RegoValue runtime(EvaluationContext ctx, RegoValue[] args) {
    if (ctx != null && ctx.getNdBuiltinCache() != null) {
      RegoValue cachedValue = ctx.getNdBuiltinCache().get("opa.runtime", args);
      if (cachedValue != null) {
        return cachedValue;
      }
    }

    RegoObject result = new RegoObject();

    if (ctx != null) {
      if (ctx.getNdBuiltinCache() != null) {
        ctx.getNdBuiltinCache().put("opa.runtime", args, result);
      }
      ctx.recordNdCacheValue("opa.runtime", args, result);
    }

    return result;
  }
}
