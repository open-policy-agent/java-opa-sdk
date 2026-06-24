package io.github.open_policy_agent.opa.jackson;

import io.github.open_policy_agent.opa.ir.policy.types.AnyType;
import io.github.open_policy_agent.opa.ir.policy.types.ArrayType;
import io.github.open_policy_agent.opa.ir.policy.types.BooleanType;
import io.github.open_policy_agent.opa.ir.policy.types.FunctionType;
import io.github.open_policy_agent.opa.ir.policy.types.NullType;
import io.github.open_policy_agent.opa.ir.policy.types.NumberType;
import io.github.open_policy_agent.opa.ir.policy.types.ObjectType;
import io.github.open_policy_agent.opa.ir.policy.types.SetType;
import io.github.open_policy_agent.opa.ir.policy.types.StringType;
import io.github.open_policy_agent.opa.ir.policy.types.Type;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DatabindException;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ValueDeserializer;

import java.util.HashMap;
import java.util.Map;

class TypeDeserializer extends ValueDeserializer<Type> {
  // Type marker strings match the OPA IR spec typeMarker constants.
  private static final Map<String, Class<? extends Type>> TYPE_REGISTRY =
      new HashMap<>() {
        {
          put(NullType.TypeMarker,     NullType.class);
          put(BooleanType.TypeMarker,  BooleanType.class);
          put(NumberType.TypeMarker,   NumberType.class);
          put(StringType.TypeMarker,   StringType.class);
          put(ArrayType.TypeMarker,    ArrayType.class);
          put(ObjectType.TypeMarker,   ObjectType.class);
          put(SetType.TypeMarker,      SetType.class);
          put(AnyType.TypeMarker,      AnyType.class);
          put(FunctionType.TypeMarker, FunctionType.class);
        }
      };

  @Override
  public Type deserialize(JsonParser jp, DeserializationContext ctx) {
    JsonNode root = ctx.readTree(jp);
    JsonNode typeNode = root.get("type");
    if (typeNode == null) {
      return null;
    }

    String type = typeNode.asText();
    Class<? extends Type> typeClass = TYPE_REGISTRY.get(type);
    if (typeClass == null) {
      throw DatabindException.from(jp, "unknown type: " + type);
    }

    return ctx.readTreeAsValue(root, typeClass);
  }
}
