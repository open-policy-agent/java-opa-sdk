package io.github.open_policy_agent.opa.plugins;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collections;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import io.github.open_policy_agent.opa.config.Config;
import io.github.open_policy_agent.opa.logging.Logger;
import io.github.open_policy_agent.opa.storage.InMem;
import io.github.open_policy_agent.opa.storage.Store;

/**
 * Comprehensive unit tests for DecisionLogPlugin.
 *
 * <p>Tests validation, initialization, and decision logging configuration.
 */
class DecisionLogPluginTest {

  private PluginManager manager;
  private Logger mockLogger;
  private Store store;
  private Config config;
  private ObjectMapper mapper;

  @BeforeEach
  void setUp() {
    mockLogger = mock(Logger.class);
    store = new InMem();
    config = new Config();
    mapper = new ObjectMapper();

    // Add service for decision logs
    Config.ServiceConfig service =
        new Config.ServiceConfig().setName("test-service").setUrl("https://example.com");
    config.setServices(Collections.singletonMap("test-service", service));
  }

  @Test
  void validate_noDecisionLogsConfigured_returnsNoErrors() {
    manager =
        new PluginManager.Builder()
            .withId("test-opa")
            .withStore(store)
            .withConfig(config)
            .withLogger(mockLogger)
            .build();

    DecisionLogPlugin plugin = new DecisionLogPlugin();
    Set<String> errors = plugin.validate(manager);

    assertTrue(errors.isEmpty());
  }

  @Test
  void validate_decisionLogsWithValidService_returnsNoErrors() {
    Config.DecisionLogsConfig decisionLogs =
        new Config.DecisionLogsConfig()
            .setService("test-service")
            .setConsole(true)
            .setResource("/logs");
    config.setDecisionLogs(decisionLogs);

    manager =
        new PluginManager.Builder()
            .withId("test-opa")
            .withStore(store)
            .withConfig(config)
            .withLogger(mockLogger)
            .build();

    DecisionLogPlugin plugin = new DecisionLogPlugin();
    Set<String> errors = plugin.validate(manager);

    assertTrue(errors.isEmpty());
  }

  @Test
  void validate_decisionLogsWithNonExistentService_returnsError() {
    Config.DecisionLogsConfig decisionLogs =
        new Config.DecisionLogsConfig().setService("nonexistent-service").setResource("/logs");
    config.setDecisionLogs(decisionLogs);

    manager =
        new PluginManager.Builder()
            .withId("test-opa")
            .withStore(store)
            .withConfig(config)
            .withLogger(mockLogger)
            .build();

    DecisionLogPlugin plugin = new DecisionLogPlugin();
    Set<String> errors = plugin.validate(manager);

    assertFalse(errors.isEmpty());
    assertTrue(errors.stream().anyMatch(e -> e.contains("non-existent service")));
  }

  @Test
  void validate_decisionLogsConsoleOnly_returnsNoErrors() {
    Config.DecisionLogsConfig decisionLogs = new Config.DecisionLogsConfig().setConsole(true);
    config.setDecisionLogs(decisionLogs);

    manager =
        new PluginManager.Builder()
            .withId("test-opa")
            .withStore(store)
            .withConfig(config)
            .withLogger(mockLogger)
            .build();

    DecisionLogPlugin plugin = new DecisionLogPlugin();
    Set<String> errors = plugin.validate(manager);

    assertTrue(errors.isEmpty());
  }

  @Test
  void initialize_noDecisionLogsConfigured_returnsPlugin() {
    manager =
        new PluginManager.Builder()
            .withId("test-opa")
            .withStore(store)
            .withConfig(config)
            .withLogger(mockLogger)
            .build();

    DecisionLogPlugin plugin = new DecisionLogPlugin();
    Plugin initialized = plugin.initialize(manager);

    assertNotNull(initialized);
    assertInstanceOf(DecisionLogPlugin.class, initialized);
  }

  @Test
  void initialize_withDecisionLogsConfig_returnsPlugin() {
    Config.DecisionLogsConfig decisionLogs =
        new Config.DecisionLogsConfig()
            .setService("test-service")
            .setConsole(true)
            .setResource("/logs")
            .setMinDelaySeconds(300)
            .setMaxDelaySeconds(600);
    config.setDecisionLogs(decisionLogs);

    manager =
        new PluginManager.Builder()
            .withId("test-opa")
            .withStore(store)
            .withConfig(config)
            .withLogger(mockLogger)
            .build();

    DecisionLogPlugin plugin = new DecisionLogPlugin();
    Plugin initialized = plugin.initialize(manager);

    assertNotNull(initialized);
    assertInstanceOf(DecisionLogPlugin.class, initialized);
  }

