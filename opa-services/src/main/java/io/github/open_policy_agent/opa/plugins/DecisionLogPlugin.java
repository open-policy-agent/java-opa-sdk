package io.github.open_policy_agent.opa.plugins;

import io.github.open_policy_agent.opa.bundle.Bundle;
import io.github.open_policy_agent.opa.config.Config;
import io.github.open_policy_agent.opa.logging.Logger;
import io.github.open_policy_agent.opa.metrics.Metrics;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import io.github.open_policy_agent.opa.ir.policy.Policy;
import io.github.open_policy_agent.opa.rego.Engine;
import io.github.open_policy_agent.opa.storage.Store;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Plugin that logs policy decision events.
 *
 * <p>DecisionLogPlugin records policy decisions for audit, compliance, and analytics purposes. It:
 *
 * <ul>
 *   <li>Buffers decision events in memory
 *   <li>Periodically uploads batches to a configured service
 *   <li>Optionally logs decisions to console
 *   <li>Supports masking and dropping sensitive decisions
 * </ul>
 */
public final class DecisionLogPlugin implements Plugin {

  private static final long DEFAULT_UPLOAD_SIZE_LIMIT_BYTES = 32768;
  private static final long RETRY_BACKOFF_NANOS = TimeUnit.SECONDS.toNanos(1);

  private DecisionLogs decisionLogs;
  private int resolvedMinDelaySeconds = Config.DecisionLogsConfig.DEFAULT_MIN_DELAY_SECONDS;
  private int resolvedMaxDelaySeconds = Config.DecisionLogsConfig.DEFAULT_MAX_DELAY_SECONDS;
  private PluginManager manager;
  private ScheduledExecutorService scheduler;
  private PluginManager.BundleActivationListener maskListener;

  public DecisionLogPlugin() {}

  @Override
  @SuppressWarnings("deprecation") // the top-level delays stay supported as a fallback
  public Set<String> validate(PluginManager manager) {
    Set<String> errors = new HashSet<>();

    Config.DecisionLogsConfig logsConfig = manager.getConfig().getDecisionLogs();
    if (logsConfig == null) {
      return errors; // No decision logs config is valid
    }

    // If service is specified, validate it exists
    if (logsConfig.getService() != null && !logsConfig.getService().isEmpty()) {
      if (manager.getConfig().getService(logsConfig.getService()) == null) {
        errors.add(
            "Decision logs references non-existent service '" + logsConfig.getService() + "'");
      }
    }

    // Validate delay settings using the same defaults as start() (OPA Go rejects when
    // post-default min > max; e.g. max_delay_seconds: 120 alone defaults min to 300).
    Integer configuredMin = logsConfig.getMinDelaySeconds();
    Integer configuredMax = logsConfig.getMaxDelaySeconds();
    if (configuredMin != null && configuredMin < 0) {
      errors.add("Decision logs min_delay_seconds must be >= 0");
    }
    if (configuredMax != null && configuredMax < 0) {
      errors.add("Decision logs max_delay_seconds must be >= 0");
    }
    int effectiveMin =
        configuredMin != null ? configuredMin : Config.DecisionLogsConfig.DEFAULT_MIN_DELAY_SECONDS;
    int effectiveMax =
        configuredMax != null ? configuredMax : effectiveMin * 2;
    if (effectiveMin > effectiveMax) {
      errors.add(
          "Decision logs min_delay_seconds ("
              + effectiveMin
              + ") cannot be greater than max_delay_seconds ("
              + effectiveMax
              + ")");
    }

    // Validate reporting config if present: OPA requires both delays or neither
    if (logsConfig.getReporting() != null) {
      Config.ReportingConfig reporting = logsConfig.getReporting();
      Integer rMin = reporting.getMinDelaySeconds();
      Integer rMax = reporting.getMaxDelaySeconds();
      if (rMin != null && rMin < 0) {
        errors.add("Decision logs reporting min_delay_seconds must be >= 0");
      }
      if (rMax != null && rMax < 0) {
        errors.add("Decision logs reporting max_delay_seconds must be >= 0");
      }
      final Integer eventLimit = reporting.getBufferSizeLimitEvents();
      if (eventLimit != null && eventLimit < 1) {
        errors.add("Decision logs reporting buffer_size_limit_events must be higher than 0");
      }
      if (rMin != null && rMax == null) {
        errors.add("Decision logs reporting is missing 'max_delay_seconds'");
      } else if (rMin == null && rMax != null) {
        errors.add("Decision logs reporting is missing 'min_delay_seconds'");
      } else if (rMin != null && rMin > rMax) {
        errors.add(
            "Decision logs reporting min_delay_seconds ("
                + rMin
                + ") cannot be greater than max_delay_seconds ("
                + rMax
                + ")");
      }
    }

    return errors;
  }

