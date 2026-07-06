package io.github.open_policy_agent.opa.ir.stmts;

import java.util.List;
import java.util.Objects;
import io.github.open_policy_agent.opa.ir.Operand;

/**
 * CallDynamicStmt represents an indirect (data) function call. The result should be stored in the
 * result local.
 */
public class CallDynamicStmt extends BaseStmt {

    private List<Operand> path;

    private List<Integer> args;

    private int result;

    public CallDynamicStmt() {
    }

    public CallDynamicStmt(List<Operand> path, List<Integer> args, int result) {
        this.path = path;
        this.args = args;
        this.result = result;
    }

    public List<Operand> getPath() {
        return path;
    }

    public void setPath(List<Operand> path) {
        this.path = path;
    }

    public List<Integer> getArgs() {
        return args;
    }

    public void setArgs(List<Integer> args) {
        this.args = args;
    }

    public int getResult() {
        return result;
    }

    public void setResult(int result) {
        this.result = result;
    }

  @Override
  public StmtType getType() {
    return StmtType.CALL_DYNAMIC;
    }

  @Override
  public int maxLocal() {
    int max = result;
    if (path != null) {
      for (Operand operand : path) {
        int operandLocal = getLocalFromOperand(operand);
        if (operandLocal > max) {
          max = operandLocal;
        }
      }
    }
    if (args != null) {
      for (Integer arg : args) {
        if (arg > max) {
          max = arg;
        }
      }
    }
    return max;
  }

    @Override
    public String toString() {
        return "CallDynamicStmt{"
                + "path="
                + path
                + ", args="
                + args
                + ", result="
                + result
                + ", file="
                + getFile()
                + ", col="
                + getCol()
                + ", row="
                + getRow()
                + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        CallDynamicStmt that = (CallDynamicStmt) o;

        if (result != that.result) return false;
        if (getFile() != that.getFile()) return false;
        if (getCol() != that.getCol()) return false;
        if (getRow() != that.getRow()) return false;
        if (!Objects.equals(path, that.path)) return false;
        return Objects.equals(args, that.args);
    }

    @Override
    public int hashCode() {
        int result1 = path != null ? path.hashCode() : 0;
        result1 = 31 * result1 + (args != null ? args.hashCode() : 0);
        result1 = 31 * result1 + result;
        result1 = 31 * result1 + getFile();
        result1 = 31 * result1 + getCol();
        result1 = 31 * result1 + getRow();
        return result1;
    }
}
