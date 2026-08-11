package io.github.open_policy_agent.opa.ir;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.open_policy_agent.opa.ast.builtin.impls.ProvidersAwsBuiltins;
import io.github.open_policy_agent.opa.ast.types.RegoDecimal;
import io.github.open_policy_agent.opa.ast.types.RegoObject;
import io.github.open_policy_agent.opa.ast.types.RegoString;
import io.github.open_policy_agent.opa.ast.types.RegoValue;
import java.lang.reflect.Method;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Verifies canonical JSON serialization matches Go's {@code encoding/json} behavior.
 */
@DisplayName("providers.aws canonical JSON")
class ProvidersAwsCanonicalJsonTest {

  /**
   * Expected values verified against Go 1.25 {@code json.Marshal(float64)} via the Go playground.
   *
   * <p>Note: {@code json.Number("1e21")} preserves the original literal as {@code 1e21}, but
   * marshaling a {@code float64} {@code 1e21} produces {@code 1e+21}. {@code RegoDecimal} follows
   * the float64 path.
   */
  static Stream<Arguments> goFloatMarshalCases() {
    return Stream.of(
        Arguments.of(1e21, "1e+21"),
        Arguments.of(3.14, "3.14"),
        Arguments.of(100.0, "100"),
        Arguments.of(1e20, "100000000000000000000"),
        Arguments.of(1e-7, "1e-7"),
        Arguments.of(1e-6, "0.000001"),
        Arguments.of(1.23e10, "12300000000"),
        Arguments.of(42.0, "42"),
        Arguments.of(-1e21, "-1e+21"),
        Arguments.of(-0.0, "0"));
  }

  @ParameterizedTest(name = "{0} => {1}")
  @MethodSource("goFloatMarshalCases")
  void floatFormattingMatchesGoJsonMarshal(double value, String expected) throws Exception {
    assertEquals(expected, canonicalJson(new RegoDecimal(value)));
  }

  @Test
  void floatInObjectMatchesGoJsonMarshal() throws Exception {
    RegoObject body = new RegoObject();
    body.setProp(new RegoString("magnitude"), new RegoDecimal(1e21));
    body.setProp(new RegoString("ratio"), new RegoDecimal(3.14));
    body.setProp(new RegoString("count"), new RegoDecimal(100.0));
    assertEquals(
        "{\"count\":100,\"magnitude\":1e+21,\"ratio\":3.14}", canonicalJson(body));
  }

  @Test
  void htmlEscapesMatchGoEncodingJson() throws Exception {
    // Go json.Marshal escapes &, <, >, U+2028, and U+2029 in strings.
    String input = "a&b<c>d\u2028e\u2029f";
    String expectedJson = "\"a\\u0026b\\u003cc\\u003ed\\u2028e\\u2029f\"";

    String actualJson = canonicalJson(new RegoString(input));
    assertEquals(expectedJson, actualJson);

    RegoObject body = new RegoObject();
    body.setProp(new RegoString("text"), new RegoString("a&b<c>d"));
    String expectedObjectJson = "{\"text\":\"a\\u0026b\\u003cc\\u003ed\"}";
    assertEquals(expectedObjectJson, canonicalJson(body));
  }

  private static String canonicalJson(RegoValue value) throws Exception {
    Method method =
        ProvidersAwsBuiltins.class.getDeclaredMethod("canonicalJson", RegoValue.class);
    method.setAccessible(true);
    return (String) method.invoke(null, value);
  }
}
