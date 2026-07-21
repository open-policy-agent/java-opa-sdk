package io.github.open_policy_agent.opa.ast.builtin.impls;

import io.github.open_policy_agent.opa.ast.types.RegoArray;
import io.github.open_policy_agent.opa.ast.types.RegoBigInt;
import io.github.open_policy_agent.opa.ast.types.RegoDecimal;
import io.github.open_policy_agent.opa.ast.types.RegoInt32;
import io.github.open_policy_agent.opa.ast.types.RegoNumber;
import io.github.open_policy_agent.opa.ast.types.RegoSet;
import io.github.open_policy_agent.opa.ast.types.RegoString;
import io.github.open_policy_agent.opa.ast.types.RegoValue;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import static java.lang.Math.abs;
import static java.lang.Math.ceil;
import static java.lang.Math.floor;
import static java.lang.Math.round;
import static io.github.open_policy_agent.opa.ast.builtin.impls.utils.ArgHelper.getArg;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.function.BiFunction;
import io.github.open_policy_agent.opa.ast.builtin.BuiltinError;
import io.github.open_policy_agent.opa.ast.builtin.OpaBuiltin;
import io.github.open_policy_agent.opa.ast.builtin.OpaType;
import io.github.open_policy_agent.opa.rego.EvaluationContext;
import io.github.open_policy_agent.opa.rego.TypeError;

public class ArithmeticBuiltins {

  public static Map<String, BiFunction<EvaluationContext, RegoValue[], RegoValue>> builtins() {
    ArithmeticBuiltins instance = new ArithmeticBuiltins();
    return Map.ofEntries(
        Map.entry("mul", instance::mul),
        Map.entry("div", instance::div),
        Map.entry("plus", instance::plus),
        Map.entry("minus", instance::minus),
        Map.entry("rem", instance::rem),
        Map.entry("abs", instance::abs_f),
        Map.entry("floor", instance::floor_f),
        Map.entry("round", instance::round_f),
        Map.entry("ceil", instance::ceil_f),
        Map.entry("numbers.range", instance::range),
        Map.entry("numbers.range_step", instance::rangeStep),
        Map.entry("rand.intn", instance::rand));
  }

  @OpaBuiltin(
      name = "numbers.range_step",
      description = "Returns an array of numbers from a to b, inclusive, with step",
      args = {
        @OpaType(type = "number", name = "a", description = "start of range"),
        @OpaType(type = "number", name = "b", description = "end of range"),
        @OpaType(type = "number", name = "step", description = "step size of range")
      },
      result =
          @OpaType(
              type = "array",
              description = "array of numbers from a to b, inclusive, with step"))
  public RegoArray rangeStep(EvaluationContext ctx, RegoValue[] args) {
    RegoNumber a = getArg(args, 0, RegoNumber.class);
    RegoNumber b = getArg(args, 1, RegoNumber.class);
    RegoNumber step = getArg(args, 2, RegoNumber.class);
    return rangeWithStep(a, b, step.getBigIntValue());
  }

  @OpaBuiltin(
      name = "numbers.range",
      description = "Returns an array of numbers from a to b, inclusive",
      args = {
        @OpaType(type = "number", name = "a", description = "start of range"),
        @OpaType(type = "number", name = "b", description = "end of range")
      },
      result =
          @OpaType(
              type = "array",
              name = "range",
              description = "the sequence of integers from `x` to `y` inclusive"))
  public RegoArray range(EvaluationContext ctx, RegoValue[] args) {
    RegoNumber a = getArg(args, 0, RegoNumber.class);
    RegoNumber b = getArg(args, 1, RegoNumber.class);
    if (a.isDecimal()) {
      throw new TypeError("numbers.range: operand 1 must be integer number but got floating-point number");
    }
    if (b.isDecimal()) {
      throw new TypeError("numbers.range: operand 2 must be integer number but got floating-point number");
    }
    return rangeWithStep(a, b, BigInteger.ONE);
  }

