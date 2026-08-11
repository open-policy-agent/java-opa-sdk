package io.github.open_policy_agent.opa.ir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.open_policy_agent.opa.ast.builtin.impls.ProvidersAwsBuiltins;
import io.github.open_policy_agent.opa.ast.types.RegoBigInt;
import io.github.open_policy_agent.opa.ast.types.RegoObject;
import io.github.open_policy_agent.opa.ast.types.RegoString;
import io.github.open_policy_agent.opa.ast.types.RegoValue;
import io.github.open_policy_agent.opa.rego.EvaluationContext;
import io.github.open_policy_agent.opa.rego.TypeError;
import java.math.BigInteger;
import java.util.function.BiFunction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Verifies {@code providers.aws.sign_req} rejects unknown HTTP request keys using the same
 * allowlist and error wording as OPA's {@code validateHTTPRequestOperand} ({@code
 * invalid request parameters(s): ...}), and that this check runs before the missing-required-keys
 * check.
 */
@DisplayName("providers.aws invalid request keys")
class ProvidersAwsInvalidRequestKeyTest {

  private static final long SIGNING_TS = 1451311705000000000L;

  @Test
  void unknownKeyRejectedEvenWhenRequiredKeysPresent() {
    RegoObject req = new RegoObject();
    req.setProp(new RegoString("method"), new RegoString("get"));
    req.setProp(new RegoString("url"), new RegoString("http://example.com"));
    req.setProp(new RegoString("bogus_key"), new RegoString("x"));

    TypeError err = assertThrows(TypeError.class, () -> sign(req));
    assertEquals(
        "eval_type_error: operand 1 invalid request parameters(s): {\"bogus_key\"}",
        err.getMessage());
  }

  @Test
  void unknownKeyCheckedBeforeMissingRequiredKeys() {
    // Only an unknown key — no method/url. Must report invalid keys, not missing keys.
    RegoObject req = new RegoObject();
    req.setProp(new RegoString("bogus_key"), new RegoString("x"));

    TypeError err = assertThrows(TypeError.class, () -> sign(req));
    assertEquals(
        "eval_type_error: operand 1 invalid request parameters(s): {\"bogus_key\"}",
        err.getMessage());
    assertTrue(
        !err.getMessage().contains("missing required"),
        "invalid-key check must run before missing-required-keys check");
  }

  @Test
  void missingRequiredKeysStillReportedWhenAllKeysAreAllowed() {
    RegoObject req = new RegoObject();

    TypeError err = assertThrows(TypeError.class, () -> sign(req));
    assertEquals(
        "eval_type_error: operand 1 missing required request parameters(s): {\"method\", \"url\"}",
        err.getMessage());
  }

  private static void sign(RegoObject req) {
    BiFunction<EvaluationContext, RegoValue[], RegoValue> fn =
        new ProvidersAwsBuiltins().builtins().get("providers.aws.sign_req");

    RegoObject config = new RegoObject();
    config.setProp(new RegoString("aws_access_key"), new RegoString("MYAWSACCESSKEYGOESHERE"));
    config.setProp(
        new RegoString("aws_secret_access_key"), new RegoString("MYAWSSECRETACCESSKEYGOESHERE"));
    config.setProp(new RegoString("aws_service"), new RegoString("s3"));
    config.setProp(new RegoString("aws_region"), new RegoString("us-east-1"));

    fn.apply(
        null, new RegoValue[] {req, config, new RegoBigInt(BigInteger.valueOf(SIGNING_TS))});
  }
}
