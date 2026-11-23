package io.github.open_policy_agent.opa.ast.builtin.impls;

import java.util.Map;

/**
 * Port of Go's {@code time.ParseDuration}, used for the {@code timeout} parameter of {@code
 * http.send}. Error messages follow Go's wording.
 */
final class GoDuration {

  private static final Map<String, Long> UNITS =
      Map.of(
          "ns", 1L,
          "us", 1_000L,
          "µs", 1_000L,
          "μs", 1_000L,
          "ms", 1_000_000L,
          "s", 1_000_000_000L,
          "m", 60L * 1_000_000_000L,
          "h", 3600L * 1_000_000_000L);

  private GoDuration() {}

  /**
   * Parses a duration such as {@code "300ms"}, {@code "-1.5h"} or {@code "2h45m"}.
   *
   * @return the duration in nanoseconds
   * @throws IllegalArgumentException with Go's message if the string is not a valid duration
   */
  static long parseNanos(String input) {
    String s = input;
    boolean negative = false;
    if (!s.isEmpty() && (s.charAt(0) == '-' || s.charAt(0) == '+')) {
      negative = s.charAt(0) == '-';
      s = s.substring(1);
    }
    if ("0".equals(s)) {
      return 0;
    }
    if (s.isEmpty()) {
      throw invalid(input);
    }

    long total = 0;
    int pos = 0;
    while (pos < s.length()) {
      char c = s.charAt(pos);
      if (c != '.' && !isDigit(c)) {
        throw invalid(input);
      }

      int start = pos;
      long whole = 0;
      while (pos < s.length() && isDigit(s.charAt(pos))) {
        whole = addDigit(whole, s.charAt(pos), input);
        pos++;
      }
      boolean hasWhole = pos != start;

      long fraction = 0;
      double scale = 1;
      boolean hasFraction = false;
      if (pos < s.length() && s.charAt(pos) == '.') {
        pos++;
        int fractionStart = pos;
        boolean overflow = false;
        while (pos < s.length() && isDigit(s.charAt(pos))) {
          if (!overflow) {
            long next = fraction * 10 + (s.charAt(pos) - '0');
            if (next < 0 || fraction > (Long.MAX_VALUE - 9) / 10) {
              // Too many digits to keep; they no longer affect the result.
              overflow = true;
            } else {
              fraction = next;
              scale *= 10;
            }
          }
          pos++;
        }
        hasFraction = pos != fractionStart;
      }
      if (!hasWhole && !hasFraction) {
        throw invalid(input);
      }

      int unitStart = pos;
      while (pos < s.length() && s.charAt(pos) != '.' && !isDigit(s.charAt(pos))) {
        pos++;
      }
      if (pos == unitStart) {
        throw new IllegalArgumentException("time: missing unit in duration " + quote(input));
      }
      String unitName = s.substring(unitStart, pos);
      Long unit = UNITS.get(unitName);
      if (unit == null) {
        throw new IllegalArgumentException(
            "time: unknown unit " + quote(unitName) + " in duration " + quote(input));
      }

      long value;
      try {
        value = Math.multiplyExact(whole, unit);
        if (fraction > 0) {
          value = Math.addExact(value, (long) ((double) fraction * ((double) unit / scale)));
        }
        total = Math.addExact(total, value);
      } catch (ArithmeticException e) {
        throw invalid(input);
      }
    }
    return negative ? -total : total;
  }

  private static long addDigit(long value, char digit, String input) {
    if (value > (Long.MAX_VALUE - 9) / 10) {
      throw invalid(input);
    }
    return value * 10 + (digit - '0');
  }

  private static boolean isDigit(char c) {
    return c >= '0' && c <= '9';
  }

  private static IllegalArgumentException invalid(String input) {
    return new IllegalArgumentException("time: invalid duration " + quote(input));
  }

  private static String quote(String s) {
    return "\"" + s + "\"";
  }
}