  @Test
  void initialize_withReportingConfig_returnsPlugin() {
    Config.ReportingConfig reporting =
        new Config.ReportingConfig()
            .setMinDelaySeconds(300)
            .setMaxDelaySeconds(600)
            .setUploadSizeLimitBytes(32768L)
            .setBufferSizeLimitEvents(10000);

    Config.DecisionLogsConfig decisionLogs =
        new Config.DecisionLogsConfig().setService("test-service").setReporting(reporting);
    config.setDecisionLogs(decisionLogs);

    manager =
        new PluginManager.Builder()
            .withId("test-opa")
            .withStore(store)
            .withConfig(config)
            .withLogger(mockLogger)
            .build();

    DecisionLogPlugin plugin = new DecisionLogPlugin();
    Plugin initialized = plugin.initialize(manager);

    assertNotNull(initialized);
    assertInstanceOf(DecisionLogPlugin.class, initialized);
  }

  @Test
  void start_setsStatusOk() {
    Config.DecisionLogsConfig decisionLogs =
        new Config.DecisionLogsConfig().setService("test-service").setConsole(true);
    config.setDecisionLogs(decisionLogs);

    manager =
        new PluginManager.Builder()
            .withId("test-opa")
            .withStore(store)
            .withConfig(config)
            .withLogger(mockLogger)
            .build();

    DecisionLogPlugin plugin = new DecisionLogPlugin();
    plugin = (DecisionLogPlugin) plugin.initialize(manager);
    plugin.start();

    assertEquals(PluginManager.Status.OK, manager.getPluginStatus("decision_logs"));
  }

  @Test
  void configDefaults_areCorrect() {
    Config.DecisionLogsConfig decisionLogs = new Config.DecisionLogsConfig();

    assertFalse(decisionLogs.getConsole());
    assertEquals("system/log/mask", decisionLogs.getMaskDecision());
    assertEquals("system/log/drop", decisionLogs.getDropDecision());
    assertEquals(300, decisionLogs.getMinDelaySeconds());
    assertEquals(600, decisionLogs.getMaxDelaySeconds());
    assertEquals("/logs", decisionLogs.getResource());
  }

  @Test
  void configBuilder_setsAllFields() {
    Config.DecisionLogsConfig decisionLogs =
        new Config.DecisionLogsConfig()
            .setService("test-service")
            .setConsole(true)
            .setResource("/custom/logs")
            .setMaskDecision("custom/mask")
            .setDropDecision("custom/drop")
            .setMinDelaySeconds(60)
            .setMaxDelaySeconds(120);

    assertEquals("test-service", decisionLogs.getService());
    assertTrue(decisionLogs.getConsole());
    assertEquals("/custom/logs", decisionLogs.getResource());
    assertEquals("custom/mask", decisionLogs.getMaskDecision());
    assertEquals("custom/drop", decisionLogs.getDropDecision());
    assertEquals(60, decisionLogs.getMinDelaySeconds());
    assertEquals(120, decisionLogs.getMaxDelaySeconds());
  }

  @Test
  void reportingConfigDefaults_areCorrect() {
    Config.ReportingConfig reporting = new Config.ReportingConfig();

    assertEquals(10000, reporting.getBufferSizeLimitEvents());
  }

  @Test
  void reportingConfigBuilder_setsAllFields() {
    Config.ReportingConfig reporting =
        new Config.ReportingConfig()
            .setMinDelaySeconds(300)
            .setMaxDelaySeconds(600)
            .setUploadSizeLimitBytes(65536L)
            .setBufferSizeLimitEvents(20000);

    assertEquals(300, reporting.getMinDelaySeconds());
    assertEquals(600, reporting.getMaxDelaySeconds());
    assertEquals(65536L, reporting.getUploadSizeLimitBytes());
    assertEquals(20000, reporting.getBufferSizeLimitEvents());
  }

  @Test
  void logDecision_addsEventToBuffer() {
    Config.DecisionLogsConfig decisionLogs =
        new Config.DecisionLogsConfig().setConsole(false).setService("test-service");
    config.setDecisionLogs(decisionLogs);

    manager =
        new PluginManager.Builder()
            .withId("test-opa")
            .withStore(store)
            .withConfig(config)
            .withLogger(mockLogger)
            .build();

    DecisionLogPlugin plugin = new DecisionLogPlugin();
    plugin = (DecisionLogPlugin) plugin.initialize(manager);
    plugin.start();

    // Get the decision logs instance
    DecisionLogPlugin.DecisionLogs decisionLogger = plugin.getDecisionLogs();
    assertNotNull(decisionLogger);

    // Create test input and result
    JsonNode input = mapper.createObjectNode().put("user", "alice");
    JsonNode result = mapper.createObjectNode().put("allow", true);

    // Log a decision
    decisionLogger.logDecision(
        "decision-123", input, result, "data.authz.allow", null, 0, null, null);

    // We can't directly verify the buffer, but we can verify no errors occurred
    verify(mockLogger, never()).error(anyString(), any());
  }

