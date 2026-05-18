package io.github.open_policy_agent.opa.ir.stmts;

import java.util.Objects;
import io.github.open_policy_agent.opa.ir.Operand;

/**
 * AssignVarStmt represents an assignment of one local variable to another.
 */
public class AssignVarStmt extends BaseStmt {
    public static final String StmtType = "AssignVarStmt";

    private Operand source;

    private int target;

    public AssignVarStmt() {
    }

    public AssignVarStmt(Operand source, int target) {
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
  public STMT_TYPE getType() {
    return STMT_TYPE.ASSIGN_VAR;
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

        AssignVarStmt that = (AssignVarStmt) o;

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
        return "AssignVarStmt{"
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
