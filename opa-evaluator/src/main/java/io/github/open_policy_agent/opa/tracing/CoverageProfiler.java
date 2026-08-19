package io.github.open_policy_agent.opa.tracing;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import io.github.open_policy_agent.opa.ir.Location;

/**
 * {@link Profiler} implementation that records the source ranges touched during evaluation, for use
 * as Rego coverage data.
 *
 * <p>Coverage is tracked as a map of file index (per the policy's static file table) to the set of
 * source {@link Range}s that were executed. Some statements start and finish on the same line, some
 * lines have more than one statement.
 *
 * <p>Ranges from statements that the evaluator ran are recorded. The Rego compiler may
 * emit statements that the evaluator runs, e.g. statements following an earlier
 * one which exited the block. Those ranges are reported as not-covered, which
 * matches the expected coverage semantics.
 *
 * <p>If only location tracing is required, this is a more performant alternative to {@link DurationProfiler}
 */
public class CoverageProfiler implements Profiler {
  private final Map<Integer, Set<Range>> hitsByFile = new HashMap<>();

  @Override
  public void addStart() {
    // No-op: coverage tracks completion, not entry.
  }

  @Override
  public void addEntry(Location location, long duration) {
      if (location == null) {
          return;
      }
    hitsByFile.computeIfAbsent(location.getFile(), k -> new HashSet<>()).add(Range.of(location));
  }

  /**
   * @return per-file map of executed source ranges. Outer key is the file index in the policy's
   *     static file table; inner value is the set of source ranges that were executed.
   */
  public Map<Integer, Set<Range>> getCoveredRanges() {
    return Collections.unmodifiableMap(hitsByFile);
  }

  /**
   * @return map of executed source rows (per file), derived from the recorded ranges, for any
   *     line-based consumers. Use {@link #getCoveredRanges()} for column-precise coverage.
   */
  public Map<Integer, Set<Integer>> getCoveredLines() {
    Map<Integer, Set<Integer>> rowsByFile = new HashMap<>();
    for (Map.Entry<Integer, Set<Range>> entry : hitsByFile.entrySet()) {
      Set<Integer> rows = new TreeSet<>();
      for (Range range : entry.getValue()) {
        for (int row = range.start().getRow(); row <= range.end().getRow(); row++) {
          rows.add(row);
        }
      }
      rowsByFile.put(entry.getKey(), rows);
    }
    return Collections.unmodifiableMap(rowsByFile);
  }
}
