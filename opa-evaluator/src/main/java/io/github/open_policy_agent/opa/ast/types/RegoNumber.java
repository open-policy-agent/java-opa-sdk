package io.github.open_policy_agent.opa.ast.types;


import java.math.BigInteger;

public interface RegoNumber extends RegoValue {

  Double getDecimalValue();

    BigInteger getBigIntValue();

    default boolean isDecimal() {
        return false;
    }

    /**
     * Whether the number's text has a fraction or exponent (e.g. {@code 1.0}, {@code 1e2}). OPA
     * keeps the text of whole numbers written that way and sprintf formats them as floats.
     */
    default boolean isFloatText() {
        return isDecimal();
    }

}
