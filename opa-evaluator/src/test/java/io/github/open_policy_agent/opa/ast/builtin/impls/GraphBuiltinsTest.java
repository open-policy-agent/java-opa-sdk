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
