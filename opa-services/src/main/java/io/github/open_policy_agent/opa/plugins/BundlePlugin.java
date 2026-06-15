package io.github.open_policy_agent.opa.plugins;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import io.github.open_policy_agent.opa.bundle.TarballBundleLoader;
import io.github.open_policy_agent.opa.config.Config;

public final class BundlePlugin implements Plugin {

  private final Map<String, Bundle> bundles = new HashMap<>();
  private PluginManager manager;
  private ScheduledExecutorService scheduler;

  public BundlePlugin() {}

  public Set<String> validate(PluginManager manager) {
    Set<String> errors = new HashSet<>();

    if (manager.getConfig().getBundles() == null || manager.getConfig().getBundles().isEmpty()) {
      return errors; // No bundles configured is valid
    }

    for (Map.Entry<String, Config.BundleConfig> entry :
        manager.getConfig().getBundles().entrySet()) {
      String name = entry.getKey();
      Config.BundleConfig bundle = entry.getValue();

      if (name == null || name.isEmpty()) {
        errors.add("Bundle with missing or empty name");
      } else {
        if (bundle.getService() == null || bundle.getService().isEmpty()) {
          errors.add("Bundle '" + name + "' has missing or empty service reference");
        } else if (manager.getConfig().getService(bundle.getService()) == null) {
          // Fail fast on typos in the service name. Without this, a misconfigured bundle would
          // pass validate() and only surface on the first download attempt (as a log line).
          errors.add(
              "Bundle '" + name + "' references unknown service '" + bundle.getService() + "'");
        }
        if (bundle.getResource() == null || bundle.getResource().isEmpty()) {
          errors.add("Bundle '" + name + "' has missing or empty resource path");
        }
        errors.addAll(BundleDownloader.validatePolling(bundle.getPolling(), "Bundle '" + name + "'"));
      }
    }
    return errors;
  }

  public Plugin initialize(PluginManager manager) {
    BundlePlugin plugin = new BundlePlugin();
    plugin.manager = manager;
    plugin.scheduler = BundleDownloader.newPollScheduler("opa-bundle-scheduler");

    // ServicePlugin is initialized before BundlePlugin by Opa.Builder, so it's safe to look up
    // here. When absent (e.g. a test constructing BundlePlugin directly), fall back to a default
    // HttpClient; that path still supports file:// URIs and unauthenticated HTTP.
    ServicePlugin servicePlugin = null;
    Plugin raw = manager.getPlugin("services");
    if (raw instanceof ServicePlugin) {
      servicePlugin = (ServicePlugin) raw;
    }

    if (manager.getConfig().getBundles() != null) {
      for (Map.Entry<String, Config.BundleConfig> entry :
          manager.getConfig().getBundles().entrySet()) {
        String name = entry.getKey();
        Config.BundleConfig bundleConfig = entry.getValue();

        ServicePlugin.Service svc =
            servicePlugin == null ? null : servicePlugin.getService(bundleConfig.getService());

        plugin.bundles.put(
            name,
            new Bundle(name, manager, svc)
                .setService(bundleConfig.getService())
                .setResource(bundleConfig.getResource())
                .setPolling(bundleConfig.getPolling()));
      }
    }

    return plugin;
  }

  public void start() {
    if (bundles.isEmpty()) {
      manager.updatePluginStatus("bundles", PluginManager.Status.OK);
      return;
    }

    // Set initial status so wait logic knows plugin is registered
    manager.updatePluginStatus("bundles", PluginManager.Status.NOT_READY);

    // Collect all initial activation futures
    CompletableFuture<?>[] activationFutures =
        bundles.values().stream()
            .map(bundle -> bundle.startPolling(scheduler))
            .toArray(CompletableFuture[]::new);

    // Wait for all bundles to complete initial activation
    CompletableFuture.allOf(activationFutures)
        .thenRun(
            () -> {
              manager.getLogger().info("All bundles initially activated");
              manager.updatePluginStatus("bundles", PluginManager.Status.OK);
            })
        .exceptionally(
            ex -> {
              manager.getLogger().error("Bundle activation failed: %s", ex.getMessage());
              manager.updatePluginStatus("bundles", PluginManager.Status.ERROR);
              return null;
            });
  }

  /** Get a bundle by name (primarily for tests and status reporting). */
  public Bundle getBundle(String name) {
    return bundles.get(name);
  }

  @Override
  public void stop() {
    if (scheduler != null) {
      manager.getLogger().info("Stopping bundles plugin...");
      scheduler.shutdown();
      try {
        if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
          manager.getLogger().warn("Bundles plugin scheduler did not terminate, forcing shutdown");
          scheduler.shutdownNow();
        }
      } catch (InterruptedException e) {
        manager.getLogger().warn("Interrupted while stopping bundles plugin");
        scheduler.shutdownNow();
        Thread.currentThread().interrupt();
      }
    }
  }

  /** Bundle downloader that activates policy and data bundles. */
  public static class Bundle extends BundleDownloader {

    private Bundle(String name, PluginManager manager, ServicePlugin.Service authService) {
      super(name, manager, authService);
    }

    public String getName() {
      return name;
    }

    @Override
    public Bundle setService(String service) {
      super.setService(service);
      return this;
    }

    public String getService() {
      return service;
    }

    @Override
    public Bundle setResource(String resource) {
      super.setResource(resource);
      return this;
    }

    public String getResource() {
      return resource;
    }

    @Override
    public Bundle setPolling(Config.PollingConfig polling) {
      super.setPolling(polling);
      return this;
    }

    public Config.PollingConfig getPolling() {
      return polling;
    }

    @Override
    protected void activateBundle(byte[] bundleData) {
      try {
        // Load bundle using TarballBundleLoader
        TarballBundleLoader loader = new TarballBundleLoader(name, bundleData);
        loader.load(manager.getStore());

        manager.getLogger().info("Bundle '%s': Activated", name);
        manager.notifyBundleActivation(name);

      } catch (Exception e) {
        manager.getLogger().error("Bundle '%s': Activation failed: %s", name, e.getMessage());
        throw new RuntimeException(e); // Re-throw to fail initial activation
      }
    }
  }
}
