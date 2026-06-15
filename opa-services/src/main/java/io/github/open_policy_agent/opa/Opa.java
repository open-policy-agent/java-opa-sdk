package io.github.open_policy_agent.opa;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.*;
import io.github.open_policy_agent.opa.ast.types.RegoValue;
import io.github.open_policy_agent.opa.bundle.Bundle;
import io.github.open_policy_agent.opa.config.Config;
import io.github.open_policy_agent.opa.config.ConfigurationException;
import io.github.open_policy_agent.opa.config.EnvInterpolator;
import io.github.open_policy_agent.opa.logging.Logger;
import io.github.open_policy_agent.opa.mapper.RegoMapper;
import io.github.open_policy_agent.opa.metrics.Metrics;
import io.github.open_policy_agent.opa.metrics.NoOpMetrics;
import io.github.open_policy_agent.opa.metrics.SimpleMetrics;
import io.github.open_policy_agent.opa.plugins.BundlePlugin;
import io.github.open_policy_agent.opa.plugins.DecisionLogPlugin;
import io.github.open_policy_agent.opa.plugins.DiscoveryPlugin;
import io.github.open_policy_agent.opa.plugins.Plugin;
import io.github.open_policy_agent.opa.plugins.PluginInitializationException;
import io.github.open_policy_agent.opa.plugins.PluginManager;
import io.github.open_policy_agent.opa.plugins.ServicePlugin;
import io.github.open_policy_agent.opa.plugins.StatusPlugin;
import io.github.open_policy_agent.opa.rego.Engine;
import io.github.open_policy_agent.opa.rego.EvaluationContext;
import io.github.open_policy_agent.opa.storage.InMem;
import io.github.open_policy_agent.opa.storage.Store;
import io.github.open_policy_agent.opa.tracing.Profiler;

/**
 * Full OPA runtime with plugin management and configuration support.
 *
 * <p>Opa provides a complete OPA runtime environment including:
 *
 * <ul>
 *   <li>Bundle management with automatic updates
 *   <li>Decision logging to remote services
 *   <li>Status reporting
 *   <li>Service discovery and management
 *   <li>Plugin lifecycle management
 * </ul>
 *
 * <p><b>When to use Opa:</b>
 *
 * <ul>
 *   <li>You need a complete OPA deployment with plugins
 *   <li>You want automatic bundle updates from a remote server
 *   <li>You need decision logging for audit and compliance
 *   <li>You want to use OPA configuration files (YAML/JSON)
 * </ul>
 *
 * <p><b>Basic Example:</b>
 *
 * <pre>{@code
 * // By default, build() waits for all plugins to be ready
 * Opa opa = new Opa.Builder()
 *     .withConfigFile("opa-config.yaml")
 *     .withDefaultEntrypoint("example/allow")
 *     .build(); // Blocks until all plugins are OK
 *
 * // OPA is ready to use immediately after build()
 * JsonNode input = mapper.readTree("{\"user\": \"bob\"}");
 * DecisionResult result = opa.makeDecision(input);
 * boolean allowed = result.getResult().asBoolean();
 * }</pre>
 *
 * <p><b>Configuration File Example (opa-config.yaml):</b>
 *
 * <pre>{@code
 * services:
 *   acmecorp:
 *     url: https://example.com/control-plane-api/v1
 *     credentials:
 *       bearer:
 *         token: "my-secret-token"
 *
 * bundles:
 *   authz:
 *     service: acmecorp
 *     resource: bundles/http/example/authz.tar.gz
 *     polling:
 *       min_delay_seconds: 60
 *       max_delay_seconds: 120
 *
 * decision_logs:
 *   service: acmecorp
 *   reporting:
 *     min_delay_seconds: 300
 * }</pre>
 *
 * @see Engine for direct policy evaluation without plugins
 */
public class Opa {

