package io.github.open_policy_agent.opa.tracing;

import io.github.open_policy_agent.opa.ir.Location;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RangeTest {

  @Test
  void of_derivesStartAndEndFromLocation() {
    Location loc = new Location(0, 2, 5, 14, 5); // file, col, row, endCol, endRow
    Range range = Range.of(loc);

    assertEquals(5, range.start().getRow());
    assertEquals(2, range.start().getCol());
    assertEquals(5, range.end().getRow());
    assertEquals(14, range.end().getCol());
  }

  @Test
  void contains_requiresFullContainment() {
    Range outer = new Range(5, 1, 5, 20);

    assertTrue(outer.contains(new Range(5, 4, 5, 10)));
    assertTrue(outer.contains(outer));
    // Same row but the column span extends past the outer range's end column.
    assertFalse(outer.contains(new Range(5, 4, 5, 24)));
    // Same row but starts before the outer range's start column.
    assertFalse(new Range(5, 5, 5, 20).contains(new Range(5, 2, 5, 10)));
  }

  @Test
  void compareTo_ordersByStartThenEnd() {
    Range a = new Range(5, 1, 5, 10);
    Range b = new Range(5, 2, 5, 10);
    Range c = new Range(6, 1, 6, 10);

    assertTrue(a.compareTo(b) < 0);
    assertTrue(b.compareTo(c) < 0);
    assertEquals(0, a.compareTo(new Range(5, 1, 5, 10)));
  }

  @Test
  void equalsAndHashCode_useAllFourCoordinates() {
    Range a = new Range(5, 1, 5, 10);
    Range same = new Range(5, 1, 5, 10);
    Range different = new Range(5, 1, 5, 11);

    assertEquals(a, same);
    assertEquals(a.hashCode(), same.hashCode());
    assertNotEquals(a, different);
  }
}
