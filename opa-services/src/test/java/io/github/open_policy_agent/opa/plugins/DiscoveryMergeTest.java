package io.github.open_policy_agent.opa.plugins;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class DiscoveryMergeTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private static DiscoveryMerge.Result merge(String boot, String discovered) throws Exception {
    return DiscoveryMerge.merge(MAPPER.readTree(boot), MAPPER.readTree(discovered), "svc");
  }

  private static JsonNode json(String text) throws Exception {
    return MAPPER.readTree(text);
  }

  @Test
  void arraysAreReplacedNotMerged() throws Exception {
    final DiscoveryMerge.Result result = merge("{\"list\":[1,2]}", "{\"list\":[3,4,5]}");

    assertEquals(json("{\"list\":[1,2]}"), result.tree());
    assertEquals(List.of("list"), result.overriddenKeys());
  }

  @Test
  void mapsMergeRecursivelyAndBootWins() throws Exception {
    final DiscoveryMerge.Result result =
        merge("{\"a\":{\"b\":1,\"c\":2}}", "{\"a\":{\"b\":9,\"d\":4},\"e\":5}");

    assertEquals(json("{\"a\":{\"b\":1,\"c\":2,\"d\":4},\"e\":5}"), result.tree());
    assertEquals(List.of("a.b"), result.overriddenKeys());
  }

  @Test
  void equalValuesAreNotReportedAsOverridden() throws Exception {
    assertEquals(List.of(), merge("{\"a\":1}", "{\"a\":1}").overriddenKeys());
  }

  @Test
  void aBootMapReplacesADiscoveredScalar() throws Exception {
    final DiscoveryMerge.Result result = merge("{\"a\":{\"b\":1}}", "{\"a\":\"x\"}");

    assertEquals(json("{\"a\":{\"b\":1}}"), result.tree());
    assertEquals(List.of("a"), result.overriddenKeys());
  }

  @Test
  void aBootScalarReplacesADiscoveredMap() throws Exception {
    final DiscoveryMerge.Result result = merge("{\"a\":1}", "{\"a\":{\"b\":2}}");

    assertEquals(json("{\"a\":1}"), result.tree());
    assertEquals(List.of("a"), result.overriddenKeys());
  }

  @Test
  void discoveryBlockComesFromBootAndIsNeverReported() throws Exception {
    final DiscoveryMerge.Result result =
        merge("{\"discovery\":{\"resource\":\"boot\"}}", "{\"discovery\":{\"resource\":\"other\"}}");

    assertEquals(json("{\"discovery\":{\"resource\":\"boot\"}}"), result.tree());
    assertEquals(List.of(), result.overriddenKeys());
  }

  @Test
  void discoveredDiscoveryBlockIsDroppedWhenBootHasNone() throws Exception {
    assertEquals(json("{}"), merge("{}", "{\"discovery\":{\"resource\":\"other\"}}").tree());
  }

  @Test
  void servicesInArrayFormMergeWithMapForm() throws Exception {
    final DiscoveryMerge.Result result =
        merge(
            "{\"services\":[{\"name\":\"svc\",\"url\":\"https://boot.example.test\"}]}",
            "{\"services\":{\"extra\":{\"url\":\"https://extra.example.test\"}}}");

    assertEquals(
        json(
            "{\"services\":{\"extra\":{\"url\":\"https://extra.example.test\"},"
                + "\"svc\":{\"url\":\"https://boot.example.test\"}}}"),
        result.tree());
  }

  @Test
  void restatingTheDiscoveryServiceIdenticallyIsAllowed() throws Exception {
    final DiscoveryMerge.Result result =
        merge(
            "{\"services\":{\"svc\":{\"name\":\"svc\",\"url\":\"u\",\"x\":null}}}",
            "{\"services\":[{\"name\":\"svc\",\"url\":\"u\"}]}");

    assertEquals(List.of(), result.overriddenKeys());
  }

  @Test
  void changingTheDiscoveryServiceIsRejected() {
    final IllegalStateException e =
        assertThrows(
            IllegalStateException.class,
            () -> merge("{\"services\":{\"svc\":{\"url\":\"a\"}}}", "{\"services\":{\"svc\":{\"url\":\"b\"}}}"));

    assertEquals("updates to the discovery service are not allowed", e.getMessage());
  }

  @Test
  void nullsAreTreatedAsAbsent() throws Exception {
    final DiscoveryMerge.Result result = merge("{\"a\":null}", "{\"a\":1,\"b\":null}");

    assertEquals(json("{\"a\":1}"), result.tree());
    assertEquals(List.of(), result.overriddenKeys());
  }

  @Test
  void nonObjectTreesAreRejected() {
    assertThrows(
        IllegalStateException.class,
        () -> DiscoveryMerge.merge(MAPPER.createObjectNode(), MAPPER.createArrayNode(), "svc"));
  }

  @Test
  void numbersAreComparedByValueNotByNodeType() {
    final DiscoveryMerge.Result result =
        DiscoveryMerge.merge(
            MAPPER.createObjectNode().put("a", 1L).put("b", 2.0),
            MAPPER.createObjectNode().put("a", 1).put("b", 2),
            "svc");

    assertEquals(List.of(), result.overriddenKeys());
  }

  @Test
  void aServiceRestatedWithDifferentNumericNodeTypesIsNotAChange() {
    final tools.jackson.databind.node.ObjectNode boot = MAPPER.createObjectNode();
    boot.putObject("services").putObject("svc").put("timeout", 30L);
    final tools.jackson.databind.node.ObjectNode discovered = MAPPER.createObjectNode();
    discovered.putObject("services").putObject("svc").put("timeout", 30);

    assertEquals(List.of(), DiscoveryMerge.merge(boot, discovered, "svc").overriddenKeys());
  }

  @Test
  void nullsInsideArrayElementsAreStripped() throws Exception {
    final DiscoveryMerge.Result result =
        merge("{\"l\":[{\"b\":1}]}", "{\"l\":[{\"a\":null,\"b\":1}]}");

    assertEquals(List.of(), result.overriddenKeys());
  }

  @Test
  void arrayFormServicesWithoutANameAreRejected() {
    assertThrows(
        IllegalStateException.class,
        () -> merge("{\"services\":[{\"url\":\"u\"}]}", "{}"));
  }

  @Test
  void restatingTheDiscoveryServiceWithAnExplicitDefaultIsAllowed() throws Exception {
    final DiscoveryMerge.Result result =
        merge(
            "{\"services\":{\"svc\":{\"url\":\"u\"}}}",
            "{\"services\":{\"svc\":{\"url\":\"u\",\"response_header_timeout_seconds\":10}}}");

    assertEquals(List.of(), result.overriddenKeys());
  }

  @Test
  void restatingTheDiscoveryServiceWithANonDefaultValueIsRejected() {
    assertThrows(
        IllegalStateException.class,
        () ->
            merge(
                "{\"services\":{\"svc\":{\"url\":\"u\"}}}",
                "{\"services\":{\"svc\":{\"url\":\"u\",\"response_header_timeout_seconds\":20}}}"));
  }
}
