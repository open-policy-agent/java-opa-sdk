package io.github.open_policy_agent.opa.ir.stmts;

import java.util.Objects;
import io.github.open_policy_agent.opa.ir.Operand;

/**
 * LenStmt represents a length() operation on a local variable. The result is stored in the target
 * local variable.
 */
public class LenStmt extends BaseStmt {

    private Operand source;

    private int target;

    public LenStmt() {
    }

    public LenStmt(Operand source, int target) {
        this.source = source;
        this.target = target;
    }

    public Operand getSource() {
        return source;
    }

    public void setSource(Operand source) {
        this.source = source;
    }

    public int getTarget() {
        return target;
    }

    public void setTarget(int target) {
        this.target = target;
    }

  @Override
  public StmtType getType() {
    return StmtType.LEN;
    }

  @Override
  public int maxLocal() {
    int max = target;
    int sourceLocal = getLocalFromOperand(source);
    if (sourceLocal > max) {
      max = sourceLocal;
    }
    return max;
  }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        LenStmt lenStmt = (LenStmt) o;

        if (target != lenStmt.target) return false;
        if (getFile() != lenStmt.getFile()) return false;
        if (getCol() != lenStmt.getCol()) return false;
        if (getRow() != lenStmt.getRow()) return false;
        return Objects.equals(source, lenStmt.source);
    }

    @Override
    public int hashCode() {
        int result = source != null ? source.hashCode() : 0;
        result = 31 * result + target;
        result = 31 * result + getFile();
        result = 31 * result + getCol();
        result = 31 * result + getRow();
        return result;
    }

    @Override
    public String toString() {
        return "LenStmt{"
                + "source="
                + source
                + ", target="
                + target
                + ", file="
                + getFile()
                + ", col="
                + getCol()
                + ", row="
                + getRow()
                + '}';
    }
}
