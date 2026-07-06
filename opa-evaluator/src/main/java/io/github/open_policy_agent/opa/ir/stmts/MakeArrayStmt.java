package io.github.open_policy_agent.opa.ir.stmts;


/**
 * MakeArrayStmt constructs a local variable that refers to an array value.
 */
public class MakeArrayStmt extends BaseStmt {

    private int capacity;

    private int target;

    public MakeArrayStmt(int capacity, int target) {
        this.capacity = capacity;
        this.target = target;
    }

    public MakeArrayStmt() {
    }

    public int getCapacity() {
        return capacity;
    }

    public void setCapacity(int capacity) {
        this.capacity = capacity;
    }

    public int getTarget() {
        return target;
    }

    public void setTarget(int target) {
        this.target = target;
    }

  @Override
  public StmtType getType() {
    return StmtType.MAKE_ARRAY;
    }

  @Override
  public int maxLocal() {
    return target;
  }

  @Override
  public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        MakeArrayStmt that = (MakeArrayStmt) o;

        if (capacity != that.capacity) return false;
        if (target != that.target) return false;
        if (getFile() != that.getFile()) return false;
        if (getCol() != that.getCol()) return false;
        return getRow() == that.getRow();
    }

    @Override
    public int hashCode() {
        int result = capacity;
        result = 31 * result + target;
        result = 31 * result + getFile();
        result = 31 * result + getCol();
        result = 31 * result + getRow();
        return result;
    }

    @Override
    public String toString() {
        return "MakeArrayStmt{"
                + "capacity="
                + capacity
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
