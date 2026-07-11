package io.github.open_policy_agent.opa.ir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import io.github.open_policy_agent.opa.OpaException;
import io.github.open_policy_agent.opa.ast.builtin.BuiltinRegistry;
import io.github.open_policy_agent.opa.ast.builtin.impls.ProvidersAwsBuiltins;
import io.github.open_policy_agent.opa.ast.types.RegoBigInt;
import io.github.open_policy_agent.opa.ast.types.RegoBoolean;
import io.github.open_policy_agent.opa.ast.types.RegoInt32;
import io.github.open_policy_agent.opa.ast.types.RegoObject;
import io.github.open_policy_agent.opa.ast.types.RegoSet;
import io.github.open_policy_agent.opa.ast.types.RegoString;
import io.github.open_policy_agent.opa.ast.types.RegoValue;
import io.github.open_policy_agent.opa.ir.policy.Policy;
import io.github.open_policy_agent.opa.jackson.RegoValueModule;
import io.github.open_policy_agent.opa.rego.EvaluationContext;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;
import java.util.function.BiFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Focused tests for the {@code providers.aws.sign_req} builtin.
 *
 * <p>Part 1 runs each compiled compliance plan end-to-end (using the fixtures' exact inputs and
 * expected values). Part 2 calls the builtin directly for every success case and prints the actual
 * computed {@code Authorization} header alongside the expected value extracted from the fixture.
 */
@DisplayName("providers.aws.sign_req")
public class ProvidersAwsSignReqTest {

  private static final String SUCCESS_FIXTURE =
      "compliance/Tests/RegoComplianceTests/TestData/v1/providers-aws/aws-sign_req.json";
  private static final String ERROR_FIXTURE =
      "compliance/Tests/RegoComplianceTests/TestData/v1/providers-aws/aws-sign_req-errors.json";

  private static final PolicyReader POLICY_READER =
      ServiceLoader.load(PolicyReader.class).findFirst().orElseThrow();

  private static final Pattern AUTH_PATTERN =
      Pattern.compile("\"Authorization\": \"(AWS4-HMAC-SHA256[^\"]*)\"");

  private static final long SIGNING_TS = 1451311705000000000L;

  private static ObjectMapper mapper() {
    return new ObjectMapper().registerModule(new RegoValueModule());
  }

  private static JsonNode loadCases(String resource) throws Exception {
    try (InputStream in =
        ProvidersAwsSignReqTest.class.getClassLoader().getResourceAsStream(resource)) {
      assertNotNull(in, "fixture not found on classpath: " + resource);
      return mapper().readTree(in).get("cases");
    }
  }

  // ------------------------------------------------------------------
  // Part 1: end-to-end evaluation of the compiled plans (all 18 cases)
  // ------------------------------------------------------------------

  static List<Arguments> allComplianceCases() throws Exception {
    List<Arguments> out = new ArrayList<>();
    for (JsonNode c : loadCases(SUCCESS_FIXTURE)) {
      out.add(Arguments.of(shortNote(c), c, false));
    }
    for (JsonNode c : loadCases(ERROR_FIXTURE)) {
      out.add(Arguments.of(shortNote(c), c, true));
    }
    return out;
  }

