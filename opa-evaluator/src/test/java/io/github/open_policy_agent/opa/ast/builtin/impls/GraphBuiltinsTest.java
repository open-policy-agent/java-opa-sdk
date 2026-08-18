package io.github.open_policy_agent.opa.ast.builtin.impls;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.github.open_policy_agent.opa.ast.builtin.BuiltinRegistry;
import io.github.open_policy_agent.opa.ast.types.RegoArray;
import io.github.open_policy_agent.opa.ast.types.RegoObject;
import io.github.open_policy_agent.opa.ast.types.RegoSet;
import io.github.open_policy_agent.opa.ast.types.RegoString;
import io.github.open_policy_agent.opa.ast.types.RegoValue;
import io.github.open_policy_agent.opa.rego.EvaluationContext;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
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
