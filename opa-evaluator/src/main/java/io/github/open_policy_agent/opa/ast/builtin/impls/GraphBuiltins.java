package io.github.open_policy_agent.opa.ast.builtin.impls;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import io.github.open_policy_agent.opa.ast.builtin.OpaBuiltin;
import io.github.open_policy_agent.opa.ast.builtin.OpaDynamic;
import io.github.open_policy_agent.opa.ast.builtin.OpaType;
import io.github.open_policy_agent.opa.ast.types.RegoArray;
import io.github.open_policy_agent.opa.ast.types.RegoInt32;
import io.github.open_policy_agent.opa.ast.types.RegoObject;
import io.github.open_policy_agent.opa.ast.types.RegoSet;
import io.github.open_policy_agent.opa.ast.types.RegoValue;
import io.github.open_policy_agent.opa.rego.EvaluationContext;

public class GraphBuiltins {

  public static Map<String, BiFunction<EvaluationContext, RegoValue[], RegoValue>> builtins() {
    GraphBuiltins instance = new GraphBuiltins();
    return Map.of("walk", instance::walk);
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
  private void collect(Deque<RegoValue> path, RegoValue value, RegoSet pairs) {
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
