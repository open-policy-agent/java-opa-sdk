package io.github.open_policy_agent.opa.ast.builtin.impls;

import java.time.LocalDate;
import java.time.YearMonth;

/**
 * A direct parser for the usual RFC3339 shape, {@code yyyy-MM-ddTHH:mm:ss[.f{1,9}](Z|+hh:mm|-hh:mm)}.
 * It answers only when the input is exactly that shape and in range; for anything else it answers
 * {@code null} so the caller falls back to the formatter, which then decides the value or the error.
 */
final class Rfc3339 {

  private static final long NANOS_PER_SECOND = 1_000_000_000L;
  private static final int MIN_LENGTH = "2006-01-02T15:04:05Z".length();

  private Rfc3339() {}

  /** The epoch nanoseconds of {@code text}, or {@code null} when the formatter has to decide. */
  static Long epochNanos(String text) {
    final int length = text.length();
    if (length < MIN_LENGTH
        || text.charAt(4) != '-'
        || text.charAt(7) != '-'
        || text.charAt(10) != 'T'
        || text.charAt(13) != ':'
        || text.charAt(16) != ':') {
      return null;
    }
    final int year = digits(text, 0, 4);
    final int month = digits(text, 5, 2);
    final int day = digits(text, 8, 2);
    final int hour = digits(text, 11, 2);
    final int minute = digits(text, 14, 2);
    final int second = digits(text, 17, 2);
    if (year < 1 || month < 1 || month > 12 || day < 1 || hour < 0 || hour > 23 || minute < 0 || minute > 59 || second < 0 || second > 59) {
      return null;
    }
    if (day > YearMonth.of(year, month).lengthOfMonth()) {
      return null;
    }

    int index = 19;
    long nanos = 0;
    if (text.charAt(index) == '.') {
      index++;
      int count = 0;
      while (index < length && count < 9) {
        final int digit = text.charAt(index) - '0';
        if (digit < 0 || digit > 9) {
          break;
        }
        nanos = nanos * 10 + digit;
        count++;
        index++;
      }
      if (count == 0) {
        return null;
      }
      for (int pad = count; pad < 9; pad++) {
        nanos *= 10;
      }
    }

    final int offsetSeconds;
    final char zone = index < length ? text.charAt(index) : ' ';
    if (zone == 'Z' && index == length - 1) {
      offsetSeconds = 0;
    } else if ((zone == '+' || zone == '-') && index == length - 6 && text.charAt(index + 3) == ':') {
      final int offsetHour = digits(text, index + 1, 2);
      final int offsetMinute = digits(text, index + 4, 2);
      // java.time caps offsets at +-18:00, so hours stop at 17; "-00:00" is left to the formatter.
      if (offsetHour < 0 || offsetHour > 17 || offsetMinute < 0 || offsetMinute > 59 || (zone == '-' && offsetHour == 0 && offsetMinute == 0)) {
        return null;
      }
      final int magnitude = offsetHour * 3600 + offsetMinute * 60;
      offsetSeconds = zone == '-' ? -magnitude : magnitude;
    } else {
      return null;
    }

    final long seconds = LocalDate.of(year, month, day).toEpochDay() * 86_400L + hour * 3600L + minute * 60L + second - offsetSeconds;
    try {
      return Math.addExact(Math.multiplyExact(seconds, NANOS_PER_SECOND), nanos);
    } catch (ArithmeticException e) {
      return null;
    }
  }

  /** The value of {@code count} ASCII digits at {@code from}, or -1 when one of them is not. */
  private static int digits(String text, int from, int count) {
    int value = 0;
    for (int i = from; i < from + count; i++) {
      final int digit = text.charAt(i) - '0';
      if (digit < 0 || digit > 9) {
        return -1;
      }
      value = value * 10 + digit;
    }
    return value;
  }
}
