package io.github.open_policy_agent.opa.integration;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import io.github.open_policy_agent.opa.ast.types.*;
import io.github.open_policy_agent.opa.rego.EvaluationContext;

/**
 * Integration tests for ND builtin cache value tracking. Verifies that nondeterministic builtins
 * record their values in the EvaluationContext for inclusion in decision logs.
 */
public class NdBuiltinCacheTrackingTest {

  @Test
  public void testTimeNowNsRecordsValue() {
    // Create evaluation context
    EvaluationContext ctx = new EvaluationContext.Builder().build();

    // Simulate time.now_ns call
    long expectedTimeNs = ctx.getEvalStartTime() * 1_000_000L;
    ctx.recordNdCacheValue("time.now_ns", new RegoValue[0], new RegoBigInt(expectedTimeNs));

    // Verify value was recorded
    Map<String, List<EvaluationContext.CacheCall>> ndCache = ctx.getNdCacheValues();
    assertNotNull(ndCache);
    assertTrue(ndCache.containsKey("time.now_ns"));

    List<EvaluationContext.CacheCall> calls = ndCache.get("time.now_ns");
    assertEquals(1, calls.size());

    EvaluationContext.CacheCall call = calls.get(0);
    assertEquals(0, call.getArgs().length);
    assertEquals(new RegoBigInt(expectedTimeNs), call.getResult());
  }

  @Test
  public void testMultipleNdBuiltinCallsRecorded() {
    EvaluationContext ctx = new EvaluationContext.Builder().build();

    // Record multiple time.now_ns calls (same builtin, multiple calls)
    long time1 = 1000000000L;
    long time2 = 2000000000L;
    ctx.recordNdCacheValue("time.now_ns", new RegoValue[0], new RegoBigInt(time1));
    ctx.recordNdCacheValue("time.now_ns", new RegoValue[0], new RegoBigInt(time2));

    // Verify both calls were recorded
    Map<String, List<EvaluationContext.CacheCall>> ndCache = ctx.getNdCacheValues();
    List<EvaluationContext.CacheCall> calls = ndCache.get("time.now_ns");
    assertEquals(2, calls.size());
    assertEquals(new RegoBigInt(time1), calls.get(0).getResult());
    assertEquals(new RegoBigInt(time2), calls.get(1).getResult());
  }

  @Test
  public void testMultipleBuiltinsRecorded() {
    EvaluationContext ctx = new EvaluationContext.Builder().build();

    // Record values from different builtins
    ctx.recordNdCacheValue("time.now_ns", new RegoValue[0], new RegoBigInt(1000000000L));

    RegoValue[] jwtArgs =
        new RegoValue[] {
          new RegoString("jwt_token"), new RegoObject() // constraints
        };
    RegoArray jwtResult =
        new RegoArray(
            List.of(
                RegoBoolean.TRUE, new RegoObject() /* header */, new RegoObject() /* payload */));
    ctx.recordNdCacheValue("io.jwt.decode_verify", jwtArgs, jwtResult);

    // Verify both builtins were recorded
    Map<String, List<EvaluationContext.CacheCall>> ndCache = ctx.getNdCacheValues();
    assertEquals(2, ndCache.size());
    assertTrue(ndCache.containsKey("time.now_ns"));
    assertTrue(ndCache.containsKey("io.jwt.decode_verify"));
  }

  @Test
  public void testCacheCallWithArguments() {
    EvaluationContext ctx = new EvaluationContext.Builder().build();

    // Record JWT encode with arguments
    RegoObject headers = new RegoObject();
    headers.setProperty("alg", new RegoString("HS256"));

    RegoObject payload = new RegoObject();
    payload.setProperty("sub", new RegoString("user123"));

    RegoObject key = new RegoObject();
    key.setProperty("secret", new RegoString("my-secret-key"));

    RegoValue[] args = new RegoValue[] {headers, payload, key};
    RegoString result = new RegoString("encoded.jwt.token");

    ctx.recordNdCacheValue("io.jwt.encode_sign", args, result);

    // Verify args and result were recorded correctly
    Map<String, List<EvaluationContext.CacheCall>> ndCache = ctx.getNdCacheValues();
    List<EvaluationContext.CacheCall> calls = ndCache.get("io.jwt.encode_sign");

    assertEquals(1, calls.size());
    EvaluationContext.CacheCall call = calls.get(0);

    assertEquals(3, call.getArgs().length);
    assertEquals(headers, call.getArgs()[0]);
    assertEquals(payload, call.getArgs()[1]);
    assertEquals(key, call.getArgs()[2]);
    assertEquals(result, call.getResult());
  }

