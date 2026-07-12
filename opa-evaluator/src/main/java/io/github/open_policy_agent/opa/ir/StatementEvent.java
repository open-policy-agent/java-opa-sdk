package io.github.open_policy_agent.opa.ir;

import java.util.Objects;

import io.github.open_policy_agent.opa.ir.stmts.Stmt;
import io.github.open_policy_agent.opa.tracing.Event;
import io.github.open_policy_agent.opa.tracing.Operation;

public class StatementEvent extends Event {
  private final Stmt stmt;
  private final String statement;
  private final int blockIndex;
  private final int stmtIndex;

  public StatementEvent(
          Operation op, Stmt stmt, String stmtKind, Location location, int blockIndex, int stmtIndex) {
    super(op, location);
    this.stmt = stmt;
    this.statement = stmtKind;
    this.blockIndex = blockIndex;
    this.stmtIndex = stmtIndex;
  }

  public String getStatement() {
    return statement;
  }

  public Stmt getStmt() {
    return stmt;
  }

  public static StatementEvent fromString(String line) {
    String[] parts = line.split(",");
    Operation op = Operation.valueOf(parts[0].split("=")[1].trim());
    String statement = parts[1].split("=")[1].trim();

    int file = Integer.parseInt(parts[2].split("=")[1].trim());
    int row = Integer.parseInt(parts[3].split("=")[1].trim());
    int col = Integer.parseInt(parts[4].split("=")[1].trim());
    Location location = new Location(file, row, col);

    int blockIndex = Integer.parseInt(parts[5].split("=")[1].trim());
    int stmtIndex = Integer.parseInt(parts[6].split("=")[1].trim());

    return new StatementEvent(op, null, statement, location, blockIndex, stmtIndex);
  }

  @Override
  public boolean equals(Object o) {
      if (this == o) {
          return true;
      }
      if (o == null || getClass() != o.getClass()) {
          return false;
      }
      if (!super.equals(o)) {
          return false;
      }
    StatementEvent that = (StatementEvent) o;
    return blockIndex == that.blockIndex
        && stmtIndex == that.stmtIndex
        && Objects.equals(statement, that.statement);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), statement, blockIndex, stmtIndex);
  }

  @Override
  public String toString() {
    return "op="
        + getOp()
        + ",statement="
        + getStatement()
        + ",file="
        + getLocation().getFile()
        + ",row="
        + getLocation().getRow()
        + ",col="
        + getLocation().getCol()
        + ",blockIndex="
        + blockIndex
        + ",stmtIndex="
        + stmtIndex;
  }
}
