package io.github.open_policy_agent.opa.plugins;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import io.github.open_policy_agent.opa.bundle.Bundle;
import io.github.open_policy_agent.opa.config.Config;
import io.github.open_policy_agent.opa.logging.Logger;

/**
 * Plugin that reports OPA runtime status periodically.
 *
 * <p>StatusPlugin collects status information about the OPA instance and reports it to a configured
 * service or console. Status includes:
 *
 * <ul>
 *   <li>Plugin statuses (bundles, decision_logs, discovery, etc.)
 *   <li>Bundle information (names and revisions)
 *   <li>Labels (instance metadata)
 * </ul>
 */
public final class StatusPlugin implements Plugin {

  private Status status;
  private PluginManager manager;
  private ScheduledExecutorService scheduler;

  public StatusPlugin() {}

  @Override
  public Set<String> validate(PluginManager manager) {
    Set<String> errors = new HashSet<>();

    Config.StatusConfig statusConfig = manager.getConfig().getStatus();
    if (statusConfig == null) {
      return errors; // No status config is valid
    }

    // If service is specified, validate it exists
    if (statusConfig.getService() != null && !statusConfig.getService().isEmpty()) {
      if (manager.getConfig().getService(statusConfig.getService()) == null) {
        errors.add("Status references non-existent service '" + statusConfig.getService() + "'");
      }
    }

    // Validate delay settings (mirrors BundleDownloader.validatePolling)
    Integer min = statusConfig.getMinDelaySeconds();
    Integer max = statusConfig.getMaxDelaySeconds();
    if (min != null && min < 0) {
      errors.add("Status min_delay_seconds must be >= 0");
    }
    if (max != null && max < 0) {
      errors.add("Status max_delay_seconds must be >= 0");
    }
    if (min != null && max != null && min >= 0 && max >= 0 && min > max) {
      errors.add(
          "Status min_delay_seconds ("
              + min
              + ") cannot be greater than max_delay_seconds ("
              + max
              + ")");
    }

    return errors;
  }

  @Override
  public Plugin initialize(PluginManager manager) {
    StatusPlugin plugin = new StatusPlugin();
    plugin.manager = manager;
    plugin.scheduler = BundleDownloader.newPollScheduler("opa-status-scheduler");

    Config.StatusConfig statusConfig = manager.getConfig().getStatus();
    if (statusConfig != null) {
      plugin.status =
          new Status(manager, manager.getLogger())
              .setConsole(statusConfig.getConsole())
              .setService(statusConfig.getService())
              .setResource(statusConfig.getResource())
              .setMinDelaySeconds(statusConfig.getMinDelaySeconds())
              .setMaxDelaySeconds(statusConfig.getMaxDelaySeconds());
    }

    return plugin;
  }

  @Override
  public void start() {
    if (status == null) {
      manager.updatePluginStatus("status", PluginManager.Status.OK);
      return;
    }

    // Get report interval bounds (default: 30 seconds, matching OPA's previous fixed interval;
    // OPA Go's status plugin has no standalone min/max delay of its own today - reports are
    // triggered by the bundle/discovery plugin's polling - so there's no upstream number to
    // mirror here beyond the interval this SDK already used).
    // - only min set → max defaults to 2 * min
    // - only max set → min defaults to min(30, max) so a max below 30 is not silently ignored
    // - neither set → min=30, max=60
    Integer configuredMin = status.getMinDelaySeconds();
    Integer configuredMax = status.getMaxDelaySeconds();
    int minDelaySeconds;
    int maxDelaySeconds;
    if (configuredMin != null && configuredMax != null) {
      minDelaySeconds = configuredMin;
      maxDelaySeconds = configuredMax;
    } else if (configuredMin != null) {
      minDelaySeconds = configuredMin;
      maxDelaySeconds = configuredMin * 2;
    } else if (configuredMax != null) {
      maxDelaySeconds = configuredMax;
      minDelaySeconds = Math.min(30, configuredMax);
    } else {
      minDelaySeconds = 30;
      maxDelaySeconds = 60;
    }

    // Report immediately on startup (matches previous behavior), then continue with a jittered
    // chained schedule for subsequent reports - mirrors BundleDownloader.startPolling(), which
    // downloads immediately before starting its own chained poll.
    scheduler.schedule(() -> status.reportStatus(), 0, TimeUnit.SECONDS);
    scheduleNextReport(minDelaySeconds, maxDelaySeconds);

    manager.updatePluginStatus("status", PluginManager.Status.OK);
  }

