package io.github.open_policy_agent.opa.ast.builtin.impls;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.open_policy_agent.opa.ast.builtin.BuiltinError;
import io.github.open_policy_agent.opa.ast.types.RegoNumber;
import io.github.open_policy_agent.opa.ast.types.RegoString;
import io.github.open_policy_agent.opa.ast.types.RegoValue;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Supplier;
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

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      value = {
        "RFC3339Nano|2024-06-01T12:00:00.123456789Z|1717243200123456789",
        "2006-01-02|2024-01-01|1704067200000000000",
        "2006-01-02 15:04:05.999999999 -0700|2024-03-04 05:06:07.123456789 +0900|1709496367123456789"
      })
  void aRepeatedLayoutGivesTheSameAnswerEveryTime(String layout, String value, String expected) {
    for (int i = 0; i < 3; i++) {
      assertEquals(
          expected,
          exact(builtins.parseNs(null, new RegoValue[] {new RegoString(layout), new RegoString(value)})));
    }
  }

  @Test
  void aLayoutThatDoesNotFitTheValueIsAnErrorEveryTime() {
    final RegoValue[] args = {new RegoString("2006-01-02"), new RegoString("not a date")};

    assertThrows(BuiltinError.class, () -> builtins.parseNs(null, args));
    assertThrows(BuiltinError.class, () -> builtins.parseNs(null, args));
  }

  @Test
  void manyDifferentLayoutsStillParse() {
    for (int i = 0; i < 600; i++) {
      final String separator = String.valueOf((char) (0x2190 + i % 300));
      final String layout = "2006" + separator + "01" + separator + "02" + " " + "15" + (i % 2 == 0 ? ":04:05" : ":04");
      final String value = "2024" + separator + "03" + separator + "04 05" + (i % 2 == 0 ? ":06:07" : ":06");
      final String expected = i % 2 == 0 ? "1709528767000000000" : "1709528760000000000";

      assertEquals(
          expected,
          exact(builtins.parseNs(null, new RegoValue[] {new RegoString(layout), new RegoString(value)})),
          layout);
    }
  }

  @Test
  void concurrentParsesGiveTheSameAnswers() throws Exception {
    final String[][] cases = {
      {"RFC3339Nano", "2024-06-01T12:00:00.123456789Z", "1717243200123456789"},
      {"2006-01-02", "2024-01-01", "1704067200000000000"},
      {"2006-01-02T15:04:05Z07:00", "2024-03-04T05:06:07+09:00", "1709496367000000000"},
      {"RFC3339", "2024-01-01T00:00:00.5Z", "1704067200500000000"}
    };
    final ExecutorService pool = Executors.newFixedThreadPool(8);
    try {
      final List<Future<String>> futures = new ArrayList<>();
      for (int t = 0; t < 8; t++) {
        futures.add(
            pool.submit(
                () -> {
                  for (int i = 0; i < 2000; i++) {
                    final String[] c = cases[i % cases.length];
                    final String got =
                        exact(builtins.parseNs(null, new RegoValue[] {new RegoString(c[0]), new RegoString(c[1])}));
                    if (!c[2].equals(got)) {
                      return c[0] + " " + c[1] + " gave " + got;
                    }
                  }
                  return "";
                }));
      }
      for (final Future<String> future : futures) {
        assertEquals("", future.get());
      }
    } finally {
      pool.shutdownNow();
    }
  }

  // The direct RFC3339 parser must agree with the formatter on every input: the same value, or the same error.
  @Test
  void rfc3339ParsingAgreesWithTheFormatterOnEveryDateAndTime() {
    final String[] years = {"0000", "0001", "1677", "1969", "1970", "2024", "2262", "9999"};
    final String[] months = {"00", "01", "02", "12", "13"};
    final String[] days = {"00", "01", "28", "29", "30", "31", "32"};
    final String[] hours = {"00", "23", "24", "25"};
    final String[] minutes = {"00", "59", "60"};
    final String[] seconds = {"00", "59", "60", "61"};
    for (final String year : years) {
      for (final String month : months) {
        for (final String day : days) {
          for (final String hour : hours) {
            for (final String minute : minutes) {
              for (final String second : seconds) {
                assertAgrees(year + "-" + month + "-" + day + "T" + hour + ":" + minute + ":" + second + "Z");
              }
            }
          }
        }
      }
    }
  }

  @Test
  void rfc3339ParsingAgreesWithTheFormatterOnEveryFractionAndOffset() {
    final String[] fractions = {"", ".1", ".12", ".123456789", ".1234567890", ".", ".000000000", ",5"};
    final String[] offsets = {"Z", "+00:00", "-00:00", "+05:30", "-02:00", "+18:00", "+18:01", "-17:59", "+23:59", "+0530", "+05", "z", "", "+5:30", "+05:60"};
    final String[] bases = {"2024-03-05T10:20:30", "1677-09-21T00:12:43", "2262-04-11T23:47:16", "1970-01-01T00:00:00", "9999-12-31T23:59:59"};
    for (final String base : bases) {
      for (final String fraction : fractions) {
        for (final String offset : offsets) {
          assertAgrees(base + fraction + offset);
        }
      }
    }
  }

  @Test
  void rfc3339ParsingAgreesWithTheFormatterOnMutatedInput() {
    final String valid = "2024-03-05T10:20:30.123456789+05:30";
    final String replacements = "0189:-+TtZz. \u0661\u00e9/";
    for (int i = 0; i <= valid.length(); i++) {
      if (i < valid.length()) {
        assertAgrees(valid.substring(0, i) + valid.substring(i + 1));
      }
      for (final char c : replacements.toCharArray()) {
        assertAgrees(valid.substring(0, i) + c + valid.substring(i));
        if (i < valid.length()) {
          assertAgrees(valid.substring(0, i) + c + valid.substring(i + 1));
        }
      }
    }
    for (final String edge : new String[] {"", " ", valid + " ", " " + valid, valid + "Z", valid.substring(0, 19), "2024-03-05", "T10:20:30Z"}) {
      assertAgrees(edge);
    }
  }

  private void assertAgrees(String value) {
    final String nano = outcome(() -> builtins.parseNsWithFormatter("RFC3339Nano", value));
    final String plain = outcome(() -> builtins.parseNsWithFormatter("RFC3339", value));

    assertEquals(nano, outcome(() -> builtins.parseRFC3339Ns(null, new RegoValue[] {new RegoString(value)})), value);
    assertEquals(
        plain, outcome(() -> builtins.parseNs(null, new RegoValue[] {new RegoString("RFC3339"), new RegoString(value)})), value);
  }

  private static String outcome(Supplier<RegoValue> call) {
    try {
      return exact(call.get());
    } catch (BuiltinError e) {
      return "error: " + e.getMessage();
    }
  }

  private static String exact(RegoValue result) {
    return ((RegoNumber) result).getBigIntValue().toString();
  }
}
