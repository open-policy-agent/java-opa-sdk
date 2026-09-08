package io.github.open_policy_agent.opa.tracing;

import io.github.open_policy_agent.opa.ir.Location;
import io.github.open_policy_agent.opa.ir.policy.Block;
import io.github.open_policy_agent.opa.ir.policy.Func;
import io.github.open_policy_agent.opa.ir.policy.Plan;
import io.github.open_policy_agent.opa.ir.policy.Policy;
import io.github.open_policy_agent.opa.ir.policy.Static;
import io.github.open_policy_agent.opa.ir.policy.StringConst;
import io.github.open_policy_agent.opa.ir.policy.UnplannedRule;
import io.github.open_policy_agent.opa.ir.stmts.BlockStmt;
import io.github.open_policy_agent.opa.ir.stmts.NotStmt;
import io.github.open_policy_agent.opa.ir.stmts.ScanStmt;
import io.github.open_policy_agent.opa.ir.stmts.Stmt;
import io.github.open_policy_agent.opa.ir.stmts.WithStmt;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * The computed coverage result for one policy: which source ranges executed, which did not, the
 * distinct-line counts, and the coverage percentage. Mirrors the shape of OPA's {@code opa eval
 * --coverage} output.
 *
 * <p>This holds the coverage figures themselves, computed once from a {@link CoverageProfiler}. A
 * {@link CoverageReportWriter} turns the model into JSON.
 *
 * <p>Each executed statement range is reported individually, sorted by position and not coalesced,
 * matching OPA. {@code covered}/{@code notCovered} may be empty and the line counts count distinct
 * source rows. File indices outside {@code filenames} are skipped (synthetic statements with no
 * source mapping). {@code notCovered} ranges come from two sources. The first is statements the
 * planner compiled but the evaluator never ran, e.g. the rest of a rule body after an earlier
 * statement failed. These are found by walking every statement in the plan and reporting any whose
 * range was not recorded as covered. The second is the plan's {@code unplanned_rules}: rules the
 * planner never compiled into statements at all, so they can never be covered.
 */
