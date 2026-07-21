package io.github.open_policy_agent.opa.ast.builtin.impls;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import org.junit.jupiter.api.Test;
import io.github.open_policy_agent.opa.ast.builtin.impls.utils.SprintfUtil;
import io.github.open_policy_agent.opa.ast.types.RegoArray;
import io.github.open_policy_agent.opa.ast.types.RegoBigInt;
import io.github.open_policy_agent.opa.ast.types.RegoBoolean;
import io.github.open_policy_agent.opa.ast.types.RegoDecimal;
import io.github.open_policy_agent.opa.ast.types.RegoObject;
import io.github.open_policy_agent.opa.ast.types.RegoSet;
import io.github.open_policy_agent.opa.ast.types.RegoString;
import io.github.open_policy_agent.opa.ast.types.RegoValue;

/**
 * Unit tests for SprintfUtil based on Go's fmt.Sprintf behavior.
 *
 * <p>Reference: <a href="https://pkg.go.dev/fmt">...</a>
 */
public class SprintfUtilTest {

  // Helper methods
  private static String sprintf(String format, RegoValue... args) {
    return SprintfUtil.sprintf(format, args);
  }

  private static RegoString str(String s) {
    return new RegoString(s);
  }

  private static RegoBigInt num(int n) {
    return new RegoBigInt(BigInteger.valueOf(n));
  }

  private static RegoDecimal dec(double d) {
    return new RegoDecimal(d);
  }

  // Basic string formatting tests
  @Test
  public void testBasicString() {
    assertEquals("hello", sprintf("%s", str("hello")));
    assertEquals("hello world", sprintf("%s %s", str("hello"), str("world")));
  }

  @Test
  public void testStringWithWidth() {
    assertEquals("  hello", sprintf("%7s", str("hello")));
    assertEquals("hello  ", sprintf("%-7s", str("hello")));
  }

  @Test
  public void testStringWithPrecision() {
    assertEquals("hel", sprintf("%.3s", str("hello")));
    assertEquals("  hel", sprintf("%5.3s", str("hello")));
  }

  @Test
  public void testQuotedString() {
    assertEquals("\"hello\"", sprintf("%q", str("hello")));
    assertEquals("\"hello\\\"world\"", sprintf("%q", str("hello\"world")));
  }

  // Integer formatting tests
  @Test
  public void testBasicInteger() {
    assertEquals("42", sprintf("%d", num(42)));
    assertEquals("-42", sprintf("%d", num(-42)));
  }

  @Test
  public void testIntegerWithSign() {
    assertEquals("+42", sprintf("%+d", num(42)));
    assertEquals("-42", sprintf("%+d", num(-42)));
    assertEquals(" 42", sprintf("% d", num(42)));
  }

  @Test
  public void testIntegerWithWidth() {
    assertEquals("   42", sprintf("%5d", num(42)));
    assertEquals("00042", sprintf("%05d", num(42)));
    assertEquals("42   ", sprintf("%-5d", num(42)));
  }

  @Test
  public void testIntegerBinary() {
    assertEquals("101010", sprintf("%b", num(42)));
    assertEquals("0b101010", sprintf("%#b", num(42)));
  }

  @Test
  public void testIntegerOctal() {
    assertEquals("52", sprintf("%o", num(42)));
    assertEquals("052", sprintf("%#o", num(42)));
    assertEquals("0o52", sprintf("%O", num(42)));
  }

  @Test
  public void testIntegerHex() {
    assertEquals("2a", sprintf("%x", num(42)));
    assertEquals("FF", sprintf("%X", num(255)));
    assertEquals("0xff", sprintf("%#x", num(255)));
    assertEquals("0XFF", sprintf("%#X", num(255)));
  }

  // Float formatting tests
  @Test
  public void testBasicFloat() {
    assertEquals("3.14", sprintf("%g", dec(3.14)));
    assertEquals("3.140000", sprintf("%f", dec(3.14)));
  }

  @Test
  public void testFloatWithPrecision() {
    assertEquals("3.14", sprintf("%.2f", dec(3.14159)));
    assertEquals("3.142", sprintf("%.3f", dec(3.14159)));
  }

  @Test
  public void testFloatScientific() {
    assertEquals("3.140000e+00", sprintf("%e", dec(3.14)));
    assertEquals("3.140000E+00", sprintf("%E", dec(3.14)));
  }

  @Test
  public void testFloatWithSign() {
    assertEquals("+3.14", sprintf("%+g", dec(3.14)));
    assertEquals(" 3.14", sprintf("% g", dec(3.14)));
  }

  @Test
  public void testFloatInfinity() {
    assertEquals("+Inf", sprintf("%v", new RegoDecimal(Double.POSITIVE_INFINITY)));
    assertEquals("-Inf", sprintf("%v", new RegoDecimal(Double.NEGATIVE_INFINITY)));
  }

  @Test
  public void testFloatNaN() {
    assertEquals("NaN", sprintf("%v", new RegoDecimal(Double.NaN)));
  }

  // Very large number that becomes infinity
  @Test
  public void testFloatOverflow() {
    // 2e308 exceeds Double.MAX_VALUE and becomes infinity
    // When stored with original string, should preserve the original
    RegoDecimal largeNum = new RegoDecimal(Double.POSITIVE_INFINITY, "2e308");
    assertEquals("2e308", sprintf("%v", largeNum));
  }

