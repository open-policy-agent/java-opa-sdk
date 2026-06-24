package io.github.open_policy_agent.opa.ir.stmts;

/**
 * MakeNumberRefStmt constructs a local variable that refers to a number stored as a string.
 */
public class MakeNumberRefStmt extends BaseStmt {

    private int index;

    private int target;

    public MakeNumberRefStmt() {
    }

    public MakeNumberRefStmt(int index, int target) {
        this.index = index;
        this.target = target;
    }

    public int getIndex() {
        return index;
    }

    public void setIndex(int index) {
        this.index = index;
    }

    public int getTarget() {
        return target;
    }

    public void setTarget(int target) {
        this.target = target;
    }

  @Override
  public StmtType getType() {
    return StmtType.MAKE_NUMBER_REF;
    }

  @Override
  public int maxLocal() {
    return target;
  }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        MakeNumberRefStmt that = (MakeNumberRefStmt) o;

        if (index != that.index) return false;
        if (target != that.target) return false;
        if (getFile() != that.getFile()) return false;
        if (getCol() != that.getCol()) return false;
        return getRow() == that.getRow();
    }

    @Override
    public int hashCode() {
        int result = index;
        result = 31 * result + target;
        result = 31 * result + getFile();
        result = 31 * result + getCol();
        result = 31 * result + getRow();
        return result;
    }

    @Override
    public String toString() {
        return "MakeNumberRefStmt{"
                + "index="
                + index
                + ", target="
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