  private RegoArray rangeWithStep(RegoNumber a, RegoNumber b, BigInteger step) {
    // Callers are responsible for pre-validating that a and b are not decimal
    List<RegoValue> range = new ArrayList<>();
    if (step.signum() <= 0) {
      throw new BuiltinError("numbers.range_step: step must be a positive integer");
    }
    if (a.compareTo(b) <= 0) {
      for (var i = a.getBigIntValue(); i.compareTo(b.getBigIntValue()) <= 0; i = i.add(step)) {
        range.add(new RegoBigInt(i));
      }
    } else {
      for (var i = a.getBigIntValue(); i.compareTo(b.getBigIntValue()) >= 0; i = i.subtract(step)) {
        range.add(new RegoBigInt(i));
      }
    }
    return new RegoArray(range);
  }

  @OpaBuiltin(
      name = "ceil",
      description = "Rounds a number up to the nearest integer",
      categories = {"numbers"},
      args = {@OpaType(type = "number", name = "x", description = "the number to round")},
      result =
          @OpaType(type = "number", name = "y", description = "the result of rounding `x` _up_"))
  public RegoValue ceil_f(EvaluationContext ctx, RegoValue[] args) {
    RegoNumber x = getArg(args, 0, RegoNumber.class);

    if (x.isDecimal()) {
      long result = (long) ceil(x.getDecimalValue());
      // Return RegoInt32 for values that fit, otherwise RegoBigInt
      if (result >= Integer.MIN_VALUE && result <= Integer.MAX_VALUE) {
        return RegoInt32.of((int) result);
      }
      return new RegoBigInt(result);
    } else {
      // Integer already - return as-is
      return x;
    }
  }

  @OpaBuiltin(
      name = "round",
      description = "Rounds the number to the nearest integer",
      categories = {"numbers"},
      args = {@OpaType(type = "number", name = "x", description = "the number to round")},
      result = @OpaType(type = "number", description = "the result of rounding x"))
  public RegoValue round_f(EvaluationContext ctx, RegoValue[] args) {
    RegoNumber x = getArg(args, 0, RegoNumber.class);

    if (x.isDecimal()) {
      long result = round(x.getDecimalValue());
      // Return RegoInt32 for values that fit, otherwise RegoBigInt
      if (result >= Integer.MIN_VALUE && result <= Integer.MAX_VALUE) {
        return RegoInt32.of((int) result);
      }
      return new RegoBigInt(result);
    } else {
      // Integer already - return as-is
      return x;
    }
  }

  @OpaBuiltin(
      name = "floor",
      description = "Rounds the number down to the nearest integer",
      categories = {"numbers"},
      args = {@OpaType(type = "number", name = "x", description = "the number to round")},
      result =
          @OpaType(type = "number", name = "y", description = "the result of rounding `x` _down_"))
  public RegoValue floor_f(EvaluationContext ctx, RegoValue[] args) {
    RegoNumber x = getArg(args, 0, RegoNumber.class);

    if (x.isDecimal()) {
      long result = (long) floor(x.getDecimalValue());
      // Return RegoInt32 for values that fit, otherwise RegoBigInt
      if (result >= Integer.MIN_VALUE && result <= Integer.MAX_VALUE) {
        return RegoInt32.of((int) result);
      }
      return new RegoBigInt(result);
    } else {
      // Integer already - return as-is
      return x;
    }
  }

  @OpaBuiltin(
      name = "abs",
      description = "Returns the number without its sign",
      categories = {"numbers"},
      args = {
        @OpaType(
            type = "number",
            name = "x",
            description = "the number to take the absolute value of")
      },
      result = @OpaType(type = "number", name = "y", description = "the absolute value of `x`"))
  public RegoValue abs_f(EvaluationContext ctx, RegoValue[] args) {
    RegoNumber x = getArg(args, 0, RegoNumber.class);

    if (x.isDecimal()) {
      return new RegoDecimal(abs(x.getDecimalValue()));
    } else {
      return new RegoBigInt(abs(x.getBigIntValue().longValueExact()));
    }
  }

