package io.github.open_policy_agent.opa.ast.types;


public class RegoUndefined implements RegoValue {

  public static final RegoUndefined INSTANCE = new RegoUndefined();

  private RegoUndefined() {}

  private Object getProperty() {
    return null;
  }

  @Override
  public Object nativeValue() {
    return null;
  }

  public String getTypeName() {
    return "undefined";
  }

  @Override
  public boolean equals(Object o) {
    return false;
  }

  @Override
  public int hashCode() {
    return System.identityHashCode(this);
  }

  @Override
  public String toString() {
    return "undefined";
  }
}
