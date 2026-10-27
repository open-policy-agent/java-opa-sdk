package io.github.open_policy_agent.opa.plugins;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.github.open_policy_agent.opa.config.Config;
import io.github.open_policy_agent.opa.plugins.DiscoveryTestSupport.RecordingLogger;
import io.github.open_policy_agent.opa.storage.InMem;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.LinkedHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import javax.net.ssl.SSLContext;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.dataformat.yaml.YAMLMapper;

/**
 * Pins OPA's discovery semantics (path selection, env substitution, defaults, boot/discovered
 * merge). Boot config is parsed from YAML exactly as {@code Opa.Builder} does; every assertion is
 * on the {@link PluginManager} config or status after discovery settles.
 */
class DiscoveryParityTest {

  private static final ObjectMapper YAML = YAMLMapper.shared();
  private static final String DISCOVERY_RESOURCE = "d.tar.gz";
  private static final String SERVICES =
      "services:\n  svc:\n    url: @URL@\n";

  @TempDir Path tempDir;

  private final RecordingLogger logger = new RecordingLogger();
  private PluginManager manager;
  private DiscoveryPlugin plugin;

  @AfterEach
  void stopPlugin() {
    if (plugin != null) {
      plugin.stop();
    }
  }

  private static String discovery(String extraLines) {
    return "discovery:\n  service: svc\n  resource: " + DISCOVERY_RESOURCE + "\n" + extraLines;
  }

  private static String fast() {
    return "  polling:\n    min_delay_seconds: 1\n    max_delay_seconds: 1\n";
  }

  private void build(String bootYaml) throws IOException {
    final String yaml = bootYaml.replace("@URL@", tempDir.toUri().toString());
    manager =
        new PluginManager.Builder()
            .withId("test-opa")
            .withStore(new InMem())
            .withConfig(YAML.readValue(yaml, Config.class))
            .withBootConfigTree(YAML.readTree(yaml))
            .withLogger(logger)
            .build();
  }

  private void activate() throws Exception {
    plugin = (DiscoveryPlugin) new DiscoveryPlugin().initialize(manager);
    plugin.start();
    final long deadline = System.currentTimeMillis() + 5000;
    while (System.currentTimeMillis() < deadline) {
      final PluginManager.Status status = manager.getPluginStatus("discovery");
      if (status == PluginManager.Status.OK || status == PluginManager.Status.ERROR) {
        return;
      }
      Thread.sleep(25);
    }
  }

  private void run(String bootYaml, String dataJson) throws Exception {
    DiscoveryTestSupport.write(tempDir, DISCOVERY_RESOURCE, DiscoveryTestSupport.dataBundle(dataJson));
    build(bootYaml);
    activate();
  }

  private String label(String key) {
    final Map<String, String> labels = manager.getConfig().getLabels();
    return labels == null ? null : labels.get(key);
  }

  // ---- path selection ----

  @Test
  void decisionSelectsTheNestedNode() throws Exception {
    run(
        SERVICES + discovery("  decision: example/discovery\n"),
        "{\"labels\":{\"src\":\"root\"},\"example\":{\"discovery\":{\"labels\":{\"src\":\"nested\"}}}}");

    assertEquals("nested", label("src"));
  }

  @Test
  void decisionLeadingAndTrailingSlashesAreTrimmed() throws Exception {
    run(
        SERVICES + discovery("  decision: /example/discovery/\n"),
        "{\"example\":{\"discovery\":{\"labels\":{\"src\":\"nested\"}}}}");

    assertEquals("nested", label("src"));
  }

  @Test
  void nameIsTheFallbackWhenDecisionIsUnset() throws Exception {
    run(
        SERVICES + discovery("  name: example/discovery\n"),
        "{\"labels\":{\"src\":\"root\"},\"example\":{\"discovery\":{\"labels\":{\"src\":\"nested\"}}}}");

    assertEquals("nested", label("src"));
  }

