package io.github.open_policy_agent.opa.ast.types;

import java.math.BigInteger;
import java.util.Objects;

public class RegoBigInt implements RegoValue, RegoNumber {
    private BigInteger value;

    public RegoBigInt(BigInteger i) {
        this.value = i;
    }

    public RegoBigInt(Long l) {
        this.value = new BigInteger(String.valueOf(l));
    }

    public void setValue(BigInteger i) {
        this.value = i;
    }

    public BigInteger getValue() {
        return value;
    }

    public Object nativeValue() {
        return value;
    }

  public Double getDecimalValue() {
    return value.doubleValue();
    }

    public BigInteger getBigIntValue() {
        return value;
    }

    public String getTypeName() { return "number";}

    @Override
    public boolean equals(Object o) {
        if (o instanceof RegoInt32) {
            RegoInt32 i = (RegoInt32) o;
            return i.getBigIntValue().equals(value);
        } else if (o instanceof RegoBigInt) {
            RegoBigInt l = (RegoBigInt) o;
            return l.getValue().equals(value);
        } else if (o instanceof RegoDecimal) {
            RegoDecimal d = (RegoDecimal) o;
            return value.floatValue() == d.getValue();
        } else if (o instanceof RegoString) {
            RegoString s = (RegoString) o;
            try {
                return new BigInteger(s.getValue()).equals(value);
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
        return value==null?"null":value.toString();
    }
}
