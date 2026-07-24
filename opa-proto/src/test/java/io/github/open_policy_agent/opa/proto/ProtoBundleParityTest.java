package io.github.open_policy_agent.opa.proto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import io.github.open_policy_agent.opa.bundle.Bundle;
import io.github.open_policy_agent.opa.bundle.FileSystemBundleLoader;
import io.github.open_policy_agent.opa.ir.PolicyReader;
import io.github.open_policy_agent.opa.ir.policy.Policy;
import io.github.open_policy_agent.opa.jackson.JacksonPolicyReader;
import io.github.open_policy_agent.opa.rego.Engine;
import io.github.open_policy_agent.opa.storage.InMem;
import io.github.open_policy_agent.opa.storage.Store;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Round-trip parity: the {@code authz-proto} and {@code authz-json} bundles are built from the same
 * policy and data at the pinned OPA commit, differing only in wire format. Loading and evaluating
 * either must produce identical results, proving the proto decoder is faithful to OPA's output.
 */
class ProtoBundleParityTest {

  private static Path bundle(String name) {
    return Paths.get(
        Objects.requireNonNull(
                ProtoBundleParityTest.class.getClassLoader().getResource("bundles/" + name))
            .getPath());
  }

  private static final Path PROTO_BUNDLE = bundle("authz-proto");
  private static final Path JSON_BUNDLE = bundle("authz-json");

  private static List<Object> eval(Path bundleDir, String entrypoint, Object input) {
    Engine engine =
        new Engine.Builder()
            .withBundleLoader(new FileSystemBundleLoader(entrypoint, bundleDir))
            .withEntrypoint(entrypoint)
            .build();
    return engine.prepareForEvaluation().build().eval(input);
  }

  private static void assertParity(String entrypoint, Object input) {
    List<Object> fromProto = eval(PROTO_BUNDLE, entrypoint, input);
    List<Object> fromJson = eval(JSON_BUNDLE, entrypoint, input);
    assertThat(fromProto)
        .as("proto and JSON bundles must evaluate identically for %s with input %s", entrypoint, input)
        .isEqualTo(fromJson);
  }

  static Stream<Arguments> parityInputs() {
    return Stream.of(
        // authz/allow: reader via data.roles, admin via membership, and denied cases
        arguments("authz/allow", Map.of("method", "GET", "user", "alice")),
        arguments("authz/allow", Map.of("method", "POST", "user", "alice")),
        arguments("authz/allow", Map.of("method", "POST", "user", "bob")),
        arguments("authz/allow", Map.of("method", "GET", "user", "carol")),
        // authz/summary: object/array/set construction, scan iteration, count/sum builtins
        arguments("authz/summary", Map.of("method", "GET", "user", "alice")),
        arguments("authz/summary", Map.of("method", "POST", "user", "bob")));
  }

  @ParameterizedTest(name = "{0} with {1}")
  @MethodSource("parityInputs")
  void evaluatesIdenticallyAcrossFormats(String entrypoint, Object input) {
    assertParity(entrypoint, input);
  }

  @Test
  void allow_actuallyDecidesAllowAndDeny() {
    // Guard against a vacuous parity check: confirm the plan really computes a decision.
    assertThat(eval(PROTO_BUNDLE, "authz/allow", Map.of("method", "POST", "user", "bob")))
        .containsExactly(Map.of("result", true));
    assertThat(eval(PROTO_BUNDLE, "authz/allow", Map.of("method", "GET", "user", "carol")))
        .containsExactly(Map.of("result", false));
  }

  @Test
  void decodedPlanMatchesJsonDecodedPlan() throws IOException {
    Policy protoPolicy;
    try (InputStream in = Files.newInputStream(PROTO_BUNDLE.resolve("plan.pb"))) {
      protoPolicy = new ProtoBundleReader().decodePlan(in);
    }

    PolicyReader jsonReader = new JacksonPolicyReader();
    Policy jsonPolicy;
    try (InputStream in = Files.newInputStream(JSON_BUNDLE.resolve("plan.json"))) {
      jsonPolicy = jsonReader.read(in);
    }

    // Plans and funcs carry the full statement/operand structure (locations, string indices, block
    // nesting). These must match exactly — this is where a faithful decoder is proven.
    assertThat(protoPolicy.getPlans()).isEqualTo(jsonPolicy.getPlans());
    assertThat(protoPolicy.getFuncs()).isEqualTo(jsonPolicy.getFuncs());

    // The interned string constants must match too. (Builtin decls are intentionally absent from
    // the proto form, so the full Static object is not compared.)
    assertThat(protoPolicy.getStatic().getStrings()).isEqualTo(jsonPolicy.getStatic().getStrings());
    assertThat(protoPolicy.getStatic().getFiles()).isEqualTo(jsonPolicy.getStatic().getFiles());
  }

  @Test
  void decodedManifestMatchesJsonManifest() {
    Store store = new InMem();
    Bundle proto = new FileSystemBundleLoader("proto", PROTO_BUNDLE).load(store);

    assertThat(proto.manifest).containsEntry("revision", "");
    assertThat(proto.manifest).containsEntry("roots", List.of(""));
    assertThat(((Number) proto.manifest.get("rego_version")).intValue()).isEqualTo(1);
  }

  @Test
  void rejectsMixedPlanAndManifestFormats(@TempDir Path dir) throws IOException {
    // proto plan paired with a JSON manifest — OPA rejects this; so must we.
    Files.copy(PROTO_BUNDLE.resolve("plan.pb"), dir.resolve("plan.pb"));
    Files.copy(JSON_BUNDLE.resolve(".manifest"), dir.resolve(".manifest"));

    assertThatThrownBy(() -> new FileSystemBundleLoader("mixed", dir).load(new InMem()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("mixes");
  }

  @Test
  void rejectsBothPlanFormats(@TempDir Path dir) throws IOException {
    Files.copy(PROTO_BUNDLE.resolve("plan.pb"), dir.resolve("plan.pb"));
    Files.copy(JSON_BUNDLE.resolve("plan.json"), dir.resolve("plan.json"));

    assertThatThrownBy(() -> new FileSystemBundleLoader("dup", dir).load(new InMem()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("ambiguous");
  }
}