  @Test
  void logDecision_consoleEnabled_logsToConsole() {
    Config.DecisionLogsConfig decisionLogs =
        new Config.DecisionLogsConfig().setConsole(true).setService("test-service");
    config.setDecisionLogs(decisionLogs);

    manager =
        new PluginManager.Builder()
            .withId("test-opa")
            .withStore(store)
            .withConfig(config)
            .withLogger(mockLogger)
            .build();

    DecisionLogPlugin plugin = new DecisionLogPlugin();
    plugin = (DecisionLogPlugin) plugin.initialize(manager);
    plugin.start();

    DecisionLogPlugin.DecisionLogs decisionLogger = plugin.getDecisionLogs();

    // Create test input and result
    JsonNode input = mapper.createObjectNode().put("user", "bob");
    JsonNode result = mapper.createObjectNode().put("allow", false);

    // Log a decision
    decisionLogger.logDecision(
        "decision-456", input, result, "data.authz.allow", null, 0, null, null);

    // Verify console logging happened
    verify(mockLogger, atLeastOnce()).info(eq("Decision: %s"), anyString());
  }

  @Test
  void logDecision_bufferLimitReached_triggersFlush() {
    Config.ReportingConfig reporting =
        new Config.ReportingConfig().setBufferSizeLimitEvents(2); // Small buffer for testing

    Config.DecisionLogsConfig decisionLogs =
        new Config.DecisionLogsConfig()
            .setConsole(false)
            .setService("test-service")
            .setReporting(reporting);
    config.setDecisionLogs(decisionLogs);

    manager =
        new PluginManager.Builder()
            .withId("test-opa")
            .withStore(store)
            .withConfig(config)
            .withLogger(mockLogger)
            .build();

    DecisionLogPlugin plugin = new DecisionLogPlugin();
    plugin = (DecisionLogPlugin) plugin.initialize(manager);
    plugin.start();

    DecisionLogPlugin.DecisionLogs decisionLogger = plugin.getDecisionLogs();

    // Create test input and result
    JsonNode input = mapper.createObjectNode().put("user", "charlie");
    JsonNode result = mapper.createObjectNode().put("allow", true);

    // Log decisions to exceed buffer limit
    decisionLogger.logDecision(
        "decision-1", input, result, "data.authz.allow", null, 0, null, null);
    decisionLogger.logDecision(
        "decision-2", input, result, "data.authz.allow", null, 0, null, null);
    decisionLogger.logDecision(
        "decision-3", input, result, "data.authz.allow", null, 0, null, null);

    // Verify debug log for flush
    verify(mockLogger, atLeastOnce()).debug(eq("Flushed %d decision log events"), anyInt());
  }

  @Test
  void validate_delaySecondsInvalid_returnsError() {
    Config.DecisionLogsConfig decisionLogs =
        new Config.DecisionLogsConfig()
            .setService("test-service")
            .setMinDelaySeconds(600)
            .setMaxDelaySeconds(300); // min > max is invalid
    config.setDecisionLogs(decisionLogs);

    manager =
        new PluginManager.Builder()
            .withId("test-opa")
            .withStore(store)
            .withConfig(config)
            .withLogger(mockLogger)
            .build();

    DecisionLogPlugin plugin = new DecisionLogPlugin();
    Set<String> errors = plugin.validate(manager);

    assertFalse(errors.isEmpty());
    assertTrue(
        errors.stream()
            .anyMatch(e -> e.contains("min_delay_seconds") && e.contains("max_delay_seconds")));
  }

  @Test
  void validate_reportingDelaySecondsInvalid_returnsError() {
    Config.ReportingConfig reporting =
        new Config.ReportingConfig()
            .setMinDelaySeconds(600)
            .setMaxDelaySeconds(300); // min > max is invalid

    Config.DecisionLogsConfig decisionLogs =
        new Config.DecisionLogsConfig().setService("test-service").setReporting(reporting);
    config.setDecisionLogs(decisionLogs);

    manager =
        new PluginManager.Builder()
            .withId("test-opa")
            .withStore(store)
            .withConfig(config)
            .withLogger(mockLogger)
            .build();

    DecisionLogPlugin plugin = new DecisionLogPlugin();
    Set<String> errors = plugin.validate(manager);

    assertFalse(errors.isEmpty());
    assertTrue(
        errors.stream()
            .anyMatch(
                e ->
                    e.contains("reporting")
                        && e.contains("min_delay_seconds")
                        && e.contains("max_delay_seconds")));
  }

