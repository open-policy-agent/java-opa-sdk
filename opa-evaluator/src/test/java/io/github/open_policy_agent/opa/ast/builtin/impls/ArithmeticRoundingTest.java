package io.github.open_policy_agent.opa.ast.builtin.impls;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

import io.github.open_policy_agent.opa.ast.types.RegoBigInt;
import io.github.open_policy_agent.opa.ast.types.RegoDecimal;
import io.github.open_policy_agent.opa.ast.types.RegoInt32;
import io.github.open_policy_agent.opa.ast.types.RegoNumber;
import io.github.open_policy_agent.opa.ast.types.RegoValue;
import java.math.BigInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Expected values come from {@code opa eval} (OPA 1.21.1). Rows with a ".5" at 2^63 hold in OPA too:
 * its 64-bit mantissa cannot keep the fraction there either.
 */
class ArithmeticRoundingTest {

  private final ArithmeticBuiltins builtins = new ArithmeticBuiltins();

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      value = {
        "-9223372036854775808|9223372036854775808",
        "9223372036854775807|9223372036854775807",
        "-9223372036854775807|9223372036854775807",
        "-9223372036854775809|9223372036854775809",
        "-2.5|2.5"
      })
  void absReturnsTheExactMagnitude(String input, String expected) {
    assertEquals(expected, show(builtins.abs_f(null, new RegoValue[] {number(input)})));
  }

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      value = {
        "-2.5|-3",
        "2.5|3",
        "-0.5|-1",
        "0.5|1",
        "1.5|2",
        "-1.5|-2",
        "0.49999999999999994|0",
        "-0.49999999999999994|0",
        "2.5000000000000004|3",
        "-2.5000000000000004|-3",
        "0.4|0",
        "0.49999999999999999999|1",
        "-0.4|0",
        "1.5e19|15000000000000000000",
        "9223372036854775807.5|9223372036854775808",
        "9007199254740993|9007199254740993"
      })
  void roundRoundsHalvesAwayFromZero(String input, String expected) {
    assertIntegerResult(expected, builtins.round_f(null, new RegoValue[] {number(input)}));
  }

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      value = {
        "1.2|2",
        "-1.2|-1",
        "0.0000001|1",
        "1e19|10000000000000000000",
        "-1e19|-10000000000000000000",
        "9223372036854775808.5|9223372036854775808"
      })
  void ceilReturnsTheExactIntegerBeyondLong(String input, String expected) {
    assertIntegerResult(expected, builtins.ceil_f(null, new RegoValue[] {number(input)}));
  }

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      value = {
        "1.8|1",
        "2.99999999999999999999|3",
        "-1.2|-2",
        "1e19|10000000000000000000",
        "-1e19|-10000000000000000000",
        "9223372036854775808.5|9223372036854775808"
      })
  void floorReturnsTheExactIntegerBeyondLong(String input, String expected) {
    assertIntegerResult(expected, builtins.floor_f(null, new RegoValue[] {number(input)}));
  }

  @Test
  void integerResultsAreInt32OnlyWhenTheyFit() {
    final RegoValue[] ok = {new RegoDecimal(2147483647.0)};
    final RegoValue[] over = {new RegoDecimal(2147483647.5)};
    final RegoValue[] under = {new RegoDecimal(-2147483648.5)};

    assertInstanceOf(RegoInt32.class, builtins.round_f(null, ok));
    assertEquals("2147483648", show(builtins.round_f(null, over)));
    assertInstanceOf(RegoBigInt.class, builtins.round_f(null, over));
    assertEquals("-2147483649", show(builtins.floor_f(null, under)));
    assertEquals("2147483648", show(builtins.ceil_f(null, new RegoValue[] {new RegoDecimal(2147483647.2)})));
  }

  @Test
  void absOfASmallIntegerStaysInt32() {
    assertInstanceOf(RegoInt32.class, builtins.abs_f(null, new RegoValue[] {RegoInt32.of(-5)}));
    assertEquals("5", show(builtins.abs_f(null, new RegoValue[] {RegoInt32.of(-5)})));
  }

  @Test
  void integersPassThroughRoundCeilAndFloor() {
    final RegoValue[] args = {RegoInt32.of(7)};

    assertSame(args[0], builtins.round_f(null, args));
    assertSame(args[0], builtins.ceil_f(null, args));
    assertSame(args[0], builtins.floor_f(null, args));
  }

  @Test
  void aNonFiniteDecimalIsReturnedAsIs() {
    final RegoValue infinity = new RegoDecimal(Double.POSITIVE_INFINITY);
    final RegoValue[] args = {infinity};

    assertSame(infinity, builtins.round_f(null, args));
    assertSame(infinity, builtins.ceil_f(null, args));
    assertSame(infinity, builtins.floor_f(null, args));
  }

  private static RegoValue number(String text) {
    try {
      if (text.contains(".") || text.contains("e")) {
        return new RegoDecimal(Double.parseDouble(text));
      }
      return new RegoBigInt(new BigInteger(text));
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("bad test number: " + text, e);
    }
  }

  private static String show(RegoValue value) {
    final RegoNumber number = (RegoNumber) value;
    return number.isDecimal() ? String.valueOf(number.getDecimalValue()) : number.getBigIntValue().toString();
  }

  private static void assertIntegerResult(String expected, RegoValue result) {
    assertFalse(((RegoNumber) result).isDecimal(), "the result must be an integer");
    assertEquals(expected, show(result));
  }
}
