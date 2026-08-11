package io.github.open_policy_agent.opa.ast.builtin.impls;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.open_policy_agent.opa.ast.builtin.BuiltinRegistry;
import io.github.open_policy_agent.opa.ast.types.RegoBigInt;
import io.github.open_policy_agent.opa.ast.types.RegoInt32;
import io.github.open_policy_agent.opa.ast.types.RegoObject;
import io.github.open_policy_agent.opa.ast.types.RegoString;
import io.github.open_policy_agent.opa.ast.types.RegoValue;
import io.github.open_policy_agent.opa.rego.EvaluationContext;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UUIDBuiltinsTest {

  private final UUIDBuiltins builtins = new UUIDBuiltins();

  @Test
  void registersUuidBuiltins() {
    assertTrue(BuiltinRegistry.AllBuiltIns.containsKey("uuid.parse"));
    assertTrue(BuiltinRegistry.AllBuiltIns.containsKey("uuid.rfc4122"));
  }

  @Test
  void parsesVersion4Uuid() {
    RegoObject result =
        (RegoObject)
            builtins.parse(
                null, new RegoValue[] {new RegoString("00000000-0000-4000-8000-000000000000")});

    assertEquals(new RegoString("RFC4122"), result.getProperty("variant"));
    assertEquals(RegoInt32.of(4), result.getProperty("version"));
  }

  @Test
  void parsesVersion2UuidMetadata() {
    RegoObject result =
        (RegoObject)
            builtins.parse(
                null, new RegoValue[] {new RegoString("000003e8-48b9-21ee-b200-325096b39f47")});

    assertEquals(RegoInt32.of(12800), result.getProperty("clocksequence"));
    assertEquals(new RegoString("Person"), result.getProperty("domain"));
    assertEquals(new RegoBigInt(1000L), result.getProperty("id"));
    assertEquals(new RegoString("local:unicast"), result.getProperty("macvariables"));
    assertEquals(new RegoString("32-50-96-b3-9f-47"), result.getProperty("nodeid"));
    assertEquals(new RegoBigInt(1693566990121469600L), result.getProperty("time"));
    assertEquals(new RegoString("RFC4122"), result.getProperty("variant"));
    assertEquals(RegoInt32.of(2), result.getProperty("version"));
  }

  @Test
  void parsesAcceptedInputFormats() {
    assertEquals(
        RegoInt32.of(4),
        ((RegoObject)
                builtins.parse(
                    null,
                    new RegoValue[] {new RegoString("{00000000-0000-4000-8000-000000000000}")}))
            .getProperty("version"));
    assertEquals(
        RegoInt32.of(2),
        ((RegoObject)
                builtins.parse(
                    null,
                    new RegoValue[] {
                      new RegoString("urn:uuid:000003e8-48b9-21ee-b200-325096b39f47")
                    }))
            .getProperty("version"));
    assertEquals(
        RegoInt32.of(3),
        ((RegoObject)
                builtins.parse(
                    null, new RegoValue[] {new RegoString("38074da40b00388d9c3c362de965547a")}))
            .getProperty("version"));
  }

  @Test
  void parseReturnsUndefinedForInvalidUuid() {
    assertNull(builtins.parse(null, new RegoValue[] {new RegoString("123")}));
  }

  /**
   * google/uuid compares the urn prefix with {@code strings.EqualFold}, so any casing parses. Not
   * covered by the upstream uuid fixtures; verified with {@code opa eval} against OPA 1.19.0.
   */
  @Test
  void parsesUrnPrefixCaseInsensitively() {
    for (String prefix : new String[] {"urn:uuid:", "URN:UUID:", "Urn:Uuid:"}) {
      RegoObject result =
          (RegoObject)
              builtins.parse(
                  null,
                  new RegoValue[] {new RegoString(prefix + "000003e8-48b9-21ee-b200-325096b39f47")});

      assertEquals(RegoInt32.of(2), result.getProperty("version"), prefix);
      assertEquals(new RegoString("RFC4122"), result.getProperty("variant"), prefix);
      assertEquals(new RegoBigInt(1000L), result.getProperty("id"), prefix);
    }
  }

  /**
   * google/uuid only examines the middle 36 bytes of the 38-byte "Microsoft style" form, so the
   * surrounding bytes are not required to be braces — {@code opa eval} accepts all of these.
   */
  @Test
  void parsesThirtyEightByteFormWithoutRequiringBraces() {
    for (String input :
        new String[] {
          "{000003e8-48b9-21ee-b200-325096b39f47}",
          "(000003e8-48b9-21ee-b200-325096b39f47)",
          "X000003e8-48b9-21ee-b200-325096b39f47Y",
          "{000003e8-48b9-21ee-b200-325096b39f47X"
        }) {
      RegoObject result = (RegoObject) builtins.parse(null, new RegoValue[] {new RegoString(input)});

      assertEquals(RegoInt32.of(2), result.getProperty("version"), input);
    }
  }

  @Test
  void parseRejectsLenientJavaUuidFieldWidths() {
    assertNull(builtins.parse(null, new RegoValue[] {new RegoString("1-1-1-1-1")}));
  }

  @Test
  void parsesUnsignedVersion2Id() {
    RegoObject result =
        (RegoObject)
            builtins.parse(
                null, new RegoValue[] {new RegoString("ffffffff-48b9-21ee-b200-325096b39f47")});

    assertEquals(new RegoBigInt(4294967295L), result.getProperty("id"));
  }

  @Test
  void parsesUnknownVersion2Domain() {
    RegoObject result =
        (RegoObject)
            builtins.parse(
                null, new RegoValue[] {new RegoString("000003e8-48b9-21ee-b203-325096b39f47")});

    assertEquals(new RegoString("Domain3"), result.getProperty("domain"));
  }

  @Test
  void rfc4122ReturnsConsistentVersion4UuidForSameKeyDuringEvaluation() {
    EvaluationContext ctx = new EvaluationContext.Builder().build();
    RegoString first = builtins.rfc4122(ctx, new RegoValue[] {new RegoString("key")});
    RegoString second = builtins.rfc4122(ctx, new RegoValue[] {new RegoString("key")});
    UUID parsed = UUID.fromString(first.getValue());

    assertEquals(first, second);
    assertEquals(4, parsed.version());
    assertEquals(2, parsed.variant());
  }

  @Test
  void rfc4122ReturnsFreshValueForSameKeyInDifferentEvaluation() {
    RegoString first =
        builtins.rfc4122(
            new EvaluationContext.Builder().build(), new RegoValue[] {new RegoString("key")});
    RegoString second =
        builtins.rfc4122(
            new EvaluationContext.Builder().build(), new RegoValue[] {new RegoString("key")});

    assertNotEquals(first, second);
  }
}
