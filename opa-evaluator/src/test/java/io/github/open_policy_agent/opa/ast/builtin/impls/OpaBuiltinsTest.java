package io.github.open_policy_agent.opa.ast.builtin.impls;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import io.github.open_policy_agent.opa.ast.builtin.BuiltinRegistry;
import io.github.open_policy_agent.opa.ast.types.RegoObject;
import io.github.open_policy_agent.opa.ast.types.RegoValue;
import io.github.open_policy_agent.opa.rego.EvaluationContext;

public class OpaBuiltinsTest {

  @Test
  public void testOpaRuntimeBuiltinRegistered() {
    BuiltinRegistry registry = BuiltinRegistry.allCapabilities();
    assertTrue(registry.hasBuiltIn("opa.runtime"), "opa.runtime should be registered");
  }

  @Test
  public void testOpaRuntimeReturnsRegoObject() {
    OpaBuiltins opaBuiltins = new OpaBuiltins();
    EvaluationContext ctx = new EvaluationContext.Builder().build();
    RegoValue result = opaBuiltins.runtime(ctx, new RegoValue[0]);

    assertNotNull(result);
    assertTrue(result instanceof RegoObject);
    assertEquals("object", result.getTypeName());
  }
}
