package io.github.open_policy_agent.opa.cache;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import io.github.open_policy_agent.opa.ast.types.RegoArray;
import io.github.open_policy_agent.opa.ast.types.RegoBoolean;
import io.github.open_policy_agent.opa.ast.types.RegoInt32;
import io.github.open_policy_agent.opa.ast.types.RegoObject;
import io.github.open_policy_agent.opa.ast.types.RegoString;
import io.github.open_policy_agent.opa.ast.types.RegoValue;

/** Unit tests for NdBuiltinCache. */
public class NdBuiltinCacheTest {

  private NdBuiltinCache cache;

  @BeforeEach
  public void setUp() {
    // Default: 10 max entries, 1 second stale period
    cache = new NdBuiltinCache(10, 1);
  }

  @AfterEach
  public void tearDown() {
    if (cache != null) {
      cache.shutdown();
    }
  }

  @Test
  public void testBasicGetAndPut() {
    RegoValue[] args = new RegoValue[] {new RegoString("test")};
    RegoValue result = RegoInt32.of(42);

    // Initially empty
    assertNull(cache.get("time.now_ns", args));

    // Put and retrieve
    cache.put("time.now_ns", args, result);
    RegoValue cached = cache.get("time.now_ns", args);

    assertNotNull(cached);
    assertEquals(result, cached);
  }

  @Test
  public void testDifferentArgsProduceDifferentKeys() {
    RegoValue result1 = RegoInt32.of(100);
    RegoValue result2 = RegoInt32.of(200);

    RegoValue[] args1 = new RegoValue[] {new RegoString("arg1")};
    RegoValue[] args2 = new RegoValue[] {new RegoString("arg2")};

    cache.put("test.builtin", args1, result1);
    cache.put("test.builtin", args2, result2);

    assertEquals(result1, cache.get("test.builtin", args1));
    assertEquals(result2, cache.get("test.builtin", args2));
  }

  @Test
  public void testDifferentBuiltinsWithSameArgsAreSeparate() {
    RegoValue[] args = new RegoValue[] {new RegoString("same")};
    RegoValue result1 = RegoInt32.of(100);
    RegoValue result2 = RegoInt32.of(200);

    cache.put("builtin.one", args, result1);
    cache.put("builtin.two", args, result2);

    assertEquals(result1, cache.get("builtin.one", args));
    assertEquals(result2, cache.get("builtin.two", args));
  }

  @Test
  public void testLRUEviction() {
    cache = new NdBuiltinCache(3, 60); // Only 3 entries max

    RegoValue[] args1 = new RegoValue[] {new RegoString("1")};
    RegoValue[] args2 = new RegoValue[] {new RegoString("2")};
    RegoValue[] args3 = new RegoValue[] {new RegoString("3")};
    RegoValue[] args4 = new RegoValue[] {new RegoString("4")};

    // Fill cache to capacity
    cache.put("test", args1, RegoInt32.of(1));
    cache.put("test", args2, RegoInt32.of(2));
    cache.put("test", args3, RegoInt32.of(3));

    assertEquals(3, cache.size());

    // Adding 4th entry should evict the least recently used (args1)
    cache.put("test", args4, RegoInt32.of(4));

    assertEquals(3, cache.size());
    assertNull(cache.get("test", args1), "LRU entry should have been evicted");
    assertNotNull(cache.get("test", args2));
    assertNotNull(cache.get("test", args3));
    assertNotNull(cache.get("test", args4));
  }

  @Test
  public void testLRUEvictionWithAccess() {
    cache = new NdBuiltinCache(3, 60); // Only 3 entries max

    RegoValue[] args1 = new RegoValue[] {new RegoString("1")};
    RegoValue[] args2 = new RegoValue[] {new RegoString("2")};
    RegoValue[] args3 = new RegoValue[] {new RegoString("3")};
    RegoValue[] args4 = new RegoValue[] {new RegoString("4")};

    // Fill cache to capacity
    cache.put("test", args1, RegoInt32.of(1));
    cache.put("test", args2, RegoInt32.of(2));
    cache.put("test", args3, RegoInt32.of(3));

    // Access args1 to make it recently used
    cache.get("test", args1);

    // Adding 4th entry should now evict args2 (least recently used)
    cache.put("test", args4, RegoInt32.of(4));

    assertEquals(3, cache.size());
    assertNotNull(cache.get("test", args1), "Recently accessed entry should not be evicted");
    assertNull(cache.get("test", args2), "LRU entry should have been evicted");
    assertNotNull(cache.get("test", args3));
    assertNotNull(cache.get("test", args4));
  }

  @Test
  public void testStaleEntryEviction() throws InterruptedException {
    // Cache with 1 second stale period
    cache = new NdBuiltinCache(100, 1);

    RegoValue[] args = new RegoValue[] {new RegoString("test")};
    RegoValue result = RegoInt32.of(42);

    cache.put("time.now_ns", args, result);
    assertNotNull(cache.get("time.now_ns", args));

    // Wait for entry to become stale
    Thread.sleep(1500); // 1.5 seconds

    // Entry should be considered stale and return null
    assertNull(cache.get("time.now_ns", args), "Stale entry should return null");
  }

