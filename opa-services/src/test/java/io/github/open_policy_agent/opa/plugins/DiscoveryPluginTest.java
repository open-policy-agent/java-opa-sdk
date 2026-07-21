package io.github.open_policy_agent.opa.plugins;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.zip.GZIPOutputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import io.github.open_policy_agent.opa.config.Config;
import io.github.open_policy_agent.opa.logging.Logger;
import io.github.open_policy_agent.opa.storage.InMem;
import io.github.open_policy_agent.opa.storage.Store;

/**
 * Comprehensive unit tests for DiscoveryPlugin.
 *
 * <p>Tests validation, initialization, good/bad bundle scenarios, and config update propagation.
 */
class DiscoveryPluginTest {

  private PluginManager manager;
  private Logger mockLogger;
  private Store store;
  private Config config;

  @BeforeEach
  void setUp() {
    mockLogger = mock(Logger.class);
    store = new InMem();

    // Base config with discovery
    config = new Config();
    Config.ServiceConfig service =
        new Config.ServiceConfig().setName("test-service").setUrl("file:///tmp/bundles");
    config.setServices(java.util.Collections.singletonMap("test-service", service));

    Config.DiscoveryConfig discoveryConfig =
        new Config.DiscoveryConfig()
            .setName("discovery")
            .setService("test-service")
            .setResource("/discovery.tar.gz");
    config.setDiscovery(discoveryConfig);

    manager =
        new PluginManager.Builder()
            .withId("test-opa")
            .withStore(store)
            .withConfig(config)
            .withLogger(mockLogger)
            .build();
  }

  @Test
  void validate_noDiscoveryConfigured_returnsNoErrors() {
    Config configWithoutDiscovery = new Config();
    PluginManager managerWithoutDiscovery =
        new PluginManager.Builder()
            .withId("test")
            .withStore(store)
            .withConfig(configWithoutDiscovery)
            .withLogger(mockLogger)
            .build();

    DiscoveryPlugin plugin = new DiscoveryPlugin();
    Set<String> errors = plugin.validate(managerWithoutDiscovery);

    assertTrue(errors.isEmpty());
  }

  @Test
  void validate_missingService_returnsError() {
    Config.DiscoveryConfig discoveryConfig =
        new Config.DiscoveryConfig().setResource("/discovery.tar.gz");
    config.setDiscovery(discoveryConfig);

    DiscoveryPlugin plugin = new DiscoveryPlugin();
    Set<String> errors = plugin.validate(manager);

    assertFalse(errors.isEmpty());
    assertTrue(errors.stream().anyMatch(e -> e.contains("missing or empty service reference")));
  }

  @Test
  void validate_nonExistentService_returnsError() {
    Config.DiscoveryConfig discoveryConfig =
        new Config.DiscoveryConfig()
            .setService("nonexistent-service")
            .setResource("/discovery.tar.gz");
    config.setDiscovery(discoveryConfig);

    DiscoveryPlugin plugin = new DiscoveryPlugin();
    Set<String> errors = plugin.validate(manager);

    assertFalse(errors.isEmpty());
    assertTrue(errors.stream().anyMatch(e -> e.contains("non-existent service")));
  }

  @Test
  void validate_missingResource_returnsError() {
    Config.DiscoveryConfig discoveryConfig =
        new Config.DiscoveryConfig().setService("test-service");
    config.setDiscovery(discoveryConfig);

    DiscoveryPlugin plugin = new DiscoveryPlugin();
    Set<String> errors = plugin.validate(manager);

    assertFalse(errors.isEmpty());
    assertTrue(errors.stream().anyMatch(e -> e.contains("missing or empty resource path")));
  }

  @Test
  void validate_validConfig_returnsNoErrors() {
    DiscoveryPlugin plugin = new DiscoveryPlugin();
    Set<String> errors = plugin.validate(manager);

    assertTrue(errors.isEmpty());
  }

