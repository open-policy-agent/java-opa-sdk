package io.github.open_policy_agent.opa.ast.builtin.impls;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.open_policy_agent.opa.ast.types.RegoArray;
import io.github.open_policy_agent.opa.ast.types.RegoInt32;
import io.github.open_policy_agent.opa.ast.types.RegoNull;
import io.github.open_policy_agent.opa.ast.types.RegoObject;
import io.github.open_policy_agent.opa.ast.types.RegoSet;
import io.github.open_policy_agent.opa.ast.types.RegoString;
import io.github.open_policy_agent.opa.ast.types.RegoValue;
import io.github.open_policy_agent.opa.rego.EvaluationContext;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * Expected pairs are the output of {@code opa eval} on the equivalent {@code walk(x, [p, v])} query
 * for the same input, so these assertions pin Go-OPA parity rather than just internal consistency.
 */
public class GraphBuiltinsTest {

  private final GraphBuiltins graphBuiltins = new GraphBuiltins();
  private final EvaluationContext ctx = new EvaluationContext.Builder().build();

  private Set<String> walkPairs(RegoValue input) {
    RegoValue result = graphBuiltins.walk(ctx, new RegoValue[] {input});
    assertTrue(result instanceof RegoSet, "walk must return a set of [path, value] pairs");
    return ((RegoSet) result)
        .getValue().stream().map(RegoValue::toString).collect(Collectors.toSet());
  }

  @Test
  public void testWalkNestedObjectAndArray() {
    // {"a": {"b": [1, {"c": "d"}]}, "e": null}
    RegoObject inner = new RegoObject(Map.of(new RegoString("c"), new RegoString("d")));
    RegoArray b = new RegoArray(List.of(RegoInt32.of(1), inner));
    Map<RegoValue, RegoValue> root = new LinkedHashMap<>();
    root.put(new RegoString("a"), new RegoObject(Map.of(new RegoString("b"), b)));
    root.put(new RegoString("e"), RegoNull.INSTANCE);
    RegoObject input = new RegoObject(root);

    Set<String> pairs = walkPairs(input);

    // The root is always emitted with an empty path, plus one pair per nested document.
    assertEquals(7, pairs.size());
    assertTrue(pairs.contains("[[], " + input + "]"), "root pair with empty path: " + pairs);
    assertTrue(pairs.contains("[[\"a\", \"b\", 0], 1]"), "array index in path: " + pairs);
    assertTrue(pairs.contains("[[\"a\", \"b\", 1, \"c\"], \"d\"]"), "leaf under array: " + pairs);
    assertTrue(
        pairs.contains("[[\"a\", \"b\", 1], " + inner + "]"), "array element itself: " + pairs);
    assertTrue(pairs.contains("[[\"a\", \"b\"], " + b + "]"), "array itself: " + pairs);
    assertTrue(pairs.contains("[[\"e\"], null]"), "null leaf is walked: " + pairs);
  }

  @Test
  public void testWalkDescendsIntoSetsKeyedByMember() {
    // walk({"k": {"x", "y"}, "n": 3} -- a set member's path segment is the member itself.
    RegoSet members = new RegoSet(false);
    members.addValue(new RegoString("x"));
    members.addValue(new RegoString("y"));
    Map<RegoValue, RegoValue> root = new LinkedHashMap<>();
    root.put(new RegoString("k"), members);
    root.put(new RegoString("n"), RegoInt32.of(3));

    Set<String> pairs = walkPairs(new RegoObject(root));

    assertEquals(5, pairs.size());
    assertTrue(pairs.contains("[[\"k\", \"x\"], \"x\"]"), "set member keyed by itself: " + pairs);
    assertTrue(pairs.contains("[[\"k\", \"y\"], \"y\"]"), "set member keyed by itself: " + pairs);
    assertTrue(pairs.contains("[[\"n\"], 3]"), "sibling scalar: " + pairs);
  }

  @Test
  public void testWalkObjectWithCompositeKeyUsesKeyAsPathSegment() {
    // walk({["a", 1]: "v"}) -- an object key can itself be composite, so a path segment can be an
    // array. opa eval yields the root plus a pair whose path is [["a", 1]].
    RegoArray compositeKey = new RegoArray(List.of(new RegoString("a"), RegoInt32.of(1)));
    RegoObject input = new RegoObject(Map.of(compositeKey, new RegoString("v")));

    Set<String> pairs = walkPairs(input);

    assertEquals(2, pairs.size());
    assertTrue(pairs.contains("[[], " + input + "]"), "root pair: " + pairs);
    assertTrue(
        pairs.contains("[[" + compositeKey + "], \"v\"]"),
        "composite key becomes one path segment: " + pairs);
  }

  @Test
  public void testWalkScalarYieldsOnlyRoot() {
    Set<String> pairs = walkPairs(RegoInt32.of(5));

    assertEquals(Set.of("[[], 5]"), pairs);
  }

  @Test
  public void testWalkEmptyCollectionYieldsOnlyRoot() {
    RegoObject empty = new RegoObject();

    assertEquals(Set.of("[[], " + empty + "]"), walkPairs(empty));
  }

  @Test
  public void testWalkUndefinedArgumentYieldsNoPairs() {
    // An undefined operand makes the expression undefined in Go-OPA; the plan scans the returned
    // collection, so an empty set is what produces zero iterations here.
    assertEquals(Set.of(), walkPairs(null));
  }
}
