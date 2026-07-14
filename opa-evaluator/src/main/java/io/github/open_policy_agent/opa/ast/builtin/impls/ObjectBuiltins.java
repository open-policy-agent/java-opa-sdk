package io.github.open_policy_agent.opa.ast.builtin.impls;

import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import io.github.open_policy_agent.opa.ast.builtin.OpaBuiltin;
import io.github.open_policy_agent.opa.ast.builtin.OpaDynamic;
import io.github.open_policy_agent.opa.ast.builtin.OpaType;
import io.github.open_policy_agent.opa.ast.types.*;
import io.github.open_policy_agent.opa.rego.EvaluationContext;
import io.github.open_policy_agent.opa.rego.TypeError;

import static io.github.open_policy_agent.opa.ast.builtin.impls.utils.ArgHelper.assertArgType;
import static io.github.open_policy_agent.opa.ast.builtin.impls.utils.ArgHelper.getArg;

public class ObjectBuiltins {

  public static Map<String, BiFunction<EvaluationContext, RegoValue[], RegoValue>> builtins() {
    ObjectBuiltins instance = new ObjectBuiltins();
    return Map.of(
        "object.get", instance::get,
        "object.keys", instance::keys,
        "object.filter", instance::filter,
        "object.remove", instance::remove,
        "object.subset", instance::subset,
        "object.union", instance::union,
        "object.union_n", instance::unionN);
  }

  @OpaBuiltin(
      name = "object.union_n",
      description =
          "Returns a new object containing all key/value pairs from the supplied objects. "
              + "If the supplied `objects` is an `array`, then `object.union_n` will search "
              + "through a nested object or array using each key in turn. For example: "
              + "`object.union_n([{\"a\": 1}, {\"b\": 2}, {\"c\": 3}])` results in "
              + "`{\"a\": 1, \"b\": 2, \"c\": 3}`.",
      args = {
        @OpaType(
            type = "array",
            name = "objects",
            description = "array of objects to merge",
            dynamic = @OpaDynamic(keyType = "any", valueType = "any"))
      },
      result =
          @OpaType(
              type = "set",
              name = "output",
              description = "set of keys in `object`",
              dynamic = @OpaDynamic(type = "any")))
  public RegoObject unionN(EvaluationContext ctx, RegoValue[] args) {
    RegoArray objectsArg = getArg(args, 0, RegoArray.class);

    RegoObject output = new RegoObject();
    for (RegoValue v : objectsArg.getValues()) {
      if (v instanceof RegoObject) {
        output = output.merge((RegoObject) v);
      } else {
        throw new TypeError("operand 0 must be array of objects but got array containing " + v.getTypeName());
      }
    }
    return output;
  }

  @OpaBuiltin(
      name = "object.keys",
      description = "Returns a set of keys in the supplied object.",
      args = {
        @OpaType(
            type = "object",
            name = "object",
            description = "object to get keys from",
            dynamic = @OpaDynamic(keyType = "any", valueType = "any"))
      },
      result =
          @OpaType(
              type = "set",
              description = "set of keys in `object`",
              dynamic = @OpaDynamic(type = "any")))
  public RegoObject union(EvaluationContext ctx, RegoValue[] args) {
    if (!(args[0] instanceof RegoObject)) {
      throw new TypeError("object.union: operand 1 must be object but got " + args[0].getTypeName());
    }
    if (!(args[1] instanceof RegoObject)) {
      throw new TypeError("object.union: operand 2 must be object but got " + args[1].getTypeName());
    }
    RegoObject a = (RegoObject) args[0];
    RegoObject b = (RegoObject) args[1];

    return a.merge(b);
  }