  @Test
  void initialize_noDiscoveryConfigured_returnsPlugin() {
    Config configWithoutDiscovery = new Config();
    PluginManager managerWithoutDiscovery =
        new PluginManager.Builder()
            .withId("test")
            .withStore(store)
            .withConfig(configWithoutDiscovery)
            .withLogger(mockLogger)
            .build();

    DiscoveryPlugin plugin = new DiscoveryPlugin();
    Plugin initialized = plugin.initialize(managerWithoutDiscovery);

    assertNotNull(initialized);
  }

  @Test
  void initialize_withDiscoveryConfigured_setsupDiscoveryBundle() {
    DiscoveryPlugin plugin = new DiscoveryPlugin();
    Plugin initialized = plugin.initialize(manager);

    assertNotNull(initialized);
    assertInstanceOf(DiscoveryPlugin.class, initialized);
  }

  @Test
  void start_noDiscoveryConfigured_setsStatusOk() {
    Config configWithoutDiscovery = new Config();
    PluginManager managerWithoutDiscovery =
        new PluginManager.Builder()
            .withId("test")
            .withStore(store)
            .withConfig(configWithoutDiscovery)
            .withLogger(mockLogger)
            .build();

    DiscoveryPlugin plugin = new DiscoveryPlugin();
    plugin = (DiscoveryPlugin) plugin.initialize(managerWithoutDiscovery);
    plugin.start();

    verify(mockLogger, never()).error(anyString(), any());
    assertEquals(PluginManager.Status.OK, managerWithoutDiscovery.getPluginStatus("discovery"));
  }

  @Test
  void discoveryBundle_withRegoFiles_rejectsBundle(@TempDir Path tempDir) throws Exception {
    // Create a discovery bundle with .rego files (should be rejected)
    byte[] bundleData = createDiscoveryBundleWithRego(tempDir);
    Path bundlePath = tempDir.resolve("discovery.tar.gz");
    Files.write(bundlePath, bundleData);

    // Update config to point to this file
    Config.ServiceConfig service =
        new Config.ServiceConfig().setName("test-service").setUrl(tempDir.toUri().toString());
    config.setServices(java.util.Collections.singletonMap("test-service", service));
    config.getDiscovery().setResource("discovery.tar.gz");

    manager =
        new PluginManager.Builder()
            .withId("test-opa")
            .withStore(store)
            .withConfig(config)
            .withLogger(mockLogger)
            .build();

    DiscoveryPlugin plugin = new DiscoveryPlugin();
    plugin = (DiscoveryPlugin) plugin.initialize(manager);
    plugin.start();

    // Wait for initial activation to complete (or fail)
    Thread.sleep(500);

    // Verify error was logged about .rego files
    verify(mockLogger, atLeastOnce())
        .error(eq("Discovery '%s': %s"), eq("discovery"), contains(".rego"));
  }

  @Test
  void discoveryBundle_validConfig_updatesPluginManagerConfig(@TempDir Path tempDir)
      throws Exception {
    // Create a valid discovery bundle with config
    Config discoveredConfig = createValidDiscoveredConfig(tempDir);
    byte[] bundleData = createDiscoveryBundle(discoveredConfig);
    Path bundlePath = tempDir.resolve("discovery.tar.gz");
    Files.write(bundlePath, bundleData);

    // Update config to point to this file
    Config.ServiceConfig service =
        new Config.ServiceConfig().setName("test-service").setUrl(tempDir.toUri().toString());
    config.setServices(java.util.Collections.singletonMap("test-service", service));
    config.getDiscovery().setResource("discovery.tar.gz");

    manager =
        new PluginManager.Builder()
            .withId("test-opa")
            .withStore(store)
            .withConfig(config)
            .withLogger(mockLogger)
            .build();

    // Register a config update listener to verify updateConfig() was called
    PluginManager.ConfigUpdateListener mockListener =
        mock(PluginManager.ConfigUpdateListener.class);
    manager.registerConfigUpdateListener(mockListener);

    DiscoveryPlugin plugin = new DiscoveryPlugin();
    plugin = (DiscoveryPlugin) plugin.initialize(manager);
    plugin.start();

    // Wait for initial activation
    Thread.sleep(500);

    // Verify config was updated
    verify(mockListener, atLeastOnce()).onConfigUpdate(any(), any());

    // Verify successful activation was logged
    verify(mockLogger, atLeastOnce()).info(eq("Discovery bundle initially activated"));

    // Verify the new config is in the manager
    Config updatedConfig = manager.getConfig();
    assertNotNull(updatedConfig);
    assertNotNull(updatedConfig.getBundles());
  }

