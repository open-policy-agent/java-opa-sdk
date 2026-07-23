package io.github.open_policy_agent.opa.ast.builtin.impls;

import java.math.BigInteger;
import java.util.Map;
import java.util.function.BiFunction;

import io.github.open_policy_agent.opa.ast.builtin.OpaBuiltin;
import io.github.open_policy_agent.opa.ast.builtin.OpaType;
import io.github.open_policy_agent.opa.ast.builtin.OpaVal;
import io.github.open_policy_agent.opa.ast.types.RegoArray;
import io.github.open_policy_agent.opa.ast.types.RegoBigInt;
import io.github.open_policy_agent.opa.ast.types.RegoBoolean;
import io.github.open_policy_agent.opa.ast.types.RegoCollection;
import io.github.open_policy_agent.opa.ast.types.RegoDecimal;
import io.github.open_policy_agent.opa.ast.types.RegoInt32;
import io.github.open_policy_agent.opa.ast.types.RegoNull;
import io.github.open_policy_agent.opa.ast.types.RegoNumber;
import io.github.open_policy_agent.opa.ast.types.RegoObject;
import io.github.open_policy_agent.opa.ast.types.RegoString;
import io.github.open_policy_agent.opa.ast.types.RegoValue;
import io.github.open_policy_agent.opa.rego.EvaluationContext;
import io.github.open_policy_agent.opa.rego.TypeError;

public class AggregateBuiltins {

    public static Map<String, BiFunction<EvaluationContext, RegoValue[], RegoValue>> builtins() {
        AggregateBuiltins instance = new AggregateBuiltins();
        return Map.of(
                "min", instance::min,
                "max", instance::max,
                "sort", instance::sort,
                "sum", instance::sum,
                "count", instance::count,
                "product", instance::product,
                "internal.member_2", instance::member);
    }

    @OpaBuiltin(
            name = "min",
            description = "Returns the minimum value in a collection",
            categories = {"aggregates"},
            args = {
                    @OpaType(
                            name = "collection",
                            description = "the set or array to be searched",
                            of = {@OpaVal("array[any]"), @OpaVal("set[any]")})
            },
            result = @OpaType(type = "any", description = "the minimum of all elements"))
    public RegoValue min(EvaluationContext ctx, RegoValue[] args) {
        RegoCollection collection = (RegoCollection) args[0];

        return collection.valueStream().min(RegoValue::compareTo).orElse(RegoNull.INSTANCE);
    }

    @OpaBuiltin(
            name = "max",
            description = "Returns the maximum value in a collection",
            categories = {"aggregates"},
            args = {
                    @OpaType(
                            name = "collection",
                            description = "the set or array to be searched",
                            of = {@OpaVal("array[any]"), @OpaVal("set[any]")})
            },
            result = @OpaType(type = "any", description = "the maximum of all elements"))
    public RegoValue max(EvaluationContext ctx, RegoValue[] args) {
        RegoCollection collection = ((RegoCollection) args[0]);

        return collection.valueStream().max(RegoValue::compareTo).orElse(RegoNull.INSTANCE);
    }

    @OpaBuiltin(
            name = "sort",
            description = "Returns a sorted array",
            categories = {"aggregates"},
            args = {
                    @OpaType(
                            name = "collection",
                            description = "the array or set to be sorted",
                            of = {@OpaVal("array[any]"), @OpaVal("set[any]")})
            },
            result = @OpaType(type = "array", description = "the sorted array"))
    public RegoArray sort(EvaluationContext ctx, RegoValue[] args) {
        RegoCollection collection = (RegoCollection) args[0];

        return collection.valueStream().sorted(RegoValue::compareTo).collect(RegoArray::new, RegoArray::addValue, RegoArray::addValue);
    }

    @OpaBuiltin(
            name = "sum",
            description = "Sums elements of an array or set of numbers",
            categories = {"aggregates"},
            args = {
                    @OpaType(
                            name = "collection",
                            description = "the set or array of numbers to sum",
                            of = {@OpaVal("array[number]"), @OpaVal("set[number]")})
            },
            result = @OpaType(type = "number", description = "the sum of all elements"))
    public RegoNumber sum(EvaluationContext ctx, RegoValue[] args) {
        RegoCollection collection = (RegoCollection) args[0];

        if (collection.valueStream()
                .map(v -> (RegoNumber) v)
                .anyMatch(RegoNumber::isDecimal)) {
            double sum =
                    collection
                            .valueStream()
                            .map(value -> (RegoNumber) value)
                            .map(RegoNumber::getDecimalValue)
                            .reduce(0.0, (Double::sum));
            return new RegoDecimal(sum);
        } else {
            BigInteger sum = collection.valueStream()
                    .map(value -> (RegoNumber) value)
                    .map(RegoNumber::getBigIntValue)
                    .reduce(BigInteger.ZERO, (BigInteger::add));
            return new RegoBigInt(sum);
        }
    }

    @OpaBuiltin(
            name = "product",
            description = "Multiplies elements of an array or set of numbers",
            categories = {"aggregates"},
            args = {
                    @OpaType(
                            name = "collection",
                            description = "the set or array of numbers to multiply",
                            of = {@OpaVal("array[number]"), @OpaVal("set[number]")})
            },
            result = @OpaType(type = "number", description = "the product of all elements"))
    public RegoNumber product(EvaluationContext ctx, RegoValue[] args) {
        RegoCollection collection = (RegoCollection) args[0];

        if (collection.valueStream()
                .map(v -> (RegoNumber) v)
                .anyMatch(RegoNumber::isDecimal)) {
            double product =
                    collection
                            .valueStream()
                            .map(value -> (RegoNumber) value)
                            .map(RegoNumber::getDecimalValue)
                            .reduce(1.0, ((a, b) -> a * b));
            return new RegoDecimal(product);
        } else {
            BigInteger product = collection.valueStream()
                    .map(value -> (RegoNumber) value)
                    .map(RegoNumber::getBigIntValue)
                    .reduce(BigInteger.ONE, (BigInteger::multiply));
            return new RegoBigInt(product);
        }

    }

    @OpaBuiltin(
            name = "count",
            description = "Returns the length of a collection, object, or string",
            categories = {"aggregates"},
            args = {
                    @OpaType(
                            name = "collection",
                            description = "collection to count elements of",
                            of = {@OpaVal("array[any]"), @OpaVal("set[any]"), @OpaVal("object"), @OpaVal("string")})
            },
            result =
            @OpaType(
                    type = "number",
                    name = "n",
                    description = "the count of elements, key/val pairs, or characters, respectively."))
    public RegoInt32 count(EvaluationContext ctx, RegoValue[] args) {

        if (args[0] instanceof RegoCollection
                || args[0] instanceof RegoObject
                || args[0] instanceof RegoString
        ) {
            return RegoInt32.of(args[0].length());
        } else {
            throw new TypeError("operand 1 must be one of {array, object, set, string} but got " + args[0].getTypeName());
        }
    }

    public RegoBoolean member(EvaluationContext ctx, RegoValue[] args) {
        RegoValue x = args[0];
        RegoValue y = args[1];

        if (y instanceof RegoCollection) {
            if (((RegoCollection) y).valueStream().anyMatch(v -> v.equals(x))) {
                return RegoBoolean.TRUE;
            }
        } else if (y instanceof RegoObject) {
            // For objects, check if x is a value in the object
            RegoObject obj = (RegoObject) y;
            if (obj.getProperties().values().stream().anyMatch(v -> v.equals(x))) {
                return RegoBoolean.TRUE;
            }
        }
        // For non-collections (string, number, etc.), return false
        return RegoBoolean.FALSE;
    }
}