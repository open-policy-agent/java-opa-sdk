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

class ArithmeticDivisionTest {

  private final ArithmeticBuiltins builtins = new ArithmeticBuiltins();

  // Fractional divisors used to truncate to 0 in the zero check and fail with "divide by zero".
  @ParameterizedTest
  @CsvSource({"1, 0.5, 2.0", "1, -0.25, -4.0", "3, 0.999, 3.003003003003003"})
  void dividesByFractionalDivisor(int x, double y, double expected) {
    RegoValue result = builtins.div(null, new RegoValue[] {RegoInt32.of(x), new RegoDecimal(y)});
    assertEquals(expected, ((RegoNumber) result).getDecimalValue());
  }

  @ParameterizedTest
  @CsvSource({"0.0", "-0.0"})
  void rejectsZeroDecimalDivisor(double y) {
    assertDivideByZero(new RegoDecimal(y));
  }

  @ParameterizedTest
  @CsvSource({"0"})
  void rejectsZeroIntegerDivisor(int y) {
    assertDivideByZero(RegoInt32.of(y));
  }

  private void assertDivideByZero(RegoValue divisor) {
    BuiltinError e = assertThrows(BuiltinError.class, () -> builtins.div(null, new RegoValue[] {RegoInt32.of(1), divisor}));
    assertTrue(e.getMessage().endsWith("div: divide by zero"), e.getMessage());
  }
}
