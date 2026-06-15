package io.github.open_policy_agent.opa.ir.stmts;


/**
 * MakeSetStmt constructs a local variable that refers to a set value.
 */
public class MakeSetStmt extends BaseStmt {
    public static final String StmtType = "MakeSetStmt";

    private int target;

    public MakeSetStmt(int target) {
        this.target = target;
    }

    public MakeSetStmt() {
    }

    public int getTarget() {
        return target;
    }

    public void setTarget(int target) {
        this.target = target;
    }

  @Override
  public STMT_TYPE getType() {
    return STMT_TYPE.MAKE_SET;
    }

  @Override
  public int maxLocal() {
    return target;
  }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        MakeSetStmt that = (MakeSetStmt) o;

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
        return "MakeSetStmt{"
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
