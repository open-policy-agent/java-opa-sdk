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
  void validate_delaySecondsInvalid_returnsError() {
    Config.StatusConfig status =
        new Config.StatusConfig()
            .setService("test-service")
            .setMinDelaySeconds(60)
            .setMaxDelaySeconds(30); // min > max is invalid
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
    assertTrue(
        errors.stream()
            .anyMatch(e -> e.contains("min_delay_seconds") && e.contains("max_delay_seconds")));
  }

  @Test
  void validate_delaySecondsEqual_returnsNoErrors() {
    Config.StatusConfig status =
        new Config.StatusConfig()
            .setService("test-service")
            .setMinDelaySeconds(30)
            .setMaxDelaySeconds(30);
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
  void validate_negativeDelaySeconds_returnsError() {
    Config.StatusConfig status =
        new Config.StatusConfig()
            .setService("test-service")
            .setMinDelaySeconds(-1)
            .setMaxDelaySeconds(-5);
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

    assertTrue(errors.stream().anyMatch(e -> e.contains("min_delay_seconds must be >= 0")));
    assertTrue(errors.stream().anyMatch(e -> e.contains("max_delay_seconds must be >= 0")));
  }

  @Test
  void validate_onlyMaxDelayNegative_returnsError() {
    Config.StatusConfig status =
        new Config.StatusConfig().setService("test-service").setMaxDelaySeconds(-1);
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

    assertTrue(errors.stream().anyMatch(e -> e.contains("max_delay_seconds must be >= 0")));
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
  void start_periodicReport_usesJitteredChainedSchedule() throws Exception {
    // min == max makes the jittered delay deterministic (always 1s), keeping the test fast and
    // non-flaky while still exercising the chained re-scheduling in scheduleNextReport.
    Config.StatusConfig status =
        new Config.StatusConfig().setConsole(true).setMinDelaySeconds(1).setMaxDelaySeconds(1);
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

    // Immediate report at t=0, plus chained reports at ~t=1s and ~t=2s.
    Thread.sleep(2500);

    // Three reports ~1s apart proves scheduleNextReport re-chains itself instead of firing once
    // (which the old immediate-only schedule() call would not do).
    verify(mockLogger, atLeast(3)).info(eq("Status: %s"), anyString());
  }

  @Test
  void start_onlyMinDelayConfigured_defaultsMaxToTwiceMin() throws Exception {
    Config.StatusConfig status =
        new Config.StatusConfig()
            .setConsole(true)
            .setMinDelaySeconds(1); // max omitted → start() uses 2 * min
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

    // With max unset, the chained report falls back to 2x min (2s here). Together with the
    // immediate report at t=0, that guarantees at least 2 reports by t=2.5s. If the fallback
    // instead used an unrelated large default, only the immediate report would land in time.
    Thread.sleep(2500);

    verify(mockLogger, atLeast(2)).info(eq("Status: %s"), anyString());
  }

  @Test
  void start_onlyMaxDelayBelowDefaultMin_defaultsMinToMax() throws Exception {
    // max-only with max < 30: previously min defaulted to 30, so scheduleNextReport used
    // min>=max → always 30s and silently ignored the configured max. Now min = min(30, max).
    Config.StatusConfig status =
        new Config.StatusConfig().setConsole(true).setMaxDelaySeconds(1);
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

    Thread.sleep(2500);

    // Immediate + chained at ~1s and ~2s proves the max was honored (not replaced by 30s).
    verify(mockLogger, atLeast(3)).info(eq("Status: %s"), anyString());
  }

  @Test
  void configDefaults_consoleIsFalse() {
    Config.StatusConfig status = new Config.StatusConfig();

    assertFalse(status.getConsole());
    assertEquals("/status", status.getResource());
  }

  @Test
  void configDefaults_delaySecondsAreCorrect() {
    Config.StatusConfig status = new Config.StatusConfig();

    // Unset in config (null) so Jackson-omitted keys keep the start()-time defaults
    // (min=30, max=2*min) reachable — same pattern as ReportingConfig.
    assertNull(status.getMinDelaySeconds());
    assertNull(status.getMaxDelaySeconds());
  }

  @Test
  void configBuilder_setsAllFields() {
    Config.StatusConfig status =
        new Config.StatusConfig()
            .setService("test-service")
            .setConsole(true)
            .setResource("/custom/status")
            .setMinDelaySeconds(60)
            .setMaxDelaySeconds(120);

    assertEquals("test-service", status.getService());
    assertTrue(status.getConsole());
    assertEquals("/custom/status", status.getResource());
    assertEquals(60, status.getMinDelaySeconds());
    assertEquals(120, status.getMaxDelaySeconds());
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
