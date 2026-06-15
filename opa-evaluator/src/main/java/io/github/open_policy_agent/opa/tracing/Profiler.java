package io.github.open_policy_agent.opa.tracing;

import io.github.open_policy_agent.opa.ir.Location;

/**
 * Hook that receives a callback for every IR statement executed during policy evaluation, keyed by
 * the statement's source location.
 *
 * <p>Two callbacks are made per statement: {@link #addStart()} fires when the statement is about
 * to evaluate, {@link #addEntry(Location, long)} fires when it completes (with its wall-clock
 * duration). Implementations can use either or both, e.g.
 *
 * <ul>
 *   <li>{@link DurationProfiler} — records exclusive time per source location for time-by-line
 *       profiling.
 *   <li>{@link CoverageProfiler} — records the set of source rows that executed for line-coverage
 *       reporting.
 * </ul>
 *
 * <p>{@link io.github.open_policy_agent.opa.rego.EvaluationContext} accepts multiple profilers via
 * repeated calls to {@code withProfiler}; each profiler receives every callback in registration
 * order.
 */
public interface Profiler {

  /**
   * Called when a statement is about to begin evaluation. {@link DurationProfiler} uses this to
   * push a backoff frame for exclusive timing; coverage-style implementations can ignore it.
   */
  void addStart();

  /**
   * Called when a statement has finished evaluating.
   *
   * @param location the statement's source location
   * @param duration wall-clock duration of the statement, in nanoseconds
   */
  void addEntry(Location location, long duration);
}
