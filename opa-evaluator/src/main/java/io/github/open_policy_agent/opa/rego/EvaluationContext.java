package io.github.open_policy_agent.opa.rego;

import java.util.*;
import io.github.open_policy_agent.opa.ast.builtin.BuiltinRegistry;
import io.github.open_policy_agent.opa.logging.Logger;
import io.github.open_policy_agent.opa.ast.types.RegoValue;
import io.github.open_policy_agent.opa.cache.NdBuiltinCache;
import io.github.open_policy_agent.opa.ir.BlockEvent;
import io.github.open_policy_agent.opa.ir.StatementEvent;
import io.github.open_policy_agent.opa.ir.stmts.Stmt;
import io.github.open_policy_agent.opa.metrics.Metrics;
import io.github.open_policy_agent.opa.metrics.NoOpMetrics;
import io.github.open_policy_agent.opa.profiling.NoOpStatementProfiler;
import io.github.open_policy_agent.opa.profiling.StatementProfiler;
import io.github.open_policy_agent.opa.storage.Store;
import io.github.open_policy_agent.opa.tracing.Event;
import io.github.open_policy_agent.opa.tracing.Operation;
import io.github.open_policy_agent.opa.tracing.Profiler;
import io.github.open_policy_agent.opa.tracing.QueryTracer;

public class EvaluationContext {
  public String entrypoint;
  public RegoValue input;
  public Store store;
  public BuiltinRegistry builtinRegistry;
  public final Metrics metrics;
  public final StatementProfiler statementProfiler;
  private QueryTracer tracer;
  private final long evalStartTime = System.currentTimeMillis();
  private final List<Profiler> profilers;
  private final boolean strictBuiltinErrors;
  public final boolean sortSets;
  private final NdBuiltinCache ndBuiltinCache;
  private final PrintHook printHook;

  /**
   * Tracks non-deterministic builtin cache values used during this evaluation. Format:
   * Map<builtinName, List<CacheCall>> where each CacheCall contains args and result.
   */
  private final Map<String, List<CacheCall>> ndCacheValues = new HashMap<>();

  public EvaluationContext(EvaluationContext other) {
    if (other == null) {
      throw new IllegalArgumentException("null EvaluationContext");
    }
    this.entrypoint = other.entrypoint;
    this.input = other.input;
    this.store = other.store;
    this.builtinRegistry = other.builtinRegistry;
    this.tracer = other.tracer;
    this.metrics = other.metrics;
    this.statementProfiler = other.statementProfiler;
    this.profilers = other.profilers;
    this.strictBuiltinErrors = other.strictBuiltinErrors;
    this.sortSets = other.sortSets;
    this.ndBuiltinCache = other.ndBuiltinCache;
    this.printHook = other.printHook;
  }

  private EvaluationContext(Builder builder) {
    this.entrypoint = builder.entrypoint;
    this.input = builder.input;
    this.store = builder.store;
    this.builtinRegistry = builder.builtinRegistry;
    if (builder.tracer != null) {
      this.tracer = builder.tracer;
    }
    this.metrics = builder.metrics;
    this.statementProfiler = builder.statementProfiler;
    this.profilers = List.copyOf(builder.profilers);
    this.strictBuiltinErrors = builder.strictBuiltinErrors;
    this.sortSets = builder.sortSets;
    this.ndBuiltinCache = builder.ndBuiltinCache;
    this.printHook = builder.printHook;
  }

  public boolean isStrictBuiltinErrors() {
    return strictBuiltinErrors;
  }

  public long getEvalStartTime() {
    return evalStartTime;
  }

  /**
   * Get the non-deterministic builtin cache, if configured.
   *
   * @return The ND builtin cache, or null if not configured
   */
  public NdBuiltinCache getNdBuiltinCache() {
    return ndBuiltinCache;
  }

  public PrintHook getPrintHook() {
    return printHook;
  }

  public void traceEnterEvent(Stmt stmt, int blockIndex, int stmtIndex) {
    if (this.tracer != null) {
      String stmtKind = stmt.getClass().getSimpleName();
      this.tracer.TraceEvent(
          new StatementEvent(Operation.ENTER, stmt, stmtKind, stmt.getLocation(), blockIndex, stmtIndex));
    }

    for (Profiler p : this.profilers) {
      p.addStart();
    }
  }

