package io.github.open_policy_agent.opa.plugins;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.Test;

/** Checked against the behavior of {@code plugins/logs/mask.go} in OPA's Go implementation. */
class MaskRuleSetTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private static ObjectNode json(String raw) throws JsonProcessingException {
    return (ObjectNode) MAPPER.readTree(raw);
  }

  private static void assertMasked(String rules, String event, String expected) throws Exception {
    ObjectNode actual = json(event);
    MaskRuleSet.parse(MAPPER.readTree(rules)).apply(actual);
    assertEquals(json(expected), actual);
  }

  private static String parseError(String rules) {
    JsonNode parsed;
    try {
      parsed = MAPPER.readTree(rules);
    } catch (JsonProcessingException e) {
      throw new AssertionError(e);
    }
    return assertThrows(IllegalArgumentException.class, () -> MaskRuleSet.parse(parsed)).getMessage();
  }

  @Test
  void apply_shorthandRule_removesFieldAndRecordsErased() throws Exception {
    assertMasked(
        "[\"/input/password\"]",
        "{\"input\": {\"user\": \"alice\", \"password\": \"secret\"}}",
        "{\"input\": {\"user\": \"alice\"}, \"erased\": [\"/input/password\"]}");
  }

  @Test
  void apply_removeOp_removesFieldAndRecordsErased() throws Exception {
    assertMasked(
        "[{\"op\": \"remove\", \"path\": \"/input/password\"}]",
        "{\"input\": {\"user\": \"alice\", \"password\": \"secret\"}}",
        "{\"input\": {\"user\": \"alice\"}, \"erased\": [\"/input/password\"]}");
  }

  @Test
  void apply_removeOp_ignoresValue() throws Exception {
    assertMasked(
        "[{\"op\": \"remove\", \"path\": \"/input/password\", \"value\": \"x\"}]",
        "{\"input\": {\"password\": \"secret\"}}",
        "{\"input\": {}, \"erased\": [\"/input/password\"]}");
  }

  @Test
  void apply_removeNestedPath_removesLeafOnly() throws Exception {
    assertMasked(
        "[\"/input/foo/0/bar\"]",
        "{\"input\": {\"foo\": [{\"bar\": 1, \"baz\": 2}]}}",
        "{\"input\": {\"foo\": [{\"baz\": 2}]}, \"erased\": [\"/input/foo/0/bar\"]}");
  }

  @Test
  void apply_removeArrayElement_shrinksArray() throws Exception {
    assertMasked(
        "[\"/input/foo/1\"]",
        "{\"input\": {\"foo\": [1, 2, 3]}}",
        "{\"input\": {\"foo\": [1, 3]}, \"erased\": [\"/input/foo/1\"]}");
  }

  @Test
  void apply_removeElementOfArrayValuedField_shrinksArray() throws Exception {
    // Deliberate divergence from Go, which skips the rule here. See MaskRule.remove.
    assertMasked(
        "[\"/input/1\"]",
        "{\"input\": [1, 2, 3]}",
        "{\"input\": [1, 3], \"erased\": [\"/input/1\"]}");
  }

  @Test
  void apply_removeWholeField_dropsFieldFromEvent() throws Exception {
    assertMasked(
        "[\"/input\"]",
        "{\"input\": {\"password\": \"secret\"}, \"result\": true}",
        "{\"result\": true, \"erased\": [\"/input\"]}");
  }

  @Test
  void apply_upsert_replacesValueAndRecordsMasked() throws Exception {
    assertMasked(
        "[{\"op\": \"upsert\", \"path\": \"/input/password\", \"value\": \"**REDACTED**\"}]",
        "{\"input\": {\"user\": \"alice\", \"password\": \"secret\"}}",
        "{\"input\": {\"user\": \"alice\", \"password\": \"**REDACTED**\"},"
            + " \"masked\": [\"/input/password\"]}");
  }

  @Test
  void apply_upsert_createsMissingIntermediateObjects() throws Exception {
    assertMasked(
        "[{\"op\": \"upsert\", \"path\": \"/input/a/b\", \"value\": 1}]",
        "{\"input\": {}}",
        "{\"input\": {\"a\": {\"b\": 1}}, \"masked\": [\"/input/a/b\"]}");
  }

  @Test
  void apply_upsertWithoutValue_setsNull() throws Exception {
    assertMasked(
        "[{\"op\": \"upsert\", \"path\": \"/input/password\"}]",
        "{\"input\": {\"password\": \"secret\"}}",
        "{\"input\": {\"password\": null}, \"masked\": [\"/input/password\"]}");
  }

  @Test
  void apply_upsertWholeField_replacesField() throws Exception {
    assertMasked(
        "[{\"op\": \"upsert\", \"path\": \"/result\", \"value\": {\"allow\": false}}]",
        "{\"input\": {}, \"result\": {\"allow\": true, \"token\": \"t\"}}",
        "{\"input\": {}, \"result\": {\"allow\": false}, \"masked\": [\"/result\"]}");
  }

  @Test
  void apply_upsertIntoArrayTarget_isNoOp() throws Exception {
    // Go only upserts into an object, so an array-valued input is left alone.
    assertMasked(
        "[{\"op\": \"upsert\", \"path\": \"/input/0\", \"value\": 1}]",
        "{\"input\": [\"a\"]}",
        "{\"input\": [\"a\"]}");
  }

  @Test
  void apply_upsertThroughNonContainer_isNoOp() throws Exception {
    assertMasked(
        "[{\"op\": \"upsert\", \"path\": \"/input/foo/bar\", \"value\": 1}]",
        "{\"input\": {\"foo\": \"scalar\"}}",
        "{\"input\": {\"foo\": \"scalar\"}}");
  }

  @Test
  void apply_undefinedPath_isNoOpAndRecordsNothing() throws Exception {
    assertMasked(
        "[\"/input/password\", \"/input/a/b\"]",
        "{\"input\": {\"user\": \"alice\"}}",
        "{\"input\": {\"user\": \"alice\"}}");
  }

  @Test
  void apply_absentTarget_isNoOp() throws Exception {
    assertMasked("[\"/result/token\"]", "{\"input\": {}}", "{\"input\": {}}");
    assertMasked("[\"/result\"]", "{\"input\": {}}", "{\"input\": {}}");
  }

  @Test
  void apply_nullTarget_masksWholeFieldOnly() throws Exception {
    // Go nils out (and records) the whole field, but cannot descend into a null one.
    assertMasked("[\"/result\"]", "{\"result\": null}", "{\"erased\": [\"/result\"]}");
    assertMasked(
        "[{\"op\": \"upsert\", \"path\": \"/result\", \"value\": 1}]",
        "{\"result\": null}",
        "{\"result\": 1, \"masked\": [\"/result\"]}");
    assertMasked("[\"/result/token\"]", "{\"result\": null}", "{\"result\": null}");
  }

  @Test
  void apply_ndBuiltinCacheTarget_isSupported() throws Exception {
    assertMasked(
        "[{\"op\": \"upsert\", \"path\": \"/nd_builtin_cache/rand.intn\", \"value\": \"x\"}]",
        "{\"nd_builtin_cache\": {\"rand.intn\": {\"[\\\"z\\\",15]\": 7}}}",
        "{\"nd_builtin_cache\": {\"rand.intn\": \"x\"},"
            + " \"masked\": [\"/nd_builtin_cache/rand.intn\"]}");
  }

  @Test
  void apply_multipleRules_recordErasedAndMaskedSeparately() throws Exception {
    assertMasked(
        "[\"/input/password\","
            + " {\"op\": \"upsert\", \"path\": \"/input/jwt\", \"value\": \"redacted\"},"
            + " \"/result/token\"]",
        "{\"input\": {\"password\": \"secret\", \"jwt\": \"a.b.c\"},"
            + " \"result\": {\"token\": \"t\", \"allow\": true}}",
        "{\"input\": {\"jwt\": \"redacted\"}, \"result\": {\"allow\": true},"
            + " \"erased\": [\"/input/password\", \"/result/token\"],"
            + " \"masked\": [\"/input/jwt\"]}");
  }

  @Test
  void apply_conflictingRulesOnOnePath_lastRuleWins() throws Exception {
    // Rules are applied in the order the policy returns them, so a remove followed by an upsert
    // leaves the field in place, redacted. Both rules still record themselves.
    assertMasked(
        "[\"/input/password\","
            + " {\"op\": \"upsert\", \"path\": \"/input/password\", \"value\": \"**REDACTED**\"}]",
        "{\"input\": {\"user\": \"alice\", \"password\": \"secret\"}}",
        "{\"input\": {\"user\": \"alice\", \"password\": \"**REDACTED**\"},"
            + " \"erased\": [\"/input/password\"], \"masked\": [\"/input/password\"]}");

    // The same pair the other way round: the upsert is undone by the remove.
    assertMasked(
        "[{\"op\": \"upsert\", \"path\": \"/input/password\", \"value\": \"**REDACTED**\"},"
            + " \"/input/password\"]",
        "{\"input\": {\"user\": \"alice\", \"password\": \"secret\"}}",
        "{\"input\": {\"user\": \"alice\"},"
            + " \"masked\": [\"/input/password\"], \"erased\": [\"/input/password\"]}");
  }

  @Test
  void apply_ruleAfterWholeFieldRemove_isSkipped() throws Exception {
    // The first rule takes the field off the event, so the second has nothing to descend into --
    // Go's nil Input pointer -- and records nothing.
    assertMasked(
        "[\"/input\", {\"op\": \"upsert\", \"path\": \"/input/password\", \"value\": \"x\"}]",
        "{\"input\": {\"password\": \"secret\"}, \"result\": true}",
        "{\"result\": true, \"erased\": [\"/input\"]}");
  }

  @Test
  void apply_ruleAfterWholeFieldUpsert_appliesToTheNewValue() throws Exception {
    // The whole-field upsert replaces input, so the remove that follows looks for its path in the
    // replacement, where it is undefined.
    assertMasked(
        "[{\"op\": \"upsert\", \"path\": \"/input\", \"value\": {\"user\": \"alice\"}},"
            + " \"/input/password\"]",
        "{\"input\": {\"user\": \"alice\", \"password\": \"secret\"}}",
        "{\"input\": {\"user\": \"alice\"}, \"masked\": [\"/input\"]}");
  }

  @Test
  void apply_doesNotMutateOriginalNodes() throws Exception {
    ObjectNode input = MAPPER.createObjectNode().put("password", "secret");
    ObjectNode result = MAPPER.createObjectNode().put("token", "t");
    ObjectNode event = MAPPER.createObjectNode();
    event.set("input", input);
    event.set("result", result);

    MaskRuleSet.parse(
            MAPPER.readTree(
                "[\"/input/password\","
                    + " {\"op\": \"upsert\", \"path\": \"/result/token\", \"value\": \"r\"}]"))
        .apply(event);

    assertEquals(json("{\"password\": \"secret\"}"), input);
    assertEquals(json("{\"token\": \"t\"}"), result);
    assertEquals(json("{}"), event.get("input"));
    assertEquals(json("{\"token\": \"r\"}"), event.get("result"));
  }

  @Test
  void apply_escapedPathSegment_usesEscapedForm() throws Exception {
    // Go escapes each segment with url.PathEscape and matches keys against the escaped form.
    assertMasked(
        "[\"/input/a/%2F%2F/b\"]",
        "{\"input\": {\"a\": {\"%252F%252F\": {\"b\": 1}}}}",
        "{\"input\": {\"a\": {\"%252F%252F\": {}}}, \"erased\": [\"/input/a/%252F%252F/b\"]}");
  }

  @Test
  void parse_notAnArray_throws() {
    assertTrue(parseError("{\"op\": \"remove\"}").startsWith("unexpected rule format"));
    assertTrue(parseError("\"/input/password\"").startsWith("unexpected rule format"));
  }

  @Test
  void parse_ruleOfUnsupportedType_throws() {
    assertTrue(parseError("[[1, 2]]").startsWith("invalid mask rule format encountered"));
  }

  @Test
  void parse_emptyPath_throws() {
    assertEquals("mask must be non-empty", parseError("[\"\"]"));
    assertEquals("mask must be non-empty", parseError("[{\"op\": \"remove\"}]"));
  }

  @Test
  void parse_pathWithoutLeadingSlash_throws() {
    assertEquals("mask must be slash-prefixed", parseError("[\"input/password\"]"));
  }

  @Test
  void parse_pathOutsideMaskableFields_throws() {
    assertEquals("mask prefix not allowed: labels", parseError("[\"/labels/environment\"]"));
  }

  @Test
  void parse_missingOp_throws() {
    // The structured form has no default op in OPA; only the shorthand string form implies remove.
    assertEquals("mask op is not supported: ", parseError("[{\"path\": \"/input/password\"}]"));
  }

  @Test
  void parse_unsupportedOp_throws() {
    assertEquals(
        "mask op is not supported: replace",
        parseError("[{\"op\": \"replace\", \"path\": \"/input/password\"}]"));
  }

  @Test
  void parse_nonStringOpOrPath_throws() {
    assertEquals(
        "invalid \"op\" value: 1", parseError("[{\"op\": 1, \"path\": \"/input/password\"}]"));
    assertEquals(
        "invalid \"path\" value: \"\"", parseError("[{\"op\": \"remove\", \"path\": \"\"}]"));
  }

  @Test
  void parse_invalidEscapeInPath_throws() {
    assertEquals("invalid URL escape \"%F\"", parseError("[\"/input/a/%F/b\"]"));
  }
}
