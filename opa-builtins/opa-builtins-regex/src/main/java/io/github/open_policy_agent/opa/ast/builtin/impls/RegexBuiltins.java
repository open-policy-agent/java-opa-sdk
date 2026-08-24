package io.github.open_policy_agent.opa.ast.builtin.impls;

import dk.brics.automaton.Automaton;
import dk.brics.automaton.RegExp;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import io.github.open_policy_agent.opa.ast.builtin.BuiltinError;
import io.github.open_policy_agent.opa.ast.builtin.BuiltinProvider;
import io.github.open_policy_agent.opa.ast.builtin.OpaBuiltin;
import io.github.open_policy_agent.opa.ast.builtin.OpaType;
import io.github.open_policy_agent.opa.ast.builtin.impls.utils.GoRegexCompatibilityValidator;
import io.github.open_policy_agent.opa.ast.types.RegoArray;
import io.github.open_policy_agent.opa.ast.types.RegoBoolean;
import io.github.open_policy_agent.opa.ast.types.RegoInt32;
import io.github.open_policy_agent.opa.ast.types.RegoString;
import io.github.open_policy_agent.opa.ast.types.RegoValue;
import io.github.open_policy_agent.opa.rego.EvaluationContext;

import static io.github.open_policy_agent.opa.ast.builtin.impls.utils.ArgHelper.getArg;

public class RegexBuiltins implements BuiltinProvider {

  /**
   * Compiles `pattern`, reporting a syntax error as a {@link BuiltinError} rather than letting
   * Java's unchecked {@link PatternSyntaxException} escape. OPA treats an invalid pattern as a
   * builtin error, so the call is undefined by default and only aborts under strict-builtin-errors.
   */
  private static Pattern compile(String pattern) {
    try {
      return Pattern.compile(pattern);
    } catch (PatternSyntaxException e) {
      throw new BuiltinError("error parsing regexp: " + e.getMessage());
    }
  }

  @Override
  public Map<String, BiFunction<EvaluationContext, RegoValue[], RegoValue>> builtins() {
    RegexBuiltins instance = new RegexBuiltins();
    // @formatter:off
    return Map.of(
        "regex.match", instance::match,
        "regex.is_valid", instance::isValid,
        "regex.split", instance::split,
        "regex.find_n", instance::find,
        "regex.find_all_string_submatch_n", instance::findSubstringMatch,
        "regex.replace", instance::replace,
        "regex.template_match", instance::templateMatch,
        "regex.globs_match", instance::globsMatch);
    // @formatter:on
  }

  @OpaBuiltin(
      name = "regex.match",
      description = "Matches a string against a regular expression.",
      args = {
        @OpaType(name = "pattern", description = "regular expression"),
        @OpaType(name = "value", description = "value to match against `pattern`")
      },
      result = @OpaType(name = "result", description = "true if `value` matches `pattern`"))
  public RegoBoolean match(EvaluationContext ctx, RegoValue[] args) {

    String pattern = getArg(args, 0, RegoString.class).getValue();
    String value = getArg(args, 1, RegoString.class).getValue();

    // Go's regexp.MatchString searches for a match anywhere in the input, so an unanchored
    // pattern matches a substring. Matcher.matches() would instead require the whole input.
    return RegoBoolean.of(compile(pattern).matcher(value).find());
  }

  @OpaBuiltin(
      name = "regex.is_valid",
      description =
          "Checks if a string is a valid regular expression: the detailed syntax for patterns is"
              + " defined by https://github.com/google/re2/wiki/Syntax.",
      args = {@OpaType(name = "pattern", description = "regular expression")},
      result =
          @OpaType(
              name = "result",
              description = "true if `pattern` is a valid regular expression"))
  public RegoBoolean isValid(EvaluationContext ctx, RegoValue[] args) {
    RegoValue patternValue = args[0];

    if (!(patternValue instanceof RegoString)) {
      return RegoBoolean.FALSE;
    }
    return RegoBoolean.of(
        GoRegexCompatibilityValidator.isGoCompatible(((RegoString) patternValue).getValue()));
  }

