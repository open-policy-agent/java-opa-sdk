package io.github.open_policy_agent.opa.ast.types;

import java.math.BigInteger;
import java.util.Map;

import io.github.open_policy_agent.opa.ast.builtin.BuiltinError;

public final class TypeUtils {
  // Up to this many characters, sign included, an integer string always fits a long.
  private static final int MAX_LONG_CHARS = 18;

  private enum Form {
    INTEGER,
    DECIMAL,
    OTHER
  }

  static Map<Class<?>, String> TYPES = Map.of(
          RegoArray.class, "array",
          RegoBigInt.class, "number",
          RegoBoolean.class, "boolean",
          RegoDecimal.class, "number",
          RegoInt32.class, "number",
          RegoNull.class, "null",
          RegoObject.class, "object",
          RegoSet.class, "set",
          RegoString.class, "string"
  );

  public static String getRegoTypeName(Class<?> clazz) {
    String name = TYPES.get(clazz);
    if (name == null) {
      return clazz.getSimpleName();
    }
    return name;
  }

  /**
   * Checks if a double represents a whole number that can be safely cast to a long.
   *
   * @param d the double value to check
   * @return true if d is a whole number within the range of a long
   */
  public static boolean isWholeNumberInLongRange(double d) {
    return d == Math.floor(d)
        && !Double.isInfinite(d)
        && !Double.isNaN(d)
        && d >= Long.MIN_VALUE
        && d <= Long.MAX_VALUE;
  }

  /**
   * Converts a long value to the appropriate Rego integer type. Returns RegoInt32 for values that
   * fit in an int, otherwise RegoBigInt.
   *
   * @param longValue the long value to convert
   * @return RegoInt32 if the value fits in an int, otherwise RegoBigInt
   */
  public static RegoNumber longToRegoInteger(long longValue) {
    if (longValue >= Integer.MIN_VALUE && longValue <= Integer.MAX_VALUE) {
      return RegoInt32.of((int) longValue);
    } else {
      return new RegoBigInt(longValue);
    }
  }

  // Whole numbers written with a fraction or exponent compare as integers, but remember their
  // float text so sprintf formats them like OPA does. Fresh instances keep the RegoInt32 cache clean.
  private static RegoNumber floatTextInteger(long longValue) {
    if (longValue >= Integer.MIN_VALUE && longValue <= Integer.MAX_VALUE) {
      RegoInt32 i = new RegoInt32(String.valueOf(longValue));
      i.setFloatText(true);
      return i;
    }
    RegoBigInt b = new RegoBigInt(longValue);
    b.setFloatText(true);
    return b;
  }

  // The common forms are told apart by their characters, so no exception is thrown and swallowed.
  // Anything else, such as non-ASCII digits or padding, takes the general path.
  public static RegoValue parseStringToNumber(String number) {
      return switch (classify(number)) {
          case INTEGER -> number.length() <= MAX_LONG_CHARS ? parseInteger(number) : parseLongInteger(number);
          case DECIMAL -> parseDecimal(number);
          default -> parseGeneral(number);
      };
  }

  private static Form classify(String number) {
    final int length = number.length();
    if (length == 0) {
      return Form.OTHER;
    }
    int start = 0;
    final char first = number.charAt(0);
    if (first == '-' || first == '+') {
      start = 1;
      if (length == 1) {
        return Form.OTHER;
      }
    }
    boolean digitsOnly = true;
    for (int i = start; i < length; i++) {
      final char c = number.charAt(i);
      if (c >= '0' && c <= '9') {
        continue;
      }
      if (c == '.' || c == 'e' || c == 'E' || c == '+' || c == '-') {
        digitsOnly = false;
      } else {
        return Form.OTHER;
      }
    }
    return digitsOnly ? Form.INTEGER : Form.DECIMAL;
  }

  private static RegoValue parseInteger(String number) {
    try {
      return longToRegoInteger(Long.parseLong(number));
    } catch (NumberFormatException e) {
      return parseGeneral(number); // not reachable for a classified integer; kept as a safe fallback
    }
  }

  private static RegoValue parseLongInteger(String number) {
    try {
      final BigInteger value = new BigInteger(number);
      return value.bitLength() < Integer.SIZE ? RegoInt32.of(value.intValue()) : new RegoBigInt(value);
    } catch (NumberFormatException e) {
      return parseGeneral(number); // not reachable for a classified integer; kept as a safe fallback
    }
  }

  private static RegoValue parseDecimal(String number) {
    final double d;
    try {
      d = Double.parseDouble(number);
    } catch (NumberFormatException e) {
      throw new BuiltinError("invalid syntax");
    }
    return fromDouble(d, number);
  }

  private static RegoValue parseGeneral(String number) {
    // Try parsing as int first
    try {
      return RegoInt32.of(Integer.parseInt(number));
    } catch (NumberFormatException e) {
      // Try parsing as long/BigInteger
      try {
        return new RegoBigInt(new BigInteger(number));
      } catch (NumberFormatException e1) {
        // Parse as double
        return parseDecimal(number);
      }
    }
  }

  private static RegoValue fromDouble(double d, String number) {
    // Go parses numbers like "3.0" and "-42.0" as integers
    // Check if it's a whole number and convert to integer type
    // BUT only if it's within the range of a long (to avoid overflow)
    if (isWholeNumberInLongRange(d)) {
      return floatTextInteger((long) d);
    }

    // If the value is infinite, preserve the original string
    if (Double.isInfinite(d)) {
      return new RegoDecimal(d, number);
    }
    return new RegoDecimal(d);
  }
}
