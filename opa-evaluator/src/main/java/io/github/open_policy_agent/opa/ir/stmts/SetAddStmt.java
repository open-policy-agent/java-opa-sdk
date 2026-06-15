package io.github.open_policy_agent.opa.ir.stmts;

import java.util.Objects;
import io.github.open_policy_agent.opa.ir.Operand;

/**
 * SetAddStmt represents a dynamic add operation of an element into a set.
 */
public class SetAddStmt extends BaseStmt {
    public static final String StmtType = "SetAddStmt";

    private Operand value;

    private int set;

    public SetAddStmt() {
    }

    public SetAddStmt(Operand value, int set) {
        this.value = value;
        this.set = set;
    }

    public Operand getValue() {
        return value;
    }

    public void setValue(Operand value) {
        this.value = value;
    }

    public int getSet() {
        return set;
    }

    public void setSet(int set) {
        this.set = set;
    }

  @Override
  public STMT_TYPE getType() {
    return STMT_TYPE.SET_ADD;
    }

  @Override
  public int maxLocal() {
    int max = set;
    int valueLocal = getLocalFromOperand(value);
    if (valueLocal > max) {
      max = valueLocal;
    }
    return max;
  }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        SetAddStmt that = (SetAddStmt) o;

        if (set != that.set) return false;
        if (getFile() != that.getFile()) return false;
        if (getCol() != that.getCol()) return false;
        if (getRow() != that.getRow()) return false;
        return Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        int result = value != null ? value.hashCode() : 0;
        result = 31 * result + set;
        result = 31 * result + getFile();
        result = 31 * result + getCol();
        result = 31 * result + getRow();
        return result;
    }

    @Override
    public String toString() {
        return "SetAddStmt{"
                + "value="
                + value
                + ", set="
                + set
                + ", file="
                + getFile()
                + ", col="
                + getCol()
                + ", row="
                + getRow()
                + '}';
    }
}
