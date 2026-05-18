package io.github.open_policy_agent.opa.ast.types;

import java.math.BigInteger;
import java.util.Objects;

public class RegoDecimal implements RegoValue, RegoNumber {
  private Double value;
  private String originalString; // Store original string for numbers that overflow to Infinity

  public RegoDecimal(Double d) {
    this.value = d;
  }

  public RegoDecimal(Double d, String originalString) {
    this.value = d;
    this.originalString = originalString;
  }

  // For backwards compatibility - convert Float to Double
  public RegoDecimal(Float f) {
    this.value = f.doubleValue();
    }

  public Double getValue() {
        return value;
    }

  public void setValue(Double d) {
    this.value = d;
    }

    public Object nativeValue() {
        return value;
    }

  public Double getDecimalValue() {
        return value;
    }

    public BigInteger getBigIntValue() {
        return BigInteger.valueOf(value.longValue());
    }

    public boolean isDecimal() {
        return true;
    }

    public String getTypeName() { return "number";}

    @Override
    public boolean equals(Object o) {
        if (o instanceof RegoInt32) {
            RegoInt32 i = (RegoInt32) o;
      return i.getValue().doubleValue() == value;
        } else if (o instanceof RegoBigInt) {
            RegoBigInt i = (RegoBigInt) o;
      return i.getValue().doubleValue() == value;
        } else if (o instanceof RegoDecimal) {
            RegoDecimal l = (RegoDecimal) o;
            return l.getValue().equals(value);
        } else if (o instanceof RegoString) {
            RegoString s = (RegoString) o;
            try {
        return Double.parseDouble(s.getValue()) == value;
            } catch (NumberFormatException e) {
                return false;
            }
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(value);
    }

    @Override
    public String toString() {
    if (value == null) {
      return "null";
    }
    // If we have an original string and the value is infinite, use the original
    if (Double.isInfinite(value) && originalString != null) {
      return originalString;
    }
    return value.toString();
    }
}
