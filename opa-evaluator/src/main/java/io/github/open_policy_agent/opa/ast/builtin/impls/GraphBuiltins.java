package io.github.open_policy_agent.opa.ast.builtin.impls;

import static io.github.open_policy_agent.opa.ast.builtin.impls.utils.ArgHelper.getArg;

import io.github.open_policy_agent.opa.ast.builtin.OpaBuiltin;
import io.github.open_policy_agent.opa.ast.builtin.OpaDynamic;
import io.github.open_policy_agent.opa.ast.builtin.OpaType;
import io.github.open_policy_agent.opa.ast.builtin.OpaVal;
import io.github.open_policy_agent.opa.ast.types.RegoArray;
import io.github.open_policy_agent.opa.ast.types.RegoCollection;
import io.github.open_policy_agent.opa.ast.types.RegoInt32;
import io.github.open_policy_agent.opa.ast.types.RegoObject;
import io.github.open_policy_agent.opa.ast.types.RegoSet;
import io.github.open_policy_agent.opa.ast.types.RegoValue;
import io.github.open_policy_agent.opa.rego.EvaluationContext;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

public class GraphBuiltins {

  public static Map<String, BiFunction<EvaluationContext, RegoValue[], RegoValue>> builtins() {
    GraphBuiltins instance = new GraphBuiltins();
    return Map.of(
        "graph.reachable", instance::reachable,
        "graph.reachable_paths", instance::reachablePaths,
        "walk", instance::walk);
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

    for (RegoValue node : initial.valueStream().toList()) {
      collectPaths(graph, node, paths);
    }

    if (!ctx.sortSets) {
      return paths;
    }
    List<RegoValue> sortedPaths = new ArrayList<>(paths.getValue());
    sortedPaths.sort(RegoValue::compareTo);
    return new RegoSet(false, new LinkedHashSet<>(sortedPaths));
  }

  private static void collectPaths(RegoObject graph, RegoValue initial, RegoSet paths) {
    RegoValue edges = graph.getProperty(initial);
    if (edges == null) {
      return;
    }

    List<RegoValue> neighbors = collectionValues(edges);
    if (neighbors.isEmpty()) {
      addPath(paths, List.of(initial));
      return;
    }

    List<RegoValue> path = new ArrayList<>(List.of(initial));
    Map<RegoValue, Integer> reached = new HashMap<>();
    reached.put(initial, 1);
    Deque<PathFrame> pending = new ArrayDeque<>();
    pending.addLast(new PathFrame(null, neighbors));

    while (!pending.isEmpty()) {
      PathFrame frame = pending.peekLast();
      if (frame.nextNeighborIndex == frame.neighbors.size()) {
        pending.removeLast();
        if (frame.node != null) {
          path.remove(path.size() - 1);
          reached.compute(
              frame.node,
              (node, visits) -> visits == null || visits == 1 ? null : visits - 1);
        }
        continue;
      }

      RegoValue neighbor = frame.neighbors.get(frame.nextNeighborIndex++);
      // OPA includes a direct self-loop from the initial node, but stops cycles after
      // entering a child frame. Keep a visit count so unwinding that self-loop does not
      // remove the initial node from the active path.
      if (frame.node != null && reached.containsKey(neighbor)) {
        addPath(paths, path);
        continue;
      }

      RegoValue neighborEdges = graph.getProperty(neighbor);
      if (neighborEdges == null) {
        addPath(paths, path);
        continue;
      }

      path.add(neighbor);
      reached.merge(neighbor, 1, Integer::sum);
      List<RegoValue> neighborValues = collectionValues(neighborEdges);
      if (neighborValues.isEmpty()) {
        addPath(paths, path);
        path.remove(path.size() - 1);
        reached.compute(
            neighbor, (node, visits) -> visits == null || visits == 1 ? null : visits - 1);
        continue;
      }
      pending.addLast(new PathFrame(neighbor, neighborValues));
    }
  }

  private static void addPath(RegoSet paths, List<RegoValue> path) {
    paths.addValue(new RegoArray(new ArrayList<>(path)));
  }

  private static final class PathFrame {
    private final RegoValue node;
    private final List<RegoValue> neighbors;
    private int nextNeighborIndex;

    private PathFrame(RegoValue node, List<RegoValue> neighbors) {
      this.node = node;
      this.neighbors = neighbors;
    }
  }

  private static List<RegoValue> collectionValues(RegoValue value) {
    if (value instanceof RegoCollection) {
      return ((RegoCollection) value).valueStream().toList();
    }
    return List.of();
  }

  /**
   * {@code walk} is the one builtin Go-OPA flags as relational ({@code Relation: true}): it yields
   * many {@code [path, value]} tuples through an iterator instead of returning a single value. The
   * IR planner turns it into an ordinary call whose result is scanned -- the plan emits a CallStmt
   * for {@code walk} followed by a ScanStmt over the returned collection -- so what is returned
   * here is the complete set of pairs rather than one tuple.
   */
  @OpaBuiltin(
      name = "walk",
      description =
          "Generates `[path, value]` tuples for all nested documents of `x` (recursively).  Queries"
              + " can use `walk` to traverse documents nested under `x`.",
      categories = {"graph"},
      args = {@OpaType(type = "any", name = "x", description = "value to walk")},
      result =
          @OpaType(
              type = "array",
              name = "output",
              dynamic = @OpaDynamic(type = "any"),
              description =
                  "pairs of `path` and `value`: `path` is an array representing the pointer to"
                      + " `value` in `x`. If `path` is assigned a wildcard (`_`), the `walk`"
                      + " function will skip path creation entirely for faster evaluation."))
  public RegoSet walk(EvaluationContext ctx, RegoValue[] args) {
    RegoSet pairs = new RegoSet(ctx.sortSets);
    // An undefined argument produces no tuples, matching the undefined expression in Go-OPA.
    if (args == null || args.length == 0 || args[0] == null) {
      return pairs;
    }
    collect(new ArrayDeque<>(), args[0], pairs);
    return pairs;
  }

  /**
   * Emits the {@code [path, value]} pair for {@code value} and then recurses into its members. The
   * root is always emitted with an empty path, including for scalars, which have no members.
   */
  private static void collect(Deque<RegoValue> path, RegoValue value, RegoSet pairs) {
    RegoArray pair = new RegoArray(2);
    // Snapshot the path: the deque is mutated as the walk unwinds, so sharing it would leave every
    // pair pointing at the same (final) path.
    pair.addValue(new RegoArray(new ArrayList<>(path)));
    pair.addValue(value);
    pairs.addValue(pair);

    if (value instanceof RegoObject) {
      for (Map.Entry<RegoValue, RegoValue> entry :
          ((RegoObject) value).getProperties().entrySet()) {
        path.addLast(entry.getKey());
        collect(path, entry.getValue(), pairs);
        path.removeLast();
      }
    } else if (value instanceof RegoArray) {
      List<RegoValue> values = ((RegoArray) value).getValue();
      for (int i = 0; i < values.size(); i++) {
        path.addLast(RegoInt32.of(i));
        collect(path, values.get(i), pairs);
        path.removeLast();
      }
    } else if (value instanceof RegoSet) {
      // Sets are keyed by their own members, so a member's path segment is the member itself.
      for (RegoValue member : ((RegoSet) value).getValue()) {
        path.addLast(member);
        collect(path, member, pairs);
        path.removeLast();
      }
    }
  }
}
