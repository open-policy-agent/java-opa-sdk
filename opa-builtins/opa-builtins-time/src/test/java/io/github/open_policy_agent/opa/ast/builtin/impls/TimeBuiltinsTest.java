package io.github.open_policy_agent.opa.ast.builtin.impls;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.open_policy_agent.opa.ast.builtin.BuiltinError;
import io.github.open_policy_agent.opa.ast.types.RegoArray;
import io.github.open_policy_agent.opa.ast.types.RegoBigInt;
import io.github.open_policy_agent.opa.ast.types.RegoDecimal;
import io.github.open_policy_agent.opa.ast.types.RegoInt32;
import io.github.open_policy_agent.opa.ast.types.RegoNumber;
import io.github.open_policy_agent.opa.ast.types.RegoString;
import io.github.open_policy_agent.opa.ast.types.RegoValue;
import io.github.open_policy_agent.opa.rego.TypeError;
import java.math.BigInteger;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

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

  // Go's AddDate adds years, months and days to the fields and then normalizes, so a day past the
  // end of the month rolls into the next one instead of being clamped. Expected values from opa 1.21.1.
  @ParameterizedTest(name = "{0}-{1}-{2} +{3}y {4}m {5}d")
  @CsvSource({
    "2023,1,31,0,1,0,1677838830123456789",
    "2024,1,31,0,1,0,1709374830123456789",
    "2023,1,31,0,1,1,1677925230123456789",
    "2024,2,29,1,0,0,1740824430123456789",
    "2024,2,29,-1,0,0,1677666030123456789",
    "2023,3,31,0,-1,0,1677838830123456789",
    "2023,10,31,0,4,0,1709374830123456789",
    "2023,1,31,0,13,0,1709374830123456789",
    "2023,1,31,0,-13,0,1640946030123456789",
    "2023,10,1,0,0,31,1698834030123456789",
    "2023,3,1,0,0,-1,1677579630123456789",
    "2023,5,31,1,-3,-45,1705486830123456789"
  })
  void addDateNormalizesLikeGo(int year, int month, int day, int years, int months, int days, long expected) {
    final long ns = OffsetDateTime.of(year, month, day, 10, 20, 30, 123456789, ZoneOffset.UTC).toEpochSecond() * 1_000_000_000L + 123456789;

    final RegoValue result = builtins.addDate(null, new RegoValue[] {new RegoBigInt(ns), RegoInt32.of(years), RegoInt32.of(months), RegoInt32.of(days)});

    assertEquals(expected, ((RegoNumber) result).getBigIntValue().longValueExact());
  }

  @ParameterizedTest
  @CsvSource({"-1000000000000000000,0,1,0,-997408000000000000", "-5000000000,0,1,31,5356795000000000"})
  void addDateNormalizesBeforeTheEpoch(long ns, int years, int months, int days, long expected) {
    final RegoValue result = builtins.addDate(null, new RegoValue[] {new RegoBigInt(ns), RegoInt32.of(years), RegoInt32.of(months), RegoInt32.of(days)});

    assertEquals(expected, ((RegoNumber) result).getBigIntValue().longValueExact());
  }

  @ParameterizedTest
  @CsvSource({"9223372036854775807,0,0,1", "0,300000,0,0", "0,0,0,2147483647", "0,2147483647,2147483647,0", "0,2147483648,0,0", "0,0,0,3000000000"})
  void addDateOutsideTheValidRangeIsAnError(long ns, long years, long months, long days) {
    final BuiltinError e =
        assertThrows(
            BuiltinError.class,
            () -> builtins.addDate(null, new RegoValue[] {new RegoBigInt(ns), new RegoBigInt(years), new RegoBigInt(months), new RegoBigInt(days)}));

    assertEquals("eval_builtin_error: time outside of valid range", e.getMessage());
  }

  // Messages from opa 1.21.1.
  @ParameterizedTest
  @CsvSource({"1.5,0,0,2", "0,1.5,0,3", "0,0,1.5,4", "1e19,0,0,2", "0,-1e19,0,3"})
  void addDateRejectsACountThatIsNotAnInt(double years, double months, double days, int operand) {
    final TypeError e =
        assertThrows(
            TypeError.class,
            () -> builtins.addDate(null, new RegoValue[] {RegoInt32.of(0), new RegoDecimal(years), new RegoDecimal(months), new RegoDecimal(days)}));

    assertEquals("eval_type_error: operand " + operand + " must be integer number but got floating-point number", e.getMessage());
  }

  @Test
  void addDateRejectsACountBeyondInt64AsAType() {
    final RegoValue tooBig = new RegoBigInt(new BigInteger("10000000000000000000"));

    assertThrows(TypeError.class, () -> builtins.addDate(null, new RegoValue[] {RegoInt32.of(0), tooBig, RegoInt32.of(0), RegoInt32.of(0)}));
  }

  @ParameterizedTest
  @CsvSource({"1.5", "1e19", "10000000000000000000"})
  void addDateRejectsATimestampBeyondInt64(String ns) {
    final RegoValue value = ns.contains(".") || ns.contains("e") ? new RegoDecimal(Double.parseDouble(ns)) : new RegoBigInt(new BigInteger(ns));

    final BuiltinError e =
        assertThrows(
            BuiltinError.class, () -> builtins.addDate(null, new RegoValue[] {value, RegoInt32.of(0), RegoInt32.of(0), RegoInt32.of(0)}));

    assertEquals("eval_builtin_error: timestamp too big", e.getMessage());
  }

  @Test
  void addDateAcceptsAWholeValuedDecimal() {
    final RegoValue result = builtins.addDate(null, new RegoValue[] {RegoInt32.of(0), new RegoDecimal(1.0), new RegoDecimal(0.0), new RegoDecimal(0.0)});

    assertEquals(31536000000000000L, ((RegoNumber) result).getBigIntValue().longValueExact());
  }
}
