package io.github.open_policy_agent.opa.ir.stmts;


/**
 * IsUndefinedStmt represents a check of whether a local variable is undefined.
 */
public class IsUndefinedStmt extends BaseStmt {
    public static final String StmtType = "IsUndefinedStmt";

    private int source;

    public IsUndefinedStmt(int source) {
        this.source = source;
    }

    public IsUndefinedStmt() {
    }

    public int getSource() {
        return source;
    }

    public void setSource(int source) {
        this.source = source;
    }

  @Override
  public STMT_TYPE getType() {
    return STMT_TYPE.IS_UNDEFINED;
    }

  @Override
  public int maxLocal() {
    return source;
  }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        IsUndefinedStmt that = (IsUndefinedStmt) o;

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
        return "IsUndefinedStmt{"
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
