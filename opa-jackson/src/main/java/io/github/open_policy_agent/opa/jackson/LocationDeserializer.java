package io.github.open_policy_agent.opa.jackson;

import io.github.open_policy_agent.opa.ir.Location;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ValueDeserializer;

/**
 * Deserializes a standalone {@code Location} JSON object ({@code file/col/row/end_col/end_row}),
 * e.g. {@code UnplannedRule.location}. Until now, every JSON occurrence of these fields was
 * flattened onto a statement envelope and handled by {@link StmtDeserializer}; {@code
 * UnplannedRule} is the first place a {@code Location} appears as its own nested object, hence
 * this dedicated deserializer.
 */
class LocationDeserializer extends ValueDeserializer<Location> {
  @Override
  public Location deserialize(JsonParser jp, DeserializationContext ctx) {
    JsonNode node = ctx.readTree(jp);
    return new Location(
        node.path("file").asInt(),
        node.path("col").asInt(),
        node.path("row").asInt(),
        node.path("end_col").asInt(),
        node.path("end_row").asInt());
  }
}
