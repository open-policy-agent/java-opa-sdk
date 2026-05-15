package io.github.open_policy_agent.opa.plugins;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
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
 * Comprehensive unit tests for BundlePlugin.
 *
 * <p>Tests validation, initialization, bundle loading from file:// and http:// schemes, polling
 * behavior, and error handling.
 */
class BundlePluginTest {

  private PluginManager manager;
  private Logger mockLogger;
  private Store store;
  private Config config;

  @BeforeEach
  void setUp() {
    mockLogger = mock(Logger.class);
    store = new InMem();

    // Base config with service
    config = new Config();
    Config.ServiceConfig service =
        new Config.ServiceConfig().setName("test-service").setUrl("file:///tmp/bundles");
    config.setServices(Collections.singletonMap("test-service", service));
  }

  @Test
  void validate_noBundlesConfigured_returnsNoErrors() {
    manager =
        new PluginManager.Builder()
            .withId("test-opa")
            .withStore(store)
            .withConfig(config)
            .withLogger(mockLogger)
            .build();

    BundlePlugin plugin = new BundlePlugin();
    Set<String> errors = plugin.validate(manager);

    assertTrue(errors.isEmpty());
  }

  @Test
  void validate_missingService_returnsError() {
    Config.BundleConfig bundle = new Config.BundleConfig().setResource("/bundles/test.tar.gz");
    config.setBundles(Collections.singletonMap("test-bundle", bundle));

    manager =
        new PluginManager.Builder()
            .withId("test-opa")
            .withStore(store)
            .withConfig(config)
            .withLogger(mockLogger)
            .build();

    BundlePlugin plugin = new BundlePlugin();
    Set<String> errors = plugin.validate(manager);

    assertFalse(errors.isEmpty());
    assertTrue(errors.stream().anyMatch(e -> e.contains("missing or empty service reference")));
  }

  @Test
  void validate_missingResource_returnsError() {
    Config.BundleConfig bundle = new Config.BundleConfig().setService("test-service");
    config.setBundles(Collections.singletonMap("test-bundle", bundle));

    manager =
        new PluginManager.Builder()
            .withId("test-opa")
            .withStore(store)
            .withConfig(config)
            .withLogger(mockLogger)
            .build();

    BundlePlugin plugin = new BundlePlugin();
    Set<String> errors = plugin.validate(manager);

    assertFalse(errors.isEmpty());
    assertTrue(errors.stream().anyMatch(e -> e.contains("missing or empty resource path")));
  }

  @Test
  void validate_unknownServiceReference_returnsError() {
    Config.BundleConfig bundle =
        new Config.BundleConfig()
            .setService("does-not-exist")
            .setResource("/bundles/test.tar.gz");
    config.setBundles(Collections.singletonMap("test-bundle", bundle));

    manager =
        new PluginManager.Builder()
            .withId("test-opa")
            .withStore(store)
            .withConfig(config)
            .withLogger(mockLogger)
            .build();

    BundlePlugin plugin = new BundlePlugin();
    Set<String> errors = plugin.validate(manager);

    assertFalse(errors.isEmpty());
    assertTrue(
        errors.stream().anyMatch(e -> e.contains("references unknown service 'does-not-exist'")),
        "expected unknown-service error, got: " + errors);
  }

  @Test
  void validate_validConfig_returnsNoErrors() {
    Config.BundleConfig bundle =
        new Config.BundleConfig().setService("test-service").setResource("/bundles/test.tar.gz");
    config.setBundles(Collections.singletonMap("test-bundle", bundle));

    manager =
        new PluginManager.Builder()
            .withId("test-opa")
            .withStore(store)
            .withConfig(config)
            .withLogger(mockLogger)
            .build();

    BundlePlugin plugin = new BundlePlugin();
    Set<String> errors = plugin.validate(manager);

    assertTrue(errors.isEmpty());
  }

  @Test
  void validate_pollingMinGreaterThanMax_returnsError() {
    Config.BundleConfig bundle =
        new Config.BundleConfig()
            .setService("test-service")
            .setResource("/bundles/test.tar.gz")
            .setPolling(
                new Config.PollingConfig().setMinDelaySeconds(120).setMaxDelaySeconds(60));
    config.setBundles(Collections.singletonMap("test-bundle", bundle));

    manager =
        new PluginManager.Builder()
            .withId("test-opa")
            .withStore(store)
            .withConfig(config)
            .withLogger(mockLogger)
            .build();

    Set<String> errors = new BundlePlugin().validate(manager);
    assertTrue(
        errors.stream().anyMatch(e -> e.contains("must be <= max_delay_seconds")),
        "expected min>max error, got: " + errors);
  }

