package io.github.open_policy_agent.opa.ast.builtin.impls;

import java.math.BigInteger;
import java.util.Map;
import java.util.function.BiFunction;

import io.github.open_policy_agent.opa.ast.builtin.BuiltinError;
import io.github.open_policy_agent.opa.ast.builtin.OpaBuiltin;
import io.github.open_policy_agent.opa.ast.builtin.OpaType;
import io.github.open_policy_agent.opa.ast.types.RegoBigInt;
import io.github.open_policy_agent.opa.ast.types.RegoBoolean;
import io.github.open_policy_agent.opa.ast.types.RegoDecimal;
import io.github.open_policy_agent.opa.ast.types.RegoInt32;
import io.github.open_policy_agent.opa.ast.types.RegoNull;
import io.github.open_policy_agent.opa.ast.types.RegoNumber;
import io.github.open_policy_agent.opa.ast.types.RegoString;
import io.github.open_policy_agent.opa.ast.types.RegoValue;
import io.github.open_policy_agent.opa.ast.types.TypeUtils;
import io.github.open_policy_agent.opa.rego.EvaluationContext;
import io.github.open_policy_agent.opa.rego.TypeError;

public class CastBuiltins {

    public static Map<String, BiFunction<EvaluationContext, RegoValue[], RegoValue>> builtins() {
        CastBuiltins instance = new CastBuiltins();
        return Map.of("to_number", instance::toNumber);
    }

    @OpaBuiltin(
            name = "to_number",
            description =
                    "Converts a string, bool, or number value to a number: Strings are converted to numbers using `strconv.Atoi`, Boolean `false` is converted to 0 and `true` is converted to 1.",
            categories = {"conversions"},
            args = {@OpaType(type = "any", name = "x", description = "value to convert")},
            result =
            @OpaType(
                    type = "number",
                    name = "num",
                    description = "the numeric representation of `x`"))
    public RegoValue toNumber(EvaluationContext ctx, RegoValue[] args) {

        if (args[0] instanceof RegoNumber) {
            return args[0];
        } else if (args[0] == null || args[0] instanceof RegoNull) {
            return new RegoBigInt(new BigInteger("0"));
        } else if (args[0] instanceof RegoBoolean) {
            if (((RegoBoolean) args[0]).getValue()) {
                return RegoInt32.of(1);
            } else {
                return RegoInt32.of(0);
            }
        } else if (args[0] instanceof RegoString) {
            String s = ((RegoString) args[0]).getValue();
            String sLower = s.toLowerCase();
            if (sLower.equals("nan") || sLower.equals("-nan") || sLower.equals("+nan")
                    || sLower.equals("inf") || sLower.equals("-inf") || sLower.equals("+inf")
                    || sLower.equals("infinity") || sLower.equals("-infinity") || sLower.equals("+infinity")) {
                throw new TypeError("operand 1 must be valid number string but got " + s);
            }
            try {
                RegoValue result = TypeUtils.parseStringToNumber(s);
                if (result instanceof RegoDecimal) {
                    double d = ((RegoDecimal) result).getValue();
                    if (Double.isNaN(d) || Double.isInfinite(d)) {
                        throw new TypeError("operand 1 must be valid number string but got " + s);
                    }
                }
                return result;
            } catch (BuiltinError e) {
                throw e;
            }
        } else {
            throw new TypeError("expected number, boolean, or string, got " + args[0].getTypeName());
        }
    }
}