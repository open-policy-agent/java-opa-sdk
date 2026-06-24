package io.github.open_policy_agent.opa.jackson;

import io.github.open_policy_agent.opa.ast.types.RegoArray;
import io.github.open_policy_agent.opa.ast.types.RegoBigInt;
import io.github.open_policy_agent.opa.ast.types.RegoBoolean;
import io.github.open_policy_agent.opa.ast.types.RegoDecimal;
import io.github.open_policy_agent.opa.ast.types.RegoInt32;
import io.github.open_policy_agent.opa.ast.types.RegoNull;
import io.github.open_policy_agent.opa.ast.types.RegoNumber;
import io.github.open_policy_agent.opa.ast.types.RegoObject;
import io.github.open_policy_agent.opa.ast.types.RegoSet;
import io.github.open_policy_agent.opa.ast.types.RegoString;
import io.github.open_policy_agent.opa.ast.types.RegoValue;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DatabindException;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.module.SimpleModule;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Map;
import java.util.TreeMap;

/**
 * Jackson {@link SimpleModule} that teaches an {@link tools.jackson.databind.ObjectMapper}
 * how to (de)serialize the OPA Rego AST value types. Replaces the in-source {@code @JsonValue}/
 * {@code @JsonAnySetter} annotations the AST types previously carried, so the evaluator module
 * has no Jackson dependency.
 *
 * <p>Only a {@link RegoObject} deserializer is registered. Reading JSON directly into a typed
 * Rego value (i.e. {@code mapper.readValue(json, T.class)}) is only used with {@link RegoObject}
 * as the target type; the deserializer recursively builds nested {@link RegoValue}s of the right
 * subtype from the JSON tree, so no separate per-type deserializer is required.
 *
 * <p>Usage:
 *
 * <pre>{@code
 * JsonMapper mapper = JsonMapper.builder().addModule(new RegoValueModule()).build();
 * String json = mapper.writeValueAsString(regoValue);
 * RegoObject obj = mapper.readValue(json, RegoObject.class);
 * }</pre>
 */
public class RegoValueModule extends SimpleModule {

  public RegoValueModule() {
    super("rego-value");
    addSerializer(RegoString.class, new RegoStringSerializer());
    addSerializer(RegoInt32.class, new RegoInt32Serializer());
    addSerializer(RegoBigInt.class, new RegoBigIntSerializer());
    addSerializer(RegoDecimal.class, new RegoDecimalSerializer());
    addSerializer(RegoBoolean.class, new RegoBooleanSerializer());
    addSerializer(RegoNull.class, new RegoNullSerializer());
    addSerializer(RegoArray.class, new RegoArraySerializer());
    addSerializer(RegoSet.class, new RegoSetSerializer());
    addSerializer(RegoObject.class, new RegoObjectSerializer());
    addDeserializer(RegoObject.class, new RegoObjectDeserializer());
  }

  private static final class RegoStringSerializer extends ValueSerializer<RegoString> {
    @Override
    public void serialize(RegoString v, JsonGenerator g, SerializationContext p) {
      g.writeString(v.getValue());
    }
  }

  private static final class RegoInt32Serializer extends ValueSerializer<RegoInt32> {
    @Override
    public void serialize(RegoInt32 v, JsonGenerator g, SerializationContext p) {
      g.writeNumber(v.getValue());
    }
  }

  private static final class RegoBigIntSerializer extends ValueSerializer<RegoBigInt> {
    @Override
    public void serialize(RegoBigInt v, JsonGenerator g, SerializationContext p) {
      g.writeNumber(v.getValue());
    }
  }

  private static final class RegoDecimalSerializer extends ValueSerializer<RegoDecimal> {
    @Override
    public void serialize(RegoDecimal v, JsonGenerator g, SerializationContext p) {
      g.writeNumber(v.getValue());
    }
  }

  private static final class RegoBooleanSerializer extends ValueSerializer<RegoBoolean> {
    @Override
    public void serialize(RegoBoolean v, JsonGenerator g, SerializationContext p) {
      g.writeBoolean(v.getValue());
    }
  }

