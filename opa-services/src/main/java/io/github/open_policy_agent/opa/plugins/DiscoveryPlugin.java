package io.github.open_policy_agent.opa.plugins;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import io.github.open_policy_agent.opa.config.Config;

/**
 * Plugin that implements OPA's discovery protocol for dynamic configuration updates.
 *
 * <p>The discovery plugin downloads a special bundle containing OPA configuration (not policy). The
 * configuration from the discovery bundle is used to configure other plugins dynamically.
 *
 * <p>Key behaviors:
 *
 * <ul>
 *   <li>Discovery bundles must NOT contain .rego files (will be rejected with error)
 *   <li>Discovery bundles contain a data.json file with configuration
 *   <li>If initial discovery load fails, plugin status is ERROR and Opa is not ready
 *   <li>If discovery update fails but existing config exists, logs error and keeps old config
 *   <li>When new configuration is loaded, PluginManager.updateConfig() is called automatically
 *   <li>Other plugins pick up new configuration on their next polling cycle
 * </ul>
 */
public final class DiscoveryPlugin implements Plugin {

  private PluginManager manager;
  private ScheduledExecutorService scheduler;
  private DiscoveryBundle discoveryBundle;

  public DiscoveryPlugin() {}

  @Override
  public Set<String> validate(PluginManager manager) {
    Set<String> errors = new HashSet<>();

    Config.DiscoveryConfig discovery = manager.getConfig().getDiscovery();
    if (discovery == null) {
      return errors; // No discovery configured is valid
    }

    if (discovery.getService() == null || discovery.getService().isEmpty()) {
      errors.add("Discovery has missing or empty service reference");
    } else {
      // Verify service exists
      if (manager.getConfig().getService(discovery.getService()) == null) {
        errors.add("Discovery references non-existent service '" + discovery.getService() + "'");
      }
    }

    if (discovery.getResource() == null || discovery.getResource().isEmpty()) {
      errors.add("Discovery has missing or empty resource path");
    }

    errors.addAll(BundleDownloader.validatePolling(discovery.getPolling(), "Discovery"));

    return errors;
  }

  @Override
  public Plugin initialize(PluginManager manager) {
    DiscoveryPlugin plugin = new DiscoveryPlugin();
    plugin.manager = manager;
    plugin.scheduler = BundleDownloader.newPollScheduler("opa-discovery-scheduler");

    Config.DiscoveryConfig discoveryConfig = manager.getConfig().getDiscovery();
    if (discoveryConfig != null) {
      String name = discoveryConfig.getName() != null ? discoveryConfig.getName() : "discovery";

      ServicePlugin.Service svc = null;
      Plugin raw = manager.getPlugin("services");
      if (raw instanceof ServicePlugin) {
        svc = ((ServicePlugin) raw).getService(discoveryConfig.getService());
        // Validate caught the missing-service case earlier; assert defensively when the
        // ServicePlugin IS registered but the lookup fails — never silently fall back to the
        // default HTTP client and skip the configured TLS context.
        if (svc == null) {
          throw new PluginInitializationException(
                  "Discovery plugin: service '"
                      + discoveryConfig.getService()
                      + "' not found in ServicePlugin")
              .withContext("serviceName", discoveryConfig.getService());
        }
      }
      // raw==null path: tests that wire DiscoveryPlugin directly without a ServicePlugin.
      // svc stays null and BundleDownloader falls back to a default HTTP client.

      plugin.discoveryBundle =
          new DiscoveryBundle(name, manager, svc)
              .setService(discoveryConfig.getService())
              .setResource(discoveryConfig.getResource())
              .setPolling(discoveryConfig.getPolling());
    }

    return plugin;
  }

  @Override
  public void start() {
    if (discoveryBundle == null) {
      manager.updatePluginStatus("discovery", PluginManager.Status.OK);
      return;
    }

    CompletableFuture<Void> initialActivation = discoveryBundle.startPolling(scheduler);

    initialActivation
        .thenRun(
            () -> {
              manager.getLogger().info("Discovery bundle initially activated");
              manager.updatePluginStatus("discovery", PluginManager.Status.OK);
            })
        .exceptionally(
            ex -> {
              manager.getLogger().error("Discovery activation failed: %s", ex.getMessage());
              manager.updatePluginStatus("discovery", PluginManager.Status.ERROR);
              return null;
            });
  }

