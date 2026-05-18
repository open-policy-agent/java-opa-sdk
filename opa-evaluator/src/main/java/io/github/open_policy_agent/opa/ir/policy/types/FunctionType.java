package io.github.open_policy_agent.opa.ir.policy.types;

import java.util.List;
import java.util.Objects;

/** Function represents a function type. */
public class FunctionType implements Type {
  public static final String TypeMarker = "function";

  private List<Type> args;

  private Type result;

  private Type variadic;

  public FunctionType() {}

  public FunctionType(List<Type> args, Type result, Type variadic) {
    this.args = args;
    this.result = result;
    this.variadic = variadic;
  }

  public FunctionType(List<Type> args, Type result) {
    this.args = args;
    this.result = result;
  }

  @Override
  public String typeMarker() {
    return TypeMarker;
  }

  public List<Type> getArgs() {
    return args;
  }

  public void setArgs(List<Type> args) {
    this.args = args;
  }

  public Type getResult() {
    return result;
  }

  public void setResult(Type result) {
    this.result = result;
  }

  public Type getVariadic() {
    return variadic;
  }

  public void setVariadic(Type variadic) {
    this.variadic = variadic;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    FunctionType that = (FunctionType) o;

    if (!Objects.equals(args, that.args)) return false;
    if (!Objects.equals(result, that.result)) return false;
    return Objects.equals(variadic, that.variadic);
  }

  @Override
  public int hashCode() {
    int result1 = args != null ? args.hashCode() : 0;
    result1 = 31 * result1 + (result != null ? result.hashCode() : 0);
    result1 = 31 * result1 + (variadic != null ? variadic.hashCode() : 0);
    return result1;
  }

  @Override
  public String toString() {
    return "FunctionType{" + "args=" + args + ", result=" + result + ", variadic=" + variadic + '}';
  }
}
