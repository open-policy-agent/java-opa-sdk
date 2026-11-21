package io.github.open_policy_agent.opa.ast.builtin.impls;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.open_policy_agent.opa.ast.builtin.BuiltinError;
import io.github.open_policy_agent.opa.ast.types.RegoBigInt;
import io.github.open_policy_agent.opa.ast.types.RegoDecimal;
import io.github.open_policy_agent.opa.ast.types.RegoInt32;
import io.github.open_policy_agent.opa.ast.types.RegoString;
import io.github.open_policy_agent.opa.ast.types.RegoValue;
import io.github.open_policy_agent.opa.rego.EvaluationContext;
import io.github.open_policy_agent.opa.rego.TypeError;
import java.math.BigInteger;
import org.junit.jupiter.api.Test;

// Expected values follow OPA's v1/topdown/bits.go (math/big semantics).
class BitsBuiltinsTest {

  private final BitsBuiltins builtins = new BitsBuiltins();
  private final EvaluationContext ctx = new EvaluationContext.Builder().build();

  private static RegoValue[] args(RegoValue... values) {
    return values;
  }

  @Test
  void bitwiseOperations() {
    assertEquals(RegoInt32.of(1), builtins.and(ctx, args(RegoInt32.of(5), RegoInt32.of(3))));
    assertEquals(RegoInt32.of(7), builtins.or(ctx, args(RegoInt32.of(5), RegoInt32.of(3))));
    assertEquals(RegoInt32.of(6), builtins.xor(ctx, args(RegoInt32.of(5), RegoInt32.of(3))));
    assertEquals(RegoInt32.of(-43), builtins.negate(ctx, args(RegoInt32.of(42))));
    assertEquals(RegoInt32.of(-6), builtins.and(ctx, args(RegoInt32.of(-6), RegoInt32.of(-2))));
  }

  @Test
  void shifts() {
    assertEquals(RegoInt32.of(8), builtins.lsh(ctx, args(RegoInt32.of(1), RegoInt32.of(3))));
    assertEquals(RegoInt32.of(1), builtins.rsh(ctx, args(RegoInt32.of(8), RegoInt32.of(3))));
    assertEquals(RegoInt32.of(-1), builtins.rsh(ctx, args(RegoInt32.of(-7), RegoInt32.of(3))));
    assertEquals(
        new RegoBigInt(new BigInteger("18446744073709551614")),
        builtins.lsh(ctx, args(new RegoBigInt(Long.MAX_VALUE), RegoInt32.of(1))));
  }

  @Test
  void shiftsBeyondIntRange() {
    RegoValue huge = new RegoBigInt(BigInteger.ONE.shiftLeft(40));
    assertEquals(RegoInt32.of(0), builtins.rsh(ctx, args(RegoInt32.of(12345), huge)));
    assertEquals(RegoInt32.of(-1), builtins.rsh(ctx, args(RegoInt32.of(-12345), huge)));
    assertEquals(RegoInt32.of(0), builtins.lsh(ctx, args(RegoInt32.of(0), huge)));
    assertThrows(BuiltinError.class, () -> builtins.lsh(ctx, args(RegoInt32.of(1), huge)));
  }

  @Test
  void acceptsIntegralFloats() {
    assertEquals(RegoInt32.of(2), builtins.and(ctx, args(new RegoDecimal(2.0), RegoInt32.of(3))));
    assertEquals(RegoInt32.of(-6), builtins.negate(ctx, args(new RegoDecimal(5.0))));
    assertEquals(RegoInt32.of(4), builtins.lsh(ctx, args(RegoInt32.of(1), new RegoDecimal(2.0))));
  }

  @Test
  void rejectsFractionalOperands() {
    TypeError lhs = assertThrows(TypeError.class, () -> builtins.or(ctx, args(new RegoDecimal(1.5), RegoInt32.of(1))));
    assertTrue(lhs.getMessage().endsWith("operand 1 must be integer number but got floating-point number"), lhs.getMessage());

    TypeError shift = assertThrows(TypeError.class, () -> builtins.lsh(ctx, args(RegoInt32.of(1), new RegoDecimal(2.5))));
    assertTrue(shift.getMessage().endsWith("operand 2 must be integer number but got floating-point number"), shift.getMessage());
  }

  @Test
  void rejectsNegativeShift() {
    TypeError e = assertThrows(TypeError.class, () -> builtins.rsh(ctx, args(RegoInt32.of(1), RegoInt32.of(-1))));
    assertTrue(e.getMessage().endsWith("operand 2 must be an unsigned integer number but got a negative integer"), e.getMessage());
  }

  @Test
  void rejectsNonNumbers() {
    TypeError e = assertThrows(TypeError.class, () -> builtins.xor(ctx, args(RegoInt32.of(1), new RegoString("1"))));
    assertTrue(e.getMessage().endsWith("operand 2 must be integer but got string"), e.getMessage());
  }
}
