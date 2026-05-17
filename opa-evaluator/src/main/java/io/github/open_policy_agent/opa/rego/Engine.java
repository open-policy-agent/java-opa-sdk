package io.github.open_policy_agent.opa.rego;

import io.github.open_policy_agent.opa.ast.builtin.BuiltinRegistry;
import io.github.open_policy_agent.opa.ast.builtin.Descriptor;
import io.github.open_policy_agent.opa.ast.types.RegoObject;
import io.github.open_policy_agent.opa.ast.types.RegoValue;
import io.github.open_policy_agent.opa.bundle.BundleLoader;
import io.github.open_policy_agent.opa.ir.PolicyNotFoundException;
import io.github.open_policy_agent.opa.ir.PreparedPlan;
import io.github.open_policy_agent.opa.ir.policy.Policy;
import io.github.open_policy_agent.opa.mapper.RegoMapper;
import io.github.open_policy_agent.opa.metrics.Metrics;
import io.github.open_policy_agent.opa.profiling.StatementProfiler;
import io.github.open_policy_agent.opa.storage.InMem;
import io.github.open_policy_agent.opa.storage.Store;
import io.github.open_policy_agent.opa.tracing.Profiler;
import io.github.open_policy_agent.opa.tracing.QueryTracer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

/**
 * Direct policy evaluation engine for OPA.
 *
 * <p>Engine provides a lightweight API for loading and evaluating OPA policies without the overhead
 * of a full plugin system. Use this when you need direct control over policy evaluation.
 *
 * <p><b>When to use Engine:</b>
 *
 * <ul>
 *   <li>You don't need bundle management or decision logging
 *   <li>You want fine-grained control over capabilities
 * </ul>
 *
 * <p><b>Hot-reload semantics (matching upstream OPA):</b>
 *
 * <ul>
 *   <li><b>Data</b> is read live from {@link Store#currentData()} on every evaluation. Data changes
 *       in the store are visible immediately without any action.
 *   <li><b>Policy</b> is compiled into an internal evaluator at build time and cached. Policy
 *       changes in the store are <em>not</em> visible until {@link #refresh()} is called. When
 *       using (services) io.github.open_policy_agent.opa.Opa Opa, refresh is called automatically on
 *       bundle activation.
 *   <li><b>{@link PreparedQuery}</b> caches both the compiled policy <em>and</em> pre-computed plan
 *       data from the evaluator at preparation time. After {@link #refresh()}, existing
 *       PreparedQuery instances continue using the old policy. Data remains live. Call
 *       {@link #prepareForEvaluation()} again to create a PreparedQuery with the new policy.
 * </ul>
 *
 * <p><b>Basic Example:</b>
 *
 * <pre>{@code
 * Engine engine = new Engine.Builder()
 *     .withBundleLoader(new TarballBundleLoader("policy.tar.gz"))
 *     .withEntrypoint("example/allow")
 *     .build();
 *
 * JsonNode input = mapper.readTree("{\"user\": \"alice\"}");
 * List<JsonNode> results = engine.prepareForEvaluation()
 *     .build()
 *     .eval(input);
 * }</pre>
 */
public class Engine {
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final RegoMapper REGO_MAPPER = new RegoMapper();

  private volatile Evaluator evaluator;
  private final Store store;
  private final BuiltinRegistry builtinRegistry;
  private final String defaultEntrypoint;

  private Engine(Builder builder) {
    this.evaluator = builder.evaluator;
    this.store = builder.store;
    this.builtinRegistry = builder.builtinRegistry;
    this.defaultEntrypoint = builder.defaultEntrypoint;
  }