  @OpaBuiltin(
      name = "regex.split",
      description = "Splits the input string by the occurrences of the given pattern.",
      args = {
        @OpaType(name = "pattern", description = "regular expression"),
        @OpaType(name = "value", description = "string to match")
      },
      result = @OpaType(name = "output", description = "the parts obtained by splitting `value`"))
  public RegoArray split(EvaluationContext ctx, RegoValue[] args) {

    String pattern = getArg(args, 0, RegoString.class).getValue();
    String value = getArg(args, 1, RegoString.class).getValue();

    RegoArray result = new RegoArray();
    for (String split : compile(pattern).split(value, -1)) {
      result.addValue(new RegoString(split));
    }
    return result;
  }

  @OpaBuiltin(
      name = "regex.find_n",
      description =
          "Returns the specified number of matches when matching the input against the pattern.",
      args = {
        @OpaType(name = "pattern", description = "regular expression"),
        @OpaType(name = "value", description = "string to match"),
        @OpaType(
            name = "number",
            description = "number of matches to return, if `-1`, returns all matches"),
      },
      result = @OpaType(name = "output", description = "collected matches"))
  public RegoArray find(EvaluationContext ctx, RegoValue[] args) {
    String pattern = getArg(args, 0, RegoString.class).getValue();
    String value = getArg(args, 1, RegoString.class).getValue();
    int number = getArg(args, 2, RegoInt32.class).getValue();
    if (number == -1) {
      number = Integer.MAX_VALUE;
    }

    RegoArray result = new RegoArray();
    Matcher matcher = compile(pattern).matcher(value);
    int count = 0;
    while (matcher.find() && count < number) {
      result.addValue(new RegoString(matcher.group()));
      count++;
    }
    return result;
  }

  @OpaBuiltin(
      name = "regex.find_all_string_submatch_n",
      description = "Returns all successive matches of the expression.",
      args = {
        @OpaType(name = "pattern", description = "regular expression"),
        @OpaType(name = "value", description = "string to match"),
        @OpaType(
            name = "number",
            description = "number of matches to return; `-1` means all matches"),
      },
      result = @OpaType(name = "output", description = "array of all matches"))
  public RegoArray findSubstringMatch(EvaluationContext ctx, RegoValue[] args) {
    String pattern = getArg(args, 0, RegoString.class).getValue();
    String value = getArg(args, 1, RegoString.class).getValue();
    int number = getArg(args, 2, RegoInt32.class).getValue();
    if (number == -1) {
      number = Integer.MAX_VALUE;
    }

    RegoArray result = new RegoArray();
    Matcher matcher = compile(pattern).matcher(value);
    int count = 0;
    while (matcher.find() && count < number) {
      RegoArray matchGroups = new RegoArray();
      for (int i = 0; i <= matcher.groupCount(); i++) {
        String group = matcher.group(i);
        matchGroups.addValue(new RegoString(group != null ? group : ""));
      }
      result.addValue(matchGroups);
      count++;
    }
    return result;
  }

  @OpaBuiltin(
      name = "regex.replace",
      description = "Find and replaces the text using the regular expression pattern.",
      args = {
        @OpaType(name = "s", description = "string being processed"),
        @OpaType(name = "pattern", description = "regex pattern to be applied"),
        @OpaType(name = "value", description = "regex value"),
      },
      result = @OpaType(name = "output", description = "string with replaced substrings"))
  public RegoString replace(EvaluationContext ctx, RegoValue[] args) {
    String s = getArg(args, 0, RegoString.class).getValue();
    String pattern = getArg(args, 1, RegoString.class).getValue();
    String value = getArg(args, 2, RegoString.class).getValue();
    return new RegoString(compile(pattern).matcher(s).replaceAll(value));
  }

