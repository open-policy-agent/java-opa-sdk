package io.github.open_policy_agent.opa.ast.builtin.impls;

import static java.lang.Math.max;
import static java.lang.Math.min;
import static io.github.open_policy_agent.opa.ast.builtin.impls.utils.ArgHelper.getArg;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import io.github.open_policy_agent.opa.ast.builtin.OpaBuiltin;
import io.github.open_policy_agent.opa.ast.builtin.OpaDynamic;
import io.github.open_policy_agent.opa.ast.builtin.OpaType;
import io.github.open_policy_agent.opa.ast.types.RegoArray;
import io.github.open_policy_agent.opa.ast.types.RegoInt32;
import io.github.open_policy_agent.opa.ast.types.RegoValue;
import io.github.open_policy_agent.opa.rego.EvaluationContext;
import io.github.open_policy_agent.opa.rego.TypeError;

public class ArrayBuiltins {

  public static Map<String, BiFunction<EvaluationContext, RegoValue[], RegoValue>> builtins() {
    return Map.of(
        "array.concat", new ArrayConcat()::eval,
        "array.slice", new ArraySlice()::eval,
        "array.reverse", new ArrayReverse()::eval,
        "array.flatten", new ArrayFlatten()::eval);
    }

    private static final class ArrayConcat {
    @OpaBuiltin(
        name = "array.concat",
        description = "Concatenates two arrays.",
        args = {
          @OpaType(
              type = "array",
              name = "x",
              description = "the first array",
              dynamic = @OpaDynamic(type = "any")),
          @OpaType(
              type = "array",
              name = "y",
              description = "the second array",
              dynamic = @OpaDynamic(type = "any"))
        },
        result =
            @OpaType(
                type = "array",
                name = "z",
                description = "the concatenation of `x` and `y`",
                dynamic = @OpaDynamic(type = "any")))
    public RegoValue eval(EvaluationContext ctx, RegoValue[] args) {
            if (!(args[0] instanceof RegoArray)) {
              throw new TypeError("array.concat: operand 1 must be array but got " + args[0].getTypeName());
            }
            if (!(args[1] instanceof RegoArray)) {
              throw new TypeError("array.concat: operand 2 must be array but got " + args[1].getTypeName());
            }
            RegoArray x = (RegoArray) args[0];
            RegoArray y = (RegoArray) args[1];

            RegoArray z = new RegoArray();
            z.getValue().addAll(x.getValue());
            z.getValue().addAll(y.getValue());
            return z;
        }
    }

    private static final class ArraySlice {
    @OpaBuiltin(
        name = "array.slice",
        description =
            "Returns a slice of a given array. If `start` is greater or equal than `stop`, `slice` is `[]`.",
        args = {
          @OpaType(
              type = "array",
              name = "arr",
              description = "the array to be sliced",
              dynamic = @OpaDynamic(type = "any")),
          @OpaType(
              type = "number",
              name = "start",
              description =
                  "the start index of the returned slice; if less than zero, it's clamped to 0"),
          @OpaType(
              type = "number",
              name = "stop",
              description =
                  "the stop index of the returned slice; if larger than `count(arr)`, it's clamped to `count(arr)`")
        },
        result =
            @OpaType(
                type = "array",
                name = "slice",
                description =
                    "the subslice of `array`, from `start` to `end`, including `arr[start]`, but excluding `arr[end]`",
                dynamic = @OpaDynamic(type = "any")))
    public RegoValue eval(EvaluationContext ctx, RegoValue[] args) {
            List<RegoValue> x = getArg(args, 0, RegoArray.class).getValue();

            int start = max(getArg(args, 1, RegoInt32.class).getValue(), 0);
            int stop = min(getArg(args, 2, RegoInt32.class).getValue(), x.size());

            if (start > x.size() || stop < start) {
                return new RegoArray();
            } else {
                RegoArray z = new RegoArray();
                z.getValue().addAll(x.subList(start, stop));
                return z;
            }
        }
    }

    private static final class ArrayReverse {
    @OpaBuiltin(
        name = "array.reverse",
        description = "Returns the reverse of a given array.",
        args = {
          @OpaType(
              type = "array",
              name = "arr",
              description = "the array to be reversed",
              dynamic = @OpaDynamic(type = "any"))
        },
        result =
            @OpaType(
                type = "array",
                name = "rev",
                description = "an array containing the elements of `arr` in reverse order",
                dynamic = @OpaDynamic(type = "any")))
    public RegoValue eval(EvaluationContext ctx, RegoValue[] args) {
            if (!(args[0] instanceof RegoArray)) {
              throw new TypeError("array.reverse: operand 1 must be array but got " + args[0].getTypeName());
            }
            List<RegoValue> arr = ((RegoArray) args[0]).getValue();

            RegoArray rev = new RegoArray(arr);
            Collections.reverse(rev.getValue());
            return rev;
        }
    }

    private static final class ArrayFlatten {
    @OpaBuiltin(
        name = "array.flatten",
        description = "Flattens one level of a given array.",
        args = {
          @OpaType(
              type = "array",
              name = "arr",
              description = "the array to be flattened",
              dynamic = @OpaDynamic(type = "any"))
        },
        result =
            @OpaType(
                type = "array",
                name = "flattened",
                description = "array flattened one level",
                dynamic = @OpaDynamic(type = "any")))
    public RegoValue eval(EvaluationContext ctx, RegoValue[] args) {
            if (!(args[0] instanceof RegoArray)) {
              throw new TypeError("array.flatten: operand 1 must be array but got " + args[0].getTypeName());
            }
            List<RegoValue> arr = ((RegoArray) args[0]).getValue();

            RegoArray flattened = new RegoArray();
            for (RegoValue element : arr) {
              if (element instanceof RegoArray) {
                flattened.getValue().addAll(((RegoArray) element).getValue());
              } else {
                flattened.getValue().add(element);
              }
            }
            return flattened;
        }
    }
}