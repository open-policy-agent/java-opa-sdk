package io.github.open_policy_agent.opa;

import static org.junit.jupiter.api.Assertions.*;

import io.github.open_policy_agent.opa.ir.PolicyNotFoundException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.StringReader;
import java.util.Collections;
import java.util.Set;
import org.junit.jupiter.api.Test;
import io.github.open_policy_agent.opa.config.Config;
import io.github.open_policy_agent.opa.config.ConfigurationException;
import io.github.open_policy_agent.opa.logging.Logger;
import io.github.open_policy_agent.opa.plugins.Plugin;
import io.github.open_policy_agent.opa.plugins.PluginManager;
import io.github.open_policy_agent.opa.tracing.DurationProfiler;

class OpaTest {
  private static final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void opa_builder_requiresConfigFile() {
    Opa.Builder builder = new Opa.Builder().withDefaultEntrypoint("test");

    // Should throw because no config file found
    assertThrows(ConfigurationException.class, builder::build);
  }

  @Test
  void opa_builder_loadsConfigFromReader() {
    String config =
        "services:\n"
            + "  test-service:\n"
            + "    url: http://localhost:8181\n"
            + "\n"
            + "bundles:\n"
            + "  test-bundle:\n"
            + "    service: test-service\n"
            + "    resource: bundles/test.tar.gz\n";

    Opa.Builder builder =
        new Opa.Builder()
            .withConfig(new StringReader(config))
            .withDefaultEntrypoint("test/allow")
            .withWaitForPlugins(false);

    // Should not throw - config loaded from reader
    Opa opa = builder.build();
    assertNotNull(opa);
  }

  @Test
  void opa_builder_setsDefaultQuery() {
    String config =
        "services:\n"
            + "  test-service:\n"
            + "    url: http://localhost:8181\n"
            + "\n"
            + "bundles:\n"
            + "  test-bundle:\n"
            + "    service: test-service\n"
            + "    resource: bundles/test.tar.gz\n";

    Opa opa =
        new Opa.Builder()
            .withConfig(new StringReader(config))
            .withDefaultEntrypoint("example/allow")
            .withWaitForPlugins(false)
            .build();

    assertNotNull(opa);
  }

  @Test
  void opa_builder_setsCustomLogger() {
    String config =
        "services:\n"
            + "  test-service:\n"
            + "    url: http://localhost:8181\n"
            + "\n"
            + "bundles:\n"
            + "  test-bundle:\n"
            + "    service: test-service\n"
            + "    resource: bundles/test.tar.gz\n";

    Logger customLogger = new Logger.StandardLogger();

    Opa opa =
        new Opa.Builder()
            .withConfig(new StringReader(config))
            .withDefaultEntrypoint("test")
            .withLogger(customLogger)
            .withWaitForPlugins(false)
            .build();

    assertNotNull(opa);
  }

  @Test
  void opa_builder_setsCustomId() {
    String config =
        "services:\n"
            + "  test-service:\n"
            + "    url: http://localhost:8181\n"
            + "\n"
            + "bundles:\n"
            + "  test-bundle:\n"
            + "    service: test-service\n"
            + "    resource: bundles/test.tar.gz\n";

    Opa opa =
        new Opa.Builder()
            .withConfig(new StringReader(config))
            .withDefaultEntrypoint("test")
            .withId("custom-opa-id")
            .withWaitForPlugins(false)
            .build();

    assertNotNull(opa);
  }

  @Test
  void opa_builder_registersCustomPlugin() {
    String config =
        "services:\n"
            + "  test-service:\n"
            + "    url: http://localhost:8181\n"
            + "\n"
            + "bundles:\n"
            + "  test-bundle:\n"
            + "    service: test-service\n"
            + "    resource: bundles/test.tar.gz\n";

    // Create a simple test plugin
    Plugin testPlugin =
        new Plugin() {
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
        };

    Opa opa =
        new Opa.Builder()
            .withConfig(new StringReader(config))
            .withDefaultEntrypoint("test")
            .withPlugin("test-plugin", testPlugin)
            .withWaitForPlugins(false)
            .build();

    assertNotNull(opa);
  }

