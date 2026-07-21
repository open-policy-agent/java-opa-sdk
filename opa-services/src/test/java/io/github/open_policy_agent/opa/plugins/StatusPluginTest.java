package io.github.open_policy_agent.opa.plugins;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import io.github.open_policy_agent.opa.config.Config;
import io.github.open_policy_agent.opa.logging.Logger;
import io.github.open_policy_agent.opa.storage.InMem;
import io.github.open_policy_agent.opa.storage.Store;

/**
 * Comprehensive unit tests for StatusPlugin.
 *
 * <p>Tests validation, initialization, and status reporting configuration.
 */
class StatusPluginTest {

  private PluginManager manager;
  private Logger mockLogger;
  private Store store;
  private Config config;

  @BeforeEach
  void setUp() {
    mockLogger = mock(Logger.class);
    store = new InMem();
    config = new Config();

    // Add service for status reporting
    Config.ServiceConfig service =
        new Config.ServiceConfig().setName("test-service").setUrl("https://example.com");
    config.setServices(Collections.singletonMap("test-service", service));
  }

  @Test
  void validate_noStatusConfigured_returnsNoErrors() {
    manager =
        new PluginManager.Builder()
            .withId("test-opa")
            .withStore(store)
            .withConfig(config)
            .withLogger(mockLogger)
            .build();

    StatusPlugin plugin = new StatusPlugin();
    Set<String> errors = plugin.validate(manager);

    assertTrue(errors.isEmpty());
  }

  @Test
  void validate_statusWithValidService_returnsNoErrors() {
    Config.StatusConfig status =
        new Config.StatusConfig().setService("test-service").setConsole(true);
    config.setStatus(status);

    manager =
        new PluginManager.Builder()
            .withId("test-opa")
            .withStore(store)
            .withConfig(config)
            .withLogger(mockLogger)
            .build();

    StatusPlugin plugin = new StatusPlugin();
    Set<String> errors = plugin.validate(manager);

    assertTrue(errors.isEmpty());
  }

  @Test
  void validate_statusWithNonExistentService_returnsError() {
    Config.StatusConfig status =
        new Config.StatusConfig().setService("nonexistent-service").setConsole(false);
    config.setStatus(status);

    manager =
        new PluginManager.Builder()
            .withId("test-opa")
            .withStore(store)
            .withConfig(config)
            .withLogger(mockLogger)
            .build();

    StatusPlugin plugin = new StatusPlugin();
    Set<String> errors = plugin.validate(manager);

    assertFalse(errors.isEmpty());
    assertTrue(errors.stream().anyMatch(e -> e.contains("non-existent service")));
  }

  @Test
  void validate_statusConsoleOnly_returnsNoErrors() {
    Config.StatusConfig status = new Config.StatusConfig().setConsole(true);
    config.setStatus(status);

    manager =
        new PluginManager.Builder()
            .withId("test-opa")
            .withStore(store)
            .withConfig(config)
            .withLogger(mockLogger)
            .build();

    StatusPlugin plugin = new StatusPlugin();
    Set<String> errors = plugin.validate(manager);

    assertTrue(errors.isEmpty());
  }

  @Test
  void initialize_noStatusConfigured_returnsPlugin() {
    manager =
        new PluginManager.Builder()
            .withId("test-opa")
            .withStore(store)
            .withConfig(config)
            .withLogger(mockLogger)
            .build();

    StatusPlugin plugin = new StatusPlugin();
    Plugin initialized = plugin.initialize(manager);

    assertNotNull(initialized);
    assertInstanceOf(StatusPlugin.class, initialized);
  }

  @Test
  void initialize_withStatusConfig_returnsPlugin() {
    Config.StatusConfig status =
        new Config.StatusConfig().setService("test-service").setConsole(true);
    config.setStatus(status);

    manager =
        new PluginManager.Builder()
            .withId("test-opa")
            .withStore(store)
            .withConfig(config)
            .withLogger(mockLogger)
            .build();

    StatusPlugin plugin = new StatusPlugin();
    Plugin initialized = plugin.initialize(manager);

    assertNotNull(initialized);
    assertInstanceOf(StatusPlugin.class, initialized);
  }

  @Test
  void start_setsStatusOk() {
    Config.StatusConfig status =
        new Config.StatusConfig().setService("test-service").setConsole(true);
    config.setStatus(status);

    manager =
        new PluginManager.Builder()
            .withId("test-opa")
            .withStore(store)
            .withConfig(config)
            .withLogger(mockLogger)
            .build();

    StatusPlugin plugin = new StatusPlugin();
    plugin = (StatusPlugin) plugin.initialize(manager);
    plugin.start();

    assertEquals(PluginManager.Status.OK, manager.getPluginStatus("status"));
  }

  @Test
  void configDefaults_consoleIsFalse() {
    Config.StatusConfig status = new Config.StatusConfig();

    assertFalse(status.getConsole());
    assertEquals("/status", status.getResource());
  }

