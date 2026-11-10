package io.github.open_policy_agent.opa.ast.builtin.impls;

import io.github.open_policy_agent.opa.ast.builtin.BuiltinError;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Duration parsing as in OPA's {@code parseExtendedDuration}: Go's {@code time.ParseDuration}, plus
 * the {@code d}, {@code w} and {@code y} units, which OPA rewrites to hours first.
 */
final class GoDuration {

  private static final BigInteger LIMIT = BigInteger.ONE.shiftLeft(63);
  private static final BigInteger TEN = BigInteger.TEN;

  private static final Map<String, BigInteger> UNITS =
      Map.of(
          "ns", BigInteger.valueOf(1L),
          "us", BigInteger.valueOf(1_000L),
          "µs", BigInteger.valueOf(1_000L),
          "μs", BigInteger.valueOf(1_000L),
          "ms", BigInteger.valueOf(1_000_000L),
          "s", BigInteger.valueOf(1_000_000_000L),
          "m", BigInteger.valueOf(60_000_000_000L),
          "h", BigInteger.valueOf(3_600_000_000_000L));

  private static final Map<String, Integer> EXTENDED_HOURS = Map.of("d", 24, "w", 7 * 24, "y", 365 * 24);

  // OPA's grammar allows µs (U+00B5) but not μs (U+03BC) when d, w or y is present.
  private static final Pattern SEGMENT = Pattern.compile("([0-9.]+)(ms|us|µs|ns|[a-z])");

  private GoDuration() {}

  static long parseNanos(String s) {
    if (s.isEmpty()) {
      throw invalid(s);
    }
    if (s.indexOf('d') < 0 && s.indexOf('w') < 0 && s.indexOf('y') < 0) {
      return parseGo(s);
    }
    return parseGo(rewriteExtended(s));
  }

  // The grammar OPA's parser accepts, with d, w and y replaced by their hours.
  private static String rewriteExtended(String s) {
    final StringBuilder rewritten = new StringBuilder();
    int pos = 0;
    if (s.charAt(0) == '-' || s.charAt(0) == '+') {
      rewritten.append(s.charAt(0));
      pos = 1;
    }
    final Matcher segment = SEGMENT.matcher(s);
    while (pos < s.length()) {
      segment.region(pos, s.length());
      if (!segment.lookingAt()) {
        throw invalid(s);
      }
      final String digits = segment.group(1);
      final String unit = segment.group(2);
      final Integer hours = EXTENDED_HOURS.get(unit);
      rewritten.append(hours == null ? digits + unit : hoursText(digits, hours, s));
      pos = segment.end();
    }
    return rewritten.toString();
  }

  private static String hoursText(String digits, int perUnit, String original) {
    final double hours;
    try {
      hours = Double.parseDouble(digits) * perUnit;
    } catch (NumberFormatException e) {
      throw invalid(original);
    }
    if (Double.isInfinite(hours)) {
      throw invalid(original);
    }
    return new BigDecimal(Double.toString(hours)).toPlainString() + "h";
  }

  // Go's time.ParseDuration, with BigInteger standing in for uint64.
  private static long parseGo(String s) {
    final String original = s;
    boolean negative = false;
    if (s.charAt(0) == '-' || s.charAt(0) == '+') {
      negative = s.charAt(0) == '-';
      s = s.substring(1);
    }
    if (s.equals("0")) {
      return 0;
    }
    if (s.isEmpty()) {
      throw invalid(original);
    }
    BigInteger total = BigInteger.ZERO;
    while (!s.isEmpty()) {
      if (!(s.charAt(0) == '.' || isDigit(s.charAt(0)))) {
        throw invalid(original);
      }
      int i = 0;
      BigInteger whole = BigInteger.ZERO;
      while (i < s.length() && isDigit(s.charAt(i))) {
        if (whole.compareTo(LIMIT.divide(TEN)) > 0) {
          throw invalid(original);
        }
        whole = whole.multiply(TEN).add(BigInteger.valueOf(s.charAt(i) - '0'));
        if (whole.compareTo(LIMIT) > 0) {
          throw invalid(original);
        }
        i++;
      }
      final boolean hasWhole = i > 0;
      boolean hasFraction = false;
      BigInteger fraction = BigInteger.ZERO;
      double scale = 1;
      if (i < s.length() && s.charAt(i) == '.') {
        i++;
        final int fractionStart = i;
        boolean overflow = false;
        while (i < s.length() && isDigit(s.charAt(i))) {
          if (!overflow) {
            if (fraction.compareTo(LIMIT.subtract(BigInteger.ONE).divide(TEN)) > 0) {
              overflow = true;
            } else {
              final BigInteger next = fraction.multiply(TEN).add(BigInteger.valueOf(s.charAt(i) - '0'));
              if (next.compareTo(LIMIT) > 0) {
                overflow = true;
              } else {
                fraction = next;
                scale *= 10;
              }
            }
          }
          i++;
        }
        hasFraction = i > fractionStart;
      }
      if (!hasWhole && !hasFraction) {
        throw invalid(original);
      }
      final int unitStart = i;
      while (i < s.length() && s.charAt(i) != '.' && !isDigit(s.charAt(i))) {
        i++;
      }
      if (i == unitStart) {
        throw invalid(original);
      }
      final BigInteger unit = UNITS.get(s.substring(unitStart, i));
      if (unit == null) {
        throw invalid(original);
      }
      s = s.substring(i);
      if (whole.compareTo(LIMIT.divide(unit)) > 0) {
        throw invalid(original);
      }
      whole = whole.multiply(unit);
      if (fraction.signum() > 0) {
        whole = whole.add(BigInteger.valueOf((long) (fraction.doubleValue() * (unit.doubleValue() / scale))));
        if (whole.compareTo(LIMIT) > 0) {
          throw invalid(original);
        }
      }
      total = total.add(whole);
      if (total.compareTo(LIMIT) > 0) {
        throw invalid(original);
      }
    }
    if (negative) {
      return total.negate().longValueExact();
    }
    if (total.compareTo(LIMIT.subtract(BigInteger.ONE)) > 0) {
      throw invalid(original);
    }
    return total.longValueExact();
  }

  private static boolean isDigit(char c) {
    return c >= '0' && c <= '9';
  }

  private static BuiltinError invalid(String s) {
    return new BuiltinError("time: invalid duration \"" + s + "\"");
  }
}
