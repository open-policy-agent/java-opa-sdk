package io.github.open_policy_agent.opa.tracing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import io.github.open_policy_agent.opa.ir.Location;

class CoverageProfilerTest {

  private CoverageProfiler profiler;

  @BeforeEach
  void setUp() {
    profiler = new CoverageProfiler();
  }

  static Stream<Arguments> recordingCases() {
    return Stream.of(
        Arguments.of(
            "single location",
            List.of(new Location(0, 3, 5)),
            Map.of(0, Set.of(5))),
        Arguments.of(
            "dedupes repeated location",
            List.of(new Location(0, 3, 5), new Location(0, 3, 5), new Location(0, 3, 5)),
            Map.of(0, Set.of(5))),
        Arguments.of(
            "accumulates rows in same file",
            List.of(new Location(0, 1, 5), new Location(0, 1, 8)),
            Map.of(0, Set.of(5, 8))),
        Arguments.of(
            "separates files",
            List.of(new Location(0, 1, 5), new Location(1, 1, 5)),
            Map.of(0, Set.of(5), 1, Set.of(5))),
        Arguments.of(
            "discards column",
            List.of(new Location(0, 1, 5), new Location(0, 7, 5), new Location(0, 99, 5)),
            Map.of(0, Set.of(5))));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("recordingCases")
  void addEntry_recordsExpectedLines(
      String name, List<Location> inputs, Map<Integer, Set<Integer>> expected) {
    inputs.forEach(loc -> profiler.addEntry(loc, 0));

    assertEquals(expected, profiler.getCoveredLines());
  }

  @Test
  void addEntry_withNullLocation_isNoOp() {
    profiler.addEntry(null, 0);

    assertTrue(profiler.getCoveredLines().isEmpty());
  }

  @Test
  void addStart_doesNotRecordAnything() {
    profiler.addStart();
    profiler.addStart();

    assertTrue(profiler.getCoveredLines().isEmpty());
  }

  @Test
  void getCoveredLines_returnsUnmodifiableView() {
    profiler.addEntry(new Location(0, 1, 5), 0);

    Map<Integer, Set<Integer>> covered = profiler.getCoveredLines();
    assertThrows(UnsupportedOperationException.class, () -> covered.put(99, Set.of(1)));
  }
}
