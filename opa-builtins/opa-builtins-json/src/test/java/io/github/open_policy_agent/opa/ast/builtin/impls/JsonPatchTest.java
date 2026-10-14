package io.github.open_policy_agent.opa.ast.builtin.impls;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import io.github.open_policy_agent.opa.ast.types.RegoString;
import io.github.open_policy_agent.opa.ast.types.RegoUndefined;
import io.github.open_policy_agent.opa.ast.types.RegoValue;
import io.github.open_policy_agent.opa.rego.EvaluationContext;
import org.junit.jupiter.api.Test;

/**
 * Covers the inline RFC 6902 engine behind json.patch where the upstream json-patch compliance
 * fixtures leave gaps. Expected values were taken from `opa eval` against OPA 1.19.0.
 */
class JsonPatchTest {

  private final JsonBuiltins builtins = new JsonBuiltins();
  private final EvaluationContext ctx = new EvaluationContext.Builder().build();

  private RegoValue json(String s) {
    return builtins.unmarshal(ctx, new RegoValue[] {new RegoString(s)});
  }

  private RegoValue patch(String doc, String patches) {
    return builtins.patch(ctx, new RegoValue[] {json(doc), json(patches)});
  }

  // RFC 6902 §4.4: "from" must not be a proper prefix of "path".
  @Test
  void moveIntoOwnChildIsUndefined() {
    assertSame(RegoUndefined.INSTANCE, patch("{\"a\":{\"b\":1}}", "[{\"op\":\"move\",\"from\":\"/a\",\"path\":\"/a/c\"}]"));
  }

  // The element is removed before it is re-added, so "-" is the end of the shortened array.
  @Test
  void moveArrayElementToEnd() {
    assertEquals(
        json("{\"a\":[{\"x\":2},{\"x\":1}]}"),
        patch("{\"a\":[{\"x\":1},{\"x\":2}]}", "[{\"op\":\"move\",\"from\":\"/a/0\",\"path\":\"/a/-\"}]"));
  }

  // "-" only names a position for add; there is no element there to remove or replace.
  @Test
  void removeAtAppendTokenIsUndefined() {
    assertSame(RegoUndefined.INSTANCE, patch("{\"a\":[1,2]}", "[{\"op\":\"remove\",\"path\":\"/a/-\"}]"));
  }

  @Test
  void replaceAtAppendTokenIsUndefined() {
    assertSame(RegoUndefined.INSTANCE, patch("{\"a\":[1,2]}", "[{\"op\":\"replace\",\"path\":\"/a/-\",\"value\":3}]"));
  }

  // A later op on the copy must not reach back into the source it was copied from.
  @Test
  void copyIsIndependentOfSource() {
    assertEquals(
        json("{\"a\":{\"b\":[1]},\"c\":{\"b\":[1,2]}}"),
        patch(
            "{\"a\":{\"b\":[1]}}",
            "[{\"op\":\"copy\",\"from\":\"/a\",\"path\":\"/c\"},{\"op\":\"add\",\"path\":\"/c/b/-\",\"value\":2}]"));
  }

  @Test
  void testOnMissingPathIsUndefined() {
    assertSame(RegoUndefined.INSTANCE, patch("{\"a\":1}", "[{\"op\":\"test\",\"path\":\"/missing\",\"value\":1}]"));
  }

  @Test
  void decodesEscapedPointerSegments() {
    assertEquals(
        json("{\"m~n\":3}"),
        patch(
            "{\"a/b\":1,\"m~n\":2}",
            "[{\"op\":\"remove\",\"path\":\"/a~1b\"},{\"op\":\"replace\",\"path\":\"/m~0n\",\"value\":3}]"));
  }

  // Go parses indices with strconv.Atoi, which accepts a leading "+".
  @Test
  void acceptsExplicitlySignedIndex() {
    assertEquals(json("{\"a\":[1,9,2]}"), patch("{\"a\":[1,2]}", "[{\"op\":\"add\",\"path\":\"/a/+1\",\"value\":9}]"));
  }

  // A failing op discards the whole patch rather than returning a partially applied document.
  @Test
  void failingOpDiscardsEarlierOps() {
    assertSame(
        RegoUndefined.INSTANCE,
        patch("{\"a\":1}", "[{\"op\":\"add\",\"path\":\"/b\",\"value\":2},{\"op\":\"remove\",\"path\":\"/missing\"}]"));
  }
}