  @Test
  void discoveryBundle_initialGood_secondBad_keepsOldConfig(@TempDir Path tempDir)
      throws Exception {
    // Create initial valid discovery bundle
    Config goodConfig = createValidDiscoveredConfig(tempDir);
    byte[] goodBundle = createDiscoveryBundle(goodConfig);
    Path bundlePath = tempDir.resolve("discovery.tar.gz");
    Files.write(bundlePath, goodBundle);

    // Setup config with SHORT polling intervals for testing
    Config.ServiceConfig service =
        new Config.ServiceConfig().setName("test-service").setUrl(tempDir.toUri().toString());
    config.setServices(java.util.Collections.singletonMap("test-service", service));

    Config.PollingConfig shortPolling =
        new Config.PollingConfig().setMinDelaySeconds(1).setMaxDelaySeconds(1);
    config.getDiscovery().setResource("discovery.tar.gz");
    config.getDiscovery().setPolling(shortPolling);

    manager =
        new PluginManager.Builder()
            .withId("test-opa")
            .withStore(store)
            .withConfig(config)
            .withLogger(mockLogger)
            .build();

    DiscoveryPlugin plugin = new DiscoveryPlugin();
    plugin = (DiscoveryPlugin) plugin.initialize(manager);
    plugin.start();

    // Wait for first activation
    Thread.sleep(500);

    // Verify first config loaded successfully
    verify(mockLogger, atLeastOnce()).info(eq("Configuration updated by discovery"));

    // Now replace with bad bundle (has .rego files)
    byte[] badBundle = createDiscoveryBundleWithRego(tempDir);
    Files.write(bundlePath, badBundle);

    // Wait for next poll (polling is every 1 second)
    Thread.sleep(1500);

    // Verify error was logged for bad bundle
    verify(mockLogger, atLeastOnce())
        .error(eq("Discovery '%s': %s"), eq("discovery"), contains(".rego"));

    // The good config should still be in use
    Config currentConfig = manager.getConfig();
    assertNotNull(currentConfig);
  }

  @Test
  void discoveryBundle_emptyDataJson_rejectsBundle(@TempDir Path tempDir) throws Exception {
    // Create discovery bundle without data.json
    byte[] bundleData = createEmptyDiscoveryBundle();
    Path bundlePath = tempDir.resolve("discovery.tar.gz");
    Files.write(bundlePath, bundleData);

    Config.ServiceConfig service =
        new Config.ServiceConfig().setName("test-service").setUrl(tempDir.toUri().toString());
    config.setServices(java.util.Collections.singletonMap("test-service", service));
    config.getDiscovery().setResource("discovery.tar.gz");

    manager =
        new PluginManager.Builder()
            .withId("test-opa")
            .withStore(store)
            .withConfig(config)
            .withLogger(mockLogger)
            .build();

    DiscoveryPlugin plugin = new DiscoveryPlugin();
    plugin = (DiscoveryPlugin) plugin.initialize(manager);
    plugin.start();

    // Wait for activation to fail
    Thread.sleep(500);

    // Verify error about missing configuration data
    verify(mockLogger, atLeastOnce())
        .error(
            eq("Discovery '%s': %s"),
            eq("discovery"),
            contains("does not contain configuration data"));
  }

  // Helper methods to create test bundles