  @OpaBuiltin(
      name = "regex.template_match",
      description = "Matches a string against a pattern, where there pattern may be glob-like",
      args = {
        @OpaType(
            name = "template",
            description = "template expression containing `0..n` regular expressions"),
        @OpaType(name = "value", description = "string to match"),
        @OpaType(
            name = "delimiter_start",
            description = "start delimiter of the regular expression in `template`"),
        @OpaType(
            name = "delimiter_end",
            description = "end delimiter of the regular expression in `template`"),
      },
      result = @OpaType(name = "result", description = "true if `value` matches the `template`"))
  public RegoBoolean templateMatch(EvaluationContext ctx, RegoValue[] args) {
    String template = getArg(args, 0, RegoString.class).getValue();
    String value = getArg(args, 1, RegoString.class).getValue();
    String delimiterStart = getArg(args, 2, RegoString.class).getValue();
    String delimiterEnd = getArg(args, 3, RegoString.class).getValue();

    // Build regex pattern from template
    StringBuilder patternBuilder = new StringBuilder();
    int pos = 0;

    while (pos < template.length()) {
      int startIdx = template.indexOf(delimiterStart, pos);
      if (startIdx == -1) {
        // No more delimiters, append rest as literal
        patternBuilder.append(Pattern.quote(template.substring(pos)));
        break;
      }

      // Append literal text before delimiter
      if (startIdx > pos) {
        patternBuilder.append(Pattern.quote(template.substring(pos, startIdx)));
      }

      // Find end delimiter
      int endIdx = template.indexOf(delimiterEnd, startIdx + delimiterStart.length());
      if (endIdx == -1) {
        // No matching end delimiter, treat rest as literal
        patternBuilder.append(Pattern.quote(template.substring(pos)));
        break;
      }

      // Extract and append regex pattern between delimiters
      String regexPart = template.substring(startIdx + delimiterStart.length(), endIdx);
      patternBuilder.append(regexPart);

      pos = endIdx + delimiterEnd.length();
    }

    // Match value against constructed pattern
    String pattern = patternBuilder.toString();
    return RegoBoolean.of(compile(pattern).matcher(value).matches());
  }

  @OpaBuiltin(
      name = "regex.globs_match",
      description =
          "Checks if the intersection of two glob-style regular expressions matches a non-empty set"
              + " of non-empty strings.\nThe set of regex symbols is limited for this builtin: only"
              + " `.`, `*`, `+`, `[`, `-`, `]` and `\\` are treated as special symbols.",
      args = {
        @OpaType(name = "glob1", description = "first glob-style regular expression"),
        @OpaType(name = "glob2", description = "second glob-style regular expression"),
      },
      result =
          @OpaType(
              name = "result",
              description =
                  "true if the intersection of `glob1` and `glob2` matches a non-empty set of"
                      + " non-empty strings"))
  public RegoBoolean globsMatch(EvaluationContext ctx, RegoValue[] args) {
    String glob1 = getArg(args, 0, RegoString.class).getValue();
    String glob2 = getArg(args, 1, RegoString.class).getValue();

    // Validate both patterns first
    validateGlobPattern(glob1);
    validateGlobPattern(glob2);

    try {
      // Create automata from the glob patterns
      RegExp r1 = new RegExp(glob1);
      Automaton a1 = r1.toAutomaton();

      RegExp r2 = new RegExp(glob2);
      Automaton a2 = r2.toAutomaton();

      // Compute the intersection
      Automaton intersection = a1.intersection(a2);

      // Check if the intersection is non-empty
      // Also ensure it doesn't accept only empty strings
      boolean nonEmpty = !intersection.isEmpty();
      if (nonEmpty) {
        // Check if it accepts at least one non-empty string
        // by checking if it accepts any string of length > 0
        Automaton emptyOnly = Automaton.makeString("");
        Automaton nonEmptyIntersection = intersection.minus(emptyOnly);
        nonEmpty = !nonEmptyIntersection.isEmpty();
      }

      return RegoBoolean.of(nonEmpty);
    } catch (IllegalArgumentException e) {
      // Shouldn't reach here if validation is correct, but handle just in case
      throw new BuiltinError("regex.globs_match: " + e.getMessage());
    }
  }

  /**
   * Validates a glob-style pattern according to OPA's rules. Only `.`, `*`, `+`, `[`, `-`, `]` and
   * `\` are treated as special symbols.
   */
  private void validateGlobPattern(String pattern) {
    int depth = 0;
    boolean inEscape = false;

    for (int i = 0; i < pattern.length(); i++) {
      char c = pattern.charAt(i);

      if (inEscape) {
        inEscape = false;
        continue;
      }

      if (c == '\\') {
        inEscape = true;
        continue;
      }

      if (c == '[') {
        depth++;
      } else if (c == ']') {
        if (depth == 0) {
          throw new BuiltinError(
              String.format(
                  "input:%s, pos:%d, set-close ']' with no preceding '[': the input provided is invalid",
                  pattern, i + 1));
        }
        depth--;
      }
    }

    if (depth > 0) {
      throw new BuiltinError(
          String.format(
              "input:%s, pos:%d, unclosed '[': the input provided is invalid",
              pattern, pattern.length()));
    }

    if (inEscape) {
      throw new BuiltinError(
          String.format(
              "input:%s, pos:%d, trailing escape: the input provided is invalid",
              pattern, pattern.length()));
    }
  }
}
