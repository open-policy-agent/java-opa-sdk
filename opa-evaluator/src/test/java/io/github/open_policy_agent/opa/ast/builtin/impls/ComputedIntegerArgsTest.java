package io.github.open_policy_agent.opa.ast.builtin.impls;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.open_policy_agent.opa.ast.types.RegoArray;
import io.github.open_policy_agent.opa.ast.types.RegoBigInt;
import io.github.open_policy_agent.opa.ast.types.RegoDecimal;
import io.github.open_policy_agent.opa.ast.types.RegoInt32;
import io.github.open_policy_agent.opa.ast.types.RegoString;
import io.github.open_policy_agent.opa.ast.types.RegoValue;
import io.github.open_policy_agent.opa.rego.EvaluationContext;
import io.github.open_policy_agent.opa.rego.TypeError;
import java.math.BigInteger;
import java.util.List;
import java.util.function.BiFunction;
import org.junit.jupiter.api.Test;

/** Integer arguments produced by arithmetic are not RegoInt32, but builtins must accept them. */
public class ComputedIntegerArgsTest {

  private final EvaluationContext ctx = new EvaluationContext.Builder().build();

  private RegoValue plus(int a, int b) {
    return call(ArithmeticBuiltins.builtins().get("plus"), RegoInt32.of(a), RegoInt32.of(b));
  }

  private RegoValue minus(int a, int b) {
    return call(ArithmeticBuiltins.builtins().get("minus"), RegoInt32.of(a), RegoInt32.of(b));
  }

  private RegoValue call(BiFunction<EvaluationContext, RegoValue[], RegoValue> fn, RegoValue... args) {
    return fn.apply(ctx, args);
  }

  @Test
  public void substringAcceptsComputedOffsetAndLength() {
    var substring = StringBuiltins.builtins().get("substring");

    assertEquals(new RegoString("ab"), call(substring, new RegoString("abc"), RegoInt32.of(0), plus(1, 1)));
    assertEquals(new RegoString("bc"), call(substring, new RegoString("abc"), minus(2, 1), RegoInt32.of(2)));
    assertEquals(new RegoString(""), call(substring, new RegoString(""), RegoInt32.of(0), minus(0, 0)));
  }

  @Test
  public void arraySliceAcceptsComputedStartAndStop() {
    var slice = ArrayBuiltins.builtins().get("array.slice");
    var arr = new RegoArray(List.of(RegoInt32.of(1), RegoInt32.of(2), RegoInt32.of(3)));

    assertEquals(new RegoArray(List.of(RegoInt32.of(1), RegoInt32.of(2))), call(slice, arr, RegoInt32.of(0), plus(1, 1)));
    assertEquals(new RegoArray(List.of(RegoInt32.of(2), RegoInt32.of(3))), call(slice, arr, minus(2, 1), RegoInt32.of(3)));
    assertEquals(new RegoArray(), call(slice, new RegoArray(), RegoInt32.of(0), minus(0, 0)));
  }

  @Test
  public void formatIntAcceptsComputedBase() {
    var formatInt = StringBuiltins.builtins().get("format_int");

    assertEquals(new RegoString("ff"), call(formatInt, RegoInt32.of(255), plus(8, 8)));
  }

  @Test
  public void fractionsAreStillRejected() {
    var substring = StringBuiltins.builtins().get("substring");

    assertThrows(TypeError.class, () -> call(substring, new RegoString("abc"), RegoInt32.of(0), new RegoDecimal(1.5)));
  }

  @Test
  public void integersBeyondIntRangeAreStillRejected() {
    var substring = StringBuiltins.builtins().get("substring");
    var tooBig = new RegoBigInt(BigInteger.ONE.shiftLeft(40));

    assertThrows(TypeError.class, () -> call(substring, new RegoString("abc"), RegoInt32.of(0), tooBig));
  }
}