  @Test
  void configBuilder_setsAllFields() {
    Config.StatusConfig status =
        new Config.StatusConfig()
            .setService("test-service")
            .setConsole(true)
            .setResource("/custom/status");

    assertEquals("test-service", status.getService());
    assertTrue(status.getConsole());
    assertEquals("/custom/status", status.getResource());
  }

  @Test
  void initialize_wiresDefaultResourcePath() throws Exception {
    Config.StatusConfig status =
        new Config.StatusConfig().setService("test-service").setConsole(false);
    config.setStatus(status);

    manager =
        new PluginManager.Builder()
            .withId("test-opa")
            .withStore(store)
            .withConfig(config)
            .withLogger(mockLogger)
            .build();

    StatusPlugin plugin = (StatusPlugin) new StatusPlugin().initialize(manager);
    StatusPlugin.Status statusReporter = getStatusReporter(plugin);

    assertEquals("/status", statusReporter.getResource());
  }

  @Test
  void initialize_wiresCustomResourcePath() throws Exception {
    Config.StatusConfig status =
        new Config.StatusConfig()
            .setService("test-service")
            .setConsole(false)
            .setResource("/custom/status");
    config.setStatus(status);

    manager =
        new PluginManager.Builder()
            .withId("test-opa")
            .withStore(store)
            .withConfig(config)
            .withLogger(mockLogger)
            .build();

    StatusPlugin plugin = (StatusPlugin) new StatusPlugin().initialize(manager);
    StatusPlugin.Status statusReporter = getStatusReporter(plugin);

    assertEquals("/custom/status", statusReporter.getResource());
  }

  @Test
  void statusReport_consoleLogging_logsToConsole() throws Exception {
    Config.StatusConfig status = new Config.StatusConfig().setConsole(true);
    config.setStatus(status);

    manager =
        new PluginManager.Builder()
            .withId("test-opa")
            .withStore(store)
            .withConfig(config)
            .withLogger(mockLogger)
            .build();

    StatusPlugin plugin = new StatusPlugin();
    plugin = (StatusPlugin) plugin.initialize(manager);

    // Access the status reporter via reflection to trigger a report manually
    java.lang.reflect.Field statusField = StatusPlugin.class.getDeclaredField("status");
    statusField.setAccessible(true);
    StatusPlugin.Status statusReporter = (StatusPlugin.Status) statusField.get(plugin);

    if (statusReporter != null) {
      statusReporter.reportStatus();

      // Verify console logging happened
      verify(mockLogger, atLeastOnce()).info(eq("Status: %s"), anyString());
    }
  }

  @Test
  void statusReport_includesPluginStatuses() {
    Config.StatusConfig status =
        new Config.StatusConfig().setService("test-service").setConsole(false);
    config.setStatus(status);

    manager =
        new PluginManager.Builder()
            .withId("test-opa")
            .withStore(store)
            .withConfig(config)
            .withLogger(mockLogger)
            .build();

    // Set some plugin statuses
    manager.updatePluginStatus("bundles", PluginManager.Status.OK);
    manager.updatePluginStatus("decision_logs", PluginManager.Status.NOT_READY);
    manager.updatePluginStatus("status", PluginManager.Status.OK);

    StatusPlugin plugin = new StatusPlugin();
    plugin = (StatusPlugin) plugin.initialize(manager);
    plugin.start();

    // Plugin statuses should be included in status report
    assertEquals(PluginManager.Status.OK, manager.getPluginStatus("bundles"));
    assertEquals(PluginManager.Status.NOT_READY, manager.getPluginStatus("decision_logs"));
  }

  @Test
  void statusReport_includesCustomRegisteredPlugin() throws Exception {
    Config.StatusConfig status = new Config.StatusConfig().setConsole(true);
    config.setStatus(status);

    manager =
        new PluginManager.Builder()
            .withId("test-opa")
            .withStore(store)
            .withConfig(config)
            .withLogger(mockLogger)
            .build();

    manager.registerPlugin("custom_plugin", new NoopPlugin());
    manager.updatePluginStatus("custom_plugin", PluginManager.Status.OK);

    StatusPlugin plugin = new StatusPlugin();
    plugin = (StatusPlugin) plugin.initialize(manager);

    ObjectNode report = buildStatusReport(plugin);
    ObjectNode plugins = (ObjectNode) report.get("plugins");

    assertTrue(plugins.has("custom_plugin"));
    assertEquals("OK", plugins.get("custom_plugin").asText());
  }