  public void traceExitEvent(Stmt stmt, int blockIndex, int stmtIndex, long duration) {
    if (tracer != null) {
      String stmtKind = stmt.getClass().getSimpleName();
      this.tracer.TraceEvent(
          new StatementEvent(Operation.EXIT, stmt, stmtKind, stmt.getLocation(), blockIndex, stmtIndex));
    }

    for (Profiler p : profilers) {
      p.addEntry(stmt.getLocation(), duration);
    }
  }

  public void traceEnterBlock() {
    if (tracer != null) {
      tracer.TraceEvent(new BlockEvent(Operation.ENTER));
    }
  }

  public void traceExitBlock() {
    if (tracer != null) {
      tracer.TraceEvent(new BlockEvent(Operation.EXIT));
    }
  }

  public void traceBreak() {
    if (tracer != null) {
      tracer.TraceEvent(new Event(Operation.BREAK, null));
    }
  }

  /**
   * Record a non-deterministic builtin cache value used during this evaluation.
   *
   * @param builtinName Name of the builtin function
   * @param args Arguments passed to the builtin
   * @param result Result returned by the builtin
   */
  public void recordNdCacheValue(String builtinName, RegoValue[] args, RegoValue result) {
    ndCacheValues
        .computeIfAbsent(builtinName, k -> new ArrayList<>())
        .add(new CacheCall(args, result));
  }

  /**
   * Get all non-deterministic cache values used during this evaluation.
   *
   * @return Map of builtin name to list of cache calls (args + result)
   */
  public Map<String, List<CacheCall>> getNdCacheValues() {
    return Collections.unmodifiableMap(ndCacheValues);
  }

  /** Represents a single non-deterministic builtin cache call with arguments and result. */
  public static class CacheCall {
    private final RegoValue[] args;
    private final RegoValue result;

    public CacheCall(RegoValue[] args, RegoValue result) {
      this.args = args;
      this.result = result;
    }

    public RegoValue[] getArgs() {
      return args;
    }

    public RegoValue getResult() {
      return result;
    }
  }

  public static class Builder {
    private String entrypoint;
    private RegoValue input;
    private Store store;
    private BuiltinRegistry builtinRegistry;
    private QueryTracer tracer;
    Metrics metrics;
    StatementProfiler statementProfiler;
    final List<Profiler> profilers = new ArrayList<>();
    private boolean strictBuiltinErrors = false;
    private boolean sortSets = false;
    private NdBuiltinCache ndBuiltinCache;
    private PrintHook printHook = PrintHook.logger(new Logger.StandardLogger());

    public Builder withStrictBuiltinErrors() {
      this.strictBuiltinErrors = true;
      return this;
    }

    public Builder withEntrypoint(String entrypoint) {
      this.entrypoint = entrypoint;
      return this;
    }

    public Builder withInput(RegoValue input) {
      this.input = input;
      return this;
    }

    public Builder withSortedSets() {
      this.sortSets = true;
      return this;
    }

    public Builder withStore(Store store) {
      this.store = store;
      return this;
    }

    public Builder withBuiltinRegistry(BuiltinRegistry builtinRegistry) {
      this.builtinRegistry = builtinRegistry;
      return this;
    }

    public Builder withTracer(QueryTracer tracer) {
      this.tracer = tracer;
      return this;
    }

    public Builder withMetrics(Metrics metrics) {
      this.metrics = metrics;
      return this;
    }

    public Builder withStatementProfiler(StatementProfiler statementProfiler) {
      this.statementProfiler = statementProfiler;
      return this;
    }

    /**
     * Register a profiler. May be called multiple times to register more than one profiler; each
     * receives every {@code addStart} / {@code addEntry} callback during evaluation in
     * registration order.
     */
    public Builder withProfiler(Profiler profiler) {
      this.profilers.add(profiler);
      return this;
    }

    public Builder withNdBuiltinCache(NdBuiltinCache ndBuiltinCache) {
      this.ndBuiltinCache = ndBuiltinCache;
      return this;
    }

    public Builder withPrintHook(PrintHook printHook) {
      this.printHook = printHook;
      return this;
    }

    public String getEntrypoint() {
      return entrypoint;
    }

    public EvaluationContext build() {
      if (builtinRegistry == null) {
        builtinRegistry = BuiltinRegistry.allCapabilities();
      }
      if (metrics == null) {
        metrics = NoOpMetrics.Instance();
      }
      if (statementProfiler == null) {
        statementProfiler = new NoOpStatementProfiler();
      }
      return new EvaluationContext(this);
    }
  }
}
