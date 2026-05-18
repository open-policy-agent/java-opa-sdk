package io.github.open_policy_agent.opa.jackson;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.ResolvableDeserializer;
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

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

class TypeDeserializer extends JsonDeserializer<Type> {
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

  // Cache of bean deserializers, built directly via the factory so they bypass
  // this TypeDeserializer (which is registered for the Type interface and would
  // otherwise be re-entered for any Type subclass).
  private final Map<Class<? extends Type>, JsonDeserializer<Object>> beanDeserializerCache =
      new ConcurrentHashMap<>();

  @Override
  public Type deserialize(JsonParser jp, DeserializationContext ctx) throws IOException {
    ObjectMapper mapper = (ObjectMapper) jp.getCodec();
    JsonNode root = mapper.readTree(jp);
    JsonNode typeNode = root.get("type");
    if (typeNode == null) {
      return null;
    }

    String type = typeNode.asText();
    Class<? extends Type> typeClass = TYPE_REGISTRY.get(type);
    if (typeClass == null) {
      throw new IOException("unknown type: " + type);
    }

    JsonDeserializer<Object> beanDeserializer = beanDeserializerFor(typeClass, ctx);
    JsonParser nodeParser = root.traverse(mapper);
    nodeParser.nextToken();
    return (Type) beanDeserializer.deserialize(nodeParser, ctx);
  }

  private JsonDeserializer<Object> beanDeserializerFor(
      Class<? extends Type> typeClass, DeserializationContext ctx) throws IOException {
    JsonDeserializer<Object> cached = beanDeserializerCache.get(typeClass);
    if (cached != null) {
      return cached;
    }
    JavaType javaType = ctx.constructType(typeClass);
    BeanDescription beanDesc = ctx.getConfig().introspect(javaType);
    JsonDeserializer<Object> beanDeserializer =
        ctx.getFactory().createBeanDeserializer(ctx, javaType, beanDesc);
    if (beanDeserializer instanceof ResolvableDeserializer) {
      ((ResolvableDeserializer) beanDeserializer).resolve(ctx);
    }
    beanDeserializerCache.put(typeClass, beanDeserializer);
    return beanDeserializer;
  }
}