  @ParameterizedTest(name = "plan[{0}]")
  @MethodSource("allComplianceCases")
  void endToEndPlan(String note, JsonNode root, boolean expectError) throws Exception {
    ObjectMapper mapper = mapper();
    Policy policy =
        POLICY_READER.read(new ByteArrayInputStream(mapper.writeValueAsBytes(root.get("plan"))));
    String entrypoint = root.get("entrypoints").get(0).asText();
    BuiltinRegistry registry = BuiltinRegistry.allCapabilities();
    EvaluationContext.Builder ctxBuilder =
        new EvaluationContext.Builder()
            .withBuiltinRegistry(registry)
            .withSortedSets()
            .withEntrypoint(entrypoint);
    if (root.has("strict_error") && root.get("strict_error").asBoolean()) {
      ctxBuilder.withStrictBuiltinErrors();
    }
    EvaluationContext ctx = ctxBuilder.build();
    Evaluator evaluator =
        new Evaluator.Builder().withPolicy(policy).withBuiltinRegistry(registry).build();

    RegoObject data = mapper.treeToValue(mapper.readTree("{}"), RegoObject.class);

    if (expectError) {
      String wantError = root.get("want_error").asText();
      String wantCode = root.get("want_error_code").asText();
      try {
        evaluator.evaluate(ctx, parseNull(mapper), data);
        fail("[" + note + "] expected error but evaluation succeeded");
      } catch (OpaException e) {
        assertEquals(wantCode, e.getErrorCode(), "[" + note + "] error code");
        String actual = e.getMessage();
        assertTrue(
            actual.contains(wantError) || wantError.contains(actual),
            "[" + note + "] error message mismatch\n  expected: " + wantError + "\n  actual:   "
                + actual);
        System.out.println("PASS error [" + note + "]  " + wantError);
      }
    } else {
      RegoValue[] result = evaluator.evaluate(ctx, parseNull(mapper), data);
      String actual = mapper.writeValueAsString(result);
      String expected = mapper.writeValueAsString(root.get("want_result"));
      assertEquals(expected, actual, "[" + note + "] plan result");
      System.out.println("PASS plan  [" + note + "]  result=" + actual);
    }
  }

  private static RegoValue parseNull(ObjectMapper mapper) throws Exception {
    return mapper.treeToValue(NullNode.getInstance(), RegoObject.class);
  }

  // ------------------------------------------------------------------
  // Part 2: direct builtin invocation, showing computed vs expected signatures
  // ------------------------------------------------------------------

  static List<Arguments> successCases() throws Exception {
    List<Arguments> out = new ArrayList<>();
    for (JsonNode c : loadCases(SUCCESS_FIXTURE)) {
      String note = shortNote(c);
      String module = c.get("modules").get(0).asText();
      Matcher m = AUTH_PATTERN.matcher(module);
      assertTrue(m.find(), "could not extract expected Authorization for " + note);
      out.add(Arguments.of(note, m.group(1)));
    }
    return out;
  }

  @ParameterizedTest(name = "sign[{0}]")
  @MethodSource("successCases")
  void computedSignature(String note, String expectedAuthorization) {
    BiFunction<EvaluationContext, RegoValue[], RegoValue> fn =
        new ProvidersAwsBuiltins().builtins().get("providers.aws.sign_req");
    RegoValue[] args = buildArgs(note);

    RegoObject signed = (RegoObject) fn.apply(null, args);
    RegoObject headers = (RegoObject) signed.getProperty("headers");
    String actualAuth = ((RegoString) headers.getProperty("Authorization")).getValue();
    RegoValue contentSha = headers.getProperty("x-amz-content-sha256");
    String contentShaStr = contentSha == null ? "(none)" : ((RegoString) contentSha).getValue();

    String expectedSig = signatureOf(expectedAuthorization);
    String actualSig = signatureOf(actualAuth);

    System.out.println("---- " + note + " ----");
    System.out.println("  x-amz-content-sha256: " + contentShaStr);
    System.out.println("  expected Signature=" + expectedSig);
    System.out.println("  actual   Signature=" + actualSig);
    System.out.println("  match: " + expectedSig.equals(actualSig));

    assertEquals(expectedAuthorization, actualAuth, "[" + note + "] Authorization header");
  }

  private static String signatureOf(String authHeader) {
    int i = authHeader.indexOf("Signature=");
    return i < 0 ? authHeader : authHeader.substring(i + "Signature=".length());
  }

  // ------------------------------------------------------------------
  // Input builders (transcribed from the fixture modules; any mistake surfaces
  // as an Authorization mismatch against the fixture-derived expected value).
  // ------------------------------------------------------------------

