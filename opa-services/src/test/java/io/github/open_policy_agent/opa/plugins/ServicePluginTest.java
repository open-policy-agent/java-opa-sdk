package io.github.open_policy_agent.opa.plugins;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import io.github.open_policy_agent.opa.config.Config;
import io.github.open_policy_agent.opa.logging.Logger;
import io.github.open_policy_agent.opa.storage.InMem;
import io.github.open_policy_agent.opa.storage.Store;

/**
 * Comprehensive unit tests for ServicePlugin.
 *
 * <p>Tests validation, initialization, credential configuration, and HTTP client setup.
 */
class ServicePluginTest {

  private PluginManager manager;
  private Logger mockLogger;
  private Store store;
  private Config config;

  @BeforeEach
  void setUp() {
    mockLogger = mock(Logger.class);
    store = new InMem();
    config = new Config();
  }

  @Test
  void validate_noServicesConfigured_returnsNoErrors() {
    manager =
        new PluginManager.Builder()
            .withId("test-opa")
            .withStore(store)
            .withConfig(config)
            .withLogger(mockLogger)
            .build();

    ServicePlugin plugin = new ServicePlugin();
    Set<String> errors = plugin.validate(manager);

    assertTrue(errors.isEmpty());
  }

  @Test
  void validate_missingUrl_returnsError() {
    Config.ServiceConfig service = new Config.ServiceConfig().setName("test-service");
    config.setServices(Collections.singletonMap("test-service", service));

    manager =
        new PluginManager.Builder()
            .withId("test-opa")
            .withStore(store)
            .withConfig(config)
            .withLogger(mockLogger)
            .build();

    ServicePlugin plugin = new ServicePlugin();
    Set<String> errors = plugin.validate(manager);

    assertFalse(errors.isEmpty());
    assertTrue(errors.stream().anyMatch(e -> e.contains("missing or empty URL")));
  }

  @Test
  void validate_validServiceWithUrl_returnsNoErrors() {
    Config.ServiceConfig service =
        new Config.ServiceConfig().setName("test-service").setUrl("https://example.com");
    config.setServices(Collections.singletonMap("test-service", service));

    manager =
        new PluginManager.Builder()
            .withId("test-opa")
            .withStore(store)
            .withConfig(config)
            .withLogger(mockLogger)
            .build();

    ServicePlugin plugin = new ServicePlugin();
    Set<String> errors = plugin.validate(manager);

    assertTrue(errors.isEmpty());
  }

  @Test
  void validate_validServiceWithBearerToken_returnsNoErrors() {
    Config.ServiceConfig service =
        new Config.ServiceConfig().setName("test-service").setUrl("https://example.com");

    Config.CredentialsConfig credentials = new Config.CredentialsConfig();
    credentials.setBearer(new Config.BearerConfig().setToken("test-token"));
    service.setCredentials(credentials);

    config.setServices(Collections.singletonMap("test-service", service));

    manager =
        new PluginManager.Builder()
            .withId("test-opa")
            .withStore(store)
            .withConfig(config)
            .withLogger(mockLogger)
            .build();

    ServicePlugin plugin = new ServicePlugin();
    Set<String> errors = plugin.validate(manager);

    assertTrue(errors.isEmpty());
  }

  @Test
  void validate_serviceNameFromMapKey_setsName() {
    // Service without name set - should be set from map key
    Config.ServiceConfig service = new Config.ServiceConfig().setUrl("https://example.com");
    config.setServices(Collections.singletonMap("my-service", service));

    manager =
        new PluginManager.Builder()
            .withId("test-opa")
            .withStore(store)
            .withConfig(config)
            .withLogger(mockLogger)
            .build();

    ServicePlugin plugin = new ServicePlugin();
    Set<String> errors = plugin.validate(manager);

    assertTrue(errors.isEmpty());
    assertEquals("my-service", service.getName());
  }

