package io.github.open_policy_agent.opa.ast.builtin.impls;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import io.github.open_policy_agent.opa.ast.builtin.BuiltinRegistry;
import io.github.open_policy_agent.opa.ast.types.RegoObject;
import io.github.open_policy_agent.opa.ast.types.RegoString;
import io.github.open_policy_agent.opa.ast.types.RegoValue;
import io.github.open_policy_agent.opa.cache.NdBuiltinCache;
import io.github.open_policy_agent.opa.rego.EvaluationContext;

public class OpaBuiltinsTest {

  private final OpaBuiltins opaBuiltins = new OpaBuiltins();

  @Test
  public void testOpaRuntimeBuiltinRegistered() {
    BuiltinRegistry registry = BuiltinRegistry.allCapabilities();
    assertTrue(registry.hasBuiltIn("opa.runtime"));
  }

  @Test
  public void testOpaRuntimeUnsetReturnsEmptyObject() {
    EvaluationContext ctx = new EvaluationContext.Builder().build();
    RegoValue result = opaBuiltins.runtime(ctx, new RegoValue[0]);

    assertEquals(new RegoObject(), result);
  }

  @Test
  public void testOpaRuntimeReturnsInjectedValue() {
    RegoObject runtime = new RegoObject();
    runtime.setProperty("version", new RegoString("1.0.0"));
    runtime.setProperty("commit", new RegoString("abc123"));

    EvaluationContext ctx = new EvaluationContext.Builder().withRuntime(runtime).build();
    RegoValue result = opaBuiltins.runtime(ctx, new RegoValue[0]);

    assertSame(runtime, result);
  }

  @Test
  public void testOpaRuntimeScrubsServiceCredentialsAndKeys() {
    RegoObject credentials = new RegoObject();
    credentials.setProperty("bearer", new RegoString("secret-token"));

    RegoObject service = new RegoObject();
    service.setProperty("url", new RegoString("https://example.com"));
    service.setProperty("credentials", credentials);

    RegoObject services = new RegoObject();
    services.setProperty("acmecorp", service);

    RegoObject keyEntry = new RegoObject();
    keyEntry.setProperty("algorithm", new RegoString("RS256"));
    keyEntry.setProperty("key", new RegoString("PUBLIC"));
    keyEntry.setProperty("private_key", new RegoString("PRIVATE"));

    RegoObject keys = new RegoObject();
    keys.setProperty("mykey", keyEntry);

    RegoObject config = new RegoObject();
    config.setProperty("services", services);
    config.setProperty("keys", keys);

    RegoObject runtime = new RegoObject();
    runtime.setProperty("version", new RegoString("1.0.0"));
    runtime.setProperty("config", config);

    EvaluationContext ctx = new EvaluationContext.Builder().withRuntime(runtime).build();
    RegoObject result = (RegoObject) opaBuiltins.runtime(ctx, new RegoValue[0]);

    RegoObject scrubbedService =
        (RegoObject)
            ((RegoObject) ((RegoObject) result.getProperty("config")).getProperty("services"))
                .getProperty("acmecorp");
    assertEquals(new RegoString("https://example.com"), scrubbedService.getProperty("url"));
    assertNull(scrubbedService.getProperty("credentials"));

    RegoObject scrubbedKey =
        (RegoObject)
            ((RegoObject) ((RegoObject) result.getProperty("config")).getProperty("keys"))
                .getProperty("mykey");
    assertEquals(new RegoString("RS256"), scrubbedKey.getProperty("algorithm"));
    assertNull(scrubbedKey.getProperty("key"));
    assertNull(scrubbedKey.getProperty("private_key"));

    // Original runtime must not be mutated
    assertEquals(credentials, service.getProperty("credentials"));
    assertEquals(new RegoString("PRIVATE"), keyEntry.getProperty("private_key"));
  }

  @Test
  public void testOpaRuntimeUsesNdBuiltinCache() {
    RegoObject runtime = new RegoObject();
    runtime.setProperty("version", new RegoString("cached"));

    NdBuiltinCache cache = new NdBuiltinCache(10, 60);
    EvaluationContext ctx =
        new EvaluationContext.Builder().withRuntime(runtime).withNdBuiltinCache(cache).build();

    RegoValue first = opaBuiltins.runtime(ctx, new RegoValue[0]);
    RegoValue second = opaBuiltins.runtime(ctx, new RegoValue[0]);

    assertSame(first, second);
    assertEquals(1, ctx.getNdCacheValues().get("opa.runtime").size());
  }
}
