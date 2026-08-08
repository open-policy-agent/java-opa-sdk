package io.github.open_policy_agent.opa.ast.builtin.impls;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.open_policy_agent.opa.ast.types.RegoBoolean;
import io.github.open_policy_agent.opa.ast.types.RegoString;
import io.github.open_policy_agent.opa.ast.types.RegoValue;
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
}