  @Test
  void initialize_noServices_returnsPlugin() {
    // Empty services map
    config.setServices(new HashMap<>());

    manager =
        new PluginManager.Builder()
            .withId("test-opa")
            .withStore(store)
            .withConfig(config)
            .withLogger(mockLogger)
            .build();

    ServicePlugin plugin = new ServicePlugin();
    Plugin initialized = plugin.initialize(manager);

    assertNotNull(initialized);
    assertInstanceOf(ServicePlugin.class, initialized);
  }

  @Test
  void initialize_withServices_createsHttpClients() {
    Config.ServiceConfig service1 =
        new Config.ServiceConfig()
            .setName("service1")
            .setUrl("https://example1.com")
            .setResponseHeaderTimeoutSeconds(30);

    Config.ServiceConfig service2 =
        new Config.ServiceConfig()
            .setName("service2")
            .setUrl("https://example2.com")
            .setAllowInsecureTLS(true);

    Map<String, Config.ServiceConfig> services = new HashMap<>();
    services.put("service1", service1);
    services.put("service2", service2);
    config.setServices(services);

    manager =
        new PluginManager.Builder()
            .withId("test-opa")
            .withStore(store)
            .withConfig(config)
            .withLogger(mockLogger)
            .build();

    ServicePlugin plugin = new ServicePlugin();
    Plugin initialized = plugin.initialize(manager);

    assertNotNull(initialized);
    assertInstanceOf(ServicePlugin.class, initialized);
  }

  @Test
  void initialize_withBearerCredentials_setsCredentials() {
    Config.ServiceConfig service =
        new Config.ServiceConfig().setName("test-service").setUrl("https://example.com");

    Config.CredentialsConfig credentials = new Config.CredentialsConfig();
    credentials.setBearer(new Config.BearerConfig().setToken("my-secret-token"));
    service.setCredentials(credentials);

    config.setServices(Collections.singletonMap("test-service", service));

    manager =
        new PluginManager.Builder()
            .withId("test-opa")
            .withStore(store)
            .withConfig(config)
            .withLogger(mockLogger)
            .build();

    ServicePlugin plugin = new ServicePlugin();
    Plugin initialized = plugin.initialize(manager);

    assertNotNull(initialized);
    assertInstanceOf(ServicePlugin.class, initialized);
  }

  @Test
  void start_setsStatusOk() {
    Config.ServiceConfig service =
        new Config.ServiceConfig().setName("test-service").setUrl("https://example.com");
    config.setServices(Collections.singletonMap("test-service", service));

    manager =
        new PluginManager.Builder()
            .withId("test-opa")
            .withStore(store)
            .withConfig(config)
            .withLogger(mockLogger)
            .build();

    ServicePlugin plugin = new ServicePlugin();
    plugin = (ServicePlugin) plugin.initialize(manager);
    plugin.start();

    assertEquals(PluginManager.Status.OK, manager.getPluginStatus("services"));
  }

  @Test
  void validate_multipleServices_allValid_returnsNoErrors() {
    Map<String, Config.ServiceConfig> services = new HashMap<>();

    services.put(
        "service1", new Config.ServiceConfig().setName("service1").setUrl("https://example1.com"));

    services.put(
        "service2", new Config.ServiceConfig().setName("service2").setUrl("https://example2.com"));

    services.put(
        "service3", new Config.ServiceConfig().setName("service3").setUrl("https://example3.com"));

    config.setServices(services);

    manager =
        new PluginManager.Builder()
            .withId("test-opa")
            .withStore(store)
            .withConfig(config)
            .withLogger(mockLogger)
            .build();

    ServicePlugin plugin = new ServicePlugin();
    Set<String> errors = plugin.validate(manager);

    assertTrue(errors.isEmpty());
  }

