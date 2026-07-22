package io.github.open_policy_agent.opa.ast.builtin.impls;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiFunction;
import io.github.open_policy_agent.opa.ast.builtin.BuiltinError;
import io.github.open_policy_agent.opa.ast.builtin.OpaBuiltin;
import io.github.open_policy_agent.opa.ast.builtin.OpaDynamic;
import io.github.open_policy_agent.opa.ast.builtin.OpaType;
import io.github.open_policy_agent.opa.ast.types.RegoArray;
import io.github.open_policy_agent.opa.ast.types.RegoObject;
import io.github.open_policy_agent.opa.ast.types.RegoString;
import io.github.open_policy_agent.opa.ast.types.RegoValue;
import io.github.open_policy_agent.opa.rego.EvaluationContext;

/**
 * Implements OPA builtins under the {@code opa.*} namespace.
 *
 * <p>{@code opa.runtime} mirrors Go OPA's {@code topdown/runtime.go}: returns the evaluation
 * context's runtime value when set, otherwise an empty object. When a {@code config} key is
 * present, service credentials and crypto private keys are scrubbed before returning.
 */
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
    if (ctx.getNdBuiltinCache() != null) {
      RegoValue cachedValue = ctx.getNdBuiltinCache().get("opa.runtime", args);
      if (cachedValue != null) {
        return cachedValue;
      }
    }

    RegoValue result = resolveRuntime(ctx);

    if (ctx.getNdBuiltinCache() != null) {
      ctx.getNdBuiltinCache().put("opa.runtime", args, result);
    }
    ctx.recordNdCacheValue("opa.runtime", args, result);

    return result;
  }

  private static RegoValue resolveRuntime(EvaluationContext ctx) {
    RegoValue runtime = ctx.getRuntime();
    if (runtime == null) {
      return new RegoObject();
    }
    return scrubConfigIfPresent(runtime);
  }

  /**
   * When {@code config} is present, return a copy with credentials/keys scrubbed. Otherwise return
   * the runtime value unchanged (matching Go OPA).
   */
  private static RegoValue scrubConfigIfPresent(RegoValue runtime) {
    if (!(runtime instanceof RegoObject runtimeObj)) {
      return runtime;
    }
    RegoValue configVal = runtimeObj.getProperty("config");
    if (!(configVal instanceof RegoObject config)) {
      return runtime;
    }

    RegoObject result = new RegoObject(new LinkedHashMap<>(runtimeObj.getProperties()));
    result.setProperty("config", activeConfig(config));
    return result;
  }

  /** Scrub secrets from config, matching Go {@code activeConfig}. */
  private static RegoObject activeConfig(RegoObject config) {
    RegoObject result = new RegoObject(new LinkedHashMap<>(config.getProperties()));

    RegoValue services = result.getProperty("services");
    if (services != null) {
      result.setProperty("services", removeServiceCredentials(services));
    }

    RegoValue keys = result.getProperty("keys");
    if (keys != null) {
      result.setProperty("keys", removeCryptoKeys(keys));
    }

    return result;
  }

  private static RegoValue removeServiceCredentials(RegoValue services) {
    if (services instanceof RegoObject map) {
      RegoObject result = new RegoObject();
      for (Map.Entry<RegoValue, RegoValue> entry : map.getProperties().entrySet()) {
        result.setProp(entry.getKey(), removeKeys(entry.getValue(), "credentials"));
      }
      return result;
    }
    if (services instanceof RegoArray arr) {
      RegoArray result = new RegoArray();
      for (RegoValue value : arr.getValues()) {
        result.addValue(removeKeys(value, "credentials"));
      }
      return result;
    }
    throw new BuiltinError("illegal service config type: " + services.getTypeName());
  }

  private static RegoValue removeCryptoKeys(RegoValue keys) {
    if (!(keys instanceof RegoObject map)) {
      throw new BuiltinError("illegal keys config type: " + keys.getTypeName());
    }
    RegoObject result = new RegoObject();
    for (Map.Entry<RegoValue, RegoValue> entry : map.getProperties().entrySet()) {
      result.setProp(entry.getKey(), removeKeys(entry.getValue(), "key", "private_key"));
    }
    return result;
  }

  private static RegoObject removeKeys(RegoValue value, String... keys) {
    if (!(value instanceof RegoObject obj)) {
      throw new BuiltinError("type assertion error");
    }
    RegoObject copy = new RegoObject(new LinkedHashMap<>(obj.getProperties()));
    for (String key : keys) {
      copy.getProperties().remove(new RegoString(key));
    }
    return copy;
  }
}
