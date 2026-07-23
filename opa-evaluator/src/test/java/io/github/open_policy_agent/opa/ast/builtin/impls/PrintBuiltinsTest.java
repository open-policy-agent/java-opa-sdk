package io.github.open_policy_agent.opa.ast.builtin.impls;

import static org.junit.jupiter.api.Assertions.*;

import io.github.open_policy_agent.opa.ast.builtin.BuiltinError;
import io.github.open_policy_agent.opa.ast.types.RegoArray;
import io.github.open_policy_agent.opa.ast.types.RegoBigInt;
import io.github.open_policy_agent.opa.ast.types.RegoBoolean;
import io.github.open_policy_agent.opa.ast.types.RegoDecimal;
import io.github.open_policy_agent.opa.ast.types.RegoNull;
import io.github.open_policy_agent.opa.ast.types.RegoObject;
import io.github.open_policy_agent.opa.ast.types.RegoSet;
import io.github.open_policy_agent.opa.ast.types.RegoString;
import io.github.open_policy_agent.opa.ast.types.RegoValue;
import io.github.open_policy_agent.opa.logging.Logger;
import io.github.open_policy_agent.opa.rego.EvaluationContext;
import io.github.open_policy_agent.opa.rego.PrintHook;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

public class PrintBuiltinsTest {

  private final PrintBuiltins printBuiltins = new PrintBuiltins();

  /** Collecting PrintHook that stores all printed messages. */
  private static class CollectingHook implements PrintHook {
    final List<String> messages = new ArrayList<>();

    @Override
    public void print(String message) {
      messages.add(message);
    }
  }

  private EvaluationContext ctxWithHook(PrintHook hook) {
    return new EvaluationContext.Builder().withPrintHook(hook).build();
  }

  /** Wrap values in a set (simulates compiler rewrite of print args). */
  private RegoSet setOf(RegoValue... values) {
    RegoSet set = new RegoSet(false);
    for (RegoValue v : values) {
      set.addValue(v);
    }
    return set;
  }

  /** Build the operand array: internal.print([set1, set2, ...]). */
  private RegoValue[] operands(RegoValue... sets) {
    RegoArray arr = new RegoArray();
    for (RegoValue s : sets) {
      arr.addValue(s);
    }
    return new RegoValue[] {arr};
  }

  // --- Default hook (stderr) ---

  @Test
  public void testDefaultHookSucceeds() {
    EvaluationContext ctx = new EvaluationContext.Builder().build();
    RegoValue result =
        printBuiltins.internalPrint(ctx, operands(setOf(new RegoString("hello"))));
    assertEquals(RegoBoolean.TRUE, result);
  }

  // --- Basic value formatting ---

  @Test
  public void testPrintSingleString() {
    CollectingHook hook = new CollectingHook();
    printBuiltins.internalPrint(ctxWithHook(hook), operands(setOf(new RegoString("hello"))));
    assertEquals(List.of("hello"), hook.messages);
  }

  @Test
  public void testPrintStringWithoutQuotes() {
    CollectingHook hook = new CollectingHook();
    printBuiltins.internalPrint(
        ctxWithHook(hook), operands(setOf(new RegoString("hello world"))));
    assertEquals(List.of("hello world"), hook.messages);
  }

  @Test
  public void testPrintNumber() {
    CollectingHook hook = new CollectingHook();
    printBuiltins.internalPrint(
        ctxWithHook(hook), operands(setOf(new RegoBigInt(BigInteger.valueOf(42)))));
    assertEquals(List.of("42"), hook.messages);
  }

  @Test
  public void testPrintBoolean() {
    CollectingHook hook = new CollectingHook();
    printBuiltins.internalPrint(ctxWithHook(hook), operands(setOf(RegoBoolean.TRUE)));
    assertEquals(List.of("true"), hook.messages);
  }

  @Test
  public void testPrintBooleanFalse() {
    CollectingHook hook = new CollectingHook();
    printBuiltins.internalPrint(ctxWithHook(hook), operands(setOf(RegoBoolean.FALSE)));
    assertEquals(List.of("false"), hook.messages);
  }

  @Test
  public void testPrintNull() {
    CollectingHook hook = new CollectingHook();
    printBuiltins.internalPrint(ctxWithHook(hook), operands(setOf(RegoNull.INSTANCE)));
    assertEquals(List.of("null"), hook.messages);
  }

  // --- Multiple arguments joined by space ---

  @Test
  public void testMultipleArgsJoinedBySpace() {
    CollectingHook hook = new CollectingHook();
    printBuiltins.internalPrint(
        ctxWithHook(hook),
        operands(
            setOf(new RegoString("x =")),
            setOf(new RegoBigInt(BigInteger.valueOf(10)))));
    assertEquals(List.of("x = 10"), hook.messages);
  }

  @Test
  public void testThreeArgs() {
    CollectingHook hook = new CollectingHook();
    printBuiltins.internalPrint(
        ctxWithHook(hook),
        operands(
            setOf(new RegoString("a")),
            setOf(new RegoString("b")),
            setOf(new RegoString("c"))));
    assertEquals(List.of("a b c"), hook.messages);
  }

  // --- Undefined (empty set) ---

  @Test
  public void testUndefinedRendersPlaceholder() {
    CollectingHook hook = new CollectingHook();
    RegoSet emptySet = new RegoSet(false);
    printBuiltins.internalPrint(ctxWithHook(hook), operands(emptySet));
    assertEquals(List.of("<undefined>"), hook.messages);
  }

