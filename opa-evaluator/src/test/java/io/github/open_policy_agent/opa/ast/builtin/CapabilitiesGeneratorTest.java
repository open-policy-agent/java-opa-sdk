package io.github.open_policy_agent.opa.ast.builtin;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import io.github.open_policy_agent.opa.rego.Capabilities;

/** Test class for validating OPA builtin annotations and capabilities generation. */
public class CapabilitiesGeneratorTest {

  @Test
  public void testAllBuiltinsHaveValidAnnotations() {
    Capabilities capabilities = BuiltinRegistry.generateCapabilities();

    // All builtins should have at least a name
    for (Descriptor descriptor : capabilities.builtins) {
      assertNotNull(descriptor.name, "Builtin must have a name");
      assertFalse(descriptor.name.isEmpty(), "Builtin name cannot be empty");
    }
  }

  @Test
  public void testAnnotatedBuiltinsHaveCompleteMetadata() {
    Capabilities capabilities = BuiltinRegistry.generateCapabilities();

    List<Descriptor> annotated =
        capabilities.builtins.stream()
            .filter(d -> d.decl != null && d.decl.args != null && !d.decl.args.isEmpty())
            .collect(Collectors.toList());

    assertFalse(annotated.isEmpty(), "Should have at least some annotated builtins");

    for (Descriptor descriptor : annotated) {
      // Validate declaration exists
      assertNotNull(descriptor.decl, "Annotated builtin must have declaration");
      assertNotNull(descriptor.decl.args, "Declaration must have args list");
      assertNotNull(descriptor.decl.result, "Declaration must have result type");

      // Validate each argument has a type
      for (Descriptor.Argument arg : descriptor.decl.args) {
        assertNotNull(arg.type, "Argument must have a type: " + descriptor.name);
        assertFalse(arg.type.isEmpty(), "Argument type cannot be empty: " + descriptor.name);
      }

      // Validate result has a type
      assertNotNull(descriptor.decl.result.type, "Result must have a type: " + descriptor.name);
      assertFalse(
          descriptor.decl.result.type.isEmpty(), "Result type cannot be empty: " + descriptor.name);
    }
  }

  @Test
  public void testDynamicTypesAreValid() {
    Capabilities capabilities = BuiltinRegistry.generateCapabilities();

    for (Descriptor descriptor : capabilities.builtins) {
      if (descriptor.decl != null && descriptor.decl.args != null) {
        for (Descriptor.Argument arg : descriptor.decl.args) {
          if (arg.dynamic != null) {
            // Dynamic must have either a type OR both key and value
            boolean hasType = arg.dynamic.type != null && !arg.dynamic.type.isEmpty();
            boolean hasKeyValue = arg.dynamic.key != null && arg.dynamic.value != null;

            assertTrue(
                hasType || hasKeyValue,
                "Dynamic type must have either 'type' or 'key+value': " + descriptor.name);

            if (hasKeyValue) {
              assertNotNull(arg.dynamic.key.type, "Dynamic key must have type: " + descriptor.name);
              assertNotNull(
                  arg.dynamic.value.type, "Dynamic value must have type: " + descriptor.name);
            }
          }
        }
      }
    }
  }

  @Test
  public void testUnionTypesAreValid() {
    Capabilities capabilities = BuiltinRegistry.generateCapabilities();

    for (Descriptor descriptor : capabilities.builtins) {
      if (descriptor.decl != null && descriptor.decl.args != null) {
        for (Descriptor.Argument arg : descriptor.decl.args) {
          if (arg.of != null && !arg.of.isEmpty()) {
            // Union type should have "any" as base type
            assertEquals("any", arg.type, "Union type base should be 'any': " + descriptor.name);

            // Each union option must have a type
            for (Descriptor.Argument unionArg : arg.of) {
              assertNotNull(unionArg.type, "Union option must have type: " + descriptor.name);
              assertFalse(
                  unionArg.type.isEmpty(), "Union option type cannot be empty: " + descriptor.name);
            }
          }
        }
      }
    }
  }

  @Test
  public void testCapabilitiesJsonSerializationWorks() throws IOException {
    Capabilities capabilities = BuiltinRegistry.generateCapabilities();

    // Should be able to serialize to JSON
    String json = io.github.open_policy_agent.opa.jackson.JacksonCapabilities.toJson(capabilities);
    assertNotNull(json);
    assertFalse(json.isEmpty());

    // Should not contain JSON null values (": null") - if it does, find and report which builtin
    // has the issue
    if (json.contains(": null")) {
      // Find the problematic builtin by checking each one individually
      StringBuilder errorMsg =
          new StringBuilder("JSON contains null field values. Problematic builtins:\n");
      for (Descriptor d : capabilities.builtins) {
        String individualJson =
            io.github.open_policy_agent.opa.jackson.JacksonCapabilities.toJson(
                new Capabilities(List.of(d)));
        if (individualJson.contains(": null")) {
          errorMsg.append("  - ").append(d.name).append("\n");
          errorMsg
              .append("    JSON: ")
              .append(individualJson, 0, Math.min(200, individualJson.length()))
              .append("...\n");
        }
      }
      fail(errorMsg.toString());
    }

    // Should be able to deserialize back
    Capabilities deserialized =
        io.github.open_policy_agent.opa.jackson.JacksonCapabilities.fromJson(json);
    assertNotNull(deserialized);
    assertEquals(capabilities.builtins.size(), deserialized.builtins.size());
  }
}
