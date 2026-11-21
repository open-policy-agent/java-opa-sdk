package io.github.open_policy_agent.opa.ast.builtin.impls;

import io.github.open_policy_agent.opa.ast.builtin.BuiltinError;
import io.github.open_policy_agent.opa.ast.builtin.OpaBuiltin;
import io.github.open_policy_agent.opa.ast.builtin.OpaType;
import io.github.open_policy_agent.opa.ast.types.RegoBigInt;
import io.github.open_policy_agent.opa.ast.types.RegoInt32;
import io.github.open_policy_agent.opa.ast.types.RegoNumber;
import io.github.open_policy_agent.opa.ast.types.RegoValue;
import io.github.open_policy_agent.opa.rego.EvaluationContext;
import io.github.open_policy_agent.opa.rego.TypeError;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Map;
import java.util.function.BiFunction;

/** Bitwise builtins. Operands are arbitrary-precision integers, as in OPA's {@code big.Int} implementation. */
public class BitsBuiltins {

  private static final BigInteger MAX_SHIFT = BigInteger.valueOf(Integer.MAX_VALUE);

  public static Map<String, BiFunction<EvaluationContext, RegoValue[], RegoValue>> builtins() {
    BitsBuiltins instance = new BitsBuiltins();
    // @formatter:off
    return Map.of(
        "bits.and", instance::and,
        "bits.or", instance::or,
        "bits.xor", instance::xor,
        "bits.negate", instance::negate,
        "bits.lsh", instance::lsh,
        "bits.rsh", instance::rsh);
    // @formatter:on
  }

  @OpaBuiltin(
      name = "bits.and",
      description = "Returns the bitwise \"AND\" of two integers.",
      args = {
        @OpaType(type = "number", name = "x", description = "the first integer"),
        @OpaType(type = "number", name = "y", description = "the second integer")
      },
      result = @OpaType(type = "number", name = "z", description = "the bitwise AND of `x` and `y`"))
  public RegoValue and(EvaluationContext ctx, RegoValue[] args) {
    return integer(intOperand(args, 0).and(intOperand(args, 1)));
  }

  @OpaBuiltin(
      name = "bits.or",
      description = "Returns the bitwise \"OR\" of two integers.",
      args = {
        @OpaType(type = "number", name = "x", description = "the first integer"),
        @OpaType(type = "number", name = "y", description = "the second integer")
      },
      result = @OpaType(type = "number", name = "z", description = "the bitwise OR of `x` and `y`"))
  public RegoValue or(EvaluationContext ctx, RegoValue[] args) {
    return integer(intOperand(args, 0).or(intOperand(args, 1)));
  }

  @OpaBuiltin(
      name = "bits.xor",
      description = "Returns the bitwise \"XOR\" (exclusive-or) of two integers.",
      args = {
        @OpaType(type = "number", name = "x", description = "the first integer"),
        @OpaType(type = "number", name = "y", description = "the second integer")
      },
      result = @OpaType(type = "number", name = "z", description = "the bitwise XOR of `x` and `y`"))
  public RegoValue xor(EvaluationContext ctx, RegoValue[] args) {
    return integer(intOperand(args, 0).xor(intOperand(args, 1)));
  }

  @OpaBuiltin(
      name = "bits.negate",
      description = "Returns the bitwise negation (flip) of an integer.",
      args = {@OpaType(type = "number", name = "x", description = "the integer to negate")},
      result = @OpaType(type = "number", name = "z", description = "the bitwise negation of `x`"))
  public RegoValue negate(EvaluationContext ctx, RegoValue[] args) {
    return integer(intOperand(args, 0).not());
  }

  @OpaBuiltin(
      name = "bits.lsh",
      description = "Returns a new integer with its bits shifted `s` bits to the left.",
      args = {
        @OpaType(type = "number", name = "x", description = "the integer to shift"),
        @OpaType(type = "number", name = "s", description = "the number of bits to shift")
      },
      result = @OpaType(type = "number", name = "z", description = "the result of shifting `x` `s` bits to the left"))
  public RegoValue lsh(EvaluationContext ctx, RegoValue[] args) {
    BigInteger x = intOperand(args, 0);
    BigInteger s = shiftOperand(args, 1);
    if (x.signum() == 0) {
      return integer(x);
    }
    if (s.compareTo(MAX_SHIFT) > 0) {
      throw new BuiltinError("operand 2 shift of " + s + " bits is too large");
    }
    return integer(x.shiftLeft(s.intValue()));
  }

  @OpaBuiltin(
      name = "bits.rsh",
      description = "Returns a new integer with its bits shifted `s` bits to the right.",
      args = {
        @OpaType(type = "number", name = "x", description = "the integer to shift"),
        @OpaType(type = "number", name = "s", description = "the number of bits to shift")
      },
      result = @OpaType(type = "number", name = "z", description = "the result of shifting `x` `s` bits to the right"))
  public RegoValue rsh(EvaluationContext ctx, RegoValue[] args) {
    BigInteger x = intOperand(args, 0);
    BigInteger s = shiftOperand(args, 1);
    if (s.compareTo(MAX_SHIFT) > 0) {
      // Every bit is shifted out: big.Int.Rsh rounds towards negative infinity.
      return integer(x.signum() < 0 ? BigInteger.ONE.negate() : BigInteger.ZERO);
    }
    return integer(x.shiftRight(s.intValue()));
  }

  // Like OPA's builtins.BigIntOperand: any number with an integral value, including 2.0.
  @SuppressWarnings("PMD.AvoidDecimalLiteralsInBigDecimalConstructor")
  private static BigInteger intOperand(RegoValue[] args, int index) {
    RegoValue arg = args[index];
    if (!(arg instanceof RegoNumber)) {
      throw new TypeError("operand " + (index + 1) + " must be integer but got " + arg.getTypeName());
    }

    RegoNumber n = (RegoNumber) arg;
    if (!n.isDecimal()) {
      return n.getBigIntValue();
    }

    double d = n.getDecimalValue();
    if (!Double.isFinite(d) || d != Math.rint(d)) {
      throw new TypeError("operand " + (index + 1) + " must be integer number but got floating-point number");
    }
    return new BigDecimal(d).toBigIntegerExact();
  }

  private static BigInteger shiftOperand(RegoValue[] args, int index) {
    BigInteger s = intOperand(args, index);
    if (s.signum() < 0) {
      throw new TypeError("operand " + (index + 1) + " must be an unsigned integer number but got a negative integer");
    }
    return s;
  }

  // RegoInt32 for values that fit, otherwise RegoBigInt
  private static RegoValue integer(BigInteger i) {
    return i.bitLength() < Integer.SIZE ? RegoInt32.of(i.intValue()) : new RegoBigInt(i);
  }
}