  private byte[] createDiscoveryBundle(Config config) throws IOException {
    ByteArrayOutputStream byteOut = new ByteArrayOutputStream();

    try (GZIPOutputStream gzipOut = new GZIPOutputStream(byteOut);
        TarArchiveOutputStream tarOut = new TarArchiveOutputStream(gzipOut)) {

      // Add data.json with config
      ObjectMapper mapper = new ObjectMapper();
      String configJson = mapper.writeValueAsString(config);

      TarArchiveEntry dataEntry = new TarArchiveEntry("data.json");
      byte[] dataBytes = configJson.getBytes();
      dataEntry.setSize(dataBytes.length);
      tarOut.putArchiveEntry(dataEntry);
      tarOut.write(dataBytes);
      tarOut.closeArchiveEntry();

      tarOut.finish();
    }

    return byteOut.toByteArray();
  }

  private byte[] createDiscoveryBundleWithRego(Path tempDir) throws IOException {
    ByteArrayOutputStream byteOut = new ByteArrayOutputStream();

    try (GZIPOutputStream gzipOut = new GZIPOutputStream(byteOut);
        TarArchiveOutputStream tarOut = new TarArchiveOutputStream(gzipOut)) {

      // Add data.json with valid config
      Config config = createValidDiscoveredConfig(tempDir);
      ObjectMapper mapper = new ObjectMapper();
      String configJson = mapper.writeValueAsString(config);

      TarArchiveEntry dataEntry = new TarArchiveEntry("data.json");
      byte[] dataBytes = configJson.getBytes();
      dataEntry.setSize(dataBytes.length);
      tarOut.putArchiveEntry(dataEntry);
      tarOut.write(dataBytes);
      tarOut.closeArchiveEntry();

      // Add a .rego file (which should cause rejection)
      String regoContent = "package discovery\n\nallow = true\n";
      TarArchiveEntry regoEntry = new TarArchiveEntry("policy.rego");
      byte[] regoBytes = regoContent.getBytes();
      regoEntry.setSize(regoBytes.length);
      tarOut.putArchiveEntry(regoEntry);
      tarOut.write(regoBytes);
      tarOut.closeArchiveEntry();

      tarOut.finish();
    }

    return byteOut.toByteArray();
  }

  private byte[] createEmptyDiscoveryBundle() throws IOException {
    ByteArrayOutputStream byteOut = new ByteArrayOutputStream();

    try (GZIPOutputStream gzipOut = new GZIPOutputStream(byteOut);
        TarArchiveOutputStream tarOut = new TarArchiveOutputStream(gzipOut)) {

      // Add only manifest, no data.json
      String manifestJson = "{\"revision\": \"test-123\"}";
      TarArchiveEntry manifestEntry = new TarArchiveEntry(".manifest");
      byte[] manifestBytes = manifestJson.getBytes();
      manifestEntry.setSize(manifestBytes.length);
      tarOut.putArchiveEntry(manifestEntry);
      tarOut.write(manifestBytes);
      tarOut.closeArchiveEntry();

      tarOut.finish();
    }

    return byteOut.toByteArray();
  }

  private Config createValidDiscoveredConfig(Path tempDir) {
    Config config = new Config();

    // Include BOTH the test-service (for discovery itself) and discovered-service
    Config.ServiceConfig testService =
        new Config.ServiceConfig().setName("test-service").setUrl(tempDir.toUri().toString());

    Config.ServiceConfig discoveredService =
        new Config.ServiceConfig()
            .setName("discovered-service")
            .setUrl("https://discovered.example.com");

    java.util.Map<String, Config.ServiceConfig> services = new java.util.HashMap<>();
    services.put("test-service", testService);
    services.put("discovered-service", discoveredService);
    config.setServices(services);

    Config.BundleConfig bundle =
        new Config.BundleConfig()
            .setService("discovered-service")
            .setResource("/bundles/authz.tar.gz");

    config.setBundles(java.util.Collections.singletonMap("authz", bundle));

    // Include discovery config so it can continue polling
    Config.DiscoveryConfig discoveryConfig =
        new Config.DiscoveryConfig()
            .setName("discovery")
            .setService("test-service")
            .setResource("discovery.tar.gz")
            .setPolling(new Config.PollingConfig().setMinDelaySeconds(1).setMaxDelaySeconds(1));
    config.setDiscovery(discoveryConfig);

    return config;
  }
}