  @Test
  public void testJsonSerialization() throws Exception {
    EvaluationContext ctx = new EvaluationContext.Builder().build();

    // Record a simple value
    ctx.recordNdCacheValue("time.now_ns", new RegoValue[0], new RegoBigInt(1234567890123456789L));

    // Get cache values
    Map<String, List<EvaluationContext.CacheCall>> ndCache = ctx.getNdCacheValues();

    // Serialize to JSON (simulating what DecisionLogPlugin does)
    ObjectMapper mapper = new ObjectMapper().registerModule(new io.github.open_policy_agent.opa.jackson.RegoValueModule());
    JsonNode cacheNode = mapper.valueToTree(ndCache);

    // This test verifies that the cache values can be serialized
    assertNotNull(cacheNode);
  }

  @Test
  public void testEmptyCacheValues() {
    EvaluationContext ctx = new EvaluationContext.Builder().build();

    // Don't record any values
    Map<String, List<EvaluationContext.CacheCall>> ndCache = ctx.getNdCacheValues();

    assertNotNull(ndCache);
    assertTrue(ndCache.isEmpty());
  }

  @Test
  public void testCacheValuesImmutable() {
    EvaluationContext ctx = new EvaluationContext.Builder().build();
    ctx.recordNdCacheValue("time.now_ns", new RegoValue[0], new RegoBigInt(1000000000L));

    // Get cache values
    Map<String, List<EvaluationContext.CacheCall>> ndCache = ctx.getNdCacheValues();

    // Should throw when trying to modify (unmodifiable map)
    assertThrows(UnsupportedOperationException.class, () -> ndCache.put("new_builtin", List.of()));
  }

  @Test
  public void testComplexArgumentSerialization() {
    EvaluationContext ctx = new EvaluationContext.Builder().build();

    // Create complex arguments with nested structures
    RegoObject complexArg = new RegoObject();
    RegoArray nestedArray =
        new RegoArray(List.of(RegoInt32.of(1), RegoInt32.of(2), RegoInt32.of(3)));
    complexArg.setProperty("numbers", nestedArray);

    RegoObject nestedObject = new RegoObject();
    nestedObject.setProperty("nested", new RegoString("value"));
    complexArg.setProperty("nested_obj", nestedObject);

    RegoValue[] args = new RegoValue[] {complexArg};
    RegoString result = new RegoString("result");

    ctx.recordNdCacheValue("test.builtin", args, result);

    // Verify complex args were recorded
    Map<String, List<EvaluationContext.CacheCall>> ndCache = ctx.getNdCacheValues();
    List<EvaluationContext.CacheCall> calls = ndCache.get("test.builtin");

    assertEquals(1, calls.size());
    EvaluationContext.CacheCall call = calls.get(0);
    assertEquals(1, call.getArgs().length);

    RegoObject recordedArg = (RegoObject) call.getArgs()[0];
    assertEquals(nestedArray, recordedArg.getProperty(new RegoString("numbers")));
    assertEquals(nestedObject, recordedArg.getProperty(new RegoString("nested_obj")));
  }

  @Test
  public void testDifferentArgsProduceDifferentEntries() {
    EvaluationContext ctx = new EvaluationContext.Builder().build();

    // Record same builtin with different args
    RegoValue[] args1 = new RegoValue[] {new RegoString("arg1")};
    RegoValue[] args2 = new RegoValue[] {new RegoString("arg2")};

    ctx.recordNdCacheValue("test.builtin", args1, RegoInt32.of(100));
    ctx.recordNdCacheValue("test.builtin", args2, RegoInt32.of(200));

    // Both should be recorded separately
    Map<String, List<EvaluationContext.CacheCall>> ndCache = ctx.getNdCacheValues();
    List<EvaluationContext.CacheCall> calls = ndCache.get("test.builtin");

    assertEquals(2, calls.size());
    assertEquals(new RegoString("arg1"), calls.get(0).getArgs()[0]);
    assertEquals(RegoInt32.of(100), calls.get(0).getResult());
    assertEquals(new RegoString("arg2"), calls.get(1).getArgs()[0]);
    assertEquals(RegoInt32.of(200), calls.get(1).getResult());
  }
}