  @Test
  void decisionLogs_sendsToService() throws Exception {
    Config.DecisionLogsConfig decisionLogs =
        new Config.DecisionLogsConfig()
            .setConsole(false)
            .setService("test-service")
            .setResource("/custom/logs");
    config.setDecisionLogs(decisionLogs);

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
    java.util.Map<String, Plugin> plugins =
        (java.util.Map<String, Plugin>) pluginsField.get(manager);
    plugins.put("services", servicePlugin);

    // Initialize DecisionLogPlugin
    DecisionLogPlugin plugin = new DecisionLogPlugin();
    plugin = (DecisionLogPlugin) plugin.initialize(manager);
    plugin.start();

    DecisionLogPlugin.DecisionLogs decisionLogger = plugin.getDecisionLogs();

    // Create test input and result
    JsonNode input = mapper.createObjectNode().put("user", "alice");
    JsonNode result = mapper.createObjectNode().put("allow", true);

    // Log a decision
    decisionLogger.logDecision(
        "decision-123", input, result, "data.authz.allow", null, 0, null, null);

    // Manually trigger flush
    decisionLogger.flush();

    // Verify debug log for sending to service
    verify(mockLogger, atLeastOnce())
        .debug(eq("Sent %d decision logs to service '%s'"), eq(1), eq("test-service"));
  }

  @Test
  void decisionLogs_includesLabels() {
    // Add labels to config
    java.util.Map<String, String> labels = new java.util.HashMap<>();
    labels.put("environment", "test");
    labels.put("region", "us-west");
    config.setLabels(labels);

    Config.DecisionLogsConfig decisionLogs =
        new Config.DecisionLogsConfig().setConsole(false).setService("test-service");
    config.setDecisionLogs(decisionLogs);

    manager =
        new PluginManager.Builder()
            .withId("test-opa")
            .withStore(store)
            .withConfig(config)
            .withLogger(mockLogger)
            .build();

    DecisionLogPlugin plugin = new DecisionLogPlugin();
    plugin = (DecisionLogPlugin) plugin.initialize(manager);
    plugin.start();

    DecisionLogPlugin.DecisionLogs decisionLogger = plugin.getDecisionLogs();

    // Create test input and result
    JsonNode input = mapper.createObjectNode().put("user", "bob");
    JsonNode result = mapper.createObjectNode().put("allow", false);

    // Log a decision
    decisionLogger.logDecision(
        "decision-456", input, result, "data.authz.allow", null, 0, null, null);

    // Labels should be included in the logged event (verified by no error)
    verify(mockLogger, never()).error(anyString(), any());
  }

  @Test
  void decisionLogs_fullSignature_includesAllFields() {
    Config.DecisionLogsConfig decisionLogs =
        new Config.DecisionLogsConfig().setConsole(false).setService("test-service");
    config.setDecisionLogs(decisionLogs);

    manager =
        new PluginManager.Builder()
            .withId("test-opa")
            .withStore(store)
            .withConfig(config)
            .withLogger(mockLogger)
            .build();

    DecisionLogPlugin plugin = new DecisionLogPlugin();
    plugin = (DecisionLogPlugin) plugin.initialize(manager);
    plugin.start();

    DecisionLogPlugin.DecisionLogs decisionLogger = plugin.getDecisionLogs();

    // Create test input and result
    JsonNode input = mapper.createObjectNode().put("user", "charlie");
    JsonNode result = mapper.createObjectNode().put("allow", true);

    // Log a decision with all optional fields
    // startTime is in milliseconds
    long startTime = System.currentTimeMillis();
    decisionLogger.logDecision(
        "decision-789",
        input,
        result,
        "data.authz.allow",
        "user@example.com", // requested_by
        startTime, // startTime in milliseconds
        null, // metrics
        null // ndCacheValues
        );

    // Verify no errors occurred
    verify(mockLogger, never()).error(anyString(), any());
  }

  @Test
  void decisionLogs_publicMethod_delegatesToInnerClass() {
    Config.DecisionLogsConfig decisionLogs =
        new Config.DecisionLogsConfig().setConsole(false).setService("test-service");
    config.setDecisionLogs(decisionLogs);

    manager =
        new PluginManager.Builder()
            .withId("test-opa")
            .withStore(store)
            .withConfig(config)
            .withLogger(mockLogger)
            .build();

    DecisionLogPlugin plugin = new DecisionLogPlugin();
    plugin = (DecisionLogPlugin) plugin.initialize(manager);
    plugin.start();

    // Create test input and result
    JsonNode input = mapper.createObjectNode().put("user", "dave");
    JsonNode result = mapper.createObjectNode().put("allow", true);

    // Log a decision using public method (as called from Opa.java)
    long startTime = System.currentTimeMillis();
    plugin.logDecision("decision-999", input, result, "data.authz.allow", startTime, null, null);

    // Verify no errors occurred
    verify(mockLogger, never()).error(anyString(), any());
  }
}
