package io.github.open_policy_agent.opa.ast.builtin.impls;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Go duration parsing")
class GoDurationTest {

  @Test
  void parsesGoDurations() {
    assertEquals(0, GoDuration.parseNanos("0"));
    assertEquals(300_000_000L, GoDuration.parseNanos("300ms"));
    assertEquals(5_000_000_000L, GoDuration.parseNanos("5s"));
    assertEquals(5_400_000_000_000L, GoDuration.parseNanos("1h30m"));
    assertEquals(1_500_000_000L, GoDuration.parseNanos("1.5s"));
    assertEquals(-2_000_000L, GoDuration.parseNanos("-2ms"));
    assertEquals(1_000L, GoDuration.parseNanos("1µs"));
    assertEquals(1_000L, GoDuration.parseNanos("+1us"));
    assertEquals(500_000_000L, GoDuration.parseNanos(".5s"));
  }

  @Test
  void rejectsInvalidDurations() {
    assertMessage("time: invalid duration \"\"", "");
    assertMessage("time: invalid duration \"abc\"", "abc");
    assertMessage("time: invalid duration \"-\"", "-");
    assertMessage("time: invalid duration \".s\"", ".s");
    assertMessage("time: missing unit in duration \"10\"", "10");
    assertMessage("time: unknown unit \"x\" in duration \"5x\"", "5x");
    assertMessage("time: invalid duration \"99999999999999999999s\"", "99999999999999999999s");
  }

  private static void assertMessage(String expected, String input) {
    IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> GoDuration.parseNanos(input));
    assertEquals(expected, e.getMessage());
  }
}
