package io.github.open_policy_agent.opa.ir.stmts;

import io.github.open_policy_agent.opa.ir.Frame;
import io.github.open_policy_agent.opa.ir.IREvaluationContext;

/**
 * NopStmt adds a nop instruction. Useful during development and debugging only.
 */
public class NopStmt extends BaseStmt {

    public NopStmt() {
    }

    public NopStmt(int file, int col, int row) {
        super(file, col, row);
    }

    public void evaluate(Frame parent, IREvaluationContext ctx) {
    }

  @Override
  public StmtType getType() {
    return StmtType.NOP;
    }

  @Override
  public int maxLocal() {
    return -1;
  }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        NopStmt nopStmt = (NopStmt) o;

        if (getFile() != nopStmt.getFile()) return false;
        if (getCol() != nopStmt.getCol()) return false;
        return getRow() == nopStmt.getRow();
    }

    @Override
    public int hashCode() {
        int result = getFile();
        result = 31 * result + getCol();
        result = 31 * result + getRow();
        return result;
    }

    @Override
    public String toString() {
        return "NopStmt{" + "file=" + getFile() + ", col=" + getCol() + ", row=" + getRow() + '}';
    }
}
