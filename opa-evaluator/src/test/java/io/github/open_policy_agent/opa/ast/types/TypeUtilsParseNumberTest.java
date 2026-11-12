package io.github.open_policy_agent.opa.ast.types;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.open_policy_agent.opa.ast.builtin.BuiltinError;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** The type and value {@code parseStringToNumber} gives each plan number constant. */
class TypeUtilsParseNumberTest {

  @ParameterizedTest(name = "[{0}] -> {1} {2}")
  @CsvSource(
      delimiter = '|',
      value = {
        "0|RegoInt32|0|false",
        "42|RegoInt32|42|false",
        "-7|RegoInt32|-7|false",
        "+5|RegoInt32|5|false",
        "007|RegoInt32|7|false",
        "2147483647|RegoInt32|2147483647|false",
        "-2147483648|RegoInt32|-2147483648|false",
        "2147483648|RegoBigInt|2147483648|false",
        "-2147483649|RegoBigInt|-2147483649|false",
        "9223372036854775807|RegoBigInt|9223372036854775807|false",
        "9223372036854775808|RegoBigInt|9223372036854775808|false",
        "123456789012345678901234567890|RegoBigInt|123456789012345678901234567890|false",
        "3.0|RegoInt32|3|true",
        "-42.0|RegoInt32|-42|true",
        "1e3|RegoInt32|1000|true",
        "1E3|RegoInt32|1000|true",
        "1e+3|RegoInt32|1000|true",
        "5e0|RegoInt32|5|true",
        "1.|RegoInt32|1|true",
        "0.0|RegoInt32|0|true",
        "-0.0|RegoInt32|0|true",
        "1e-400|RegoInt32|0|true",
        "1e18|RegoBigInt|1000000000000000000|true",
        "1.5|RegoDecimal|1.5|true",
        "0.5|RegoDecimal|0.5|true",
        "-0.5|RegoDecimal|-0.5|true",
        ".5|RegoDecimal|0.5|true",
        "00.50|RegoDecimal|0.5|true",
        "9.3e18|RegoDecimal|9.3E18|true",
        "1.7976931348623157e308|RegoDecimal|1.7976931348623157E308|true",
        "1e400|RegoDecimal|1e400|true",
        "-1e400|RegoDecimal|-1e400|true",
        "٣|RegoInt32|3|false",
        "-0|RegoInt32|0|false",
        "+0|RegoInt32|0|false",
        "000000000000000000007|RegoInt32|7|false",
        "999999999999999999|RegoBigInt|999999999999999999|false",
        "-9223372036854775808|RegoBigInt|-9223372036854775808|false",
        "+000000000000000000007|RegoInt32|7|false",
        "-0000000000000000000|RegoInt32|0|false",
        "000000000000000000000|RegoInt32|0|false",
        "0000000000000002147483648|RegoBigInt|2147483648|false",
        "-99999999999999999|RegoBigInt|-99999999999999999|false",
        "-999999999999999999|RegoBigInt|-999999999999999999|false",
        "+999999999999999999|RegoBigInt|999999999999999999|false",
        "-100000000000000000|RegoBigInt|-100000000000000000|false"
      })
  void parsesToTheExpectedTypeAndValue(String text, String type, String value, boolean floatText) {
    final RegoNumber number = (RegoNumber) TypeUtils.parseStringToNumber(text);

    assertEquals(type, number.getClass().getSimpleName());
    assertEquals(value, number.toString());
    assertEquals(floatText, number.isFloatText());
  }

  @ParameterizedTest
  @ValueSource(strings = {"abc", "", "0x10", "1_000", "--5", "1e", "e5"})
  void textThatIsNotANumberIsRejected(String text) {
    assertThrows(BuiltinError.class, () -> TypeUtils.parseStringToNumber(text));
  }
}