  @Test
  void dataRootIsTheFallbackWhenNeitherDecisionNorNameIsSet() throws Exception {
    run(SERVICES + discovery(""), "{\"labels\":{\"src\":\"root\"}}");

    assertEquals("root", label("src"));
  }

  @Test
  void decisionPointingAtAMissingPathFailsInitialActivation() throws Exception {
    run(
        SERVICES + discovery("  decision: example/absent\n"),
        "{\"labels\":{\"src\":\"root\"}}");

    assertEquals(PluginManager.Status.ERROR, manager.getPluginStatus("discovery"));
    assertTrue(logger.contains("undefined configuration"), logger.lines()::toString);
  }

  @Test
  void decisionPointingAtAMissingPathOnUpdateKeepsThePreviousConfig() throws Exception {
    run(
        SERVICES + discovery("  decision: example/discovery\n" + fast()),
        "{\"example\":{\"discovery\":{\"labels\":{\"src\":\"first\"}}}}");
    assertEquals("first", label("src"));

    DiscoveryTestSupport.write(
        tempDir,
        DISCOVERY_RESOURCE,
        DiscoveryTestSupport.dataBundle("{\"other\":{\"labels\":{\"src\":\"second\"}}}"));
    Thread.sleep(2500);

    assertEquals("first", label("src"));
    assertTrue(logger.contains("undefined configuration"), logger.lines()::toString);
  }

  // ---- env substitution on discovered data ----

  @Test
  void definedEnvVarsAreSubstitutedInDiscoveredData() throws Exception {
    final String home = System.getenv("HOME");
    assumeTrue(home != null && home.matches("[A-Za-z0-9_./:\\-]+"), "HOME must be JSON-safe");

    run(SERVICES + discovery(""), "{\"labels\":{\"p\":\"${HOME}\"}}");

    assertEquals(home, label("p"));
  }

  @Test
  void undefinedEnvVarsAreLeftAsWrittenInDiscoveredData() throws Exception {
    run(SERVICES + discovery(""), "{\"labels\":{\"p\":\"${DISCOVERY_PARITY_UNDEFINED}\"}}");

    assertEquals(PluginManager.Status.OK, manager.getPluginStatus("discovery"));
    assertEquals("${DISCOVERY_PARITY_UNDEFINED}", label("p"));
  }

  // ---- defaults ----

  @Test
  void nameWithoutResourceDownloadsFromBundlesSlashName() throws Exception {
    DiscoveryTestSupport.write(
        tempDir,
        "bundles/disca",
        DiscoveryTestSupport.dataBundle("{\"disca\":{\"labels\":{\"k\":\"v\"}}}"));
    build(SERVICES + "discovery:\n  service: svc\n  name: disca\n");

    assertTrue(new DiscoveryPlugin().validate(manager).isEmpty());
    activate();
    assertEquals("v", label("k"));
  }

  @Test
  void serviceIsOptionalWhenExactlyOneServiceIsDefined() throws Exception {
    DiscoveryTestSupport.write(
        tempDir, DISCOVERY_RESOURCE, DiscoveryTestSupport.dataBundle("{\"labels\":{\"k\":\"v\"}}"));
    build(SERVICES + "discovery:\n  resource: " + DISCOVERY_RESOURCE + "\n");

    assertTrue(new DiscoveryPlugin().validate(manager).isEmpty());
    activate();
    assertEquals("v", label("k"));
  }

  @Test
  void missingServiceWithSeveralServicesIsAValidationError() throws Exception {
    build(SERVICES + "  other:\n    url: https://other.example.test\n"
        + "discovery:\n  resource: " + DISCOVERY_RESOURCE + "\n");

    final Set<String> errors = new DiscoveryPlugin().validate(manager);

    assertTrue(errors.stream().anyMatch(e -> e.contains("more than one service")), errors::toString);
  }

  // ---- merge of discovered config over boot config ----

