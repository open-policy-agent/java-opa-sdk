package io.github.open_policy_agent.opa.ir.stmts;

import java.util.List;
import java.util.Objects;
import io.github.open_policy_agent.opa.ir.Operand;

/** CallStmt represents a named function call. The result should be stored in the result local. */
public class CallStmt extends BaseStmt {

  private final int maxLocal = Integer.MIN_VALUE;

  private String func;

  private List<Operand> args;

  private int result;

  public CallStmt() {}

  public CallStmt(String func, List<Operand> args, int result) {
    this.func = func;
    this.args = args;
    this.result = result;
  }

  public String getFunc() {
    return func;
  }

  public void setFunc(String func) {
    this.func = func;
  }

  public List<Operand> getArgs() {
    return args;
  }

  public void setArgs(List<Operand> args) {
    this.args = args;
  }

  public int getResult() {
    return result;
  }

  public void setResult(int result) {
    this.result = result;
  }

  public int getMaxLocal() {
    return result;
  }

  @Override
  public StmtType getType() {
    return StmtType.CALL;
  }

  @Override
  public int maxLocal() {
    int max = result;
    if (args != null) {
      for (Operand arg : args) {
        int argLocal = getLocalFromOperand(arg);
        if (argLocal > max) {
          max = argLocal;
        }
      }
    }
    return max;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    CallStmt callStmt = (CallStmt) o;

    if (result != callStmt.result) return false;
    if (getFile() != callStmt.getFile()) return false;
    if (getCol() != callStmt.getCol()) return false;
    if (getRow() != callStmt.getRow()) return false;
    if (!Objects.equals(func, callStmt.func)) return false;
    return Objects.equals(args, callStmt.args);
  }

  @Override
  public int hashCode() {
    int result1 = func != null ? func.hashCode() : 0;
    result1 = 31 * result1 + (args != null ? args.hashCode() : 0);
    result1 = 31 * result1 + result;
    result1 = 31 * result1 + getFile();
    result1 = 31 * result1 + getCol();
    result1 = 31 * result1 + getRow();
    return result1;
  }

  @Override
  public String toString() {
    return "CallStmt{"
        + "func='"
        + func
        + '\''
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
}
