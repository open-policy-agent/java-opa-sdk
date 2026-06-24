package io.github.open_policy_agent.opa.config;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.StringReader;
import org.junit.jupiter.api.Test;

/** Unit tests for Config class to ensure YAML, JSON, and direct object configuration work. */
class ConfigTest {

  @Test
  void config_loadsFromYaml() throws Exception {
    String yamlConfig =
        "services:\n"
            + "  acmecorp:\n"
            + "    url: https://example.com/control-plane-api/v1\n"
            + "    credentials:\n"
            + "      bearer:\n"
            + "        token: \"my-secret-token\"\n"
            + "\n"
            + "bundles:\n"
            + "  authz:\n"
            + "    service: acmecorp\n"
            + "    resource: bundles/http/example/authz.tar.gz\n"
            + "    polling:\n"
            + "      min_delay_seconds: 60\n"
            + "      max_delay_seconds: 120\n"
            + "\n"
            + "decision_logs:\n"
            + "  service: acmecorp\n"
            + "  resource: /logs\n"
            + "  min_delay_seconds: 300\n"
            + "  max_delay_seconds: 600\n"
            + "\n"
            + "status:\n"
            + "  service: acmecorp\n"
            + "  console: true\n";

    ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
    Config config = mapper.readValue(new StringReader(yamlConfig), Config.class);

    assertNotNull(config);
    assertNotNull(config.getServices());
    assertEquals(1, config.getServices().size());

    Config.ServiceConfig service = config.getService("acmecorp");
    assertNotNull(service);
    assertEquals("https://example.com/control-plane-api/v1", service.getUrl());
    assertNotNull(service.getCredentials());
    assertNotNull(service.getCredentials().getBearer());
    assertEquals("my-secret-token", service.getCredentials().getBearer().getToken());

    assertNotNull(config.getBundles());
    assertEquals(1, config.getBundles().size());

    Config.BundleConfig bundle = config.getBundles().get("authz");
    assertNotNull(bundle);
    assertEquals("acmecorp", bundle.getService());
    assertEquals("bundles/http/example/authz.tar.gz", bundle.getResource());
    assertNotNull(bundle.getPolling());
    assertEquals(60, bundle.getPolling().getMinDelaySeconds());
    assertEquals(120, bundle.getPolling().getMaxDelaySeconds());

    assertNotNull(config.getDecisionLogs());
    assertEquals("acmecorp", config.getDecisionLogs().getService());
    assertEquals(300, config.getDecisionLogs().getMinDelaySeconds());
    assertEquals(600, config.getDecisionLogs().getMaxDelaySeconds());

    assertNotNull(config.getStatus());
    assertEquals("acmecorp", config.getStatus().getService());
    assertTrue(config.getStatus().getConsole());
  }

  @Test
  void config_loadsFromJson() throws Exception {
    String jsonConfig =
        "{\n"
            + "  \"services\": {\n"
            + "    \"acmecorp\": {\n"
            + "      \"url\": \"https://example.com/control-plane-api/v1\",\n"
            + "      \"credentials\": {\n"
            + "        \"bearer\": {\n"
            + "          \"token\": \"my-secret-token\"\n"
            + "        }\n"
            + "      }\n"
            + "    }\n"
            + "  },\n"
            + "  \"bundles\": {\n"
            + "    \"authz\": {\n"
            + "      \"service\": \"acmecorp\",\n"
            + "      \"resource\": \"bundles/http/example/authz.tar.gz\",\n"
            + "      \"polling\": {\n"
            + "        \"min_delay_seconds\": 60,\n"
            + "        \"max_delay_seconds\": 120\n"
            + "      }\n"
            + "    }\n"
            + "  },\n"
            + "  \"decision_logs\": {\n"
            + "    \"service\": \"acmecorp\"\n"
            + "  }\n"
            + "}";

    ObjectMapper mapper = new ObjectMapper();
    Config config = mapper.readValue(new StringReader(jsonConfig), Config.class);

    assertNotNull(config);
    assertNotNull(config.getServices());
    assertEquals(1, config.getServices().size());

    Config.ServiceConfig service = config.getService("acmecorp");
    assertNotNull(service);
    assertEquals("https://example.com/control-plane-api/v1", service.getUrl());

    assertNotNull(config.getBundles());
    assertEquals(1, config.getBundles().size());

    Config.BundleConfig bundle = config.getBundles().get("authz");
    assertNotNull(bundle);
    assertEquals("acmecorp", bundle.getService());
  }

