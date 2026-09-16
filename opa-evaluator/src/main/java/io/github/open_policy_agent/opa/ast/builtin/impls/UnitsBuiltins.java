package io.github.open_policy_agent.opa.ast.builtin.impls;

import static io.github.open_policy_agent.opa.ast.builtin.impls.utils.ArgHelper.getArg;

import io.github.open_policy_agent.opa.ast.builtin.BuiltinError;
import io.github.open_policy_agent.opa.ast.builtin.OpaBuiltin;
import io.github.open_policy_agent.opa.ast.builtin.OpaType;
import io.github.open_policy_agent.opa.ast.types.RegoBigInt;
import io.github.open_policy_agent.opa.ast.types.RegoDecimal;
import io.github.open_policy_agent.opa.ast.types.RegoString;
import io.github.open_policy_agent.opa.ast.types.RegoValue;
import io.github.open_policy_agent.opa.rego.EvaluationContext;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UnitsBuiltins {
  private static final Pattern AMOUNT_PATTERN =
      Pattern.compile("^([+-]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)(?:[eE][+-]?\\d+)?)(.*)$");
  private static final Pattern EXPONENT_PATTERN = Pattern.compile("[eE]([+-]?\\d+)");

  private static final Map<String, BigDecimal> DECIMAL_UNITS =
      Map.ofEntries(
          Map.entry("", BigDecimal.ONE),
          Map.entry("k", BigDecimal.valueOf(1_000L)),
          Map.entry("K", BigDecimal.valueOf(1_000L)),
          Map.entry("m", new BigDecimal("0.001")),
          Map.entry("M", BigDecimal.valueOf(1_000_000L)),
          Map.entry("g", BigDecimal.valueOf(1_000_000_000L)),
          Map.entry("G", BigDecimal.valueOf(1_000_000_000L)),
          Map.entry("t", BigDecimal.valueOf(1_000_000_000_000L)),
          Map.entry("T", BigDecimal.valueOf(1_000_000_000_000L)),
          Map.entry("p", BigDecimal.valueOf(1_000_000_000_000_000L)),
          Map.entry("P", BigDecimal.valueOf(1_000_000_000_000_000L)),
          Map.entry("e", new BigDecimal("1000000000000000000")),
          Map.entry("E", new BigDecimal("1000000000000000000")),
          Map.entry("ki", BigDecimal.valueOf(1_024L)),
          Map.entry("mi", BigDecimal.valueOf(1_048_576L)),
          Map.entry("gi", BigDecimal.valueOf(1_073_741_824L)),
          Map.entry("ti", BigDecimal.valueOf(1_099_511_627_776L)),
          Map.entry("pi", BigDecimal.valueOf(1_125_899_906_842_624L)),
          Map.entry("ei", new BigDecimal("1152921504606846976")));

  private static final Map<String, BigDecimal> BYTE_UNITS =
      Map.ofEntries(
          Map.entry("", BigDecimal.ONE),
          Map.entry("k", BigDecimal.valueOf(1_000L)),
          Map.entry("K", BigDecimal.valueOf(1_000L)),
          Map.entry("m", BigDecimal.valueOf(1_000_000L)),
          Map.entry("M", BigDecimal.valueOf(1_000_000L)),
          Map.entry("g", BigDecimal.valueOf(1_000_000_000L)),
          Map.entry("G", BigDecimal.valueOf(1_000_000_000L)),
          Map.entry("t", BigDecimal.valueOf(1_000_000_000_000L)),
          Map.entry("T", BigDecimal.valueOf(1_000_000_000_000L)),
          Map.entry("p", BigDecimal.valueOf(1_000_000_000_000_000L)),
          Map.entry("P", BigDecimal.valueOf(1_000_000_000_000_000L)),
          Map.entry("e", new BigDecimal("1000000000000000000")),
          Map.entry("E", new BigDecimal("1000000000000000000")),
          Map.entry("ki", BigDecimal.valueOf(1_024L)),
          Map.entry("mi", BigDecimal.valueOf(1_048_576L)),
          Map.entry("gi", BigDecimal.valueOf(1_073_741_824L)),
          Map.entry("ti", BigDecimal.valueOf(1_099_511_627_776L)),
          Map.entry("pi", BigDecimal.valueOf(1_125_899_906_842_624L)),
          Map.entry("ei", new BigDecimal("1152921504606846976")));

  public static Map<String, BiFunction<EvaluationContext, RegoValue[], RegoValue>> builtins() {
    UnitsBuiltins instance = new UnitsBuiltins();
    return Map.of(
        "units.parse", instance::parse,
        "units.parse_bytes", instance::parseBytes);
  }

  @OpaBuiltin(
      name = "units.parse",
      description = "Parses a resource quantity string into a number.",
      categories = {"units"},
      args = {@OpaType(type = "string", name = "x", description = "resource quantity")},
      result = @OpaType(type = "number", name = "n", description = "parsed quantity"))
  public RegoValue parse(EvaluationContext ctx, RegoValue[] args) {
    String input = getArg(args, 0, RegoString.class).getValue();
    return parseQuantity(input, "units.parse", DECIMAL_UNITS, false);
  }

  @OpaBuiltin(
      name = "units.parse_bytes",
      description = "Parses a byte quantity string into a number of bytes.",
      categories = {"units"},
      args = {@OpaType(type = "string", name = "x", description = "byte quantity")},
      result = @OpaType(type = "number", name = "n", description = "parsed byte quantity"))
  public RegoValue parseBytes(EvaluationContext ctx, RegoValue[] args) {
    String input = getArg(args, 0, RegoString.class).getValue();
    return parseQuantity(input, "units.parse_bytes", BYTE_UNITS, true);
  }

  private RegoValue parseQuantity(
      String input, String name, Map<String, BigDecimal> units, boolean byteMode) {
    if (input.chars().anyMatch(Character::isWhitespace)) {
      throw new BuiltinError(name + ": spaces not allowed in resource strings");
    }

    Matcher matcher = AMOUNT_PATTERN.matcher(input);
    if (!matcher.matches()) {
      throw new BuiltinError(name + ": no " + amountName(byteMode) + " provided");
    }

    String amountText = matcher.group(1);
    String unitText = normalizeUnit(matcher.group(2), byteMode);
    BigDecimal unit = units.get(unitText);
    if (unit == null) {
      if (matcher.group(2).startsWith(".")) {
        throw new BuiltinError(name + ": could not parse " + amountName(byteMode) + " to a number");
      }
      throw new BuiltinError(name + ": unit " + matcher.group(2) + " not recognized");
    }

    BigDecimal amount = parseAmount(name, amountText, byteMode);
    return toRegoNumber(amount.multiply(unit));
  }

  private static String normalizeUnit(String unit, boolean byteMode) {
    String normalized = unit;
    if (byteMode
        && !normalized.isEmpty()
        && normalized.substring(normalized.length() - 1).equalsIgnoreCase("b")) {
      normalized = normalized.substring(0, normalized.length() - 1);
    }
    if (normalized.length() > 1) {
      return normalized.toLowerCase();
    }
    return normalized;
  }

  private static BigDecimal parseAmount(String name, String amountText, boolean byteMode) {
    Matcher exponentMatcher = EXPONENT_PATTERN.matcher(amountText);
    if (exponentMatcher.find()) {
      try {
        if (Math.abs(Integer.parseInt(exponentMatcher.group(1))) > 1_000_000) {
          throw new BuiltinError(name + ": exponent too large");
        }
      } catch (NumberFormatException e) {
        throw new BuiltinError(name + ": exponent too large");
      }
    }

    try {
      return new BigDecimal(amountText);
    } catch (NumberFormatException e) {
      throw new BuiltinError(name + ": could not parse " + amountName(byteMode) + " to a number");
    }
  }

  private static RegoValue toRegoNumber(BigDecimal value) {
    BigDecimal normalized = value.stripTrailingZeros();
    if (normalized.scale() <= 0) {
      return new RegoBigInt(normalized.toBigIntegerExact());
    }
    return new RegoDecimal(normalized.doubleValue());
  }

  private static String amountName(boolean byteMode) {
    return byteMode ? "byte amount" : "amount";
  }
}
