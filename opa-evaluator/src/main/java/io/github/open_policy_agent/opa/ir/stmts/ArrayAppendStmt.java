package io.github.open_policy_agent.opa.ir.stmts;

import java.util.Objects;
import io.github.open_policy_agent.opa.ir.Operand;

/**
 * ArrayAppendStmt represents a dynamic append operation of a value onto an array.
 */
public class ArrayAppendStmt extends BaseStmt {
    public static final String StmtType = "ArrayAppendStmt";

    private Operand value;

    private int array;

    public ArrayAppendStmt() {
    }

    public ArrayAppendStmt(Operand value, int array) {
        this.value = value;
        this.array = array;
    }

    public Operand getValue() {
        return value;
    }

    public void setValue(Operand value) {
        this.value = value;
    }

    public int getArray() {
        return array;
    }

    public void setArray(int array) {
        this.array = array;
    }

  @Override
  public STMT_TYPE getType() {
    return STMT_TYPE.ARRAY_APPEND;
    }

  @Override
  public int maxLocal() {
    int max = array;
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

        ArrayAppendStmt that = (ArrayAppendStmt) o;

        if (getFile() != that.getFile()) return false;
        if (getCol() != that.getCol()) return false;
        if (getRow() != that.getRow()) return false;
        if (!Objects.equals(value, that.value)) return false;
        return Objects.equals(array, that.array);
    }

    @Override
    public int hashCode() {
        int result = value != null ? value.hashCode() : 0;
        result = 31 * result + array;
        result = 31 * result + getFile();
        result = 31 * result + getCol();
        result = 31 * result + getRow();
        return result;
    }

    @Override
    public String toString() {
        return "ArrayAppendStmt{"
                + "value="
                + value
                + ", array="
                + array
                + ", file="
                + getFile()
                + ", col="
                + getCol()
                + ", row="
                + getRow()
                + '}';
    }
}
