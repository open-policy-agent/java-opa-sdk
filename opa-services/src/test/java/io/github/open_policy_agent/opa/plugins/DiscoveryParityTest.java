package io.github.open_policy_agent.opa.plugins;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.sun.net.httpserver.HttpServer;
import io.github.open_policy_agent.opa.config.Config;
import io.github.open_policy_agent.opa.plugins.DiscoveryTestSupport.RecordingLogger;
import io.github.open_policy_agent.opa.storage.InMem;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import javax.net.ssl.SSLContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.dataformat.yaml.YAMLMapper;

/**
 * Pins the discovery semantics: path selection, env substitution, defaults, and the merge of the
 * discovered config under the boot config. Boot config is parsed from YAML exactly as {@code
 * Opa.Builder} does; every assertion is on the {@link PluginManager} config or status after
 * discovery settles. JSON in the tests uses single quotes for readability.
 */
class DiscoveryParityTest {

  private static final ObjectMapper YAML = YAMLMapper.shared();
  private static final String RESOURCE = "d.tar.gz";
  private static final String SERVICES = "services:\n  svc:\n    url: @URL@\n";
  private static final String NESTED =
      "{'labels':{'src':'root'},'example':{'discovery':{'labels':{'src':'nested'}}}}";
  private static final String FAST = "  polling:\n    min_delay_seconds: 1\n    max_delay_seconds: 1\n";

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
    return "discovery:\n  service: svc\n  resource: " + RESOURCE + "\n" + extraLines;
  }

  private void write(String path, byte[] bundle) throws IOException {
    DiscoveryTestSupport.write(tempDir, path, bundle);
  }

  private void write(String dataJson) throws IOException {
    write(RESOURCE, DiscoveryTestSupport.dataBundle(dataJson.replace('\'', '"')));
  }

  private void build(String bootYaml) throws IOException {
    final String yaml = bootYaml.replace("@URL@", tempDir.toUri().toString());
    buildFrom(YAML.readValue(yaml, Config.class), YAML.readTree(yaml));
  }

  private void buildFrom(Config boot, JsonNode tree) {
    manager =
        new PluginManager.Builder()
            .withId("test-opa")
            .withStore(new InMem())
            .withConfig(boot)
            .withBootConfigTree(tree)
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
    write(dataJson);
    build(bootYaml);
    activate();
  }

  private String label(String key) {
    final Map<String, String> labels = manager.getConfig().getLabels();
    return labels == null ? null : labels.get(key);
  }

  private void assertLogged(String fragment) {
    assertTrue(logger.contains(fragment), logger.lines()::toString);
  }

  // ---- path selection ----

  static Stream<Arguments> configPaths() {
    return Stream.of(
        Arguments.of("  decision: example/discovery\n", NESTED, "nested"),
        Arguments.of("  decision: /example/discovery/\n", NESTED, "nested"),
        Arguments.of("  name: example/discovery\n", NESTED, "nested"),
        Arguments.of("", "{'labels':{'src':'root'}}", "root"));
  }

  @ParameterizedTest
  @MethodSource("configPaths")
  void theConfigIsReadFromDecisionThenNameThenTheDataRoot(
      String lines, String data, String expected) throws Exception {
    run(SERVICES + discovery(lines), data);

    assertEquals(expected, label("src"));
  }

  @Test
  void decisionPointingAtAMissingPathFailsInitialActivation() throws Exception {
    run(SERVICES + discovery("  decision: example/absent\n"), "{'labels':{'src':'root'}}");

    assertEquals(PluginManager.Status.ERROR, manager.getPluginStatus("discovery"));
    assertLogged("undefined configuration");
  }

  @Test
  void decisionPointingAtAMissingPathOnUpdateKeepsThePreviousConfig() throws Exception {
    run(SERVICES + discovery("  decision: example/discovery\n" + FAST), NESTED);
    assertEquals("nested", label("src"));

    write("{'other':{'labels':{'src':'second'}}}");
    Thread.sleep(2500);

    assertEquals("nested", label("src"));
    assertLogged("undefined configuration");
  }

  // ---- env substitution on discovered data ----

  @Test
  void definedEnvVarsAreSubstitutedInDiscoveredData() throws Exception {
    final String home = System.getenv("HOME");
    assumeTrue(home != null, "HOME must be set");

    run(SERVICES + discovery(""), "{'labels':{'p':'${HOME}'}}");

    assertEquals(home, label("p"));
  }

  // ---- defaults ----

  @Test
  void nameWithoutResourceDownloadsFromBundlesSlashName() throws Exception {
    write("bundles/disca", DiscoveryTestSupport.dataBundle("{\"disca\":{\"labels\":{\"k\":\"v\"}}}"));
    build(SERVICES + "discovery:\n  service: svc\n  name: disca\n");

    assertTrue(new DiscoveryPlugin().validate(manager).isEmpty());
    activate();
    assertEquals("v", label("k"));
  }

  @Test
  void serviceIsOptionalWhenExactlyOneServiceIsDefined() throws Exception {
    write("{'labels':{'k':'v'}}");
    build(SERVICES + "discovery:\n  resource: " + RESOURCE + "\n");

    assertTrue(new DiscoveryPlugin().validate(manager).isEmpty());
    activate();
    assertEquals("v", label("k"));
  }

  @Test
  void missingServiceWithSeveralServicesIsAValidationError() throws Exception {
    build(SERVICES + "  other:\n    url: https://other.example.test\n" + discovery("").replace("  service: svc\n", ""));

    final var errors = new DiscoveryPlugin().validate(manager);

    assertTrue(errors.stream().anyMatch(e -> e.contains("more than one service")), errors::toString);
  }

  // ---- merge of discovered config under boot config ----

  @Test
  void bootServicesAreKeptAndDiscoveredServicesAdded() throws Exception {
    run(
        SERVICES + discovery(""),
        "{'bundles':{'b':{'service':'svc','resource':'b.tar.gz'}},"
            + "'services':{'extra':{'url':'https://extra.example.test'}}}");

    assertNotNull(manager.getConfig().getService("svc"));
    assertNotNull(manager.getConfig().getService("extra"));
    assertNotNull(manager.getConfig().getBundles().get("b"));
    assertTrue(new BundlePlugin().validate(manager).isEmpty(), "bundle must resolve the boot service");
  }

  @Test
  void bootValuesWinAndDiscoveredOnlyKeysAreAdded() throws Exception {
    run(
        SERVICES + discovery("") + "default_decision: boot/decision\nlabels:\n  a: boot\n  b: boot\n",
        "{'default_decision':'discovered/decision','labels':{'a':'discovered','c':'discovered'},"
            + "'discovery':{'service':'svc','resource':'other.tar.gz'}}");

    assertEquals("boot/decision", manager.getConfig().getDefaultDecision());
    assertEquals(Map.of("a", "boot", "b", "boot", "c", "discovered"), manager.getConfig().getLabels());
    assertEquals(RESOURCE, manager.getConfig().getDiscovery().getResource());
  }

  @Test
  void fieldsUnsetInBootNeverOverrideDiscoveredValues() throws Exception {
    run(
        SERVICES + discovery("") + "decision_logs:\n  console: true\n",
        "{'decision_logs':{'min_delay_seconds':60,'max_delay_seconds':90}}");

    final Config.DecisionLogsConfig logs = manager.getConfig().getDecisionLogs();
    assertEquals(Boolean.TRUE, logs.getConsole());
    assertEquals(Integer.valueOf(60), logs.getMinDelaySeconds());
    assertEquals(Integer.valueOf(90), logs.getMaxDelaySeconds());
  }

  @Test
  void discoveredChangeToTheDiscoveryServiceIsAnError() throws Exception {
    run(SERVICES + discovery(""), "{'services':{'svc':{'url':'https://elsewhere.example.test'}}}");

    assertEquals(PluginManager.Status.ERROR, manager.getPluginStatus("discovery"));
    assertLogged("updates to the discovery service are not allowed");
  }

  @Test
  void overriddenKeysAreInTheDiscoveryStatusAndLogged() throws Exception {
    run(
        SERVICES + discovery("") + "default_decision: boot/decision\n",
        "{'default_decision':'discovered/decision'}");

    final String message =
        "Keys in the discovered configuration overridden by boot configuration: default_decision";
    assertEquals(message, manager.discoveryStatus().message());
    assertLogged(message);
  }

  @Test
  void unsupportedDiscoveryKeysAreWarnedAbout() throws Exception {
    run(SERVICES + discovery("  persist: true\n"), "{}");

    assertEquals(PluginManager.Status.OK, manager.getPluginStatus("discovery"));
    assertLogged("unsupported configuration key 'persist'");
  }

  // ---- bundle handling ----

  @Test
  void regoFilesAreRejectedEvenWhenTheyComeAfterAnUndefinedConfigPath() throws Exception {
    final Map<String, String> entries = new LinkedHashMap<>();
    entries.put("data.json", "{}");
    entries.put("policy.rego", "package discovery\n");
    write(RESOURCE, DiscoveryTestSupport.tarGz(entries));
    build(SERVICES + discovery("  decision: example/absent\n"));
    activate();

    assertEquals(PluginManager.Status.ERROR, manager.getPluginStatus("discovery"));
    assertLogged(".rego files");
    assertFalse(logger.contains("undefined configuration"), logger.lines()::toString);
  }

  @Test
  void unchangedDiscoveryPollsDoNotNotifyConfigListeners() throws Exception {
    final byte[] bundle = DiscoveryTestSupport.dataBundle("{\"labels\":{\"k\":\"v\"}}");
    final HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
    server.createContext(
        "/" + RESOURCE,
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
              + discovery(FAST));
      final AtomicInteger updates = new AtomicInteger();
      manager.registerConfigUpdateListener((oldConfig, newConfig) -> updates.incrementAndGet());
      activate();
      Thread.sleep(2500);

      assertEquals(1, updates.get());
    } finally {
      server.stop(0);
    }
  }

  private Config.ServiceConfig buildTypedBootWithSslContext() throws Exception {
    final Config.ServiceConfig service =
        new Config.ServiceConfig()
            .setName("svc")
            .setUrl(tempDir.toUri().toString())
            .setSslContext(SSLContext.getDefault());
    buildFrom(
        new Config()
            .setServices(Map.of("svc", service))
            .setDiscovery(new Config.DiscoveryConfig().setService("svc").setResource(RESOURCE)),
        null);
    return service;
  }

  @Test
  void aTypedBootConfigKeepsItsSslContextAndIgnoresJavaFieldDefaults() throws Exception {
    write("{'default_decision':'discovered/decision','labels':{'k':'v'}}");
    final Config.ServiceConfig service = buildTypedBootWithSslContext();
    activate();

    assertEquals("v", label("k"));
    assertEquals("discovered/decision", manager.getConfig().getDefaultDecision());
    assertSame(service.getSslContext(), manager.getConfig().getService("svc").getSslContext());
  }

  @Test
  void aTypedBootServiceKeepsItsSslContextWhenDiscoveryRepeatsIt() throws Exception {
    write("{'services':{'svc':{'url':'" + tempDir.toUri() + "'}},'labels':{'k':'v'}}");
    final Config.ServiceConfig service = buildTypedBootWithSslContext();
    activate();

    assertEquals("v", label("k"));
    assertSame(service.getSslContext(), manager.getConfig().getService("svc").getSslContext());
  }

  @Test
  void prefixIsUsedForTheDefaultResource() throws Exception {
    write("custom/disca", DiscoveryTestSupport.dataBundle("{\"disca\":{\"labels\":{\"k\":\"v\"}}}"));
    build(SERVICES + "discovery:\n  service: svc\n  name: disca\n  prefix: /custom/\n");

    assertTrue(new DiscoveryPlugin().validate(manager).isEmpty());
    activate();
    assertEquals("v", label("k"));
    assertFalse(logger.contains("unsupported configuration key 'prefix'"), logger.lines()::toString);
  }

  @Test
  void discoveredKeysTheSdkDoesNotModelAreIgnored() throws Exception {
    run(
        SERVICES + discovery(""),
        "{'labels':{'k':'v'},'caching':{'inter_query_builtin_cache':{'max_size_bytes':1}},"
            + "'keys':{'k1':{'key':'x'}},'server':{'encoding':{'gzip':{}}},"
            + "'distributed_tracing':{'type':'grpc'},'persistence_directory':'/tmp/x',"
            + "'plugins':{'p':{}},'bundles':{'b':{'service':'svc','resource':'b.tar.gz','persist':true,"
            + "'signing':{'keyid':'k1'},'trigger':'manual'}}}");

    assertEquals(PluginManager.Status.OK, manager.getPluginStatus("discovery"));
    assertEquals("v", label("k"));
  }
}