  @OpaBuiltin(
      name = "object.subset",
      description =
          "Determines if an object `sub` is a subset of another object `super`."
              + "Object `sub` is a subset of object `super` if and only if every key in `sub` "
              + "is also in `super`, **and** for all keys which `sub` and `super` share, they "
              + "have the same value. This function works with objects, sets, arrays and a set "
              + "of array and set.If both arguments are objects, then the operation is "
              + "recursive, e.g. `{\"c\": {\"x\": {10, 15, 20}}` is a subset of "
              + "`{\"a\": \"b\", \"c\": {\"x\": {10, 15, 20, 25}, \"y\": \"z\"}}`. "
              + "If both arguments are sets, then this function checks if every element of "
              + "`sub` is a member of `super`, but does not attempt to recurse. If both "
              + "arguments are arrays, then this function checks if `sub` appears contiguously "
              + "in order within `super`, and also does not attempt to recurse. If `super` is "
              + "array and `sub` is set, then this function checks if `super` contains every "
              + "element of `sub` with no consideration of ordering, and also does not attempt "
              + "to recurse.",
      args = {
        @OpaType(
            type = "object|set|array",
            name = "super",
            description = "object to check `sub` against",
            dynamic = @OpaDynamic(keyType = "any", valueType = "any")),
        @OpaType(
            type = "object|set|array",
            name = "sub",
            description = "object to check `super` against",
            dynamic = @OpaDynamic(keyType = "any", valueType = "any"))
      },
      result =
          @OpaType(
              type = "boolean",
              description = "`true` if `sub` is a subset of `super`, otherwise `false`",
              dynamic = @OpaDynamic(type = "boolean")))
  public RegoBoolean subset(EvaluationContext ctx, RegoValue[] args) {
    assertArgType(args, 0, RegoObject.class, RegoSet.class, RegoArray.class);
    assertArgType(args, 1, RegoObject.class, RegoSet.class, RegoArray.class);

    RegoValue superVal = args[0];
    RegoValue sub = args[1];

    if (sub instanceof RegoObject && superVal instanceof RegoObject) {
      return RegoBoolean.of(isObjectSubset((RegoObject) sub, (RegoObject) superVal));
    }

    if (sub instanceof RegoSet && superVal instanceof RegoSet) {
      RegoSet subSet = (RegoSet) sub;
      RegoSet superSet = (RegoSet) superVal;
      return RegoBoolean.of(subSet.valueStream().allMatch(superSet::contains));
    }

    if (sub instanceof RegoArray && superVal instanceof RegoArray) {
      return RegoBoolean.of(isArraySubset((RegoArray) sub, (RegoArray) superVal));
    }

    if (sub instanceof RegoSet && superVal instanceof RegoArray) {
      RegoSet subSet = (RegoSet) sub;
      Set<RegoValue> arrayValues = ((RegoArray) superVal).valueStream().collect(Collectors.toSet());
      return RegoBoolean.of(subSet.valueStream().allMatch(arrayValues::contains));
    }

    return RegoBoolean.FALSE;
  }

  private boolean isObjectSubset(RegoObject sub, RegoObject superObj) {
    // Every key in sub must exist in superObj with the same value
    for (Map.Entry<RegoValue, RegoValue> entry : sub.getProperties().entrySet()) {
      RegoValue key = entry.getKey();
      RegoValue subValue = entry.getValue();

      if (!superObj.hasProperty(key)) {
        return false;
      }

      RegoValue superValue = superObj.getProperty(key);

      // Recursive check based on value types
      if (subValue instanceof RegoObject && superValue instanceof RegoObject) {
        if (!isObjectSubset((RegoObject) subValue, (RegoObject) superValue)) {
          return false;
        }
      } else if (subValue instanceof RegoSet && superValue instanceof RegoSet) {
        RegoSet subSet = (RegoSet) subValue;
        RegoSet superSet = (RegoSet) superValue;
        if (!superSet.getValue().containsAll(subSet.getValue())) {
          return false;
        }
      } else if (subValue instanceof RegoArray && superValue instanceof RegoArray) {
        if (!isArraySubset((RegoArray) subValue, (RegoArray) superValue)) {
          return false;
        }
      } else if (!subValue.equals(superValue)) {
        return false;
      }
    }
    return true;
  }

