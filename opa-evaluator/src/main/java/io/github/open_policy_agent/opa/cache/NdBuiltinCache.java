package io.github.open_policy_agent.opa.cache;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import io.github.open_policy_agent.opa.ast.types.RegoValue;

/**
 * Thread-safe cache for non-deterministic builtin function results with LRU eviction and stale
 * entry cleanup.
 *
 * <p>This cache ensures that non-deterministic builtins (like time.now_ns, http.send, etc.) return
 * consistent results within a single evaluation and enables decision replay.
 *
 * <p>Features:
 *
 * <ul>
 *   <li>LRU eviction when max_num_entries is reached
 *   <li>Background stale entry cleanup based on configurable period
 *   <li>Thread-safe access using ConcurrentHashMap and ReentrantReadWriteLock for LRU operations
 *   <li>Supports any builtin with serializable arguments and results
 * </ul>
 */
public class NdBuiltinCache {

  private final int maxNumEntries;
  private final long staleEntryEvictionPeriodMillis;
  private final ConcurrentHashMap<CacheKey, CacheEntry> cache;
  private final LinkedHashMap<CacheKey, Long> lruTracker; // Tracks access order for LRU
  private final ReentrantReadWriteLock lruLock;
  private final ScheduledExecutorService cleanupExecutor;

  /**
   * Create a new ND builtin cache.
   *
   * @param maxNumEntries Maximum number of entries before LRU eviction (default 10000)
   * @param staleEntryEvictionPeriodSeconds Period in seconds for stale entry cleanup (default 60)
   */
  public NdBuiltinCache(int maxNumEntries, int staleEntryEvictionPeriodSeconds) {
    this.maxNumEntries = maxNumEntries;
    this.staleEntryEvictionPeriodMillis = staleEntryEvictionPeriodSeconds * 1000L;
    this.cache = new ConcurrentHashMap<>();
    this.lruTracker = new LinkedHashMap<>(16, 0.75f, true); // access-order
    this.lruLock = new ReentrantReadWriteLock();

    // Start background cleanup task
    this.cleanupExecutor =
        Executors.newSingleThreadScheduledExecutor(
            r -> {
              Thread thread = new Thread(r, "nd-builtin-cache-cleanup");
              thread.setDaemon(true);
              return thread;
            });

    // Schedule periodic cleanup
    if (staleEntryEvictionPeriodSeconds > 0) {
      this.cleanupExecutor.scheduleAtFixedRate(
          this::cleanupStaleEntries,
          staleEntryEvictionPeriodSeconds,
          staleEntryEvictionPeriodSeconds,
          TimeUnit.SECONDS);
    }
  }

  /**
   * Get a cached result for the given builtin and arguments.
   *
   * @param builtinName Name of the builtin function
   * @param args Arguments passed to the builtin
   * @return Cached result, or null if not found or stale
   */
  public RegoValue get(String builtinName, RegoValue[] args) {
    CacheKey key = new CacheKey(builtinName, args);
    CacheEntry entry = cache.get(key);

    if (entry == null) {
      return null;
    }

    // Check if entry is stale
    if (isStale(entry)) {
      cache.remove(key);
      lruLock.writeLock().lock();
      try {
        lruTracker.remove(key);
      } finally {
        lruLock.writeLock().unlock();
      }
      return null;
    }

    // Update LRU tracker, but only if the entry hasn't been evicted since we read it
    lruLock.writeLock().lock();
    try {
      if (cache.containsKey(key)) {
        lruTracker.put(key, System.currentTimeMillis());
      }
    } finally {
      lruLock.writeLock().unlock();
    }

    return entry.result;
  }

  /**
   * Store a result in the cache.
   *
   * @param builtinName Name of the builtin function
   * @param args Arguments passed to the builtin
   * @param result Result to cache
   */
  public void put(String builtinName, RegoValue[] args, RegoValue result) {
    CacheKey key = new CacheKey(builtinName, args);
    CacheEntry entry = new CacheEntry(result, System.currentTimeMillis());

    // Check if we need to evict
    lruLock.writeLock().lock();
    try {
      if (lruTracker.size() >= maxNumEntries) {
        // Remove least recently used entry
        CacheKey lruKey = lruTracker.keySet().iterator().next();
        lruTracker.remove(lruKey);
        cache.remove(lruKey);
      }
      lruTracker.put(key, System.currentTimeMillis());
      cache.put(key, entry);
    } finally {
      lruLock.writeLock().unlock();
    }
  }

  /** Remove all entries from the cache. */
  public void clear() {
    cache.clear();
    lruLock.writeLock().lock();
    try {
      lruTracker.clear();
    } finally {
      lruLock.writeLock().unlock();
    }
  }

  /** Get the current number of entries in the cache. */
  public int size() {
    return cache.size();
  }

  /** Shutdown the cleanup executor. Should be called when done with the cache. */
  public void shutdown() {
    cleanupExecutor.shutdown();
    try {
      if (!cleanupExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
        cleanupExecutor.shutdownNow();
      }
    } catch (InterruptedException e) {
      cleanupExecutor.shutdownNow();
      Thread.currentThread().interrupt();
    }
  }

  /** Check if an entry is stale based on the eviction period. */
  private boolean isStale(CacheEntry entry) {
    long age = System.currentTimeMillis() - entry.timestamp;
    return age > staleEntryEvictionPeriodMillis;
  }

  /** Background task to remove stale entries. */
  private void cleanupStaleEntries() {
    long now = System.currentTimeMillis();
    List<CacheKey> staleKeys = new ArrayList<>();

    // Find stale entries
    cache.forEach(
        (key, entry) -> {
          if (now - entry.timestamp > staleEntryEvictionPeriodMillis) {
            staleKeys.add(key);
          }
        });

    // Remove stale entries
    if (!staleKeys.isEmpty()) {
      lruLock.writeLock().lock();
      try {
        for (CacheKey key : staleKeys) {
          cache.remove(key);
          lruTracker.remove(key);
        }
      } finally {
        lruLock.writeLock().unlock();
      }
    }
  }

  /** Cache key combining builtin name and arguments. */
  public static class CacheKey {
    private final String builtinName;
    private final RegoValue[] args;
    private final int hashCode;

    public CacheKey(String builtinName, RegoValue[] args) {
      this.builtinName = builtinName;
      this.args = args;
      this.hashCode = computeHashCode();
    }

    private int computeHashCode() {
      int result = builtinName.hashCode();
      result = 31 * result + Arrays.hashCode(args);
      return result;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
      CacheKey cacheKey = (CacheKey) o;
      return builtinName.equals(cacheKey.builtinName) && Arrays.equals(args, cacheKey.args);
    }

    @Override
    public int hashCode() {
      return hashCode;
    }

    public String getBuiltinName() {
      return builtinName;
    }

    public RegoValue[] getArgs() {
      return args;
    }
  }

  /** Cache entry containing result and timestamp. */
  public static class CacheEntry {
    private final RegoValue result;
    private final long timestamp;

    public CacheEntry(RegoValue result, long timestamp) {
      this.result = result;
      this.timestamp = timestamp;
    }

    public RegoValue getResult() {
      return result;
    }

    public long getTimestamp() {
      return timestamp;
    }
  }
}
