package io.github.open_policy_agent.opa.ast.builtin.impls;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.open_policy_agent.opa.ast.builtin.BuiltinRegistry;
import io.github.open_policy_agent.opa.ast.types.RegoArray;
import io.github.open_policy_agent.opa.ast.types.RegoInt32;
import io.github.open_policy_agent.opa.ast.types.RegoNull;
import io.github.open_policy_agent.opa.ast.types.RegoObject;
import io.github.open_policy_agent.opa.ast.types.RegoSet;
import io.github.open_policy_agent.opa.ast.types.RegoString;
import io.github.open_policy_agent.opa.ast.types.RegoValue;
import io.github.open_policy_agent.opa.rego.EvaluationContext;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class GraphBuiltinsTest {

  private static final EvaluationContext CONTEXT = new EvaluationContext.Builder().build();

  @Test
  void reachableTraversesArrayAndSetEdges() {
    RegoString a = new RegoString("a");
    RegoString b = new RegoString("b");
    RegoString c = new RegoString("c");
    RegoString d = new RegoString("d");
    RegoObject graph =
        new RegoObject(
            Map.of(
                a, new RegoArray(List.of(b, c)),
                b, setOf(d),
                c, new RegoArray(List.of(d)),
                d, setOf()));

    RegoValue result = call("graph.reachable", graph, setOf(a, new RegoString("missing")));

    assertEquals(setOf(a, b, c, d), result);
  }

  @Test
  void reachablePathsStopsAtCyclesAndMissingNodes() {
    RegoString a = new RegoString("a");
    RegoString b = new RegoString("b");
    RegoString missing = new RegoString("missing");
    RegoObject graph =
        new RegoObject(
            Map.of(
                a, new RegoArray(List.of(b, missing)),
                b, setOf(a)));

    RegoValue result = call("graph.reachable_paths", graph, setOf(a));

    assertEquals(
        setOf(new RegoArray(List.of(a, b)), new RegoArray(List.of(a))), result);
  }

  @Test
  void reachablePathsIncludesInitialSelfLoopOnce() {
    RegoString a = new RegoString("a");
    RegoString b = new RegoString("b");
    RegoObject graph = new RegoObject(Map.of(a, setOf(a, b), b, setOf()));

    RegoValue result = call("graph.reachable_paths", graph, setOf(a));

    assertEquals(
        setOf(
            new RegoArray(List.of(a, a)),
            new RegoArray(List.of(a, a, b)),
            new RegoArray(List.of(a, b))),
        result);
  }

  @Test
  void reachablePathsHandlesDeepGraphsWithoutRecursion() {
    int nodeCount = 10_000;
    List<RegoValue> nodes = new ArrayList<>(nodeCount);
    Map<RegoValue, RegoValue> graph = new LinkedHashMap<>();

    for (int index = 0; index < nodeCount; index++) {
      nodes.add(new RegoString("node-" + index));
    }
    for (int index = 0; index < nodeCount - 1; index++) {
      graph.put(nodes.get(index), new RegoArray(List.of(nodes.get(index + 1))));
    }
    graph.put(nodes.get(nodeCount - 1), setOf());

    RegoValue result = call("graph.reachable_paths", new RegoObject(graph), setOf(nodes.get(0)));

    assertEquals(setOf(new RegoArray(nodes)), result);
  }

  @Test
  void walkNestedObjectAndArray() {
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
  void walkDescendsIntoSetsKeyedByMember() {
    // walk({"k": {"x", "y"}, "n": 3} -- a set member's path segment is the member itself.
    RegoSet members = setOf(new RegoString("x"), new RegoString("y"));
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
  void walkObjectWithCompositeKeyUsesKeyAsPathSegment() {
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
  void walkScalarYieldsOnlyRoot() {
    assertEquals(Set.of("[[], 5]"), walkPairs(RegoInt32.of(5)));
  }

  @Test
  void walkEmptyCollectionYieldsOnlyRoot() {
    RegoObject empty = new RegoObject();

    assertEquals(Set.of("[[], " + empty + "]"), walkPairs(empty));
  }

  @Test
  void walkUndefinedArgumentYieldsNoPairs() {
    // An undefined operand makes the expression undefined in Go-OPA; the plan scans the returned
    // collection, so an empty set is what produces zero iterations here.
    assertEquals(Set.of(), walkPairs(null));
  }

  /**
   * Expected pairs are the output of {@code opa eval} on the equivalent {@code walk(x, [p, v])}
   * query for the same input, so the walk assertions pin Go-OPA parity rather than just internal
   * consistency.
   */
  private static Set<String> walkPairs(RegoValue input) {
    RegoValue result = call("walk", input);
    assertTrue(result instanceof RegoSet, "walk must return a set of [path, value] pairs");
    return ((RegoSet) result)
        .getValue().stream().map(RegoValue::toString).collect(Collectors.toSet());
  }

  private static RegoValue call(String name, RegoValue... args) {
    BiFunction<EvaluationContext, RegoValue[], RegoValue> builtin =
        BuiltinRegistry.AllBuiltIns.get(name);
    assertNotNull(builtin, name + " should be registered");
    return builtin.apply(CONTEXT, args);
  }

  private static RegoSet setOf(RegoValue... values) {
    RegoSet set = new RegoSet(false);
    for (RegoValue value : values) {
      set.addValue(value);
    }
    return set;
  }
}
