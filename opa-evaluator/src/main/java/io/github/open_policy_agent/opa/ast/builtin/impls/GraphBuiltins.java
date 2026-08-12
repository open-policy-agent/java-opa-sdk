package io.github.open_policy_agent.opa.ast.builtin.impls;

import static io.github.open_policy_agent.opa.ast.builtin.impls.utils.ArgHelper.getArg;

import io.github.open_policy_agent.opa.ast.builtin.OpaBuiltin;
import io.github.open_policy_agent.opa.ast.builtin.OpaDynamic;
import io.github.open_policy_agent.opa.ast.builtin.OpaType;
import io.github.open_policy_agent.opa.ast.builtin.OpaVal;
import io.github.open_policy_agent.opa.ast.types.RegoArray;
import io.github.open_policy_agent.opa.ast.types.RegoCollection;
import io.github.open_policy_agent.opa.ast.types.RegoObject;
import io.github.open_policy_agent.opa.ast.types.RegoSet;
import io.github.open_policy_agent.opa.ast.types.RegoValue;
import io.github.open_policy_agent.opa.rego.EvaluationContext;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;

public class GraphBuiltins {

  public static Map<String, BiFunction<EvaluationContext, RegoValue[], RegoValue>> builtins() {
    GraphBuiltins instance = new GraphBuiltins();
    return Map.of(
        "graph.reachable", instance::reachable,
        "graph.reachable_paths", instance::reachablePaths);
  }

  @OpaBuiltin(
      name = "graph.reachable",
      description =
          "Computes the set of reachable nodes in the graph from a set of starting nodes.",
      categories = {"graphs"},
      args = {
        @OpaType(
            type = "object",
            name = "graph",
            description = "object containing a set or array of neighboring vertices",
            dynamic = @OpaDynamic(keyType = "any", valueType = "any")),
        @OpaType(
            name = "initial",
            description = "set or array of root vertices",
            of = {@OpaVal("set"), @OpaVal("array")})
      },
      result =
          @OpaType(
              type = "set",
              name = "output",
              description = "vertices reachable from the initial vertices in the directed graph",
              dynamic = @OpaDynamic(type = "any")))
  public RegoSet reachable(EvaluationContext ctx, RegoValue[] args) {
    RegoObject graph = getArg(args, 0, RegoObject.class);
    RegoCollection initial = getArg(args, 1, RegoCollection.class);
    Deque<RegoValue> queue = new ArrayDeque<>(initial.valueStream().toList());
    RegoSet reached = new RegoSet(false);

    while (!queue.isEmpty()) {
      RegoValue node = queue.removeFirst();
      RegoValue edges = graph.getProperty(node);
      if (edges == null) {
        continue;
      }
      for (RegoValue neighbor : collectionValues(edges)) {
        if (!reached.contains(neighbor)) {
          queue.addLast(neighbor);
        }
      }
      reached.addValue(node);
    }

    return new RegoSet(ctx.sortSets, reached.getValue());
  }

  @OpaBuiltin(
      name = "graph.reachable_paths",
      description =
          "Computes the set of reachable paths in the graph from a set of starting nodes.",
      categories = {"graphs"},
      args = {
        @OpaType(
            type = "object",
            name = "graph",
            description = "object containing a set or array of neighboring vertices",
            dynamic = @OpaDynamic(keyType = "any", valueType = "any")),
        @OpaType(
            name = "initial",
            description = "set or array of root vertices",
            of = {@OpaVal("set"), @OpaVal("array")})
      },
      result =
          @OpaType(
              type = "set",
              name = "output",
              description = "paths reachable from the initial vertices in the directed graph",
              dynamic = @OpaDynamic(type = "array")))
  public RegoSet reachablePaths(EvaluationContext ctx, RegoValue[] args) {
    RegoObject graph = getArg(args, 0, RegoObject.class);
    RegoCollection initial = getArg(args, 1, RegoCollection.class);
    RegoSet paths = new RegoSet(false);

    initial
        .valueStream()
        .forEach(
            node -> {
              RegoValue edges = graph.getProperty(node);
              if (edges == null) {
                return;
              }
              List<RegoValue> neighbors = collectionValues(edges);
              if (neighbors.isEmpty()) {
                paths.addValue(new RegoArray(List.of(node)));
                return;
              }
              for (RegoValue neighbor : neighbors) {
                buildPaths(
                    graph,
                    neighbor,
                    new ArrayList<>(List.of(node)),
                    paths,
                    new HashSet<>(Set.of(node)));
              }
            });

    if (!ctx.sortSets) {
      return paths;
    }
    List<RegoValue> sortedPaths = new ArrayList<>(paths.getValue());
    sortedPaths.sort(RegoValue::compareTo);
    return new RegoSet(false, new LinkedHashSet<>(sortedPaths));
  }

  private static void buildPaths(
      RegoObject graph,
      RegoValue root,
      List<RegoValue> path,
      RegoSet paths,
      Set<RegoValue> reached) {
    RegoValue edges = graph.getProperty(root);
    if (edges == null) {
      paths.addValue(new RegoArray(path));
      return;
    }

    path.add(root);
    List<RegoValue> neighbors = collectionValues(edges);
    if (neighbors.isEmpty()) {
      paths.addValue(new RegoArray(path));
      return;
    }

    Set<RegoValue> nextReached = new HashSet<>(reached);
    nextReached.add(root);
    for (RegoValue neighbor : neighbors) {
      if (nextReached.contains(neighbor)) {
        paths.addValue(new RegoArray(path));
      } else {
        buildPaths(
            graph,
            neighbor,
            new ArrayList<>(path),
            paths,
            new HashSet<>(nextReached));
      }
    }
  }

  private static List<RegoValue> collectionValues(RegoValue value) {
    if (value instanceof RegoCollection) {
      return ((RegoCollection) value).valueStream().toList();
    }
    return List.of();
  }
}
