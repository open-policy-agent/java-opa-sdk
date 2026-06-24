package io.github.open_policy_agent.opa.ir.stmts;


/**
 * ObjectMergeStmt performs a recursive merge of two object values. If either of the locals refer to
 * non-object values this operation will abort with a conflict error. Overlapping object keys are
 * merged recursively.
 */
public class ObjectMergeStmt extends BaseStmt {

    private int a;

    private int b;

    private int target;

    public ObjectMergeStmt() {
    }

    public ObjectMergeStmt(int a, int b, int target) {
        this.a = a;
        this.b = b;
        this.target = target;
    }

  @Override
  public StmtType getType() {
    return StmtType.OBJECT_MERGE;
    }

  @Override
  public int maxLocal() {
    int max = a;
    if (b > max) {
      max = b;
    }
    if (target > max) {
      max = target;
    }
    return max;
  }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ObjectMergeStmt that = (ObjectMergeStmt) o;

        if (a != that.a) return false;
        if (b != that.b) return false;
        if (target != that.target) return false;
        if (getFile() != that.getFile()) return false;
        if (getCol() != that.getCol()) return false;
        return getRow() == that.getRow();
    }

    @Override
    public int hashCode() {
        int result = a;
        result = 31 * result + b;
        result = 31 * result + target;
        result = 31 * result + getFile();
        result = 31 * result + getCol();
        result = 31 * result + getRow();
        return result;
    }

    public int getA() {
        return a;
    }

    public void setA(int a) {
        this.a = a;
    }

    public int getB() {
        return b;
    }

    public void setB(int b) {
        this.b = b;
    }

    public int getTarget() {
        return target;
    }

    public void setTarget(int target) {
        this.target = target;
    }

    @Override
    public String toString() {
        return "ObjectMergeStmt{"
                + "a="
                + a
                + ", b="
                + b
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
