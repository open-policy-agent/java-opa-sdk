package io.github.open_policy_agent.opa.ir.stmts;


/**
 * MakeNumberIntStmt constructs a local variable that refers to an integer value.
 */
public class MakeNumberIntStmt extends BaseStmt {

    private long value;

    private int target;

    public MakeNumberIntStmt(long value, int target) {
        this.value = value;
        this.target = target;
    }

    public MakeNumberIntStmt() {
    }

    public long getValue() {
        return value;
    }

    public void setValue(long value) {
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
    return StmtType.MAKE_NUMBER_INT;
    }

  @Override
  public int maxLocal() {
    return target;
  }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        MakeNumberIntStmt that = (MakeNumberIntStmt) o;

        if (value != that.value) return false;
        if (target != that.target) return false;
        if (getFile() != that.getFile()) return false;
        if (getCol() != that.getCol()) return false;
        return getRow() == that.getRow();
    }

    @Override
    public int hashCode() {
        int result = (int) (value ^ (value >>> 32));
        result = 31 * result + target;
        result = 31 * result + getFile();
        result = 31 * result + getCol();
        result = 31 * result + getRow();
        return result;
    }

    @Override
    public String toString() {
        return "MakeNumberIntStmt{"
                + "value="
                + value
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
