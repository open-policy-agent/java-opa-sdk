package io.github.open_policy_agent.opa.plugins;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
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
    services.put("service2", new Config.ServiceConfig().setName("service2"));

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

  @ParameterizedTest(name = "{0}")
  @MethodSource("invalidTlsCases")
  void validate_tls_invalidCases_returnError(
      String caseName, Consumer<Config.ServiceConfig> mutator, String expectedFragment) {
    Config.ServiceConfig service =
        new Config.ServiceConfig().setName("s").setUrl("https://example.com");
    mutator.accept(service);
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
        errors.stream().anyMatch(e -> e.contains(expectedFragment)),
        "expected error containing '" + expectedFragment + "' but got " + errors);
  }

  static java.util.stream.Stream<Arguments> invalidTlsCases() {
    return java.util.stream.Stream.of(
        Arguments.of(
            "allowInsecureTls + programmatic SSLContext",
            (Consumer<Config.ServiceConfig>)
                s -> s.setAllowInsecureTLS(true).setSslContext(mock(javax.net.ssl.SSLContext.class)),
            "allow_insecure_tls=true alongside"),
        Arguments.of(
            "programmatic SSLContext + ca_cert",
            (Consumer<Config.ServiceConfig>)
                s ->
                    s.setTls(new Config.TlsConfig().setCaCert("/nonexistent/ca.pem"))
                        .setSslContext(mock(javax.net.ssl.SSLContext.class)),
            "programmatic SSLContext alongside"),
        Arguments.of(
            "tls block has neither ca_cert nor system_ca_required",
            (Consumer<Config.ServiceConfig>) s -> s.setTls(new Config.TlsConfig()),
            "tls block has no effect"),
        Arguments.of(
            "tls.ca_cert + tls.truststore",
            (Consumer<Config.ServiceConfig>)
                s ->
                    s.setTls(
                        new Config.TlsConfig()
                            .setCaCert("/etc/ca.pem")
                            .setTruststore(new Config.TruststoreConfig().setPath("/etc/ts.jks"))),
            "both ca_cert and truststore"),
        Arguments.of(
            "client_tls cert without private_key",
            (Consumer<Config.ServiceConfig>)
                s ->
                    s.setCredentials(
                        new Config.CredentialsConfig()
                            .setClientTls(new Config.ClientTlsConfig().setCert("/some/cert.pem"))),
            "must set both cert and private_key"),
        Arguments.of(
            "client_tls keystore + cert",
            (Consumer<Config.ServiceConfig>)
                s ->
                    s.setCredentials(
                        new Config.CredentialsConfig()
                            .setClientTls(
                                new Config.ClientTlsConfig()
                                    .setCert("/c.pem")
                                    .setPrivateKey("/k.pem")
                                    .setKeystore(
                                        new Config.KeystoreConfig().setPath("/etc/ks.p12")))),
            "both keystore and cert/private_key"),
        Arguments.of(
            "negative cert_reread_interval_seconds",
            (Consumer<Config.ServiceConfig>)
                s ->
                    s.setCredentials(
                        new Config.CredentialsConfig()
                            .setClientTls(
                                new Config.ClientTlsConfig()
                                    .setCert("/c.pem")
                                    .setPrivateKey("/k.pem")
                                    .setCertRereadIntervalSeconds(-1))),
            "cert_reread_interval_seconds must be >= 0"),
        Arguments.of(
            "cert_reread_interval_seconds without cert/key",
            (Consumer<Config.ServiceConfig>)
                s ->
                    s.setCredentials(
                        new Config.CredentialsConfig()
                            .setClientTls(
                                new Config.ClientTlsConfig().setCertRereadIntervalSeconds(60))),
            "cert_reread_interval_seconds requires both cert"));
  }

  @Test
  void validate_tls_bearerAndClientTls_isAllowed() {
    // Bearer tokens and mTLS are not mutually exclusive — many APIs combine a session bearer
    // token with mTLS for client identity. Validate accepts the combination.
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
    assertTrue(errors.isEmpty(), "expected no errors, got: " + errors);
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
