package io.github.open_policy_agent.opa.ast.builtin.impls;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import io.github.open_policy_agent.opa.ast.builtin.OpaBuiltin;
import io.github.open_policy_agent.opa.ast.builtin.OpaDynamic;
import io.github.open_policy_agent.opa.ast.builtin.OpaType;
import io.github.open_policy_agent.opa.ast.types.RegoSet;
import io.github.open_policy_agent.opa.ast.types.RegoValue;
import io.github.open_policy_agent.opa.rego.EvaluationContext;

import static io.github.open_policy_agent.opa.ast.builtin.impls.utils.ArgHelper.getArg;

public class SetBuiltins {

  public static Map<String, BiFunction<EvaluationContext, RegoValue[], RegoValue>> builtins() {
    SetBuiltins instance = new SetBuiltins();
    return Map.of(
        "and", instance::and,
        "or", instance::or,
        "intersection", instance::intersection,
        "union", instance::union);
  }

  public RegoSet union(EvaluationContext ctx, RegoValue[] args) {
    RegoSet xs = (RegoSet) args[0];

    Set<RegoValue> y =
        xs.getValue().stream()
            .map(s -> (RegoSet) s)
            .map(RegoSet::getValue)
            .flatMap(Set::stream)
            .collect(Collectors.toSet());

    return new RegoSet(ctx.sortSets, y);
  }

  @OpaBuiltin(
      name = "and",
      description = "Returns the intersection of two sets.",
      categories = {"sets"},
      args = {
        @OpaType(
            type = "set",
            name = "x",
            description = "the first set",
            dynamic = @OpaDynamic(type = "any")),
        @OpaType(
            type = "set",
            name = "y",
            description = "the second set",
            dynamic = @OpaDynamic(type = "any"))
      },
      result =
          @OpaType(
              type = "set",
              name = "z",
              description = "the intersection of `x` and `y`",
              dynamic = @OpaDynamic(type = "any")))
  public RegoSet and(EvaluationContext ctx, RegoValue[] args) {

    RegoSet x = getArg(args, 0, RegoSet.class);
    RegoSet y = getArg(args, 1, RegoSet.class);

    HashSet<RegoValue> z = new HashSet<>(x.getValue());
    z.retainAll(y.getValue());

    return new RegoSet(ctx.sortSets, z);
  }

  @OpaBuiltin(
      name = "or",
      description = "Returns the union of two sets.",
      categories = {"sets"},
      args = {
        @OpaType(
            type = "set",
            name = "x",
            description = "the first set",
            dynamic = @OpaDynamic(type = "any")),
        @OpaType(
            type = "set",
            name = "y",
            description = "the second set",
            dynamic = @OpaDynamic(type = "any"))
      },
      result =
          @OpaType(
              type = "set",
              description = "the union of `x` and `y`",
              dynamic = @OpaDynamic(type = "any")))
  public RegoSet or(EvaluationContext ctx, RegoValue[] args) {

    RegoSet x = getArg(args, 0, RegoSet.class);
    RegoSet y = getArg(args, 1, RegoSet.class);

    HashSet<RegoValue> z = new HashSet<>(x.getValue());
    z.addAll(y.getValue());

    return new RegoSet(ctx.sortSets, z);
  }

  @OpaBuiltin(
      name = "intersection",
      description = "Returns the intersection of a set of sets.",
      categories = {"sets"},
      args = {
        @OpaType(
            type = "set",
            name = "xs",
            description = "set of sets to intersect",
            dynamic = @OpaDynamic(type = "set", keyType = "any", valueType = "any"))
      },
      result =
          @OpaType(
              type = "set",
              name = "y",
              description = "the intersection of all `xs` sets",
              dynamic = @OpaDynamic(type = "any")))
  public RegoSet intersection(EvaluationContext ctx, RegoValue[] args) {
    RegoSet xs = getArg(args, 0, RegoSet.class);

    // Get all sets from the input
    List<Set<RegoValue>> sets =
        xs.getValue().stream()
            .map(s -> (RegoSet) s)
            .map(RegoSet::getValue)
            .collect(Collectors.toList());

    // If no sets, return empty set
    if (sets.isEmpty()) {
      return new RegoSet(ctx.sortSets);
    }

    // Start with the first set and intersect with all others
    Set<RegoValue> result = new HashSet<>(sets.get(0));
    for (int i = 1; i < sets.size(); i++) {
      result.retainAll(sets.get(i));
    }

    return new RegoSet(ctx.sortSets, result);
  }
}
