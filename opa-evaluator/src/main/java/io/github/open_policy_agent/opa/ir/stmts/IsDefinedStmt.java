package io.github.open_policy_agent.opa.ir.stmts;


/**
 * IsDefinedStmt represents a check of whether a local variable is defined.
 */
public class IsDefinedStmt extends BaseStmt {

    private int source;

    public IsDefinedStmt() {
    }

    public IsDefinedStmt(int source) {
        this.source = source;
    }

    public int getSource() {
        return source;
    }

    public void setSource(int source) {
        this.source = source;
    }

  @Override
  public StmtType getType() {
    return StmtType.IS_DEFINED;
    }

  @Override
  public int maxLocal() {
    return source;
  }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        IsDefinedStmt that = (IsDefinedStmt) o;

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
        return "IsDefinedStmt{"
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
