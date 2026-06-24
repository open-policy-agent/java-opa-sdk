package io.github.open_policy_agent.opa.ir.stmts;

import java.util.Objects;
import io.github.open_policy_agent.opa.ir.Operand;

/**
 * AssignVarOnceStmt represents an assignment of one local variable to another. If the target is
 * defined, execution aborts with a conflict error.
 */
public class AssignVarOnceStmt extends BaseStmt {

    private Operand source;

    private int target;

    public AssignVarOnceStmt() {
    }

    public AssignVarOnceStmt(Operand source, int target) {
        this.source = source;
        this.target = target;
    }

    public int getTarget() {
        return target;
    }

    public void setTarget(int target) {
        this.target = target;
    }

    public Operand getSource() {
        return source;
    }

    public void setSource(Operand source) {
        this.source = source;
    }

  @Override
  public StmtType getType() {
    return StmtType.ASSIGN_VAR_ONCE;
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

        AssignVarOnceStmt that = (AssignVarOnceStmt) o;

        if (target != that.target) return false;
        if (getFile() != that.getFile()) return false;
        if (getCol() != that.getCol()) return false;
        if (getRow() != that.getRow()) return false;
        return Objects.equals(source, that.source);
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
        return "AssignVarOnceStmt{"
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
