package io.github.open_policy_agent.opa.ast.builtin.impls.utils;

import io.github.open_policy_agent.opa.ast.types.RegoArray;
import io.github.open_policy_agent.opa.ast.types.RegoCollection;
import io.github.open_policy_agent.opa.ast.types.RegoSet;
import io.github.open_policy_agent.opa.ast.types.RegoValue;
import io.github.open_policy_agent.opa.ast.types.TypeUtils;
import io.github.open_policy_agent.opa.rego.TypeError;

import java.util.Arrays;
import java.util.stream.Collectors;

public class ArgHelper {
    public static <T extends RegoValue> T getArg(RegoValue[] args, int index, Class<T> clazz) {
        RegoValue arg = args[index];

        if (!clazz.isInstance(arg)) {
            if (clazz.equals(RegoCollection.class)) {
                throw new TypeError("operand " +
                        (index + 1) +
                        " must be one of {" +
                        TypeUtils.getRegoTypeName(RegoSet.class) +
                        ", " +
                        TypeUtils.getRegoTypeName(RegoArray.class) +
                        "} but got " +
                        arg.getTypeName());
            }

            throw new TypeError("operand " +
                    (index + 1) +
                    " must be " +
                    TypeUtils.getRegoTypeName(clazz) +
                    " but got " +
                    arg.getTypeName());
        }

        return clazz.cast(arg);
    }

    public static void assertArgType(RegoValue[] args, int index, Class<?>... clazzs) {
        assertArgType(args[index], index, clazzs);
    }

    public static void assertArgType(RegoValue arg, int index, Class<?>... clazzs) {
        for (Class<?> clazz : clazzs) {
            if (clazz.isInstance(arg)) {
                return;
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("operand ");
        sb.append(index + 1);
        sb.append(" must be one of {");

        sb.append(
                Arrays.stream(clazzs)
                        .map(TypeUtils::getRegoTypeName)
                        .collect(Collectors.joining(", "))
        );

        sb.append("} but got ");
        sb.append(arg.getTypeName());

        throw new TypeError(sb.toString());
    }
}