  private static RegoValue[] buildArgs(String note) {
    RegoObject req;
    RegoObject config = baseConfig();
    switch (note) {
      case "success-simple-no body":
        req = req("get", "http://example.com");
        break;
      case "success-simple-with headers no body":
        req = req("get", "http://example.com");
        req.setProp(str("headers"), obj("foo", str("bar")));
        break;
      case "success-simple-no body-with session token":
        req = req("get", "http://example.com");
        config.setProp(str("aws_session_token"), str("MYAWSSECURITYTOKENGOESHERE"));
        break;
      case "success-simple-body":
        req = req("get", "http://example.com");
        req.setProp(str("body"), exampleBody());
        break;
      case "success-simple-raw_body":
        req = req("get", "http://example.com");
        req.setProp(str("raw_body"), str("{\"example\": {1, 2, 3, 4}}"));
        break;
      case "success-simple-body-and-raw_body":
        req = req("get", "http://example.com");
        req.setProp(str("body"), exampleBody());
        req.setProp(str("raw_body"), str("{\"example\": {1, 2, 3, 4}}"));
        break;
      case "success-simple-with-headers-no-body-with-payload-signing":
        req = req("get", "https://example.com");
        req.setProp(str("headers"), obj("foo", str("bar")));
        config.setProp(str("disable_payload_signing"), RegoBoolean.FALSE);
        break;
      case "success-simple-with-headers-no-body-no-payload-signing":
        req = req("get", "https://example.com");
        req.setProp(str("headers"), obj("foo", str("bar")));
        config.setProp(str("disable_payload_signing"), RegoBoolean.TRUE);
        break;
      case "success-simple-with-headers-with-body-with-payload-signing":
        req = req("get", "https://example.com");
        req.setProp(str("headers"), obj("foo", str("bar")));
        req.setProp(str("body"), exampleBody());
        config.setProp(str("disable_payload_signing"), RegoBoolean.FALSE);
        break;
      case "success-simple-with-headers-with-body-no-payload-signing":
        req = req("get", "https://example.com");
        req.setProp(str("headers"), obj("foo", str("bar")));
        req.setProp(str("body"), exampleBody());
        config.setProp(str("disable_payload_signing"), RegoBoolean.TRUE);
        break;
      case "success-simple-with-existing-sha-header-with-body-with-payload-signing":
        req = req("get", "https://example.com");
        req.setProp(str("headers"), existingShaHeaders());
        req.setProp(str("body"), exampleBody());
        config.setProp(str("disable_payload_signing"), RegoBoolean.FALSE);
        break;
      case "success-simple-with-existing-sha-header-with-body-no-payload-signing":
        req = req("get", "https://example.com");
        req.setProp(str("headers"), existingShaHeaders());
        req.setProp(str("body"), exampleBody());
        config.setProp(str("disable_payload_signing"), RegoBoolean.TRUE);
        break;
      default:
        throw new IllegalArgumentException("unhandled success case: " + note);
    }
    return new RegoValue[] {req, config, new RegoBigInt(BigInteger.valueOf(SIGNING_TS))};
  }

  private static RegoString str(String s) {
    return new RegoString(s);
  }

  private static RegoObject req(String method, String url) {
    RegoObject o = new RegoObject();
    o.setProp(str("method"), str(method));
    o.setProp(str("url"), str(url));
    return o;
  }

  private static RegoObject obj(String key, RegoValue value) {
    RegoObject o = new RegoObject();
    o.setProp(str(key), value);
    return o;
  }

  private static RegoObject existingShaHeaders() {
    RegoObject o = new RegoObject();
    o.setProp(str("foo"), str("bar"));
    o.setProp(str("x-amz-content-sha256"), str("existing-value"));
    return o;
  }

  private static RegoObject baseConfig() {
    RegoObject o = new RegoObject();
    o.setProp(str("aws_access_key"), str("MYAWSACCESSKEYGOESHERE"));
    o.setProp(str("aws_secret_access_key"), str("MYAWSSECRETACCESSKEYGOESHERE"));
    o.setProp(str("aws_service"), str("s3"));
    o.setProp(str("aws_region"), str("us-east-1"));
    return o;
  }

  /** body := {"example": {1, 2, 3, 4}} */
  private static RegoObject exampleBody() {
    RegoSet set = new RegoSet(true);
    set.addValue(RegoInt32.of(1));
    set.addValue(RegoInt32.of(2));
    set.addValue(RegoInt32.of(3));
    set.addValue(RegoInt32.of(4));
    return obj("example", set);
  }

  private static String shortNote(JsonNode c) {
    String note = c.get("note").asText();
    int slash = note.indexOf('/');
    return slash < 0 ? note : note.substring(slash + 1);
  }
}