  @Test
  void bootServicesSurviveWhenDiscoveredConfigOmitsThem() throws Exception {
    run(
        SERVICES + discovery(""),
        "{\"bundles\":{\"b\":{\"service\":\"svc\",\"resource\":\"b.tar.gz\"}}}");

    assertNotNull(manager.getConfig().getService("svc"));
    assertNotNull(manager.getConfig().getBundles().get("b"));
    assertTrue(new BundlePlugin().validate(manager).isEmpty(), "bundle must resolve the boot service");
  }

  @Test
  void discoveredServicesAreAddedAlongsideBootServices() throws Exception {
    run(
        SERVICES + discovery(""),
        "{\"services\":{\"extra\":{\"url\":\"https://extra.example.test\"}}}");

    assertNotNull(manager.getConfig().getService("svc"));
    assertNotNull(manager.getConfig().getService("extra"));
  }

  @Test
  void bootScalarWinsOverDiscoveredScalar() throws Exception {
    run(
        SERVICES + discovery("") + "default_decision: boot/decision\n",
        "{\"default_decision\":\"discovered/decision\"}");

    assertEquals("boot/decision", manager.getConfig().getDefaultDecision());
  }

  @Test
  void discoveredOnlyKeysAreAdded() throws Exception {
    run(SERVICES + discovery(""), "{\"labels\":{\"env\":\"prod\"},\"default_decision\":\"discovered/decision\"}");

    assertEquals("prod", label("env"));
    assertEquals("discovered/decision", manager.getConfig().getDefaultDecision());
  }

  @Test
  void fieldsUnsetInBootNeverOverrideDiscoveredValues() throws Exception {
    run(
        SERVICES + discovery("") + "decision_logs:\n  console: true\n",
        "{\"decision_logs\":{\"min_delay_seconds\":60,\"max_delay_seconds\":90}}");

    final Config.DecisionLogsConfig logs = manager.getConfig().getDecisionLogs();
    assertEquals(Boolean.TRUE, logs.getConsole());
    assertEquals(Integer.valueOf(60), logs.getMinDelaySeconds());
    assertEquals(Integer.valueOf(90), logs.getMaxDelaySeconds());
  }

  @Test
  void discoveryBlockAlwaysComesFromBootConfig() throws Exception {
    run(
        SERVICES + discovery(""),
        "{\"discovery\":{\"service\":\"svc\",\"resource\":\"other.tar.gz\"}}");

    assertEquals(DISCOVERY_RESOURCE, manager.getConfig().getDiscovery().getResource());
  }

  @Test
  void bootLabelsWinAndDiscoveredOnlyLabelsAreKept() throws Exception {
    run(
        SERVICES + discovery("") + "labels:\n  a: boot\n  b: boot\n",
        "{\"labels\":{\"a\":\"discovered\",\"c\":\"discovered\"}}");

    assertEquals(Map.of("a", "boot", "b", "boot", "c", "discovered"), manager.getConfig().getLabels());
  }

  @Test
  void discoveredChangeToTheDiscoveryServiceIsAnError() throws Exception {
    run(
        SERVICES + discovery(""),
        "{\"services\":{\"svc\":{\"url\":\"https://elsewhere.example.test\"}}}");

    assertEquals(PluginManager.Status.ERROR, manager.getPluginStatus("discovery"));
    assertTrue(
        logger.contains("updates to the discovery service are not allowed"), logger.lines()::toString);
  }

  @Test
  void overriddenKeysAreReportedInTheDiscoveryStatusMessage() throws Exception {
    run(
        SERVICES + discovery("") + "default_decision: boot/decision\n",
        "{\"default_decision\":\"discovered/decision\"}");

    assertEquals(
        "Keys in the discovered configuration overridden by boot configuration: default_decision",
        manager.getPluginStatusMessage("discovery"));
  }

