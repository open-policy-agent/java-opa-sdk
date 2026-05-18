package io.github.open_policy_agent.opa.ir.stmts;

import java.util.Objects;
import io.github.open_policy_agent.opa.ir.Operand;

/**
 * EqualStmt represents a value-equality check of two local variables.
 */
public class EqualStmt extends BaseStmt {
    public static final String StmtType = "EqualStmt";

    private Operand a;

    private Operand b;

    public EqualStmt() {
    }

    public EqualStmt(Operand a, Operand b) {
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
  public STMT_TYPE getType() {
    return STMT_TYPE.EQUAL;
    }

  @Override
  public int maxLocal() {
    int max = -1;
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

        EqualStmt equalStmt = (EqualStmt) o;

        if (getFile() != equalStmt.getFile()) return false;
        if (getCol() != equalStmt.getCol()) return false;
        if (getRow() != equalStmt.getRow()) return false;
        if (!Objects.equals(a, equalStmt.a)) return false;
        return Objects.equals(b, equalStmt.b);
    }

    @Override
    public int hashCode() {
        int result = a != null ? a.hashCode() : 0;
        result = 31 * result + (b != null ? b.hashCode() : 0);
        result = 31 * result + getFile();
        result = 31 * result + getCol();
        result = 31 * result + getRow();
        return result;
    }

    @Override
    public String toString() {
        return "EqualStmt{"
                + "a="
                + a
                + ", b="
                + b
                + ", file="
                + getFile()
                + ", col="
                + getCol()
                + ", row="
                + getRow()
                + '}';
    }
}
