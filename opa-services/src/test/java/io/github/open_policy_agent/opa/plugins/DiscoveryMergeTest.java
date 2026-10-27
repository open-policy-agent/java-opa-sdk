package io.github.open_policy_agent.opa.plugins;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/** JSON in these tests uses single quotes for readability. */
class DiscoveryMergeTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private static JsonNode json(String text) {
    return MAPPER.readTree(text.replace('\'', '"'));
  }

  private static DiscoveryMerge.Result merge(String boot, String discovered) {
    return DiscoveryMerge.merge(json(boot), json(discovered), "svc");
  }

  static Stream<Arguments> merges() {
    return Stream.of(
        Arguments.of("{'list':[1,2]}", "{'list':[3,4,5]}", "{'list':[1,2]}", "list"),
        Arguments.of(
            "{'a':{'b':1,'c':2}}", "{'a':{'b':9,'d':4},'e':5}", "{'a':{'b':1,'c':2,'d':4},'e':5}", "a.b"),
        Arguments.of("{'a':{'b':1}}", "{'a':'x'}", "{'a':{'b':1}}", "a"),
        Arguments.of("{'a':1}", "{'a':{'b':2}}", "{'a':1}", "a"),
        Arguments.of("{'a':1}", "{'a':1}", "{'a':1}", ""),
        Arguments.of("{'a':null}", "{'a':1,'b':null}", "{'a':1}", ""),
        Arguments.of(
            "{'discovery':{'resource':'boot'}}",
            "{'discovery':{'resource':'other'}}",
            "{'discovery':{'resource':'boot'}}",
            ""),
        Arguments.of("{}", "{'discovery':{'resource':'other'}}", "{}", ""),
        Arguments.of(
            "{'services':[{'name':'svc','url':'u'}]}",
            "{'services':{'extra':{'url':'e'}}}",
            "{'services':{'extra':{'url':'e'},'svc':{'url':'u'}}}",
            ""),
        Arguments.of(
            "{'services':{'svc':{'name':'svc','url':'u','x':null}}}",
            "{'services':[{'name':'svc','url':'u'}]}",
            "{'services':{'svc':{'url':'u'}}}",
            ""),
        Arguments.of(
            "{'services':{'svc':{'url':'u'}}}",
            "{'services':{'svc':{'url':'u','response_header_timeout_seconds':10}}}",
            "{'services':{'svc':{'url':'u','response_header_timeout_seconds':10}}}",
            ""),
        Arguments.of("{'l':[{'b':1}]}", "{'l':[{'a':null,'b':1}]}", "{'l':[{'b':1}]}", ""));
  }

  @ParameterizedTest
  @MethodSource("merges")
  void bootWinsAndOverriddenKeysAreReported(
      String boot, String discovered, String expected, String overriddenKey) {
    final DiscoveryMerge.Result result = merge(boot, discovered);

    assertEquals(json(expected), result.tree());
    assertEquals(overriddenKey.isEmpty() ? List.of() : List.of(overriddenKey), result.overriddenKeys());
  }

  static Stream<Arguments> rejections() {
    return Stream.of(
        Arguments.of("{'services':{'svc':{'url':'a'}}}", "{'services':{'svc':{'url':'b'}}}"),
        Arguments.of(
            "{'services':{'svc':{'url':'u'}}}",
            "{'services':{'svc':{'url':'u','response_header_timeout_seconds':20}}}"),
        Arguments.of("{'services':[{'url':'u'}]}", "{}"),
        Arguments.of("{}", "[]"));
  }

  @ParameterizedTest
  @MethodSource("rejections")
  void invalidConfigIsRejected(String boot, String discovered) {
    assertThrows(IllegalStateException.class, () -> merge(boot, discovered));
  }

  @Test
  void changingTheDiscoveryServiceSaysSo() {
    final IllegalStateException e =
        assertThrows(
            IllegalStateException.class,
            () -> merge("{'services':{'svc':{'url':'a'}}}", "{'services':{'svc':{'url':'b'}}}"));

    assertEquals("updates to the discovery service are not allowed", e.getMessage());
  }

  @Test
  void numbersAreComparedByValueNotByNodeType() {
    final ObjectNode boot = MAPPER.createObjectNode().put("a", 1L).put("b", 2.0);
    boot.putObject("services").putObject("svc").put("timeout", 30L);
    final ObjectNode discovered = MAPPER.createObjectNode().put("a", 1).put("b", 2);
    discovered.putObject("services").putObject("svc").put("timeout", 30);

    assertEquals(List.of(), DiscoveryMerge.merge(boot, discovered, "svc").overriddenKeys());
  }
}
