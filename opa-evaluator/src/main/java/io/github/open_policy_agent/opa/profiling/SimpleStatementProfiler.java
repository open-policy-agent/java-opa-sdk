package io.github.open_policy_agent.opa.profiling;

import java.time.Duration;
import java.util.*;
import io.github.open_policy_agent.opa.ir.stmts.CallStmt;
import io.github.open_policy_agent.opa.ir.stmts.Stmt;
import io.github.open_policy_agent.opa.ir.stmts.Stmt.STMT_TYPE;

/**
 * Simple implementation of StatementProfiler that tracks statement execution time and frequency.
 *
 * <p>This profiler uses a stack-based approach to calculate exclusive time - the time spent in a
 * statement minus the time spent in child statements. This gives accurate measurements of where
 * time is actually being spent in policy evaluation.
 *
 * <p>Example: If statement A takes 100ms total, but calls statement B which takes 60ms, then
 * statement A's exclusive time is 40ms.
 */
public class SimpleStatementProfiler implements StatementProfiler {

  private final Map<String, StatementSummary> stmtSummaries = new HashMap<>();

  // Stack for tracking nested statement execution to calculate exclusive time
  private final Deque<StatementSummary> timerStack = new ArrayDeque<>();

  @Override
  public void startStatement(Stmt stmt) {
    String name = getStatementName(stmt);
    if (!stmtSummaries.containsKey(name)) {
      stmtSummaries.put(name, new StatementSummary(name));
    }
    timerStack.push(stmtSummaries.get(name));
  }

  @Override
  public void stopStatement(Stmt stmt, long durationNanos) {
    StatementSummary currentTimer = timerStack.pop();
    if (!currentTimer.getName().equals(getStatementName(stmt))) {
      throw new IllegalStateException(
          "Statement profiler timers are out of order. Expected: "
              + currentTimer.getName()
              + ", but got: "
              + getStatementName(stmt));
    }

    Duration dur = Duration.ofNanos(durationNanos);
    currentTimer.add(dur);

    // Subtract this statement's time from parent to get parent's exclusive time
    if (!timerStack.isEmpty()) {
      timerStack.peek().subtract(dur);
    }
  }

  @Override
  public Map<String, StatementSummary> getStatementSummaries() {
    return Collections.unmodifiableMap(stmtSummaries);
  }

  /**
   * Get a descriptive name for a statement.
   *
   * <p>For function calls, uses the function name. For other statements, uses the statement type
   * name.
   *
   * @param stmt the statement
   * @return a human-readable name for the statement
   */
  private String getStatementName(Stmt stmt) {
    if (stmt.getType() == STMT_TYPE.CALL) {
      return ((CallStmt) stmt).getFunc();
    } else {
      return stmt.getType().getTypeName();
    }
  }
}
