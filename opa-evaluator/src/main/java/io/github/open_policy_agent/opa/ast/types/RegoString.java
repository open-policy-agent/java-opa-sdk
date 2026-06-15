package io.github.open_policy_agent.opa.ast.types;


import java.math.BigInteger;
import java.util.Objects;

public class RegoString implements RegoValue {
    private final String value;

    public RegoString(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }


    @Override
    public Object nativeValue() {
        return value;
    }

    @Override
    public int length() {
        return value.length();
    }

    public String getTypeName() { return "string";}

    @Override
    public String toString() {
        return value==null?"null":"\""+value+"\"";  //This formatting is for readability in the debugger
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof RegoString) {
            RegoString s = (RegoString) o;
            return value.equals(s.getValue());
        } else if (o instanceof RegoDecimal) {
            RegoDecimal d = (RegoDecimal) o;
            try {
                return Float.parseFloat(value) == d.getValue();
            } catch (NumberFormatException e) {
                return false;
            }
        } else if (o instanceof RegoInt32) {
            RegoInt32 i = (RegoInt32) o;
            try {
                return Integer.parseInt(value) == i.getValue();
            } catch (NumberFormatException e) {
                return false;
            }
        } else if (o instanceof RegoBigInt) {
            RegoBigInt i = (RegoBigInt) o;
            try {
                return BigInteger.valueOf(Long.parseLong(value)).equals(i.getValue());
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
}
