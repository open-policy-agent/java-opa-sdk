package io.github.open_policy_agent.opa.ast.builtin.impls;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.Map;
import java.util.function.BiFunction;
import io.github.open_policy_agent.opa.ast.builtin.BuiltinError;
import io.github.open_policy_agent.opa.ast.builtin.OpaBuiltin;
import io.github.open_policy_agent.opa.ast.builtin.OpaType;
import io.github.open_policy_agent.opa.ast.types.RegoBigInt;
import io.github.open_policy_agent.opa.ast.types.RegoDecimal;
import io.github.open_policy_agent.opa.ast.types.RegoString;
import io.github.open_policy_agent.opa.ast.types.RegoValue;
import io.github.open_policy_agent.opa.rego.EvaluationContext;

import static io.github.open_policy_agent.opa.ast.builtin.impls.utils.ArgHelper.getArg;

public class UnitsBuiltins {

  // Maximum number of digits allowed in the exponent of scientific notation input.
  private static final int MAX_EXPONENT_DIGITS = 6;

  private static final BigDecimal SI_MILLI = new BigDecimal("0.001");
  private static final BigDecimal SI_K = BigDecimal.valueOf(1_000L);
  private static final BigDecimal SI_M = BigDecimal.valueOf(1_000_000L);
  private static final BigDecimal SI_G = BigDecimal.valueOf(1_000_000_000L);
  private static final BigDecimal SI_T = BigDecimal.valueOf(1_000_000_000_000L);
  private static final BigDecimal SI_P = BigDecimal.valueOf(1_000_000_000_000_000L);
  private static final BigDecimal SI_E = BigDecimal.valueOf(1_000_000_000_000_000_000L);

  private static final BigDecimal BI_KI = BigDecimal.valueOf(1L << 10);
  private static final BigDecimal BI_MI = BigDecimal.valueOf(1L << 20);
  private static final BigDecimal BI_GI = BigDecimal.valueOf(1L << 30);
  private static final BigDecimal BI_TI = BigDecimal.valueOf(1L << 40);
  private static final BigDecimal BI_PI = BigDecimal.valueOf(1L << 50);
  private static final BigDecimal BI_EI = BigDecimal.valueOf(1L << 60);

  public static Map<String, BiFunction<EvaluationContext, RegoValue[], RegoValue>> builtins() {
    UnitsBuiltins instance = new UnitsBuiltins();
    return Map.of(
        "units.parse", instance::parse,
        "units.parse_bytes", instance::parseBytes);
  }

  @OpaBuiltin(
      name = "units.parse",
      description =
          "Converts strings like \"10G\", \"5K\", \"4M\", \"1500m\" and the like into a number. "
              + "This number can be a non-integer, such as 1.5, 0.22, etc. Supports standard "
              + "metric decimal and binary SI units (e.g., K, Ki, M, Mi, G, Gi etc.) "
              + "`units.parse(\"1\")` also works and returns 1.",
      categories = {"units"},
      args = {@OpaType(type = "string", name = "x", description = "the unit to parse")},
      result = @OpaType(type = "number", name = "y", description = "the parsed number"))
  public RegoValue parse(EvaluationContext ctx, RegoValue[] args) {
    String raw = getArg(args, 0, RegoString.class).getValue();
    String s = raw.replace("\"", "");

    if (s.contains(" ")) {
      throw new BuiltinError("units.parse: spaces not allowed in resource strings");
    }

    String[] numAndUnit;
    try {
      numAndUnit = extractNumAndUnit(s);
    } catch (ExponentTooLargeException e) {
      throw new BuiltinError("units.parse: exponent too large");
    }

    String num = numAndUnit[0];
    String unit = numAndUnit[1];

    if (num.isEmpty()) {
      throw new BuiltinError("units.parse: no amount provided");
    }

    // Unlike in units.parse_bytes, we only lowercase after the first letter,
    // so that we can distinguish between 'm' and 'M'.
    if (unit.length() > 1) {
      unit = unit.substring(0, 1) + unit.substring(1).toLowerCase();
    }

    BigDecimal multiplier;
    switch (unit) {
      case "":
        multiplier = BigDecimal.ONE;
        break;
      case "m":
        multiplier = SI_MILLI;
        break;
      case "k":
      case "K":
        multiplier = SI_K;
        break;
      case "ki":
      case "Ki":
        multiplier = BI_KI;
        break;
      case "M":
        multiplier = SI_M;
        break;
      case "mi":
      case "Mi":
        multiplier = BI_MI;
        break;
      case "g":
      case "G":
        multiplier = SI_G;
        break;
      case "gi":
      case "Gi":
        multiplier = BI_GI;
        break;
      case "t":
      case "T":
        multiplier = SI_T;
        break;
      case "ti":
      case "Ti":
        multiplier = BI_TI;
        break;
      case "p":
      case "P":
        multiplier = SI_P;
        break;
      case "pi":
      case "Pi":
        multiplier = BI_PI;
        break;
      case "e":
      case "E":
        multiplier = SI_E;
        break;
      case "ei":
      case "Ei":
        multiplier = BI_EI;
        break;
      default:
        throw new BuiltinError("units.parse: unit " + unit + " not recognized");
    }

    BigDecimal amount;
    try {
      amount = new BigDecimal(num);
    } catch (NumberFormatException e) {
      throw new BuiltinError("units.parse: could not parse amount to a number");
    }

    BigDecimal product = amount.multiply(multiplier);

    try {
      return new RegoBigInt(product.toBigIntegerExact());
    } catch (ArithmeticException e) {
      // Cleaner printout when we have a pure integer value; otherwise round to 10 decimal
      // places, matching the reference implementation's use of big.Rat.FloatString(10).
      BigDecimal rounded = product.setScale(10, RoundingMode.HALF_UP);
      return new RegoDecimal(Double.parseDouble(rounded.toPlainString()));
    }
  }