  @Override
  public Plugin initialize(PluginManager manager) {
    DecisionLogPlugin plugin = new DecisionLogPlugin();
    plugin.manager = manager;
    plugin.scheduler = BundleDownloader.newPollScheduler("opa-decision-log-scheduler");

    Config.DecisionLogsConfig logsConfig = manager.getConfig().getDecisionLogs();
    if (logsConfig != null) {
      plugin.decisionLogs =
          new DecisionLogs(manager.getLogger(), manager)
              .setConsole(logsConfig.getConsole())
              .setService(logsConfig.getService())
              .setMaskDecision(logsConfig.getMaskDecision())
              .setDropDecision(logsConfig.getDropDecision())
              .setResource(logsConfig.getResource())
              .setReporting(logsConfig.getReporting());

      plugin.decisionLogs.setFlushExecutor(plugin.scheduler);

      final int[] delays = logsConfig.flushDelaysSeconds();
      plugin.resolvedMinDelaySeconds = delays[0];
      plugin.resolvedMaxDelaySeconds = delays[1];

      // A new bundle means a new policy, so the cached mask query has to be rebuilt.
      DecisionLogs logs = plugin.decisionLogs;
      plugin.maskListener = bundleName -> logs.dropMaskQuery();
      manager.registerBundleActivationListener(plugin.maskListener);
    }

    return plugin;
  }

  @Override
  public void start() {
    if (decisionLogs == null) {
      manager.updatePluginStatus("decision_logs", PluginManager.Status.OK);
      return;
    }

    scheduleNextFlush(0);

    manager.updatePluginStatus("decision_logs", PluginManager.Status.OK);
  }

  int resolvedMinDelaySeconds() {
    return resolvedMinDelaySeconds;
  }

  int resolvedMaxDelaySeconds() {
    return resolvedMaxDelaySeconds;
  }

  /** Seconds before retry number {@code failures} + 1: 1s doubling, capped at the max delay. */
  static int retryDelaySeconds(int failures, int maxDelaySeconds) {
    return (int) Math.min(1L << Math.min(failures, 30), maxDelaySeconds);
  }

  // After a successful flush the next one comes after a uniformly random delay in [min, max],
  // matching Go-OPA's jittered reporting interval; after a failed one it is a backoff retry
  // (mirrors BundleDownloader.scheduleNextPoll). The task chains itself because
  // ScheduledExecutorService has no jitter. RejectedExecutionException after a shutdown ends it.
  private void scheduleNextFlush(int failures) {
    final long delay =
        failures > 0
            ? retryDelaySeconds(failures - 1, resolvedMaxDelaySeconds)
            : resolvedMinDelaySeconds >= resolvedMaxDelaySeconds
                ? resolvedMinDelaySeconds
                : ThreadLocalRandom.current()
                    .nextLong(resolvedMinDelaySeconds, (long) resolvedMaxDelaySeconds + 1);
    try {
      scheduler.schedule(
          () -> {
            int nextFailures = failures + 1;
            try {
              if (decisionLogs.flush()) {
                nextFailures = 0;
              }
            } catch (Exception e) {
              // flush() handles its own logging; swallow so the chain keeps flushing. Only
              // Exception is caught here: Errors (OOM, etc.) propagate and let the executor's
              // uncaught-exception handler tear down the pool.
            } finally {
              scheduleNextFlush(nextFailures);
            }
          },
          delay,
          TimeUnit.SECONDS);
    } catch (RejectedExecutionException stopped) {
      // Scheduler was shut down; let the chain end.
    }
  }