  /**
   * Refresh the engine to pick up policy changes from the store. Rebuilds the evaluator from the
   * current policy in the store. Data does not need refreshing -- it is read live from the store on
   * each evaluation via {@code store.currentData()}.
   *
   * <p>This is called automatically by {@code Opa} when a bundle is activated. Direct Engine users
   * should call this after updating the store with new policies.
   *
   * <p>Note: Existing {@link PreparedQuery} instances retain their warmed plan from the previous
   * evaluator. Call {@link #prepareForEvaluation()} again after refresh to get an updated
   * PreparedQuery.
   *
   * @throws PolicyNotFoundException if no policy is found for the default entrypoint
   */
  public void refresh() {
    Policy policy = store.getIrPolicyForEntrypoint(defaultEntrypoint);
    if (policy == null) {
      throw new PolicyNotFoundException(defaultEntrypoint);
    }
    this.evaluator =
        new io.github.open_policy_agent.opa.ir.Evaluator.Builder()
            .withPolicy(policy)
            .withBuiltinRegistry(builtinRegistry)
            .build();
  }

  /**
   * Create a builder for a {@link PreparedQuery} that pre-computes plan data for faster evaluation.
   *
   * <p>The PreparedQuery captures the current policy at the time {@code build()} is called. If the
   * engine is later refreshed via {@link #refresh()}, existing PreparedQuery instances continue using
   * the old policy. Data remains live (read from the store on each eval). To pick up a new policy,
   * call this method again after refresh.
   *
   * @return a new PreparedQuery builder
   */
  public PreparedQuery.Builder prepareForEvaluation() {
    return new PreparedQuery.Builder()
        .withEngine(this)
        .withContextBuilder(
            new EvaluationContext.Builder()
                .withStore(store)
                .withBuiltinRegistry(builtinRegistry)
                .withEntrypoint(defaultEntrypoint));
  }

  /**
   * Evaluate with a JsonNode input and return JsonNode results. Uses the engine's current policy
   * (updated by {@link #refresh()}) and reads data live from the store.
   *
   * @param ctx the evaluation context
   * @param input the input as a Jackson JsonNode
   * @return list of JsonNode results
   */
  public List<JsonNode> evaluate(EvaluationContext ctx, JsonNode input) {
    RegoObject regoInput = parseJsonInput(ctx, input);
    RegoValue[] results = evaluateCore(null, ctx, regoInput);
    return marshalJsonResults(ctx, results);
  }

  /**
   * Evaluate with a POJO input and return typed results. This bypasses intermediate JsonNode
   * allocations by converting the POJO directly to RegoValue and the results directly to the target
   * type. Uses the engine's current policy (updated by {@link #refresh()}) and reads data live from
   * the store.
   *
   * @param <T> the result type
   * @param ctx the evaluation context
   * @param pojoInput the input as a POJO (any JavaBean-compatible object)
   * @param resultType the class of the desired result type
   * @return list of typed results
   */
  public <T> List<T> evaluate(EvaluationContext ctx, Object pojoInput, Class<T> resultType) {
    RegoObject regoInput = parsePojoInput(ctx, pojoInput);
    RegoValue[] results = evaluateCore(null, ctx, regoInput);
    return marshalPojoResults(ctx, results, resultType);
  }

  List<JsonNode> evaluateWithPreparedPlan(
      PreparedPlan preparedPlan, EvaluationContext ctx, JsonNode input) {
    RegoObject regoInput = parseJsonInput(ctx, input);
    RegoValue[] results = evaluateCore(preparedPlan, ctx, regoInput);
    return marshalJsonResults(ctx, results);
  }

  <T> List<T> evaluateWithPreparedPlan(
      PreparedPlan preparedPlan, EvaluationContext ctx, Object pojoInput, Class<T> resultType) {
    RegoObject regoInput = parsePojoInput(ctx, pojoInput);
    RegoValue[] results = evaluateCore(preparedPlan, ctx, regoInput);
    return marshalPojoResults(ctx, results, resultType);
  }

  // ---------------------------------------------------------------------------
  // Private core: parse → evaluate → marshal
  // ---------------------------------------------------------------------------

