package io.github.open_policy_agent.opa.ast.types;

import java.math.BigDecimal;
import java.math.BigInteger;

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
      return equalsInteger(value, i.getValue());
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
    @SuppressWarnings("PMD.AvoidDecimalLiteralsInBigDecimalConstructor")
    public int hashCode() {
        // Whole values hash like the RegoInt32/RegoBigInt they equal, so hash lookups agree with equals().
        double d = value;
        if (d == Math.rint(d) && d >= Long.MIN_VALUE && d < 0x1p63) {
            // Same result as BigInteger.hashCode() for this value, without allocating.
            long v = (long) d;
            long mag = Math.abs(v);
            int h = 31 * (int) (mag >>> 32) + (int) mag;
            return v < 0 ? -h : h;
        }
        if (Double.isFinite(d) && d == Math.rint(d)) {
            // Exact conversion. BigDecimal.valueOf would round through Double.toString.
            return new BigDecimal(d).toBigInteger().hashCode();
        }
        return Double.hashCode(d);
    }

    // Exact comparison, like OPA. Converting v to a double would round above 2^53.
    @SuppressWarnings("PMD.AvoidDecimalLiteralsInBigDecimalConstructor")
    static boolean equalsInteger(double d, BigInteger v) {
        if (d != Math.rint(d)) {
            return false;
        }
        if (d >= Long.MIN_VALUE && d < 0x1p63) {
            return v.bitLength() < 64 && v.longValue() == (long) d;
        }
        return Double.isFinite(d) && new BigDecimal(d).toBigInteger().equals(v);
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
