package io.github.open_policy_agent.opa.ast.builtin.impls;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.open_policy_agent.opa.ast.builtin.BuiltinError;
import io.github.open_policy_agent.opa.ast.types.RegoArray;
import io.github.open_policy_agent.opa.ast.types.RegoBoolean;
import io.github.open_policy_agent.opa.ast.types.RegoInt32;
import io.github.open_policy_agent.opa.ast.types.RegoSet;
import io.github.open_policy_agent.opa.ast.types.RegoString;
import io.github.open_policy_agent.opa.ast.types.RegoValue;
import io.github.open_policy_agent.opa.rego.EvaluationContext;
import java.util.function.BiFunction;
import org.junit.jupiter.api.Test;

/** Empty and malformed inputs where OPA gives undefined, an error or false. */
public class EmptyInputBuiltinsTest {

  private final EvaluationContext ctx = new EvaluationContext.Builder().build();
  private final AggregateBuiltins aggregates = new AggregateBuiltins();

  private RegoValue call(BiFunction<EvaluationContext, RegoValue[], RegoValue> fn, RegoValue... args) {
    return fn.apply(ctx, args);
  }

  // A null result is how a builtin says "undefined".
  @Test
  public void maxAndMinOfEmptyCollectionsAreUndefined() {
    assertNull(aggregates.max(ctx, new RegoValue[] {new RegoArray()}));
    assertNull(aggregates.max(ctx, new RegoValue[] {new RegoSet(false)}));
    assertNull(aggregates.min(ctx, new RegoValue[] {new RegoArray()}));
    assertNull(aggregates.min(ctx, new RegoValue[] {new RegoSet(false)}));
  }

  @Test
  public void indexofRejectsEmptySearchString() {
    var indexof = StringBuiltins.builtins().get("indexof");

    assertThrows(BuiltinError.class, () -> call(indexof, new RegoString("abc"), new RegoString("")));
    assertThrows(BuiltinError.class, () -> call(indexof, new RegoString(""), new RegoString("")));
    assertEquals(RegoInt32.of(1), call(indexof, new RegoString("abc"), new RegoString("b")));
  }

  // The empty search string used to make indexof_n loop forever.
  @Test
  public void indexofNRejectsEmptySearchString() {
    var indexofN = StringBuiltins.builtins().get("indexof_n");

    assertThrows(BuiltinError.class, () -> call(indexofN, new RegoString("abc"), new RegoString("")));
    assertThrows(BuiltinError.class, () -> call(indexofN, new RegoString(""), new RegoString("")));
  }

  @Test
  public void base64IsValidRequiresPadding() {
    var isValid = EncodingBuiltins.builtins().get("base64.is_valid");

    assertEquals(RegoBoolean.FALSE, call(isValid, new RegoString("abc")));
    assertEquals(RegoBoolean.FALSE, call(isValid, new RegoString("YQ=")));
    assertEquals(RegoBoolean.TRUE, call(isValid, new RegoString("YWJj")));
    assertEquals(RegoBoolean.TRUE, call(isValid, new RegoString("YQ==")));
  }
}
