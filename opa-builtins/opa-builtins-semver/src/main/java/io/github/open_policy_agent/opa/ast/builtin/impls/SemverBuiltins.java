package io.github.open_policy_agent.opa.ast.builtin.impls;

import java.util.Map;
import java.util.function.BiFunction;
import io.github.open_policy_agent.opa.ast.builtin.BuiltinError;
import io.github.open_policy_agent.opa.ast.builtin.BuiltinProvider;
import io.github.open_policy_agent.opa.ast.builtin.OpaBuiltin;
import io.github.open_policy_agent.opa.ast.builtin.OpaType;
import io.github.open_policy_agent.opa.ast.types.RegoBoolean;
import io.github.open_policy_agent.opa.ast.types.RegoInt32;
import io.github.open_policy_agent.opa.ast.types.RegoNumber;
import io.github.open_policy_agent.opa.ast.types.RegoString;
import io.github.open_policy_agent.opa.ast.types.RegoValue;
import io.github.open_policy_agent.opa.rego.EvaluationContext;
import org.semver4j.Semver;

public class SemverBuiltins implements BuiltinProvider {

  @Override
  public Map<String, BiFunction<EvaluationContext, RegoValue[], RegoValue>> builtins() {
    SemverBuiltins instance = new SemverBuiltins();
    return Map.of(
        "semver.compare", instance::compare,
        "semver.is_valid", instance::isValid);
  }

  @OpaBuiltin(
      name = "semver.compare",
      description = "Compares two semver versions",
      categories = {"comparators"},
      args = {
        @OpaType(type = "string", name = "a", description = "first version string"),
        @OpaType(type = "string", name = "b", description = "second version string")
      },
      result =
          @OpaType(
              type = "number",
              name = "result",
              description = "`-1` if `a < b`; `1` if `a > b`; `0` if `a == b`"))
  public RegoNumber compare(EvaluationContext ctx, RegoValue[] args) {
    String version1 = (String) args[0].nativeValue();
    String version2 = (String) args[1].nativeValue();
    if (!Semver.isValid(version1)) {
      throw new BuiltinError(
          "semver.compare: operand 1: string \"" + version1 + "\" is not a valid SemVer");
    }

    if (!Semver.isValid(version2)) {
      throw new BuiltinError(
          "semver.compare: operand 2: string \"" + version2 + "\" is not a valid SemVer");
    }
    return RegoInt32.of(new Semver(version1).compareTo(Semver.parse(version2)));
  }

  @OpaBuiltin(
      name = "semver.is_valid",
      description = "Returns true if the input is a valid semver version",
      categories = {"validators"},
      args = {@OpaType(type = "any", name = "vsn", description = "input to validate")},
      result =
          @OpaType(
              type = "boolean",
              name = "result",
              description = "`true` if `vsn` is a valid SemVer; `false` otherwise"))
  public RegoBoolean isValid(EvaluationContext ctx, RegoValue[] args) {
    RegoValue versionValue = args[0];
    if (!(versionValue instanceof RegoString)) {
      return RegoBoolean.FALSE;
    }
    String version = ((RegoString) versionValue).getValue();
    if (!version.contains(".")) { // test 0351 seems to indicate we need more than just the major
      return RegoBoolean.FALSE;
    }
    return RegoBoolean.of(Semver.isValid(version));
  }
}
