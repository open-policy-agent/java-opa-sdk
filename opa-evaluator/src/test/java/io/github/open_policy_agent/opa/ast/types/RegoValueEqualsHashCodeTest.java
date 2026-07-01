package io.github.open_policy_agent.opa.ast.types;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Constructor;
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

  private static RegoSet setOf(RegoValue... values) {
    RegoSet set = new RegoSet(false);
    for (RegoValue value : values) {
      set.addValue(value);
    }
    return set;
  }
}