  @Test
  void initialize_nullServices_returnsPlugin() {
    // Minimal config: new Config() with no services set (getServices() returns null)
    manager =
        new PluginManager.Builder()
            .withId("test-opa")
            .withStore(store)
            .withConfig(config)
            .withLogger(mockLogger)
            .build();

    ServicePlugin plugin = new ServicePlugin();
    Plugin initialized = plugin.initialize(manager);

    assertNotNull(initialized);
    assertInstanceOf(ServicePlugin.class, initialized);
  }

  @Test
  void validate_multipleServices_oneInvalid_returnsError() {
    Map<String, Config.ServiceConfig> services = new HashMap<>();

    services.put(
        "service1", new Config.ServiceConfig().setName("service1").setUrl("https://example1.com"));

    services.put("service2", new Config.ServiceConfig().setName("service2")); // Missing URL

    config.setServices(services);

    manager =
        new PluginManager.Builder()
            .withId("test-opa")
            .withStore(store)
            .withConfig(config)
            .withLogger(mockLogger)
            .build();

    ServicePlugin plugin = new ServicePlugin();
    Set<String> errors = plugin.validate(manager);

    assertFalse(errors.isEmpty());
    assertTrue(errors.stream().anyMatch(e -> e.contains("service2")));
    assertTrue(errors.stream().anyMatch(e -> e.contains("missing or empty URL")));
  }

  @Test
  void validate_tls_allowInsecureTlsConflict_returnsError() {
    Config.ServiceConfig service =
        new Config.ServiceConfig()
            .setName("s")
            .setUrl("https://example.com")
            .setAllowInsecureTLS(true)
            .setSslContext(mock(javax.net.ssl.SSLContext.class));
    config.setServices(Collections.singletonMap("s", service));

    manager =
        new PluginManager.Builder()
            .withId("t")
            .withStore(store)
            .withConfig(config)
            .withLogger(mockLogger)
            .build();

    Set<String> errors = new ServicePlugin().validate(manager);
    assertTrue(errors.stream().anyMatch(e -> e.contains("allow_insecure_tls=true alongside")));
  }

  @Test
  void validate_tls_programmaticAndFileConflict_returnsError() {
    Config.ServiceConfig service =
        new Config.ServiceConfig()
            .setName("s")
            .setUrl("https://example.com")
            .setTls(new Config.TlsConfig().setCaCert("/nonexistent/ca.pem"))
            .setSslContext(mock(javax.net.ssl.SSLContext.class));
    config.setServices(Collections.singletonMap("s", service));

    manager =
        new PluginManager.Builder()
            .withId("t")
            .withStore(store)
            .withConfig(config)
            .withLogger(mockLogger)
            .build();

    Set<String> errors = new ServicePlugin().validate(manager);
    assertTrue(errors.stream().anyMatch(e -> e.contains("programmatic SSLContext alongside")));
  }

  @Test
  void validate_tls_emptyBlock_returnsError() {
    // tls block with neither ca_cert nor system_ca_required=true is a no-op — likely a typo.
    Config.ServiceConfig service =
        new Config.ServiceConfig()
            .setName("s")
            .setUrl("https://example.com")
            .setTls(new Config.TlsConfig());
    config.setServices(Collections.singletonMap("s", service));

    manager =
        new PluginManager.Builder()
            .withId("t")
            .withStore(store)
            .withConfig(config)
            .withLogger(mockLogger)
            .build();

    Set<String> errors = new ServicePlugin().validate(manager);
    assertTrue(errors.stream().anyMatch(e -> e.contains("tls block has no effect")));
  }

  @Test
  void validate_tls_certWithoutKey_returnsError() {
    Config.ServiceConfig service =
        new Config.ServiceConfig()
            .setName("s")
            .setUrl("https://example.com")
            .setCredentials(
                new Config.CredentialsConfig()
                    .setClientTls(new Config.ClientTlsConfig().setCert("/some/cert.pem")));
    config.setServices(Collections.singletonMap("s", service));

    manager =
        new PluginManager.Builder()
            .withId("t")
            .withStore(store)
            .withConfig(config)
            .withLogger(mockLogger)
            .build();

    Set<String> errors = new ServicePlugin().validate(manager);
    assertTrue(errors.stream().anyMatch(e -> e.contains("must set both cert and private_key")));
  }