  @Test
  public void testBackgroundCleanup() throws InterruptedException {
    // Cache with 1 second stale period and cleanup
    cache = new NdBuiltinCache(100, 1);

    RegoValue[] args1 = new RegoValue[] {new RegoString("1")};
    RegoValue[] args2 = new RegoValue[] {new RegoString("2")};

    cache.put("test", args1, RegoInt32.of(1));
    cache.put("test", args2, RegoInt32.of(2));

    assertEquals(2, cache.size());

    // Wait for entries to become stale and for at least one cleanup cycle to run.
    // Stale period is 1s and cleanup runs every 1s, so we need >2s to guarantee
    // a cleanup fires after entries are stale (entries inserted after scheduler start).
    Thread.sleep(3500);

    // Background cleanup should have removed stale entries
    assertEquals(0, cache.size(), "Background cleanup should have removed all stale entries");
  }

  @Test
  public void testClear() {
    RegoValue[] args1 = new RegoValue[] {new RegoString("1")};
    RegoValue[] args2 = new RegoValue[] {new RegoString("2")};

    cache.put("test", args1, RegoInt32.of(1));
    cache.put("test", args2, RegoInt32.of(2));

    assertEquals(2, cache.size());

    cache.clear();

    assertEquals(0, cache.size());
    assertNull(cache.get("test", args1));
    assertNull(cache.get("test", args2));
  }

  @Test
  public void testComplexArguments() {
    // Test with array and object arguments
    RegoArray arrayArg = new RegoArray(java.util.List.of(new RegoString("a"), RegoInt32.of(1)));
    RegoObject objectArg = new RegoObject();
    objectArg.setProperty("key", new RegoString("value"));

    RegoValue[] args = new RegoValue[] {arrayArg, objectArg};
    RegoValue result = RegoBoolean.TRUE;

    cache.put("complex.builtin", args, result);
    RegoValue cached = cache.get("complex.builtin", args);

    assertNotNull(cached);
    assertEquals(result, cached);
  }

  @Test
  public void testConcurrentAccess() throws InterruptedException {
    cache = new NdBuiltinCache(100, 60);
    int numThreads = 10;
    int operationsPerThread = 100;

    Thread[] threads = new Thread[numThreads];

    for (int i = 0; i < numThreads; i++) {
      final int threadId = i;
      threads[i] =
          new Thread(
              () -> {
                for (int j = 0; j < operationsPerThread; j++) {
                  RegoValue[] args = new RegoValue[] {RegoInt32.of(threadId), RegoInt32.of(j)};
                  RegoValue result = RegoInt32.of(threadId * 1000 + j);

                  cache.put("concurrent.test", args, result);
                  RegoValue cached = cache.get("concurrent.test", args);

                  if (cached != null) {
                    assertEquals(result, cached);
                  }
                }
              });
    }

    // Start all threads
    for (Thread thread : threads) {
      thread.start();
    }

    // Wait for all threads to complete
    for (Thread thread : threads) {
      thread.join();
    }

    // Cache should be in valid state
    assertTrue(cache.size() <= 100, "Cache should respect max entries");
  }

  @Test
  public void testCacheKeyEquality() {
    RegoValue[] args1 = new RegoValue[] {new RegoString("test"), RegoInt32.of(42)};
    RegoValue[] args2 = new RegoValue[] {new RegoString("test"), RegoInt32.of(42)};
    RegoValue[] args3 = new RegoValue[] {new RegoString("test"), RegoInt32.of(99)};

    NdBuiltinCache.CacheKey key1 = new NdBuiltinCache.CacheKey("builtin", args1);
    NdBuiltinCache.CacheKey key2 = new NdBuiltinCache.CacheKey("builtin", args2);
    NdBuiltinCache.CacheKey key3 = new NdBuiltinCache.CacheKey("builtin", args3);
    NdBuiltinCache.CacheKey key4 = new NdBuiltinCache.CacheKey("other", args1);

    // Same builtin and args should be equal
    assertEquals(key1, key2);
    assertEquals(key1.hashCode(), key2.hashCode());

    // Different args should not be equal
    assertNotEquals(key1, key3);

    // Different builtin should not be equal
    assertNotEquals(key1, key4);
  }

  @Test
  public void testEmptyArgs() {
    RegoValue[] emptyArgs = new RegoValue[0];
    RegoValue result = RegoInt32.of(12345);

    cache.put("time.now_ns", emptyArgs, result);
    RegoValue cached = cache.get("time.now_ns", emptyArgs);

    assertNotNull(cached);
    assertEquals(result, cached);
  }

  @Test
  public void testShutdown() {
    cache.shutdown();

    // Should be able to call shutdown multiple times safely
    cache.shutdown();
  }
}