  private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());
  private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

  private final String id;
  private final Logger logger;
  private final Store store;
  private final PluginManager manager;
  private final String defaultEntrypoint;
  private final boolean showMetrics;
  private Engine engine;
  private Thread shutdownHook;

  private Opa(Builder builder, final String defaultEntrypoint) {
    this.id = builder.id;
    this.logger = builder.logger;
    this.store = builder.store;
    this.manager = builder.manager;
    this.showMetrics = builder.showMetrics;
    this.defaultEntrypoint = defaultEntrypoint;

    PluginManager.PluginStatusListener[] listenerRef = new PluginManager.PluginStatusListener[1];
    listenerRef[0] =
        (pluginName, status) -> {
          if ("bundles".equals(pluginName) && status == PluginManager.Status.OK) {
            engine = new Engine.Builder().withStore(store).withEntrypoint(defaultEntrypoint).build();
            manager.deregisterPluginStatusListener(listenerRef[0]);
          }
        };
    manager.registerPluginStatusListener(listenerRef[0]);

    manager.registerBundleActivationListener(
        (bundleName) -> {
          if (engine != null) {
            logger.info("Bundle '%s' activated, refreshing engine", bundleName);
            engine.refresh();
          }
        });

    shutdownHook = new Thread(this::shutdown, "opa-shutdown-hook");
    Runtime.getRuntime().addShutdownHook(shutdownHook);
  }

  public boolean ready() {
    return engine != null && manager.allPluginsOk();
  }

  /**
   * Explicitly close the OPA instance and clean up resources.
   *
   * <p>This method performs a clean shutdown and removes the shutdown hook. It's recommended to
   * call this method when done with the OPA instance to ensure proper resource cleanup.
   *
   * <p>After calling close(), this OPA instance should not be used.
   */
  public void close() {
    try {
      if (shutdownHook != null) {
        Runtime.getRuntime().removeShutdownHook(shutdownHook);
        shutdownHook = null;
      }
    } catch (IllegalStateException e) {
      // Shutdown already in progress, ignore
    }
    shutdown();
  }

  private void shutdown() {
    try {
      logger.info("Shutting down OPA instance...");

      DecisionLogPlugin decisionLogPlugin = (DecisionLogPlugin) manager.getPlugin("decision_logs");
      if (decisionLogPlugin != null && decisionLogPlugin.getDecisionLogs() != null) {
        logger.info("Flushing pending decision logs...");
        decisionLogPlugin.flush();
      }

      manager.stopAllPlugins();
      logger.info("OPA shutdown complete");
    } catch (Exception e) {
      logger.error("Error during OPA shutdown: %s", e.getMessage());
    }
  }

  /**
   * Evaluate a policy decision with full options control.
   *
   * @param options decision options including input, metrics, profiler, and decision ID
   * @return the decision result with provenance information
   */
  public DecisionResult makeDecision(DecisionOptions options) {
    String decisionId = resolveDecisionId(options);
    EvaluationContext ctx = buildContext(options);
    // Engine now operates on POJO trees (Map/List/primitives) so the evaluator stays Jackson-free.
    // Bridge JsonNode <-> POJO here at the opa-services boundary.
    Object pojoInput = JSON_MAPPER.convertValue(options.getInput(), Object.class);
    List<Object> rawResults = engine.evaluate(ctx, pojoInput);
    JsonNode resultNode = JSON_MAPPER.valueToTree(rawResults.get(0));

    logDecision(decisionId, options.getInput(), resultNode, options, ctx);

    return new DecisionResult()
        .setResult(resultNode)
        .setId(decisionId)
        .setProvenance(buildProvenance());
  }

  public DecisionResult makeDecision(JsonNode input) {
    return makeDecision(new DecisionOptions().setInput(input));
  }

  public DecisionResult makeDecision(String input) {
    try {
      JsonNode inputNode = YAML_MAPPER.readTree(input);
      return makeDecision(inputNode);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Evaluate a policy decision with a POJO input and return a typed result. This is the most
   * efficient path — it bypasses all intermediate JsonNode allocations by converting the POJO
   * directly to/from internal RegoValues.
   *
   * <pre>{@code
   * MyInput input = new MyInput();
   * input.setUser("alice");
   * MyResult result = opa.makeDecision(input, MyResult.class);
   * boolean allowed = result.isAllowed();
   * }</pre>
   *
   * @param <T> the result type
   * @param input the input as a POJO (any JavaBean-compatible object)
   * @param resultType the class of the desired result type
   * @return the typed decision result
   */
  public <T> T makeDecision(Object input, Class<T> resultType) {
    EvaluationContext ctx = buildContext(null);
    List<T> results = engine.evaluate(ctx, input, resultType);
    return results.get(0);
  }

  private EvaluationContext buildContext(DecisionOptions options) {
    boolean useMetrics = showMetrics || (options != null && options.showMetrics);
    Metrics metrics = useMetrics ? new SimpleMetrics() : NoOpMetrics.Instance();

    EvaluationContext.Builder builder =
        new EvaluationContext.Builder()
            .withStore(store)
            .withMetrics(metrics)
            .withEntrypoint(defaultEntrypoint);

    if (options != null && options.getProfiler() != null) {
      builder.withProfiler(options.getProfiler());
    }

    return builder.build();
  }

  private static String resolveDecisionId(DecisionOptions options) {
    if (options.decisionID != null && !options.decisionID.isEmpty()) {
      return options.decisionID;
    }
    return UUID.randomUUID().toString();
  }

  private void logDecision(
      String decisionId,
      JsonNode input,
      JsonNode result,
      DecisionOptions options,
      EvaluationContext ctx) {
    DecisionLogPlugin plugin = (DecisionLogPlugin) manager.getPlugin("decision_logs");
    if (plugin == null) {
      return;
    }

    String path =
        (options.getPath() != null && !options.getPath().isEmpty())
            ? options.getPath()
            : (defaultEntrypoint != null && !defaultEntrypoint.isEmpty()) ? defaultEntrypoint : "";

    plugin.logDecision(
        decisionId, input, result, path,
        ctx.getEvalStartTime(), ctx.metrics, ctx.getNdCacheValues());
  }

  private Provenance buildProvenance() {
    Provenance provenance = new Provenance();
    String version = Opa.class.getPackage().getImplementationVersion();
    provenance.setVersion(version != null ? version : "unknown");

    Map<String, Provenance.ProvenanceBundle> bundleProvenance = new HashMap<>();
    for (Map.Entry<String, Bundle> entry : store.getBundles().entrySet()) {
      Bundle bundle = entry.getValue();
      if (bundle.manifest != null && bundle.manifest.containsKey("revision")) {
        Provenance.ProvenanceBundle pb = new Provenance.ProvenanceBundle();
        pb.setRevision(String.valueOf(bundle.manifest.get("revision")));
        bundleProvenance.put(entry.getKey(), pb);
      }
    }

    if (!bundleProvenance.isEmpty()) {
      provenance.setBundles(bundleProvenance);
    }
    return provenance;
  }

  public static class DecisionResult {
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();
    private static final RegoMapper REGO_MAPPER = new RegoMapper();

    private String id;
    private JsonNode result;
    private RegoValue rawResult;
    private Provenance provenance;

    private DecisionResult() {}

    public String getId() {
      return id;
    }

    private DecisionResult setId(String id) {
      this.id = id;
      return this;
    }

    public JsonNode getResult() {
      if (result == null && rawResult != null) {
        result = JSON_MAPPER.valueToTree(rawResult);
      }
      return result;
    }

    private DecisionResult setResult(JsonNode result) {
      this.result = result;
      return this;
    }

    private DecisionResult setRawResult(RegoValue rawResult) {
      this.rawResult = rawResult;
      return this;
    }

    /**
     * Convert the decision result to a typed POJO. Uses {@link RegoMapper} for direct conversion
     * when a raw RegoValue is available, or falls back to Jackson for JsonNode results.
     *
     * <pre>{@code
     * DecisionResult dr = opa.makeDecision(input);
     * MyResult typed = dr.getResultAs(MyResult.class);
     * }</pre>
     *
     * @param <T> the result type
     * @param type the class of the desired result type
     * @return the typed result
     */
    public <T> T getResultAs(Class<T> type) {
      if (rawResult != null) {
        return REGO_MAPPER.fromRegoValue(rawResult, type);
      }
      try {
        return JSON_MAPPER.treeToValue(result, type);
      } catch (Exception e) {
        throw new RuntimeException("Failed to convert result to " + type.getName(), e);
      }
    }

    public Provenance getProvenance() {
      return provenance;
    }

    private DecisionResult setProvenance(Provenance provenance) {
      this.provenance = provenance;
      return this;
    }
  }

  public static class DecisionOptions {
    private long nowNs;
    private String path;
    private JsonNode input;
    private Object ndbCache;
    private boolean strictBuiltinErrors;
    boolean showMetrics;
    private Profiler profiler;
    private boolean instrument;
    String decisionID;

    public long getNowNs() {
      return nowNs;
    }

    public DecisionOptions setNowNs(long nowNs) {
      this.nowNs = nowNs;
      return this;
    }

    public String getPath() {
      return path;
    }

    public DecisionOptions setPath(String path) {
      this.path = path;
      return this;
    }

    public JsonNode getInput() {
      return input;
    }

    public DecisionOptions setInput(JsonNode input) {
      this.input = input;
      return this;
    }

    public Object getNdbCache() {
      return ndbCache;
    }

    public DecisionOptions setNdbCache(Object ndbCache) {
      this.ndbCache = ndbCache;
      return this;
    }

    public boolean isStrictBuiltinErrors() {
      return strictBuiltinErrors;
    }

    public DecisionOptions setStrictBuiltinErrors(boolean strictBuiltinErrors) {
      this.strictBuiltinErrors = strictBuiltinErrors;
      return this;
    }

    public boolean getShowMetrics() {
      return showMetrics;
    }

    public DecisionOptions showMetrics() {
      this.showMetrics = true;
      return this;
    }

    public Profiler getProfiler() {
      return profiler;
    }

    public DecisionOptions setProfiler(Profiler profiler) {
      this.profiler = profiler;
      return this;
    }

    public boolean isInstrument() {
      return instrument;
    }

    public DecisionOptions setInstrument(boolean instrument) {
      this.instrument = instrument;
      return this;
    }

    public String getDecisionID() {
      return decisionID;
    }

    public DecisionOptions setDecisionID(String decisionID) {
      this.decisionID = decisionID;
      return this;
    }
  }

  public static class Provenance {
    private String version;
    private String buildCommit;
    private String buildTimestamp;
    private String buildHostname;
    private Map<String, ProvenanceBundle> bundles;

    Provenance() {}

    public String getVersion() {
      return version;
    }

    void setVersion(String version) {
      this.version = version;
    }

    public String getBuildCommit() {
      return buildCommit;
    }

    Provenance setBuildCommit(String buildCommit) {
      this.buildCommit = buildCommit;
      return this;
    }

    public String getBuildTimestamp() {
      return buildTimestamp;
    }

    Provenance setBuildTimestamp(String buildTimestamp) {
      this.buildTimestamp = buildTimestamp;
      return this;
    }

    public String getBuildHostname() {
      return buildHostname;
    }

    Provenance setBuildHostname(String buildHostname) {
      this.buildHostname = buildHostname;
      return this;
    }

    public Map<String, ProvenanceBundle> getBundles() {
      return bundles;
    }

    void setBundles(Map<String, ProvenanceBundle> bundles) {
      this.bundles = bundles;
    }

    public static class ProvenanceBundle {
      private String revision;

      ProvenanceBundle() {}

      public String getRevision() {
        return revision;
      }

      void setRevision(String revision) {
        this.revision = revision;
      }
    }
  }

  /**
   * Builder for creating Opa instances with a full plugin-based runtime.
   *
   * <p>Use this builder when you need:
   *
   * <ul>
   *   <li>Bundle management with automatic updates
   *   <li>Decision logging to remote services
   *   <li>Status reporting
   *   <li>Plugin lifecycle management
   * </ul>
   *
   * <p><b>Default Behavior:</b> By default, {@code build()} blocks until all plugins reach OK
   * status. This ensures the OPA instance is fully ready to evaluate policies immediately after
   * construction. This behavior can be overridden with {@link #withWaitForPlugins(boolean)}.
   *
   * <p>Example usage:
   *
   * <pre>{@code
   * // Blocking (default) - ready immediately after build()
   * Opa opa = new Opa.Builder()
   *     .withConfigFile("opa-config.yaml")
   *     .withDefaultEntrypoint("example/allow")
   *     .build(); // Blocks until all plugins are OK
   *
   * }</pre>
   */
  public static class Builder {
    private String id;
    private Logger logger;
    private Reader configIn;
    private Config config;
    private final Map<String, Plugin> userPlugins = new HashMap<>();
    private Store store;
    private PluginManager manager;
    private String defaultEntrypoint;
    private boolean showMetrics = false;
    private boolean waitForPlugins = true;

    /**
     * Set the default entrypoint to evaluate. This entrypoint will be used when makeDecision()
     * is called without specifying an entrypoint path.
     *
     * @param defaultEntrypoint the entrypoint path (e.g., "example/allow")
     * @return this builder
     */
    public Builder withDefaultEntrypoint(String defaultEntrypoint) {
      this.defaultEntrypoint = defaultEntrypoint;
      return this;
    }

    /**
     * Set a custom logger implementation. If not provided, uses StandardLogger which writes to
     * System.out.
     *
     * @param logger the logger implementation
     * @return this builder
     */
    public Builder withLogger(Logger logger) {
      this.logger = logger;
      return this;
    }

    public Builder withMetrics() {
      this.showMetrics = true;
      return this;
    }

    /**
     * Set OPA configuration directly from a Config object. This bypasses file/reader parsing and
     * allows full programmatic configuration.
     *
     * @param config the configuration object
     * @return this builder
     */
    public Builder withConfig(Config config) {
      this.config = config;
      return this;
    }

    /**
     * Load OPA configuration from a Reader. The configuration should be in YAML or JSON format and
     * define services, bundles, decision logs, and other plugin settings.
     *
     * @param configIn reader containing the configuration
     * @return this builder
     */
    public Builder withConfig(Reader configIn) {
      this.configIn = configIn;
      return this;
    }

    /**
     * Load OPA configuration from a file. The file should be in YAML or JSON format. If not
     * specified, the builder will look for "opa-config.yaml" or "opa-config.json" in the current
     * directory.
     *
     * @param configFile path to the configuration file
     * @return this builder
     * @throws RuntimeException if the file cannot be found
     */
    public Builder withConfigFile(String configFile) {
      try {
        this.configIn = new FileReader(configFile);
      } catch (FileNotFoundException e) {
        throw new RuntimeException(e);
      }
      return this;
    }

    /**
     * Register a custom plugin. Custom plugins are initialized and started after the built-in OPA
     * plugins (services, bundles, decision_logs, status).
     *
     * @param name the plugin name (used for configuration and lifecycle)
     * @param plugin the plugin implementation
     * @return this builder
     */
    public Builder withPlugin(String name, Plugin plugin) {
      this.userPlugins.put(name, plugin);
      return this;
    }

    /**
     * Set a unique identifier for this OPA instance. This ID is used in decision logs, status
     * reports, and plugin management. If not provided, a random UUID is generated.
     *
     * @param id the instance identifier
     * @return this builder
     */
    public Builder withId(String id) {
      this.id = id;
      return this;
    }

    /**
     * Control whether build() waits for all plugins to be ready before returning.
     *
     * <p>By default (waitForPlugins = true), build() will block until all plugins have reached OK
     * status. This ensures the OPA instance is fully ready to evaluate policies immediately after
     * build() returns.
     *
     * <p>If set to false, build() returns immediately and plugins initialize in the background. Use
     * opa.ready() to check if plugins have finished initialization.
     *
     * @param waitForPlugins true to wait for all plugins (default), false to return immediately
     * @return this builder
     */
    public Builder withWaitForPlugins(boolean waitForPlugins) {
      this.waitForPlugins = waitForPlugins;
      return this;
    }

    public Opa build() {
      if (logger == null) {
        logger = new Logger.StandardLogger();
      }
      if (id == null) {
        id = UUID.randomUUID().toString();
      }
      if (config == null && configIn == null) {
        try {
          configIn = new FileReader("opa-config.yaml");
        } catch (FileNotFoundException e) {
          try {
            configIn = new FileReader("opa-config.json");
          } catch (FileNotFoundException ex) {
            throw new ConfigurationException("No OPA configuration found");
          }
        }
      }
      if (config == null) {
        try {
          // Read fully so we can apply ${VAR} env-var interpolation before parsing. Avoids
          // plaintext secrets in committed YAML; matches Go-OPA's config-loading behaviour.
          char[] buf = new char[8192];
          StringBuilder raw = new StringBuilder();
          int n;
          while ((n = configIn.read(buf)) > 0) {
            raw.append(buf, 0, n);
          }
          String interpolated = EnvInterpolator.interpolate(raw.toString());
          config = YAML_MAPPER.readValue(interpolated, Config.class);
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
      store = new InMem();
      manager =
          new PluginManager.Builder()
              .withId(id)
              .withStore(store)
              .withConfig(config)
              .withLogger(logger)
              .build();

      if (config.getDiscovery() != null) {
        initializeWithDiscovery();
      } else {
        initializeWithoutDiscovery();
      }

      if (defaultEntrypoint == null) {
        defaultEntrypoint = store.getDefaultEntrypoint();
      }

      return new Opa(this, defaultEntrypoint);
    }

    private void initializeWithDiscovery() {
      Map<String, Plugin> phase1Plugins = new LinkedHashMap<>();
      phase1Plugins.put("services", new ServicePlugin());
      phase1Plugins.put("discovery", new DiscoveryPlugin());

      validateInitializeAndStart(phase1Plugins);

      logger.info("Waiting for discovery plugin to complete initial activation...");
      waitForDiscoveryComplete();

      PluginManager.Status discoveryStatus = manager.getPluginStatus("discovery");
      if (discoveryStatus == PluginManager.Status.ERROR) {
        throw new PluginInitializationException(
            "Discovery plugin failed to initialize. Cannot start OPA without valid configuration.");
      }

      logger.info("Discovery plugin activated successfully");

      Map<String, Plugin> phase2Plugins = new LinkedHashMap<>();
      phase2Plugins.put("bundles", new BundlePlugin());
      phase2Plugins.put("decision_logs", new DecisionLogPlugin());
      phase2Plugins.put("status", new StatusPlugin());
      phase2Plugins.putAll(userPlugins);

      validateInitializeAndStart(phase2Plugins);

      if (waitForPlugins) {
        waitForAllPluginsReady();
      }
    }

    private void initializeWithoutDiscovery() {
      Map<String, Plugin> allPlugins = new LinkedHashMap<>();
      allPlugins.put("services", new ServicePlugin());
      allPlugins.put("bundles", new BundlePlugin());
      allPlugins.put("decision_logs", new DecisionLogPlugin());
      allPlugins.put("status", new StatusPlugin());
      allPlugins.putAll(userPlugins);

      validateInitializeAndStart(allPlugins);

      if (waitForPlugins) {
        waitForAllPluginsReady();
      }
    }

    private void validateInitializeAndStart(Map<String, Plugin> plugins) {
      for (Map.Entry<String, Plugin> plugin : plugins.entrySet()) {
        Set<String> errors = plugin.getValue().validate(manager);
        if (!errors.isEmpty()) {
          String errorMessages = String.join(", ", errors);
          throw new PluginInitializationException(
                  "Invalid plugin config for '" + plugin.getKey() + "': " + errorMessages)
              .withContext("pluginName", plugin.getKey())
              .withContext("errors", errors);
        }
      }

      for (Map.Entry<String, Plugin> plugin : plugins.entrySet()) {
        Plugin initializedPlugin = plugin.getValue().initialize(manager);
        manager.registerPlugin(plugin.getKey(), initializedPlugin);
      }

      for (Map.Entry<String, Plugin> plugin : plugins.entrySet()) {
        Plugin registeredPlugin = manager.getPlugin(plugin.getKey());
        if (registeredPlugin != null) {
          registeredPlugin.start();
        }
      }
    }

    private void waitForDiscoveryComplete() {
      int maxWaitMs = 30000;
      int waitedMs = 0;
      int pollIntervalMs = 100;

      while (waitedMs < maxWaitMs) {
        PluginManager.Status status = manager.getPluginStatus("discovery");

        if (status == PluginManager.Status.OK || status == PluginManager.Status.ERROR) {
          return;
        }

        try {
          Thread.sleep(pollIntervalMs);
          waitedMs += pollIntervalMs;
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          throw new PluginInitializationException("Interrupted while waiting for discovery", e);
        }
      }

      throw new PluginInitializationException(
          "Discovery plugin did not complete within " + (maxWaitMs / 1000) + " seconds");
    }

    private void waitForAllPluginsReady() {
      logger.info("Waiting for all plugins to be ready...");

      java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
      final String[] errorStatus = {null};

      PluginManager.PluginStatusListener[] listenerRef = new PluginManager.PluginStatusListener[1];
      listenerRef[0] =
          (pluginName, status) -> {
            if (status == PluginManager.Status.ERROR) {
              errorStatus[0] = pluginName + "=" + status;
              latch.countDown();
              return;
            }
            boolean allOk = true;
            for (String name :
                new String[] {"services", "bundles", "decision_logs", "status", "discovery"}) {
              PluginManager.Status s = manager.getPluginStatus(name);
              if (s != null && s != PluginManager.Status.OK) {
                allOk = false;
                break;
              }
            }
            if (allOk) {
              for (String name : userPlugins.keySet()) {
                PluginManager.Status s = manager.getPluginStatus(name);
                if (s != null && s != PluginManager.Status.OK) {
                  allOk = false;
                  break;
                }
              }
            }
            if (allOk) {
              latch.countDown();
            }
          };

      manager.registerPluginStatusListener(listenerRef[0]);

      try {
        if (!latch.await(60, java.util.concurrent.TimeUnit.SECONDS)) {
          throw new PluginInitializationException(
              "Plugins did not reach OK status within 60 seconds");
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new PluginInitializationException(
            "Interrupted while waiting for plugins to be ready", e);
      } finally {
        manager.deregisterPluginStatusListener(listenerRef[0]);
      }

      if (errorStatus[0] != null) {
        throw new PluginInitializationException(
            "One or more plugins failed to initialize: " + errorStatus[0]);
      }

      logger.info("All plugins ready.");
    }
  }
}
