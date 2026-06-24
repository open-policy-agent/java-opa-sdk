package io.github.open_policy_agent.opa.ir.stmts;

import java.util.Objects;
import io.github.open_policy_agent.opa.ir.Operand;

/**
 * DotStmt represents a lookup operation on a value (e.g., array, object, etc.). The source of a
 * DotStmt may be a scalar value, in which case the statement will be undefined.
 */
public class DotStmt extends BaseStmt {

    private Operand source;

    private Operand key;

    private int target;

    public DotStmt() {
    }

    public DotStmt(Operand source, Operand key, int target) {
        this.source = source;
        this.key = key;
        this.target = target;
    }

  @Override
  public StmtType getType() {
    return StmtType.DOT;
    }

  @Override
  public int maxLocal() {
    int max = target;
    int sourceLocal = getLocalFromOperand(source);
    if (sourceLocal > max) {
      max = sourceLocal;
    }
    int keyLocal = getLocalFromOperand(key);
    if (keyLocal > max) {
      max = keyLocal;
    }
    return max;
  }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        DotStmt dotStmt = (DotStmt) o;

        if (target != dotStmt.target) return false;
        if (getFile() != dotStmt.getFile()) return false;
        if (getCol() != dotStmt.getCol()) return false;
        if (getRow() != dotStmt.getRow()) return false;
        if (!Objects.equals(source, dotStmt.source)) return false;
        return Objects.equals(key, dotStmt.key);
    }

    @Override
    public int hashCode() {
        int result = source != null ? source.hashCode() : 0;
        result = 31 * result + (key != null ? key.hashCode() : 0);
        result = 31 * result + target;
        result = 31 * result + getFile();
        result = 31 * result + getCol();
        result = 31 * result + getRow();
        return result;
    }

    public Operand getSource() {
        return source;
    }

    public void setSource(Operand source) {
        this.source = source;
    }

    public Operand getKey() {
        return key;
    }

    public void setKey(Operand key) {
        this.key = key;
    }

    public int getTarget() {
        return target;
    }

    public void setTarget(int target) {
        this.target = target;
    }

    @Override
    public String toString() {
        return "DotStmt{"
                + "source="
                + source
                + ", key="
                + key
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