public record CoverageReport(
    Map<String, FileCoverage> files, int coveredLines, int notCoveredLines, double coverage) {

  /**
   * Per-file coverage. {@code covered} and {@code notCovered} are sorted by position and may be
   * empty; {@code coveredLines}/{@code notCoveredLines} count distinct source rows.
   */
  public record FileCoverage(
      List<Range> covered,
      List<Range> notCovered,
      int coveredLines,
      int notCoveredLines,
      double coverage) {}

  /**
   * Build the report from recorded coverage.
   *
   * @param profiler the profiler that recorded coverage during evaluation
   * @param policy the evaluated policy, used to resolve filenames, unplanned rules, and every
   *     statement range the plan contains
   * @return the computed, library-neutral report
   */
  public static CoverageReport from(CoverageProfiler profiler, Policy policy) {
    List<String> filenames = policy.getStaticFilenames();
    List<UnplannedRule> unplannedRules = policy.getUnplannedRules();
    Map<Integer, Set<Range>> plannedRanges = plannedRangesByFile(policy);

    Map<Integer, Set<Range>> coveredByFile = profiler.getCoveredRanges();
    Map<Integer, List<Range>> notCoveredByFile =
        notCoveredRangesByFile(unplannedRules, plannedRanges, coveredByFile);

    Set<Integer> fileIndices = new TreeSet<>(coveredByFile.keySet());
    fileIndices.addAll(notCoveredByFile.keySet());

    Map<String, FileCoverage> files = new LinkedHashMap<>();
    int totalCovered = 0;
    int totalNotCovered = 0;
    for (int fileIndex : fileIndices) {
      if (fileIndex < 0 || fileIndex >= filenames.size()) {
        continue;
      }

      Set<Range> covered = coveredByFile.get(fileIndex);
      List<Range> notCovered = notCoveredByFile.get(fileIndex);
      boolean hasCovered = covered != null && !covered.isEmpty();
      boolean hasNotCovered = notCovered != null && !notCovered.isEmpty();
      if (!hasCovered && !hasNotCovered) {
        continue;
      }

      int coveredLines = countLines(covered);
      int notCoveredLines = countLines(notCovered);
      files.put(
          filenames.get(fileIndex),
          new FileCoverage(
              sorted(covered),
              sorted(notCovered),
              coveredLines,
              notCoveredLines,
              coveragePercent(coveredLines, notCoveredLines)));
      totalCovered += coveredLines;
      totalNotCovered += notCoveredLines;
    }

    return new CoverageReport(
        files, totalCovered, totalNotCovered, coveragePercent(totalCovered, totalNotCovered));
  }

  /**
   * Build the report without statement-level not-covered detection, for callers with no
   * {@link Policy} on hand. Wraps the inputs in a plan-less {@link Policy} and delegates to
   * {@link #from(CoverageProfiler, Policy)}.
   *
   * @param profiler the profiler that recorded coverage during evaluation
   * @param filenames file index to filename mapping, typically {@code policy.getStaticFilenames()}
   * @param unplannedRules rules to report as not covered, typically {@code
   *     policy.getUnplannedRules()}; may be null
   * @return the computed, library-neutral report
   */
  public static CoverageReport from(
      CoverageProfiler profiler, List<String> filenames, List<UnplannedRule> unplannedRules) {
    List<StringConst> files = new ArrayList<>();
    for (String filename : filenames) {
      files.add(new StringConst(filename));
    }
    Policy policy = new Policy(new Static(List.of(), List.of(), files), null, null, unplannedRules);
    return from(profiler, policy);
  }

  /**
   * Ranges to report as not covered:
   *
   * <ul>
   *   <li>{@code plannedRanges} entries absent from {@code coveredByFile} (a compiled statement
   *       that never ran)
   *   <li>every {@code unplannedRules} range (a rule the planner never compiled at all)
   * </ul>
   *
   * <p>Membership uses {@link Range#contains}, not equality, matching OPA's {@code v1/cover}
   * {@code isRangeCovered}: a covered range that fully contains the candidate counts as covered.
   */
  private static Map<Integer, List<Range>> notCoveredRangesByFile(
      List<UnplannedRule> unplannedRules,
      Map<Integer, Set<Range>> plannedRanges,
      Map<Integer, Set<Range>> coveredByFile) {
    Map<Integer, Set<Range>> byFile = new HashMap<>();
    if (unplannedRules != null) {
      for (UnplannedRule rule : unplannedRules) {
        byFile
            .computeIfAbsent(rule.getLocation().getFile(), k -> new LinkedHashSet<>())
            .add(Range.of(rule.getLocation()));
      }
    }

    if (plannedRanges != null) {
      for (Map.Entry<Integer, Set<Range>> entry : plannedRanges.entrySet()) {
        Set<Range> covered = coveredByFile.getOrDefault(entry.getKey(), Set.of());
        for (Range range : entry.getValue()) {
          if (!isRangeCovered(range, covered)) {
            byFile.computeIfAbsent(entry.getKey(), k -> new LinkedHashSet<>()).add(range);
          }
        }
      }
    }

    Map<Integer, List<Range>> result = new HashMap<>();
    for (Map.Entry<Integer, Set<Range>> entry : byFile.entrySet()) {
      result.put(entry.getKey(), new ArrayList<>(entry.getValue()));
    }
    return result;
  }

  private static boolean isRangeCovered(Range range, Set<Range> covered) {
    for (Range candidate : covered) {
      if (candidate.contains(range)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Every statement range the plan contains, per file, regardless of whether it ran. This is the
   * IR-level equivalent of walking a Rego AST's rules and expressions: a statement present here but
   * absent from {@link CoverageProfiler#getCoveredRanges()} is reported as not covered, which
   * catches the tail of a rule body that stopped executing partway through (e.g. after an earlier
   * statement failed), not just whole rules the planner dropped.
   */
  private static Map<Integer, Set<Range>> plannedRangesByFile(Policy policy) {
    Map<Integer, Set<Range>> byFile = new HashMap<>();
    if (policy.getPlans() != null && policy.getPlans().getPlans() != null) {
      for (Plan plan : policy.getPlans().getPlans()) {
        collectBlocks(plan.getBlocks(), byFile);
      }
    }

    if (policy.getFuncs() != null && policy.getFuncs().getFuncs() != null) {
      for (Func func : policy.getFuncs().getFuncs()) {
        collectBlocks(func.getBlocks(), byFile);
      }
    }

    return byFile;
  }

  /** Recursively records every statement's range, so nested blocks are walked, not just the top level. */
  private static void collectBlocks(List<Block> blocks, Map<Integer, Set<Range>> byFile) {
    if (blocks == null) {
      return;
    }
    for (Block block : blocks) {
      if (block == null || block.getStmts() == null) {
        continue;
      }
      for (Stmt stmt : block.getStmts()) {
        collectStmt(stmt, byFile);
      }
    }
  }

  private static void collectStmt(Stmt stmt, Map<Integer, Set<Range>> byFile) {
    if (stmt == null) {
      return;
    }
    Location location = stmt.getLocation();
    if (location != null) {
      byFile.computeIfAbsent(location.getFile(), k -> new LinkedHashSet<>()).add(Range.of(location));
    }
    // These are the only Stmt types carrying nested Blocks (an "or" branch, a with-override, a
    // scan body, a negation). This matches OPA's v1/ir walk.go exactly (Walk's *BlockStmt/*ScanStmt/
    // *NotStmt/*WithStmt cases). Keep this list in sync if upstream's IR shape changes.
    if (stmt instanceof BlockStmt blockStmt) {
      collectBlocks(blockStmt.getBlocks(), byFile);
    } else if (stmt instanceof WithStmt withStmt && withStmt.getBlock() != null) {
      collectBlocks(List.of(withStmt.getBlock()), byFile);
    } else if (stmt instanceof ScanStmt scanStmt && scanStmt.getBlock() != null) {
      collectBlocks(List.of(scanStmt.getBlock()), byFile);
    } else if (stmt instanceof NotStmt notStmt && notStmt.getBlock() != null) {
      collectBlocks(List.of(notStmt.getBlock()), byFile);
    }
  }

  /** Distinct source rows spanned by {@code ranges}, matching OPA's line-based counts. */
  private static int countLines(Collection<Range> ranges) {
    if (ranges == null || ranges.isEmpty()) {
      return 0;
    }
    Set<Integer> rows = new TreeSet<>();
    for (Range range : ranges) {
      for (int row = range.start().getRow(); row <= range.end().getRow(); row++) {
        rows.add(row);
      }
    }
    return rows.size();
  }

  private static List<Range> sorted(Collection<Range> ranges) {
    if (ranges == null || ranges.isEmpty()) {
      return List.of();
    }
    List<Range> result = new ArrayList<>(ranges);
    result.sort(Range::compareTo);
    return result;
  }

  private static double coveragePercent(int coveredLines, int notCoveredLines) {
    int total = coveredLines + notCoveredLines;
    return total == 0 ? 0.0 : (double) coveredLines / total * 100;
  }
}
