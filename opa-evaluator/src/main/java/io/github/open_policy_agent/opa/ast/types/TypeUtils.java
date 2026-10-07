package io.github.open_policy_agent.opa.ast.types;

import java.math.BigInteger;
import java.util.Map;

import io.github.open_policy_agent.opa.ast.builtin.BuiltinError;

public final class TypeUtils {
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

  public static RegoValue parseStringToNumber(String number) {
    // Try parsing as int first
    try {
      return RegoInt32.of(Integer.parseInt(number));
    } catch (NumberFormatException e) {
      // Try parsing as long/BigInteger
      try {
        return new RegoBigInt(new BigInteger(number));
      } catch (NumberFormatException e1) {
        // Parse as double
        try {
          double d = Double.parseDouble(number);

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
        } catch (NumberFormatException e2) {
          throw new BuiltinError("invalid syntax");
        }
      }
    }
  }
}