  private RegoValue[] evaluateCore(PreparedPlan plan, EvaluationContext ctx, RegoValue input) {
    try {
      ctx.metrics.timer("rego_query_eval").start();
      if (plan != null) {
        return evaluator.evaluate(plan, ctx, input, store.currentData());
      }
      return evaluator.evaluate(ctx, input, store.currentData());
    } finally {
      ctx.metrics.timer("rego_query_eval").stop();
    }
  }

  private RegoObject parseJsonInput(EvaluationContext ctx, JsonNode input) {
    try {
      ctx.metrics.timer("rego_parse_json_input").start();
      return MAPPER.treeToValue(input, RegoObject.class);
    } catch (Exception e) {
      throw new RuntimeException("Failed to parse JsonNode input", e);
    } finally {
      ctx.metrics.timer("rego_parse_json_input").stop();
    }
  }

  private RegoObject parsePojoInput(EvaluationContext ctx, Object input) {
    try {
      ctx.metrics.timer("rego_parse_pojo_input").start();
      return REGO_MAPPER.toRegoObject(input);
    } catch (Exception e) {
      throw new RuntimeException("Failed to parse POJO input", e);
    } finally {
      ctx.metrics.timer("rego_parse_pojo_input").stop();
    }
  }

  private List<JsonNode> marshalJsonResults(EvaluationContext ctx, RegoValue[] results) {
    try {
      ctx.metrics.timer("rego_marshal_json_results").start();
      List<JsonNode> jsonResults = new ArrayList<>(results.length);
      for (RegoValue result : results) {
        jsonResults.add(MAPPER.valueToTree(result));
      }
      return jsonResults;
    } finally {
      ctx.metrics.timer("rego_marshal_json_results").stop();
    }
  }

  private <T> List<T> marshalPojoResults(
      EvaluationContext ctx, RegoValue[] results, Class<T> resultType) {
    try {
      ctx.metrics.timer("rego_marshal_pojo_results").start();
      List<T> typedResults = new ArrayList<>(results.length);
      for (RegoValue result : results) {
        // OPA plans wrap every result as {"result": <actual_decision>}.
        // Unwrap so the target type maps to the decision value, not the wrapper.
        RegoValue value = unwrapResultKey(result);
        typedResults.add(REGO_MAPPER.fromRegoValue(value, resultType));
      }
      return typedResults;
    } finally {
      ctx.metrics.timer("rego_marshal_pojo_results").stop();
    }
  }

  private static RegoValue unwrapResultKey(RegoValue value) {
    if (value instanceof RegoObject) {
      RegoValue inner = ((RegoObject) value).getProperty("result");
      if (inner != null) {
        return inner;
      }
    }
    return value;
  }

  /**
   * Builder for creating Engine instances with direct policy evaluation capabilities.
   *
   * <p>Use this builder when you need:
   *
   * <ul>
   *   <li>Direct, low-level control over policy evaluation
   *   <li>Custom builtin functions
   *   <li>Specific capability restrictions
   *   <li>Lightweight evaluation without plugins
   * </ul>
   *
   * <p>Example usage:
   *
   * <pre>{@code
   * Engine engine = new Engine.Builder()
   *     .withBundleLoader(new TarballBundleLoader("policy.tar.gz"))
   *     .withEntrypoint("example/allow")
   *     .withBuiltin("custom.function", customImpl)
   *     .build();
   * }</pre>
   */
  public static class Builder {
    private Evaluator evaluator;
    private Store store;
    private Capabilities capabilities;
    private final HashMap<String, BiFunction<EvaluationContext, RegoValue[], RegoValue>>
        customBuiltIns = new HashMap<>();
    private BuiltinRegistry builtinRegistry;
    private String defaultEntrypoint = "";
    private final List<BundleLoader> loaders = new ArrayList<>();

    public Builder() {}

    /**
     * Set the data store for policy evaluation. The store contains compiled policies and data
     * documents. If not provided, an in-memory store (InMem) is used.
     *
     * @param store the data store implementation
     * @return this builder
     */
    public Builder withStore(Store store) {
      this.store = store;
      return this;
    }

