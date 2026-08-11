package io.github.open_policy_agent.opa.ast.builtin.impls;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.open_policy_agent.opa.ast.builtin.BuiltinError;
import io.github.open_policy_agent.opa.ast.types.RegoBoolean;
import io.github.open_policy_agent.opa.ast.types.RegoInt32;
import io.github.open_policy_agent.opa.ast.types.RegoString;
import io.github.open_policy_agent.opa.ast.types.RegoValue;
import java.util.ServiceLoader;
import org.junit.jupiter.api.Test;

class RegexBuiltinsTest {

  private final RegexBuiltins builtins = new RegexBuiltins();

  /**
   * The provider entry in META-INF/services was commented out, so none of these builtins were
   * reachable for consumers even though the module was documented as supported.
   */
  @Test
  void providerIsDiscoverableViaServiceLoader() {
    boolean found =
        ServiceLoader.load(
                io.github.open_policy_agent.opa.ast.builtin.BuiltinProvider.class,
                RegexBuiltins.class.getClassLoader())
            .stream()
            .anyMatch(p -> p.type().equals(RegexBuiltins.class));

    assertTrue(found, "RegexBuiltins should be registered as a BuiltinProvider");
  }

  @Test
  void registersAllRegexBuiltins() {
    assertTrue(
        builtins
            .builtins()
            .keySet()
            .containsAll(
                java.util.List.of(
                    "regex.match",
                    "regex.is_valid",
                    "regex.split",
                    "regex.find_n",
                    "regex.find_all_string_submatch_n",
                    "regex.replace",
                    "regex.template_match",
                    "regex.globs_match")));
  }

  // regex.match mirrors Go's regexp.MatchString, which searches rather than matching the whole
  // input. An unanchored pattern therefore matches a substring.
  @Test
  void matchSearchesRatherThanRequiringFullInput() {
    assertEquals(RegoBoolean.TRUE, match("", "x"));
    assertEquals(RegoBoolean.TRUE, match("b", "abc"));
    assertEquals(RegoBoolean.TRUE, match("^[a-z]+\\[[0-9]+\\]$", "foo[1]"));
  }

  @Test
  void matchHonoursAnchors() {
    assertEquals(RegoBoolean.TRUE, match("^$", ""));
    assertEquals(RegoBoolean.FALSE, match("^$", "something"));
    assertEquals(RegoBoolean.FALSE, match("^b", "abc"));
  }

  // An invalid pattern is a builtin error in OPA, not an unchecked PatternSyntaxException.
  @Test
  void matchRaisesBuiltinErrorForInvalidPattern() {
    assertThrows(BuiltinError.class, () -> match("$^[[[", "something"));
  }

  @Test
  void replaceRaisesBuiltinErrorForInvalidPattern() {
    RegoValue[] args = {new RegoString("foo"), new RegoString("["), new RegoString("$1")};

    assertThrows(BuiltinError.class, () -> builtins.replace(null, args));
  }

  @Test
  void splitRaisesBuiltinErrorForInvalidPattern() {
    RegoValue[] args = {new RegoString("["), new RegoString("foo")};

    assertThrows(BuiltinError.class, () -> builtins.split(null, args));
  }

  @Test
  void findRaisesBuiltinErrorForInvalidPattern() {
    RegoValue[] args = {new RegoString("["), new RegoString("foo"), RegoInt32.of(-1)};

    assertThrows(BuiltinError.class, () -> builtins.find(null, args));
  }

  @Test
  void findAllStringSubmatchRaisesBuiltinErrorForInvalidPattern() {
    RegoValue[] args = {new RegoString("["), new RegoString("foo"), RegoInt32.of(-1)};

    assertThrows(BuiltinError.class, () -> builtins.findSubstringMatch(null, args));
  }

  @Test
  void templateMatchRaisesBuiltinErrorForInvalidPattern() {
    RegoValue[] args = {
      new RegoString("{[}"), new RegoString("foo"), new RegoString("{"), new RegoString("}")
    };

    assertThrows(BuiltinError.class, () -> builtins.templateMatch(null, args));
  }

  private RegoBoolean match(String pattern, String value) {
    RegoValue[] args = {new RegoString(pattern), new RegoString(value)};
    return builtins.match(null, args);
  }
}
