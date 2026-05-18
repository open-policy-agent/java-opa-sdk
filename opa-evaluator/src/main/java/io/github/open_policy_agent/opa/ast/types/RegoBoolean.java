package io.github.open_policy_agent.opa.ast.types;


public class RegoBoolean implements RegoValue {

  public static final RegoBoolean TRUE = new RegoBoolean(true);
  public static final RegoBoolean FALSE = new RegoBoolean(false);

  final boolean value;

  private RegoBoolean(boolean value) {
    this.value = value;
  }

  public boolean getValue() {
    return value;
  }

  @Override
  public Object nativeValue() {
    return value;
  }

  public String getTypeName() {
    return "boolean";
  }

  @Override
  public final boolean equals(Object o) {
    if (!(o instanceof RegoBoolean)) return false;

    return value == ((RegoBoolean) o).value;
  }

  public static RegoBoolean of(boolean value) {
    if (value) {
      return TRUE;
    }
    return FALSE;
  }

  @Override
  public int hashCode() {
    return Boolean.hashCode(value);
  }

  @Override
  public String toString() {
    return Boolean.toString(value);
  }
}
