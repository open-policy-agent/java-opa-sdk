package io.github.open_policy_agent.opa.jackson;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.List;
import java.util.stream.Stream;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import io.github.open_policy_agent.opa.ir.Location;
import io.github.open_policy_agent.opa.tracing.CoverageProfiler;

class OpaCoverageReportTest {

  /** A coverage range as it should appear in the OPA JSON output. */
  private static final class Range {
    final int start;
    final int end;

    Range(int start, int end) {
      this.start = start;
      this.end = end;
    }
  }

  static Stream<Arguments> rangeCases() {
    return Stream.of(
        Arguments.of(
            "single row emits a singleton range",
            List.of(5),
            List.of(new Range(5, 5))),
        Arguments.of(
            "contiguous rows are coalesced",
            List.of(5, 6, 7),
            List.of(new Range(5, 7))),
        Arguments.of(
            "disjoint rows emit multiple ranges",
            List.of(5, 7, 8, 10),
            List.of(new Range(5, 5), new Range(7, 8), new Range(10, 10))),
        Arguments.of(
            "input order does not matter; output is sorted",
            List.of(10, 5, 7),
            List.of(new Range(5, 5), new Range(7, 7), new Range(10, 10))));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("rangeCases")
  void from_emitsExpectedRangesForSingleFile(
      String name, List<Integer> rows, List<Range> expected) {
    CoverageProfiler profiler = new CoverageProfiler();
    rows.forEach(row -> profiler.addEntry(new Location(0, 1, row), 0));

    ObjectNode report = OpaCoverageReport.from(profiler, List.of("policy.rego"));

    JsonNode covered = report.path("files").path("policy.rego").path("covered");
    assertEquals(expected.size(), covered.size());
    for (int i = 0; i < expected.size(); i++) {
      assertEquals(expected.get(i).start, covered.get(i).path("start").path("row").asInt());
      assertEquals(expected.get(i).end, covered.get(i).path("end").path("row").asInt());
    }
  }

  @Test
  void emptyProfiler_yieldsEmptyFilesObject() {
    CoverageProfiler profiler = new CoverageProfiler();

    ObjectNode report = OpaCoverageReport.from(profiler, List.of("policy.rego"));

    assertEquals(0, report.get("files").size());
  }

  @Test
  void multipleFiles_eachAppearWithOwnFilename() {
    CoverageProfiler profiler = new CoverageProfiler();
    profiler.addEntry(new Location(0, 1, 5), 0);
    profiler.addEntry(new Location(1, 1, 12), 0);

    ObjectNode report = OpaCoverageReport.from(profiler, List.of("a.rego", "b.rego"));

    JsonNode files = report.path("files");
    assertEquals(2, files.size());
    assertEquals(5, files.path("a.rego").path("covered").get(0).path("start").path("row").asInt());
    assertEquals(12, files.path("b.rego").path("covered").get(0).path("start").path("row").asInt());
  }

  @Test
  void unknownFileIndex_isSkipped() {
    CoverageProfiler profiler = new CoverageProfiler();
    profiler.addEntry(new Location(0, 1, 5), 0);
    profiler.addEntry(new Location(7, 1, 9), 0); // file index 7 has no filename mapping

    ObjectNode report = OpaCoverageReport.from(profiler, List.of("policy.rego"));

    JsonNode files = report.path("files");
    assertEquals(1, files.size());
    assertFalse(files.has("7"));
  }
}
