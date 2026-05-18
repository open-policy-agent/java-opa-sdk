package io.github.open_policy_agent.opa.ir.stmts;


/**
 * ReturnLocalStmt represents a return statement that yields a local value.
 */
public class ReturnLocalStmt extends BaseStmt {
    public static final String StmtType = "ReturnLocalStmt";

    private int source;

    public ReturnLocalStmt(int source) {
        this.source = source;
    }

    public ReturnLocalStmt() {
    }

    public int getSource() {
        return source;
    }

    public void setSource(int source) {
        this.source = source;
    }

  @Override
  public STMT_TYPE getType() {
    return STMT_TYPE.RETURN_LOCAL;
    }

  @Override
  public int maxLocal() {
    return source;
  }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ReturnLocalStmt that = (ReturnLocalStmt) o;

        if (source != that.source) return false;
        if (getFile() != that.getFile()) return false;
        if (getCol() != that.getCol()) return false;
        return getRow() == that.getRow();
    }

    @Override
    public int hashCode() {
        int result = source;
        result = 31 * result + getFile();
        result = 31 * result + getCol();
        result = 31 * result + getRow();
        return result;
    }

    @Override
    public String toString() {
        return "ReturnLocalStmt{"
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
