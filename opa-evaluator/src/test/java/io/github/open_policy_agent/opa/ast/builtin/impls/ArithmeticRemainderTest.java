package io.github.open_policy_agent.opa.ast.builtin.impls;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.open_policy_agent.opa.ast.builtin.BuiltinError;
import io.github.open_policy_agent.opa.ast.types.RegoDecimal;
import io.github.open_policy_agent.opa.ast.types.RegoInt32;
import io.github.open_policy_agent.opa.ast.types.RegoNumber;
import io.github.open_policy_agent.opa.ast.types.RegoValue;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Expected behavior follows OPA's builtinRem, which rejects non-integers before checking for zero. */
class ArithmeticRemainderTest {

  private final ArithmeticBuiltins builtins = new ArithmeticBuiltins();

  // Fractional operands used to truncate to 0 in the zero check and fail with "modulo by zero".
  @ParameterizedTest
  @CsvSource({"5, 0.5", "5.5, 0", "1.1, 1", "-0.5, 3"})
  void rejectsFractionalOperands(String x, String y) {
    assertRemError("modulo on floating-point number", number(x), number(y));
  }

  // OPA converts whole-valued floats to integers, so a RegoDecimal such as the result of 2.5 * 2 still works.
  @ParameterizedTest
  @CsvSource({"5.0, 3, 2", "7, 5.0, 2", "-7.0, 5, -2", "1e20, 7, 2"})
  void acceptsWholeDecimalOperands(String x, String y, String expected) {
    RegoValue result = builtins.rem(null, new RegoValue[] {number(x), number(y)});
    assertEquals(expected, ((RegoNumber) result).getBigIntValue().toString());
  }

  @ParameterizedTest
  @CsvSource({"7, 0", "7, 0.0", "7, -0.0", "7.0, 0"})
  void rejectsZeroDivisor(String x, String y) {
    assertRemError("modulo by zero", number(x), number(y));
  }

  private void assertRemError(String expected, RegoValue x, RegoValue y) {
    BuiltinError e = assertThrows(BuiltinError.class, () -> builtins.rem(null, new RegoValue[] {x, y}));
    assertTrue(e.getMessage().endsWith(expected), e.getMessage());
  }

  // Builds RegoDecimal directly for any text with a fraction or exponent, the way arithmetic results arrive.
  private static RegoValue number(String text) {
    try {
      if (text.contains(".") || text.contains("e")) {
        return new RegoDecimal(Double.parseDouble(text));
      }
      return RegoInt32.of(Integer.parseInt(text));
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("bad test number: " + text, e);
    }
  }
}
