package io.github.open_policy_agent.opa.ast.builtin.impls;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.open_policy_agent.opa.ast.types.RegoArray;
import io.github.open_policy_agent.opa.ast.types.RegoBoolean;
import io.github.open_policy_agent.opa.ast.types.RegoInt32;
import io.github.open_policy_agent.opa.ast.types.RegoNull;
import io.github.open_policy_agent.opa.ast.types.RegoObject;
import io.github.open_policy_agent.opa.ast.types.RegoSet;
import io.github.open_policy_agent.opa.ast.types.RegoString;
import io.github.open_policy_agent.opa.ast.types.RegoValue;
import io.github.open_policy_agent.opa.rego.EvaluationContext;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Values of different types are ordered null < boolean < number < string < array < object < set. */
public class CrossTypeComparisonTest {

  private final EvaluationContext ctx = new EvaluationContext.Builder().build();

  private RegoValue compare(String op, RegoValue a, RegoValue b) {
    return ComparisonBuiltins.builtins().get(op).apply(ctx, new RegoValue[] {a, b});
  }

  @Test
  public void anArrayIsGreaterThanANumber() {
    RegoValue array = new RegoArray();
    RegoValue zero = RegoInt32.of(0);

    assertEquals(RegoBoolean.TRUE, compare("gt", array, zero));
    assertEquals(RegoBoolean.TRUE, compare("gte", array, zero));
    assertEquals(RegoBoolean.FALSE, compare("lt", array, zero));
    assertEquals(RegoBoolean.FALSE, compare("lte", array, zero));
    assertEquals(RegoBoolean.FALSE, compare("equal", array, zero));
  }

  @Test
  public void sortOrdersMixedTypes() {
    RegoValue[] unordered = {
      new RegoSet(false),
      new RegoObject(),
      new RegoArray(),
      new RegoString("a"),
      RegoInt32.of(1),
      RegoBoolean.TRUE,
      RegoNull.INSTANCE,
      RegoBoolean.FALSE
    };
    RegoValue[] ordered = {
      RegoNull.INSTANCE,
      RegoBoolean.FALSE,
      RegoBoolean.TRUE,
      RegoInt32.of(1),
      new RegoString("a"),
      new RegoArray(),
      new RegoObject(),
      new RegoSet(false)
    };

    RegoValue sorted =
        new AggregateBuiltins().sort(ctx, new RegoValue[] {new RegoArray(List.of(unordered))});

    assertEquals(new RegoArray(List.of(ordered)), sorted);
  }
}
