package io.github.open_policy_agent.opa.ast.builtin.impls.utils;

/**
 * Converts Go time layout strings to Java DateTimeFormatter patterns.
 *
 * <p>Go uses a reference time approach for time formatting: "Mon Jan 2 15:04:05 MST 2006" This
 * class converts Go's layout strings to equivalent Java DateTimeFormatter patterns.
 *
 * <p>Supported Go layouts:
 *
 * <ul>
 *   <li>RFC constants: RFC822, RFC822Z, RFC850, RFC1123, RFC1123Z, RFC3339, RFC3339Nano
 *   <li>Custom layouts using Go's reference time components
 * </ul>
 *
 * <p>Example:
 *
 * <pre>{@code
 * String[] javaPatterns = GoTimeLayoutConverter.convert("2006-01-02 15:04:05");
 * // Returns: ["yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd HH:mm:ss.SSSSSSSSS"]
 * }</pre>
 */
public class GoTimeLayoutConverter {

  /**
   * Converts a Go time layout string to one or more Java DateTimeFormatter patterns.
   *
   * <p>Returns multiple patterns when fractional seconds may or may not be present. This handles
   * Go's forgiving parsing behavior for nanoseconds.
   *
   * @param goLayout the Go time layout string
   * @return array of Java DateTimeFormatter pattern strings
   */
  public static String[] convert(String goLayout) {
    // Handle RFC constants
    switch (goLayout) {
      case "RFC822":
        return new String[] {"dd MMM yy HH:mm z"};
      case "RFC822Z":
        return new String[] {"dd MMM yy HH:mm Z"};
      case "RFC850":
        return new String[] {"EEEE, dd-MMM-yy HH:mm:ss z", "EEEE, dd-MMM-yy HH:mm:ss.SSSSSSSSS z"};
      case "RFC1123":
        return new String[] {"E, dd MMM yy HH:mm:ss z", "E, dd MMM yy HH:mm:ss.SSSSSSSSS z"};
      case "RFC1123Z":
        return new String[] {"E, dd MMM yyyy HH:mm:ss Z", "E, dd MMM yyyy HH:mm:ss.SSSSSSSSS Z"};
      case "RFC3339":
        return new String[] {"yyyy-MM-dd'T'HH:mm:ssXXX", "yyyy-MM-dd'T'HH:mm:ss.SSSSSSSSSXXX"};
      case "RFC3339Nano":
        return new String[] {"yyyy-MM-dd'T'HH:mm:ss.SSSSSSSSSXXX", "yyyy-MM-dd'T'HH:mm:ssXXX"};
      default:
        break;
    }

    // Go's reference time: Mon Jan 2 15:04:05 MST 2006
    // Convert Go time layout to Java DateTimeFormatter pattern
    String javaPattern = goLayout;

    // Year
    javaPattern = javaPattern.replace("2006", "yyyy");
    javaPattern = javaPattern.replace("06", "yy");

    // Month - handle longer patterns first
    javaPattern = javaPattern.replace("January", "MMMM");
    javaPattern = javaPattern.replace("Jan", "MMM");
    javaPattern = javaPattern.replace("01", "MM");
    javaPattern = replaceStandalone(javaPattern, "1", "M");

    // Day - handle longer patterns first
    javaPattern = javaPattern.replace("_2", " d");
    javaPattern = javaPattern.replace("02", "dd");
    javaPattern = replaceStandalone(javaPattern, "2", "d");

    // Weekday
    javaPattern = javaPattern.replace("Monday", "EEEE");
    javaPattern = javaPattern.replace("Mon", "EEE");

    // Hour - handle longer patterns first
    javaPattern = javaPattern.replace("15", "HH"); // 24-hour
    javaPattern = javaPattern.replace("03", "hh"); // 12-hour
    javaPattern = replaceStandalone(javaPattern, "3", "h");

    // Minute
    javaPattern = javaPattern.replace("04", "mm");
    javaPattern = replaceStandalone(javaPattern, "4", "m");

    // Second
    javaPattern = javaPattern.replace("05", "ss");
    javaPattern = replaceStandalone(javaPattern, "5", "s");

    // Nanosecond/Microsecond/Millisecond
    javaPattern = javaPattern.replace(".000000000", ".SSSSSSSSS");
    javaPattern = javaPattern.replace(".000000", ".SSSSSS");
    javaPattern = javaPattern.replace(".000", ".SSS");
    javaPattern = javaPattern.replace(".999999999", ".SSSSSSSSS");
    javaPattern = javaPattern.replace(".999999", ".SSSSSS");
    javaPattern = javaPattern.replace(".999", ".SSS");

    // AM/PM
    javaPattern = javaPattern.replace("PM", "a");
    javaPattern = javaPattern.replace("pm", "a");

    // Timezone - handle Go's timezone reference time -07:00 (MST)
    if (javaPattern.contains("-07:00")) {
      javaPattern = javaPattern.replace("-07:00", "XXX");
    } else if (javaPattern.contains("-0700")) {
      javaPattern = javaPattern.replace("-0700", "XX");
    } else if (javaPattern.contains("-07")) {
      javaPattern = javaPattern.replace("-07", "X");
    } else if (javaPattern.contains("Z07:00")) {
      javaPattern = javaPattern.replace("Z07:00", "XXX");
    } else if (javaPattern.contains("Z0700")) {
      javaPattern = javaPattern.replace("Z0700", "XX");
    } else if (javaPattern.contains("Z07")) {
      javaPattern = javaPattern.replace("Z07", "X");
    } else if (javaPattern.contains("MST")) {
      javaPattern = javaPattern.replace("MST", "zzz");
    }

    // Replace special characters
    javaPattern = javaPattern.replace("'", "''");
    javaPattern = javaPattern.replace("/", "'/'");

    // Only escape T if it appears as a literal (common in ISO formats)
    if (javaPattern.contains("T") && !javaPattern.contains("'T'")) {
      javaPattern = javaPattern.replace("T", "'T'");
    }

    // Return variants with and without nanoseconds for flexibility
    // Go's time parsing is forgiving with fractional seconds
    String[] patterns;
    if (javaPattern.contains("ss.SSSSSSSSS")) {
      patterns = new String[] {javaPattern, javaPattern.replace("ss.SSSSSSSSS", "ss")};
    } else if (javaPattern.contains("ss")) {
      patterns = new String[] {javaPattern, javaPattern.replace("ss", "ss.SSSSSSSSS")};
    } else {
      patterns = new String[] {javaPattern};
    }

    return patterns;
  }

  /**
   * Replaces a target string only when it appears as a standalone token (not part of a larger
   * number).
   *
   * @param input the input string
   * @param target the target to replace
   * @param replacement the replacement string
   * @return the input with standalone occurrences replaced
   */
  private static String replaceStandalone(String input, String target, String replacement) {
    // Replace target only when it's a standalone number (not part of a larger number)
    return input.replaceAll("(?<!\\d)" + target + "(?!\\d)", replacement);
  }
}
