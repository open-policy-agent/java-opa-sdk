package io.github.open_policy_agent.opa.ast.types;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class RegoValueCompareToTest {

  static Stream<Arguments> numericLessThanPairs() {
    return Stream.of(
        Arguments.of(RegoInt32.of(3), RegoInt32.of(10), "Int32(3) < Int32(10)"),
        Arguments.of(new RegoBigInt(3L), new RegoBigInt(10L), "BigInt(3) < BigInt(10)"),
        Arguments.of(new RegoDecimal(0.5), new RegoDecimal(1.5), "Decimal(0.5) < Decimal(1.5)"),
        Arguments.of(RegoInt32.of(9), new RegoBigInt(10L), "Int32(9) < BigInt(10)"),
        Arguments.of(new RegoBigInt(3L), RegoInt32.of(10), "BigInt(3) < Int32(10)"),
        Arguments.of(RegoInt32.of(1), new RegoDecimal(1.5), "Int32(1) < Decimal(1.5)"),
        Arguments.of(new RegoDecimal(0.5), RegoInt32.of(1), "Decimal(0.5) < Int32(1)"),
        Arguments.of(new RegoBigInt(2L), new RegoDecimal(2.5), "BigInt(2) < Decimal(2.5)"),
        Arguments.of(new RegoDecimal(1.5), new RegoBigInt(2L), "Decimal(1.5) < BigInt(2)")
    );
  }

  @ParameterizedTest(name = "{2}")
  @MethodSource("numericLessThanPairs")
  void numericLessThan(RegoValue smaller, RegoValue larger, String label) {
    assertTrue(smaller.compareTo(larger) < 0,
        label + ": expected negative, got " + smaller.compareTo(larger));
    assertTrue(larger.compareTo(smaller) > 0,
        label + " (reversed): expected positive, got " + larger.compareTo(smaller));
  }

  static Stream<Arguments> numericEqualPairs() {
    return Stream.of(
        // Same type
        Arguments.of(RegoInt32.of(5), RegoInt32.of(5), "Int32(5) == Int32(5)"),
        Arguments.of(new RegoBigInt(5L), new RegoBigInt(5L), "BigInt(5) == BigInt(5)"),
        Arguments.of(new RegoDecimal(2.5), new RegoDecimal(2.5), "Decimal(2.5) == Decimal(2.5)"),
        Arguments.of(RegoInt32.of(7), new RegoBigInt(7L), "Int32(7) == BigInt(7)"),
        Arguments.of(new RegoBigInt(7L), RegoInt32.of(7), "BigInt(7) == Int32(7)"),
        Arguments.of(RegoInt32.of(3), new RegoDecimal(3.0), "Int32(3) == Decimal(3.0)"),
        Arguments.of(new RegoDecimal(3.0), RegoInt32.of(3), "Decimal(3.0) == Int32(3)"),
        Arguments.of(new RegoBigInt(4L), new RegoDecimal(4.0), "BigInt(4) == Decimal(4.0)"),
        Arguments.of(new RegoDecimal(4.0), new RegoBigInt(4L), "Decimal(4.0) == BigInt(4)")
    );
  }

  @ParameterizedTest(name = "{2}")
  @MethodSource("numericEqualPairs")
  void numericEqual(RegoValue a, RegoValue b, String label) {
    assertEquals(0, a.compareTo(b), label);
  }

  static Stream<Arguments> sameTypeLessThanPairs() {
    return Stream.of(
        Arguments.of(new RegoString("abc"), new RegoString("xyz"), "String(abc) < String(xyz)"),
        Arguments.of(new RegoString("a"), new RegoString("b"), "String(a) < String(b)"),
        Arguments.of(RegoBoolean.FALSE, RegoBoolean.TRUE, "Boolean(false) < Boolean(true)"),
        Arguments.of(arrayOf(RegoInt32.of(1)), arrayOf(RegoInt32.of(2)), "Array([1]) < Array([2])"),
        Arguments.of(arrayOf(RegoInt32.of(1)), arrayOf(RegoInt32.of(1), RegoInt32.of(2)),
            "Array([1]) < Array([1,2])")
    );
  }

  @ParameterizedTest(name = "{2}")
  @MethodSource("sameTypeLessThanPairs")
  void sameTypeLessThan(RegoValue smaller, RegoValue larger, String label) {
    assertTrue(smaller.compareTo(larger) < 0,
        label + ": expected negative, got " + smaller.compareTo(larger));
    assertTrue(larger.compareTo(smaller) > 0,
        label + " (reversed): expected positive, got " + larger.compareTo(smaller));
  }

  static Stream<Arguments> sameTypeEqualPairs() {
    return Stream.of(
        Arguments.of(new RegoString("hello"), new RegoString("hello"), "String(hello) == String(hello)"),
        Arguments.of(RegoBoolean.TRUE, RegoBoolean.TRUE, "Boolean(true) == Boolean(true)"),
        Arguments.of(RegoBoolean.FALSE, RegoBoolean.FALSE, "Boolean(false) == Boolean(false)"),
        Arguments.of(RegoNull.INSTANCE, RegoNull.INSTANCE, "Null == Null"),
        Arguments.of(arrayOf(RegoInt32.of(1), RegoInt32.of(2)),
            arrayOf(RegoInt32.of(1), RegoInt32.of(2)), "Array([1,2]) == Array([1,2])")
    );
  }

  @ParameterizedTest(name = "{2}")
  @MethodSource("sameTypeEqualPairs")
  void sameTypeEqual(RegoValue a, RegoValue b, String label) {
    assertEquals(0, a.compareTo(b), label);
  }

  // Rego orders types: null < boolean < number < string < array < object < set
  static Stream<Arguments> crossTypeOrderingPairs() {
    return Stream.of(
        Arguments.of(RegoNull.INSTANCE, RegoBoolean.FALSE, "Null < Boolean"),
        Arguments.of(RegoBoolean.TRUE, RegoInt32.of(1), "Boolean < Number"),
        Arguments.of(RegoInt32.of(1), new RegoString("a"), "Number < String"),
        Arguments.of(new RegoString("a"), arrayOf(RegoInt32.of(1)), "String < Array"),
        Arguments.of(arrayOf(RegoInt32.of(1)), new RegoObject(), "Array < Object"),
        Arguments.of(new RegoObject(), new RegoSet(false), "Object < Set"),
        Arguments.of(RegoNull.INSTANCE, new RegoSet(false), "Null < Set"),
        Arguments.of(new RegoBigInt(100L), arrayOf(), "BigInt < empty Array")
    );
  }

  @ParameterizedTest(name = "{2}")
  @MethodSource("crossTypeOrderingPairs")
  void crossTypeOrdering(RegoValue smaller, RegoValue larger, String label) {
    assertTrue(smaller.compareTo(larger) < 0,
        label + ": expected negative, got " + smaller.compareTo(larger));
    assertTrue(larger.compareTo(smaller) > 0,
        label + " (reversed): expected positive, got " + larger.compareTo(smaller));
  }

  static Stream<Arguments> allTypes() {
    return Stream.of(
        Arguments.of(RegoInt32.of(1), "Int32"),
        Arguments.of(new RegoBigInt(1L), "BigInt"),
        Arguments.of(new RegoDecimal(1.0), "Decimal"),
        Arguments.of(new RegoString("a"), "String"),
        Arguments.of(RegoBoolean.TRUE, "Boolean"),
        Arguments.of(RegoNull.INSTANCE, "Null"),
        Arguments.of(arrayOf(RegoInt32.of(1)), "Array"),
        Arguments.of(new RegoObject(), "Object"),
        Arguments.of(new RegoSet(false), "Set")
    );
  }

  @ParameterizedTest(name = "{1}.compareTo(null) > 0")
  @MethodSource("allTypes")
  void compareToNullIsPositive(RegoValue value) {
    assertTrue(value.compareTo(null) > 0);
  }

  @ParameterizedTest(name = "{1}.compareTo(self) == 0")
  @MethodSource("allTypes")
  void compareToSelfIsZero(RegoValue value) {
    assertEquals(0, value.compareTo(value));
  }

  private static RegoArray arrayOf(RegoValue... values) {
    RegoArray array = new RegoArray(values.length);
    for (RegoValue v : values) {
      array.addValue(v);
    }
    return array;
  }
}
