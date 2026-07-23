package io.github.open_policy_agent.opa.ast.builtin.impls;

import java.util.Map;
import java.util.function.BiFunction;
import io.github.open_policy_agent.opa.ast.builtin.OpaBuiltin;
import io.github.open_policy_agent.opa.ast.builtin.OpaType;
import io.github.open_policy_agent.opa.ast.types.RegoArray;
import io.github.open_policy_agent.opa.ast.types.RegoBoolean;
import io.github.open_policy_agent.opa.ast.types.RegoNull;
import io.github.open_policy_agent.opa.ast.types.RegoNumber;
import io.github.open_policy_agent.opa.ast.types.RegoObject;
import io.github.open_policy_agent.opa.ast.types.RegoSet;
import io.github.open_policy_agent.opa.ast.types.RegoString;
import io.github.open_policy_agent.opa.ast.types.RegoValue;
import io.github.open_policy_agent.opa.rego.EvaluationContext;

public class TypeBuiltins {

  // TODO: ?need to implement all the string cases
  public static Map<String, BiFunction<EvaluationContext, RegoValue[], RegoValue>> builtins() {
    TypeBuiltins instance = new TypeBuiltins();
    return Map.of(
        "is_number", instance::isNumber,
        "is_null", instance::isNull,
        "is_array", instance::isArray,
        "is_boolean", instance::isBoolean,
        "is_set", instance::isSet,
        "is_string", instance::isString,
        "is_object", instance::isObject,
        "type_name", instance::typeName);
    }

  @OpaBuiltin(
      name = "type_name",
      description = "Returns the type of its input value.",
      categories = {"types"},
      args = {@OpaType(type = "any", name = "x", description = "input value")},
      result =
          @OpaType(
              type = "string",
              name = "type",
              description =
                  "one of \"null\", \"boolean\", \"number\", \"string\", \"array\", \"object\", \"set\""))
  public RegoString typeName(EvaluationContext ctx, RegoValue[] args) {
        if (args == null || args[0] == null) {
            return new RegoString("null");
        }
        return new RegoString(args[0].getTypeName());
    }

  @OpaBuiltin(
      name = "is_number",
      description = "Returns `true` if the input value is a number.",
      categories = {"types"},
      args = {@OpaType(type = "any", name = "x", description = "input value")},
      result =
          @OpaType(
              type = "boolean",
              name = "result",
              description = "`true` if `x` is a number, `false` otherwise."))
  public RegoBoolean isNumber(EvaluationContext ctx, RegoValue[] args) {

        if (args[0] instanceof RegoNumber) {
            return RegoBoolean.TRUE;
        }

        return RegoBoolean.FALSE;
    }

  @OpaBuiltin(
      name = "is_null",
      description = "Returns `true` if the input value is null.",
      categories = {"types"},
      args = {@OpaType(type = "any", name = "x", description = "input value")},
      result =
          @OpaType(
              type = "boolean",
              name = "result",
              description = "`true` if `x` is null, `false` otherwise."))
  public RegoBoolean isNull(EvaluationContext ctx, RegoValue[] args) {

        if (args[0] == null || args[0] instanceof RegoNull) {
            return RegoBoolean.TRUE;
        }

        return RegoBoolean.FALSE;
    }

  @OpaBuiltin(
      name = "is_array",
      description = "Returns `true` if the input value is an array.",
      categories = {"types"},
      args = {@OpaType(type = "any", name = "x", description = "input value")},
      result =
          @OpaType(
              type = "boolean",
              name = "result",
              description = "`true` if `x` is an array, `false` otherwise."))
  public RegoBoolean isArray(EvaluationContext ctx, RegoValue[] args) {

        if (args[0] instanceof RegoArray) {
            return RegoBoolean.TRUE;
        }

        return RegoBoolean.FALSE;
    }

  @OpaBuiltin(
      name = "is_boolean",
      description = "Returns `true` if the input value is a boolean.",
      categories = {"types"},
      args = {@OpaType(type = "any", name = "x", description = "input value")},
      result =
          @OpaType(
              type = "boolean",
              name = "result",
              description = "`true` if `x` is an boolean, `false` otherwise."))
  public RegoBoolean isBoolean(EvaluationContext ctx, RegoValue[] args) {

        if (args[0] instanceof RegoBoolean) {
            return RegoBoolean.TRUE;
        }

        return RegoBoolean.FALSE;
    }

  @OpaBuiltin(
      name = "is_set",
      description = "Returns `true` if the input value is a set.",
      categories = {"types"},
      args = {@OpaType(type = "any", name = "x", description = "input value")},
      result =
          @OpaType(
              type = "boolean",
              name = "result",
              description = "`true` if `x` is a set, `false` otherwise."))
  public RegoBoolean isSet(EvaluationContext ctx, RegoValue[] args) {

        if (args[0] instanceof RegoSet) {
            return RegoBoolean.TRUE;
        }

        return RegoBoolean.FALSE;
    }

  @OpaBuiltin(
      name = "is_string",
      description = "Returns `true` if the input value is a string.",
      categories = {"types"},
      args = {@OpaType(type = "any", name = "x", description = "input value")},
      result =
          @OpaType(
              type = "boolean",
              name = "result",
              description = "`true` if `x` is a string, `false` otherwise."))
  public RegoBoolean isString(EvaluationContext ctx, RegoValue[] args) {

        if (args[0] instanceof RegoString) {
            return RegoBoolean.TRUE;
        }

        return RegoBoolean.FALSE;
    }

  @OpaBuiltin(
      name = "is_object",
      description = "Returns true if the input value is an object",
      categories = {"types"},
      args = {@OpaType(type = "any", name = "x", description = "input value")},
      result =
          @OpaType(
              type = "boolean",
              name = "result",
              description = "`true` if `x` is an object, `false` otherwise."))
  public RegoBoolean isObject(EvaluationContext ctx, RegoValue[] args) {

        if (args[0] instanceof RegoObject) {
            return RegoBoolean.TRUE;
        }

        return RegoBoolean.FALSE;
    }
}