package io.github.open_policy_agent.opa.ast.builtin.impls;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.open_policy_agent.opa.ast.types.RegoBoolean;
import io.github.open_policy_agent.opa.ast.types.RegoObject;
import io.github.open_policy_agent.opa.ast.types.RegoString;
import io.github.open_policy_agent.opa.ast.types.RegoValue;
import io.github.open_policy_agent.opa.rego.EvaluationContext;
import org.junit.jupiter.api.Test;

class JsonBuiltinsTest {

  private final JsonBuiltins builtins = new JsonBuiltins();

  @Test
  void registersYamlIsValidBuiltin() {
    assertTrue(builtins.builtins().containsKey("yaml.is_valid"));
  }

  @Test
  void yamlIsValidReturnsTrueForValidYaml() {
    RegoValue[] args = {new RegoString("name: opa\nvalues:\n  - sdk\n  - java\n")};

    assertEquals(RegoBoolean.TRUE, builtins.yamlIsValid(null, args));
  }

  @Test
  void yamlIsValidReturnsFalseForInvalidYaml() {
    RegoValue[] args = {new RegoString("name: [unterminated")};

    assertEquals(RegoBoolean.FALSE, builtins.yamlIsValid(null, args));
  }

  @Test
  void yamlIsValidReturnsFalseForNonStringInput() {
    RegoValue[] args = {RegoBoolean.TRUE};

    assertEquals(RegoBoolean.FALSE, builtins.yamlIsValid(null, args));
  }

  @Test
  void isValidReturnsFalseForEmptyAndBlankInput() {
    assertEquals(RegoBoolean.FALSE, builtins.is_valid(null, new RegoValue[] {new RegoString("")}));
    assertEquals(RegoBoolean.FALSE, builtins.is_valid(null, new RegoValue[] {new RegoString("  ")}));
  }

  @Test
  void isValidReturnsTrueForValidJson() {
    assertEquals(RegoBoolean.TRUE, builtins.is_valid(null, new RegoValue[] {new RegoString("{\"a\": 1}")}));
    assertEquals(RegoBoolean.TRUE, builtins.is_valid(null, new RegoValue[] {new RegoString("null")}));
  }

  @Test
  void unmarshalGivesAnUnpairedSurrogateTheReplacementCharacterLikeOpa() {
    final RegoValue json = new RegoString("{\"k\": \"v\\ude00\", \"pair\": \"x\\ud83d\\ude00y\"}");

    final RegoObject result =
        (RegoObject) builtins.unmarshal(new EvaluationContext.Builder().build(), new RegoValue[] {json});

    assertEquals("v\uFFFD", ((RegoString) result.getProperty("k")).getValue());
    assertEquals("x\uD83D\uDE00y", ((RegoString) result.getProperty("pair")).getValue());
  }

  @Test
  void yamlUnmarshalGivesAnUnpairedSurrogateTheReplacementCharacterInKeysAndValues() {
    final RegoValue yaml = new RegoString("\"k\\uD83Dz\": \"v\\uDE00\"");

    final RegoObject result =
        (RegoObject) builtins.yamlUnmarshal(new EvaluationContext.Builder().build(), new RegoValue[] {yaml});

    assertEquals("v\uFFFD", ((RegoString) result.getProperty("k\uFFFDz")).getValue());
  }
}