  // Boolean formatting tests
  @Test
  public void testBoolean() {
    assertEquals("true", sprintf("%v", RegoBoolean.TRUE));
    assertEquals("false", sprintf("%v", RegoBoolean.FALSE));
  }

  // Type formatting tests
  @Test
  public void testTypeVerb() {
    assertEquals("string", sprintf("%T", str("hello")));
    assertEquals("int", sprintf("%T", num(42)));
    assertEquals("float64", sprintf("%T", dec(3.14)));
  }

  // General value formatting tests
  @Test
  public void testGeneralValue() {
    assertEquals("hello", sprintf("%v", str("hello")));
    assertEquals("42", sprintf("%v", num(42)));
    assertEquals("3.14", sprintf("%v", dec(3.14)));
  }

  @Test
  public void testSharpV() {
    assertEquals("\"hello\"", sprintf("%#v", str("hello")));
  }

  // Array/Set formatting tests
  @Test
  public void testArrayFormatting() {
    RegoArray arr = new RegoArray();
    arr.addValue(num(1));
    arr.addValue(num(2));
    arr.addValue(num(3));
    assertEquals("[1, 2, 3]", sprintf("%v", arr));
  }

  @Test
  public void testSetFormatting() {
    RegoSet set = new RegoSet(false);
    set.addValue(num(1));
    set.addValue(num(2));
    assertEquals("{1, 2}", sprintf("%v", set));
  }

  @Test
  public void testObjectFormatting() {
    RegoObject obj = new RegoObject();
    obj.setProp(str("a"), num(1));
    obj.setProp(str("b"), num(2));
    String result = sprintf("%v", obj);
    // Object order may vary, so just check it's formatted correctly
    assertTrue(result.contains("\"a\": 1"));
    assertTrue(result.contains("\"b\": 2"));
  }

  // Multiple arguments tests
  @Test
  public void testMultipleArgs() {
    assertEquals("hello 42", sprintf("%s %d", str("hello"), num(42)));
    assertEquals("1 2 3", sprintf("%d %d %d", num(1), num(2), num(3)));
  }

  // Argument indexing tests
  @Test
  public void testArgIndexing() {
    assertEquals("2 1", sprintf("%[2]d %[1]d", num(1), num(2)));
    assertEquals("3 1 2", sprintf("%[3]d %[1]d %[2]d", num(1), num(2), num(3)));
  }

  // Width from argument tests
  @Test
  public void testWidthFromArg() {
    assertEquals("  hello", sprintf("%*s", num(7), str("hello")));
    assertEquals("hello  ", sprintf("%*s", num(-7), str("hello")));
  }

  // Precision from argument tests
  @Test
  public void testPrecisionFromArg() {
    assertEquals("3.14", sprintf("%.*f", num(2), dec(3.14159)));
  }

  // Special cases
  @Test
  public void testPercent() {
    assertEquals("%", sprintf("%%"));
    assertEquals("100%", sprintf("100%%"));
  }

  @Test
  public void testNull() {
    assertEquals("<nil>", sprintf("%v", (RegoValue) null));
  }

  // Error cases - missing arguments
  @Test
  public void testMissingArgument() {
    assertEquals("%!d(MISSING)", sprintf("%d"));
  }

  // Error cases - bad verb
  @Test
  public void testBadVerb() {
    String result = sprintf("%p", num(42));
    assertTrue(result.contains("%!p"));
  }

  // Error cases - extra arguments
  @Test
  public void testExtraArguments() {
    String result = sprintf("%d", num(1), num(2), num(3));
    assertTrue(result.contains("EXTRA"));
  }

  // Unicode tests
  @Test
  public void testUnicodeCodePoint() {
    assertEquals("U+0041", sprintf("%U", num(65))); // 'A'
    assertEquals("U+0041 'A'", sprintf("%#U", num(65)));
  }

  @Test
  public void testCharacter() {
    assertEquals("A", sprintf("%c", num(65)));
    assertEquals("'A'", sprintf("%q", num(65)));
  }

  // Hex string tests
  @Test
  public void testHexString() {
    assertEquals("68656c6c6f", sprintf("%x", str("hello")));
    assertEquals("68656C6C6F", sprintf("%X", str("hello")));
    assertEquals("0x68656c6c6f", sprintf("%#x", str("hello")));
  }

  @Test
  public void testHexStringWithSpaces() {
    assertEquals("68 65 6c 6c 6f", sprintf("% x", str("hello")));
    assertEquals("0x68 0x65 0x6c 0x6c 0x6f", sprintf("%# x", str("hello")));
  }

  // BigInteger tests
  @Test
  public void testBigInteger() {
    RegoBigInt big = new RegoBigInt(new BigInteger("123456789012345"));
    assertEquals("123456789012345", sprintf("%d", big));
    assertEquals("7048860DDF79", sprintf("%X", big));
  }

  // Complex formatting combinations
  @Test
  public void testComplexFormatting() {
    assertEquals("The answer is 42", sprintf("The answer is %d", num(42)));
    assertEquals("Pi is approximately 3.14", sprintf("Pi is approximately %.2f", dec(3.14159)));
    assertEquals("Hello, World!", sprintf("Hello, %s!", str("World")));
  }

  @Test
  public void testMixedTypes() {
    assertEquals(
        "string: hello, int: 42, float: 3.14",
        sprintf("string: %s, int: %d, float: %.2f", str("hello"), num(42), dec(3.14)));
  }
}
