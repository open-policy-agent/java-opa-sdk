package io.github.open_policy_agent.opa.ast.builtin.impls;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.open_policy_agent.opa.ast.types.RegoArray;
import io.github.open_policy_agent.opa.ast.types.RegoBigInt;
import io.github.open_policy_agent.opa.ast.types.RegoBoolean;
import io.github.open_policy_agent.opa.ast.types.RegoDecimal;
import io.github.open_policy_agent.opa.ast.types.RegoInt32;
import io.github.open_policy_agent.opa.ast.types.RegoObject;
import io.github.open_policy_agent.opa.ast.types.RegoSet;
import io.github.open_policy_agent.opa.ast.types.RegoString;
import io.github.open_policy_agent.opa.ast.types.RegoValue;
import io.github.open_policy_agent.opa.rego.EvaluationContext;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

public class AggregateBuiltinsTest {

  private final AggregateBuiltins aggregateBuiltins = new AggregateBuiltins();
  private final EvaluationContext ctx = new EvaluationContext.Builder().build();

  @Test
  public void testMember2Set() {
    RegoSet set = new RegoSet(false);
    set.addValue(RegoInt32.of(1));
    set.addValue(new RegoBigInt(1L << 40));
    set.addValue(new RegoDecimal(16777217.0));
    set.addValue(new RegoString("2"));
    set.addValue(new RegoArray(List.of(RegoInt32.of(1), RegoInt32.of(2))));
    set.addValue(new RegoObject(Map.of(new RegoString("a"), RegoInt32.of(1))));
    set.addValue(new RegoSet(false, Set.of(RegoInt32.of(1), RegoInt32.of(2))));

    RegoValue[] members = {
      RegoInt32.of(1),
      new RegoDecimal(1.0),
      new RegoBigInt(1L),
      new RegoDecimal((double) (1L << 40)),
      // Above 2^24, where a float comparison would round the int.
      RegoInt32.of(16777217),
      new RegoArray(List.of(RegoInt32.of(1), RegoInt32.of(2))),
      new RegoObject(Map.of(new RegoString("a"), RegoInt32.of(1))),
      new RegoSet(false, Set.of(RegoInt32.of(1), RegoInt32.of(2))),
    };
    for (RegoValue x : members) {
      assertEquals(RegoBoolean.TRUE, aggregateBuiltins.member2(ctx, new RegoValue[] {x, set}), x.toString());
    }

    // OPA: `2 in {"2"}` is false.
    RegoValue[] nonMembers = {
      RegoInt32.of(2),
      new RegoDecimal(1.5),
      RegoInt32.of(16777216),
      new RegoArray(List.of(RegoInt32.of(2))),
      new RegoSet(false, Set.of(RegoInt32.of(2))),
    };
    for (RegoValue x : nonMembers) {
      assertEquals(RegoBoolean.FALSE, aggregateBuiltins.member2(ctx, new RegoValue[] {x, set}), x.toString());
    }
  }

  @Test
  public void testMember2Array() {
    RegoArray arr = new RegoArray(List.of(new RegoString("a"), new RegoString("b")));

    assertEquals(RegoBoolean.TRUE, aggregateBuiltins.member2(ctx, new RegoValue[] {new RegoString("a"), arr}));
    assertEquals(RegoBoolean.FALSE, aggregateBuiltins.member2(ctx, new RegoValue[] {new RegoString("c"), arr}));
  }

  @Test
  public void testMember2Object() {
    RegoObject obj = new RegoObject(Map.of(new RegoString("foo"), RegoInt32.of(1)));

    assertEquals(RegoBoolean.TRUE, aggregateBuiltins.member2(ctx, new RegoValue[] {RegoInt32.of(1), obj}));
    assertEquals(RegoBoolean.FALSE, aggregateBuiltins.member2(ctx, new RegoValue[] {new RegoString("foo"), obj}));
  }

  @Test
  public void testMember2NonCollection() {
    assertEquals(RegoBoolean.FALSE, aggregateBuiltins.member2(ctx, new RegoValue[] {RegoInt32.of(1), new RegoString("foo")}));
  }

  @Test
  public void testMember3Object() {
    RegoObject obj = new RegoObject(Map.of(new RegoString("foo"), RegoInt32.of(1)));

    assertEquals(RegoBoolean.TRUE, aggregateBuiltins.member3(ctx, new RegoValue[] {new RegoString("foo"), RegoInt32.of(1), obj}));
    assertEquals(RegoBoolean.FALSE, aggregateBuiltins.member3(ctx, new RegoValue[] {new RegoString("foo"), RegoInt32.of(2), obj}));
    assertEquals(RegoBoolean.FALSE, aggregateBuiltins.member3(ctx, new RegoValue[] {new RegoString("bar"), RegoInt32.of(1), obj}));
  }

  @Test
  public void testMember3Array() {
    RegoArray arr = new RegoArray(List.of(new RegoString("one"), new RegoString("two")));

    assertEquals(RegoBoolean.TRUE, aggregateBuiltins.member3(ctx, new RegoValue[] {RegoInt32.of(1), new RegoString("two"), arr}));
    assertEquals(RegoBoolean.FALSE, aggregateBuiltins.member3(ctx, new RegoValue[] {RegoInt32.of(0), new RegoString("two"), arr}));
    assertEquals(RegoBoolean.FALSE, aggregateBuiltins.member3(ctx, new RegoValue[] {RegoInt32.of(5), new RegoString("two"), arr}));
    assertEquals(RegoBoolean.FALSE, aggregateBuiltins.member3(ctx, new RegoValue[] {new RegoString("1"), new RegoString("two"), arr}));
  }

  @Test
  public void testMember3Set() {
    RegoSet set = new RegoSet(false);
    set.addValue(new RegoString("x"));

    assertEquals(RegoBoolean.TRUE, aggregateBuiltins.member3(ctx, new RegoValue[] {new RegoString("x"), new RegoString("x"), set}));
    assertEquals(RegoBoolean.FALSE, aggregateBuiltins.member3(ctx, new RegoValue[] {new RegoString("x"), new RegoString("y"), set}));
  }

  @Test
  public void testMember3NonCollection() {
    assertEquals(RegoBoolean.FALSE, aggregateBuiltins.member3(ctx, new RegoValue[] {RegoInt32.of(1), new RegoString("a"), new RegoString("bar")}));
  }
}