    /**
     * Add a bundle loader to load compiled policies. Multiple loaders can be added by calling this
     * method multiple times. Bundles are loaded in the order they are added.
     *
     * <p>Example:
     *
     * <pre>{@code
     * builder.withBundleLoader(new TarballBundleLoader("auth.tar.gz"))
     *        .withBundleLoader(new TarballBundleLoader("data.tar.gz"));
     * }</pre>
     *
     * @param loader the bundle loader to add
     * @return this builder
     */
    public Builder withBundleLoader(BundleLoader loader) {
      this.loaders.add(loader);
      return this;
    }

    /**
     * Set the capabilities defining which builtins are available. If not provided, all implemented
     * builtins are available. Use this to restrict which builtins policies can use.
     *
     * <p>Note: Only builtins that are both in the capabilities list AND implemented in this SDK
     * will be available.
     *
     * @param capabilities the capabilities object defining available builtins
     * @return this builder
     */
    public Builder withCapabilities(Capabilities capabilities) {
      this.capabilities = capabilities;
      return this;
    }

    /**
     * Register a custom builtin function. Custom builtins can be called from Rego policies using
     * the specified name.
     *
     * <p>Example:
     *
     * <pre>{@code
     * builder.withBuiltin("custom.reverse", (ctx, args) -> {
     *     String input = ((RegoString) args[0]).getValue();
     *     String reversed = new StringBuilder(input).reverse().toString();
     *     return new RegoString(reversed);
     * });
     * }</pre>
     *
     * @param name the builtin function name (e.g., "custom.reverse")
     * @param builtIn the function implementation
     * @return this builder
     */
    public Builder withBuiltin(
        String name, BiFunction<EvaluationContext, RegoValue[], RegoValue> builtIn) {
      this.customBuiltIns.put(name, builtIn);
      return this;
    }

    /**
     * Set the default entrypoint to evaluate. This entrypoint will be used by
     * prepareForEvaluation() unless overridden.
     *
     * @param entrypoint the entrypoint path (e.g., "example/allow")
     * @return this builder
     */
    public Builder withEntrypoint(String entrypoint) {
      this.defaultEntrypoint = entrypoint;
      return this;
    }

    /**
     * Validates that all builtin functions required by the policy are available in the registry.
     *
     * @param policy the policy to validate
     * @param registry the builtin registry
     * @throws io.github.open_policy_agent.opa.ir.FunctionNotFoundError if any required functions are
     *     missing
     */
    public static void validateRequiredBuiltins(Policy policy, BuiltinRegistry registry) {
      java.util.Set<String> missingFunctions = new java.util.LinkedHashSet<>();

      // Check all required builtin functions from the policy's static section
      if (policy.getStaticField() != null && policy.getStaticField().getBuiltinFuncs() != null) {
        for (io.github.open_policy_agent.opa.ir.policy.BuiltinFunc builtinFunc :
            policy.getStaticField().getBuiltinFuncs()) {
          String funcName = builtinFunc.getName();
          if (!registry.hasBuiltIn(funcName)) {
            missingFunctions.add(funcName);
          }
        }
      }

      if (!missingFunctions.isEmpty()) {
        // Throw error with the first missing function but include all in context
        String firstMissing = missingFunctions.iterator().next();
        io.github.open_policy_agent.opa.ir.FunctionNotFoundError error =
            new io.github.open_policy_agent.opa.ir.FunctionNotFoundError(firstMissing);
        error.withContext("all_missing", new java.util.ArrayList<>(missingFunctions));
        throw error;
      }
    }

