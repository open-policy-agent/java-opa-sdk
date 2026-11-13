package io.github.open_policy_agent.opa.ast.builtin.impls;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.open_policy_agent.opa.ast.types.RegoString;
import io.github.open_policy_agent.opa.ast.types.RegoValue;
import io.github.open_policy_agent.opa.rego.EvaluationContext;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

// Expected values from opa 1.21.1.
class HexBuiltinsTest {

  private final HexBuiltins builtins = new HexBuiltins();
  private final EvaluationContext ctx = new EvaluationContext.Builder().build();

  static Stream<Arguments> encodeCases() {
    return Stream.of(
        Arguments.of("", ""),
        Arguments.of("a", "61"),
        Arguments.of("hello", "68656c6c6f"),
        Arguments.of("\u00E9", "c3a9"),
        Arguments.of("\u00FF\u0080", "c3bfc280"),
        Arguments.of("\uD83D\uDE00", "f09f9880"),
        Arguments.of("The quick brown fox jumps over the lazy dog", "54686520717569636b2062726f776e20666f78206a756d7073206f76657220746865206c617a7920646f67"),
        Arguments.of("x".repeat(1000), "78".repeat(1000)));
  }

  @ParameterizedTest
  @MethodSource("encodeCases")
  void encodeMatchesOpa(String input, String expected) {
    assertEquals(new RegoString(expected), builtins.encode(ctx, new RegoValue[] {new RegoString(input)}));
  }
}
