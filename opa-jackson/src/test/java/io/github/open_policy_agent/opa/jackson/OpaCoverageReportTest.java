package io.github.open_policy_agent.opa.jackson;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.List;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import io.github.open_policy_agent.opa.ir.Location;
import io.github.open_policy_agent.opa.tracing.CoverageProfiler;

class OpaCoverageReportTest {

  @Test
  void covered_emitsRawRangesWithColumnsSortedNotCoalesced() {
    CoverageProfiler profiler = new CoverageProfiler();
    profiler.addEntry(new Location(0, 5, 6, 12, 6), 0); // row 6, col 5..12
    profiler.addEntry(new Location(0, 2, 5, 14, 5), 0); // row 5, col 2..14

    ObjectNode report = OpaCoverageReport.from(profiler, List.of("policy.rego"));

    JsonNode covered = report.path("files").path("policy.rego").path("covered");
    assertEquals(2, covered.size());

    assertEquals(5, covered.get(0).path("start").path("row").asInt());
    assertEquals(2, covered.get(0).path("start").path("col").asInt());
    assertEquals(5, covered.get(0).path("end").path("row").asInt());
    assertEquals(14, covered.get(0).path("end").path("col").asInt());

    assertEquals(6, covered.get(1).path("start").path("row").asInt());
    assertEquals(5, covered.get(1).path("start").path("col").asInt());
  }

  @Test
  void position_omitsColumnWhenZero() {
    CoverageProfiler profiler = new CoverageProfiler();
    profiler.addEntry(new Location(0, 0, 5, 0, 5), 0); // col 0 both ends

    ObjectNode report = OpaCoverageReport.from(profiler, List.of("policy.rego"));

    JsonNode start = report.path("files").path("policy.rego").path("covered").get(0).path("start");
    assertEquals(5, start.path("row").asInt());
    assertFalse(start.has("col"));
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
    profiler.addEntry(new Location(0, 1, 5, 10, 5), 0);
    profiler.addEntry(new Location(1, 1, 12, 10, 12), 0);

    ObjectNode report = OpaCoverageReport.from(profiler, List.of("a.rego", "b.rego"));

    JsonNode files = report.path("files");
    assertEquals(2, files.size());
    assertEquals(5, files.path("a.rego").path("covered").get(0).path("start").path("row").asInt());
    assertEquals(12, files.path("b.rego").path("covered").get(0).path("start").path("row").asInt());
  }

  @Test
  void unknownFileIndex_isSkipped() {
    CoverageProfiler profiler = new CoverageProfiler();
    profiler.addEntry(new Location(0, 1, 5, 10, 5), 0);
    profiler.addEntry(new Location(7, 1, 9, 10, 9), 0); // file index 7 has no name

    ObjectNode report = OpaCoverageReport.from(profiler, List.of("policy.rego"));

    JsonNode files = report.path("files");
    assertEquals(1, files.size());
    assertFalse(files.has("7"));
  }
}
