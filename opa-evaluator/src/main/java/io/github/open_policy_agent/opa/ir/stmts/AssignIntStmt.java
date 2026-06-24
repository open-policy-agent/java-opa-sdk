package io.github.open_policy_agent.opa.ir.stmts;

import java.util.Objects;

/**
 * AssignIntStmt represents a dynamic append operation of a value onto an array.
 */
public class AssignIntStmt extends BaseStmt {

    private Long value;

    private int target;

    public AssignIntStmt() {
    }

    public AssignIntStmt(Long value, int target) {
        this.value = value;
        this.target = target;
    }

    public Long getValue() {
        return value;
    }

    public void setValue(Long value) {
        this.value = value;
    }

    public int getTarget() {
        return target;
    }

    public void setTarget(int target) {
        this.target = target;
    }

  @Override
  public StmtType getType() {
    return StmtType.ASSIGN_INT;
  }

  @Override
  public int maxLocal() {
    return target;
  }

  @Override
  public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        AssignIntStmt that = (AssignIntStmt) o;

        if (target != that.target) return false;
        if (getFile() != that.getFile()) return false;
        if (getCol() != that.getCol()) return false;
        if (getRow() != that.getRow()) return false;
        return Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        int result = value != null ? value.hashCode() : 0;
        result = 31 * result + target;
        result = 31 * result + getFile();
        result = 31 * result + getCol();
        result = 31 * result + getRow();
        return result;
    }

    @Override
    public String toString() {
        return "AssignIntStmt{" + "value=" + value + ", target=" + target + ", file=" + getFile() + ", col=" + getCol() + ", row=" + getRow() + '}';
    }
}
