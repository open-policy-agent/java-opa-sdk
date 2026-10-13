package io.github.open_policy_agent.opa.jackson;

import io.github.open_policy_agent.opa.ir.Operand;
import io.github.open_policy_agent.opa.ir.vals.BoolVal;
import io.github.open_policy_agent.opa.ir.vals.LocalVal;
import io.github.open_policy_agent.opa.ir.vals.StringIndexVal;
import io.github.open_policy_agent.opa.ir.vals.Val;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DatabindException;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ValueDeserializer;

import java.util.HashMap;
import java.util.Map;

class OperandDeserializer extends ValueDeserializer<Operand> {
  private static final Map<String, Class<? extends Val>> VAL_REGISTRY =
      new HashMap<String, Class<? extends Val>>() {
        {
          put("bool", BoolVal.class);
          put("string_index", StringIndexVal.class);
          put("local", LocalVal.class);
        }
      };

  @Override
  public Operand deserialize(JsonParser jp, DeserializationContext ctx) {
    JsonNode node = ctx.readTree(jp);
    JsonNode opNode = node.get("type");
    if (opNode == null) {
      return null;
    }

    String opValType = opNode.asText();
    Class<? extends Val> opClass = VAL_REGISTRY.get(opValType);
    if (opClass == null) {
      throw DatabindException.from(jp, "unknown val type: " + opValType);
    }

    return new Operand(ctx.readTreeAsValue(node, opClass));
  }
}
