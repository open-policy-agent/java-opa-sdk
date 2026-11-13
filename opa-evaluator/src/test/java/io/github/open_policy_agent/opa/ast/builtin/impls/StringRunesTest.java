package io.github.open_policy_agent.opa.ast.builtin.impls;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.open_policy_agent.opa.ast.builtin.BuiltinRegistry;
import io.github.open_policy_agent.opa.ast.types.RegoArray;
import io.github.open_policy_agent.opa.ast.types.RegoInt32;
import io.github.open_policy_agent.opa.ast.types.RegoNumber;
import io.github.open_policy_agent.opa.ast.types.RegoString;
import io.github.open_policy_agent.opa.ast.types.RegoValue;
import io.github.open_policy_agent.opa.rego.EvaluationContext;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Strings are measured and cut in code points, as Go (and so OPA) does, not in UTF-16 units. The
 * strings hold an emoji (two UTF-16 units, one code point) and a combining mark. Expected values are
 * what {@code opa eval} (OPA 1.21.1) prints for the same call.
 */
class StringRunesTest {

  private static final BuiltinRegistry REGISTRY = BuiltinRegistry.allCapabilities();

  static Stream<Arguments> cases() {
    return Stream.of(
        Arguments.of("count", new Object[] {"emoji \ud83d\ude00 test"}, 12),
        Arguments.of("count", new Object[] {"a\ud83d\ude00b"}, 3),
        Arguments.of("count", new Object[] {"\ud83d\ude00\ud83d\ude00\ud83d\ude00"}, 3),
        Arguments.of("count", new Object[] {"e\u0301x"}, 3),
        Arguments.of("count", new Object[] {"  a\ud83d\ude00  "}, 6),
        Arguments.of("strings.count", new Object[] {"emoji \ud83d\ude00 test", ""}, 13),
        Arguments.of("strings.count", new Object[] {"a\ud83d\ude00b", ""}, 4),
        Arguments.of("strings.count", new Object[] {"\ud83d\ude00\ud83d\ude00\ud83d\ude00", ""}, 4),
        Arguments.of("strings.count", new Object[] {"e\u0301x", ""}, 4),
        Arguments.of("substring", new Object[] {"a\ud83d\ude00b", 1, 2}, "\ud83d\ude00b"),
        Arguments.of("substring", new Object[] {"a\ud83d\ude00b", 0, 3}, "a\ud83d\ude00b"),
        Arguments.of("substring", new Object[] {"\ud83d\ude00\ud83d\ude00\ud83d\ude00", 1, 2}, "\ud83d\ude00\ud83d\ude00"),
        Arguments.of("substring", new Object[] {"a\ud83d\ude00b", 2, -1}, "b"),
        Arguments.of("substring", new Object[] {"\ud83d\ude00\ud83d\ude00\ud83d\ude00", 2, -1}, "\ud83d\ude00"),
        Arguments.of("substring", new Object[] {"emoji \ud83d\ude00 test", 6, 1}, "\ud83d\ude00"),
        Arguments.of("substring", new Object[] {"e\u0301x", 1, 2}, "\u0301x"),
        Arguments.of("substring", new Object[] {"\ud83d\ude00\ud83d\ude00\ud83d\ude00", 0, 3}, "\ud83d\ude00\ud83d\ude00\ud83d\ude00"),
        Arguments.of("sprintf", new Object[] {"[%6s][%-6s][%.2s]", List.of("a\ud83d\ude00b", "a\ud83d\ude00b", "a\ud83d\ude00b")}, "[   a\ud83d\ude00b][a\ud83d\ude00b   ][a\ud83d\ude00]"),
        Arguments.of("sprintf", new Object[] {"[%6s][%-6s][%.2s]", List.of("\ud83d\ude00\ud83d\ude00\ud83d\ude00", "\ud83d\ude00\ud83d\ude00\ud83d\ude00", "\ud83d\ude00\ud83d\ude00\ud83d\ude00")}, "[   \ud83d\ude00\ud83d\ude00\ud83d\ude00][\ud83d\ude00\ud83d\ude00\ud83d\ude00   ][\ud83d\ude00\ud83d\ude00]"),
        Arguments.of("upper", new Object[] {"\u00df \u0130 \u01c5"}, "\u00df \u0130 \u01c4"),
        Arguments.of("lower", new Object[] {"\u00df \u0130 \u01c5"}, "\u00df i \u01c6"),
        Arguments.of("trim", new Object[] {"\ud83d\ude01", "\ud83d\ude00"}, "\ud83d\ude01"),
        Arguments.of("trim_left", new Object[] {"\ud83d\ude01a", "\ud83d\ude00"}, "\ud83d\ude01a"),
        Arguments.of("trim_right", new Object[] {"a\ud83d\ude01", "\ud83d\ude00"}, "a\ud83d\ude01"),
        Arguments.of("trim", new Object[] {"\ud83d\ude00a\ud83d\ude00", "\ud83d\ude00"}, "a"),
        Arguments.of("sprintf", new Object[] {"%.2x", List.of("a\ud83d\ude00")}, "61f0"),
        Arguments.of("sprintf", new Object[] {"%.2x", List.of("abcd")}, "6162"),
        Arguments.of("sprintf", new Object[] {"%.1x", List.of("\ud83d\ude00")}, "f0"),
        Arguments.of("sprintf", new Object[] {"[%12x]", List.of("a\ud83d\ude00")}, "[  61f09f9880]"),
        Arguments.of("sprintf", new Object[] {"%x", List.of("a\ud83d\ude00")}, "61f09f9880"));
  }

  @ParameterizedTest(name = "{0}({1})")
  @MethodSource("cases")
  void matchesOpa(String builtin, Object[] arguments, Object expected) {
    final RegoValue[] args = new RegoValue[arguments.length];
    for (int i = 0; i < arguments.length; i++) {
      args[i] = toRego(arguments[i]);
    }

    final RegoValue result =
        REGISTRY.getBuiltIn(builtin).apply(new EvaluationContext.Builder().build(), args);

    assertEquals(expected, fromRego(result));
  }

  private static RegoValue toRego(Object value) {
    if (value instanceof String) {
      return new RegoString((String) value);
    }
    if (value instanceof Integer) {
      return RegoInt32.of((Integer) value);
    }
    final List<RegoValue> list = new ArrayList<>();
    for (Object element : (List<?>) value) {
      list.add(toRego(element));
    }
    return new RegoArray(list);
  }

  private static Object fromRego(RegoValue value) {
    if (value instanceof RegoString) {
      return ((RegoString) value).getValue();
    }
    return ((RegoNumber) value).getBigIntValue().intValueExact();
  }
}
