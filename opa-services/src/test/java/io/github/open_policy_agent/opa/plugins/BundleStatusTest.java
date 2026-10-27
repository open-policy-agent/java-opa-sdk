package io.github.open_policy_agent.opa.plugins;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.open_policy_agent.opa.config.Config;
import io.github.open_policy_agent.opa.plugins.DiscoveryTestSupport.RecordingLogger;
import io.github.open_policy_agent.opa.storage.InMem;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;

class BundleStatusTest {

  @TempDir Path tempDir;

  @Test
  void aFailingActivationListenerLeavesTheBundleNotActivated() throws Exception {
    DiscoveryTestSupport.write(tempDir, "a.tar.gz", DiscoveryTestSupport.bundle("a", "rev-1"));
    final Config config =
        new Config()
            .setServices(
                Map.of(
                    "svc",
                    new Config.ServiceConfig().setName("svc").setUrl(tempDir.toUri().toString())))
            .setBundles(
                Map.of("a", new Config.BundleConfig().setService("svc").setResource("a.tar.gz")));
    final PluginManager manager =
        new PluginManager.Builder()
            .withId("test-opa")
            .withStore(new InMem())
            .withConfig(config)
            .withLogger(new RecordingLogger())
            .build();
    manager.registerBundleActivationListener(
        name -> {
          throw new IllegalStateException("listener failed");
        });

    final BundlePlugin plugin = (BundlePlugin) new BundlePlugin().initialize(manager);
    plugin.start();
    try {
      JsonNode status = manager.bundleStatuses().get("a").toJson();
      for (int i = 0; i < 50 && !status.has("code"); i++) {
        Thread.sleep(100);
        status = manager.bundleStatuses().get("a").toJson();
      }

      assertTrue(status.has("code"), status.toString());
      assertEquals("0001-01-01T00:00:00Z", status.path("last_successful_activation").asString());
    } finally {
      plugin.stop();
    }
  }
}
