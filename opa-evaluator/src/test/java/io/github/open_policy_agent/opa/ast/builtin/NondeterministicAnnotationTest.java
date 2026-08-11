package io.github.open_policy_agent.opa.ast.builtin;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;
import io.github.open_policy_agent.opa.rego.Capabilities;

/** Tests that nondeterministic builtins are properly annotated and recognized. */
public class NondeterministicAnnotationTest {

  @Test
  public void testNondeterministicBuiltinsAreMarked() {
    // Generate capabilities from annotations
    Capabilities capabilities = BuiltinRegistry.generateCapabilities();

    // List of builtins that should be marked as nondeterministic
    List<String> expectedNondeterministicBuiltins =
        List.of(
            "time.now_ns", "io.jwt.decode_verify", "io.jwt.encode_sign", "io.jwt.encode_sign_raw", "opa.runtime");

    for (String builtinName : expectedNondeterministicBuiltins) {
      // Find the descriptor for this builtin
      Descriptor descriptor =
          capabilities.builtins.stream()
              .filter(d -> d.name.equals(builtinName))
              .findFirst()
              .orElse(null);

      assertNotNull(descriptor, "Builtin '" + builtinName + "' should be present in capabilities");

      assertEquals(Boolean.TRUE, descriptor.nondeterministic, "Builtin '" + builtinName + "' should be marked as nondeterministic");
    }
  }

  @Test
  public void testDeterministicBuiltinsAreNotMarked() {
    // Generate capabilities from annotations
    Capabilities capabilities = BuiltinRegistry.generateCapabilities();

    // Sample of builtins that should NOT be marked as nondeterministic
    List<String> deterministicBuiltins = List.of("concat", "count", "sum", "max", "min");

    for (String builtinName : deterministicBuiltins) {
      // Find the descriptor for this builtin
      Descriptor descriptor =
          capabilities.builtins.stream()
              .filter(d -> d.name.equals(builtinName))
              .findFirst()
              .orElse(null);

      if (descriptor != null) {
        assertNotEquals(
            Boolean.TRUE,
            descriptor.nondeterministic,
            "Builtin '" + builtinName + "' should NOT be marked as nondeterministic");
      }
    }
  }

  @Test
  public void testTimeNowNsIsNondeterministic() {
    Capabilities capabilities = BuiltinRegistry.generateCapabilities();

    Descriptor timeNowNs =
        capabilities.builtins.stream()
            .filter(d -> d.name.equals("time.now_ns"))
            .findFirst()
            .orElseThrow(() -> new AssertionError("time.now_ns not found"));

    assertEquals(Boolean.TRUE, timeNowNs.nondeterministic, "time.now_ns must be marked as nondeterministic");
  }

  @Test
  public void testJwtBuiltinsAreNondeterministic() {
    Capabilities capabilities = BuiltinRegistry.generateCapabilities();

    // Test JWT encode/sign builtins
    Descriptor encodeSign =
        capabilities.builtins.stream()
            .filter(d -> d.name.equals("io.jwt.encode_sign"))
            .findFirst()
            .orElseThrow(() -> new AssertionError("io.jwt.encode_sign not found"));

    assertEquals(Boolean.TRUE, encodeSign.nondeterministic, "io.jwt.encode_sign must be marked as nondeterministic");

    Descriptor encodeSignRaw =
        capabilities.builtins.stream()
            .filter(d -> d.name.equals("io.jwt.encode_sign_raw"))
            .findFirst()
            .orElseThrow(() -> new AssertionError("io.jwt.encode_sign_raw not found"));

    assertEquals(Boolean.TRUE, encodeSignRaw.nondeterministic, "io.jwt.encode_sign_raw must be marked as nondeterministic");

    // Test JWT decode_verify
    Descriptor decodeVerify =
        capabilities.builtins.stream()
            .filter(d -> d.name.equals("io.jwt.decode_verify"))
            .findFirst()
            .orElseThrow(() -> new AssertionError("io.jwt.decode_verify not found"));

    assertEquals(Boolean.TRUE, decodeVerify.nondeterministic, "io.jwt.decode_verify must be marked as nondeterministic");
  }
}
