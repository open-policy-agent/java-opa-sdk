package io.github.open_policy_agent.opa.jackson;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.open_policy_agent.opa.tracing.CoverageProfiler;

/**
 * Serializes a {@link CoverageProfiler}'s collected line coverage into the JSON shape produced by
 * OPA's {@code opa eval --coverage} command.
 *
 * <p>Output structure:
 *
 * <pre>{@code
 * {
 *   "files": {
 *     "policy.rego": {
 *       "covered": [
 *         { "start": { "row": 5 }, "end": { "row": 5 } },
 *         { "start": { "row": 8 }, "end": { "row": 10 } }
 *       ]
 *     }
 *   }
 * }
 * }</pre>
 *
 * <p>Contiguous source rows are coalesced into a single range. File indices that fall outside the
 * provided filename list are skipped silently — those typically come from synthetic statements
 * with no source mapping.
 */
public final class OpaCoverageReport {

  private OpaCoverageReport() {}

  /**
   * Build the OPA-format coverage report.
   *
   * @param profiler the profiler that recorded coverage during evaluation
   * @param filenames file index -> filename mapping, typically obtained via
   *     {@code policy.getStaticField().getFiles()} mapped to {@code StringConst::getValue}
   * @return a Jackson {@link ObjectNode} with the OPA coverage shape
   */
  public static ObjectNode from(CoverageProfiler profiler, List<String> filenames) {
    ObjectNode root = JsonNodeFactory.instance.objectNode();
    ObjectNode filesNode = root.putObject("files");

    Map<Integer, Set<Integer>> hitsByFile = profiler.getCoveredLines();

    List<Integer> fileIndices = new ArrayList<>(hitsByFile.keySet());
    Collections.sort(fileIndices);

    for (int fileIndex : fileIndices) {
      if (fileIndex < 0 || fileIndex >= filenames.size()) {
        continue;
      }
      Set<Integer> rows = hitsByFile.get(fileIndex);
      if (rows == null || rows.isEmpty()) {
        continue;
      }

      ObjectNode fileEntry = filesNode.putObject(filenames.get(fileIndex));
      ArrayNode coveredArray = fileEntry.putArray("covered");
      for (int[] range : coalesceRanges(rows)) {
        ObjectNode rangeNode = coveredArray.addObject();
        rangeNode.putObject("start").put("row", range[0]);
        rangeNode.putObject("end").put("row", range[1]);
      }
    }

    return root;
  }

  /** Coalesce a set of row numbers into inclusive [start, end] ranges. */
  static List<int[]> coalesceRanges(Set<Integer> rows) {
    TreeSet<Integer> sorted = new TreeSet<>(rows);
    List<int[]> ranges = new ArrayList<>();
    int start = -1;
    int end = -1;
    for (int row : sorted) {
      if (start == -1) {
        start = row;
        end = row;
      } else if (row == end + 1) {
        end = row;
      } else {
        ranges.add(new int[] {start, end});
        start = row;
        end = row;
      }
    }
    if (start != -1) {
      ranges.add(new int[] {start, end});
    }
    return ranges;
  }
}
