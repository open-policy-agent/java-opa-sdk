package io.github.open_policy_agent.opa;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.GZIPOutputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import io.github.open_policy_agent.opa.config.Config;
import io.github.open_policy_agent.opa.logging.Logger;

/**
 * End-to-end decision logging through a real {@link Opa} instance, with bundles loaded from disk.
 * Covers what {@code DecisionLogPluginMaskTest} cannot: that the event handed to the mask policy,
 * and the result handed to the caller, hold the decision rather than the plan's
 * {@code {"result": <value>}} envelope.
 */
class OpaDecisionLogMaskTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @TempDir private Path bundleDir;

  @Test
  void makeDecision_masksEventWithoutTouchingTheCallersResult() throws Exception {
    writeBundle("authz.tar.gz", "/mask/plan-authz.json", "[\"authz\"]");
    writeBundle("mask.tar.gz", "/mask/plan.json", "[\"system\", \"test\"]");

    // A spy over the real logger so a bundle or plugin failure still shows up in the test output.
    Logger logger = spy(new Logger.StandardLogger());
    Opa opa =
        new Opa.Builder()
            .withConfig(config())
            .withDefaultEntrypoint("authz/decision")
            .withLogger(logger)
            .build();

    try {
      ObjectNode input = MAPPER.createObjectNode();
      input.put("user", "alice");
      input.put("password", "secret");
      input.put("token", "eyJhbGciOi");

      Opa.DecisionResult decision =
          opa.makeDecision(new Opa.DecisionOptions().setInput(input).setDecisionID("decision-1"));

      assertEquals("eyJhbGciOi", decision.getResult().get("token").asText());
      assertTrue(decision.getResult().get("allow").asBoolean());
      assertEquals("secret", input.get("password").asText(), "caller's input should be untouched");

      JsonNode event = loggedEvent(logger);
      assertEquals("decision-1", event.get("decision_id").asText());
      assertEquals("alice", event.get("input").get("user").asText());
      assertFalse(event.get("input").has("password"), "password should have been erased");
      // Fails if the event carries the plan envelope: /result/token would not resolve.
      assertEquals("**REDACTED**", event.get("result").get("token").asText());
      assertTrue(event.get("result").get("allow").asBoolean());
      assertEquals("[\"/input/password\"]", event.get("erased").toString());
      assertEquals("[\"/result/token\"]", event.get("masked").toString());
    } finally {
      opa.close();
    }
  }

  @Test
  void makeDecision_scalarDecision_returnsTheValueNotThePlanEnvelope() throws Exception {
    // The usage both READMEs document: getResult().asBoolean() on a boolean rule.
    writeBundle("authz.tar.gz", "/mask/plan-authz.json", "[\"authz\"]");
    writeBundle("mask.tar.gz", "/mask/plan.json", "[\"system\", \"test\"]");

    Logger logger = spy(new Logger.StandardLogger());
    Opa opa =
        new Opa.Builder()
            .withConfig(config())
            .withDefaultEntrypoint("authz/allow")
            .withLogger(logger)
            .build();

    try {
      Opa.DecisionResult decision =
          opa.makeDecision(MAPPER.createObjectNode().put("user", "alice"));

      assertTrue(decision.getResult().asBoolean());
      assertTrue(decision.getResultAs(Boolean.class));
      assertTrue(loggedEvent(logger).get("result").asBoolean());
    } finally {
      opa.close();
    }
  }

  private Config config() {
    Config config = new Config();
    config.setServices(
        Map.of(
            "local",
            new Config.ServiceConfig().setName("local").setUrl(bundleDir.toUri().toString())));

    Map<String, Config.BundleConfig> bundles = new LinkedHashMap<>();
    bundles.put(
        "authz", new Config.BundleConfig().setService("local").setResource("authz.tar.gz"));
    bundles.put("mask", new Config.BundleConfig().setService("local").setResource("mask.tar.gz"));
    config.setBundles(bundles);

    config.setDecisionLogs(new Config.DecisionLogsConfig().setConsole(true));
    return config;
  }

  /** The console event, which is the same object that gets buffered. */
  private static JsonNode loggedEvent(Logger logger) throws IOException {
    ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
    verify(logger).info(eq("Decision: %s"), captor.capture());
    return MAPPER.readTree(captor.getValue());
  }

  /** Bundle tarball for BundlePlugin to load over file://; roots keep the two from conflicting. */
  private void writeBundle(String name, String planResource, String roots) throws IOException {
    byte[] plan;
    try (InputStream in = getClass().getResourceAsStream(planResource)) {
      assertNotNull(in, "missing plan fixture " + planResource);
      plan = in.readAllBytes();
    }

    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (GZIPOutputStream gzip = new GZIPOutputStream(bytes);
        TarArchiveOutputStream tar = new TarArchiveOutputStream(gzip)) {
      addEntry(tar, "plan.json", plan);
      addEntry(
          tar,
          ".manifest",
          ("{\"revision\": \"" + name + "-1\", \"roots\": " + roots + "}")
              .getBytes(java.nio.charset.StandardCharsets.UTF_8));
      tar.finish();
    }

    Files.write(bundleDir.resolve(name), bytes.toByteArray());
  }

  private static void addEntry(TarArchiveOutputStream tar, String name, byte[] content)
      throws IOException {
    TarArchiveEntry entry = new TarArchiveEntry(name);
    entry.setSize(content.length);
    tar.putArchiveEntry(entry);
    tar.write(content);
    tar.closeArchiveEntry();
  }
}
