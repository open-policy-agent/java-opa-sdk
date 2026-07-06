package io.github.open_policy_agent.opa.ir.stmts;

import java.util.Objects;
import io.github.open_policy_agent.opa.ir.Operand;

/**
 * IsArrayStmt represents a dynamic type check on a local variable.
 */
public class IsArrayStmt extends BaseStmt {

    private Operand source;

    public IsArrayStmt() {
    }

    public IsArrayStmt(Operand source) {
        this.source = source;
    }

    public Operand getSource() {
        return source;
    }

    public void setSource(Operand source) {
        this.source = source;
    }

  @Override
  public StmtType getType() {
    return StmtType.IS_ARRAY;
    }

  @Override
  public int maxLocal() {
    return getLocalFromOperand(source);
  }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        IsArrayStmt that = (IsArrayStmt) o;

        if (getFile() != that.getFile()) return false;
        if (getCol() != that.getCol()) return false;
        if (getRow() != that.getRow()) return false;
        return Objects.equals(source, that.source);
    }

    @Override
    public int hashCode() {
        int result = source != null ? source.hashCode() : 0;
        result = 31 * result + getFile();
        result = 31 * result + getCol();
        result = 31 * result + getRow();
        return result;
    }

    @Override
    public String toString() {
        return "IsArrayStmt{"
                + "source="
                + source
                + ", file="
                + getFile()
                + ", col="
                + getCol()
                + ", row="
                + getRow()
                + '}';
    }
}
