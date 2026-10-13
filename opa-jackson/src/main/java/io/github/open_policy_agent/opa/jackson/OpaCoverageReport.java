package io.github.open_policy_agent.opa.jackson;

import java.util.List;
import java.util.Map;
import io.github.open_policy_agent.opa.ir.policy.UnplannedRule;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;
import io.github.open_policy_agent.opa.tracing.CoverageProfiler;
import io.github.open_policy_agent.opa.tracing.CoverageReport;
import io.github.open_policy_agent.opa.tracing.CoverageReport.FileCoverage;
import io.github.open_policy_agent.opa.tracing.Position;
import io.github.open_policy_agent.opa.tracing.Range;

/**
 * Encodes a {@link CoverageReport} into the JSON shape produced by OPA's {@code opa eval
 * --coverage} command, using Jackson.
 *
 * <p>The coverage semantics (covered vs. not-covered ranges, line counts, percentage) are
 * computed upstream by {@link CoverageReport}; this class only maps that model onto Jackson nodes.
 * It does no I/O ({@link JacksonCoverageReportWriter} calls it and writes the result out), so the
 * report can be built and asserted on without touching disk.
 *
 * <p>Output structure:
 *
 * <pre>{@code
 * {
 *   "files": {
 *     "policy.rego": {
 *       "covered": [ { "start": { "row": 5, "col": 2 }, "end": { "row": 5, "col": 14 } } ],
 *       "not_covered": [ { "start": { "row": 11, "col": 1 }, "end": { "row": 11, "col": 15 } } ],
 *       "covered_lines": 1,
 *       "not_covered_lines": 1,
 *       "coverage": 50.0
 *     }
 *   },
 *   "covered_lines": 1,
 *   "not_covered_lines": 1,
 *   "coverage": 50.0
 * }
 * }</pre>
 *
 * <p>A range's column is omitted when zero, matching OPA's {@code col,omitempty}.
 */
public final class OpaCoverageReport {

  private OpaCoverageReport() {}

  /**
   * Build the OPA-format coverage report JSON.
   *
   * @param profiler the profiler that recorded coverage during evaluation
   * @param filenames file index -&gt; filename mapping, typically obtained via
   *     {@code policy.getStaticField().getFiles()} mapped to {@code StringConst::getValue}
   * @return a Jackson {@link ObjectNode} with the OPA coverage shape
   */
  public static ObjectNode from(CoverageProfiler profiler, List<String> filenames) {
    return from(profiler, filenames, List.of());
  }

  /**
   * Build the coverage report JSON, adding {@code not_covered} entries for rules that were never
   * compiled into statements.
   *
   * @param profiler the profiler that recorded coverage during evaluation
   * @param filenames file index -&gt; filename mapping, typically obtained via
   *     {@code policy.getStaticField().getFiles()} mapped to {@code StringConst::getValue}
   * @param unplannedRules rules to report as not covered, typically obtained via
   *     {@code policy.getUnplannedRules()}
   * @return a Jackson {@link ObjectNode} with the OPA coverage shape
   */
  public static ObjectNode from(
      CoverageProfiler profiler, List<String> filenames, List<UnplannedRule> unplannedRules) {
    return toJson(CoverageReport.from(profiler, filenames, unplannedRules));
  }

  /**
   * Encode a computed {@link CoverageReport} into OPA's coverage JSON shape.
   *
   * @param report the library-neutral report to serialize
   * @return a Jackson {@link ObjectNode} with the OPA coverage shape
   */
  public static ObjectNode toJson(CoverageReport report) {
    ObjectNode root = JsonNodeFactory.instance.objectNode();
    ObjectNode filesNode = root.putObject("files");

    for (Map.Entry<String, FileCoverage> entry : report.files().entrySet()) {
      FileCoverage fileCoverage = entry.getValue();
      ObjectNode fileEntry = filesNode.putObject(entry.getKey());
      if (!fileCoverage.covered().isEmpty()) {
        writeRanges(fileEntry.putArray("covered"), fileCoverage.covered());
      }
      if (!fileCoverage.notCovered().isEmpty()) {
        writeRanges(fileEntry.putArray("not_covered"), fileCoverage.notCovered());
      }
      writeTotals(
          fileEntry,
          fileCoverage.coveredLines(),
          fileCoverage.notCoveredLines(),
          fileCoverage.coverage());
    }

    writeTotals(root, report.coveredLines(), report.notCoveredLines(), report.coverage());

    return root;
  }

  private static void writeTotals(
      ObjectNode node, int coveredLines, int notCoveredLines, double coverage) {
    node.put("covered_lines", coveredLines);
    node.put("not_covered_lines", notCoveredLines);
    node.put("coverage", coverage);
  }

  private static void writeRanges(ArrayNode array, List<Range> ranges) {
    for (Range range : ranges) {
      ObjectNode rangeNode = array.addObject();
      writePosition(rangeNode.putObject("start"), range.start());
      writePosition(rangeNode.putObject("end"), range.end());
    }
  }

  /** Writes a position. Column is omitted when zero, matching OPA's {@code col,omitempty}. */
  private static void writePosition(ObjectNode node, Position position) {
    node.put("row", position.getRow());
    if (position.getCol() != 0) {
      node.put("col", position.getCol());
    }
  }
}
