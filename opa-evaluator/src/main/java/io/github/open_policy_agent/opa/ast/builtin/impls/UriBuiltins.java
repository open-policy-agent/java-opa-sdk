package io.github.open_policy_agent.opa.ast.builtin.impls;

import io.github.open_policy_agent.opa.ast.builtin.BuiltinError;
import io.github.open_policy_agent.opa.ast.builtin.OpaBuiltin;
import io.github.open_policy_agent.opa.ast.builtin.OpaType;
import io.github.open_policy_agent.opa.ast.types.RegoBoolean;
import io.github.open_policy_agent.opa.ast.types.RegoObject;
import io.github.open_policy_agent.opa.ast.types.RegoString;
import io.github.open_policy_agent.opa.ast.types.RegoValue;
import io.github.open_policy_agent.opa.rego.EvaluationContext;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.function.BiFunction;

import static io.github.open_policy_agent.opa.ast.builtin.impls.utils.ArgHelper.getArg;

public class UriBuiltins {
  public static Map<String, BiFunction<EvaluationContext, RegoValue[], RegoValue>> builtins() {
    UriBuiltins instance = new UriBuiltins();

    return Map.of(
        "uri.parse", instance::parse,
        "uri.is_valid", instance::isValid);
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

  @OpaBuiltin(
      name = "uri.parse",
      description = "Parses an input URI and returns its components.",
      categories = {"encoding"},
      args = {@OpaType(name = "x", description = "URI string to parse")},
      result = @OpaType(name = "result", description = "Parsed URI components"))
  public RegoObject parse(EvaluationContext ctx, RegoValue[] args) {
    String input = getArg(args, 0, RegoString.class).getValue();

    try {
      URI uri = new URI(input);
      RegoObject result = new RegoObject();

      if (uri.getScheme() != null && !uri.getScheme().isEmpty()) {
        result.setProperty("scheme", new RegoString(uri.getScheme()));
      }

      String hostname = uri.getHost();
      if (hostname != null && !hostname.isEmpty()) {
        if (hostname.startsWith("[") && hostname.endsWith("]")) {
          hostname = hostname.substring(1, hostname.length() - 1);
        }

        result.setProperty("hostname", new RegoString(hostname));
      }

      if (uri.getPort() != -1) {
        result.setProperty("port", new RegoString(Integer.toString(uri.getPort())));
      }

      String path = uri.getPath();
      if (path != null && !path.isEmpty()) {
        result.setProperty("path", new RegoString(path));

        String rawPath = uri.getRawPath();
        if (rawPath == null || rawPath.isEmpty()) {
          rawPath = path;
        }

        result.setProperty("raw_path", new RegoString(rawPath));
      }

      String rawQuery = uri.getRawQuery();
      if (rawQuery != null && !rawQuery.isEmpty()) {
        result.setProperty("raw_query", new RegoString(rawQuery));
      }

      String fragment = uri.getFragment();
      if (fragment != null && !fragment.isEmpty()) {
        result.setProperty("fragment", new RegoString(fragment));
      }

      return result;
    } catch (URISyntaxException e) {
      throw new BuiltinError(e.getMessage());
    }
  }
}