  private boolean isArraySubset(RegoArray sub, RegoArray superArr) {
    if (sub.length() == 0) {
      return true;
    }
    if (sub.length() > superArr.length()) {
      return false;
    }

    // Check if sub appears contiguously in superArr
    for (int i = 0; i <= superArr.length() - sub.length(); i++) {
      boolean match = true;
      for (int j = 0; j < sub.length(); j++) {
        if (!sub.getValues().get(j).equals(superArr.getValues().get(i + j))) {
          match = false;
          break;
        }
      }
      if (match) {
        return true;
      }
    }
    return false;
  }

  @OpaBuiltin(
      name = "object.remove",
      description =
          "Returns a new object containing all key/value pairs except for those for which the "
              + "key is present in the supplied set. If the supplied `keys` is an `array`, then "
              + "`object.remove` will search through a nested object or array using each key in "
              + "turn. For example: `object.remove({\"a\": [{ \"b\": true }]}, "
              + "[\"a\", 0, \"b\"])` results in `{\"a\": []}`.",
      args = {
        @OpaType(
            type = "object",
            name = "object",
            description = "object to remove keys from",
            dynamic = @OpaDynamic(keyType = "any", valueType = "any")),
        @OpaType(
            type = "set|array",
            name = "keys",
            description = "set of keys to remove from `object`",
            dynamic = @OpaDynamic(type = "any"))
      },
      result =
          @OpaType(
              type = "object",
              name = "output",
              description =
                  "new object containing all key/value pairs except for those for which the key is present in `keys`",
              dynamic = @OpaDynamic(keyType = "any", valueType = "any")))
  public RegoObject remove(EvaluationContext ctx, RegoValue[] args) {
    if (!(args[0] instanceof RegoObject)) {
      throw new TypeError("object.remove: operand 1 must be object but got " + args[0].getTypeName());
    }
    RegoObject object = (RegoObject) args[0];

    if (!(args[1] instanceof RegoObject) && !(args[1] instanceof RegoSet) && !(args[1] instanceof RegoArray)) {
      throw new TypeError(
          "object.remove: operand 2 must be one of {object, set, array} but got "
              + args[1].getTypeName());
    }
    RegoValue keys = args[1];

    Set<RegoValue> filterSet;
    if (keys instanceof RegoCollection) {
      filterSet = ((RegoCollection) keys).valueStream().collect(Collectors.toSet());
    } else if (keys instanceof RegoObject) {
      filterSet = ((RegoObject) keys).getProperties().keySet();
    } else {
        throw new TypeError(
                "operand 2 must be one of {object, set, array} but got"
                        + keys.getTypeName());
    }

    Map<RegoValue, RegoValue> filteredMap =
        object.stream()
            .filter(e -> !filterSet.contains(e.getKey()))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

    return new RegoObject(filteredMap);
  }

  @OpaBuiltin(
      name = "object.filter",
      description =
          "Returns a new object containing only the key/value pairs for which the key is present "
              + "in the supplied set. If the supplied `keys` is an `array`, then "
              + "`object.filter` will search through a nested object or array using each key in "
              + "turn. For example: `object.filter({\"a\": [{ \"b\": true }]}, "
              + "[\"a\", 0, \"b\"])` results in `{\"a\": [{ \"b\": true }]}`.",
      args = {
        @OpaType(
            type = "object",
            name = "object",
            description = "object to filter keys from",
            dynamic = @OpaDynamic(keyType = "any", valueType = "any")),
        @OpaType(
            type = "set|array",
            name = "keys",
            description = "set of keys to filter from `object`",
            dynamic = @OpaDynamic(type = "any"))
      },
      result =
          @OpaType(
              type = "object",
              name = "filtered",
              description =
                  "new object containing only the key/value pairs for which the key is present in `keys`",
              dynamic = @OpaDynamic(keyType = "any", valueType = "any")))
  public RegoObject filter(EvaluationContext ctx, RegoValue[] args) {
    if (!(args[0] instanceof RegoObject)) {
      throw new TypeError("object.filter: operand 1 must be object but got " + args[0].getTypeName());
    }
    RegoObject object = (RegoObject) args[0];

    if (!(args[1] instanceof RegoObject) && !(args[1] instanceof RegoSet) && !(args[1] instanceof RegoArray)) {
      throw new TypeError("object.filter: operand 2 must be one of {object, set, array} but got " + args[1].getTypeName());
    }
    RegoValue keys = args[1];

    Set<RegoValue> filterSet;
    if (keys instanceof RegoCollection) {
      filterSet = ((RegoCollection) keys).valueStream().collect(Collectors.toSet());
    } else if (keys instanceof RegoObject) {
      filterSet = ((RegoObject) keys).getProperties().keySet();
    } else {
        throw new TypeError(
                "operand 2 must be one of {object, set, array} but got"
                        + keys.getTypeName());
    }

    Map<RegoValue, RegoValue> filteredMap =
        object.stream()
            .filter(e -> filterSet.contains(e.getKey()))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

    return new RegoObject(filteredMap);
  }