  @Test
  void unsupportedDiscoveryKeysAreWarnedAbout() throws Exception {
    run(SERVICES + discovery("  persist: true\n"), "{}");

    assertEquals(PluginManager.Status.OK, manager.getPluginStatus("discovery"));
    assertTrue(logger.contains("unsupported configuration key 'persist'"), logger.lines()::toString);
  }

  @Test
  void regoFilesAreRejectedEvenWhenTheyComeAfterAnUndefinedConfigPath() throws Exception {
    final Map<String, String> entries = new LinkedHashMap<>();
    entries.put("data.json", "{}");
    entries.put("policy.rego", "package discovery\n");
    DiscoveryTestSupport.write(tempDir, DISCOVERY_RESOURCE, DiscoveryTestSupport.tarGz(entries));
    build(SERVICES + discovery("  decision: example/absent\n"));
    activate();

    assertEquals(PluginManager.Status.ERROR, manager.getPluginStatus("discovery"));
    assertTrue(logger.contains(".rego files"), logger.lines()::toString);
    assertFalse(logger.contains("undefined configuration"), logger.lines()::toString);
  }

  @Test
  void unchangedDiscoveryPollsDoNotNotifyConfigListeners() throws Exception {
    final byte[] bundle = DiscoveryTestSupport.dataBundle("{\"labels\":{\"k\":\"v\"}}");
    final HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
    server.createContext(
        "/" + DISCOVERY_RESOURCE,
        exchange -> {
          exchange.sendResponseHeaders(200, bundle.length);
          exchange.getResponseBody().write(bundle);
          exchange.close();
        });
    server.start();
    try {
      build(
          "services:\n  svc:\n    url: http://localhost:"
              + server.getAddress().getPort()
              + "\n"
              + discovery(fast()));
      final AtomicInteger updates = new AtomicInteger();
      manager.registerConfigUpdateListener((oldConfig, newConfig) -> updates.incrementAndGet());
      activate();
      Thread.sleep(2500);

      assertEquals(1, updates.get());
    } finally {
      server.stop(0);
    }
  }

  private void buildTyped(Config boot) {
    manager =
        new PluginManager.Builder()
            .withId("test-opa")
            .withStore(new InMem())
            .withConfig(boot)
            .withLogger(logger)
            .build();
  }

  private Config typedBoot() throws Exception {
    return new Config()
        .setServices(
            Map.of(
                "svc",
                new Config.ServiceConfig()
                    .setName("svc")
                    .setUrl(tempDir.toUri().toString())
                    .setSslContext(SSLContext.getDefault())))
        .setDiscovery(new Config.DiscoveryConfig().setService("svc").setResource(DISCOVERY_RESOURCE));
  }

  @Test
  void javaFieldDefaultsInATypedBootConfigDoNotOverrideDiscoveredValues() throws Exception {
    DiscoveryTestSupport.write(
        tempDir,
        DISCOVERY_RESOURCE,
        DiscoveryTestSupport.dataBundle("{\"default_decision\":\"discovered/decision\"}"));
    buildTyped(typedBoot());
    activate();

    assertEquals("discovered/decision", manager.getConfig().getDefaultDecision());
  }

  @Test
  void aTypedBootConfigKeepsItsSslContextAfterDiscovery() throws Exception {
    DiscoveryTestSupport.write(
        tempDir, DISCOVERY_RESOURCE, DiscoveryTestSupport.dataBundle("{\"labels\":{\"k\":\"v\"}}"));
    final Config boot = typedBoot();
    buildTyped(boot);
    activate();

    assertEquals("v", label("k"));
    assertSame(
        boot.getService("svc").getSslContext(), manager.getConfig().getService("svc").getSslContext());
  }

  @Test
  void overriddenKeysAreLogged() throws Exception {
    run(
        SERVICES + discovery("") + "default_decision: boot/decision\n",
        "{\"default_decision\":\"discovered/decision\"}");

    assertTrue(
        logger.contains(
            "Keys in the discovered configuration overridden by boot configuration: default_decision"),
        logger.lines()::toString);
  }
}