  // Re-schedules the next status report with a uniformly random delay in [minDelay, maxDelay],
  // mirroring BundleDownloader.scheduleNextPoll and DecisionLogPlugin.scheduleNextFlush.
  // ScheduledExecutorService has no built-in jitter, so the task chains itself.
  // RejectedExecutionException after a shutdown breaks the chain cleanly.
  private void scheduleNextReport(int minDelay, int maxDelay) {
    long delay =
        minDelay >= maxDelay
            ? minDelay
            : ThreadLocalRandom.current().nextLong(minDelay, (long) maxDelay + 1);
    try {
      scheduler.schedule(
          () -> {
            try {
              status.reportStatus();
            } catch (Exception e) {
              // reportStatus() handles its own logging; swallow so the chain keeps reporting.
              // Only Exception is caught here — Errors (OOM, etc.) propagate and let the
              // executor's uncaught-exception handler tear down the pool, which is the right
              // outcome for unrecoverable conditions.
            } finally {
              scheduleNextReport(minDelay, maxDelay);
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
    if (scheduler != null) {
      manager.getLogger().info("Stopping status plugin...");
      scheduler.shutdown();
      try {
        if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
          manager.getLogger().warn("Status plugin scheduler did not terminate, forcing shutdown");
          scheduler.shutdownNow();
        }
      } catch (InterruptedException e) {
        manager.getLogger().warn("Interrupted while stopping status plugin");
        scheduler.shutdownNow();
        Thread.currentThread().interrupt();
      }
    }
  }

  /** Status reporter that collects and sends status information. */
  public static class Status {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final PluginManager manager;
    private final Logger logger;
    private Boolean console;
    private String service;
    private String resource;
    private Integer minDelaySeconds;
    private Integer maxDelaySeconds;

    private Status(PluginManager manager, Logger logger) {
      this.manager = manager;
      this.logger = logger;
    }

    public Boolean getConsole() {
      return console;
    }

    public Status setConsole(Boolean console) {
      this.console = console;
      return this;
    }

    public String getService() {
      return service;
    }

    public Status setService(String service) {
      this.service = service;
      return this;
    }

    public String getResource() {
      return resource;
    }

    public Status setResource(String resource) {
      this.resource = resource;
      return this;
    }

    public Integer getMinDelaySeconds() {
      return minDelaySeconds;
    }

    public Status setMinDelaySeconds(Integer minDelaySeconds) {
      this.minDelaySeconds = minDelaySeconds;
      return this;
    }

    public Integer getMaxDelaySeconds() {
      return maxDelaySeconds;
    }

    public Status setMaxDelaySeconds(Integer maxDelaySeconds) {
      this.maxDelaySeconds = maxDelaySeconds;
      return this;
    }

    /** Collect and report current status. */
    void reportStatus() {
      try {
        ObjectNode statusReport = buildStatusReport();

        // Log to console if enabled
        if (Boolean.TRUE.equals(console)) {
          logger.info("Status: %s", statusReport.toString());
        }

        // Send to service if configured
        if (service != null && !service.isEmpty()) {
          sendToService(statusReport);
        }

      } catch (Exception e) {
        logger.error("Failed to report status: %s", e.getMessage());
      }
    }

    /** Build status report JSON. */
    private ObjectNode buildStatusReport() {
      ObjectNode report = MAPPER.createObjectNode();

      // Add instance ID
      if (manager.getId() != null) {
        report.put("id", manager.getId());
      }

      // Add labels
      if (manager.getConfig().getLabels() != null) {
        ObjectNode labels = MAPPER.createObjectNode();
        manager.getConfig().getLabels().forEach(labels::put);
        report.set("labels", labels);
      }

      // Add bundle information
      ObjectNode bundles = MAPPER.createObjectNode();
      Map<String, Bundle> storeBundles = manager.getStore().getBundles();
      if (storeBundles != null) {
        for (Map.Entry<String, Bundle> entry : storeBundles.entrySet()) {
          ObjectNode bundleInfo = MAPPER.createObjectNode();
          if (entry.getValue().manifest != null
              && entry.getValue().manifest.containsKey("revision")) {
            bundleInfo.put("revision", String.valueOf(entry.getValue().manifest.get("revision")));
          }
          bundleInfo.put("active", true);
          bundles.set(entry.getKey(), bundleInfo);
        }
      }
      report.set("bundles", bundles);

      // Add plugin statuses (only for registered plugins). The "services" plugin holds REST
      // service clients rather than a status-reporting plugin, so it is excluded to match the
      // previous behavior and Go OPA, where it does not appear in the status report.
      ObjectNode plugins = MAPPER.createObjectNode();
      for (String pluginName : manager.getPluginNames()) {
        if ("services".equals(pluginName)) {
          continue;
        }
        addPluginStatusIfRegistered(plugins, pluginName);
      }
      report.set("plugins", plugins);

      return report;
    }

    /** Add a plugin status only if the plugin is registered (non-null status). */
    private void addPluginStatusIfRegistered(ObjectNode plugins, String pluginName) {
      PluginManager.Status status = manager.getPluginStatus(pluginName);
      if (status != null) {
        plugins.put(pluginName, status.toString());
      }
      // If status is null, plugin is not registered - don't add to report
    }

    /** Send status report to configured service. */
    private void sendToService(ObjectNode statusReport) {
      // Get ServicePlugin from manager
      Plugin plugin = manager.getPlugin("services");
      if (!(plugin instanceof ServicePlugin)) {
        logger.error("ServicePlugin not found or not initialized");
        return;
      }

      ServicePlugin servicePlugin = (ServicePlugin) plugin;
      ServicePlugin.Service svc = servicePlugin.getService(service);

      if (svc == null) {
        logger.error("Service '%s' not found for status reporting", service);
        return;
      }

      try {
        // Determine resource path (default: /status)
        String path = (resource != null && !resource.isEmpty()) ? resource : "/status";

        svc.post(path, statusReport.toString());
        logger.debug("Status report sent to service '%s'", service);
      } catch (Exception e) {
        logger.error("Failed to send status to service '%s': %s", service, e.getMessage());
      }
    }
  }
}
