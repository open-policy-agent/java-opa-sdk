package io.github.open_policy_agent.opa.ast.builtin.impls;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import io.github.open_policy_agent.opa.ast.types.RegoArray;
import io.github.open_policy_agent.opa.ast.types.RegoInt32;
import io.github.open_policy_agent.opa.ast.types.RegoObject;
import io.github.open_policy_agent.opa.ast.types.RegoSet;
import io.github.open_policy_agent.opa.ast.types.RegoString;
import io.github.open_policy_agent.opa.ast.types.RegoUndefined;
import io.github.open_policy_agent.opa.ast.types.RegoValue;
import io.github.open_policy_agent.opa.rego.EvaluationContext;
import java.util.LinkedHashSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class JsonPatchSetTest {

  private final JsonBuiltins builtins = new JsonBuiltins();
  private final EvaluationContext ctx = new EvaluationContext.Builder().build();

  private static RegoSet set(RegoValue... members) {
    return new RegoSet(false, new LinkedHashSet<>(List.of(members)));
  }

  /** Builds a patch operation from alternating key/value pairs. */
  private static RegoObject op(String... kv) {
    if (kv.length % 2 != 0) {
      throw new IllegalArgumentException("op() takes key/value pairs, got " + kv.length + " args");
    }
    RegoObject o = new RegoObject();
    for (int i = 0; i + 1 < kv.length; i += 2) {
      o.setProp(new RegoString(kv[i]), new RegoString(kv[i + 1]));
    }
    return o;
  }

  @Test
  @Timeout(10)
  void addsMemberToSet() {
    RegoObject doc = new RegoObject();
    doc.setProp(new RegoString("foo"), set(new RegoString("a"), new RegoString("b")));

    RegoValue r =
        builtins.patch(
            ctx,
            new RegoValue[] {
              doc, new RegoArray(List.of(op("op", "add", "path", "foo/c", "value", "c")))
            });

    RegoObject expected = new RegoObject();
    expected.setProp(
        new RegoString("foo"), set(new RegoString("a"), new RegoString("b"), new RegoString("c")));
    assertEquals(expected, r);
  }

  @Test
  @Timeout(10)
  void removesMemberFromSet() {
    RegoObject doc = new RegoObject();
    doc.setProp(
        new RegoString("foo"), set(new RegoString("a"), new RegoString("b"), new RegoString("c")));

    RegoValue r =
        builtins.patch(
            ctx,
            new RegoValue[] {doc, new RegoArray(List.of(op("op", "remove", "path", "foo/b")))});

    RegoObject expected = new RegoObject();
    expected.setProp(new RegoString("foo"), set(new RegoString("a"), new RegoString("c")));
    assertEquals(expected, r);
  }

  // In a set the path segment *is* the member, so adding "e" at ".../d" is incoherent.
  @Test
  @Timeout(10)
  void addWithMismatchedMemberIsUndefined() {
    RegoObject doc = new RegoObject();
    doc.setProp(new RegoString("foo"), set(new RegoString("a"), new RegoString("b")));

    RegoValue r =
        builtins.patch(
            ctx,
            new RegoValue[] {
              doc, new RegoArray(List.of(op("op", "add", "path", "foo/d", "value", "e")))
            });

    // RegoUndefined.equals() always returns false, so compare by identity.
    assertSame(RegoUndefined.INSTANCE, r);
  }

  // A digit-only segment that overflows an int is not a usable index. It used to reach
  // Integer.parseInt and throw NumberFormatException out of the builtin instead of going undefined.
  @Test
  @Timeout(10)
  void oversizedNumericIndexIsUndefined() {
    RegoObject doc = new RegoObject();
    doc.setProp(new RegoString("members"), set(new RegoString("a")));
    doc.setProp(new RegoString("list"), new RegoArray(List.of(RegoInt32.of(1))));

    RegoValue r =
        builtins.patch(
            ctx,
            new RegoValue[] {
              doc,
              new RegoArray(
                  List.of(op("op", "remove", "path", "list/99999999999999999999")))
            });

    assertSame(RegoUndefined.INSTANCE, r);
  }

  @Test
  @Timeout(10)
  void addsToArrayNestedInSet() {
    // doc := {[1]} — a set whose single member is the array [1]
    RegoObject wrapper = new RegoObject();
    wrapper.setProp(new RegoString("x"), set(new RegoArray(List.of(RegoInt32.of(1)))));

    RegoObject patchOp = new RegoObject();
    patchOp.setProp(new RegoString("op"), new RegoString("add"));
    patchOp.setProp(
        new RegoString("path"),
        new RegoArray(List.of(new RegoArray(List.of(RegoInt32.of(1))), RegoInt32.of(1))));
    patchOp.setProp(new RegoString("value"), RegoInt32.of(2));

    RegoValue r =
        builtins.patch(
            ctx, new RegoValue[] {wrapper.getProperty("x"), new RegoArray(List.of(patchOp))});

    assertEquals(set(new RegoArray(List.of(RegoInt32.of(1), RegoInt32.of(2)))), r);
  }
}