  @Test
  void config_discoveryConfig_loadsFromYaml() throws Exception {
    String yamlConfig =
        "services:\n"
            + "  acmecorp:\n"
            + "    url: https://example.com/control-plane-api/v1\n"
            + "\n"
            + "discovery:\n"
            + "  name: discovery\n"
            + "  service: acmecorp\n"
            + "  resource: /bundles/discovery.tar.gz\n"
            + "  polling:\n"
            + "    min_delay_seconds: 60\n"
            + "    max_delay_seconds: 120\n";

    ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
    Config config = mapper.readValue(new StringReader(yamlConfig), Config.class);

    assertNotNull(config);
    assertNotNull(config.getDiscovery());
    assertEquals("discovery", config.getDiscovery().getName());
    assertEquals("acmecorp", config.getDiscovery().getService());
    assertEquals("/bundles/discovery.tar.gz", config.getDiscovery().getResource());
    assertNotNull(config.getDiscovery().getPolling());
    assertEquals(60, config.getDiscovery().getPolling().getMinDelaySeconds());
    assertEquals(120, config.getDiscovery().getPolling().getMaxDelaySeconds());
  }

  @Test
  void config_discoveryConfig_loadsFromJson() throws Exception {
    String jsonConfig =
        "{\n"
            + "  \"services\": {\n"
            + "    \"acmecorp\": {\n"
            + "      \"url\": \"https://example.com/control-plane-api/v1\"\n"
            + "    }\n"
            + "  },\n"
            + "  \"discovery\": {\n"
            + "    \"name\": \"discovery\",\n"
            + "    \"service\": \"acmecorp\",\n"
            + "    \"resource\": \"/bundles/discovery.tar.gz\",\n"
            + "    \"polling\": {\n"
            + "      \"min_delay_seconds\": 60,\n"
            + "      \"max_delay_seconds\": 120\n"
            + "    }\n"
            + "  }\n"
            + "}";

    ObjectMapper mapper = new ObjectMapper();
    Config config = mapper.readValue(new StringReader(jsonConfig), Config.class);

    assertNotNull(config);
    assertNotNull(config.getDiscovery());
    assertEquals("discovery", config.getDiscovery().getName());
    assertEquals("acmecorp", config.getDiscovery().getService());
    assertEquals("/bundles/discovery.tar.gz", config.getDiscovery().getResource());
  }

  @Test
  void config_builderPattern_setsAllFields() {
    Config config = new Config();

    Config.ServiceConfig service =
        new Config.ServiceConfig()
            .setUrl("https://example.com")
            .setName("test-service")
            .setResponseHeaderTimeoutSeconds(30)
            .setAllowInsecureTLS(true);

    Config.CredentialsConfig credentials = new Config.CredentialsConfig();
    Config.BearerConfig bearer = new Config.BearerConfig().setToken("test-token");
    credentials.setBearer(bearer);
    service.setCredentials(credentials);

    config.setServices(java.util.Collections.singletonMap("test-service", service));

    Config.BundleConfig bundleConfig =
        new Config.BundleConfig()
            .setService("test-service")
            .setResource("/bundles/test.tar.gz")
            .setPolling(new Config.PollingConfig().setMinDelaySeconds(30).setMaxDelaySeconds(60));

    config.setBundles(java.util.Collections.singletonMap("test-bundle", bundleConfig));

    Config.DiscoveryConfig discoveryConfig =
        new Config.DiscoveryConfig()
            .setName("discovery")
            .setService("test-service")
            .setResource("/bundles/discovery.tar.gz")
            .setPolling(new Config.PollingConfig().setMinDelaySeconds(60).setMaxDelaySeconds(120));

    config.setDiscovery(discoveryConfig);

    Config.DecisionLogsConfig decisionLogs =
        new Config.DecisionLogsConfig()
            .setService("test-service")
            .setConsole(true)
            .setResource("/logs")
            .setMinDelaySeconds(300)
            .setMaxDelaySeconds(600);

    config.setDecisionLogs(decisionLogs);

    Config.StatusConfig status =
        new Config.StatusConfig().setService("test-service").setConsole(true);

    config.setStatus(status);

    config.setLabels(java.util.Collections.singletonMap("env", "test"));
    config.setDefaultDecision("data.test.allow");

    Config.NdBuiltinCacheConfig ndCacheConfig =
        new Config.NdBuiltinCacheConfig()
            .setMaxNumEntries(5000)
            .setStaleEntryEvictionPeriodSeconds(30);
    config.setNdBuiltinCache(ndCacheConfig);

    // Verify all fields are set correctly
    assertNotNull(config.getServices());
    assertNotNull(config.getService("test-service"));
    assertEquals("https://example.com", config.getService("test-service").getUrl());
    assertEquals(30, config.getService("test-service").getResponseHeaderTimeoutSeconds());
    assertTrue(config.getService("test-service").isAllowInsecureTLS());

    assertNotNull(config.getBundles());
    assertNotNull(config.getBundles().get("test-bundle"));

    assertNotNull(config.getDiscovery());
    assertEquals("discovery", config.getDiscovery().getName());

    assertNotNull(config.getDecisionLogs());
    assertTrue(config.getDecisionLogs().getConsole());

    assertNotNull(config.getStatus());
    assertTrue(config.getStatus().getConsole());

    assertNotNull(config.getLabels());
    assertEquals("test", config.getLabels().get("env"));

    assertEquals("data.test.allow", config.getDefaultDecision());

    assertNotNull(config.getNdBuiltinCache());
    assertEquals(5000, config.getNdBuiltinCache().getMaxNumEntries());
    assertEquals(30, config.getNdBuiltinCache().getStaleEntryEvictionPeriodSeconds());
  }

