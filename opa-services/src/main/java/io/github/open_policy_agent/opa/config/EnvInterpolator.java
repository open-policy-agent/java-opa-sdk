package io.github.open_policy_agent.opa.config;

import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Replaces {@code ${VAR}} references in raw config text with environment-variable values, matching
 * Go-OPA's behaviour. Missing variables throw — silently substituting an empty string would mask
 * misconfiguration of credentials and TLS paths.
 *
 * <p>Escape with a leading backslash ({@code \${VAR}}) to keep a literal {@code ${VAR}} in the
 * config (the backslash is consumed).
 */
public final class EnvInterpolator {

  private static final Pattern PLACEHOLDER =
      Pattern.compile("(\\\\?)\\$\\{([A-Za-z_][A-Za-z0-9_]*)\\}");

  private static final Pattern LENIENT_PLACEHOLDER = Pattern.compile("\\$\\{([^}\\n\"]+)\\}");

  private EnvInterpolator() {}

  /**
   * Like {@link #interpolate(String)}, but leaves undefined variables as written and has no escape
   * syntax. Defined-but-empty variables substitute to the empty string.
   */
  public static String interpolateLenient(String input) {
    return interpolateLenient(input, System.getenv());
  }

  /** Lenient interpolation against an arbitrary lookup, useful for tests. */
  public static String interpolateLenient(String input, Map<String, String> env) {
    return interpolateLenient(input, env::get);
  }

  /** Lenient interpolation where {@code lookup} returns null for an undefined variable. */
  public static String interpolateLenient(String input, Function<String, String> lookup) {
    if (input == null || input.indexOf('$') < 0) {
      return input;
    }
    final Matcher m = LENIENT_PLACEHOLDER.matcher(input);
    final StringBuilder out = new StringBuilder(input.length());
    while (m.find()) {
      final String value = lookup.apply(m.group(1));
      m.appendReplacement(out, Matcher.quoteReplacement(value != null ? value : m.group()));
    }
    m.appendTail(out);
    return out.toString();
  }

  /** Interpolate against the process environment ({@link System#getenv()}). */
  public static String interpolate(String input) {
    return interpolate(input, System.getenv());
  }

  /** Interpolate against an arbitrary lookup, useful for tests. */
  public static String interpolate(String input, Map<String, String> env) {
    if (input == null || input.indexOf('$') < 0) {
      return input;
    }
    Matcher m = PLACEHOLDER.matcher(input);
    StringBuilder out = new StringBuilder(input.length());
    while (m.find()) {
      String escape = m.group(1);
      String varName = m.group(2);
      String replacement;
      if (!escape.isEmpty()) {
        // Escaped: drop the backslash, keep the literal placeholder.
        replacement = "${" + varName + "}";
      } else {
        String value = env.get(varName);
        if (value == null) {
          throw new ConfigurationException(
              "Environment variable '" + varName + "' referenced in config is not set");
        }
        replacement = value;
      }
      m.appendReplacement(out, Matcher.quoteReplacement(replacement));
    }
    m.appendTail(out);
    return out.toString();
  }
}