  @OpaBuiltin(
      name = "object.get",
      description =
          "Returns value of an object's key if present, otherwise a default. If the supplied "
              + "`key` is an `array`, then `object.get` will search through a nested object or "
              + "array using each key in turn. For example: "
              + "`object.get({\"a\": [{ \"b\": true }]}, [\"a\", 0, \"b\"], false)` results "
              + "in `true`.",
      args = {
        @OpaType(
            type = "object",
            name = "object",
            description = "object to get `key` from",
            dynamic = @OpaDynamic(keyType = "any", valueType = "any")),
        @OpaType(type = "any", name = "key", description = "key to lookup in `object`"),
        @OpaType(type = "any", name = "default", description = "default to use when lookup fails")
      },
      result =
          @OpaType(
              type = "any",
              name = "value",
              description = "`object[key]` if present, otherwise `default`"))
  public RegoValue get(EvaluationContext ctx, RegoValue[] args) {
    if (!(args[0] instanceof RegoObject)) {
      throw new TypeError("object.get: operand 1 must be object but got " + args[0].getTypeName());
    }
    RegoObject object = (RegoObject) args[0];
    RegoValue key = args[1];
    RegoValue defaultValue = args[2];

    if (key instanceof RegoArray) {
      RegoArray path = (RegoArray) key;
      if (path.length() == 0) {
        return object;
      }
      // Traverse the path
      RegoValue current = object;
      for (RegoValue segment : path.getValues()) {
        if (current instanceof RegoObject) {
          RegoObject obj = (RegoObject) current;
          if (!obj.hasProperty(segment)) {
            return defaultValue;
          }
          current = obj.getProperty(segment);
        } else if (current instanceof RegoArray) {
          RegoArray arr = (RegoArray) current;
          if (!(segment instanceof RegoNumber)) {
            return defaultValue;
          }
          int idx = ((RegoNumber) segment).getBigIntValue().intValue();
          if (idx < 0 || idx >= arr.length()) {
            return defaultValue;
          }
          current = arr.getValues().get(idx);
        } else {
          return defaultValue;
        }
      }
      return current;
    }

    if (object.hasProperty(key)) {
      return object.getProperty(key);
    }
    return defaultValue;
  }

  @OpaBuiltin(
      name = "object.keys",
      description =
          "Returns a set of an object's keys. For example: `object.keys({\"a\": 1, \"b\": true, \"c\": \"d\")` results in `{\"a\", \"b\", \"c\"}`.",
      args = {
        @OpaType(
            type = "object",
            name = "object",
            description = "object to get keys from",
            dynamic = @OpaDynamic(keyType = "any", valueType = "any"))
      },
      result =
          @OpaType(
              type = "set",
              description = "set of `object`'s keys",
              dynamic = @OpaDynamic(type = "any")))
  public RegoSet keys(EvaluationContext ctx, RegoValue[] args) {
    if (!(args[0] instanceof RegoObject)) {
      throw new TypeError("object.keys: operand 1 must be object but got " + args[0].getTypeName());
    }
    RegoObject object = (RegoObject) args[0];
    return new RegoSet(ctx.sortSets, object.getProperties().keySet());
  }
}
