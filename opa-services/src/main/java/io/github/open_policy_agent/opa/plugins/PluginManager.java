package io.github.open_policy_agent.opa.plugins;

import io.github.open_policy_agent.opa.config.Config;
import io.github.open_policy_agent.opa.logging.Logger;
import io.github.open_policy_agent.opa.storage.Store;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class PluginManager {

  private final String id;
  private final Store store;
  private final Logger logger;
  private final List<ConfigUpdateListener> configUpdateListeners = new CopyOnWriteArrayList<>();
  private final Map<String, Status> status = new ConcurrentHashMap<>();
  private final Map<String, Plugin> plugins = new ConcurrentHashMap<>();
  private final List<PluginStatusListener> statusListeners = new CopyOnWriteArrayList<>();
  private final List<BundleActivationListener> bundleActivationListeners = new CopyOnWriteArrayList<>();
  private Config config;

  private PluginManager(Builder builder) {
    this.config = builder.config;
    this.id = builder.id;
    this.store = builder.store;
    this.logger = builder.logger;
  }

  public void updatePluginStatus(String plugin, Status status) {
    this.status.put(plugin, status);
    notifyStatusListeners(plugin, status);
  }

  public Status getPluginStatus(String plugin) {
    return this.status.get(plugin);
  }

  public synchronized boolean allPluginsOk() {
    // Check that all registered plugins have OK status
    if (plugins.isEmpty()) {
      return true; // No plugins means all are OK
    }

    return plugins.keySet().stream().allMatch(pluginName -> status.get(pluginName) == Status.OK);
  }

  public synchronized void registerPluginStatusListener(PluginStatusListener listener) {
    statusListeners.add(listener);
    // do an initial send of the current status
    for (String key : plugins.keySet()) {
      listener.onStatusChange(key, status.get(key));
    }
  }

  private void notifyStatusListeners(String pluginName, Status status) {
    // CopyOnWriteArrayList is thread-safe for iteration, no defensive copy needed
    for (PluginStatusListener listener : statusListeners) {
      listener.onStatusChange(pluginName, status);
    }
  }

  public void registerPlugin(String name, Plugin plugin) {
    plugins.put(name, plugin);
  }

  public Plugin getPlugin(String name) {
    return plugins.get(name);
  }

  public Set<String> getPluginNames() {
    return Collections.unmodifiableSet(new HashSet<>(plugins.keySet()));
  }

  public String getId() {
    return id;
  }

  public Config getConfig() {
    return config;
  }

  public Store getStore() {
    return store;
  }

  public Logger getLogger() {
    return logger;
  }

  public void deregisterPluginStatusListener(PluginStatusListener listener) {
    statusListeners.remove(listener);
  }

  /**
   * Stop all registered plugins in reverse order.
   *
   * <p>This method should be called during shutdown to cleanly stop all plugins and release
   * resources like scheduler executors.
   */
  public void stopAllPlugins() {
    // Create a list of plugin names to avoid modification during iteration
    List<String> pluginNames = new ArrayList<>(plugins.keySet());

    // Stop plugins in reverse order (opposite of start order)
    for (int i = pluginNames.size() - 1; i >= 0; i--) {
      String pluginName = pluginNames.get(i);
      Plugin plugin = plugins.get(pluginName);
      if (plugin != null) {
        try {
          plugin.stop();
        } catch (Exception e) {
          logger.error("Error stopping plugin '%s': %s", pluginName, e.getMessage());
        }
      }
    }
  }

  /**
   * Update the configuration used by plugins.
   *
   * <p>This method is called when discovery loads new configuration. It updates the internal config
   * reference and notifies all registered config update listeners.
   *
   * <p>Note: This does NOT automatically restart plugins. Plugins will pick up the new
   * configuration on their next polling cycle or when they explicitly check the config.
   *
   * @param newConfig the new configuration to use
   */
  public synchronized void updateConfig(Config newConfig) {
    Config oldConfig = this.config;
    this.config = newConfig;
    logger.info("Configuration updated by discovery");
    notifyConfigUpdateListeners(oldConfig, newConfig);
  }

  /**
   * Register a listener to be notified when configuration is updated.
   *
   * @param listener the listener to register
   */
  public void registerConfigUpdateListener(ConfigUpdateListener listener) {
    configUpdateListeners.add(listener);
  }

  /**
   * Deregister a config update listener.
   *
   * @param listener the listener to remove
   */
  public void deregisterConfigUpdateListener(ConfigUpdateListener listener) {
    configUpdateListeners.remove(listener);
  }

  private void notifyConfigUpdateListeners(Config oldConfig, Config newConfig) {
    // CopyOnWriteArrayList is thread-safe for iteration, no defensive copy needed
    for (ConfigUpdateListener listener : configUpdateListeners) {
      listener.onConfigUpdate(oldConfig, newConfig);
    }
  }

  /**
   * Register a listener to be notified when a bundle is activated.
   *
   * @param listener the listener to register
   */
  public void registerBundleActivationListener(BundleActivationListener listener) {
    bundleActivationListeners.add(listener);
  }

  /**
   * Deregister a bundle activation listener.
   *
   * @param listener the listener to remove
   */
  public void deregisterBundleActivationListener(BundleActivationListener listener) {
    bundleActivationListeners.remove(listener);
  }

  /**
   * Notify all registered listeners that a bundle has been activated.
   *
   * @param bundleName the name of the activated bundle
   */
  public void notifyBundleActivation(String bundleName) {
    for (BundleActivationListener listener : bundleActivationListeners) {
      listener.onBundleActivation(bundleName);
    }
  }

  @FunctionalInterface
  public interface PluginStatusListener {
    void onStatusChange(String pluginName, Status status);
  }

  /**
   * Listener interface for configuration updates.
   *
   * <p>Listeners are notified when the PluginManager's configuration is updated, typically by the
   * discovery plugin.
   */
  @FunctionalInterface
  public interface ConfigUpdateListener {
    /**
     * Called when configuration is updated.
     *
     * @param oldConfig the previous configuration
     * @param newConfig the new configuration
     */
    void onConfigUpdate(Config oldConfig, Config newConfig);
  }

  /**
   * Listener interface for bundle activation events.
   *
   * <p>Listeners are notified after a bundle has been successfully activated (both policy and data
   * written to the store).
   */
  @FunctionalInterface
  public interface BundleActivationListener {
    /**
     * Called when a bundle is activated.
     *
     * @param bundleName the name of the activated bundle
     */
    void onBundleActivation(String bundleName);
  }

  public enum Status {
    NOT_READY,
    OK,
    ERROR,
    WARN,
  }

  public static class Builder {
    private Config config;
    private String id;
    private Store store;
    private Logger logger;

    public Builder withConfig(Config config) {
      this.config = config;
      return this;
    }

    public Builder withId(String id) {
      this.id = id;
      return this;
    }

    public Builder withStore(Store store) {
      this.store = store;
      return this;
    }

    public Builder withLogger(Logger logger) {
      this.logger = logger;
      return this;
    }

    public PluginManager build() {
      return new PluginManager(this);
    }
  }
}
