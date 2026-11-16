package io.github.open_policy_agent.opa.ast.builtin.impls;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.open_policy_agent.opa.ast.builtin.BuiltinError;
import io.github.open_policy_agent.opa.ast.types.RegoNumber;
import io.github.open_policy_agent.opa.ast.types.RegoString;
import io.github.open_policy_agent.opa.ast.types.RegoValue;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** Expected values come from {@code opa eval} (OPA 1.21.1); a null expectation means undefined. */
class TimeParseDurationNsTest {

  private final TimeBuiltins builtins = new TimeBuiltins();

  static Stream<Arguments> durations() {
    return Stream.of(
        Arguments.of("1h30m", "5400000000000"),
        Arguments.of("1m30s", "90000000000"),
        Arguments.of("2h45m30s", "9930000000000"),
        Arguments.of("1.5h", "5400000000000"),
        Arguments.of("1.5s", "1500000000"),
        Arguments.of("90m", "5400000000000"),
        Arguments.of("1h", "3600000000000"),
        Arguments.of("-1h", "-3600000000000"),
        Arguments.of("+1h", "3600000000000"),
        Arguments.of("-1h30m", "-5400000000000"),
        Arguments.of("0", "0"),
        Arguments.of("-0", "0"),
        Arguments.of("+0", "0"),
        Arguments.of("1h-30m", null),
        Arguments.of("1.5", null),
        Arguments.of(".5s", "500000000"),
        Arguments.of("5.s", "5000000000"),
        Arguments.of(".", null),
        Arguments.of("1\u00b5s", "1000"),
        Arguments.of("1\u03bcs", "1000"),
        Arguments.of("1h1h", "7200000000000"),
        Arguments.of("1d", "86400000000000"),
        Arguments.of("1w", "604800000000000"),
        Arguments.of("1y", "31536000000000000"),
        Arguments.of("1d2h30m", "95400000000000"),
        Arguments.of("1.5d", "129600000000000"),
        Arguments.of("-1d", "-86400000000000"),
        Arguments.of("+1w", "604800000000000"),
        Arguments.of("1d1d", "172800000000000"),
        Arguments.of("1d1\u03bcs", null),
        Arguments.of("1d1\u00b5s", "86400000001000"),
        Arguments.of("1d1ns", "86400000000001"),
        Arguments.of("1d1ms", "86400001000000"),
        Arguments.of("1d1us", "86400000001000"),
        Arguments.of("2y3w4d5h6m7s8ms9us10ns", "65250367008009010"),
        Arguments.of("1.5y", "47304000000000000"),
        Arguments.of("0.5w", "302400000000000"),
        Arguments.of(".5d", "43200000000000"),
        Arguments.of("5.d", "432000000000000"),
        Arguments.of(".d", null),
        Arguments.of("1d.5h", "88200000000000"),
        Arguments.of("1d1.2.3h", null),
        Arguments.of("1d1x", null),
        Arguments.of("1d ", null),
        Arguments.of("d", null),
        Arguments.of("1dd", null),
        Arguments.of("1d1", null),
        Arguments.of("9223372036854775807ns", "9223372036854775807"),
        Arguments.of("9223372036854775808ns", null),
        Arguments.of("-9223372036854775808ns", "-9223372036854775808"),
        Arguments.of("-9223372036854775809ns", null),
        Arguments.of("9223372036854775807", null),
        Arguments.of("2562047h47m16.854775807s", "9223372036854775807"),
        Arguments.of("2562047h47m16.854775808s", null),
        Arguments.of("-2562047h47m16.854775808s", "-9223372036854775808"),
        Arguments.of("2562047h47m16.854775807s1ns", null),
        Arguments.of("106751d23h47m16.854775807s", "9223372036854775807"),
        Arguments.of("106751d23h47m16.854775808s", null),
        Arguments.of("106752d", null),
        Arguments.of("1000000000y", null),
        Arguments.of("1e3s", null),
        Arguments.of(" 1h", null),
        Arguments.of("1h ", null),
        Arguments.of("1H", null),
        Arguments.of("1hour", null),
        Arguments.of("1ns2ns", "3"),
        Arguments.of("100000000000000000000s", null),
        Arguments.of("0.000000001s", "1"),
        Arguments.of("0.0000000009s", "0"),
        Arguments.of("1.0000000001s", "1000000000"),
        Arguments.of("1.123456789123456789s", "1123456789"),
        Arguments.of("1h0.5m", "3630000000000"),
        Arguments.of("-.5h", "-1800000000000"),
        Arguments.of("+.5h", "1800000000000"),
        Arguments.of("-0.0s", "0"),
        Arguments.of("1d0.0001h", "86400360000000"),
        Arguments.of("9".repeat(400) + "d", null));
  }

  @ParameterizedTest
  @MethodSource("durations")
  void matchesOpa(String duration, String expected) {
    final RegoValue[] args = {new RegoString(duration)};

    if (expected == null) {
      assertThrows(BuiltinError.class, () -> builtins.parseDurationNs(null, args));
    } else {
      assertEquals(expected, ((RegoNumber) builtins.parseDurationNs(null, args)).getBigIntValue().toString());
    }
  }
}
