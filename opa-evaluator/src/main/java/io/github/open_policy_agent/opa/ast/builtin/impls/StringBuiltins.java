package io.github.open_policy_agent.opa.ast.builtin.impls;

import static io.github.open_policy_agent.opa.ast.builtin.impls.utils.ArgHelper.assertArgType;
import static io.github.open_policy_agent.opa.ast.builtin.impls.utils.ArgHelper.getArg;

import io.github.open_policy_agent.opa.ast.builtin.BuiltinError;
import io.github.open_policy_agent.opa.ast.builtin.OpaBuiltin;
import io.github.open_policy_agent.opa.ast.builtin.OpaDynamic;
import io.github.open_policy_agent.opa.ast.builtin.OpaType;
import io.github.open_policy_agent.opa.ast.builtin.impls.utils.SprintfUtil;
import io.github.open_policy_agent.opa.ast.types.RegoArray;
import io.github.open_policy_agent.opa.ast.types.RegoBigInt;
import io.github.open_policy_agent.opa.ast.types.RegoBoolean;
import io.github.open_policy_agent.opa.ast.types.RegoCollection;
import io.github.open_policy_agent.opa.ast.types.RegoDecimal;
import io.github.open_policy_agent.opa.ast.types.RegoInt32;
import io.github.open_policy_agent.opa.ast.types.RegoObject;
import io.github.open_policy_agent.opa.ast.types.RegoSet;
import io.github.open_policy_agent.opa.ast.types.RegoString;
import io.github.open_policy_agent.opa.ast.types.RegoValue;
import io.github.open_policy_agent.opa.rego.EvaluationContext;
import io.github.open_policy_agent.opa.rego.TypeError;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class StringBuiltins {

  public static Map<String, BiFunction<EvaluationContext, RegoValue[], RegoValue>> builtins() {
    StringBuiltins instance = new StringBuiltins();
    return Map.ofEntries(
            Map.entry("contains", instance::contains),
            Map.entry("indexof", instance::indexOf),
            Map.entry("trim", instance::trim),
            Map.entry("trim_left", instance::trimLeft),
            Map.entry("trim_right", instance::trimRight),
            Map.entry("upper", instance::upper),
            Map.entry("substring", instance::substring),
            Map.entry("lower", instance::lower),
            Map.entry("concat", instance::concat),
            Map.entry("sprintf", instance::sprintf),
            Map.entry("split", instance::split),
            Map.entry("replace", instance::replace),
            Map.entry("startswith", instance::startsWith),
            Map.entry("endswith", instance::endsWith),
            Map.entry("format_int", instance::formatInt),
            Map.entry("strings.reverse", instance::reverse),
            Map.entry("strings.count", instance::count),
            Map.entry("indexof_n", instance::indexOfN),
            Map.entry("strings.any_suffix_match", instance::anySuffixMatch),
            Map.entry("strings.any_prefix_match", instance::anyPrefixMatch),
            Map.entry("strings.replace_n", instance::replaceN),
            Map.entry("trim_prefix", instance::trimPrefix),
            Map.entry("trim_space", instance::trimSpace),
            Map.entry("trim_suffix", instance::trimSuffix));
  }

  @OpaBuiltin(
      name = "trim_space",
      description = "Returns `value` with all leading or trailing spaces removed.",
      categories = {"strings"},
      args = {@OpaType(type = "string", name = "value", description = "string to trim")},
      result =
          @OpaType(
              type = "string",
              name = "output",
              description = "string leading and trailing white space cut off"))
  public RegoString trimSpace(EvaluationContext ctx, RegoValue[] args) {
    String value = getArg(args, 0, RegoString.class).getValue();

    return new RegoString(value.trim());
  }

  @OpaBuiltin(
      name = "trim_suffix",
      description = "Returns `value` with all trailing occurrences of `prefix` removed.",
      categories = {"strings"},
      args = {
        @OpaType(type = "string", name = "value", description = "string to trim"),
        @OpaType(type = "string", name = "suffix", description = "suffix to cut off")
      },
      result =
          @OpaType(type = "string", name = "output", description = "string with `suffix` cut off"))
  public RegoString trimSuffix(EvaluationContext ctx, RegoValue[] args) {
    String value = getArg(args, 0, RegoString.class).getValue();
    String prefix = getArg(args, 1, RegoString.class).getValue();

    String output = value;
    if (value.endsWith(prefix)) {
      output = value.substring(0, value.length() - prefix.length());
    }
    return new RegoString(output);
  }

  @OpaBuiltin(
      name = "trim_prefix",
      description = "Returns `value` with all leading occurrences of `prefix` removed.",
      categories = {"strings"},
      args = {
        @OpaType(type = "string", name = "value", description = "string to trim"),
        @OpaType(type = "string", name = "prefix", description = "prefix to cut off")
      },
      result =
          @OpaType(type = "string", name = "output", description = "string with `prefix` cut off"))
  public RegoString trimPrefix(EvaluationContext ctx, RegoValue[] args) {
    String value = getArg(args, 0, RegoString.class).getValue();
    String prefix = getArg(args, 1, RegoString.class).getValue();

    String output = value;
    if (value.startsWith(prefix)) {
      output = value.substring(prefix.length());
    }
    return new RegoString(output);
  }

  @OpaBuiltin(
      name = "strings.replace_n",
      description = "Replace all occurrences of a pattern with a replacement",
      categories = {"strings"},
      args = {
        @OpaType(type = "object", name = "patterns", description = "replacement pairs"),
        @OpaType(
            type = "string",
            name = "value",
            description = "string to replace substring matches in")
      },
      result =
          @OpaType(
              type = "string",
              name = "output",
              description = "string with replaced substrings"))
  public RegoString replaceN(EvaluationContext ctx, RegoValue[] args) {
    RegoObject patterns = getArg(args, 0, RegoObject.class);
    String value = getArg(args, 1, RegoString.class).getValue();

    List<Map.Entry<RegoValue, RegoValue>> entries =
        new ArrayList<>(patterns.getProperties().entrySet());
    List<String[]> pairs = new ArrayList<>(entries.size());
    for (Map.Entry<RegoValue, RegoValue> entry : entries) {
      if (!(entry.getKey() instanceof RegoString)) {
        throw new TypeError("operand 1 non-string key found in pattern object");
      }
      if (!(entry.getValue() instanceof RegoString)) {
        throw new TypeError("operand 1 non-string value found in pattern object");
      }
      pairs.add(
          new String[] {
            ((RegoString) entry.getKey()).getValue(),
            ((RegoString) entry.getValue()).getValue()
          });
    }
    pairs.sort(Comparator.comparing(p -> p[0]));

    // Single left-to-right scan matching Go's strings.NewReplacer semantics:
    // at each position, try all patterns in order and take the first match.
    StringBuilder sb = new StringBuilder(value.length());
    int i = 0;
    while (i < value.length()) {
      boolean matched = false;
      for (String[] pair : pairs) {
        if (value.startsWith(pair[0], i)) {
          sb.append(pair[1]);
          i += pair[0].length();
          matched = true;
          break;
        }
      }
      if (!matched) {
        sb.append(value.charAt(i));
        i++;
      }
    }
    return new RegoString(sb.toString());
  }

  @OpaBuiltin(
      name = "contains",
      description = "Returns true if the search string is included in the base string",
      categories = {"strings"},
      args = {
        @OpaType(type = "string", name = "haystack", description = "string to search in"),
        @OpaType(type = "string", name = "needle", description = "substring to look for")
      },
      result =
          @OpaType(
              type = "boolean",
              name = "result",
              description = "result of the containment check"))
  public RegoValue contains(EvaluationContext ctx, RegoValue[] args) {
    String haystack = getArg(args, 0, RegoString.class).getValue();
    String needle = getArg(args, 1, RegoString.class).getValue();

    if (haystack.contains(needle)) {
      return RegoBoolean.TRUE;
    }
    return RegoBoolean.FALSE;
  }

  @OpaBuiltin(
      name = "indexof",
      description = "Returns the index of a substring contained inside a string.",
      categories = {"strings"},
      args = {
        @OpaType(type = "string", name = "haystack", description = "string to search in"),
        @OpaType(type = "string", name = "needle", description = "substring to look for")
      },
      result =
          @OpaType(
              type = "number",
              name = "output",
              description = "index of first occurrence, `-1` if not found"))
  public RegoValue indexOf(EvaluationContext ctx, RegoValue[] args) {
    String haystack = getArg(args, 0, RegoString.class).getValue();
    String needle = getArg(args, 1, RegoString.class).getValue();

    int idx = haystack.indexOf(needle);
    return RegoInt32.of(idx < 0 ? idx : haystack.codePointCount(0, idx));
  }

  @OpaBuiltin(
      name = "trim",
      description =
          "Returns `value` with all leading or trailing instances of the `cutset` characters removed.",
      categories = {"strings"},
      args = {
        @OpaType(type = "string", name = "value", description = "string to trim"),
        @OpaType(
            type = "string",
            name = "cutset",
            description = "string of characters that are cut off")
      },
      result =
          @OpaType(
              type = "string",
              name = "output",
              description = "string trimmed of `cutset` characters"))
  public RegoValue trim(EvaluationContext ctx, RegoValue[] args) {
    String value = getArg(args, 0, RegoString.class).getValue();
    String cutset = getArg(args, 1, RegoString.class).getValue();

    Set<Character> cutChars = new HashSet<>();
    for (char c : cutset.toCharArray()) {
      cutChars.add(c);
    }

    // Trim from the beginning
    int start = 0;
    while (start < value.length() && cutChars.contains(value.charAt(start))) {
      start++;
    }

    // Trim from the end
    int end = value.length();
    while (end > start && cutChars.contains(value.charAt(end - 1))) {
      end--;
    }

    return new RegoString(value.substring(start, end));
  }

  @OpaBuiltin(
      name = "trim_left",
      description =
          "Returns `value` with all leading instances of the `cutset` characters removed.",
      categories = {"strings"},
      args = {
        @OpaType(type = "string", name = "value", description = "string to trim"),
        @OpaType(
            type = "string",
            name = "cutset",
            description = "string of characters that are cut off on the left")
      },
      result =
          @OpaType(
              type = "string",
              name = "output",
              description = "string left-trimmed of `cutset` characters"))
  public RegoString trimLeft(EvaluationContext ctx, RegoValue[] args) {
    String value = getArg(args, 0, RegoString.class).getValue();
    String cutset = getArg(args, 1, RegoString.class).getValue();

    Set<Character> cutChars = new HashSet<>();
    for (char c : cutset.toCharArray()) {
      cutChars.add(c);
    }

    // Trim from the beginning
    int start = 0;
    while (start < value.length() && cutChars.contains(value.charAt(start))) {
      start++;
    }

    return new RegoString(value.substring(start));
  }

  @OpaBuiltin(
      name = "trim_right",
      description =
          "Returns `value` with all trailing instances of the `cutset` characters removed.",
      categories = {"strings"},
      args = {
        @OpaType(type = "string", name = "value", description = "string to trim"),
        @OpaType(
            type = "string",
            name = "cutset",
            description = "string of characters that are cut off on the right")
      },
      result =
          @OpaType(
              type = "string",
              name = "output",
              description = "string right-trimmed of `cutset` characters"))
  public RegoString trimRight(EvaluationContext ctx, RegoValue[] args) {
    String value = getArg(args, 0, RegoString.class).getValue();
    String cutset = getArg(args, 1, RegoString.class).getValue();

    Set<Character> cutChars = new HashSet<>();
    for (char c : cutset.toCharArray()) {
      cutChars.add(c);
    }

    int start = 0;
    // Trim from the end
    int end = value.length();
    while (end > start && cutChars.contains(value.charAt(end - 1))) {
      end--;
    }

    return new RegoString(value.substring(start, end));
  }

  @OpaBuiltin(
      name = "upper",
      description = "Returns the input string but with all characters in upper-case.",
      categories = {"strings"},
      args = {
        @OpaType(
            type = "string",
            name = "x",
            description = "string that is converted to upper-case")
      },
      result = @OpaType(type = "string", name = "y", description = "upper-case of x"))
  public RegoValue upper(EvaluationContext ctx, RegoValue[] args) {
    String x = getArg(args, 0, RegoString.class).getValue();
    return new RegoString(x.toUpperCase());
  }

  @OpaBuiltin(
      name = "substring",
      description =
          "Returns the  portion of a string for a given `offset` and a `length`.  If `length < 0`, `output` is the remainder of the string.",
      categories = {"strings"},
      args = {
        @OpaType(type = "string", name = "value", description = "string to extract substring from"),
        @OpaType(type = "number", name = "offset", description = "offset, must be positive"),
        @OpaType(
            type = "number",
            name = "length",
            description = "length of the substring starting from `offset`")
      },
      result =
          @OpaType(
              type = "string",
              name = "output",
              description = "substring of `value` from `offset`, of length `length`"))
  public RegoValue substring(EvaluationContext ctx, RegoValue[] args) {
    String value = getArg(args, 0, RegoString.class).getValue();
    int offset = getArg(args, 1, RegoInt32.class).getValue();
    int length = getArg(args, 2, RegoInt32.class).getValue();

    if (offset < 0) {
      throw new BuiltinError("negative offset");
    }

    String ss;
    if (offset > value.length()) {
      ss = "";
    } else if (length < 0 || offset + length > value.length()) {
      ss = value.substring(offset);
    } else {
      ss = value.substring(offset, offset + length);
    }

    return new RegoString(ss);
  }

  @OpaBuiltin(
      name = "lower",
      description = "Returns the input string but with all characters in lower-case.",
      categories = {"strings"},
      args = {
        @OpaType(
            type = "string",
            name = "x",
            description = "string that is converted to lower-case")
      },
      result = @OpaType(type = "string", name = "y", description = "lower-case of x"))
  public RegoValue lower(EvaluationContext ctx, RegoValue[] args) {
    String x = getArg(args, 0, RegoString.class).getValue();
    return new RegoString(x.toLowerCase());
  }

  @OpaBuiltin(
      name = "concat",
      description = "Concatenates the elements of an array into a string using a delimiter",
      args = {
        @OpaType(type = "string", name = "delimiter", description = "string to use as a delimiter"),
        @OpaType(type = "any", name = "collection", description = "strings to join")
      },
      result = @OpaType(type = "string", name = "output", description = "the joined string"))
  public RegoValue concat(EvaluationContext ctx, RegoValue[] args) {
    String delimiter = getArg(args, 0, RegoString.class).getValue();
    RegoValue collection = args[1];
    Collection<RegoValue> c;
    if (collection instanceof RegoArray) {
      RegoArray ra = (RegoArray) collection;
      c = ra.getValue();
    } else if (collection instanceof RegoSet) {
      RegoSet rs = (RegoSet) collection;
      c = rs.getValue();
    } else {
      throw new TypeError("concat requires array or set but got " + collection.getTypeName())
          .withContext("operation", "concat")
          .withContext("arg", collection)
          .withContext("expectedTypes", List.of("array", "set"));
    }
    List<String> strings =
        c.stream().map(s -> ((RegoString) s).getValue()).collect(Collectors.toList());

    String joined = String.join(delimiter, strings);
    return new RegoString(joined);
  }

  @OpaBuiltin(
      name = "sprintf",
      description = "Returns a formatted string using Go fmt.Sprintf-style format specifiers",
      args = {
        @OpaType(type = "string", name = "format", description = "string with formatting verbs"),
        @OpaType(
            type = "array",
            name = "values",
            description = "arguments to format into formatting verbs",
            dynamic = @OpaDynamic(type = "any"))
      },
      result =
          @OpaType(
              type = "string",
              name = "output",
              description = "`format` formatted by the values in `values`"))
  public RegoValue sprintf(EvaluationContext ctx, RegoValue[] args) {
    String format = getArg(args, 0, RegoString.class).getValue();
    List<RegoValue> fmtArgs = getArg(args, 1, RegoArray.class).getValue();

    RegoValue[] argsArray = fmtArgs.toArray(new RegoValue[0]);
    String result = SprintfUtil.sprintf(format, argsArray);
    return new RegoString(result);
  }

  @OpaBuiltin(
      name = "split",
      description =
          "Split returns an array containing elements of the input string split on a delimiter.",
      categories = {"strings"},
      args = {
        @OpaType(type = "string", name = "x", description = "string that is split"),
        @OpaType(type = "string", name = "delimiter", description = "delimiter used for splitting")
      },
      result =
          @OpaType(
              type = "array",
              name = "ys",
              description = "split parts",
              dynamic = @OpaDynamic(type = "string")))
  public RegoValue split(EvaluationContext ctx, RegoValue[] args) {
    String x = getArg(args, 0, RegoString.class).getValue();
    String delimiter = (getArg(args, 1, RegoString.class)).getValue();

    if (delimiter.isEmpty()) {
      if (x.isEmpty()) {
        return new RegoArray(List.of());
      }
      List<RegoValue> chars = new ArrayList<>();
      x.codePoints()
          .forEach(cp -> chars.add(new RegoString(new String(Character.toChars(cp)))));
      return new RegoArray(chars);
    }

    List<RegoValue> myResult =
        Arrays.stream(x.split(Pattern.quote(delimiter), -1))
            .map(s -> (RegoValue) new RegoString(s))
            .collect(Collectors.toList());
    return new RegoArray(myResult);
  }

  @OpaBuiltin(
      name = "replace",
      description = "Replace replaces all instances of a sub-string.",
      categories = {"strings"},
      args = {
        @OpaType(type = "string", name = "x", description = "string being processed"),
        @OpaType(type = "string", name = "old", description = "substring to replace"),
        @OpaType(type = "string", name = "new", description = "string to replace `old` with")
      },
      result =
          @OpaType(type = "string", name = "y", description = "string with replaced substrings"))
  public RegoValue replace(EvaluationContext ctx, RegoValue[] args) {
    String x = getArg(args, 0, RegoString.class).getValue();
    String oldStr = getArg(args, 1, RegoString.class).getValue();
    String newStr = getArg(args, 2, RegoString.class).getValue();

    String replaced = x.replace(oldStr, newStr);
    return new RegoString(replaced);
  }

  @OpaBuiltin(
      name = "startswith",
      description = "Returns true if the search string begins with the base string.",
      categories = {"strings"},
      args = {
        @OpaType(type = "string", name = "search", description = "search string"),
        @OpaType(type = "string", name = "base", description = "base string")
      },
      result =
          @OpaType(type = "boolean", name = "result", description = "result of the prefix check"))
  public RegoValue startsWith(EvaluationContext ctx, RegoValue[] args) {
    String base = getArg(args, 0, RegoString.class).getValue();
    String search = getArg(args, 1, RegoString.class).getValue();

    if (base.startsWith(search)) {
      return RegoBoolean.TRUE;
    } else {
      return RegoBoolean.FALSE;
    }
  }

  @OpaBuiltin(
      name = "endswith",
      description = "Returns true if the search string ends with the base string.",
      categories = {"strings"},
      args = {
        @OpaType(type = "string", name = "search", description = "search string"),
        @OpaType(type = "string", name = "base", description = "base string")
      },
      result =
          @OpaType(type = "boolean", name = "result", description = "result of the suffix check"))
  public RegoValue endsWith(EvaluationContext ctx, RegoValue[] args) {
    String base = getArg(args, 0, RegoString.class).getValue();
    String search = getArg(args, 1, RegoString.class).getValue();

    if (base.endsWith(search)) {
      return RegoBoolean.TRUE;
    } else {
      return RegoBoolean.FALSE;
    }
  }

  @OpaBuiltin(
      name = "format_int",
      description =
          "Returns the string representation of the number in the given base after rounding it down to an integer value.",
      categories = {"strings"},
      args = {
        @OpaType(type = "number", name = "number", description = "number to format"),
        @OpaType(
            type = "number",
            name = "base",
            description = "base of number representation to use")
      },
      result = @OpaType(type = "string", name = "output", description = "formatted number"))
  public RegoValue formatInt(EvaluationContext ctx, RegoValue[] args) {
    int base = getArg(args, 1, RegoInt32.class).getValue();
    if (base != 2 && base != 8 && base != 10 && base != 16) {
      throw new TypeError("operand 2 must be one of {2, 8, 10, 16}");
    }

    String formatted;
    if (args[0] instanceof RegoInt32) {
      RegoInt32 ri = (RegoInt32) args[0];
      formatted = Integer.toString(ri.getValue(), base);
    } else if (args[0] instanceof RegoBigInt) {
      RegoBigInt ri = (RegoBigInt) args[0];
      formatted = ri.getValue().toString(base);
    } else if (args[0] instanceof RegoDecimal) {
      RegoDecimal rd = (RegoDecimal) args[0];
      // toBigInteger() truncates toward zero, matching OPA's round-down-to-integer semantics.
      formatted = BigDecimal.valueOf(rd.getValue()).toBigInteger().toString(base);
    } else {
      throw new UnsupportedOperationException(
          "Unsupported type for format_int operation: " + args[0].getClass().getSimpleName());
    }
    return new RegoString(formatted);
  }

  @OpaBuiltin(
      name = "strings.reverse",
      description = "Reverses a given string.",
      categories = {"strings"},
      args = {@OpaType(type = "string", name = "x", description = "string to reverse")},
      result = @OpaType(type = "string", name = "y", description = "reversed string"))
  public RegoValue reverse(EvaluationContext ctx, RegoValue[] args) {
    String x = getArg(args, 0, RegoString.class).getValue();
    String reversed = new StringBuilder(x).reverse().toString();
    return new RegoString(reversed);
  }

  @OpaBuiltin(
      name = "strings.count",
      description = "Returns the number of non-overlapping instances of a substring in a string.",
      categories = {"strings"},
      args = {
        @OpaType(type = "string", name = "search", description = "string to search in"),
        @OpaType(type = "string", name = "substring", description = "substring to look for")
      },
      result =
          @OpaType(
              type = "number",
              name = "output",
              description = "count of occurrences, `0` if not found"))
  public RegoValue count(EvaluationContext ctx, RegoValue[] args) {
    String search = getArg(args, 0, RegoString.class).getValue();
    String substring = getArg(args, 1, RegoString.class).getValue();

    int count = 0;
    int lastIdx = 0;

    if (substring == null || substring.isEmpty()) {
        return RegoInt32.of(search.length()+1);

    }

    while ((lastIdx = search.indexOf(substring, lastIdx)) != -1) {
      count++;
      lastIdx += substring.length();
    }

    return RegoInt32.of(count);
  }

  @OpaBuiltin(
      name = "indexof_n",
      description = "Returns a list of all the indexes of a substring contained inside a string.",
      categories = {"strings"},
      args = {
        @OpaType(type = "string", name = "haystack", description = "string to search in"),
        @OpaType(type = "string", name = "needle", description = "substring to look for")
      },
      result =
          @OpaType(
              type = "array",
              name = "output",
              description = "all indices at which `needle` occurs in `haystack`, may be empty",
              dynamic = @OpaDynamic(type = "number")))
  public RegoValue indexOfN(EvaluationContext ctx, RegoValue[] args) {
    String haystack = getArg(args, 0, RegoString.class).getValue();
    String needle = getArg(args, 1, RegoString.class).getValue();

    List<RegoValue> indices = new ArrayList<>();
    int lastIdx = 0;
    while ((lastIdx = haystack.indexOf(needle, lastIdx)) != -1) {
      indices.add(RegoInt32.of(haystack.codePointCount(0, lastIdx)));
      lastIdx++;
    }
    return new RegoArray(indices);
  }

  @OpaBuiltin(
      name = "strings.any_suffix_match",
      description = "Returns true if any of the search strings ends with any of the base strings.",
      categories = {"strings"},
      args = {
        @OpaType(type = "any", name = "search", description = "search string(s)"),
        @OpaType(type = "any", name = "base", description = "base string(s)")
      },
      result =
          @OpaType(type = "boolean", name = "result", description = "result of the suffix check"))
  public RegoValue anySuffixMatch(EvaluationContext ctx, RegoValue[] args) {
    assertArgType(args, 0, RegoString.class, RegoSet.class, RegoArray.class);
    assertArgType(args, 1, RegoString.class, RegoSet.class, RegoArray.class);

    List<String> bases;
    if (args[0] instanceof RegoString) {
      bases = List.of(getArg(args, 0, RegoString.class).getValue());
    } else if (args[0] instanceof RegoArray) {
      bases = getStrings(getArg(args, 0, RegoArray.class));
    } else if (args[0] instanceof RegoSet) {
      bases = getStrings(getArg(args, 0, RegoSet.class));
    } else {
      throw new UnsupportedOperationException(
              "Unsupported type for strings.any_suffix_match operation: "
                      + args[0].getClass().getSimpleName());
    }

    List<String> matchers;
    if (args[1] instanceof RegoString) {
      matchers = List.of(getArg(args, 1, RegoString.class).getValue());
    } else if (args[1] instanceof RegoArray) {
      matchers = getStrings(getArg(args, 1, RegoArray.class));
    } else if (args[1] instanceof RegoSet) {
      matchers = getStrings(getArg(args, 1, RegoSet.class));
    } else {
      throw new UnsupportedOperationException(
          "Unsupported type for strings.any_suffix_match operation: "
              + args[0].getClass().getSimpleName());
    }

    for (String matcher : matchers) {
      for (String baseStr : bases) {
        if (baseStr.endsWith(matcher)) {
          return RegoBoolean.TRUE;
        }
      }
    }
    return RegoBoolean.FALSE;
  }

  @OpaBuiltin(
      name = "strings.any_prefix_match",
      description =
          "Returns true if any of the search strings begins with any of the base strings.",
      categories = {"strings"},
      args = {
        @OpaType(type = "any", name = "search", description = "search string(s)"),
        @OpaType(type = "any", name = "base", description = "base string(s)")
      },
      result =
          @OpaType(type = "boolean", name = "result", description = "result of the prefix check"))
  public RegoValue anyPrefixMatch(EvaluationContext ctx, RegoValue[] args) {
    assertArgType(args, 0, RegoString.class, RegoSet.class, RegoArray.class);
    assertArgType(args, 1, RegoString.class, RegoSet.class, RegoArray.class);

    List<String> bases;
    if (args[0] instanceof RegoString) {
      bases = List.of(getArg(args, 0, RegoString.class).getValue());
    } else if (args[0] instanceof RegoArray) {
      bases = getStrings(getArg(args, 0, RegoArray.class));
    } else if (args[0] instanceof RegoSet) {
      bases = getStrings(getArg(args, 0, RegoSet.class));
    } else {
      throw new UnsupportedOperationException(
          "Unsupported type for strings.any_prefix_match operation: "
              + args[0].getClass().getSimpleName());
    }

    List<String> matchers;
    if (args[1] instanceof RegoString) {
      matchers = List.of(getArg(args, 1, RegoString.class).getValue());
    } else if (args[1] instanceof RegoArray) {
      matchers = getStrings(getArg(args, 1, RegoArray.class));
    } else if (args[1] instanceof RegoSet) {
      matchers = getStrings(getArg(args, 1, RegoSet.class));
    } else {
      throw new UnsupportedOperationException(
          "Unsupported type for strings.any_prefix_match operation: "
              + args[0].getClass().getSimpleName());
    }

    for (String matcher : matchers) {
      for (String baseStr : bases) {
        if (baseStr.startsWith(matcher)) {
          return RegoBoolean.TRUE;
        }
      }
    }
    return RegoBoolean.FALSE;
  }

  private static List<String> getStrings(RegoCollection ra) {
    List<String> bases;
    bases = new ArrayList<>();
    for (RegoValue v : ra.valueStream().collect(Collectors.toList())) {
      if (v instanceof RegoString) {
        bases.add(((RegoString) v).getValue());
      } else {
        throw new TypeError("operand 0 must be array of strings but got array containing " + v.getTypeName());
      }
    }
    return bases;
  }
}
