package io.github.open_policy_agent.opa.ast.types;

import java.math.BigInteger;
import java.util.Objects;

public class RegoInt32 implements RegoValue, RegoNumber {
    private Integer value;

  private static final RegoInt32[] INSTANCES =
      new RegoInt32[] {
        new RegoInt32(0),
        new RegoInt32(1),
        new RegoInt32(2),
        new RegoInt32(3),
        new RegoInt32(4),
        new RegoInt32(5),
        new RegoInt32(6),
        new RegoInt32(7),
        new RegoInt32(8),
        new RegoInt32(9),
        new RegoInt32(10),
        new RegoInt32(11),
        new RegoInt32(12),
        new RegoInt32(13),
        new RegoInt32(14),
        new RegoInt32(15),
        new RegoInt32(16),
        new RegoInt32(17),
        new RegoInt32(18),
        new RegoInt32(19),
        new RegoInt32(20)
      };

  private RegoInt32(Integer value) {
    this.value = value;
    }

    public RegoInt32(String number) {
        this.value = Integer.parseInt(number);
    }

    public Object nativeValue() {
        return value;
    }

    public void setValue(Integer i) {
        this.value = i;
    }

    public Integer getValue() {
        return value;
    }

  public Double getDecimalValue() {
    return value.doubleValue();
    }

    public BigInteger getBigIntValue() {
        return BigInteger.valueOf(value);
    }

    public String getTypeName() { return "number";}

  public static RegoInt32 of(Integer i) {
    if (i >= 0 && i <= 20) {
      return INSTANCES[i];
    } else {
      return new RegoInt32(i);
    }
  }

    @Override
    public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null) return false;

        if (o instanceof RegoInt32) {
      RegoInt32 other = (RegoInt32) o;
      return Objects.equals(value, other.value);
        } else if (o instanceof RegoBigInt) {
      RegoBigInt other = (RegoBigInt) o;
      return other.getBigIntValue().equals(BigInteger.valueOf(value));
        } else if (o instanceof RegoDecimal) {
      RegoDecimal other = (RegoDecimal) o;
      return value.floatValue() == other.getValue();
    }

    return false;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(value);
    }

    @Override
    public String toString() {
        return value==null?"null":value.toString();
    }
}