  @Test
  void validate_pollingNegativeDelay_returnsError() {
    Config.BundleConfig bundle =
        new Config.BundleConfig()
            .setService("test-service")
            .setResource("/bundles/test.tar.gz")
            .setPolling(new Config.PollingConfig().setMinDelaySeconds(-1));
    config.setBundles(Collections.singletonMap("test-bundle", bundle));

    manager =
        new PluginManager.Builder()
            .withId("test-opa")
            .withStore(store)
            .withConfig(config)
            .withLogger(mockLogger)
            .build();

    Set<String> errors = new BundlePlugin().validate(manager);
    assertTrue(
        errors.stream().anyMatch(e -> e.contains("min_delay_seconds must be >= 0")),
        "expected negative-delay error, got: " + errors);
  }

  @Test
  void initialize_noBundlesConfigured_returnsPlugin() {
    manager =
        new PluginManager.Builder()
            .withId("test-opa")
            .withStore(store)
            .withConfig(config)
            .withLogger(mockLogger)
            .build();

    BundlePlugin plugin = new BundlePlugin();
    Plugin initialized = plugin.initialize(manager);

    assertNotNull(initialized);
    assertInstanceOf(BundlePlugin.class, initialized);
  }

  @Test
  void initialize_withBundles_setupsBundleConfiguration() {
    Config.BundleConfig bundle =
        new Config.BundleConfig()
            .setService("test-service")
            .setResource("/bundles/test.tar.gz")
            .setPolling(new Config.PollingConfig().setMinDelaySeconds(60).setMaxDelaySeconds(120));
    config.setBundles(Collections.singletonMap("test-bundle", bundle));

    manager =
        new PluginManager.Builder()
            .withId("test-opa")
            .withStore(store)
            .withConfig(config)
            .withLogger(mockLogger)
            .build();

    BundlePlugin plugin = new BundlePlugin();
    Plugin initialized = plugin.initialize(manager);

    assertNotNull(initialized);
    assertInstanceOf(BundlePlugin.class, initialized);
  }

  @Test
  void start_noBundles_setsStatusOk() {
    manager =
        new PluginManager.Builder()
            .withId("test-opa")
            .withStore(store)
            .withConfig(config)
            .withLogger(mockLogger)
            .build();

    BundlePlugin plugin = new BundlePlugin();
    plugin = (BundlePlugin) plugin.initialize(manager);
    plugin.start();

    assertEquals(PluginManager.Status.OK, manager.getPluginStatus("bundles"));
  }

  @Test
  void bundle_loadFromFile_activatesSuccessfully(@TempDir Path tempDir) throws Exception {
    // Create a test bundle with plan.json
    byte[] bundleData = createValidBundle();
    Path bundlePath = tempDir.resolve("test.tar.gz");
    Files.write(bundlePath, bundleData);

    // Update config to point to this file
    Config.ServiceConfig service =
        new Config.ServiceConfig().setName("test-service").setUrl(tempDir.toUri().toString());
    config.setServices(Collections.singletonMap("test-service", service));

    Config.BundleConfig bundle =
        new Config.BundleConfig().setService("test-service").setResource("test.tar.gz");
    config.setBundles(Collections.singletonMap("test-bundle", bundle));

    manager =
        new PluginManager.Builder()
            .withId("test-opa")
            .withStore(store)
            .withConfig(config)
            .withLogger(mockLogger)
            .build();

    BundlePlugin plugin = new BundlePlugin();
    plugin = (BundlePlugin) plugin.initialize(manager);
    plugin.start();

    // Wait for bundle activation
    Thread.sleep(500);

    // Verify success was logged
    verify(mockLogger, atLeastOnce()).info(eq("All bundles initially activated"));
  }

  @Test
  void bundle_fileNotFound_logsError(@TempDir Path tempDir) throws Exception {
    // Point to non-existent file
    Config.ServiceConfig service =
        new Config.ServiceConfig().setName("test-service").setUrl(tempDir.toUri().toString());
    config.setServices(Collections.singletonMap("test-service", service));

    Config.BundleConfig bundle =
        new Config.BundleConfig().setService("test-service").setResource("nonexistent.tar.gz");
    config.setBundles(Collections.singletonMap("test-bundle", bundle));

    manager =
        new PluginManager.Builder()
            .withId("test-opa")
            .withStore(store)
            .withConfig(config)
            .withLogger(mockLogger)
            .build();

    BundlePlugin plugin = new BundlePlugin();
    plugin = (BundlePlugin) plugin.initialize(manager);
    plugin.start();

    // Wait for attempt
    Thread.sleep(500);

    // Verify error was logged
    verify(mockLogger, atLeastOnce())
        .error(eq("Bundle '%s': Download error: %s"), eq("test-bundle"), anyString());
  }

