package io.github.open_policy_agent.opa.ir;

import io.github.open_policy_agent.opa.ast.types.RegoValue;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Frame {
  int breakTarget = -1;
  private final RegoValue[] locals;
  // Tracks which local indices hold values derived directly from the data document via DotStmt.
  // AssignVarStmt does NOT propagate this flag, matching OPA semantics where integer keys
  // coerce to string only during direct data-reference traversal.
  private final Set<Integer> dataLocals = new HashSet<>();
    // caller holds a reference to the frame that created the current frame; the initial frame that
    // starts the
    // evaluation will not have a caller
    private Frame caller;
    // contains the result of a function call which will be set by either a `CallStmt` or a
    // `DynamicCallStmt`
    // via the ReturnLocalStmt
    private RegoValue result;
    // accumulates results from ResultSetAddStmt (plan-level result set)
    private List<RegoValue> resultSet = null;
    // true if this frame is executing a user-defined function (params > 2)
    private boolean userFunction = false;
    private String funcName;

  private Frame(RegoValue[] locals) {
    this.locals = locals;
  }

  private Frame(Builder builder) {
    this.locals = builder.locals;
    }

  public RegoValue getLocal(int i) {
    if (i < 0 || i >= locals.length) {
      throw new EvaluationException("Local index out of bounds: " + i)
          .withContext("index", i)
          .withContext("capacity", locals.length);
    }
    return locals[i];
  }

  public RegoValue setLocal(int i, RegoValue value) {
    if (i < 0 || i >= locals.length) {
      throw new EvaluationException("Local index out of bounds: " + i)
          .withContext("index", i)
          .withContext("capacity", locals.length);
    }
    RegoValue old = locals[i];
    locals[i] = value;
    return old;
  }

    public RegoValue getResult() {
        return result;
    }

    public void setResult(RegoValue result) {
        this.result = result;
    }

    public void addToResultSet(RegoValue value) {
        if (resultSet == null) {
            resultSet = new ArrayList<>();
        }
        resultSet.add(value);
    }

    public List<RegoValue> getResultSet() {
        return resultSet;
    }

    public Frame getCaller() {
        return caller;
    }

    public void setBreakTarget(int breakTarget) {
        this.breakTarget = breakTarget;
    }

  public boolean shouldBreak() {
    return breakTarget > -1;
  }

  public void decrementBreakTarget() {
    if (breakTarget < 0) {
      throw new EvaluationException("breakTarget is negative");
    }
    this.breakTarget--;
  }

    public int getBreakTarget() {
        return this.breakTarget;
    }

  public void markDataDerived(int local) {
    dataLocals.add(local);
  }

  public boolean isDataDerived(int local) {
    return dataLocals.contains(local);
  }

  public Frame subFrame(RegoValue[] locals) {
    Frame newFrame = new Frame(locals);
    newFrame.caller = this;
    return newFrame;
  }

  public boolean isUserFunction() {
    return userFunction;
  }

  public String getFuncName() {
    return funcName;
  }

  public void setFuncName(String funcName) {
    this.funcName = funcName;
  }

  public void setUserFunction(boolean userFunction) {
    this.userFunction = userFunction;
  }

  public static class Builder {
    private RegoValue[] locals;

    public Builder withLocalCapacity(int i) {
      this.locals = new RegoValue[i];
      return this;
    }

    public Frame build() {
      return new Frame(this);
    }
  }
}
