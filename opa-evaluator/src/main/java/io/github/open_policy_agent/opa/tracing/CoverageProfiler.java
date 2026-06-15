package io.github.open_policy_agent.opa.tracing;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import io.github.open_policy_agent.opa.ir.Location;

/**
 * {@link Profiler} implementation that records the source rows touched during evaluation, for use
 * as Rego line-coverage data.
 *
 * <p>Coverage is tracked as a map of file index (per the policy's static file table) to the set
 * of source rows that were executed. The column is intentionally discarded: coverage is
 * line-granular.
 *
 * <p>Only locations from statements that the evaluator dispatched are recorded. The Rego compiler
 * may emit statements that the evaluator never reaches — e.g. statements that follow an earlier
 * statement which short-circuited the block. Those rows are reported as not-covered, which
 * matches the expected coverage semantics.
 *
 * If only location line tracing is required, this is a more performant alternative to {@link DurationProfiler}
 */
public class CoverageProfiler implements Profiler {
  private final Map<Integer, Set<Integer>> hitsByFile = new HashMap<>();

  @Override
  public void addStart() {
    // No-op: coverage tracks completion, not entry.
  }

  @Override
  public void addEntry(Location location, long duration) {
    if (location == null) return;
    hitsByFile.computeIfAbsent(location.getFile(), k -> new HashSet<>()).add(location.getRow());
  }

  /**
   * @return per-file map of executed source rows. Outer key is the file index in the policy's
   *     static file table; inner value is the set of source rows that were executed.
   */
  public Map<Integer, Set<Integer>> getCoveredLines() {
    return Collections.unmodifiableMap(hitsByFile);
  }
}