    /**
     * Build the Engine instance. This loads all bundles and initializes the evaluation environment.
     *
     * @return the configured Engine
     * @throws io.github.open_policy_agent.opa.ir.PolicyNotFoundException if no policy is found for the
     *     default entrypoint
     */
    public Engine build() {
      if (store == null) {
        store = new InMem();
      }
      loaders.forEach(loader -> loader.load(store));

      if (capabilities == null) {
        capabilities = new Capabilities();
        capabilities.builtins =
            BuiltinRegistry.allCapabilities().registeredBuiltins().stream()
                .map(Descriptor::new)
                .collect(Collectors.toList());
      }

      builtinRegistry = BuiltinRegistry.fromCapabilities(capabilities);

      for (String name : customBuiltIns.keySet()) {
        builtinRegistry.registerBuiltIn(name, customBuiltIns.get(name));
      }

      Policy policy = store.getIrPolicyForEntrypoint(defaultEntrypoint);
      if (policy == null) {
        throw new io.github.open_policy_agent.opa.ir.PolicyNotFoundException(defaultEntrypoint);
      }

      evaluator =
          new io.github.open_policy_agent.opa.ir.Evaluator.Builder()
              .withPolicy(policy)
              .withBuiltinRegistry(builtinRegistry)
              .build();

      return new Engine(this);
    }
  }

  /**
   * A pre-compiled query that caches plan data for faster repeated evaluation.
   *
   * <p>PreparedQuery snapshots the policy (plan, functions, string table) at preparation time for
   * optimized evaluation. Data is still read live from the store on each {@link #eval} call.
   *
   * <p><b>Hot-reload behavior:</b> After {@link Engine#refresh()}, this PreparedQuery continues
   * using the policy it was prepared with. Data changes are visible immediately. To evaluate with an
   * updated policy, create a new PreparedQuery via {@link Engine#prepareForEvaluation()}.
   */
  public static class PreparedQuery {
    private final EvaluationContext.Builder contextBuilder;
    private final Engine engine;
    private final io.github.open_policy_agent.opa.ir.PreparedPlan preparedPlan;

    private PreparedQuery(Builder builder) {
      this.engine = builder.engine;
      this.contextBuilder = builder.contextBuilder;
      this.preparedPlan = builder.preparedPlan;
    }

    /**
     * Evaluate with a JsonNode input and return JsonNode results. Uses the policy captured at
     * preparation time and reads data live from the store.
     *
     * @param input the input as a Jackson JsonNode
     * @return list of JsonNode results
     */
    public List<JsonNode> eval(JsonNode input) {
      return engine.evaluateWithPreparedPlan(preparedPlan, contextBuilder.build(), input);
    }

    /**
     * Evaluate with a POJO input and return typed results. This is the most efficient
     * evaluation path — it bypasses all intermediate JsonNode allocations. Uses the policy captured
     * at preparation time and reads data live from the store.
     *
     * <pre>{@code
     * MyResult result = pq.eval(myInput, MyResult.class).get(0);
     * }</pre>
     *
     * @param <T> the result type
     * @param input the input as a POJO (any JavaBean-compatible object)
     * @param resultType the class of the desired result type
     * @return list of typed results
     */
    public <T> List<T> eval(Object input, Class<T> resultType) {
      return engine.evaluateWithPreparedPlan(
          preparedPlan, contextBuilder.build(), input, resultType);
    }

    /**
     * Builder for creating PreparedQuery instances with evaluation options.
     *
     * <p>Use this builder to configure evaluation-specific settings like metrics, profiling, and
     * tracing without rebuilding the Engine.
     *
     * <p>The {@link #build()} method captures the engine's current policy into a
     * {@link io.github.open_policy_agent.opa.ir.PreparedPlan PreparedPlan}. If the engine is refreshed
     * later, this PreparedQuery retains the old policy. Data remains live.
     *
     * <p>Example usage:
     *
     * <pre>{@code
     * PreparedQuery pq = engine.prepareForEvaluation()
     *     .withMetrics(metrics)
     *     .withProfiler(profiler)
     *     .withEntrypoint("custom/rule")
     *     .build();
     * }</pre>
     */
    public static class Builder {
      private Engine engine;
      private EvaluationContext.Builder contextBuilder;
      private QueryTracer tracer;
      private Metrics metrics;
      private StatementProfiler statementProfiler;
      private final List<Profiler> profilers = new ArrayList<>();
      private String entrypoint;
      private PreparedPlan preparedPlan;
      private PrintHook printHook;
      private boolean printHookSet;

