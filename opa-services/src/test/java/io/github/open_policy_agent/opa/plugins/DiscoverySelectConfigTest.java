package io.github.open_policy_agent.opa.plugins;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Env substitution in discovered config; path selection is covered by {@code DiscoveryParityTest}. */
class DiscoverySelectConfigTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private static JsonNode select(String data, Map<String, String> env) {
    return DiscoveryPlugin.selectConfig(MAPPER.readTree(data.replace('\'', '"')), List.of(), env);
  }

  @Test
  void substitutedValuesCannotChangeTheDocumentStructure() {
    final String quoted = "a\"b\\c\nd";
    final String injection = "x\",\"admin\":\"y";
    final String unicode = "h\u00e9llo \u2603 \uD83D\uDE00";

    final JsonNode config =
        select(
            "{'labels':{'q':'${Q}','i':'${I}','u':'${U}'}}",
            Map.of("Q", quoted, "I", injection, "U", unicode));

    assertEquals(3, config.path("labels").size());
    assertEquals(quoted, config.path("labels").path("q").asString());
    assertEquals(injection, config.path("labels").path("i").asString());
    assertEquals(unicode, config.path("labels").path("u").asString());
  }

  @Test
  void keysAndArrayElementsAreSubstituted() {
    final JsonNode config =
        select("{'${K}':'v','a${b':'w','l':['${V}',1]}", Map.of("K", "key", "V", "x"));

    assertEquals("v", config.path("key").asString());
    assertEquals("w", config.path("a${b").asString());
    assertEquals("x", config.path("l").path(0).asString());
    assertEquals(1, config.path("l").path(1).asInt());
  }

  @Test
  void undefinedVariablesAreLeftAsWrittenAndEmptyOnesAreSubstitutedAsEmpty() {
    final JsonNode config = select("{'a':'${NOPE}','b':'x${E}y'}", Map.of("E", ""));

    assertEquals("${NOPE}", config.path("a").asString());
    assertEquals("xy", config.path("b").asString());
  }
}
