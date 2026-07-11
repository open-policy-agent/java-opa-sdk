package io.github.open_policy_agent.opa.ast.builtin.impls;

import static io.github.open_policy_agent.opa.ast.builtin.impls.utils.ArgHelper.getArg;

import java.util.Map;
import java.util.function.BiFunction;

import io.github.open_policy_agent.opa.ast.builtin.OpaBuiltin;
import io.github.open_policy_agent.opa.ast.builtin.OpaDynamic;
import io.github.open_policy_agent.opa.ast.builtin.OpaType;
import io.github.open_policy_agent.opa.ast.types.RegoBigInt;
import io.github.open_policy_agent.opa.ast.types.RegoNumber;
import io.github.open_policy_agent.opa.ast.types.RegoValue;
import io.github.open_policy_agent.opa.rego.EvaluationContext;
import io.github.open_policy_agent.opa.rego.TypeError;

public class BitsBuiltins {

  public static Map<String, BiFunction<EvaluationContext, RegoValue[], RegoValue>> builtins() {
    BitsBuiltins instance = new BitsBuiltins();
    return Map.ofEntries(
        Map.entry("bits.and", instance::and),
        Map.entry("bits.or", instance::or),
        Map.entry("bits.xor", instance::xor),
        Map.entry("bits.negate", instance::negate),
        Map.entry("bits.lsh", instance::lsh),
        Map.entry("bits.rsh", instance::rsh));
  }

  @OpaBuiltin(
      name = "bits.and",
      description = "Returns the bitwise AND of two integers.",
      args = {
        @OpaType(type = "number", name = "x", description = "the first integer", dynamic = @OpaDynamic(type = "any")),
        @OpaType(type = "number", name = "y", description = "the second integer", dynamic = @OpaDynamic(type = "any"))
      },
      result = @OpaType(type = "number", name = "z", description = "the bitwise AND of x and y", dynamic = @OpaDynamic(type = "any")))
  public RegoValue and(EvaluationContext ctx, RegoValue[] args) {
    RegoNumber x = getArg(args, 0, RegoNumber.class);
    RegoNumber y = getArg(args, 1, RegoNumber.class);
    requireInteger(x, "bits.and", 1);
    requireInteger(y, "bits.and", 2);
    return new RegoBigInt(x.getBigIntValue().and(y.getBigIntValue()));
  }

  @OpaBuiltin(
      name = "bits.or",
      description = "Returns the bitwise OR of two integers.",
      args = {
        @OpaType(type = "number", name = "x", description = "the first integer", dynamic = @OpaDynamic(type = "any")),
        @OpaType(type = "number", name = "y", description = "the second integer", dynamic = @OpaDynamic(type = "any"))
      },
      result = @OpaType(type = "number", name = "z", description = "the bitwise OR of x and y", dynamic = @OpaDynamic(type = "any")))
  public RegoValue or(EvaluationContext ctx, RegoValue[] args) {
    RegoNumber x = getArg(args, 0, RegoNumber.class);
    RegoNumber y = getArg(args, 1, RegoNumber.class);
    requireInteger(x, "bits.or", 1);
    requireInteger(y, "bits.or", 2);
    return new RegoBigInt(x.getBigIntValue().or(y.getBigIntValue()));
  }

  @OpaBuiltin(
      name = "bits.xor",
      description = "Returns the bitwise XOR of two integers.",
      args = {
        @OpaType(type = "number", name = "x", description = "the first integer", dynamic = @OpaDynamic(type = "any")),
        @OpaType(type = "number", name = "y", description = "the second integer", dynamic = @OpaDynamic(type = "any"))
      },
      result = @OpaType(type = "number", name = "z", description = "the bitwise XOR of x and y", dynamic = @OpaDynamic(type = "any")))
  public RegoValue xor(EvaluationContext ctx, RegoValue[] args) {
    RegoNumber x = getArg(args, 0, RegoNumber.class);
    RegoNumber y = getArg(args, 1, RegoNumber.class);
    requireInteger(x, "bits.xor", 1);
    requireInteger(y, "bits.xor", 2);
    return new RegoBigInt(x.getBigIntValue().xor(y.getBigIntValue()));
  }

  @OpaBuiltin(
      name = "bits.negate",
      description = "Returns the bitwise negation of an integer.",
      args = {
        @OpaType(type = "number", name = "x", description = "the integer to negate", dynamic = @OpaDynamic(type = "any"))
      },
      result = @OpaType(type = "number", name = "z", description = "the bitwise negation of x", dynamic = @OpaDynamic(type = "any")))
  public RegoValue negate(EvaluationContext ctx, RegoValue[] args) {
    RegoNumber x = getArg(args, 0, RegoNumber.class);
    requireInteger(x, "bits.negate", 1);
    return new RegoBigInt(x.getBigIntValue().not());
  }

  @OpaBuiltin(
      name = "bits.lsh",
      description = "Returns the result of shifting x left by s bits.",
      args = {
        @OpaType(type = "number", name = "x", description = "the integer to shift", dynamic = @OpaDynamic(type = "any")),
        @OpaType(type = "number", name = "s", description = "the number of bits to shift", dynamic = @OpaDynamic(type = "any"))
      },
      result = @OpaType(type = "number", name = "z", description = "the result of shifting x s bits to the left", dynamic = @OpaDynamic(type = "any")))
  public RegoValue lsh(EvaluationContext ctx, RegoValue[] args) {
    RegoNumber x = getArg(args, 0, RegoNumber.class);
    RegoNumber s = getArg(args, 1, RegoNumber.class);
    requireInteger(x, "bits.lsh", 1);
    requireUnsigned(s, "bits.lsh");
    return new RegoBigInt(x.getBigIntValue().shiftLeft(s.getBigIntValue().intValueExact()));
  }

  @OpaBuiltin(
      name = "bits.rsh",
      description = "Returns the result of shifting x right by s bits.",
      args = {
        @OpaType(type = "number", name = "x", description = "the integer to shift", dynamic = @OpaDynamic(type = "any")),
        @OpaType(type = "number", name = "s", description = "the number of bits to shift", dynamic = @OpaDynamic(type = "any"))
      },
      result = @OpaType(type = "number", name = "z", description = "the result of shifting x s bits to the right", dynamic = @OpaDynamic(type = "any")))
  public RegoValue rsh(EvaluationContext ctx, RegoValue[] args) {
    RegoNumber x = getArg(args, 0, RegoNumber.class);
    RegoNumber s = getArg(args, 1, RegoNumber.class);
    requireInteger(x, "bits.rsh", 1);
    requireUnsigned(s, "bits.rsh");
    return new RegoBigInt(x.getBigIntValue().shiftRight(s.getBigIntValue().intValueExact()));
  }

  private static void requireInteger(RegoNumber n, String fn, int operandIndex) {
    if (n.isDecimal()) {
      throw new TypeError(fn + ": operand " + operandIndex + " must be integer number but got floating-point number");
    }
  }

  private static void requireUnsigned(RegoNumber s, String fn) {
    if (s.getBigIntValue().signum() < 0) {
      throw new TypeError(fn + ": operand 2 must be an unsigned integer number but got a negative integer");
    }
  }
}