  @Test
  public void testMixedDefinedAndUndefined() {
    CollectingHook hook = new CollectingHook();
    printBuiltins.internalPrint(
        ctxWithHook(hook),
        operands(
            setOf(new RegoString("value:")),
            new RegoSet(false)));
    assertEquals(List.of("value: <undefined>"), hook.messages);
  }

  // --- Cross-product ---

  @Test
  public void testCrossProductMultiValueSets() {
    CollectingHook hook = new CollectingHook();
    printBuiltins.internalPrint(
        ctxWithHook(hook),
        operands(
            setOf(new RegoString("a"), new RegoString("b")),
            setOf(new RegoString("1"))));
    assertEquals(2, hook.messages.size());
    assertTrue(hook.messages.contains("a 1"));
    assertTrue(hook.messages.contains("b 1"));
  }

  @Test
  public void testCrossProductBothMultiValue() {
    CollectingHook hook = new CollectingHook();
    printBuiltins.internalPrint(
        ctxWithHook(hook),
        operands(
            setOf(new RegoString("x"), new RegoString("y")),
            setOf(new RegoString("1"), new RegoString("2"))));
    assertEquals(4, hook.messages.size());
    assertTrue(hook.messages.contains("x 1"));
    assertTrue(hook.messages.contains("x 2"));
    assertTrue(hook.messages.contains("y 1"));
    assertTrue(hook.messages.contains("y 2"));
  }

  // --- Complex types ---

  @Test
  public void testPrintArray() {
    CollectingHook hook = new CollectingHook();
    RegoArray arr = new RegoArray();
    arr.addValue(new RegoBigInt(BigInteger.valueOf(1)));
    arr.addValue(new RegoBigInt(BigInteger.valueOf(2)));
    arr.addValue(new RegoBigInt(BigInteger.valueOf(3)));
    printBuiltins.internalPrint(ctxWithHook(hook), operands(setOf(arr)));
    assertEquals(1, hook.messages.size());
    assertEquals("[1, 2, 3]", hook.messages.get(0));
  }

  @Test
  public void testPrintObject() {
    CollectingHook hook = new CollectingHook();
    Map<RegoValue, RegoValue> props = new LinkedHashMap<>();
    props.put(new RegoString("key"), new RegoString("val"));
    RegoObject obj = new RegoObject(props);
    printBuiltins.internalPrint(ctxWithHook(hook), operands(setOf(obj)));
    assertEquals(List.of("{\"key\":\"val\"}"), hook.messages);
  }

  @Test
  public void testPrintDecimal() {
    CollectingHook hook = new CollectingHook();
    printBuiltins.internalPrint(
        ctxWithHook(hook), operands(setOf(new RegoDecimal(3.14))));
    assertEquals(1, hook.messages.size());
    assertEquals("3.14", hook.messages.get(0));
  }

  // --- Edge cases ---

  @Test
  public void testNullArgs() {
    CollectingHook hook = new CollectingHook();
    RegoValue result = printBuiltins.internalPrint(ctxWithHook(hook), null);
    assertEquals(RegoBoolean.TRUE, result);
    assertTrue(hook.messages.isEmpty());
  }

  @Test
  public void testEmptyArgs() {
    CollectingHook hook = new CollectingHook();
    RegoValue result = printBuiltins.internalPrint(ctxWithHook(hook), new RegoValue[0]);
    assertEquals(RegoBoolean.TRUE, result);
    assertTrue(hook.messages.isEmpty());
  }

  @Test
  public void testEmptyArray() {
    CollectingHook hook = new CollectingHook();
    RegoValue result = printBuiltins.internalPrint(ctxWithHook(hook), operands());
    assertEquals(RegoBoolean.TRUE, result);
    assertTrue(hook.messages.isEmpty());
  }

  @Test
  public void testNonArrayArgSucceeds() {
    CollectingHook hook = new CollectingHook();
    RegoValue result =
        printBuiltins.internalPrint(
            ctxWithHook(hook), new RegoValue[] {new RegoString("not an array")});
    assertEquals(RegoBoolean.TRUE, result);
    assertTrue(hook.messages.isEmpty());
  }

  @Test
  public void testAlwaysReturnsTrue() {
    CollectingHook hook = new CollectingHook();
    RegoValue result =
        printBuiltins.internalPrint(
            ctxWithHook(hook), operands(setOf(new RegoString("test"))));
    assertSame(RegoBoolean.TRUE, result);
  }

  @Test
  public void testHookExceptionDoesNotPropagate() {
    PrintHook failingHook = msg -> { throw new RuntimeException("write failed"); };
    RegoValue result =
        printBuiltins.internalPrint(
            ctxWithHook(failingHook), operands(setOf(new RegoString("boom"))));
    assertEquals(RegoBoolean.TRUE, result);
  }

  @Test
  public void testHookExceptionPropagatesInStrictMode() {
    PrintHook failingHook = msg -> { throw new RuntimeException("write failed"); };
    EvaluationContext strictCtx =
        new EvaluationContext.Builder()
            .withPrintHook(failingHook)
            .withStrictBuiltinErrors()
            .build();
    assertThrows(
        BuiltinError.class,
        () -> printBuiltins.internalPrint(strictCtx, operands(setOf(new RegoString("boom")))));
  }

  // --- PrintHook factory methods ---

  @Test
  public void testPrintHookOf() throws Exception {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    PrintHook hook = PrintHook.of(baos);
    hook.print("hello");
    String output = baos.toString();
    assertTrue(output.contains("hello"));
    assertTrue(output.endsWith(System.lineSeparator()));
  }

  @Test
  public void testPrintHookLogger() throws Exception {
    // Just verify it can be created and called without error
    PrintHook hook = PrintHook.logger(new Logger.StandardLogger());
    assertNotNull(hook);
  }
}
