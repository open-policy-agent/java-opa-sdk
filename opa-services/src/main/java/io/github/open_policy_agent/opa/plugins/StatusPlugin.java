package io.github.open_policy_agent.opa.plugins;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
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

    return errors;
  }

  @Override
  public Plugin initialize(PluginManager manager) {
    StatusPlugin plugin = new StatusPlugin();
    plugin.manager = manager;
    plugin.scheduler = Executors.newScheduledThreadPool(1);

    Config.StatusConfig statusConfig = manager.getConfig().getStatus();
    if (statusConfig != null) {
      plugin.status =
          new Status(manager, manager.getLogger())
              .setConsole(statusConfig.getConsole())
              .setService(statusConfig.getService());
    }

    return plugin;
  }

  @Override
  public void start() {
    if (status == null) {
      manager.updatePluginStatus("status", PluginManager.Status.OK);
      return;
    }

    // Report status every 30 seconds (matches OPA default)
    scheduler.scheduleAtFixedRate(() -> status.reportStatus(), 0, 30, TimeUnit.SECONDS);

    manager.updatePluginStatus("status", PluginManager.Status.OK);
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

      // Add plugin statuses (only for registered plugins)
      ObjectNode plugins = MAPPER.createObjectNode();
      addPluginStatusIfRegistered(plugins, "bundles");
      addPluginStatusIfRegistered(plugins, "decision_logs");
      addPluginStatusIfRegistered(plugins, "status");
      addPluginStatusIfRegistered(plugins, "discovery");
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
        // Send status report to service
        // Default resource path is /status (same as OPA)
        svc.post("/status", statusReport.toString());
        logger.debug("Status report sent to service '%s'", service);
      } catch (Exception e) {
        logger.error("Failed to send status to service '%s': %s", service, e.getMessage());
      }
    }
  }
}
