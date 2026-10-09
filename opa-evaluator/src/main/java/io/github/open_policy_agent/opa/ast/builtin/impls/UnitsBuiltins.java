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
import java.math.RoundingMode;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiFunction;

public class UnitsBuiltins {
  private static final int MAX_EXPONENT_DIGITS = 6;

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
          Map.entry("Ki", BigDecimal.valueOf(1_024L)),
          Map.entry("mi", BigDecimal.valueOf(1_048_576L)),
          Map.entry("Mi", BigDecimal.valueOf(1_048_576L)),
          Map.entry("gi", BigDecimal.valueOf(1_073_741_824L)),
          Map.entry("Gi", BigDecimal.valueOf(1_073_741_824L)),
          Map.entry("ti", BigDecimal.valueOf(1_099_511_627_776L)),
          Map.entry("Ti", BigDecimal.valueOf(1_099_511_627_776L)),
          Map.entry("pi", BigDecimal.valueOf(1_125_899_906_842_624L)),
          Map.entry("Pi", BigDecimal.valueOf(1_125_899_906_842_624L)),
          Map.entry("ei", new BigDecimal("1152921504606846976")),
          Map.entry("Ei", new BigDecimal("1152921504606846976")));

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
    String quantity = input.replace("\"", "");
    if (quantity.contains(" ")) {
      throw new BuiltinError(name + ": spaces not allowed in resource strings");
    }

    ParsedQuantity parsed = extractQuantity(quantity, name);
    if (parsed.amount().isEmpty()) {
      throw new BuiltinError(name + ": no " + amountName(byteMode) + " provided");
    }

    String amountText = parsed.amount();
    String unitText = normalizeUnit(parsed.unit(), byteMode);
    BigDecimal unit = units.get(unitText);
    if (unit == null) {
      String unitName = byteMode ? "byte unit" : "unit";
      String unrecognizedUnit = byteMode
          ? parsed.unit().toLowerCase(Locale.ROOT)
          : normalizeUnit(parsed.unit(), false);
      throw new BuiltinError(name + ": " + unitName + " " + unrecognizedUnit + " not recognized");
    }

    BigDecimal amount = parseAmount(name, amountText, byteMode);
    return toRegoNumber(amount.multiply(unit), byteMode);
  }

  private static String normalizeUnit(String unit, boolean byteMode) {
    String normalized = byteMode ? unit.toLowerCase(Locale.ROOT) : unit;
    if (byteMode && normalized.length() > 1 && normalized.endsWith("b")) {
      normalized = normalized.substring(0, normalized.length() - 1);
    }
    if (!byteMode && normalized.length() > 1) {
      return normalized.substring(0, 1) + normalized.substring(1).toLowerCase(Locale.ROOT);
    }
    return normalized;
  }

  private static ParsedQuantity extractQuantity(String quantity, String name) {
    int firstNonNumberIndex = -1;
    for (int index = 0; index < quantity.length(); index++) {
      char current = quantity.charAt(index);
      boolean numeric = Character.isDigit(current) || current == '.';
      if (!numeric && current != 'e' && current != 'E' && current != '+' && current != '-') {
        firstNonNumberIndex = index;
        break;
      }
      if (current == 'e' || current == 'E') {
        if (index == quantity.length() - 1) {
          firstNonNumberIndex = index;
          break;
        }
        char next = quantity.charAt(index + 1);
        if (!Character.isDigit(next) && next != '+' && next != '-') {
          firstNonNumberIndex = index;
          break;
        }
        if (next == '+' || next == '-') {
          index++;
        }
        int exponentStart = index + 1;
        int exponentEnd = exponentStart;
        while (exponentEnd < quantity.length()
            && Character.isDigit(quantity.charAt(exponentEnd))) {
          exponentEnd++;
        }
        if (exponentEnd - exponentStart > MAX_EXPONENT_DIGITS) {
          throw new BuiltinError(name + ": exponent too large");
        }
      }
    }

    if (firstNonNumberIndex < 0) {
      return new ParsedQuantity(quantity, "");
    }
    if (firstNonNumberIndex == 0) {
      return new ParsedQuantity("", quantity);
    }
    return new ParsedQuantity(
        quantity.substring(0, firstNonNumberIndex), quantity.substring(firstNonNumberIndex));
  }

  private static BigDecimal parseAmount(String name, String amountText, boolean byteMode) {
    try {
      return new BigDecimal(amountText);
    } catch (NumberFormatException e) {
      throw new BuiltinError(name + ": could not parse " + amountName(byteMode) + " to a number");
    }
  }

  private static RegoValue toRegoNumber(BigDecimal value, boolean byteMode) {
    if (byteMode) {
      return new RegoBigInt(value.toBigInteger());
    }
    BigDecimal normalized = value.setScale(10, RoundingMode.HALF_UP).stripTrailingZeros();
    if (normalized.scale() <= 0) {
      return new RegoBigInt(normalized.toBigIntegerExact());
    }
    return new RegoDecimal(normalized.doubleValue(), normalized.toPlainString());
  }

  private static String amountName(boolean byteMode) {
    return byteMode ? "byte amount" : "amount";
  }

  private record ParsedQuantity(String amount, String unit) {}
}