  @OpaBuiltin(
      name = "rem",
      description = "Returns the remainder for of x divided by y, for y != 0",
      categories = {"numbers"},
      args = {@OpaType(type = "number", name = "x"), @OpaType(type = "number", name = "y")},
      result =
          @OpaType(
              type = "number",
              name = "z",
              description = "the remainder of `x` divided by `y`"),
      infix = "%")
  public RegoValue rem(EvaluationContext ctx, RegoValue[] args) {
    RegoNumber x = getArg(args, 0, RegoNumber.class);
    RegoNumber y = getArg(args, 1, RegoNumber.class);

    if (y.getBigIntValue().equals(BigInteger.ZERO)) {
      throw new BuiltinError("modulo by zero");
    }

    if (x.isDecimal() || y.isDecimal()) {
      throw new BuiltinError("modulo on floating-point number");
    }

    return new RegoBigInt((x.getBigIntValue().divideAndRemainder(y.getBigIntValue()))[1]);
  }

  @OpaBuiltin(
      name = "plus",
      description = "Adds two numbers",
      args = {
        @OpaType(type = "number", name = "x", description = "first number"),
        @OpaType(type = "number", name = "y", description = "second number")
      },
      result = @OpaType(type = "number", name = "z", description = "the result of `x` plus `y`"),
      infix = "+")
  public RegoValue plus(EvaluationContext ctx, RegoValue[] args) {
    RegoNumber x = getArg(args, 0, RegoNumber.class);
    RegoNumber y = getArg(args, 1, RegoNumber.class);

    if (x.isDecimal() || y.isDecimal()) {
      return new RegoDecimal(x.getDecimalValue() + y.getDecimalValue());
    } else {
      return new RegoBigInt((x.getBigIntValue().add(y.getBigIntValue())));
    }
  }

  @OpaBuiltin(
      name = "minus",
      description = "Subtracts two numbers or computes set difference",
      args = {
        @OpaType(
            type = "number",
            name = "x",
            description = "number to subtract from (or set for set difference)"),
        @OpaType(
            type = "number",
            name = "y",
            description = "number to subtract (or set for set difference)")
      },
      result = @OpaType(type = "number", name = "z", description = "the result of `x` minus `y`"),
      infix = "-")
  public RegoValue minus(EvaluationContext ctx, RegoValue[] args) {
    if (args[0] instanceof RegoNumber && args[1] instanceof RegoNumber) {
      RegoNumber x = getArg(args, 0, RegoNumber.class);
      RegoNumber y = getArg(args, 1, RegoNumber.class);

      if (x.isDecimal() || y.isDecimal()) {
        return new RegoDecimal(x.getDecimalValue() - y.getDecimalValue());
      } else {
        return new RegoBigInt((x.getBigIntValue().subtract(y.getBigIntValue())));
      }
    } else if (args[0] instanceof RegoSet) {
      if (!(args[1] instanceof RegoSet)) {
        throw new TypeError("operand 2 must be set but got " + args[1].getTypeName());
      }
      RegoSet x = getArg(args, 0, RegoSet.class);
      RegoSet y = getArg(args, 1, RegoSet.class);

      Set<RegoValue> newSet = new HashSet<>(x.getValue());
      newSet.removeAll(y.getValue());
      return new RegoSet(ctx.sortSets, newSet);
    } else if (args[0] instanceof RegoNumber) {
      throw new TypeError("operand 2 must be number but got " + args[1].getTypeName());
    } else {
      throw new TypeError(
              "Arithmetic operation not supported for types: "
                  + args[0].getTypeName()
                  + ", "
                  + args[1].getTypeName())
          .withContext("operation", "subtraction")
          .withContext("leftType", args[0].getTypeName())
          .withContext("rightType", args[1].getTypeName());
    }
  }

