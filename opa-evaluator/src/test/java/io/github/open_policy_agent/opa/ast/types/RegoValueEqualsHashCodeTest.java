package io.github.open_policy_agent.opa.ast.types;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Constructor;
import java.math.BigInteger;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RegoValueEqualsHashCodeTest {

  @Test
  void equalRegoSetsHaveSameHashCode() {
    RegoSet first = setOf(RegoInt32.of(1), new RegoString("two"));
    RegoSet second = setOf(RegoInt32.of(1), new RegoString("two"));

    assertEquals(first, second);
    assertEquals(first.hashCode(), second.hashCode());
  }

  @Test
  void hashSetFindsEquivalentRegoSet() {
    Set<RegoSet> values = new HashSet<>();
    values.add(setOf(RegoInt32.of(1), new RegoString("two")));

    assertTrue(values.contains(setOf(RegoInt32.of(1), new RegoString("two"))));
  }

  @Test
  void regoNullEqualInstancesHaveSameHashCode() throws ReflectiveOperationException {
    Constructor<RegoNull> constructor = RegoNull.class.getDeclaredConstructor();
    constructor.setAccessible(true);
    RegoNull other = constructor.newInstance();

    assertEquals(RegoNull.INSTANCE, other);
    assertEquals(RegoNull.INSTANCE.hashCode(), other.hashCode());
  }

  @Test
  void regoUndefinedUsesIdentityHashCode() {
    assertFalse(RegoUndefined.INSTANCE.equals(RegoUndefined.INSTANCE));
    assertEquals(System.identityHashCode(RegoUndefined.INSTANCE), RegoUndefined.INSTANCE.hashCode());
  }

  @Test
  void wholeDecimalsHashLikeEqualIntegers() {
    BigInteger big = BigInteger.ONE.shiftLeft(70);
    RegoNumber[][] pairs = {
      {RegoInt32.of(1), new RegoDecimal(1.0)},
      {RegoInt32.of(-7), new RegoDecimal(-7.0)},
      {RegoInt32.of(0), new RegoDecimal(-0.0)},
      {new RegoBigInt(1L << 40), new RegoDecimal((double) (1L << 40))},
      {new RegoBigInt(-(1L << 40) - 4), new RegoDecimal((double) (-(1L << 40) - 4))},
      {new RegoBigInt(Long.MIN_VALUE), new RegoDecimal((double) Long.MIN_VALUE)},
      {new RegoBigInt(big), new RegoDecimal(big.doubleValue())},
      // Above 2^24, so float rounding would break equality.
      {RegoInt32.of(16777217), new RegoDecimal(16777217.0)},
      {new RegoBigInt(16777217L), new RegoDecimal(16777217.0)},
    };
    for (RegoNumber[] pair : pairs) {
      assertEquals(pair[0], pair[1]);
      assertEquals(pair[1], pair[0]);
      assertEquals(pair[0].hashCode(), pair[1].hashCode(), pair[0] + " vs " + pair[1]);
    }
  }

  @Test
  void bigIntsCompareExactlyWithDecimals() {
    // Each decimal is the nearest double to its int, so a double comparison would call them equal.
    BigInteger big = BigInteger.ONE.shiftLeft(70);
    RegoNumber[][] pairs = {
      {new RegoBigInt(9007199254740993L), new RegoDecimal(9007199254740992.0)},
      {new RegoBigInt(Long.MAX_VALUE), new RegoDecimal((double) Long.MAX_VALUE)},
      {new RegoBigInt(big.add(BigInteger.ONE)), new RegoDecimal(big.doubleValue())},
    };
    for (RegoNumber[] pair : pairs) {
      assertNotEquals(pair[0], pair[1]);
      assertNotEquals(pair[1], pair[0]);
    }
  }

  private static RegoSet setOf(RegoValue... values) {
    RegoSet set = new RegoSet(false);
    for (RegoValue value : values) {
      set.addValue(value);
    }
    return set;
  }
}