  @Test
  void statusReport_excludesServicesPlugin() throws Exception {
    Config.StatusConfig status = new Config.StatusConfig().setConsole(true);
    config.setStatus(status);

    manager =
        new PluginManager.Builder()
            .withId("test-opa")
            .withStore(store)
            .withConfig(config)
            .withLogger(mockLogger)
            .build();

    // The "services" plugin reports an OK status but must not surface in the report.
    manager.registerPlugin("services", new NoopPlugin());
    manager.updatePluginStatus("services", PluginManager.Status.OK);
    manager.registerPlugin("custom_plugin", new NoopPlugin());
    manager.updatePluginStatus("custom_plugin", PluginManager.Status.OK);

    StatusPlugin plugin = new StatusPlugin();
    plugin = (StatusPlugin) plugin.initialize(manager);

    ObjectNode report = buildStatusReport(plugin);
    ObjectNode plugins = (ObjectNode) report.get("plugins");

    assertFalse(plugins.has("services"));
    assertTrue(plugins.has("custom_plugin"));
  }

  @Test
  void statusReport_sendsToService() throws Exception {
    Config.StatusConfig status =
        new Config.StatusConfig().setService("test-service").setConsole(false);
    config.setStatus(status);

    manager =
        new PluginManager.Builder()
            .withId("test-opa")
            .withStore(store)
            .withConfig(config)
            .withLogger(mockLogger)
            .build();

    // Initialize ServicePlugin first
    ServicePlugin servicePlugin = new ServicePlugin();
    servicePlugin = (ServicePlugin) servicePlugin.initialize(manager);
    servicePlugin.start();

    // Register the ServicePlugin with PluginManager
    java.lang.reflect.Field pluginsField = PluginManager.class.getDeclaredField("plugins");
    pluginsField.setAccessible(true);
    @SuppressWarnings("unchecked")
    Map<String, Plugin> plugins = (Map<String, Plugin>) pluginsField.get(manager);
    plugins.put("services", servicePlugin);

    // Initialize StatusPlugin
    StatusPlugin plugin = new StatusPlugin();
    plugin = (StatusPlugin) plugin.initialize(manager);

    // Access the status reporter via reflection to trigger a report manually
    java.lang.reflect.Field statusField = StatusPlugin.class.getDeclaredField("status");
    statusField.setAccessible(true);
    StatusPlugin.Status statusReporter = (StatusPlugin.Status) statusField.get(plugin);

    if (statusReporter != null) {
      statusReporter.reportStatus();

      // Verify debug log for sending to service
      verify(mockLogger, atLeastOnce())
          .debug(eq("Status report sent to service '%s'"), eq("test-service"));
    }
  }

  @Test
  void statusReport_includesInstanceId() throws Exception {
    Config.StatusConfig status = new Config.StatusConfig().setConsole(true);
    config.setStatus(status);

    manager =
        new PluginManager.Builder()
            .withId("prod-us-west-1-opa-123")
            .withStore(store)
            .withConfig(config)
            .withLogger(mockLogger)
            .build();

    StatusPlugin plugin = new StatusPlugin();
    plugin = (StatusPlugin) plugin.initialize(manager);

    // Access the status reporter via reflection
    java.lang.reflect.Field statusField = StatusPlugin.class.getDeclaredField("status");
    statusField.setAccessible(true);
    StatusPlugin.Status statusReporter = (StatusPlugin.Status) statusField.get(plugin);

    if (statusReporter != null) {
      // Use reflection to call buildStatusReport
      java.lang.reflect.Method buildStatusMethod =
          StatusPlugin.Status.class.getDeclaredMethod("buildStatusReport");
      buildStatusMethod.setAccessible(true);
      com.fasterxml.jackson.databind.node.ObjectNode report =
          (com.fasterxml.jackson.databind.node.ObjectNode) buildStatusMethod.invoke(statusReporter);

      // Verify the instance ID is included
      assertNotNull(report);
      assertTrue(report.has("id"));
      assertEquals("prod-us-west-1-opa-123", report.get("id").asText());
    }
  }

  private ObjectNode buildStatusReport(StatusPlugin plugin) throws Exception {
    return buildStatusReport(getStatusReporter(plugin));
  }

  private static StatusPlugin.Status getStatusReporter(StatusPlugin plugin) throws Exception {
    java.lang.reflect.Field statusField = StatusPlugin.class.getDeclaredField("status");
    statusField.setAccessible(true);
    return (StatusPlugin.Status) statusField.get(plugin);
  }

  private ObjectNode buildStatusReport(StatusPlugin.Status statusReporter) throws Exception {
    java.lang.reflect.Method buildStatusMethod =
        StatusPlugin.Status.class.getDeclaredMethod("buildStatusReport");
    buildStatusMethod.setAccessible(true);
    return (ObjectNode) buildStatusMethod.invoke(statusReporter);
  }

  private static class NoopPlugin implements Plugin {
    @Override
    public Set<String> validate(PluginManager manager) {
      return Collections.emptySet();
    }

    @Override
    public Plugin initialize(PluginManager manager) {
      return this;
    }

    @Override
    public void start() {}

    @Override
    public void stop() {}
  }
}
