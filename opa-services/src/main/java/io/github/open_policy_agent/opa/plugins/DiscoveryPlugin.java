package io.github.open_policy_agent.opa.plugins;

import io.github.open_policy_agent.opa.config.Config;
import io.github.open_policy_agent.opa.config.EnvInterpolator;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

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
 *   <li>Discovery bundles contain a data.json file with configuration, read from the node named by
 *       {@code decision} (else {@code name}, else the data root)
 *   <li>The discovered configuration is merged under the boot configuration (see {@link
 *       DiscoveryMerge}); the boot {@code discovery} block, services and labels always win
 *   <li>If initial discovery load fails, plugin status is ERROR and Opa is not ready
 *   <li>If discovery update fails but existing config exists, logs error and keeps old config
 *   <li>Each successful load calls {@link PluginManager#updateConfig(Config)}. Plugins that read
 *       their config when initialized only see what discovery produced before they started
 * </ul>
 */
public final class DiscoveryPlugin implements Plugin {

  private static final ObjectMapper MAPPER = JsonMapper.shared();
  private static final Set<String> SUPPORTED_KEYS =
      Set.of("name", "decision", "prefix", "service", "resource", "polling");

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

    final String service = resolveService(manager.getConfig());
    if (service == null) {
      final Map<String, Config.ServiceConfig> services = manager.getConfig().getServices();
      errors.add(
          "Discovery has missing or empty service reference"
              + (services != null && services.size() > 1 ? ": more than one service is defined" : ""));
    } else if (manager.getConfig().getService(service) == null) {
      errors.add("Discovery references non-existent service '" + service + "'");
    }

    if (resolveResource(discovery) == null) {
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
      warnOnUnsupportedKeys(manager);
      String name = discoveryConfig.getName() != null ? discoveryConfig.getName() : "discovery";

      final String service = resolveService(manager.getConfig());
      ServicePlugin.Service svc = null;
      Plugin raw = manager.getPlugin("services");
      if (raw instanceof ServicePlugin) {
        svc = ((ServicePlugin) raw).getService(service);
        // Validate caught the missing-service case earlier; assert defensively when the
        // ServicePlugin IS registered but the lookup fails — never silently fall back to the
        // default HTTP client and skip the configured TLS context.
        if (svc == null) {
          throw new PluginInitializationException(
                  "Discovery plugin: service '" + service + "' not found in ServicePlugin")
              .withContext("serviceName", service);
        }
      }
      // raw==null path: tests that wire DiscoveryPlugin directly without a ServicePlugin.
      // svc stays null and BundleDownloader falls back to a default HTTP client.

      plugin.discoveryBundle =
          new DiscoveryBundle(name, manager, svc, configPath(discoveryConfig))
              .setService(service)
              .setResource(resolveResource(discoveryConfig))
              .setPolling(discoveryConfig.getPolling());
    }

    return plugin;
  }

  /**
   * Selects the config node by OPA's rule and substitutes {@code ${VAR}} in its JSON text, escaping
   * the values so they cannot change the document's structure (undefined variables stay as written).
   */
  static JsonNode selectConfig(JsonNode data, List<String> path, Map<String, String> env) {
    JsonNode node = data;
    for (String segment : path) {
      node = node.path(segment);
    }
    if (node.isMissingNode()) {
      throw new IllegalStateException("undefined configuration");
    }
    return MAPPER.readTree(
        EnvInterpolator.interpolateLenient(
            MAPPER.writeValueAsString(node), name -> jsonEscaped(env.get(name))));
  }

  private static String jsonEscaped(String value) {
    if (value == null) {
      return null;
    }
    final String quoted = MAPPER.writeValueAsString(value);
    return quoted.substring(1, quoted.length() - 1);
  }

  private static void warnOnUnsupportedKeys(PluginManager manager) {
    for (String key : manager.getBootConfigTree().path("discovery").propertyNames()) {
      if (!SUPPORTED_KEYS.contains(key)) {
        manager.getLogger().warn("Discovery: ignoring unsupported configuration key '%s'", key);
      }
    }
  }

  private static String resolveService(Config config) {
    final String configured = config.getDiscovery().getService();
    if (configured != null && !configured.isEmpty()) {
      return configured;
    }
    final Map<String, Config.ServiceConfig> services = config.getServices();
    return services != null && services.size() == 1 ? services.keySet().iterator().next() : null;
  }

  private static String resolveResource(Config.DiscoveryConfig discovery) {
    if (discovery.getResource() != null && !discovery.getResource().isEmpty()) {
      return discovery.getResource();
    }
    final String name = trimSlashes(discovery.getName());
    final String prefix = discovery.getPrefix() != null ? discovery.getPrefix() : "bundles";
    return name.isEmpty() ? null : trimSlashes(prefix) + "/" + name;
  }

  private static List<String> configPath(Config.DiscoveryConfig discovery) {
    final String path =
        discovery.getDecision() != null ? discovery.getDecision() : discovery.getName();
    final String trimmed = trimSlashes(path);
    return trimmed.isEmpty() ? List.of() : List.of(trimmed.split("/"));
  }

  private static String trimSlashes(String path) {
    return path == null ? "" : path.replaceAll("^/+|/+$", "");
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
    private final List<String> configPath;
    private Config discoveredConfig; // Store the last successfully loaded config
    private JsonNode lastMergedTree;

    private DiscoveryBundle(
        String name, PluginManager manager, ServicePlugin.Service service, List<String> configPath) {
      super(name, "discovery", manager, service);
      manager.setDiscoveryStatus(status);
      this.configPath = configPath;
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
        DiscoveryBundleContent content = parseDiscoveryBundle(bundleData);

        // Validate: discovery bundles should not contain Rego
        if (content.hasRegoFiles) {
          manager.updatePluginStatus("discovery", PluginManager.Status.WARN);
          throw new IllegalStateException(
              "Discovery bundle contains .rego files, which is not allowed. "
                  + "Discovery bundles should only contain configuration data.");
        }
        if (content.data == null) {
          throw new IllegalStateException("Discovery bundle does not contain configuration data");
        }
        status.downloaded(bundleData.length);

        final JsonNode selected = selectConfig(content.data, configPath, System.getenv());
        final DiscoveryMerge.Result merged =
            DiscoveryMerge.merge(manager.getBootConfigTree(), selected, service);
        final String overridden =
            merged.overriddenKeys().isEmpty()
                ? null
                : "Keys in the discovered configuration overridden by boot configuration: "
                    + String.join(", ", merged.overriddenKeys());

        if (lastMergedTree != null && DiscoveryMerge.sameJson(lastMergedTree, merged.tree())) {
          manager.getLogger().debug("Discovery '%s': Configuration unchanged", name);
        } else {
          final Config config = MAPPER.treeToValue(merged.tree(), Config.class);
          keepBootServices(config, merged.unchangedServices());
          manager.updateConfig(config);
          discoveredConfig = config;
          lastMergedTree = merged.tree();
          manager.getLogger().info("Discovery '%s': Configuration loaded successfully", name);
          if (overridden != null) {
            manager.getLogger().debug("Discovery '%s': %s", name, overridden);
          }
        }
        status.activated(content.revision);
        status.setMessage(overridden);

      } catch (Exception e) {
        manager.getLogger().error("Discovery '%s': %s", name, "Failed to activate discovery bundle: " + e.getMessage());
        if (discoveredConfig != null) {
          manager.getLogger().warn("Discovery '%s': Continuing with previous configuration", name);
        }
        // The downloader records the failure and fails the initial activation if there is none yet.
        throw e instanceof RuntimeException ? (RuntimeException) e : new RuntimeException(e);
      }
    }

    /**
     * Services that discovery leaves as they are come back from the tree as plain config, which
     * loses what a tree cannot hold ({@code SSLContext}, key stores), so those are the boot config's.
     */
    private void keepBootServices(Config config, Set<String> unchangedServices) {
      final Map<String, Config.ServiceConfig> bootServices = manager.getBootConfig().getServices();
      if (bootServices == null || config.getServices() == null) {
        return;
      }
      for (String name : unchangedServices) {
        if (bootServices.containsKey(name) && config.getServices().containsKey(name)) {
          config.getServices().put(name, bootServices.get(name));
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
              content.data = MAPPER.readTree(tarIn.readAllBytes());
            } else if (entryName.equals(".manifest")) {
              content.revision = MAPPER.readTree(tarIn.readAllBytes()).path("revision").asString("");
            }
          }
        }
      }

      return content;
    }
  }

  /** Container for discovery bundle parsing results. */
  private static class DiscoveryBundleContent {
    JsonNode data;
    String revision = "";
    boolean hasRegoFiles = false;
  }
}
