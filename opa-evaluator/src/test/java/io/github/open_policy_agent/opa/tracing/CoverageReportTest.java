package io.github.open_policy_agent.opa.tracing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.open_policy_agent.opa.ir.Location;
import io.github.open_policy_agent.opa.ir.policy.Block;
import io.github.open_policy_agent.opa.ir.policy.Plan;
import io.github.open_policy_agent.opa.ir.policy.Plans;
import io.github.open_policy_agent.opa.ir.policy.Policy;
import io.github.open_policy_agent.opa.ir.policy.Static;
import io.github.open_policy_agent.opa.ir.policy.StringConst;
import io.github.open_policy_agent.opa.ir.policy.UnplannedRule;
import io.github.open_policy_agent.opa.ir.stmts.NopStmt;
import io.github.open_policy_agent.opa.ir.stmts.Stmt;
import java.util.List;
import org.junit.jupiter.api.Test;

class CoverageReportTest {

  @Test
  void from_recordsCoveredRangesSortedPerFile() {
    CoverageProfiler profiler = new CoverageProfiler();
    profiler.addEntry(new Location(0, 5, 6, 12, 6), 0); // row 6
    profiler.addEntry(new Location(0, 2, 5, 14, 5), 0); // row 5

    CoverageReport report = CoverageReport.from(profiler, List.of("policy.rego"), List.of());

    CoverageReport.FileCoverage file = report.files().get("policy.rego");
    assertEquals(2, file.covered().size());
    // Sorted by position: row 5 before row 6.
    assertEquals(5, file.covered().get(0).start().getRow());
    assertEquals(6, file.covered().get(1).start().getRow());
    assertTrue(file.notCovered().isEmpty());
  }

  @Test
  void from_addsUnplannedRulesAsNotCovered() {
    CoverageProfiler profiler = new CoverageProfiler();
    profiler.addEntry(new Location(0, 1, 5, 10, 5), 0);
    List<UnplannedRule> unplannedRules =
        List.of(new UnplannedRule("data.example.unused", new Location(0, 1, 11, 15, 11)));

    CoverageReport report =
        CoverageReport.from(profiler, List.of("policy.rego"), unplannedRules);

    CoverageReport.FileCoverage file = report.files().get("policy.rego");
    assertEquals(1, file.covered().size());
    assertEquals(1, file.notCovered().size());
    assertEquals(11, file.notCovered().get(0).start().getRow());
  }

  @Test
  void from_countsDistinctLinesAndCoveragePerFileAndOverall() {
    CoverageProfiler profiler = new CoverageProfiler();
    profiler.addEntry(new Location(0, 1, 5, 10, 5), 0); // covered row 5
    List<UnplannedRule> unplannedRules =
        // not covered rows 11..12 (a two-line rule body)
        List.of(new UnplannedRule("data.example.unused", new Location(0, 1, 11, 15, 12)));

    CoverageReport report =
        CoverageReport.from(profiler, List.of("policy.rego"), unplannedRules);

    CoverageReport.FileCoverage file = report.files().get("policy.rego");
    assertEquals(1, file.coveredLines());
    assertEquals(2, file.notCoveredLines());
    assertEquals(1.0 / 3 * 100, file.coverage());

    assertEquals(1, report.coveredLines());
    assertEquals(2, report.notCoveredLines());
    assertEquals(1.0 / 3 * 100, report.coverage());
  }

  @Test
  void from_skipsFileIndicesOutsideFilenameList() {
    CoverageProfiler profiler = new CoverageProfiler();
    profiler.addEntry(new Location(0, 1, 5, 10, 5), 0);
    profiler.addEntry(new Location(7, 1, 5, 10, 5), 0); // index 7 has no filename

    CoverageReport report = CoverageReport.from(profiler, List.of("policy.rego"), List.of());

    assertEquals(1, report.files().size());
    assertTrue(report.files().containsKey("policy.rego"));
  }

  @Test
  void from_toleratesNullUnplannedRules() {
    CoverageProfiler profiler = new CoverageProfiler();
    profiler.addEntry(new Location(0, 1, 5, 10, 5), 0);

    CoverageReport report = CoverageReport.from(profiler, List.of("policy.rego"), null);

    CoverageReport.FileCoverage file = report.files().get("policy.rego");
    assertFalse(file.covered().isEmpty());
    assertTrue(file.notCovered().isEmpty());
  }

  @Test
  void from_coverageIsZeroWhenNothingRecorded() {
    CoverageReport report =
        CoverageReport.from(new CoverageProfiler(), List.of("policy.rego"), List.of());

    assertTrue(report.files().isEmpty());
    assertEquals(0, report.coveredLines());
    assertEquals(0, report.notCoveredLines());
    assertEquals(0.0, report.coverage());
  }

  @Test
  void from_marksRestOfPartlyRunRuleAsNotCovered() {
    // A single plan (rule body) of two statements, e.g. `false` on row 5 then `another_rule` on
    // row 6: the evaluator only runs the first before the rule fails, so the second was compiled
    // (it's in the plan) but never executed and must show up as not covered.
    Stmt first = new NopStmt(0, 1, 5);
    Stmt second = new NopStmt(0, 1, 6);
    Policy policy = policyWithSinglePlan(List.of(first, second));

    CoverageProfiler profiler = new CoverageProfiler();
    profiler.addEntry(first.getLocation(), 0);

    CoverageReport report = CoverageReport.from(profiler, policy);

    CoverageReport.FileCoverage file = report.files().get("policy.rego");
    assertEquals(1, file.covered().size());
    assertEquals(5, file.covered().get(0).start().getRow());
    assertEquals(1, file.notCovered().size());
    assertEquals(6, file.notCovered().get(0).start().getRow());
  }

  @Test
  void from_reportsNothingNotCoveredWhenWholeRuleRuns() {
    Stmt first = new NopStmt(0, 1, 5);
    Stmt second = new NopStmt(0, 1, 6);
    Policy policy = policyWithSinglePlan(List.of(first, second));

    CoverageProfiler profiler = new CoverageProfiler();
    profiler.addEntry(first.getLocation(), 0);
    profiler.addEntry(second.getLocation(), 0);

    CoverageReport report = CoverageReport.from(profiler, policy);

    CoverageReport.FileCoverage file = report.files().get("policy.rego");
    assertEquals(2, file.covered().size());
    assertTrue(file.notCovered().isEmpty());
  }

  @Test
  void from_treatsAPlannedRangeAsCoveredWhenContainedInALargerCoveredRange() {
    // A planned range only needs to fall inside a covered range, not match it exactly, matching
    // OPA's v1/cover isRangeCovered.
    Stmt stmt = new NopStmt();
    stmt.setLocation(0, 5, 3, 5, 8); // row 5, cols 3-8: inside the covered row 5, cols 1-10
    Policy policy = policyWithSinglePlan(List.of(stmt));

    CoverageProfiler profiler = new CoverageProfiler();
    profiler.addEntry(new Location(0, 1, 5, 10, 5), 0);

    CoverageReport report = CoverageReport.from(profiler, policy);

    CoverageReport.FileCoverage file = report.files().get("policy.rego");
    assertTrue(file.notCovered().isEmpty());
  }

  private static Policy policyWithSinglePlan(List<Stmt> stmts) {
    Static staticField = new Static(List.of(), List.of(), List.of(new StringConst("policy.rego")));
    Plan plan = new Plan("data.example", List.of(new Block(stmts)));
    Plans plans = new Plans(List.of(plan));
    return new Policy(staticField, plans, null, List.of());
  }
}
