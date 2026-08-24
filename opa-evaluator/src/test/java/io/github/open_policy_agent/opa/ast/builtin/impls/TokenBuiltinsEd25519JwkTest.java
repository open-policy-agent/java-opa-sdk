package io.github.open_policy_agent.opa.ast.builtin.impls;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import io.github.open_policy_agent.opa.ast.types.RegoBoolean;
import io.github.open_policy_agent.opa.ast.types.RegoString;
import io.github.open_policy_agent.opa.ast.types.RegoValue;
import io.github.open_policy_agent.opa.rego.EvaluationContext;

import org.junit.jupiter.api.Test;

/**
 * Covers the JWK (OKP / Ed25519) path for {@code io.jwt.verify_eddsa}, which is not exercised by
 * compliance fixtures (those only use PEM public keys for verify_eddsa).
 */
public class TokenBuiltinsEd25519JwkTest {

  // From jwtencodesign/test-jwtencodesign-eddsa.json
  private static final String PRIVATE_JWK =
      "{\"kty\":\"OKP\",\"alg\":\"EdDSA\",\"crv\":\"Ed25519\","
          + "\"x\":\"wEZFfoAj1rFKTLOOmjJjVZlCHwksuvMb2I5y_hg70E8\","
          + "\"d\":\"9XI34uQzYUJfWhDZf_0nYsLMBRVu8a6dFsy60P8uugk\"}";

  private static final String PUBLIC_JWK =
      "{\"kty\":\"OKP\",\"crv\":\"Ed25519\","
          + "\"x\":\"wEZFfoAj1rFKTLOOmjJjVZlCHwksuvMb2I5y_hg70E8\"}";

  // Different Ed25519 public key (same length, wrong material)
  private static final String WRONG_PUBLIC_JWK =
      "{\"kty\":\"OKP\",\"crv\":\"Ed25519\","
          + "\"x\":\"11qYAYKxCrfVS_7TyWQHOg7hcvPapiMlrwIaaPcHURo\"}";

  private static final String HEADERS = "{\"alg\":\"EdDSA\",\"typ\":\"JWT\"}";
  private static final String PAYLOAD =
      "{\"iss\":\"joe\",\"exp\":1300819380,\"http://example.com/is_root\":true}";

  private final TokenBuiltins builtins = new TokenBuiltins();
  private final EvaluationContext ctx = new EvaluationContext.Builder().build();

  @Test
  public void verifyEddsaWithMatchingPublicJwkReturnsTrue() {
    RegoString jwt =
        builtins.encodeSignRaw(
            ctx,
            new RegoValue[] {
              new RegoString(HEADERS), new RegoString(PAYLOAD), new RegoString(PRIVATE_JWK)
            });

    RegoBoolean result =
        builtins.verifyEdDSA(
            ctx, new RegoValue[] {jwt, new RegoString(PUBLIC_JWK)});

    assertEquals(RegoBoolean.TRUE, result);
  }

  @Test
  public void verifyEddsaWithMismatchedPublicJwkReturnsFalse() {
    RegoString jwt =
        builtins.encodeSignRaw(
            ctx,
            new RegoValue[] {
              new RegoString(HEADERS), new RegoString(PAYLOAD), new RegoString(PRIVATE_JWK)
            });

    RegoBoolean result =
        builtins.verifyEdDSA(
            ctx, new RegoValue[] {jwt, new RegoString(WRONG_PUBLIC_JWK)});

    assertInstanceOf(RegoBoolean.class, result);
    assertEquals(RegoBoolean.FALSE, result);
  }
}
