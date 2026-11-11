package io.github.open_policy_agent.opa.ast.builtin.impls;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.open_policy_agent.opa.ast.builtin.BuiltinError;
import io.github.open_policy_agent.opa.ast.types.RegoNumber;
import io.github.open_policy_agent.opa.ast.types.RegoString;
import io.github.open_policy_agent.opa.ast.types.RegoValue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Expected values come from {@code opa eval} (OPA 1.21.1), which returns the exact int64. */
class TimeParseNsTest {

  private final TimeBuiltins builtins = new TimeBuiltins();

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      value = {
        "2024-01-01T00:00:00.000000001Z|1704067200000000001",
        "1970-01-01T00:00:00Z|0",
        "1969-12-31T23:59:59.999999999Z|-1",
        "1677-09-21T00:12:43.145224192Z|-9223372036854775808",
        "2262-04-11T23:47:16.854775807Z|9223372036854775807",
        "2024-06-01T12:00:00.1Z|1717243200100000000",
        "2024-06-01T12:00:00.12Z|1717243200120000000",
        "2024-06-01T12:00:00.123Z|1717243200123000000",
        "2024-06-01T12:00:00.1234Z|1717243200123400000",
        "2024-06-01T12:00:00.12345Z|1717243200123450000",
        "2024-06-01T12:00:00.123456Z|1717243200123456000",
        "2024-06-01T12:00:00.1234567Z|1717243200123456700",
        "2024-06-01T12:00:00.12345678Z|1717243200123456780",
        "2024-06-01T12:00:00.123456789Z|1717243200123456789",
        "2024-06-01T12:00:00.123456789+05:30|1717223400123456789",
        "2024-06-01T12:00:00.123456789-02:00|1717250400123456789",
        "1900-01-01T00:00:00.000000007Z|-2208988799999999993",
        "2023-12-31T23:59:59.999999999Z|1704067199999999999"
      })
  void parseRfc3339NsReturnsTheExactNanoseconds(String value, String expected) {
    assertEquals(expected, exact(builtins.parseRFC3339Ns(null, new RegoValue[] {new RegoString(value)})));
  }

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      value = {
        "RFC3339Nano|2024-01-01T00:00:00.000000001Z|1704067200000000001",
        "2006-01-02|2024-01-01|1704067200000000000",
        "2006-01-02T15:04:05Z07:00|2024-03-04T05:06:07+09:00|1709496367000000000",
        "2006-01-02 15:04:05.999999999 -0700|2024-03-04 05:06:07.123456789 +0900|1709496367123456789",
        "2006-01-02T15:04:05.000000000Z07:00|2024-01-01T00:00:00.000000001Z|1704067200000000001",
        "2006-01-02T15:04:05.000Z07:00|2024-01-01T00:00:00.123Z|1704067200123000000",
        "2006-01-02T15:04:05Z07:00|2024-01-01T00:00:00.123456789Z|1704067200123456789",
        "RFC3339|2024-01-01T00:00:00.5Z|1704067200500000000",
        "2006-01-02T15:04:05.999999999Z07:00|2024-01-01T00:00:00.1Z|1704067200100000000",
        "2006-01-02T15:04:05.999999999Z07:00|2024-01-01T00:00:00.12Z|1704067200120000000",
        "2006-01-02T15:04:05.999999999Z07:00|2024-01-01T00:00:00Z|1704067200000000000",
        "2006-01-02T15:04:05.999Z07:00|2024-01-01T00:00:00.1Z|1704067200100000000",
        "2006-01-02T15:04:05.999Z07:00|2024-01-01T00:00:00.12345Z|1704067200123450000",
        "2006-01-02T15:04:05.999Z07:00|2024-01-01T00:00:00.123456789Z|1704067200123456789",
        "2006-01-02T15:04:05.999999Z07:00|2024-01-01T00:00:00.1Z|1704067200100000000"
      })
  void parseNsReturnsTheExactNanoseconds(String layout, String value, String expected) {
    assertEquals(
        expected,
        exact(builtins.parseNs(null, new RegoValue[] {new RegoString(layout), new RegoString(value)})));
  }

  @ParameterizedTest
  @CsvSource({
    "1677-09-21T00:12:43.145224191Z",
    "2262-04-11T23:47:16.854775808Z",
    "9999-12-31T23:59:59Z",
    "not a time"
  })
  void parseRfc3339NsIsUndefinedOutsideTheInt64RangeAndForInvalidInput(String value) {
    assertThrows(
        BuiltinError.class,
        () -> builtins.parseRFC3339Ns(null, new RegoValue[] {new RegoString(value)}));
  }

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      value = {
        "2006-01-02|2262-04-12",
        "2006-01-02|1677-09-21",
        "2006-01-02T15:04:05.000Z07:00|2024-01-01T00:00:00.1Z"
      })
  void parseNsIsUndefinedOutsideTheInt64Range(String layout, String value) {
    assertThrows(
        BuiltinError.class,
        () -> builtins.parseNs(null, new RegoValue[] {new RegoString(layout), new RegoString(value)}));
  }

  @Test
  void theResultIsAnIntegerNotADecimal() {
    final RegoValue result =
        builtins.parseRFC3339Ns(null, new RegoValue[] {new RegoString("2024-01-01T00:00:00.000000001Z")});

    assertFalse(((RegoNumber) result).isDecimal());
  }

  private static String exact(RegoValue result) {
    return ((RegoNumber) result).getBigIntValue().toString();
  }
}
