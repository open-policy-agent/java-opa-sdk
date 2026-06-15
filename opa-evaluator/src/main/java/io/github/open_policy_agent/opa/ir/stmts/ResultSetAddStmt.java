package io.github.open_policy_agent.opa.ir.stmts;


/**
 * ResultSetAddStmt adds a value into the result set returned by the query plan.
 */
public class ResultSetAddStmt extends BaseStmt {
    public static final String StmtType = "ResultSetAddStmt";

    private int value;

    public ResultSetAddStmt() {
    }

    public ResultSetAddStmt(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    public void setValue(int value) {
        this.value = value;
    }

  @Override
  public STMT_TYPE getType() {
    return STMT_TYPE.RESULT_SET_ADD;
    }

  @Override
  public int maxLocal() {
    return value;
  }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ResultSetAddStmt that = (ResultSetAddStmt) o;

        if (value != that.value) return false;
        if (getFile() != that.getFile()) return false;
        if (getCol() != that.getCol()) return false;
        return getRow() == that.getRow();
    }

    @Override
    public int hashCode() {
        int result = value;
        result = 31 * result + getFile();
        result = 31 * result + getCol();
        result = 31 * result + getRow();
        return result;
    }

    @Override
    public String toString() {
        return "ResultSetAddStmt{"
                + "value="
                + value
                + ", file="
                + getFile()
                + ", col="
                + getCol()
                + ", row="
                + getRow()
                + '}';
    }
}