  @OpaBuiltin(
      name = "units.parse_bytes",
      description =
          "Converts strings like \"10GB\", \"5K\", \"4mb\" into an integer number of bytes. "
              + "Supports standard byte units (e.g., K, Ki, M, Mi, G, Gi, ...) as well as their "
              + "power-of-two variants (e.g., Ki, Mi, Gi, ...). Without a unit, bytes are "
              + "assumed.",
      categories = {"units"},
      args = {@OpaType(type = "string", name = "x", description = "the byte unit to parse")},
      result = @OpaType(type = "number", name = "y", description = "the parsed number"))
  public RegoValue parseBytes(EvaluationContext ctx, RegoValue[] args) {
    String raw = getArg(args, 0, RegoString.class).getValue();
    String s = raw.toLowerCase().replace("\"", "");

    if (s.contains(" ")) {
      throw new BuiltinError("units.parse_bytes: spaces not allowed in resource strings");
    }

    String[] numAndUnit;
    try {
      numAndUnit = extractNumAndUnit(s);
    } catch (ExponentTooLargeException e) {
      throw new BuiltinError("units.parse_bytes: exponent too large");
    }

    String num = numAndUnit[0];
    String unit = numAndUnit[1];

    if (num.isEmpty()) {
      throw new BuiltinError("units.parse_bytes: no byte amount provided");
    }

    BigDecimal multiplier;
    switch (unit) {
      case "":
        multiplier = BigDecimal.ONE;
        break;
      case "kb":
      case "k":
        multiplier = SI_K;
        break;
      case "kib":
      case "ki":
        multiplier = BI_KI;
        break;
      case "mb":
      case "m":
        multiplier = SI_M;
        break;
      case "mib":
      case "mi":
        multiplier = BI_MI;
        break;
      case "gb":
      case "g":
        multiplier = SI_G;
        break;
      case "gib":
      case "gi":
        multiplier = BI_GI;
        break;
      case "tb":
      case "t":
        multiplier = SI_T;
        break;
      case "tib":
      case "ti":
        multiplier = BI_TI;
        break;
      case "pb":
      case "p":
        multiplier = SI_P;
        break;
      case "pib":
      case "pi":
        multiplier = BI_PI;
        break;
      case "eb":
      case "e":
        multiplier = SI_E;
        break;
      case "eib":
      case "ei":
        multiplier = BI_EI;
        break;
      default:
        throw new BuiltinError("units.parse_bytes: byte unit " + unit + " not recognized");
    }

    BigDecimal amount;
    try {
      amount = new BigDecimal(num);
    } catch (NumberFormatException e) {
      throw new BuiltinError("units.parse_bytes: could not parse byte amount to a number");
    }

    // Truncate towards zero, matching the reference implementation's use of big.Float.Int().
    BigInteger result = amount.multiply(multiplier).toBigInteger();
    return new RegoBigInt(result);
  }

  private static final class ExponentTooLargeException extends RuntimeException {}

  /**
   * Splits a string into a number part such as "10" or "10.2" and a unit part such as "gb" or
   * "MiB" (either part may be empty). Mirrors the reference implementation's scanning logic so
   * that scientific notation (with an optional sign and up to {@link #MAX_EXPONENT_DIGITS}
   * exponent digits) is recognized as part of the number.
   */
  private static String[] extractNumAndUnit(String s) {
    int firstNonNumIdx = -1;
    int idx = 0;
    while (idx < s.length()) {
      char r = s.charAt(idx);
      boolean isNum = Character.isDigit(r) || r == '.';
      if (!isNum && r != 'e' && r != 'E' && r != '+' && r != '-') {
        firstNonNumIdx = idx;
        break;
      }
      if (r == 'e' || r == 'E') {
        if (idx == s.length() - 1
            || (!Character.isDigit(s.charAt(idx + 1))
                && s.charAt(idx + 1) != '+'
                && s.charAt(idx + 1) != '-')) {
          firstNonNumIdx = idx;
          break;
        }
        if (idx + 1 < s.length() && (s.charAt(idx + 1) == '+' || s.charAt(idx + 1) == '-')) {
          idx++;
        }

        int expStart = idx + 1;
        int expEnd = expStart;
        while (expEnd < s.length() && Character.isDigit(s.charAt(expEnd))) {
          expEnd++;
        }
        if (expEnd - expStart > MAX_EXPONENT_DIGITS) {
          throw new ExponentTooLargeException();
        }
      }
      idx++;
    }

    if (firstNonNumIdx == -1) {
      return new String[] {s, ""};
    }
    if (firstNonNumIdx == 0) {
      return new String[] {"", s};
    }
    return new String[] {s.substring(0, firstNonNumIdx), s.substring(firstNonNumIdx)};
  }
}