      private Builder withEngine(Engine engine) {
        this.engine = engine;
        return this;
      }

      private Builder withContextBuilder(EvaluationContext.Builder contextBuilder) {
        this.contextBuilder = contextBuilder;
        return this;
      }

      /**
       * Enable tracing for debugging. The tracer receives events during evaluation
       * showing which rules were entered, exited, and their results.
       *
       * @param tracer the tracer implementation
       * @return this builder
       */
      public Builder withTracer(QueryTracer tracer) {
        this.tracer = tracer;
        return this;
      }

      /**
       * Enable performance metrics collection. Metrics track timing information for various stages
       * of evaluation.
       *
       * @param metrics the metrics collector
       * @return this builder
       */
      public Builder withMetrics(Metrics metrics) {
        this.metrics = metrics;
        return this;
      }

      /**
       * Enable statement profiling. Statement profilers track execution time and frequency of
       * individual statements during policy evaluation.
       *
       * @param statementProfiler the statement profiler implementation
       * @return this builder
       */
      public Builder withStatementProfiler(StatementProfiler statementProfiler) {
        this.statementProfiler = statementProfiler;
        return this;
      }

      /**
       * Register a profiler. Profilers receive a callback for every executed statement, keyed by
       * source location, and can be used for time-by-line profiling, line coverage, or any other
       * location-based analysis. May be called multiple times to register more than one profiler;
       * each receives every callback in registration order.
       *
       * @param profiler the profiler implementation
       * @return this builder
       */
      public Builder withProfiler(Profiler profiler) {
        this.profilers.add(profiler);
        return this;
      }

      /**
       * Override the default entrypoint for this evaluation. This allows evaluating different
       * entrypoints using the same Engine instance.
       *
       * @param entrypoint the entrypoint path (e.g., "example/deny")
       * @return this builder
       */
      public Builder withEntrypoint(String entrypoint) {
        this.entrypoint = entrypoint;
        return this;
      }

      /**
       * Configure a print hook to capture output from OPA's {@code print()} builtin.
       * By default, print output goes to stderr.
       *
       * @param printHook the hook to receive print output
       * @return this builder
       * @see PrintHook#logger(Logger)
       */
      public Builder withPrintHook(PrintHook printHook) {
        this.printHook = printHook;
        this.printHookSet = true;
        return this;
      }

      /**
       * Build the PreparedQuery ready for evaluation. Captures the engine's current policy into a
       * pre-computed plan. Subsequent policy refreshes do not affect this PreparedQuery.
       *
       * @return the configured PreparedQuery
       */
      public PreparedQuery build() {
        if (tracer != null) {
          contextBuilder.withTracer(tracer);
        }
        if (metrics != null) {
          contextBuilder.withMetrics(metrics);
        }
        if (statementProfiler != null) {
          contextBuilder.withStatementProfiler(statementProfiler);
        }
        for (Profiler p : profilers) {
          contextBuilder.withProfiler(p);
        }
        if (entrypoint != null) {
          contextBuilder.withEntrypoint(entrypoint);
        }
        if (printHookSet) {
          contextBuilder.withPrintHook(printHook);
        }

        // Warm up the plan by pre-computing expensive values
        String entrypointToWarm = entrypoint != null ? entrypoint : engine.defaultEntrypoint;

        this.preparedPlan =
            new PreparedPlan.Builder()
                .warmUp(
                    entrypointToWarm,
                    (io.github.open_policy_agent.opa.ir.Evaluator) engine.evaluator)
                .build();

        return new PreparedQuery(this);
      }
    }
  }
}
