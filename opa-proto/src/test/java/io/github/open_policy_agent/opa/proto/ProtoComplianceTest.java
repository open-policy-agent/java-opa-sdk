package io.github.open_policy_agent.opa.proto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import io.github.open_policy_agent.opa.ast.builtin.BuiltinRegistry;
import io.github.open_policy_agent.opa.ast.types.RegoBigInt;
import io.github.open_policy_agent.opa.ast.types.RegoDecimal;
import io.github.open_policy_agent.opa.ast.types.RegoInt32;
import io.github.open_policy_agent.opa.ast.types.RegoObject;
import io.github.open_policy_agent.opa.ast.types.RegoString;
import io.github.open_policy_agent.opa.ast.types.RegoValue;
import io.github.open_policy_agent.opa.ir.Evaluator;
import io.github.open_policy_agent.opa.ir.policy.Policy;
import io.github.open_policy_agent.opa.jackson.JacksonPolicyReader;
import io.github.open_policy_agent.opa.jackson.RegoValueModule;
import io.github.open_policy_agent.opa.rego.EvaluationContext;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Proto coverage over the full OPA compliance corpus.
 *
 * <p>{@link ProtoBundleParityTest} proves decoder faithfulness for one representative bundle. This
 * test instead runs across every case in the shared compliance fixtures (the same ones the JSON
 * {@code ComplianceTest} in {@code opa-evaluator} evaluates), which the generator emits with both a
 * JSON {@code plan} and a base64 {@code plan_proto} — the latter produced by OPA's own {@code
 * ir.PolicyToProto} (see {@code tools/generate-compliance-tests}).
 *
 * <p>For each case it decodes <em>both</em> plans and evaluates them against identical input/data,
 * asserting the outcomes (result set, or error) are equal. Evaluation parity — rather than a
 * structural comparison of the two decoded {@link Policy} objects — is the contract that matters:
 * the JSON reader and the proto decoder legitimately differ in how they represent absent repeated
 * fields (OPA's JSON omits empty ones, yielding {@code null}, while proto always materializes an
 * empty list), a cosmetic difference that does not affect evaluation. Since {@code ComplianceTest}
 * already proves the JSON plans evaluate correctly, matching that evaluation transitively gives the
 * proto decoder the same breadth of coverage.
 */
class ProtoComplianceTest {

  private static final ObjectMapper MAPPER =
      new ObjectMapper().registerModule(new RegoValueModule());

  /** Locate the compliance fixtures, which live in the sibling {@code opa-evaluator} module. */
  private static Path complianceDir() {
    // Gradle runs tests with the working directory set to the module dir (opa-proto), so the
    // fixtures resolve one level up; also accept the repo root for IDE run configurations.
    List<Path> candidates =
        List.of(
            Paths.get(
                "..",
                "opa-evaluator",
                "src/test/resources/compliance/Tests/RegoComplianceTests/TestData"),
            Paths.get(
                "opa-evaluator",
                "src/test/resources/compliance/Tests/RegoComplianceTests/TestData"));
    return candidates.stream()
        .filter(Files::isDirectory)
        .findFirst()
        .orElseThrow(
            () ->
                new IllegalStateException("Compliance fixtures not found; looked in " + candidates));
  }

  static Stream<Arguments> complianceCases() throws IOException {
    try (Stream<Path> files = Files.walk(complianceDir())) {
      return files
          .filter(Files::isRegularFile)
          .filter(p -> p.toString().endsWith(".json"))
          .filter(p -> !p.getFileName().toString().equals("index.json"))
          .flatMap(ProtoComplianceTest::casesIn)
          // Collect eagerly so the Files.walk stream can be closed before the test runs.
          .collect(Collectors.toList())
          .stream();
    }
  }

  private static Stream<Arguments> casesIn(Path file) {
    try {
      JsonNode root = MAPPER.readTree(file.toFile());
      List<Arguments> args = new ArrayList<>();
      for (JsonNode c : root.get("cases")) {
        if (c.get("plan") == null || c.get("plan_proto") == null) {
          throw new IllegalStateException(
              "case missing plan/plan_proto in " + file + ": " + c.get("note"));
        }
        args.add(Arguments.of(c.get("note").asText("unknown"), c));
      }
      return args.stream();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("complianceCases")
  void protoPlanEvaluatesIdenticallyToJsonPlan(String note, JsonNode c) throws IOException {
    Policy jsonPolicy =
        new JacksonPolicyReader()
            .read(new ByteArrayInputStream(MAPPER.writeValueAsBytes(c.get("plan"))));
    Policy protoPolicy;
    try (ByteArrayInputStream in =
        new ByteArrayInputStream(Base64.getDecoder().decode(c.get("plan_proto").asText()))) {
      protoPolicy = new ProtoBundleReader().decodePlan(in);
    }

    // Feed both decodings the same input/data; the outcome must match regardless of wire format.
    // Both may legitimately raise the same error (e.g. an unimplemented builtin) — that is parity
    // too, so compare a serialized outcome that captures either a result set or the failure.
    // Input/data are rebuilt fresh per evaluation: some builtins (e.g. array.reverse) mutate their
    // operands in place, so sharing them between the two runs would let the first corrupt the second.
    String jsonOutcome = outcome(jsonPolicy, c);
    String protoOutcome = outcome(protoPolicy, c);
    assertThat(protoOutcome).as("proto vs JSON evaluation for %s", note).isEqualTo(jsonOutcome);
  }

  private static String outcome(Policy policy, JsonNode c) {
    try {
      String entrypoint = c.get("entrypoints").get(0).asText();
      RegoValue input = toRego(c.hasNonNull("input") ? c.get("input") : NullNode.getInstance());
      RegoObject data =
          MAPPER.treeToValue(
              c.hasNonNull("data") ? c.get("data") : MAPPER.createObjectNode(), RegoObject.class);
      BuiltinRegistry builtins = BuiltinRegistry.allCapabilities();
      EvaluationContext ctx =
          new EvaluationContext.Builder()
              .withBuiltinRegistry(builtins)
              .withSortedSets() // deterministic set ordering, so the serialized results compare
              .withEntrypoint(entrypoint)
              .build();
      Evaluator evaluator =
          new Evaluator.Builder().withPolicy(policy).withBuiltinRegistry(builtins).build();
      RegoValue[] result = evaluator.evaluate(ctx, input, data);
      return "OK:" + MAPPER.writeValueAsString(result);
    } catch (Exception e) {
      // Compare error class + message rather than the stack, so the two decodings must fail the
      // same way to be considered equal.
      return "ERR:" + e.getClass().getName() + ":" + e.getMessage();
    }
  }

  private static RegoValue toRego(JsonNode node) throws IOException {
    if (node == null || node.isNull()) {
      return MAPPER.treeToValue(NullNode.getInstance(), RegoObject.class);
    }
    if (node.isTextual()) {
      return MAPPER.treeToValue(node, RegoString.class);
    }
    if (node.isIntegralNumber()) {
      long v = node.asLong();
      return v >= Integer.MIN_VALUE && v <= Integer.MAX_VALUE
          ? RegoInt32.of((int) v)
          : new RegoBigInt(v);
    }
    if (node.isNumber()) {
      return new RegoDecimal(node.asDouble());
    }
    // Objects, arrays and booleans go through the RegoValueModule deserializer.
    return MAPPER.treeToValue(node, RegoObject.class);
  }
}
