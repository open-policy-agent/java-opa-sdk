package io.github.open_policy_agent.opa.plugins;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import io.github.open_policy_agent.opa.bundle.Bundle;
import io.github.open_policy_agent.opa.config.Config;
import io.github.open_policy_agent.opa.ir.policy.Policy;
import io.github.open_policy_agent.opa.logging.Logger;
import io.github.open_policy_agent.opa.metrics.Metrics;
import io.github.open_policy_agent.opa.rego.Engine;
import io.github.open_policy_agent.opa.storage.Store;

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

  private DecisionLogs decisionLogs;
  private PluginManager manager;
  private ScheduledExecutorService scheduler;
  private PluginManager.BundleActivationListener maskListener;

  public DecisionLogPlugin() {}

  @Override
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
    int effectiveMin = configuredMin != null ? configuredMin : 300;
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

    // Validate reporting config if present (same defaulting as above when used)
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
      if (rMin != null || rMax != null) {
        int rEffectiveMin = rMin != null ? rMin : 300;
        int rEffectiveMax = rMax != null ? rMax : rEffectiveMin * 2;
        if (rEffectiveMin > rEffectiveMax) {
          errors.add(
              "Decision logs reporting min_delay_seconds ("
                  + rEffectiveMin
                  + ") cannot be greater than max_delay_seconds ("
                  + rEffectiveMax
                  + ")");
        }
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
              .setMinDelaySeconds(logsConfig.getMinDelaySeconds())
              .setMaxDelaySeconds(logsConfig.getMaxDelaySeconds())
              .setResource(logsConfig.getResource())
              .setReporting(logsConfig.getReporting());

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

    // Get upload interval bounds (default: 300-600 seconds, matching OPA Go's decision log
    // defaults). If only a min is configured, default the max to twice the min so the jitter
    // window stays sensible.
    int minDelaySeconds =
        (decisionLogs.getMinDelaySeconds() != null) ? decisionLogs.getMinDelaySeconds() : 300;
    int maxDelaySeconds =
        (decisionLogs.getMaxDelaySeconds() != null)
            ? decisionLogs.getMaxDelaySeconds()
            : minDelaySeconds * 2;

    scheduleNextFlush(minDelaySeconds, maxDelaySeconds);

    manager.updatePluginStatus("decision_logs", PluginManager.Status.OK);
  }

  // Re-schedules the next flush with a uniformly random delay in [minDelay, maxDelay], matching
  // Go-OPA's jittered reporting interval (mirrors BundleDownloader.scheduleNextPoll).
  // ScheduledExecutorService has no built-in jitter, so the task chains itself.
  // RejectedExecutionException after a shutdown breaks the chain cleanly.
  private void scheduleNextFlush(int minDelay, int maxDelay) {
    long delay =
        minDelay >= maxDelay
            ? minDelay
            : ThreadLocalRandom.current().nextLong(minDelay, (long) maxDelay + 1);
    try {
      scheduler.schedule(
          () -> {
            try {
              decisionLogs.flush();
            } catch (Exception e) {
              // flush() handles its own logging; swallow so the chain keeps flushing. Only
              // Exception is caught here — Errors (OOM, etc.) propagate and let the executor's
              // uncaught-exception handler tear down the pool, which is the right outcome for
              // unrecoverable conditions.
            } finally {
              scheduleNextFlush(minDelay, maxDelay);
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
    private static final ObjectMapper MAPPER = new ObjectMapper();

    static {
      MAPPER.registerModule(new JavaTimeModule());
    }

    private final Logger logger;
    private final PluginManager manager;
    private final ConcurrentLinkedQueue<ObjectNode> buffer = new ConcurrentLinkedQueue<>();
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

    public Integer getMinDelaySeconds() {
      return minDelaySeconds;
    }

    public DecisionLogs setMinDelaySeconds(Integer minDelaySeconds) {
      this.minDelaySeconds = minDelaySeconds;
      return this;
    }

    public Integer getMaxDelaySeconds() {
      return maxDelaySeconds;
    }

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

        // Check buffer limits
        int bufferLimit =
            (reporting != null && reporting.getBufferSizeLimitEvents() != null)
                ? reporting.getBufferSizeLimitEvents()
                : 10000;

        if (buffer.size() >= bufferLimit) {
          flush();
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
      if (manager.getConfig().getLabels() != null && !manager.getConfig().getLabels().isEmpty()) {
        ObjectNode labels = MAPPER.createObjectNode();
        manager.getConfig().getLabels().forEach(labels::put);
        event.set("labels", labels);
      }

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

        metrics
            .all()
            .forEach(
                (key, value) -> {
                  // Convert Timer metrics to nanoseconds with proper naming
                  if (value instanceof Metrics.Timer) {
                    Metrics.Timer timer = (Metrics.Timer) value;
                    long nanos = timer.value().toNanos();
                    String metricName = "timer_" + key + "_ns";
                    metricsNode.put(metricName, nanos);
                  } else {
                    // For other metric types, use default serialization
                    metricsNode.set(key, MAPPER.valueToTree(value));
                  }
                });
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

    /** Flush buffered decisions to service. */
    protected void flush() {
      if (buffer.isEmpty()) {
        return;
      }

      try {
        // Create batch from queue (drain to avoid holding references)
        ArrayNode batch = MAPPER.createArrayNode();
        ObjectNode event;
        while ((event = buffer.poll()) != null) {
          batch.add(event);
        }

        if (batch.isEmpty()) {
          return; // Another thread already flushed
        }

        // Send to service if configured
        if (service != null && !service.isEmpty()) {
          sendToService(batch);
        }

        logger.debug("Flushed %d decision log events", batch.size());

      } catch (Exception e) {
        logger.error("Failed to flush decision logs: %s", e.getMessage());
      }
    }

    /** Send decision logs batch to configured service. */
    private void sendToService(ArrayNode batch) {
      // Get ServicePlugin from manager
      Plugin plugin = manager.getPlugin("services");
      if (!(plugin instanceof ServicePlugin)) {
        logger.error("ServicePlugin not found or not initialized");
        return;
      }

      ServicePlugin servicePlugin = (ServicePlugin) plugin;
      ServicePlugin.Service svc = servicePlugin.getService(service);

      if (svc == null) {
        logger.error("Service '%s' not found for decision logs", service);
        return;
      }

      try {
        // Determine resource path (default: /logs)
        String path = (resource != null && !resource.isEmpty()) ? resource : "/logs";

        // Send batch to service
        svc.post(path, batch.toString());
        logger.debug("Sent %d decision logs to service '%s'", batch.size(), service);
      } catch (Exception e) {
        logger.error("Failed to send decision logs to service '%s': %s", service, e.getMessage());
      }
    }
  }
}
