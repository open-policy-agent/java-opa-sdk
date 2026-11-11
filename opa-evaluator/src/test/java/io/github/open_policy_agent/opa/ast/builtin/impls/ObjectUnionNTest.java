package io.github.open_policy_agent.opa.ast.builtin.impls;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.open_policy_agent.opa.ast.types.RegoArray;
import io.github.open_policy_agent.opa.ast.types.RegoObject;
import io.github.open_policy_agent.opa.ast.types.RegoString;
import io.github.open_policy_agent.opa.ast.types.RegoValue;
import io.github.open_policy_agent.opa.jackson.RegoValueModule;
import io.github.open_policy_agent.opa.rego.TypeError;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import tools.jackson.databind.json.JsonMapper;

/** Expected values come from {@code opa eval} (OPA 1.21.1). */
class ObjectUnionNTest {

  private static final JsonMapper MAPPER =
      JsonMapper.builder().addModule(new RegoValueModule()).build();

  private final ObjectBuiltins builtins = new ObjectBuiltins();

  private static RegoObject object(String json) {
    return MAPPER.readValue(json, RegoObject.class);
  }

  private static RegoArray array(String json) {
    return (RegoArray) object("{\"v\": " + json + "}").getProperty("v");
  }

  private RegoObject unionN(RegoArray objects) {
    return builtins.unionN(null, new RegoValue[] {objects});
  }

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      value = {
        "[]|{}",
        "[{\"a\": 1}]|{\"a\": 1}",
        "[{\"a\": 1}, {\"b\": 2}, {\"c\": 3}]|{\"a\": 1, \"b\": 2, \"c\": 3}",
        "[{\"a\": 1}, {\"a\": 2}]|{\"a\": 2}",
        "[{\"a\": {\"x\": 1}}, {\"a\": {\"y\": 2}}]|{\"a\": {\"x\": 1, \"y\": 2}}",
        "[{\"a\": {\"x\": 1}}, {\"a\": 5}]|{\"a\": 5}",
        "[{\"a\": 5}, {\"a\": {\"x\": 1}}]|{\"a\": {\"x\": 1}}",
        "[{\"a\": {\"b\": {\"c\": 1}}}, {\"a\": {\"b\": {\"d\": 2}}}, {\"a\": {\"b\": {\"c\": 3}}}]|{\"a\": {\"b\": {\"c\": 3, \"d\": 2}}}",
        "[{\"a\": 1, \"b\": 2}, {\"c\": 3, \"a\": 9}]|{\"a\": 9, \"b\": 2, \"c\": 3}",
        "[{\"a\": {\"x\": 1}}, {\"a\": {\"x\": {\"y\": 2}}}, {\"a\": {\"x\": 7}}]|{\"a\": {\"x\": 7}}",
        "[{}, {\"a\": {}}, {\"a\": {\"b\": 1}}]|{\"a\": {\"b\": 1}}",
        "[{\"a\": {\"x\": 1}}, {\"a\": {\"y\": 2}}, {\"a\": 5}]|{\"a\": 5}",
        "[{\"a\": {\"x\": 1}}, {\"a\": {\"y\": 2}}, {\"a\": 5}, {\"a\": {\"z\": 3}}]|{\"a\": {\"z\": 3}}",
        "[{\"a\": {\"b\": {\"x\": 1}}}, {\"a\": {\"b\": {\"y\": 2}}}, {\"a\": {\"b\": 7}}, {\"a\": {\"b\": {\"z\": 3}}}]|{\"a\": {\"b\": {\"z\": 3}}}"
      })
  void unionNMatchesOpa(String input, String expected) {
    assertEquals(object(expected), unionN(array(input)));
  }

  @Test
  void unionOfTwoObjectsMatchesOpa() {
    final RegoValue merged =
        builtins.union(null, new RegoValue[] {object("{\"a\": {\"x\": 1}}"), object("{\"a\": {\"y\": 2}, \"b\": 3}")});
    final RegoValue replaced =
        builtins.union(null, new RegoValue[] {object("{\"a\": 1}"), object("{\"a\": {\"y\": 2}}")});

    assertEquals(object("{\"a\": {\"x\": 1, \"y\": 2}, \"b\": 3}"), merged);
    assertEquals(object("{\"a\": {\"y\": 2}}"), replaced);
  }

  @Test
  void keysKeepTheOrderOfTheirFirstOccurrence() {
    final RegoObject result = unionN(array("[{\"a\": 1, \"b\": 2}, {\"c\": 3, \"a\": 9}]"));

    final List<String> keys =
        result.getProperties().keySet().stream().map(k -> ((RegoString) k).getValue()).collect(Collectors.toList());

    assertEquals(List.of("a", "b", "c"), keys);
  }

  @Test
  void theInputsAreNeverMutated() {
    final RegoArray input =
        array("[{\"a\": {\"x\": 1}, \"k\": 1}, {\"a\": {\"y\": 2}}, {\"a\": {\"x\": 3}, \"k\": 2}]");
    final String before = input.toString();

    final RegoObject result = unionN(input);

    assertEquals(before, input.toString());
    assertEquals(object("{\"a\": {\"x\": 3, \"y\": 2}, \"k\": 2}"), result);
  }

  @Test
  void aNestedObjectBuiltFromAConflictIsNotSharedWithAnInput() {
    final RegoArray input = array("[{\"a\": {\"x\": 1}}, {\"a\": {\"y\": 2}}]");
    final RegoObject first = (RegoObject) input.getValues().get(0);
    final RegoObject second = (RegoObject) input.getValues().get(1);

    final RegoObject result = unionN(input);
    final RegoObject merged = (RegoObject) result.getProperty("a");
    merged.setProperty("z", new RegoString("changed"));

    assertNotSame(first.getProperty("a"), merged);
    assertNotSame(second.getProperty("a"), merged);
    assertEquals(object("{\"a\": {\"x\": 1}}"), first);
    assertEquals(object("{\"a\": {\"y\": 2}}"), second);
  }

  @Test
  void theSameObjectTwiceIsMergedWithItselfWithoutHarm() {
    final RegoObject shared = object("{\"a\": {\"x\": 1}}");
    final List<RegoValue> values = new ArrayList<>(List.of(shared, shared, object("{\"a\": {\"y\": 2}}")));

    final RegoObject result = unionN(new RegoArray(values));

    assertEquals(object("{\"a\": {\"x\": 1, \"y\": 2}}"), result);
    assertEquals(object("{\"a\": {\"x\": 1}}"), shared);
  }

  @Test
  void aNonObjectElementIsATypeError() {
    final RegoArray input = array("[{\"a\": 1}, 5]");

    final TypeError error = assertThrows(TypeError.class, () -> unionN(input));

    assertTrue(
        error.getMessage().endsWith("operand 0 must be array of objects but got array containing number"),
        error.getMessage());
  }
}