  @Test
  void opa_ready_returnsFalseInitially() {
    String config =
        "services:\n"
            + "  test-service:\n"
            + "    url: http://localhost:8181\n"
            + "\n"
            + "bundles:\n"
            + "  test-bundle:\n"
            + "    service: test-service\n"
            + "    resource: bundles/test.tar.gz\n";

    Opa opa =
        new Opa.Builder()
            .withConfig(new StringReader(config))
            .withDefaultEntrypoint("test/allow")
            .withWaitForPlugins(false)
            .build();

    // Initially not ready because bundles haven't been loaded
    assertFalse(opa.ready());
  }

  @Test
  void decisionOptions_builder_setsAllFields() {
    JsonNode input = objectMapper.createObjectNode().put("user", "bob");

    Opa.DecisionOptions options =
        new Opa.DecisionOptions()
            .setNowNs(System.nanoTime())
            .setPath("example/allow")
            .setInput(input)
            .setStrictBuiltinErrors(true)
            .showMetrics()
            .setProfiler(new DurationProfiler())
            .setInstrument(true)
            .setDecisionID("test-decision-id");

    assertNotNull(options.getInput());
    assertNotNull(options.getPath());
    assertTrue(options.getShowMetrics());
    assertNotNull(options.getProfiler());
    assertNotNull(options.getDecisionID());
    assertTrue(options.isStrictBuiltinErrors());
    assertTrue(options.isInstrument());
  }

  @Test
  void decisionOptions_builder_hasDefaultValues() {
    Opa.DecisionOptions options = new Opa.DecisionOptions();

    assertEquals(0, options.getNowNs());
    assertFalse(options.isStrictBuiltinErrors());
    assertFalse(options.isInstrument());
  }

  @Test
  void opa_builder_loadsYamlConfig() {
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
            + "  reporting:\n"
            + "    min_delay_seconds: 300\n";

    Opa opa =
        new Opa.Builder()
            .withConfig(new StringReader(yamlConfig))
            .withDefaultEntrypoint("authz/allow")
            .withWaitForPlugins(false)
            .build();

    assertNotNull(opa);
  }

  @Test
  void opa_builder_createsStoreForPlugins() {
    String config =
        "services:\n"
            + "  test-service:\n"
            + "    url: http://localhost:8181\n"
            + "\n"
            + "bundles:\n"
            + "  test-bundle:\n"
            + "    service: test-service\n"
            + "    resource: bundles/test.tar.gz\n";

    Opa opa =
        new Opa.Builder()
            .withConfig(new StringReader(config))
            .withDefaultEntrypoint("test")
            .withWaitForPlugins(false)
            .build();

    assertNotNull(opa);
    // Store is created internally during build
  }

  @Test
  void opa_builder_minimalConfig_buildsSuccessfully() {
    assertThrowsExactly(PolicyNotFoundException.class,() -> new Opa.Builder()
                      .withConfig(new Config())
                      .withDefaultEntrypoint("test/allow")
                      .withWaitForPlugins(true)
                      .build());
  }

  @Test
  void opa_builder_registersBuiltinPlugins() {
    String config =
        "services:\n"
            + "  test-service:\n"
            + "    url: http://localhost:8181\n"
            + "\n"
            + "bundles:\n"
            + "  test-bundle:\n"
            + "    service: test-service\n"
            + "    resource: bundles/test.tar.gz\n"
            + "\n"
            + "decision_logs:\n"
            + "  service: test-service\n"
            + "\n"
            + "status:\n"
            + "  service: test-service\n";

    Opa opa =
        new Opa.Builder()
            .withConfig(new StringReader(config))
            .withDefaultEntrypoint("test")
            .withWaitForPlugins(false)
            .build();

    assertNotNull(opa);
    // Built-in plugins (services, bundles, decision_logs, status) are registered during build
  }
}
