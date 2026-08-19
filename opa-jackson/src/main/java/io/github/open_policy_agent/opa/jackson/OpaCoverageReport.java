package io.github.open_policy_agent.opa.jackson;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.open_policy_agent.opa.tracing.CoverageProfiler;
import io.github.open_policy_agent.opa.tracing.Position;
import io.github.open_policy_agent.opa.tracing.Range;

/**
 * Serializes a {@link CoverageProfiler}'s collected coverage into the JSON shape produced by OPA's
 * {@code opa eval --coverage} command.
 *
 * <p>Output structure:
 *
 * <pre>{@code
 * {
 *   "files": {
 *     "policy.rego": {
 *       "covered": [ { "start": { "row": 5, "col": 2 }, "end": { "row": 5, "col": 14 } } ]
 *     }
 *   }
 * }
 * }</pre>
 *
 * <p>Each executed statement range is emitted individually (sorted by position, not coalesced),
 * matching OPA's {@code v1/cover} report; a range's column is omitted when zero. File indices that
 * fall outside the provided filename list are skipped silently — those typically come from
 * synthetic statements with no source mapping.
 */
public final class OpaCoverageReport {

  private OpaCoverageReport() {}

  /**
   * Build the OPA-format coverage report.
   *
   * @param profiler the profiler that recorded coverage during evaluation
   * @param filenames file index -&gt; filename mapping, typically obtained via
   *     {@code policy.getStaticField().getFiles()} mapped to {@code StringConst::getValue}
   * @return a Jackson {@link ObjectNode} with the OPA coverage shape
   */
  public static ObjectNode from(CoverageProfiler profiler, List<String> filenames) {
    ObjectNode root = JsonNodeFactory.instance.objectNode();
    ObjectNode filesNode = root.putObject("files");

    Map<Integer, Set<Range>> coveredByFile = profiler.getCoveredRanges();

    List<Integer> fileIndices = new ArrayList<>(coveredByFile.keySet());
    Collections.sort(fileIndices);

    for (int fileIndex : fileIndices) {
      if (fileIndex < 0 || fileIndex >= filenames.size()) {
        continue;
      }
      Set<Range> covered = coveredByFile.get(fileIndex);
      if (covered == null || covered.isEmpty()) {
        continue;
      }
      ObjectNode fileEntry = filesNode.putObject(filenames.get(fileIndex));
      writeRanges(fileEntry.putArray("covered"), covered);
    }

    return root;
  }

  private static void writeRanges(ArrayNode array, Collection<Range> ranges) {
    List<Range> sorted = new ArrayList<>(ranges);
    sorted.sort(Range::compareTo);
    for (Range range : sorted) {
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
