package io.github.open_policy_agent.opa.plugins;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class DiscoverySelectConfigTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private static JsonNode select(String data, List<String> path, Map<String, String> env)
      throws Exception {
    return DiscoveryPlugin.selectConfig(MAPPER.readTree(data), path, env);
  }

  @Test
  void substitutedValuesAreNotParsedAsJson() throws Exception {
    final String value = "a\"b\\c\nd";

    final JsonNode config =
        select("{\"labels\":{\"k\":\"${V}\"}}", List.of(), Map.of("V", value));

    assertEquals(value, config.path("labels").path("k").asString());
  }

  @Test
  void substitutedValuesCannotInjectKeys() throws Exception {
    final JsonNode config =
        select("{\"labels\":{\"k\":\"${V}\"}}", List.of(), Map.of("V", "x\",\"admin\":\"y"));

    assertEquals(1, config.path("labels").size());
    assertEquals("x\",\"admin\":\"y", config.path("labels").path("k").asString());
  }

  @Test
  void keysAreSubstitutedToo() throws Exception {
    final JsonNode config = select("{\"labels\":{\"${K}\":\"v\"}}", List.of(), Map.of("K", "region"));

    assertEquals("v", config.path("labels").path("region").asString());
  }

  @Test
  void undefinedVariablesAndNonStringValuesAreLeftAlone() throws Exception {
    final JsonNode config = select("{\"a\":\"${NOPE}\",\"n\":1,\"b\":true}", List.of(), Map.of());

    assertEquals("${NOPE}", config.path("a").asString());
    assertEquals(1, config.path("n").asInt());
    assertEquals(true, config.path("b").asBoolean());
  }

  @Test
  void pathSelectsTheNestedNode() throws Exception {
    final JsonNode config =
        select("{\"x\":{\"y\":{\"k\":1}},\"k\":2}", List.of("x", "y"), Map.of());

    assertEquals(1, config.path("k").asInt());
  }

  @Test
  void undefinedPathIsAnError() {
    assertThrows(IllegalStateException.class, () -> select("{}", List.of("x"), Map.of()));
  }

  @Test
  void stringsInsideArraysAreSubstituted() throws Exception {
    final JsonNode config = select("{\"l\":[\"${V}\",1]}", List.of(), Map.of("V", "x"));

    assertEquals("x", config.path("l").path(0).asString());
    assertEquals(1, config.path("l").path(1).asInt());
  }
}
