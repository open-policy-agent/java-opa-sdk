package io.github.open_policy_agent.opa.ir.stmts;

import java.util.Objects;
import io.github.open_policy_agent.opa.ir.Operand;

/**
 * NotEqualStmt represents a != check of two local variables.
 */
public class NotEqualStmt extends BaseStmt {

    private Operand a;

    private Operand b;

    private int source;

    public NotEqualStmt() {
    }

    public NotEqualStmt(Operand a, Operand b) {
        this.a = a;
        this.b = b;
    }

    public Operand getA() {
        return a;
    }

    public void setA(Operand a) {
        this.a = a;
    }

    public Operand getB() {
        return b;
    }

    public void setB(Operand b) {
        this.b = b;
    }

  @Override
  public StmtType getType() {
    return StmtType.NOT_EQUAL;
    }

  @Override
  public int maxLocal() {
    int max = source;
    int aLocal = getLocalFromOperand(a);
    if (aLocal > max) {
      max = aLocal;
    }
    int bLocal = getLocalFromOperand(b);
    if (bLocal > max) {
      max = bLocal;
    }
    return max;
  }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        NotEqualStmt that = (NotEqualStmt) o;

        if (source != that.source) return false;
        if (getFile() != that.getFile()) return false;
        if (getCol() != that.getCol()) return false;
        if (getRow() != that.getRow()) return false;
        if (!Objects.equals(a, that.a)) return false;
        return Objects.equals(b, that.b);
    }

    @Override
    public int hashCode() {
        int result = a != null ? a.hashCode() : 0;
        result = 31 * result + (b != null ? b.hashCode() : 0);
        result = 31 * result + source;
        result = 31 * result + getFile();
        result = 31 * result + getCol();
        result = 31 * result + getRow();
        return result;
    }

    @Override
    public String toString() {
        return "NotEqualStmt{"
                + "a="
                + a
                + ", b="
                + b
                + ", source="
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
