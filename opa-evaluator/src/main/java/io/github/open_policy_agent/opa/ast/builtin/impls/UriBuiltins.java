package io.github.open_policy_agent.opa.ast.builtin.impls;

import io.github.open_policy_agent.opa.ast.builtin.OpaBuiltin;
import io.github.open_policy_agent.opa.ast.builtin.OpaType;
import io.github.open_policy_agent.opa.ast.types.RegoBoolean;
import io.github.open_policy_agent.opa.ast.types.RegoString;
import io.github.open_policy_agent.opa.ast.types.RegoValue;
import io.github.open_policy_agent.opa.rego.EvaluationContext;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.function.BiFunction;

public class UriBuiltins {
  public static Map<String, BiFunction<EvaluationContext, RegoValue[], RegoValue>> builtins() {
    UriBuiltins instance = new UriBuiltins();

    return Map.of("uri.is_valid", instance::isValid);
  }

  @OpaBuiltin(
      name = "uri.is_valid",
      description = "Returns true if the input string is a valid URI",
      categories = {"encoding"},
      args = {@OpaType(name = "x", description = "URI string to validate")},
      result = @OpaType(name = "result", description = "true if x is a valid URI"))
  public RegoBoolean isValid(EvaluationContext ctx, RegoValue[] args) {
    if (!(args[0] instanceof RegoString input)) {
      return RegoBoolean.FALSE;
    }

    if (input.getValue().isEmpty()) {
      return RegoBoolean.FALSE;
    }

    try {
      new URI(input.getValue());
      return RegoBoolean.TRUE;
    } catch (URISyntaxException e) {
      return RegoBoolean.FALSE;
    }
  }
}
