package io.github.open_policy_agent.opa.ast.builtin.impls;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.open_policy_agent.opa.OpaException;
import io.github.open_policy_agent.opa.ast.builtin.BuiltinRegistry;
import io.github.open_policy_agent.opa.ast.types.RegoBigInt;
import io.github.open_policy_agent.opa.ast.types.RegoDecimal;
import io.github.open_policy_agent.opa.ast.types.RegoInt32;
import io.github.open_policy_agent.opa.ast.types.RegoString;
import io.github.open_policy_agent.opa.ast.types.RegoValue;
import java.math.BigInteger;
import org.junit.jupiter.api.Test;

class UnitsBuiltinsTest {
  private final UnitsBuiltins builtins = new UnitsBuiltins();

  @Test
  void registersUnitsBuiltins() {
    assertTrue(BuiltinRegistry.AllBuiltIns.containsKey("units.parse"));
    assertTrue(BuiltinRegistry.AllBuiltIns.containsKey("units.parse_bytes"));
  }

  @Test
  void parsesDecimalAndBinaryUnits() {
    assertEquals(new RegoBigInt(100_000L), parse("100K"));
    assertEquals(new RegoBigInt(102_400L), parse("100Ki"));
    assertEquals(new RegoDecimal(0.1), parse("100m"));
    assertEquals(new RegoBigInt(new BigInteger("11529215046068469760")), parse("10Ei"));
    assertEquals(new RegoBigInt(109_951_162_777_600L), parse("\"100TI\""));
  }

  @Test
  void parsesScientificNotation() {
    assertEquals(new RegoBigInt(10_000_000_000L), parse("1e10"));
    assertEquals(new RegoBigInt(2_500_000L), parse("2.5e3K"));
    assertEquals(new RegoDecimal(0.78), parse("7.8E-1"));
  }

  @Test
  void parsesByteUnitsWithOptionalByteSuffix() {
    assertEquals(new RegoBigInt(100_000_000L), parseBytes("100mb"));
    assertEquals(new RegoBigInt(104_857_600L), parseBytes("100MiB"));
    assertEquals(new RegoBigInt(335_544_320L), parseBytes("3.2E2MiB"));
    assertEquals(new RegoBigInt(109_951_162_777_600L), parseBytes("\"100TIB\""));
    assertEquals(RegoInt32.of(10), parseBytes("1e-2KB"));
    assertEquals(RegoInt32.of(1), parseBytes("1.9"));
    assertEquals(RegoInt32.of(-1), parseBytes("-1.9"));
    assertEquals(RegoInt32.of(0), parseBytes("0.9"));
  }

  @Test
  void reportsOpaCompatibleErrors() {
    assertError("units.parse: no amount provided", () -> parse(""));
    assertError("units.parse: spaces not allowed in resource strings", () -> parse("100 kb"));
    assertError(
        "units.parse: could not parse amount to a number",
        () -> parse("0.0.0"));
    assertError("units.parse: exponent too large", () -> parse("10e10000000Ei"));
    assertError("units.parse: exponent too large", () -> parse("1e0000001"));

    assertError("units.parse_bytes: no byte amount provided", () -> parseBytes("GB"));
    assertError(
        "units.parse_bytes: could not parse byte amount to a number",
        () -> parseBytes(".5.2"));
    assertError(
        "units.parse_bytes: byte unit xb not recognized", () -> parseBytes("1XB"));
  }

  private RegoValue parse(String input) {
    return builtins.parse(null, new RegoValue[] {new RegoString(input)});
  }

  private RegoValue parseBytes(String input) {
    return builtins.parseBytes(null, new RegoValue[] {new RegoString(input)});
  }

  private static void assertError(String expected, Runnable runnable) {
    OpaException error = assertThrows(OpaException.class, runnable::run);
    assertEquals("eval_builtin_error: " + expected, error.getMessage());
  }
}
