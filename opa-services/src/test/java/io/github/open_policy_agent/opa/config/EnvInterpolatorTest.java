package io.github.open_policy_agent.opa.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EnvInterpolatorTest {

  @Test
  void interpolate_substitutesKnownVar() {
    Map<String, String> env = new HashMap<>();
    env.put("TOKEN", "abc123");
    String out = EnvInterpolator.interpolate("token: ${TOKEN}", env);
    assertEquals("token: abc123", out);
  }

  @Test
  void interpolate_multiplePlaceholders() {
    Map<String, String> env = new HashMap<>();
    env.put("USER", "alice");
    env.put("PASS", "secret");
    String out = EnvInterpolator.interpolate("user=${USER}, pass=${PASS}", env);
    assertEquals("user=alice, pass=secret", out);
  }

  @Test
  void interpolate_missingVar_throws() {
    Map<String, String> env = new HashMap<>();
    ConfigurationException e =
        assertThrows(
            ConfigurationException.class,
            () -> EnvInterpolator.interpolate("token: ${UNSET}", env));
    assertTrue(e.getMessage().contains("UNSET"));
  }

  @Test
  void interpolate_escaped_keepsLiteral() {
    Map<String, String> env = new HashMap<>();
    env.put("TOKEN", "abc");
    String out = EnvInterpolator.interpolate("token: \\${TOKEN}", env);
    assertEquals("token: ${TOKEN}", out);
  }

  @Test
  void interpolate_noPlaceholder_returnsUnchanged() {
    String input = "plain config string";
    assertEquals(input, EnvInterpolator.interpolate(input, new HashMap<>()));
  }

  @Test
  void interpolate_replacementWithDollarSigns_escapesProperly() {
    // Matcher.appendReplacement treats $ specially; quoteReplacement guards against this.
    Map<String, String> env = new HashMap<>();
    env.put("VAR", "$weird $1 value");
    String out = EnvInterpolator.interpolate("v=${VAR}", env);
    assertEquals("v=$weird $1 value", out);
  }
}