  @Override
  public void stop() {
    if (scheduler != null) {
      manager.getLogger().info("Stopping discovery plugin...");
      scheduler.shutdown();
      try {
        if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
          manager
              .getLogger()
              .warn("Discovery plugin scheduler did not terminate, forcing shutdown");
          scheduler.shutdownNow();
        }
      } catch (InterruptedException e) {
        manager.getLogger().warn("Interrupted while stopping discovery plugin");
        scheduler.shutdownNow();
        Thread.currentThread().interrupt();
      }
    }
  }

  /**
   * Internal class representing the discovery bundle.
   *
   * <p>Handles downloading, validation, and activation of discovery configuration.
   */
  private static class DiscoveryBundle extends BundleDownloader {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private Config discoveredConfig; // Store the last successfully loaded config

    private DiscoveryBundle(String name, PluginManager manager, ServicePlugin.Service service) {
      super(name, manager, service);
    }

    @Override
    public DiscoveryBundle setService(String service) {
      super.setService(service);
      return this;
    }

    @Override
    public DiscoveryBundle setResource(String resource) {
      super.setResource(resource);
      return this;
    }

    @Override
    public DiscoveryBundle setPolling(Config.PollingConfig polling) {
      super.setPolling(polling);
      return this;
    }

    @Override
    protected void activateBundle(byte[] bundleData) {
      try {
        // Parse discovery bundle
        DiscoveryBundleContent content = parseDiscoveryBundle(bundleData);

        // Validate: discovery bundles should not contain Rego
        if (content.hasRegoFiles) {
          String errorMsg =
              "Discovery bundle contains .rego files, which is not allowed. "
                  + "Discovery bundles should only contain configuration data.";
          manager.getLogger().error("Discovery '%s': %s", name, errorMsg);
          manager.updatePluginStatus("discovery", PluginManager.Status.WARN);

          // If this is initial activation, fail it
          if (!initialActivation.isDone()) {
            initialActivation.completeExceptionally(new RuntimeException(errorMsg));
          }
          return;
        }

        // Parse configuration from data.json
        if (content.configData == null) {
          String errorMsg = "Discovery bundle does not contain configuration data";
          manager.getLogger().error("Discovery '%s': %s", name, errorMsg);

          // If this is initial activation, fail it
          if (!initialActivation.isDone()) {
            initialActivation.completeExceptionally(new RuntimeException(errorMsg));
          }
          return;
        }

        // Store the successfully loaded config
        discoveredConfig = content.configData;

        manager.getLogger().info("Discovery '%s': Configuration loaded successfully", name);

        // Trigger configuration update in PluginManager
        // This will notify listeners and allow other plugins to pick up new config
        manager.updateConfig(discoveredConfig);

      } catch (Exception e) {
        String errorMsg = "Failed to activate discovery bundle: " + e.getMessage();
        manager.getLogger().error("Discovery '%s': %s", name, errorMsg);

        // If we have an existing config, keep using it (don't fail)
        if (discoveredConfig != null) {
          manager.getLogger().warn("Discovery '%s': Continuing with previous configuration", name);
        } else {
          // No existing config, fail initial activation
          if (!initialActivation.isDone()) {
            initialActivation.completeExceptionally(new RuntimeException(errorMsg));
          }
        }
      }
    }

    /**
     * Parse a discovery bundle and extract configuration data.
     *
     * @param bundleData raw tarball bytes
     * @return parsed bundle content with config and validation flags
     */
    private DiscoveryBundleContent parseDiscoveryBundle(byte[] bundleData) throws IOException {
      DiscoveryBundleContent content = new DiscoveryBundleContent();

      try (ByteArrayInputStream byteIn = new ByteArrayInputStream(bundleData);
          GZIPInputStream gzipIn = new GZIPInputStream(byteIn);
          TarArchiveInputStream tarIn = new TarArchiveInputStream(gzipIn, true)) {

        TarArchiveEntry entry;
        while ((entry = tarIn.getNextTarEntry()) != null) {
          String entryName =
              entry.getName().startsWith("/") ? entry.getName().substring(1) : entry.getName();

          if (!entry.isDirectory()) {
            if (entryName.endsWith(".rego")) {
              content.hasRegoFiles = true;
              manager
                  .getLogger()
                  .warn(
                      "Discovery '%s': Found .rego file '%s' in discovery bundle", name, entryName);
            } else if (entryName.equals("data.json")) {
              byte[] entryBytes = tarIn.readAllBytes();
              JsonNode dataNode = MAPPER.readTree(entryBytes);
              content.configData = MAPPER.treeToValue(dataNode, Config.class);
            } else if (entryName.equals(".manifest")) {
              // Discovery bundles can have manifests, but we don't need to process them
              manager.getLogger().debug("Discovery '%s': Found manifest in discovery bundle", name);
            }
          }
        }
      }

      return content;
    }
  }

  /** Container for discovery bundle parsing results. */
  private static class DiscoveryBundleContent {
    Config configData;
    boolean hasRegoFiles = false;
  }
}
