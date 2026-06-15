package io.github.open_policy_agent.opa.rego;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.open_policy_agent.opa.jackson.RegoValueModule;
import java.util.ArrayList;
import java.util.List;

/**
 * Test helper: bridges between {@link JsonNode} and the Object-based Engine API.
 *
 * <p>Engine no longer accepts/returns JsonNode directly (the evaluator module is Jackson-free).
 * Tests that prefer to express inputs as JsonNode use this bridge to convert at the boundary.
 */
public final class JsonNodeBridge {
  private static final ObjectMapper MAPPER =
      new ObjectMapper().registerModule(new RegoValueModule());

  private JsonNodeBridge() {}

  public static List<JsonNode> eval(Engine engine, EvaluationContext ctx, JsonNode input) {
    Object pojoInput = MAPPER.convertValue(input, Object.class);
    return wrap(engine.evaluate(ctx, pojoInput));
  }

  public static List<JsonNode> eval(Engine.PreparedQuery pq, JsonNode input) {
    Object pojoInput = MAPPER.convertValue(input, Object.class);
    return wrap(pq.eval(pojoInput));
  }

  private static List<JsonNode> wrap(List<Object> raw) {
    List<JsonNode> out = new ArrayList<>(raw.size());
    for (Object o : raw) {
      out.add(MAPPER.valueToTree(o));
    }
    return out;
  }
}