  @OpaBuiltin(
      name = "mul",
      description = "Multiplies two numbers",
      categories = {"numbers"},
      args = {@OpaType(type = "number", name = "x"), @OpaType(type = "number", name = "y")},
      result =
          @OpaType(
              type = "number",
              name = "z",
              description = "the result of `x` multiplied by `y`"),
      infix = "*")
  public RegoValue mul(EvaluationContext ctx, RegoValue[] args) {
    RegoNumber x = getArg(args, 0, RegoNumber.class);
    RegoNumber y = getArg(args, 1, RegoNumber.class);

    if (x.isDecimal() || y.isDecimal()) {
      return new RegoDecimal(x.getDecimalValue() * y.getDecimalValue());
    } else {
      return new RegoBigInt((x.getBigIntValue().multiply(y.getBigIntValue())));
    }
  }

  @OpaBuiltin(
      name = "div",
      description = "Divides the first number by the second number",
      categories = {"numbers"},
      args = {
        @OpaType(type = "number", name = "x", description = "the dividend"),
        @OpaType(type = "number", name = "y", description = "the divisor")
      },
      result =
          @OpaType(type = "number", name = "z", description = "the result of `x` divided by `y`"),
      infix = "/")
  public RegoValue div(EvaluationContext ctx, RegoValue[] args) {
    RegoNumber x = getArg(args, 0, RegoNumber.class);
    RegoNumber y = getArg(args, 1, RegoNumber.class);

    if (y.getBigIntValue().equals(BigInteger.ZERO)) {
      throw new BuiltinError("div: divide by zero");
    }

    if (x.isDecimal() || y.isDecimal()) {
      return new RegoDecimal(x.getDecimalValue() / y.getDecimalValue());
    } else {
      BigDecimal xDec = new BigDecimal(x.getBigIntValue());
      BigDecimal yDec = new BigDecimal(y.getBigIntValue());
      BigDecimal result = xDec.divide(yDec, 50, RoundingMode.HALF_UP);
      return new RegoDecimal(result.doubleValue());
    }
  }

  @OpaBuiltin(
      name = "rand.intn",
      description = "Returns a random integer in the range `[0, abs(n))`",
      categories = {"random"},
      args = {
        @OpaType(
            type = "string",
            name = "str",
            description = "seed string for deterministic random number generation"),
        @OpaType(type = "number", name = "n", description = "upper bound (exclusive)")
      },
      result =
          @OpaType(
              type = "number",
              name = "y",
              description = "random integer in the range `[0, abs(n))`"))
  public RegoNumber rand(EvaluationContext ctx, RegoValue[] args) {
    RegoString str = getArg(args, 0, RegoString.class);
    RegoNumber n = getArg(args, 1, RegoNumber.class);

    if (n.isDecimal()) {
      throw new TypeError("operand 2 must be integer but got floating-point number");
    }

    BigInteger bound = n.getBigIntValue().abs();

    if (bound.equals(BigInteger.ZERO)) {
      throw new BuiltinError("rand.intn: operand 2 must be non-zero");
    }

    long seed = 0;
    byte[] bytes = str.getValue().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    for (byte b : bytes) {
      seed = seed * 31 + b;
    }

    Random random = new Random(seed);

    if (bound.compareTo(BigInteger.valueOf(Integer.MAX_VALUE)) > 0) {
      BigInteger randomBigInt = new BigInteger(bound.bitLength(), random);
      while (randomBigInt.compareTo(bound) >= 0) {
        randomBigInt = new BigInteger(bound.bitLength(), random);
      }
      return new RegoBigInt(randomBigInt);
    } else {
      int result = random.nextInt(bound.intValue());
      return RegoInt32.of(result);
    }
  }
}