  @Test
  void config_pollingDefaults() {
    Config.PollingConfig polling = new Config.PollingConfig();

    assertEquals(60, polling.getMinDelaySeconds());
    assertEquals(120, polling.getMaxDelaySeconds());
  }

  @Test
  void config_decisionLogsDefaults() {
    Config.DecisionLogsConfig decisionLogs = new Config.DecisionLogsConfig();

    assertFalse(decisionLogs.getConsole());
    assertEquals("system/log/mask", decisionLogs.getMaskDecision());
    assertEquals("system/log/drop", decisionLogs.getDropDecision());
    assertEquals(300, decisionLogs.getMinDelaySeconds());
    assertEquals(600, decisionLogs.getMaxDelaySeconds());
    assertEquals("/logs", decisionLogs.getResource());
  }

  @Test
  void config_statusDefaults() {
    Config.StatusConfig status = new Config.StatusConfig();

    assertFalse(status.getConsole());
  }

  @Test
  void config_serviceDefaults() {
    Config.ServiceConfig service = new Config.ServiceConfig();

    assertEquals(10, service.getResponseHeaderTimeoutSeconds());
    assertFalse(service.isAllowInsecureTLS());
  }

  @Test
  void config_ndBuiltinCacheDefaults() {
    Config.NdBuiltinCacheConfig ndCache = new Config.NdBuiltinCacheConfig();

    assertEquals(10000, ndCache.getMaxNumEntries());
    assertEquals(60, ndCache.getStaleEntryEvictionPeriodSeconds());
  }

  @Test
  void config_toString_includesDiscovery() {
    Config config = new Config();
    config.setDiscovery(
        new Config.DiscoveryConfig()
            .setName("discovery")
            .setService("test-service")
            .setResource("/bundles/discovery.tar.gz"));

    String configString = config.toString();

    assertNotNull(configString);
    assertTrue(configString.contains("discovery"));
  }

  @Test
  void config_loadsMtlsFromYaml() throws Exception {
    String yaml =
        "services:\n"
            + "  test-service:\n"
            + "    url: https://opa.example.com\n"
            + "    tls:\n"
            + "      ca_cert: /etc/ssl/corp-ca.pem\n"
            + "      system_ca_required: true\n"
            + "    credentials:\n"
            + "      client_tls:\n"
            + "        cert: /etc/ssl/client.pem\n"
            + "        private_key: /etc/ssl/client-key.pem\n"
            + "        private_key_passphrase: \"secret\"\n"
            + "        cert_reread_interval_seconds: 3600\n";

    ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
    Config config = mapper.readValue(new StringReader(yaml), Config.class);

    Config.ServiceConfig svc = config.getService("test-service");
    assertNotNull(svc);

    Config.TlsConfig tls = svc.getTls();
    assertNotNull(tls);
    assertEquals("/etc/ssl/corp-ca.pem", tls.getCaCert());
    assertTrue(tls.isSystemCaRequired());

    Config.ClientTlsConfig clientTls = svc.getCredentials().getClientTls();
    assertNotNull(clientTls);
    assertEquals("/etc/ssl/client.pem", clientTls.getCert());
    assertEquals("/etc/ssl/client-key.pem", clientTls.getPrivateKey());
    assertEquals("secret", clientTls.getPrivateKeyPassphrase());
    assertEquals(3600, clientTls.getCertRereadIntervalSeconds());
  }

  @Test
  void config_mtlsDefaults() {
    Config.TlsConfig tls = new Config.TlsConfig();
    assertFalse(tls.isSystemCaRequired());
    assertNull(tls.getCaCert());

    Config.ClientTlsConfig clientTls = new Config.ClientTlsConfig();
    assertNull(clientTls.getCert());
    assertNull(clientTls.getPrivateKey());
    assertNull(clientTls.getPrivateKeyPassphrase());
    assertNull(clientTls.getCertRereadIntervalSeconds());
  }

  @Test
  void config_clientTlsToString_redactsPassphrase() {
    Config.ClientTlsConfig clientTls =
        new Config.ClientTlsConfig()
            .setCert("/c.pem")
            .setPrivateKey("/k.pem")
            .setPrivateKeyPassphrase("super-secret");
    String s = clientTls.toString();
    assertTrue(s.contains("<redacted>"), s);
    assertFalse(s.contains("super-secret"), "passphrase must never appear in toString(): " + s);
  }
}
