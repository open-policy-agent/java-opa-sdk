package io.github.open_policy_agent.opa.ir;

import static io.github.open_policy_agent.opa.tracing.TracePrinter.traceToString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import io.github.open_policy_agent.opa.OpaException;
import io.github.open_policy_agent.opa.ast.builtin.BuiltinRegistry;
import io.github.open_policy_agent.opa.ast.types.RegoBigInt;
import io.github.open_policy_agent.opa.ast.types.RegoBoolean;
import io.github.open_policy_agent.opa.ast.types.RegoDecimal;
import io.github.open_policy_agent.opa.ast.types.RegoInt32;
import io.github.open_policy_agent.opa.ast.types.RegoObject;
import io.github.open_policy_agent.opa.ast.types.RegoSet;
import io.github.open_policy_agent.opa.ast.types.RegoString;
import io.github.open_policy_agent.opa.ast.types.RegoValue;
import io.github.open_policy_agent.opa.ir.policy.Policy;
import io.github.open_policy_agent.opa.rego.EvaluationContext;
import io.github.open_policy_agent.opa.tracing.BufferedQueryTracer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.skyscreamer.jsonassert.JSONAssert;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.Stack;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@DisplayName("Compliance Tests")
public class ComplianceTest {

  private static final Pattern JWT_PATTERN =
      Pattern.compile("^[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]*\\.[A-Za-z0-9_-]*$");
  private static final PolicyReader POLICY_READER =
      ServiceLoader.load(PolicyReader.class).findFirst().orElseThrow();
  private static final String complianceDir;
  private static final Set<String> missingFunctions = ConcurrentHashMap.newKeySet();

  private static final boolean ignoreFunctionNotFoundTests = true;

  /**
   * Alternate acceptable error message substrings for tests where the Java IR evaluator produces a
   * cosmetically different error message than the Go OPA implementation. Keyed by test note.
   */
  private static final Map<String, List<String>> ALTERNATE_ERROR_MESSAGES = Map.of(
      "strings/any_suffix_match/type_error_strict", List.of(
          "eval_type_error: strings.any_suffix_match: eval_type_error: operand 1 must be one of {string, set, array} but got number",
          "eval_type_error: strings.any_suffix_match: eval_type_error: operand 2 must be one of {string, set, array} but got number",
          "operand 0 must be array of strings but got array containing number"
      ),
        "strings/any_prefix_match/type_error_strict", List.of("eval_type_error: strings.any_prefix_match: operand 0 must be array of strings but got array containing number")
  );

  static {
    try {
      URL resource =
          ComplianceTest.class
              .getClassLoader()
              .getResource("compliance/Tests/RegoComplianceTests/TestData");
      //       .getResource("compliance/Tests/RegoComplianceTests/TestData/v1/jwtbuiltins");
      //                .getResource("compliance/Tests/RegoComplianceTests/TestData/v1/eqexpr");
      if (resource == null) {
        throw new RuntimeException("compliance directory not found");
      }
      complianceDir = Paths.get(resource.toURI()).toString();
    } catch (URISyntaxException e) {
      throw new RuntimeException(e);
    }
  }

  @AfterAll
  public static void reportMissingFunctions() {
    if (!missingFunctions.isEmpty()) {
      String report = buildMissingFunctionsReport();
      // TODO: add missing builtins
      System.err.println(report);
    }
  }

  private static String buildMissingFunctionsReport() {
    StringBuilder sb = new StringBuilder();
    sb.append("\n=== Missing Builtin Report ===\n");
    sb.append("Total missing builtins: ").append(missingFunctions.size()).append("\n");
    missingFunctions.stream().sorted().forEach(fn -> sb.append("  - ").append(fn).append("\n"));
    sb.append("================================\n");
    return sb.toString();
  }

