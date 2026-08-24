package io.github.open_policy_agent.opa.plugins;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.ServiceLoader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import io.github.open_policy_agent.opa.ast.types.RegoObject;
import io.github.open_policy_agent.opa.bundle.Bundle;
import io.github.open_policy_agent.opa.config.Config;
import io.github.open_policy_agent.opa.ir.PolicyReader;
import io.github.open_policy_agent.opa.logging.Logger;
import io.github.open_policy_agent.opa.storage.InMem;
import io.github.open_policy_agent.opa.storage.Store;

/**
 * Tests for applying the configured {@code mask_decision} policy to decision log events. The mask
 * policies come from the compiled plans in {@code src/test/resources/mask}.
 */
class DecisionLogPluginMaskTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final PolicyReader POLICY_READER =
      ServiceLoader.load(PolicyReader.class).findFirst().orElseThrow();

  private Logger mockLogger;
  private Store store;
  private Config config;
  private PluginManager manager;

  @BeforeEach
  void setUp() {
    mockLogger = mock(Logger.class);
    store = new InMem();
    config = new Config();
    config.setServices(
        Collections.singletonMap(
            "test-service",
            new Config.ServiceConfig().setName("test-service").setUrl("https://example.com")));
  }

  private void loadMaskPolicies() throws IOException {
    loadPlan("/mask/plan.json");
  }

  private void loadPlan(String resource) throws IOException {
    try (InputStream plan = getClass().getResourceAsStream(resource)) {
      assertNotNull(plan, "missing mask plan fixture " + resource);
      Bundle bundle = new Bundle.Builder().withIrPolicy(POLICY_READER.read(plan)).build();
      store.write("mask-bundle", bundle, new RegoObject());
    }
  }

  private DecisionLogPlugin startPlugin(String maskDecision) {
    config.setDecisionLogs(
        new Config.DecisionLogsConfig()
            .setConsole(true)
            .setService("test-service")
            .setMaskDecision(maskDecision));

    manager =
        new PluginManager.Builder()
            .withId("test-opa")
            .withStore(store)
            .withConfig(config)
            .withLogger(mockLogger)
            .build();

    DecisionLogPlugin plugin = (DecisionLogPlugin) new DecisionLogPlugin().initialize(manager);
    plugin.start();
    manager.registerPlugin("decision_logs", plugin);
    return plugin;
  }

  private static JsonNode input() {
    ObjectNode input = MAPPER.createObjectNode();
    input.put("user", "alice");
    input.put("password", "secret");
    return input;
  }

  private static JsonNode result() {
    ObjectNode result = MAPPER.createObjectNode();
    result.put("allow", true);
    result.put("token", "eyJhbGciOi");
    return result;
  }

  private void logDecision(DecisionLogPlugin plugin, JsonNode input, JsonNode result) {
    plugin
        .getDecisionLogs()
        .logDecision("decision-1", input, result, "authz/allow", null, 0, null, null);
  }

  /** The console event, which is the same object that gets buffered. */
  private JsonNode loggedEvent() throws IOException {
    ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
    verify(mockLogger).info(eq("Decision: %s"), captor.capture());
    return MAPPER.readTree(captor.getValue());
  }

  @Test
  void logDecision_maskPolicyApplied_removesAndUpsertsFields() throws IOException {
    loadMaskPolicies();
    DecisionLogPlugin plugin = startPlugin("system/log/mask");

    logDecision(plugin, input(), result());

    JsonNode event = loggedEvent();
    assertFalse(event.get("input").has("password"), "password should have been erased");
    assertEquals("alice", event.get("input").get("user").asText());
    assertEquals("**REDACTED**", event.get("result").get("token").asText());
    assertTrue(event.get("result").get("allow").asBoolean());
    assertEquals("[\"/input/password\"]", event.get("erased").toString());
    assertEquals("[\"/result/token\"]", event.get("masked").toString());
    verify(mockLogger, never()).error(anyString(), any());
  }

  @Test
  void logDecision_maskPolicyLeadingSlashConfig_isAccepted() throws IOException {
    loadMaskPolicies();
    DecisionLogPlugin plugin = startPlugin("/system/log/mask");

    logDecision(plugin, input(), result());

    assertFalse(loggedEvent().get("input").has("password"));
  }

  @Test
  void logDecision_maskPolicyDoesNotMatch_leavesEventUnchanged() throws IOException {
    loadMaskPolicies();
    DecisionLogPlugin plugin = startPlugin("system/log/mask");

    ObjectNode input = MAPPER.createObjectNode().put("user", "alice");
    ObjectNode result = MAPPER.createObjectNode().put("allow", true);
    logDecision(plugin, input, result);

    JsonNode event = loggedEvent();
    assertEquals("alice", event.get("input").get("user").asText());
    assertFalse(event.has("erased"));
    assertFalse(event.has("masked"));
    verify(mockLogger, never()).error(anyString(), any());
  }

  @Test
  void logDecision_noMaskPolicyInBundle_logsEventUnmaskedAndWarns() throws IOException {
    // Bundles are loaded, but none of them was built with the mask entrypoint.
    loadMaskPolicies();
    DecisionLogPlugin plugin = startPlugin("system/log/other_mask");

    logDecision(plugin, input(), result());

    assertEquals("secret", loggedEvent().get("input").get("password").asText());
    verify(mockLogger)
        .warn(
            contains("masking is inactive"),
            eq("system/log/other_mask"),
            eq("system/log/other_mask"));
    verify(mockLogger, never()).error(anyString(), any());
  }

  @Test
  void logDecision_noBundlesLoaded_logsEventUnmasked() throws IOException {
    DecisionLogPlugin plugin = startPlugin("system/log/mask");

    logDecision(plugin, input(), result());

    assertEquals("secret", loggedEvent().get("input").get("password").asText());
    // The default applies to every deployment, so its absence is not worth a warning.
    verify(mockLogger, never()).warn(anyString(), any(), any());
    verify(mockLogger, never()).error(anyString(), any());
  }

  @Test
  void logDecision_uploadedBatch_containsTheMaskedEvent() throws Exception {
    loadMaskPolicies();
    DecisionLogPlugin plugin = startPlugin("system/log/mask");

    ServicePlugin services = mock(ServicePlugin.class);
    ServicePlugin.Service service = mock(ServicePlugin.Service.class);
    when(services.getService("test-service")).thenReturn(service);
    manager.registerPlugin("services", services);

    logDecision(plugin, input(), result());
    plugin.flush();

    ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
    verify(service).post(eq("/logs"), body.capture());
    JsonNode batch = MAPPER.readTree(body.getValue());
    assertEquals(1, batch.size());
    assertFalse(batch.get(0).get("input").has("password"), "password should have been erased");
    assertEquals("**REDACTED**", batch.get(0).get("result").get("token").asText());
  }

  @Test
  void logDecision_missingMaskPolicy_warnsOncePerPreparation() throws IOException {
    loadMaskPolicies();
    DecisionLogPlugin plugin = startPlugin("system/log/other_mask");

    logDecision(plugin, input(), result());
    logDecision(plugin, input(), result());

    verify(mockLogger, times(1)).warn(contains("masking is inactive"), any(), any());
  }

  @Test
  void logDecision_bundleActivationAfterFailure_reenablesMasking() throws IOException {
    loadPlan("/mask/plan-error.json");
    DecisionLogPlugin plugin = startPlugin("test/log/mask_error");

    logDecision(plugin, input(), result());
    verify(mockLogger).error(eq("Log event masking failed: %s"), contains("regex.match"));

    loadPlan("/mask/plan-fixed.json");
    manager.notifyBundleActivation("mask-bundle");
    reset(mockLogger);
    logDecision(plugin, input(), result());

    assertFalse(loggedEvent().get("input").has("password"));
    verify(mockLogger, never()).error(anyString(), any());
  }

  @Test
  void logDecision_maskDecisionWithTrailingSlash_isAccepted() throws IOException {
    loadMaskPolicies();
    DecisionLogPlugin plugin = startPlugin("system/log/mask/");

    logDecision(plugin, input(), result());

    assertFalse(loggedEvent().get("input").has("password"));
  }

  @Test
  void logDecision_maskDecisionEmpty_logsEventUnmasked() throws IOException {
    loadMaskPolicies();
    DecisionLogPlugin plugin = startPlugin("");

    logDecision(plugin, input(), result());

    assertEquals("secret", loggedEvent().get("input").get("password").asText());
    verify(mockLogger, never()).warn(anyString(), any(), any());
  }

  @Test
  void logDecision_maskDecisionUnset_logsEventUnmasked() throws IOException {
    loadMaskPolicies();
    DecisionLogPlugin plugin = startPlugin(null);

    logDecision(plugin, input(), result());

    assertEquals("secret", loggedEvent().get("input").get("password").asText());
    verify(mockLogger, never()).error(anyString(), any());
  }

  @Test
  void logDecision_invalidMaskRule_dropsEvent() throws IOException {
    // test/log/mask_invalid returns "/labels/environment", which is not a maskable path.
    loadMaskPolicies();
    DecisionLogPlugin plugin = startPlugin("test/log/mask_invalid");

    logDecision(plugin, input(), result());

    verify(mockLogger).error(eq("Log event masking failed: %s"), contains("mask prefix not allowed"));
    verify(mockLogger, never()).info(eq("Decision: %s"), anyString());
    assertNothingBuffered(plugin);
  }

  @Test
  void logDecision_maskEvaluationFails_dropsEvent() throws IOException {
    // test/log/mask_error calls regex.match, which is not on this module's classpath.
    loadPlan("/mask/plan-error.json");
    DecisionLogPlugin plugin = startPlugin("test/log/mask_error");

    // Twice: the cached preparation failure has to keep dropping events.
    logDecision(plugin, input(), result());
    logDecision(plugin, input(), result());

    verify(mockLogger, times(2)).error(eq("Log event masking failed: %s"), contains("regex.match"));
    verify(mockLogger, never()).info(eq("Decision: %s"), anyString());
    assertNothingBuffered(plugin);
  }

  @Test
  void logDecision_conflictingRulesFromASet_lastRuleWins() throws IOException {
    // test/log/mask_conflict returns both "/input/password" and an upsert of the same path, with
    // the remove coming first, so the upsert is the rule that survives.
    loadMaskPolicies();
    DecisionLogPlugin plugin = startPlugin("test/log/mask_conflict");

    logDecision(plugin, input(), result());

    JsonNode event = loggedEvent();
    assertEquals("**REDACTED**", event.get("input").get("password").asText());
    assertEquals("alice", event.get("input").get("user").asText());
    assertEquals("[\"/input/password\"]", event.get("erased").toString());
    assertEquals("[\"/input/password\"]", event.get("masked").toString());
    verify(mockLogger, never()).error(anyString(), any());
  }

  @Test
  void logDecision_conflictingRulesFromAnArray_appliesThemInPolicyOrder() throws IOException {
    // test/log/mask_order returns an array, which pins the order: the upsert of "/result/token"
    // comes first and the remove that follows drops the field again.
    loadMaskPolicies();
    DecisionLogPlugin plugin = startPlugin("test/log/mask_order");

    logDecision(plugin, input(), result());

    JsonNode event = loggedEvent();
    assertFalse(event.get("result").has("token"), "the trailing remove should win");
    assertTrue(event.get("result").get("allow").asBoolean());
    assertEquals("[\"/result/token\"]", event.get("masked").toString());
    assertEquals("[\"/result/token\"]", event.get("erased").toString());
    verify(mockLogger, never()).error(anyString(), any());
  }

  @Test
  void logDecision_doesNotMutateCallerNodes() throws IOException {
    loadMaskPolicies();
    DecisionLogPlugin plugin = startPlugin("system/log/mask");

    JsonNode input = input();
    JsonNode result = result();
    logDecision(plugin, input, result);

    assertEquals("secret", input.get("password").asText());
    assertEquals("eyJhbGciOi", result.get("token").asText());
  }

  @Test
  void logDecision_bundleActivation_rebuildsMaskQuery() throws IOException {
    DecisionLogPlugin plugin = startPlugin("system/log/mask");

    logDecision(plugin, input(), result());
    assertEquals("secret", loggedEvent().get("input").get("password").asText());

    loadMaskPolicies();
    manager.notifyBundleActivation("mask-bundle");

    reset(mockLogger);
    logDecision(plugin, input(), result());
    assertFalse(loggedEvent().get("input").has("password"), "mask policy should apply after reload");
  }

  /** A dropped event never reaches the buffer, so flushing has nothing to report. */
  private void assertNothingBuffered(DecisionLogPlugin plugin) {
    plugin.flush();
    verify(mockLogger, never()).debug(eq("Flushed %d decision log events"), anyInt());
  }
}