  private static final class RegoNullSerializer extends ValueSerializer<RegoNull> {
    @Override
    public void serialize(RegoNull v, JsonGenerator g, SerializationContext p) {
      g.writeNull();
    }
  }

  private static final class RegoArraySerializer extends ValueSerializer<RegoArray> {
    @Override
    public void serialize(RegoArray v, JsonGenerator g, SerializationContext p) {
      g.writeStartArray();
      for (RegoValue item : v.getValues()) {
        p.writeValue(g, item);
      }
      g.writeEndArray();
    }
  }

  private static final class RegoSetSerializer extends ValueSerializer<RegoSet> {
    @Override
    public void serialize(RegoSet v, JsonGenerator g, SerializationContext p) {
      g.writeStartArray();
      for (RegoValue item : v.getValue()) {
        p.writeValue(g, item);
      }
      g.writeEndArray();
    }
  }

  private static final class RegoObjectSerializer extends ValueSerializer<RegoObject> {
    @Override
    public void serialize(RegoObject v, JsonGenerator g, SerializationContext p) {
      // OPA (Go) emits sorted keys; preserve that for round-trip fidelity.
      Map<String, RegoValue> sorted = new TreeMap<>();
      for (Map.Entry<RegoValue, RegoValue> entry : v.getProperties().entrySet()) {
        sorted.put(keyToString(entry.getKey()), entry.getValue());
      }
      g.writeStartObject();
      for (Map.Entry<String, RegoValue> entry : sorted.entrySet()) {
        g.writeName(entry.getKey());
        p.writeValue(g, entry.getValue());
      }
      g.writeEndObject();
    }

    private static String keyToString(RegoValue key) {
      if (key instanceof RegoString) {
        return ((RegoString) key).getValue();
      }
      if (key instanceof RegoNumber) {
        return ((RegoNumber) key).getBigIntValue().toString();
      }
      return key.toString();
    }
  }

  private static final class RegoObjectDeserializer extends ValueDeserializer<RegoObject> {
    @Override
    public RegoObject deserialize(JsonParser jp, DeserializationContext ctx) {
      JsonNode root = ctx.readTree(jp);
      if (!root.isObject()) {
        throw DatabindException.from(jp, "expected JSON object for RegoObject, got " + root.getNodeType());
      }
      RegoObject obj = new RegoObject();
      for (Map.Entry<String, JsonNode> field : root.properties()) {
        obj.setProp(new RegoString(field.getKey()), convertNode(jp, field.getValue()));
      }
      return obj;
    }

    private static RegoValue convertNode(JsonParser jp, JsonNode node) {
      if (node == null || node.isNull()) {
        return RegoNull.INSTANCE;
      }
      if (node.isTextual()) {
        return new RegoString(node.asText());
      }
      if (node.isBoolean()) {
        return RegoBoolean.of(node.asBoolean());
      }
      if (node.isIntegralNumber()) {
        return new RegoBigInt(node.bigIntegerValue());
      }
      if (node.isFloatingPointNumber()) {
        return new RegoDecimal(node.doubleValue());
      }
      if (node.isNumber()) {
        BigDecimal bd = node.decimalValue();
        try {
          BigInteger bi = bd.toBigIntegerExact();
          return new RegoBigInt(bi);
        } catch (ArithmeticException ignored) {
          return new RegoDecimal(bd.doubleValue());
        }
      }
      if (node.isArray()) {
        RegoArray arr = new RegoArray();
        for (JsonNode element : node) {
          arr.addValue(convertNode(jp, element));
        }
        return arr;
      }
      if (node.isObject()) {
        RegoObject obj = new RegoObject();
        for (Map.Entry<String, JsonNode> field : node.properties()) {
          obj.setProp(new RegoString(field.getKey()), convertNode(jp, field.getValue()));
        }
        return obj;
      }
      throw DatabindException.from(jp, "unsupported JSON node type: " + node.getNodeType());
    }
  }
}