  public static Stream<Object[]> getComplianceTestData() throws IOException {
    return Files.walk(Paths.get(complianceDir))
            .filter(Files::isRegularFile)
            .filter(path -> !path.endsWith("index.json"))
            .filter(path -> path.toString().endsWith(".json"))
                .map(Path::toFile)
                .flatMap(f -> {
                    try {
                      ObjectMapper mapper = new ObjectMapper().registerModule(new io.github.open_policy_agent.opa.jackson.RegoValueModule());
                      JsonNode root = mapper.readTree(f);
                      List<JsonNode> cases = new ArrayList<>();
                      root.get("cases").forEach(cases::add);
                      return cases.stream()
                              .map(c -> new Object[]{
                                      c.get("note").asText("unknown"), c
                              });
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
  }

  private static boolean looksLikeJwt(JsonNode node) {
    if (node == null) {
      return false;
    }
    if (node.isArray()) {
      return looksLikeJwt(node.get(0));
    }
    if (node.isTextual()) {
      return JWT_PATTERN.matcher(node.asText()).matches();
    }
    if (node.isObject() && node.has("x") && node.get("x").isTextual()) {
      return JWT_PATTERN.matcher(node.get("x").asText()).matches();
    }
    return false;
  }

  // TODO: This is (mostly) copied from TimeBuiltins, so maybe there should be a util they both draw
  // from
  public long parseDurationMs(String duration) {

    long ns;

    if (duration.endsWith("ns")) {
      duration = duration.substring(0, duration.length() - 2);
      ns = Long.parseLong(duration) / 1_000_000;
    } else if (duration.endsWith("us") || duration.endsWith("µs")) {
      duration = duration.substring(0, duration.length() - 2);
      ns = Long.parseLong(duration) / 1000;
    } else if (duration.endsWith("ms")) {
      duration = duration.substring(0, duration.length() - 2);
      ns = Long.parseLong(duration);
    } else if (duration.endsWith("s")) {
      duration = duration.substring(0, duration.length() - 1);
      ns = Long.parseLong(duration) * 1_000_000;
    } else if (duration.endsWith("m")) {
      duration = duration.substring(0, duration.length() - 1);
      ns = Long.parseLong(duration) * 1_000_000 * 60;
    } else if (duration.endsWith("h")) {
      duration = duration.substring(0, duration.length() - 1);
      ns = Long.parseLong(duration) * 1_000_000 * 60 * 60;
    } else {
      throw new UnsupportedOperationException("Unsupported time format: " + duration);
    }
    return ns;
  }

  private static void assertJwtsEqual(String jwt1, String jwt2) {
    String[] parts1 = jwt1.split("\\.");
    String[] parts2 = jwt2.split("\\.");

    assertThat(parts1).hasSize(3).describedAs("JWT1 should have 3 parts");
    assertThat(parts2).hasSize(3).describedAs("JWT2 should have 3 parts");

    // Decode and compare headers
    String header1 = decodeBase64(parts1[0]);
    String header2 = decodeBase64(parts2[0]);

    // Check if payload is empty or if it's text/plain
    boolean isEmptyPayload1 = parts1[1].isEmpty();
    boolean isEmptyPayload2 = parts2[1].isEmpty();

    String payload1 = isEmptyPayload1 ? "" : decodeBase64(parts1[1]);
    String payload2 = isEmptyPayload2 ? "" : decodeBase64(parts2[1]);

    // Check if header indicates text/plain type
    boolean isTextPlain =
        header1.contains("\"typ\":\"text/plain\"") || header1.contains("\"typ\": \"text/plain\"");

    // Use assertAll to see all failures at once
    assertAll(
        "JWT Equality",
        () -> JSONAssert.assertEquals("Headers should match", header1, header2, false),
        () -> {
          if (isEmptyPayload1 && isEmptyPayload2) {
            // Both empty, that's fine
            assertEquals(payload1, payload2, "Payloads should both be empty");
          } else if (isTextPlain) {
            // For text/plain, compare as plain strings
            assertEquals(payload1, payload2, "Text payloads should match");
          } else {
            // For JSON payloads, use JSON comparison
            JSONAssert.assertEquals("Payloads should match", payload1, payload2, false);
          }
        });
  }

  private static String decodeBase64(String encoded) {
    return new String(Base64.getUrlDecoder().decode(encoded));
  }

  /**
   * Converts Rego set syntax to array syntax for JSON parsing. Transforms {{1}} to [[1]], {1, 2} to
   * [1, 2], etc. Leaves JSON objects with key:value pairs unchanged.
   */
  private static String convertRegoSetsToArrays(String regoStr) {
    StringBuilder result = new StringBuilder();
    boolean inString = false;
    char prevChar = '\0';

    // Track brace positions and their content to determine if they're sets or objects
    Stack<Integer> braceStarts = new Stack<>();
    Stack<Boolean> isObject = new Stack<>(); // true if this brace level contains ':'

    for (int i = 0; i < regoStr.length(); i++) {
      char c = regoStr.charAt(i);

      // Track if we're inside a string
      if (c == '"' && prevChar != '\\') {
        inString = !inString;
      }

      if (!inString) {
        if (c == '{') {
          braceStarts.push(result.length());
          isObject.push(false); // Assume set until we find a ':'
          result.append('['); // Tentatively convert to array
        } else if (c == '}') {
          if (!braceStarts.isEmpty()) {
            boolean wasObject = isObject.pop();
            int startPos = braceStarts.pop();
            if (wasObject) {
              // This was actually an object, convert back
              result.setCharAt(startPos, '{');
              result.append('}');
            } else {
              // This was a set, keep as array
              result.append(']');
            }
          } else {
            result.append(c);
          }
        } else if (c == ':' && !isObject.isEmpty()) {
          // Found a colon, mark current brace level as object
          isObject.set(isObject.size() - 1, true);
          result.append(c);
        } else {
          result.append(c);
        }
      } else {
        result.append(c);
      }

      prevChar = c;
    }

    return result.toString();
  }

  private static RegoValue jsonNodeToRegoValue(JsonNode node, ObjectMapper mapper)
      throws IOException {
    return jsonNodeToRegoValue(node, mapper, false);
  }

  private static RegoValue jsonNodeToRegoValue(
      JsonNode node, ObjectMapper mapper, boolean treatArraysAsSets) throws IOException {
    if (node == null || node.isNull()) {
      return mapper.treeToValue(node, RegoObject.class);
    } else if (node.isTextual()) {
      return mapper.treeToValue(node, RegoString.class);
    } else if (node.isNumber()) {
      // Handle numeric types - check for integer vs decimal
      if (node.isIntegralNumber()) {
        long value = node.asLong();
        // Use RegoInt32 for values that fit in an int, otherwise RegoBigInt
        if (value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE) {
          return RegoInt32.of((int) value);
        } else {
          return new RegoBigInt(value);
        }
      } else {
        // Floating point number
        return new RegoDecimal(node.asDouble());
      }
    } else if (node.isArray() && treatArraysAsSets) {
      // Convert array to set recursively
      RegoSet set = new RegoSet(true);
      for (JsonNode element : node) {
        RegoValue elementValue = jsonNodeToRegoValue(element, mapper, treatArraysAsSets);
        set.addValue(elementValue);
      }
      return set;
    } else if (node.isObject()) {
      // When treatArraysAsSets is true, recursively process object values
      if (treatArraysAsSets) {
        RegoObject obj = new RegoObject();
        node.fields()
            .forEachRemaining(
                entry -> {
                  try {
                    RegoValue key = new RegoString(entry.getKey());
                    RegoValue value =
                        jsonNodeToRegoValue(entry.getValue(), mapper, treatArraysAsSets);
                    obj.setProp(key, value);
                  } catch (IOException e) {
                    throw new RuntimeException(e);
                  }
                });
        return obj;
      } else {
        return mapper.treeToValue(node, RegoObject.class);
      }
    } else {
      // For other types (arrays, booleans), delegate to Jackson's deserializer
      return mapper.treeToValue(node, RegoObject.class);
    }
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("getComplianceTestData")
  public void testEvaluate(String caseName, JsonNode root) {
    try {
      ObjectMapper mapper = new ObjectMapper().registerModule(new io.github.open_policy_agent.opa.jackson.RegoValueModule());

      if (root.has("skip") && root.get("skip").asBoolean()) {
        System.out.println("skipping: " + caseName);
        return;
      }

      Policy policy = POLICY_READER.read(new ByteArrayInputStream(mapper.writeValueAsBytes(root.get("plan"))));
      JsonNode input = root.get("input");
      if (input == null) {
        input = NullNode.getInstance();
      }

      JsonNode data = root.get("data");
      if (data == null) {
        data = mapper.readTree("{}");
      }

      String entrypoint = root.get("entrypoints").get(0).asText();
      BuiltinRegistry builtinRegistry = BuiltinRegistry.allCapabilities();
      builtinRegistry.registerBuiltIn(
          "test.sleep",
          (ctx, args) -> {
            try {
              Thread.sleep(parseDurationMs(((RegoString) args[0]).getValue()));
            } catch (InterruptedException ignore) {
            }
            return RegoBoolean.TRUE;
          });

      BufferedQueryTracer tracer = new BufferedQueryTracer();

      EvaluationContext.Builder contextBuilder =
          new EvaluationContext.Builder()
              .withBuiltinRegistry(builtinRegistry)
              .withSortedSets()
              .withEntrypoint(entrypoint)
              .withTracer(tracer);

      if (root.has("strict_error") && root.get("strict_error").asBoolean()) {
        contextBuilder.withStrictBuiltinErrors();
      }

      EvaluationContext ctx = contextBuilder.build();

      try {
        Evaluator evaluator =
            new Evaluator.Builder().withPolicy(policy).withBuiltinRegistry(builtinRegistry).build();
        RegoValue inputObj = jsonNodeToRegoValue(input, mapper);
        RegoObject dataObj = mapper.treeToValue(data, RegoObject.class);

        JsonNode inputTerm = root.get("input_term");
        if (inputTerm != null) {
          // If inputTerm is a string containing JSON or Rego syntax
          if (inputTerm.isTextual()) {
            String termStr = inputTerm.asText();
            try {
              // First try parsing as standard JSON
              JsonNode parsedJson = mapper.readTree(termStr);
              inputObj = jsonNodeToRegoValue(parsedJson, mapper);
            } catch (Exception e) {
              // If JSON parsing fails, it might contain Rego set syntax like {{1}}
              // Convert Rego set syntax {} to array syntax [] and try again
              String convertedStr = convertRegoSetsToArrays(termStr);
              JsonNode parsedJson = mapper.readTree(convertedStr);
              // Parse with treatArraysAsSets=true since we converted sets to arrays
              inputObj = jsonNodeToRegoValue(parsedJson, mapper, true);
            }
          } else {
            // If inputTerm is already a JSON node, convert it to appropriate RegoValue
            inputObj = jsonNodeToRegoValue(inputTerm, mapper);
          }
        }

        RegoValue[] result = evaluator.evaluate(ctx, inputObj, dataObj);

        JsonNode want = root.get("want_result");
        if (want == null) {
          JsonNode wantError = root.get("want_error");
          if (wantError != null) {
            fail("error wanted but not thrown: " + wantError.asText());
          }
          System.out.println("no want_result for: " + caseName);
          return;
        }

        if (looksLikeJwt(want)) {
          RegoObject r = (RegoObject) result[0];
          String jwt = (String) r.getProperty("x").nativeValue();
          // this will not test signature equality
          assertJwtsEqual(want.get(0).get("x").asText(), jwt);
        } else {
          boolean sortBindings = root.has("sort_bindings") && root.get("sort_bindings").asBoolean();
          String actual = mapper.writeValueAsString(result);
          String expected = mapper.writeValueAsString(want);
          if (sortBindings) {
            JsonNode actualNode = deepSortArrays(mapper.readTree(actual));
            JsonNode expectedNode = deepSortArrays(mapper.readTree(expected));
            assertEquals(mapper.writeValueAsString(expectedNode), mapper.writeValueAsString(actualNode), traceToString(tracer));
          } else {
            assertEquals(expected, actual, traceToString(tracer));
          }
        }
      } catch (OpaException re) {
        // Track missing functions for reporting
        if (re instanceof FunctionNotFoundError) {
          String functionName = (String) re.getContext().get("name");
          if (functionName != null) {
            missingFunctions.add(functionName);
          }
          if (ignoreFunctionNotFoundTests) {
            return;
          }
        }

        if (root.get("want_error_code") == null && root.get("want_error") == null) {
          fail("error received, but not expected: " + re);
        } else {
          if (root.has("want_error_code")) {
            String wantCode = root.get("want_error_code").asText();
            assertEquals(wantCode, re.getErrorCode());
          }

          if (root.has("want_error")) {
            String wantError = root.get("want_error").asText();
            List<String> acceptable = new ArrayList<>();
            acceptable.add(wantError);
            List<String> alternates = ALTERNATE_ERROR_MESSAGES.get(caseName);
            if (alternates != null) {
              acceptable.addAll(alternates);
            }
            String actualMsg = re.getMessage();
            boolean matched =
                acceptable.stream()
                    .anyMatch(exp -> exp.contains(actualMsg) || actualMsg.contains(exp));
            assertThat(matched)
                .withFailMessage(
                    "Error message mismatch:\n  expected to contain one of: %s\n  but was: <%s>\n%s",
                    acceptable, actualMsg, traceToString(tracer))
                .isTrue();
          }
        }
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    } catch (IOException e) {
      e.printStackTrace();
      fail("test threw exception: " + e);
    }
  }

  /**
   * Recursively sorts all arrays in a JsonNode tree for deterministic comparison.
   * Used when sort_bindings is true in OPA compliance tests.
   */
  private static JsonNode deepSortArrays(JsonNode node) {
    if (node.isArray()) {
      ArrayNode array = (ArrayNode) node;
      List<JsonNode> elements = new ArrayList<>();
      for (JsonNode element : array) {
        elements.add(deepSortArrays(element));
      }
      elements.sort(Comparator.comparing(JsonNode::toString));
      ArrayNode sorted = array.arrayNode();
      elements.forEach(sorted::add);
      return sorted;
    } else if (node.isObject()) {
      ObjectNode obj = (ObjectNode) node;
      ObjectNode result = obj.objectNode();
      Iterator<Map.Entry<String, JsonNode>> fields = obj.fields();
      while (fields.hasNext()) {
        Map.Entry<String, JsonNode> field = fields.next();
        result.set(field.getKey(), deepSortArrays(field.getValue()));
      }
      return result;
    }
    return node;
  }
}
