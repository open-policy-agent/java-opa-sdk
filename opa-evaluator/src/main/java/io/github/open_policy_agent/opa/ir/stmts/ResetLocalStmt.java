package io.github.open_policy_agent.opa.ir.stmts;


/**
 * ResetLocalStmt resets a local variable to 0.
 */
public class ResetLocalStmt extends BaseStmt {
    public static final String StmtType = "ResetLocalStmt";

    private int target;

    public ResetLocalStmt(int target) {
        this.target = target;
    }

    public ResetLocalStmt() {
    }

    public int getTarget() {
        return target;
    }

    public void setTarget(int target) {
        this.target = target;
    }

  @Override
  public STMT_TYPE getType() {
    return STMT_TYPE.RESET_LOCAL;
    }

  @Override
  public int maxLocal() {
    return target;
  }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ResetLocalStmt that = (ResetLocalStmt) o;

        if (target != that.target) return false;
        if (getFile() != that.getFile()) return false;
        if (getCol() != that.getCol()) return false;
        return getRow() == that.getRow();
    }

    @Override
    public int hashCode() {
        int result = target;
        result = 31 * result + getFile();
        result = 31 * result + getCol();
        result = 31 * result + getRow();
        return result;
    }

    @Override
    public String toString() {
        return "ResetLocalStmt{"
                + "target="
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