  @Override
  public void stop() {
    if (maskListener != null) {
      manager.deregisterBundleActivationListener(maskListener);
      maskListener = null;
    }

    if (scheduler != null) {
      manager.getLogger().info("Stopping decision logs plugin...");

      // Flush any remaining logs before shutting down
      if (decisionLogs != null) {
        decisionLogs.flush();
        if (decisionLogs.pending() > 0) {
          manager
              .getLogger()
              .warn("Shutting down with %d undelivered decision log events", decisionLogs.pending());
        }
      }

      scheduler.shutdown();
      try {
        if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
          manager
              .getLogger()
              .warn("Decision logs plugin scheduler did not terminate, forcing shutdown");
          scheduler.shutdownNow();
        }
      } catch (InterruptedException e) {
        manager.getLogger().warn("Interrupted while stopping decision logs plugin");
        scheduler.shutdownNow();
        Thread.currentThread().interrupt();
      }
    }
  }

  /**
   * Log a decision event. This is the main entry point for logging decisions.
   *
   * @param decisionId unique identifier for this decision
   * @param input the input document
   * @param result the policy decision result
   * @param path the policy path evaluated
   * @param startTime startTime when the decision was made (Instant)
   * @param metrics performance metrics for the decision (optional)
   * @param ndCacheValues non-deterministic cache values used during evaluation (optional)
   */
  public void logDecision(
      String decisionId,
      JsonNode input,
      JsonNode result,
      String path,
      long startTime,
      Metrics metrics,
      Map<String, java.util.List<io.github.open_policy_agent.opa.rego.EvaluationContext.CacheCall>>
          ndCacheValues) {
    if (decisionLogs != null) {
      decisionLogs.logDecision(
          decisionId, input, result, path, null, startTime, metrics, ndCacheValues);
    }
  }

  /**
   * Get the DecisionLogs instance for testing.
   *
   * @return the DecisionLogs instance, or null if not configured
   */
  public DecisionLogs getDecisionLogs() {
    return decisionLogs;
  }

  public void flush() {
    decisionLogs.flush();
  }

  /** Decision logger that buffers and uploads decision events. */
  public static class DecisionLogs {
    // Jackson 3 has java.time support built-in; no need to register JavaTimeModule.
    private static final ObjectMapper MAPPER = JsonMapper.shared();

    private final Logger logger;
    private final PluginManager manager;
    private final ConcurrentLinkedDeque<ObjectNode> buffer = new ConcurrentLinkedDeque<>();
    // ConcurrentLinkedDeque.size() walks the list, which is too slow to call per decision.
    private final AtomicInteger buffered = new AtomicInteger();
    private final AtomicBoolean dropWarned = new AtomicBoolean();
    private volatile long retryNotBeforeNanos = System.nanoTime();
    // The buffer limit starts its flush here instead of on the logging thread, which would
    // otherwise wait for the upload.
    private final AtomicBoolean flushQueued = new AtomicBoolean();
    private Executor flushExecutor = Runnable::run;
    private final Object maskLock = new Object();
    private Boolean console;
    private String service;
    private String maskEntrypoint;
    private String dropDecision;
    private Integer minDelaySeconds;
    private Integer maxDelaySeconds;
    private String resource;
    private Config.ReportingConfig reporting;
    private Engine.PreparedQuery maskQuery;
    private String maskQueryFailure;
    private boolean maskQueryPrepared;

    private DecisionLogs(Logger logger, PluginManager manager) {
      this.logger = logger;
      this.manager = manager;
    }

    public DecisionLogs setConsole(Boolean console) {
      this.console = console;
      return this;
    }

    public String getService() {
      return service;
    }

    public DecisionLogs setService(String service) {
      this.service = service;
      return this;
    }

    public DecisionLogs setMaskDecision(String maskDecision) {
      // Configured as a data path ("/system/log/mask"); plan entrypoints have no leading slash.
      String entrypoint = maskDecision == null ? "" : maskDecision.trim().replaceAll("^/+|/+$", "");
      this.maskEntrypoint = entrypoint.isEmpty() ? null : entrypoint;
      dropMaskQuery();
      return this;
    }

    public DecisionLogs setDropDecision(String dropDecision) {
      this.dropDecision = dropDecision;
      return this;
    }

    /** @deprecated the flush schedule is resolved from {@code reporting}; this is no longer used. */
    @Deprecated
    public Integer getMinDelaySeconds() {
      return minDelaySeconds;
    }

    /** @deprecated the flush schedule is resolved from {@code reporting}; this is no longer used. */
    @Deprecated
    public DecisionLogs setMinDelaySeconds(Integer minDelaySeconds) {
      this.minDelaySeconds = minDelaySeconds;
      return this;
    }

    /** @deprecated the flush schedule is resolved from {@code reporting}; this is no longer used. */
    @Deprecated
    public Integer getMaxDelaySeconds() {
      return maxDelaySeconds;
    }

    /** @deprecated the flush schedule is resolved from {@code reporting}; this is no longer used. */
    @Deprecated
    public DecisionLogs setMaxDelaySeconds(Integer maxDelaySeconds) {
      this.maxDelaySeconds = maxDelaySeconds;
      return this;
    }

    public String getResource() {
      return resource;
    }

    public DecisionLogs setResource(String resource) {
      this.resource = resource;
      return this;
    }

    public DecisionLogs setReporting(Config.ReportingConfig reporting) {
      this.reporting = reporting;
      return this;
    }

    /**
     * Log a decision event with full OPA-compatible fields.
     *
     * @param decisionId unique identifier for this decision
     * @param input the input document
     * @param result the policy decision result
     * @param path the policy path evaluated
     * @param requestedBy identity of the client that requested the decision (optional)
     * @param startTime timestamp when the decision was made (milliseconds since epoch, 0 = use
     *     current time)
     * @param metrics performance metrics for the decision (optional)
     * @param ndCacheValues non-deterministic cache values used during evaluation (optional)
     */
    public void logDecision(
        String decisionId,
        JsonNode input,
        JsonNode result,
        String path,
        String requestedBy,
        long startTime,
        Metrics metrics,
        Map<String, java.util.List<io.github.open_policy_agent.opa.rego.EvaluationContext.CacheCall>>
            ndCacheValues) {
      try {
        // Convert startTime (milliseconds since epoch) to Instant
        java.time.Instant timestamp = null;
        if (startTime > 0) {
          timestamp = java.time.Instant.ofEpochMilli(startTime);
        }
        ObjectNode event =
            buildDecisionEvent(
                decisionId, input, result, path, requestedBy, timestamp, metrics, ndCacheValues);

        // TODO: Apply drop decision policy if configured

        if (!applyMask(event)) {
          return; // masking failed: drop the event rather than log it unmasked, as OPA Go does
        }

        // Add to buffer (thread-safe)
        buffer.add(event);
        buffered.incrementAndGet();

        trimToLimit();
        if (buffered.get() >= bufferLimit() && System.nanoTime() - retryNotBeforeNanos >= 0) {
          flushInBackground();
        }

        // Log to console if enabled
        if (Boolean.TRUE.equals(console)) {
          logger.info("Decision: %s", event.toString());
        }

      } catch (Exception e) {
        logger.error("Failed to log decision: %s", e.getMessage());
      }
    }

    /**
     * Evaluate the configured {@code mask_decision} policy against the event and apply the
     * redactions it returns. The event is the policy's input, so rules address the decision's data
     * as {@code /input/...}, {@code /result/...} or {@code /nd_builtin_cache/...}.
     *
     * @param event the decision event to redact in place
     * @return true when the event may be logged, false when masking failed and it must be dropped
     */
    private boolean applyMask(ObjectNode event) {
      String entrypoint = maskEntrypoint;
      if (entrypoint == null) {
        return true;
      }

      try {
        Engine.PreparedQuery query = maskQuery(entrypoint);
        if (query == null) {
          return true; // no mask policy in the bundle
        }

        // The typed overload strips the {"result": <value>} envelope IR plans add.
        List<Object> results = query.eval(MAPPER.convertValue(event, Object.class), Object.class);
        if (results.isEmpty()) {
          return true; // mask rule undefined for this event
        }

        MaskRuleSet.parse(MAPPER.valueToTree(results.get(0))).apply(event);
        return true;
      } catch (Exception e) {
        logger.error("Log event masking failed: %s", describe(e));
        return false;
      }
    }

    // Prepared on first use and cached until a new bundle is activated (Go-OPA's prepareOnce).
    // Returns null when no plan holds the mask entrypoint. A preparation failure is cached too and
    // re-raised per event, so a broken mask policy drops events instead of logging them unmasked.
    private Engine.PreparedQuery maskQuery(String entrypoint) {
      synchronized (maskLock) {
        if (!maskQueryPrepared) {
          try {
            maskQuery = prepareMaskQuery(entrypoint);
          } catch (RuntimeException e) {
            maskQueryFailure = describe(e);
          }
          // Set only after the attempt yields a query or a cached failure: an Error leaves the
          // flag unset and propagates, rather than quietly disabling masking.
          maskQueryPrepared = true;
        }
        if (maskQueryFailure != null) {
          throw new IllegalStateException(maskQueryFailure);
        }
        return maskQuery;
      }
    }

    private Engine.PreparedQuery prepareMaskQuery(String entrypoint) {
      Store store = manager.getStore();
      Policy policy = store.getIrPolicyForEntrypoint(entrypoint);
      // getIrPolicyForEntrypoint falls back to the first policy it finds, so the plan itself has
      // to be looked up to tell "no mask policy" apart from "some other policy".
      if (policy == null
          || policy.getPlans() == null
          || policy.getPlans().getPlans() == null
          || policy.getPlans().getPlanByName(entrypoint) == null) {
        reportMissingMaskPolicy(entrypoint);
        return null;
      }

      return new Engine.Builder()
          .withStore(store)
          .withEntrypoint(entrypoint)
          .build()
          .prepareForEvaluation()
          .build();
    }

    // A mask policy not built as a plan entrypoint leaves masking silently inactive. The default
    // path is absent in most deployments, so only an explicitly configured one is worth a warning.
    private void reportMissingMaskPolicy(String entrypoint) {
      if (Config.DecisionLogsConfig.DEFAULT_MASK_DECISION.equals(entrypoint)) {
        logger.debug("No decision log mask policy found for entrypoint '%s'", entrypoint);
        return;
      }

      logger.warn(
          "Decision log masking is inactive: no plan for mask_decision entrypoint '%s'."
              + " Was the bundle built with 'opa build -t plan -e %s ...'?",
          entrypoint, entrypoint);
    }

    /** Discard the cached mask query so the next masked event re-prepares it. */
    void dropMaskQuery() {
      synchronized (maskLock) {
        maskQueryPrepared = false;
        maskQuery = null;
        maskQueryFailure = null;
      }
    }

    // Not every exception carries a message, so fall back to the type name rather than "null".
    private static String describe(Throwable t) {
      return t.getMessage() != null ? t.getMessage() : t.toString();
    }

    /**
     * Build a decision event JSON object per OPA specification.
     *
     * <p>Includes:
     *
     * <ul>
     *   <li>decision_id: Unique identifier
     *   <li>path: Policy path evaluated
     *   <li>input: Input document
     *   <li>result: Policy result
     *   <li>timestamp: RFC3339 timestamp
     *   <li>labels: Instance labels (from config)
     *   <li>bundles: Bundle revisions (from store)
     *   <li>requested_by: Client identity (optional)
     *   <li>metrics: Performance metrics (optional)
     *   <li>nd_builtin_cache: Non-deterministic cache values (optional)
     * </ul>
     */
    private ObjectNode buildDecisionEvent(
        String decisionId,
        JsonNode input,
        JsonNode result,
        String path,
        String requestedBy,
        Instant timestamp,
        Metrics metrics,
        Map<String, java.util.List<io.github.open_policy_agent.opa.rego.EvaluationContext.CacheCall>>
            ndCacheValues) {
      ObjectNode event = MAPPER.createObjectNode();

      // Required fields
      event.put("decision_id", decisionId);
      event.put("path", path);
      event.set("input", input);
      event.set("result", result);

      // Timestamp (RFC3339 format)
      Instant ts = (timestamp != null) ? timestamp : Instant.now();
      event.put("timestamp", DateTimeFormatter.ISO_INSTANT.format(ts));

      // Add labels from config (if present)
      final ObjectNode labels = MAPPER.createObjectNode();
      manager.getLabels().forEach(labels::put);
      event.set("labels", labels);

      // Add bundle revisions from store (if present)
      Map<String, Bundle> bundles = manager.getStore().getBundles();
      if (bundles != null && !bundles.isEmpty()) {
        ObjectNode bundlesNode = MAPPER.createObjectNode();
        for (Map.Entry<String, Bundle> entry : bundles.entrySet()) {
          ObjectNode bundleInfo = MAPPER.createObjectNode();
          if (entry.getValue().manifest != null
              && entry.getValue().manifest.containsKey("revision")) {
            bundleInfo.put("revision", String.valueOf(entry.getValue().manifest.get("revision")));
          }
          bundlesNode.set(entry.getKey(), bundleInfo);
        }
        if (!bundlesNode.isEmpty()) {
          event.set("bundles", bundlesNode);
        }
      }

      // Add requested_by (optional)
      if (requestedBy != null && !requestedBy.isEmpty()) {
        event.put("requested_by", requestedBy);
      }

      // Add metrics (optional)
      if (metrics != null) {
        ObjectNode metricsNode = MAPPER.createObjectNode();

        metrics.all().forEach((key, value) -> addMetric(metricsNode, key, value));
        event.set("metrics", metricsNode);
      }

      // Add nd_builtin_cache (optional)
      if (ndCacheValues != null && !ndCacheValues.isEmpty()) {
        ObjectNode cacheNode = MAPPER.createObjectNode();

        for (Map.Entry<
                String, java.util.List<io.github.open_policy_agent.opa.rego.EvaluationContext.CacheCall>>
            entry : ndCacheValues.entrySet()) {
          ArrayNode callsArray = MAPPER.createArrayNode();

          for (io.github.open_policy_agent.opa.rego.EvaluationContext.CacheCall call : entry.getValue()) {
            ObjectNode callNode = MAPPER.createObjectNode();

            // Serialize args array
            ArrayNode argsArray = MAPPER.createArrayNode();
            for (io.github.open_policy_agent.opa.ast.types.RegoValue arg : call.getArgs()) {
              argsArray.add(MAPPER.valueToTree(arg));
            }
            callNode.set("args", argsArray);

            // Serialize result
            callNode.set("result", MAPPER.valueToTree(call.getResult()));

            callsArray.add(callNode);
          }

          cacheNode.set(entry.getKey(), callsArray);
        }

        event.set("nd_builtin_cache", cacheNode);
      }

      return event;
    }

    /**
     * Add one metric to the event's {@code metrics} object. {@link Metrics#all()} supplies the
     * type-prefixed name OPA Go publishes (see {@code metrics.formatKey}): {@code timer_<name>_ns},
     * {@code counter_<name>} or {@code histogram_<name>}.
     *
     * <p>The three known types are written out field by field rather than handed to Jackson:
     * none of them exposes a Jackson-visible property, so default serialization throws and
     * {@code logDecision} drops the whole decision event rather than one metric. Any other
     * implementation still falls back to Jackson under its bare name, as Go's default branch does.
     */
    private static void addMetric(ObjectNode metricsNode, String key, Metrics.Metric metric) {
      if (metric instanceof Metrics.Timer) {
        metricsNode.put(key, ((Metrics.Timer) metric).value().toNanos());
      } else if (metric instanceof Metrics.Counter) {
        metricsNode.put(key, ((Metrics.Counter) metric).value());
      } else if (metric instanceof Metrics.Histogram) {
        Metrics.Histogram.Values values = ((Metrics.Histogram) metric).value();
        if (values == null) {
          return;
        }
        ObjectNode stats = metricsNode.putObject(key);
        stats.put("count", values.count);
        stats.put("min", values.min);
        stats.put("max", values.max);
        stats.put("mean", values.mean);
        stats.put("stddev", values.stddev);
        stats.put("median", values.median);
        if (values.percentiles != null) {
          values.percentiles.forEach(stats::put);
        }
      } else {
        metricsNode.set(key, MAPPER.valueToTree(metric));
      }
    }

    /**
     * Flush buffered decisions to service. Only an accepted upload empties the buffer: a batch the
     * service did not accept goes back to the front of the buffer for the next flush.
     */
    protected synchronized boolean flush() {
      if (buffer.isEmpty()) {
        return true;
      }

      final List<ObjectNode> batch = new ArrayList<>();
      ObjectNode event;
      while ((event = buffer.pollFirst()) != null) {
        batch.add(event);
      }
      buffered.addAndGet(-batch.size());

      int delivered = 0;
      try {
        delivered = service != null && !service.isEmpty() ? upload(batch) : batch.size();
      } catch (RuntimeException e) {
        logger.error("Failed to flush decision logs: %s", describe(e));
      }

      if (delivered < batch.size()) {
        final List<ObjectNode> undelivered = batch.subList(delivered, batch.size());
        for (int i = undelivered.size() - 1; i >= 0; i--) {
          buffer.addFirst(undelivered.get(i));
        }
        buffered.addAndGet(undelivered.size());
        trimToLimit();
        return false;
      }

      dropWarned.set(false);
      logger.debug("Flushed %d decision log events", batch.size());
      return true;
    }

    void setFlushExecutor(Executor flushExecutor) {
      this.flushExecutor = flushExecutor;
    }

    private void flushInBackground() {
      if (!flushQueued.compareAndSet(false, true)) {
        return;
      }
      try {
        flushExecutor.execute(
            () -> {
              flushQueued.set(false);
              flush();
            });
      } catch (RejectedExecutionException stopped) {
        flushQueued.set(false);
      }
    }

    int pending() {
      return buffered.get();
    }

    private int bufferLimit() {
      return reporting != null && reporting.getBufferSizeLimitEvents() != null
          ? reporting.getBufferSizeLimitEvents()
          : 10000;
    }

    /** Drops the oldest events beyond {@code buffer_size_limit_events}. */
    private void trimToLimit() {
      final int limit = bufferLimit();
      int dropped = 0;
      while (buffered.get() > limit && buffer.pollFirst() != null) {
        buffered.decrementAndGet();
        dropped++;
      }
      if (dropped > 0) {
        // Warn once per overflow; a service that stays down would otherwise log per decision.
        final String message =
            "Dropped %d oldest decision log events: buffer_size_limit_events (%d) reached";
        if (dropWarned.compareAndSet(false, true)) {
          logger.warn(message, dropped, limit);
        } else {
          logger.debug(message, dropped, limit);
        }
      }
    }

    /**
     * Uploads the batch in chunks of at most {@code upload_size_limit_bytes} and waits for each
     * answer. Returns how many events from the start of the batch were delivered; the rest are to
     * be kept for another try after a transport failure or a response that is not 2xx. A missing
     * service cannot be fixed by retrying, so that is logged and the whole batch counts as done.
     */
    private int upload(List<ObjectNode> batch) {
      final Plugin plugin = manager.getPlugin("services");
      if (!(plugin instanceof ServicePlugin)) {
        logger.error("ServicePlugin not found or not initialized");
        return batch.size();
      }

      final ServicePlugin.Service svc = ((ServicePlugin) plugin).getService(service);
      if (svc == null) {
        logger.error("Service '%s' not found for decision logs", service);
        return batch.size();
      }

      final String path = (resource != null && !resource.isEmpty()) ? resource : "/logs";
      final List<String> json = batch.stream().map(ObjectNode::toString).toList();
      int sent = 0;
      while (sent < json.size()) {
        final int end = chunkEnd(json, sent);
        final String body = "[" + String.join(",", json.subList(sent, end)) + "]";
        try {
          final int code = svc.postSync(path, body);
          if (code / 100 != 2) {
            uploadFailed(json.size() - sent, "status " + code);
            return sent;
          }
        } catch (IOException | RuntimeException e) {
          uploadFailed(json.size() - sent, String.valueOf(e.getMessage()));
          return sent;
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          uploadFailed(json.size() - sent, "interrupted");
          return sent;
        }
        logger.debug("Sent %d decision logs to service '%s'", end - sent, service);
        sent = end;
      }
      return sent;
    }

    /** The end of the chunk that starts at {@code start}: as many events as fit the size limit. */
    private int chunkEnd(List<String> json, int start) {
      final long limit =
          reporting != null
                  && reporting.getUploadSizeLimitBytes() != null
                  && reporting.getUploadSizeLimitBytes() > 0
              ? reporting.getUploadSizeLimitBytes()
              : DEFAULT_UPLOAD_SIZE_LIMIT_BYTES;
      long size = 2;
      int end = start;
      while (end < json.size()) {
        final long eventSize = json.get(end).getBytes(StandardCharsets.UTF_8).length + 1L;
        if (end > start && size + eventSize > limit) {
          break;
        }
        size += eventSize;
        end++;
      }
      return end;
    }

    private void uploadFailed(int events, String reason) {
      retryNotBeforeNanos = System.nanoTime() + RETRY_BACKOFF_NANOS;
      logger.warn(
          "Failed to upload %d decision log events to service '%s': %s; they stay buffered",
          events, service, reason);
    }
  }
}