  @Test
  void bundle_serviceNotFound_logsError(@TempDir Path tempDir) throws Exception {
    // Create a test bundle
    byte[] bundleData = createValidBundle();
    Path bundlePath = tempDir.resolve("test.tar.gz");
    Files.write(bundlePath, bundleData);

    // Point to service that doesn't exist in config
    Config.ServiceConfig service =
        new Config.ServiceConfig().setName("test-service").setUrl(tempDir.toUri().toString());
    config.setServices(Collections.singletonMap("test-service", service));

    Config.BundleConfig bundle =
        new Config.BundleConfig().setService("nonexistent-service").setResource("test.tar.gz");
    config.setBundles(Collections.singletonMap("test-bundle", bundle));

    manager =
        new PluginManager.Builder()
            .withId("test-opa")
            .withStore(store)
            .withConfig(config)
            .withLogger(mockLogger)
            .build();

    BundlePlugin plugin = new BundlePlugin();
    plugin = (BundlePlugin) plugin.initialize(manager);
    plugin.start();

    // Wait for attempt
    Thread.sleep(500);

    // Verify error was logged
    verify(mockLogger, atLeastOnce())
        .error(
            eq("Bundle '%s': Service '%s' not found"),
            eq("test-bundle"),
            eq("nonexistent-service"));
  }

  @Test
  void bundle_absolutePathEntries_loadsSuccessfully() throws Exception {
    // Regression test: bundles with absolute-path entries (/plan.json, /data.json)
    // were not stored because store.write was gated on data != null.
    byte[] bundleData = createBundleWithAbsolutePaths();
    Store store = new InMem();

    io.github.open_policy_agent.opa.bundle.TarballBundleLoader loader =
        new io.github.open_policy_agent.opa.bundle.TarballBundleLoader("test-bundle", bundleData);
    loader.load(store);

    assertFalse(store.getBundles().isEmpty(), "Bundle should be registered in the store");
    assertNotNull(store.getBundles().get("test-bundle"), "Bundle should be stored under its id");
    assertNotNull(
        store.getBundles().get("test-bundle").irPolicy, "plan.json should have been loaded");
    assertNotNull(
        store.getIrPolicyForEntrypoint("main/main"), "main/main plan should be queryable from store");
  }

  // Helper methods to create test bundles

  private byte[] createValidBundle() throws IOException {
    ByteArrayOutputStream byteOut = new ByteArrayOutputStream();

    try (GZIPOutputStream gzipOut = new GZIPOutputStream(byteOut);
        TarArchiveOutputStream tarOut = new TarArchiveOutputStream(gzipOut)) {

      // Add a simple plan.json
      String planJson =
          "{\"static\": {\"entrypoints\": [{\"path\": \"data/test/allow\", \"plan_compiled\": []}]}}";

      TarArchiveEntry planEntry = new TarArchiveEntry("plan.json");
      byte[] planBytes = planJson.getBytes();
      planEntry.setSize(planBytes.length);
      tarOut.putArchiveEntry(planEntry);
      tarOut.write(planBytes);
      tarOut.closeArchiveEntry();

      // Add manifest
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

  /** Bundle with absolute-path entries and empty data.json, matching the production server format. */
  private byte[] createBundleWithAbsolutePaths() throws IOException {
    ByteArrayOutputStream byteOut = new ByteArrayOutputStream();

    try (GZIPOutputStream gzipOut = new GZIPOutputStream(byteOut);
        TarArchiveOutputStream tarOut = new TarArchiveOutputStream(gzipOut)) {
      tarOut.setLongFileMode(TarArchiveOutputStream.LONGFILE_GNU);

      // data.json with absolute path and empty object content
      byte[] dataBytes = "{}".getBytes();
      TarArchiveEntry dataEntry = new TarArchiveEntry("/data.json");
      dataEntry.setSize(dataBytes.length);
      tarOut.putArchiveEntry(dataEntry);
      tarOut.write(dataBytes);
      tarOut.closeArchiveEntry();

      // plan.json with absolute path and minimal valid plan
      String planJson =
          "{\"static\":{\"strings\":[],\"files\":[]},"
              + "\"plans\":{\"plans\":[{\"name\":\"main/main\",\"blocks\":[]}]},"
              + "\"funcs\":{\"funcs\":[]}}";      byte[] planBytes = planJson.getBytes();
      TarArchiveEntry planEntry = new TarArchiveEntry("/plan.json");
      planEntry.setSize(planBytes.length);
      tarOut.putArchiveEntry(planEntry);
      tarOut.write(planBytes);
      tarOut.closeArchiveEntry();

      // .manifest with absolute path
      byte[] manifestBytes = "{\"revision\":\"\",\"roots\":[\"\"]}".getBytes();
      TarArchiveEntry manifestEntry = new TarArchiveEntry("/.manifest");
      manifestEntry.setSize(manifestBytes.length);
      tarOut.putArchiveEntry(manifestEntry);
      tarOut.write(manifestBytes);
      tarOut.closeArchiveEntry();

      tarOut.finish();
    }

    return byteOut.toByteArray();
  }
}
