package io.github.open_policy_agent.opa.tracing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.time.Duration;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import io.github.open_policy_agent.opa.ir.Location;
import io.github.open_policy_agent.opa.tracing.DurationProfiler.EvalTotal;
import io.github.open_policy_agent.opa.tracing.DurationProfiler.Loc;

class DurationProfilerTest {

  private DurationProfiler profiler;

  @BeforeEach
  void setUp() {
    profiler = new DurationProfiler();
  }

  static Stream<Arguments> recordingScenarios() {
    return Stream.of(
        Arguments.of(
            "single statement",
            (Consumer<DurationProfiler>)
                p -> {
                  p.addStart();
                  p.addEntry(new Location(0, 1, 5), 100L);
                },
            Map.of(new Loc(0, 5), expected(1, 100L))),
        Arguments.of(
            "repeated same location accumulates count and duration",
            (Consumer<DurationProfiler>)
                p -> {
                  p.addStart();
                  p.addEntry(new Location(0, 1, 5), 30L);
                  p.addStart();
                  p.addEntry(new Location(0, 1, 5), 70L);
                },
            Map.of(new Loc(0, 5), expected(2, 100L))),
        Arguments.of(
            "discards column — same row+file collapses",
            (Consumer<DurationProfiler>)
                p -> {
                  p.addStart();
                  p.addEntry(new Location(0, 3, 5), 10L);
                  p.addStart();
                  p.addEntry(new Location(0, 99, 5), 20L);
                },
            Map.of(new Loc(0, 5), expected(2, 30L))),
        Arguments.of(
            "different rows in same file are tracked separately",
            (Consumer<DurationProfiler>)
                p -> {
                  p.addStart();
                  p.addEntry(new Location(0, 1, 5), 10L);
                  p.addStart();
                  p.addEntry(new Location(0, 1, 8), 20L);
                },
            Map.of(
                new Loc(0, 5), expected(1, 10L),
                new Loc(0, 8), expected(1, 20L))),
        Arguments.of(
            "different files are tracked separately",
            (Consumer<DurationProfiler>)
                p -> {
                  p.addStart();
                  p.addEntry(new Location(0, 1, 5), 10L);
                  p.addStart();
                  p.addEntry(new Location(1, 1, 5), 20L);
                },
            Map.of(
                new Loc(0, 5), expected(1, 10L),
                new Loc(1, 5), expected(1, 20L))),
        Arguments.of(
            // Parent (row 5) takes 100ns total; child (row 8) takes 60ns.
            // Parent's exclusive time should be 40ns.
            "nested child time subtracts from parent",
            (Consumer<DurationProfiler>)
                p -> {
                  p.addStart(); // parent
                  p.addStart(); // child
                  p.addEntry(new Location(0, 1, 8), 60L);
                  p.addEntry(new Location(0, 1, 5), 100L);
                },
            Map.of(
                new Loc(0, 5), expected(1, 40L),
                new Loc(0, 8), expected(1, 60L))),
        Arguments.of(
            // Parent (row 5) takes 100ns, two sequential children (rows 8, 9) of 30 + 40.
            // Parent's exclusive time should be 100 - 30 - 40 = 30ns.
            "sibling children both subtract from parent",
            (Consumer<DurationProfiler>)
                p -> {
                  p.addStart(); // parent
                  p.addStart(); // child A
                  p.addEntry(new Location(0, 1, 8), 30L);
                  p.addStart(); // child B
                  p.addEntry(new Location(0, 1, 9), 40L);
                  p.addEntry(new Location(0, 1, 5), 100L);
                },
            Map.of(
                new Loc(0, 5), expected(1, 30L),
                new Loc(0, 8), expected(1, 30L),
                new Loc(0, 9), expected(1, 40L))),
        Arguments.of(
            // grandparent (row 5) -> parent (row 8) -> child (row 9).
            // Child of 20ns subtracts from parent only; grandparent unchanged by child.
            "deeply nested only immediate parent is adjusted",
            (Consumer<DurationProfiler>)
                p -> {
                  p.addStart(); // grandparent
                  p.addStart(); // parent
                  p.addStart(); // child
                  p.addEntry(new Location(0, 1, 9), 20L);
                  p.addEntry(new Location(0, 1, 8), 80L); // 80 - 20 = 60
                  p.addEntry(new Location(0, 1, 5), 200L); // 200 - 80 = 120
                },
            Map.of(
                new Loc(0, 5), expected(1, 120L),
                new Loc(0, 8), expected(1, 60L),
                new Loc(0, 9), expected(1, 20L))));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("recordingScenarios")
  void recordingScenarios_produceExpectedDurations(
      String name, Consumer<DurationProfiler> recording, Map<Loc, long[]> expected) {
    recording.accept(profiler);

    Map<Loc, EvalTotal> durations = profiler.getDurations();
    assertEquals(expected.keySet(), durations.keySet(), "location keys");
    expected.forEach(
        (loc, exp) -> {
          EvalTotal actual = durations.get(loc);
          assertEquals(exp[0], actual.getCount(), "count for " + loc);
          assertEquals(
              Duration.ofNanos(exp[1]), actual.getTotalDuration(), "duration for " + loc);
        });
  }

  static Stream<Arguments> locEqualityCases() {
    return Stream.of(
        Arguments.of("equal", new Loc(0, 5), new Loc(0, 5), true),
        Arguments.of("different row", new Loc(0, 5), new Loc(0, 6), false),
        Arguments.of("different file", new Loc(0, 5), new Loc(1, 5), false),
        Arguments.of("different file and row", new Loc(0, 5), new Loc(1, 6), false));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("locEqualityCases")
  void loc_equalityFollowsFileAndRow(String name, Loc a, Loc b, boolean equal) {
    if (equal) {
      assertEquals(a, b);
      assertEquals(a.hashCode(), b.hashCode());
    } else {
      assertNotEquals(a, b);
    }
  }

  @Test
  void loc_isNotEqualToOtherTypes() {
    assertNotEquals(new Loc(0, 5), "0:5");
    assertNotEquals(new Loc(0, 5), null);
  }

  private static long[] expected(long count, long nanos) {
    return new long[] {count, nanos};
  }
}