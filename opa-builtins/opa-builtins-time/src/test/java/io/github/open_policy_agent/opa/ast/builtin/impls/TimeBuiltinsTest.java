package io.github.open_policy_agent.opa.ast.builtin.impls;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.open_policy_agent.opa.ast.builtin.BuiltinError;
import io.github.open_policy_agent.opa.ast.types.RegoArray;
import io.github.open_policy_agent.opa.ast.types.RegoBigInt;
import io.github.open_policy_agent.opa.ast.types.RegoDecimal;
import io.github.open_policy_agent.opa.ast.types.RegoInt32;
import io.github.open_policy_agent.opa.ast.types.RegoString;
import io.github.open_policy_agent.opa.ast.types.RegoValue;
import java.util.List;
import org.junit.jupiter.api.Test;

class TimeBuiltinsTest {

  private final TimeBuiltins builtins = new TimeBuiltins();

  // A timestamp can be any integer representation: small ones are RegoInt32, large or computed
  // ones are RegoBigInt.
  private final RegoValue[] epochs = {RegoInt32.of(0), new RegoBigInt(0L)};

  private static RegoArray ints(int... values) {
    List<RegoValue> list = new java.util.ArrayList<>();
    for (int v : values) {
      list.add(RegoInt32.of(v));
    }
    return new RegoArray(list);
  }

  @Test
  void dateAcceptsEveryIntegerRepresentation() {
    for (RegoValue epoch : epochs) {
      assertEquals(ints(1970, 1, 1), builtins.date(null, new RegoValue[] {epoch}));
    }
  }

  @Test
  void clockAcceptsEveryIntegerRepresentation() {
    for (RegoValue epoch : epochs) {
      assertEquals(ints(0, 0, 0), builtins.clock(null, new RegoValue[] {epoch}));
    }
  }

  @Test
  void weekdayAcceptsEveryIntegerRepresentation() {
    for (RegoValue epoch : epochs) {
      assertEquals(new RegoString("Thursday"), builtins.weekday(null, new RegoValue[] {epoch}));
    }
  }

  @Test
  void diffAcceptsEveryIntegerRepresentation() {
    RegoValue later = new RegoBigInt(1_000_000_000_000_000_000L);
    for (RegoValue epoch : epochs) {
      assertEquals(ints(31, 8, 8, 1, 46, 40), builtins.diff(null, new RegoValue[] {epoch, later}));
    }
  }

  @Test
  void timestampWithTimezoneAcceptsEveryIntegerRepresentation() {
    for (RegoValue epoch : epochs) {
      RegoArray withZone = new RegoArray(List.<RegoValue>of(epoch, new RegoString("UTC")));
      assertEquals(ints(1970, 1, 1), builtins.date(null, new RegoValue[] {withZone}));
    }
  }

  @Test
  void fractionsAreStillRejected() {
    assertThrows(
        BuiltinError.class, () -> builtins.date(null, new RegoValue[] {new RegoDecimal(1.5)}));
  }
}