  @Test
  void validate_tls_bearerAndClientTls_returnsError() {
    Config.ClientTlsConfig ctls =
        new Config.ClientTlsConfig().setCert("/c.pem").setPrivateKey("/k.pem");
    Config.CredentialsConfig creds =
        new Config.CredentialsConfig()
            .setBearer(new Config.BearerConfig().setToken("abc"))
            .setClientTls(ctls);
    Config.ServiceConfig service =
        new Config.ServiceConfig().setName("s").setUrl("https://example.com").setCredentials(creds);
    config.setServices(Collections.singletonMap("s", service));

    manager =
        new PluginManager.Builder()
            .withId("t")
            .withStore(store)
            .withConfig(config)
            .withLogger(mockLogger)
            .build();

    Set<String> errors = new ServicePlugin().validate(manager);
    assertTrue(
        errors.stream().anyMatch(e -> e.contains("both bearer and client_tls")),
        "expected bearer/client_tls conflict but got " + errors);
  }

  @Test
  void validate_tls_negativeRereadInterval_returnsError() {
    Config.ServiceConfig service =
        new Config.ServiceConfig()
            .setName("s")
            .setUrl("https://example.com")
            .setCredentials(
                new Config.CredentialsConfig()
                    .setClientTls(
                        new Config.ClientTlsConfig()
                            .setCert("/c.pem")
                            .setPrivateKey("/k.pem")
                            .setCertRereadIntervalSeconds(-1)));
    config.setServices(Collections.singletonMap("s", service));

    manager =
        new PluginManager.Builder()
            .withId("t")
            .withStore(store)
            .withConfig(config)
            .withLogger(mockLogger)
            .build();

    Set<String> errors = new ServicePlugin().validate(manager);
    assertTrue(errors.stream().anyMatch(e -> e.contains("cert_reread_interval_seconds must be >= 0")));
  }

  @Test
  void validate_tls_rereadIntervalWithoutCert_returnsError() {
    Config.ServiceConfig service =
        new Config.ServiceConfig()
            .setName("s")
            .setUrl("https://example.com")
            .setCredentials(
                new Config.CredentialsConfig()
                    .setClientTls(new Config.ClientTlsConfig().setCertRereadIntervalSeconds(60)));
    config.setServices(Collections.singletonMap("s", service));

    manager =
        new PluginManager.Builder()
            .withId("t")
            .withStore(store)
            .withConfig(config)
            .withLogger(mockLogger)
            .build();

    Set<String> errors = new ServicePlugin().validate(manager);
    assertTrue(
        errors.stream()
            .anyMatch(e -> e.contains("cert_reread_interval_seconds requires both cert")),
        "expected reread-without-cert error, got " + errors);
  }

  @Test
  void stop_shutsDownCertReloadScheduler() throws Exception {
    Config.ServiceConfig service =
        new Config.ServiceConfig().setName("s").setUrl("https://example.com");
    config.setServices(Collections.singletonMap("s", service));

    manager =
        new PluginManager.Builder()
            .withId("t")
            .withStore(store)
            .withConfig(config)
            .withLogger(mockLogger)
            .build();

    ServicePlugin plugin = (ServicePlugin) new ServicePlugin().initialize(manager);

    java.lang.reflect.Field f = ServicePlugin.class.getDeclaredField("certReloadScheduler");
    f.setAccessible(true);
    java.util.concurrent.ScheduledExecutorService scheduler =
        (java.util.concurrent.ScheduledExecutorService) f.get(plugin);
    assertNotNull(scheduler, "scheduler should be created when services are configured");
    assertFalse(scheduler.isShutdown(), "scheduler must be running before stop()");

    plugin.stop();

    assertTrue(scheduler.isShutdown(), "scheduler must be shut down after stop()");
  }
}
