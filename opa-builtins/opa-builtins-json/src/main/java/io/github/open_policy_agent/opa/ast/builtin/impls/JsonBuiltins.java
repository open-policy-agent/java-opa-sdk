package io.github.open_policy_agent.opa.ast.builtin.impls;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.exc.InvalidTypeIdException;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.github.fge.jsonpatch.JsonPatch;
import com.github.fge.jsonpatch.JsonPatchException;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import java.io.IOException;
import java.util.*;
import java.util.function.BiFunction;
import io.github.open_policy_agent.opa.ast.builtin.BuiltinError;
import io.github.open_policy_agent.opa.ast.builtin.BuiltinProvider;
import io.github.open_policy_agent.opa.ast.builtin.OpaBuiltin;
import io.github.open_policy_agent.opa.ast.builtin.OpaType;
import io.github.open_policy_agent.opa.ast.types.*;
import io.github.open_policy_agent.opa.rego.EvaluationContext;
import io.github.open_policy_agent.opa.rego.TypeError;

import static io.github.open_policy_agent.opa.ast.builtin.impls.utils.ArgHelper.getArg;

public class JsonBuiltins implements BuiltinProvider {

  private static final ObjectMapper JSON_MAPPER;
  private static final ObjectMapper YAML_MAPPER;

  // Shared serializers for Rego numeric types
  private static final JsonSerializer<RegoDecimal> REGO_DECIMAL_SERIALIZER =
          new JsonSerializer<>() {
            @Override
            public void serialize(RegoDecimal value, JsonGenerator gen, SerializerProvider serializers)
                    throws IOException {
              Double d = value.getValue();
              // If the double is a whole number that fits in a long, serialize as integer
              if (d != null && TypeUtils.isWholeNumberInLongRange(d)) {
                gen.writeNumber(d.longValue());
              } else {
                gen.writeNumber(d);
              }
            }
          };

  private static final JsonSerializer<RegoBigInt> REGO_BIG_INT_SERIALIZER =
          new JsonSerializer<>() {
            @Override
            public void serialize(RegoBigInt value, JsonGenerator gen, SerializerProvider serializers)
                    throws IOException {
              gen.writeNumber(value.getValue());
            }
          };

  private static final JsonSerializer<RegoInt32> REGO_INT32_SERIALIZER =
          new JsonSerializer<>() {
            @Override
            public void serialize(RegoInt32 value, JsonGenerator gen, SerializerProvider serializers)
                    throws IOException {
              gen.writeNumber(value.getValue());
            }
          };

  private static final JsonSerializer<RegoNull> REGO_NULL_SERIALIZER =
          new JsonSerializer<>() {
            @Override
            public void serialize(RegoNull value, JsonGenerator gen, SerializerProvider serializers)
                    throws IOException {
              gen.writeNull();
            }
          };

  static {
    // findAndRegisterModules picks up RegoValueModule (from opa-jackson) via SPI so
    // RegoString/RegoArray/RegoObject etc. (de)serialize without annotations on the AST types.
    JSON_MAPPER = new ObjectMapper().findAndRegisterModules();

    // Register custom serializers for Rego numeric types (overrides RegoValueModule defaults).
    SimpleModule module = new SimpleModule();
    module.addSerializer(RegoDecimal.class, REGO_DECIMAL_SERIALIZER);
    module.addSerializer(RegoBigInt.class, REGO_BIG_INT_SERIALIZER);
    module.addSerializer(RegoInt32.class, REGO_INT32_SERIALIZER);
    JSON_MAPPER.registerModule(module);

    // YAML mapper needs all serializers including null handling
    SimpleModule yamlModule = new SimpleModule();
    yamlModule.addSerializer(RegoDecimal.class, REGO_DECIMAL_SERIALIZER);
    yamlModule.addSerializer(RegoBigInt.class, REGO_BIG_INT_SERIALIZER);
    yamlModule.addSerializer(RegoInt32.class, REGO_INT32_SERIALIZER);
    yamlModule.addSerializer(RegoNull.class, REGO_NULL_SERIALIZER);

    YAML_MAPPER =
            new ObjectMapper(
                    YAMLFactory.builder().disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER).build())
                .findAndRegisterModules();
    YAML_MAPPER.registerModule(yamlModule);
  }

  @Override
  public Map<String, BiFunction<EvaluationContext, RegoValue[], RegoValue>> builtins() {
    JsonBuiltins instance = new JsonBuiltins();
    // @formatter:off
    Map<String, BiFunction<EvaluationContext, RegoValue[], RegoValue>> result =
            new LinkedHashMap<>();
    result.put("json.filter", instance::filter);
    result.put("json.is_valid", instance::is_valid);
    result.put("json.marshal", instance::marshal);
    result.put("json.marshal_with_options", instance::marshal_with_options);
    result.put("json.match_schema", instance::match_schema);
    result.put("json.patch", instance::patch);
    result.put("json.remove", instance::remove);
    result.put("json.unmarshal", instance::unmarshal);
    result.put("json.verify_schema", instance::verify_schema);
    result.put("yaml.marshal", instance::yamlMarshal);
    result.put("yaml.unmarshal", instance::yamlUnmarshal);
    return result;
    // @formatter:on
  }

  @OpaBuiltin(
          name = "json.marshal",
          description = "Serializes the input term to JSON.",
          categories = {"encoding"},
          args = {@OpaType(type = "any", name = "x", description = "the term to serialize")},
          result =
          @OpaType(
                  type = "string",
                  name = "y",
                  description = "the JSON string representation of `x`"))
  public RegoString marshal(EvaluationContext ctx, RegoValue[] args) {
    RegoValue input = args[0];

    try {
      String json = JSON_MAPPER.writeValueAsString(input);
      return new RegoString(json);
    } catch (JsonProcessingException e) {
      throw new BuiltinError("json.marshal: " + e.getMessage());
    }
  }

  @OpaBuiltin(
          name = "json.marshal_with_options",
          description = "Serializes the input term to JSON with options.",
          categories = {"encoding"},
          args = {
                  @OpaType(name = "x", description = "the term to serialize"),
                  @OpaType(name = "opts", description = "encoding options")
          },
          result = @OpaType(name = "y", description = "the JSON string representation of `x`"))
  public RegoString marshal_with_options(EvaluationContext ctx, RegoValue[] args) {
    RegoValue input = args[0];
    RegoObject options = getArg(args, 1, RegoObject.class);

    try {
      ObjectMapper mapper = JSON_MAPPER.copy();

      // Check for indent option
      RegoValue indentValue = options.getProperty(new RegoString("indent"));
      if (indentValue instanceof RegoString) {
        String indent = ((RegoString) indentValue).getValue();
        DefaultPrettyPrinter printer = new DefaultPrettyPrinter();
        // Use DefaultIndenter which accepts custom indent string
        DefaultPrettyPrinter.Indenter indenter =
                new DefaultIndenter(indent, DefaultIndenter.SYS_LF);
        printer.indentArraysWith(indenter);
        printer.indentObjectsWith(indenter);
        mapper.setDefaultPrettyPrinter(printer);
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
      }

      // Check for prefix option (for pretty printing)
      RegoValue prefixValue = options.getProperty(new RegoString("prefix"));
      if (prefixValue instanceof RegoString) {
        // Prefix is typically used with indent for pretty printing
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
      }

      String json = mapper.writeValueAsString(input);
      return new RegoString(json);
    } catch (JsonProcessingException e) {
      throw new BuiltinError("json.marshal_with_options: " + e.getMessage());
    }
  }

  @OpaBuiltin(
          name = "json.unmarshal",
          description = "Deserializes the input string.",
          categories = {"encoding"},
          args = {@OpaType(name = "x", description = "a JSON string")},
          result = @OpaType(name = "y", description = "the term deserialized from `x`"))
  public RegoValue unmarshal(EvaluationContext ctx, RegoValue[] args) {
    String jsonInput = getArg(args, 0, RegoString.class).getValue();

    try {
      Object parsed = JSON_MAPPER.readValue(jsonInput, Object.class);
      return convertToRegoValue(parsed);
    } catch (JsonProcessingException e) {
      throw new BuiltinError("json.unmarshal: " + e.getMessage());
    }
  }

  @OpaBuiltin(
          name = "json.is_valid",
          description = "Verifies the input string is a valid JSON document.",
          categories = {"encoding"},
          args = {@OpaType(name = "x", description = "a JSON string")},
          result =
          @OpaType(name = "result", description = "`true` if `x` is valid JSON, `false` otherwise"))
  public RegoBoolean is_valid(EvaluationContext ctx, RegoValue[] args) {
    String jsonInput = getArg(args, 0, RegoString.class).getValue();

    try {
      JSON_MAPPER.readTree(jsonInput);
      return RegoBoolean.TRUE;
    } catch (JsonProcessingException e) {
      return RegoBoolean.FALSE;
    }
  }

  @OpaBuiltin(
          name = "json.filter",
          description = "Filters object keys by the given list of JSON Pointers.",
          categories = {"encoding"},
          args = {
                  @OpaType(name = "object", description = "object to filter"),
                  @OpaType(name = "paths", description = "JSON string paths")
          },
          result =
          @OpaType(
                  name = "filtered",
                  description = "remaining data from `object` with only keys specified in `paths`"))
  public RegoValue filter(EvaluationContext ctx, RegoValue[] args) {
    RegoObject object = getArg(args, 0, RegoObject.class);
    RegoCollection pathsArg = getArg(args, 1, RegoCollection.class);

    try {
      // Convert object to JsonNode
      String json = JSON_MAPPER.writeValueAsString(object);
      JsonNode node = JSON_MAPPER.readTree(json);

      // Extract paths from set or array
      Set<String> paths = extractPaths(pathsArg, ctx.sortSets);

      // Filter the object
      JsonNode filtered = filterNode(node, paths);

      // Convert back to RegoValue
      Object result = JSON_MAPPER.treeToValue(filtered, Object.class);
      return convertToRegoValue(result);
    } catch (Exception e) {
      throw new BuiltinError("json.filter: " + e.getMessage());
    }
  }

  @OpaBuiltin(
          name = "json.remove",
          description = "Removes object keys by the given list of JSON Pointers.",
          categories = {"encoding"},
          args = {
                  @OpaType(name = "object", description = "object to remove paths from"),
                  @OpaType(name = "paths", description = "JSON string paths")
          },
          result =
          @OpaType(
                  name = "output",
                  description = "remaining data from `object` with keys specified in `paths` removed"))
  public RegoValue remove(EvaluationContext ctx, RegoValue[] args) {
    RegoObject object = getArg(args, 0, RegoObject.class);
    RegoCollection pathsArg = getArg(args, 1, RegoCollection.class);

    pathsArg
            .valueStream()
            .filter(p -> !(p instanceof RegoString) && !(p instanceof RegoArray))
            .findFirst()
            .ifPresent(
                    p -> {
                      throw new TypeError(
                              "operand 2 must be one of {set, array} containing string paths or array of path segments but got "
                                      + p.getTypeName());
                    });

    try {
      // Convert object to JsonNode
      String json = JSON_MAPPER.writeValueAsString(object);
      JsonNode node = JSON_MAPPER.readTree(json);

      // Extract paths from set or array
      Set<String> paths = extractPaths(pathsArg, ctx.sortSets);

      // Remove the paths
      JsonNode result = removeNodes(node, paths);

      // Convert back to RegoValue
      Object resultObj = JSON_MAPPER.treeToValue(result, Object.class);
      return convertToRegoValue(resultObj);
    } catch (Exception e) {
      throw new BuiltinError("json.remove: " + e.getMessage());
    }
  }

  @OpaBuiltin(
          name = "json.patch",
          description = "Patches object according to RFC 6902 JSON Patch standard.",
          categories = {"encoding"},
          args = {
                  @OpaType(name = "object", description = "object to apply patches to"),
                  @OpaType(name = "patches", description = "array of JSON patch objects")
          },
          result = @OpaType(name = "output", description = "patched object"))
  public RegoValue patch(EvaluationContext ctx, RegoValue[] args) {
    RegoValue object = args[0];
    RegoValue patches = args[1];

    try {
      // Convert to JsonNode
      String objectJson = JSON_MAPPER.writeValueAsString(object);
      if (objectJson == null) {
        throw new BuiltinError("json.patch: failed to serialize object");
      }
      JsonNode objectNode = JSON_MAPPER.readTree(objectJson);

      String patchesJson = JSON_MAPPER.writeValueAsString(patches);
      if (patchesJson == null) {
        throw new BuiltinError("json.patch: failed to serialize patches");
      }
      JsonNode patchesNode = JSON_MAPPER.readTree(patchesJson);

      // Normalize paths - ensure they start with "/" and convert array paths to strings
      // Also resolve array value lookups to indices
      if (patchesNode.isArray()) {
        ArrayNode normalizedPatches = JSON_MAPPER.createArrayNode();
        for (JsonNode patchOp : patchesNode) {
          if (patchOp.isObject()) {
            ObjectNode normalizedOp = patchOp.deepCopy();

            // Verify required fields exist - if not, return undefined (OPA behavior)
            if (!normalizedOp.has("op") || !normalizedOp.has("path")) {
              return RegoUndefined.INSTANCE;
            }

            // Check if path or op are null
            if (normalizedOp.get("op").isNull() || normalizedOp.get("path").isNull()) {
              return RegoUndefined.INSTANCE;
            }

            // For add/replace/test operations, verify "value" field exists
            String op = normalizedOp.get("op").asText();
            if (("add".equals(op) || "replace".equals(op) || "test".equals(op))
                    && !normalizedOp.has("value")) {
              return RegoUndefined.INSTANCE;
            }

            // For copy/move operations, verify "from" field exists and is not null
            if (("copy".equals(op) || "move".equals(op))) {
              if (!normalizedOp.has("from") || normalizedOp.get("from").isNull()) {
                return RegoUndefined.INSTANCE;
              }
            }

            // Normalize "path" field
            JsonNode pathNode = normalizedOp.get("path");
            if (pathNode != null) {
              // Validate the original path BEFORE normalization
              String originalPath = normalizeJsonPointerPath(pathNode);
              if (!isValidJsonPointerPath(originalPath)) {
                return RegoUndefined.INSTANCE;
              }

              String normalizedPath = normalizeAndResolveJsonPointerPath(pathNode, objectNode);
              normalizedOp.put("path", normalizedPath);
            }

            // Also normalize "from" field for move/copy operations
            JsonNode fromNode = normalizedOp.get("from");
            if (fromNode != null) {
              // Validate the original path BEFORE normalization
              String originalFrom = normalizeJsonPointerPath(fromNode);
              if (!isValidJsonPointerPath(originalFrom)) {
                return RegoUndefined.INSTANCE;
              }

              String normalizedFrom = normalizeAndResolveJsonPointerPath(fromNode, objectNode);
              normalizedOp.put("from", normalizedFrom);
            }

            normalizedPatches.add(normalizedOp);
          } else {
            normalizedPatches.add(patchOp);
          }
        }
        patchesNode = normalizedPatches;
      }

      // Apply the patch
      JsonPatch jsonPatch;
      try {
        jsonPatch = JsonPatch.fromJson(patchesNode);
      } catch (InvalidTypeIdException e) {
        // Invalid operation type (not one of: add, copy, move, remove, replace, test)
        // Return undefined (OPA behavior)
        return RegoUndefined.INSTANCE;
      }

      if (jsonPatch == null) {
        throw new BuiltinError("json.patch: failed to create JsonPatch");
      }

      JsonNode patched;
      try {
        patched = jsonPatch.apply(objectNode);
      } catch (JsonPatchException e) {
        // Return undefined on patch failure (OPA behavior)
        // This happens when the patch operation is invalid (e.g., removing non-existent path)
        return RegoUndefined.INSTANCE;
      }

      // Convert back to RegoValue
      Object result = JSON_MAPPER.treeToValue(patched, Object.class);
      return convertToRegoValue(result);
    } catch (BuiltinError e) {
      throw e;
    } catch (Exception e) {
      String message = e.getMessage();
      if (message == null || message.isEmpty()) {
        message = e.getClass().getSimpleName();
        if (e.getCause() != null && e.getCause().getMessage() != null) {
          message += ": " + e.getCause().getMessage();
        }
      }
      throw new BuiltinError("json.patch: " + message);
    }
  }

  /**
   * Validates a JSON Pointer path according to RFC 6901. Specifically checks for invalid array
   * indices with leading zeros.
   */
  private boolean isValidJsonPointerPath(String path) {
    if (path == null || path.isEmpty()) {
      return true; // Empty path is valid (refers to whole document)
    }

    if (!path.startsWith("/")) {
      return false; // Must start with /
    }

    // Split and check each segment
    String[] segments = path.substring(1).split("/", -1); // -1 to preserve trailing empty strings
    for (String segment : segments) {
      // Check for leading zeros in numeric segments
      // Per RFC 6901, array indices must not have leading zeros (except "0" itself)
      if (segment.matches("^0\\d+$")) {
        // Has leading zero followed by more digits (like "00", "01", "007")
        return false;
      }
    }

    return true;
  }

  /**
   * Normalizes a JSON Pointer path and resolves array value lookups to indices. OPA's json.patch
   * allows referencing array elements by their value, not just index.
   */
  private String normalizeAndResolveJsonPointerPath(JsonNode pathNode, JsonNode document) {
    // First normalize the path
    String normalizedPath = normalizeJsonPointerPath(pathNode);

    // Handle empty path (refers to whole document)
    if (normalizedPath.isEmpty()) {
      return "";
    }

    // Handle root-only path
    if (normalizedPath.equals("/")) {
      return "/";
    }

    // Parse the path and resolve array value lookups
    String[] segments =
            normalizedPath
                    .substring(1)
                    .split("/", -1); // Skip leading /, -1 to preserve trailing empty strings
    JsonNode current = document;
    StringBuilder resolvedPath = new StringBuilder();

    for (String segment : segments) {
      // Unescape segment per RFC 6901
      String unescaped = segment.replace("~1", "/").replace("~0", "~");

      if (current != null && current.isArray()) {
        // Try to parse as index first
        try {
          int index = Integer.parseInt(unescaped);
          resolvedPath.append("/").append(index);
          if (index >= 0 && index < current.size()) {
            current = current.get(index);
          } else {
            current = null;
          }
        } catch (NumberFormatException e) {
          // Not a number - search for value in array
          boolean found = false;
          for (int i = 0; i < current.size(); i++) {
            JsonNode element = current.get(i);
            if (element.isTextual() && element.asText().equals(unescaped)) {
              resolvedPath.append("/").append(i);
              current = element;
              found = true;
              break;
            }
          }
          if (!found) {
            // Value not found, keep original segment
            resolvedPath.append("/").append(segment);
            current = null;
          }
        }
      } else if (current != null && current.isObject()) {
        resolvedPath.append("/").append(segment);
        current = current.get(unescaped);
      } else {
        resolvedPath.append("/").append(segment);
        current = null;
      }
    }

    return resolvedPath.toString();
  }

  /**
   * Normalizes a JSON Pointer path from either string or array format. Ensures the path starts with
   * "/" and converts array paths to string format.
   */
  private String normalizeJsonPointerPath(JsonNode pathNode) {
    if (pathNode.isTextual()) {
      String path = pathNode.asText();
      if (path.isEmpty() || path.equals("/")) {
        return path;
      }
      return path.startsWith("/") ? path : "/" + path;
    } else if (pathNode.isArray()) {
      // Convert array of path segments to JSON Pointer string
      StringBuilder sb = new StringBuilder();
      for (JsonNode segment : pathNode) {
        sb.append("/");
        if (segment.isTextual()) {
          String segmentStr = segment.asText();
          // Escape special characters per RFC 6901
          segmentStr = segmentStr.replace("~", "~0").replace("/", "~1");
          sb.append(segmentStr);
        } else {
          // For numeric indices, just append as-is
          sb.append(segment.asText());
        }
      }
      return sb.toString();
    } else {
      // Fallback: convert to string
      return "/" + pathNode.asText();
    }
  }

  @OpaBuiltin(
          name = "json.match_schema",
          description = "Verifies the input matches the provided JSON schema.",
          categories = {"encoding"},
          args = {
                  @OpaType(name = "document", description = "document to verify by schema"),
                  @OpaType(name = "schema", description = "schema to verify document by")
          },
          result =
          @OpaType(
                  name = "output",
                  description =
                          "`output` is of the form `[match, errors]`. If the document is valid given the"
                                  + " schema, then `match` is `true`, and `errors` is an empty array."
                                  + " Otherwise, `match` is `false` and `errors` is an array of objects"
                                  + " describing the error(s)."))
  public RegoValue match_schema(EvaluationContext ctx, RegoValue[] args) {
    RegoValue document = args[0];
    RegoValue schemaInput = args[1];

    try {
      // Convert document and schema to JsonNode
      JsonNode documentNode;
      if (document instanceof RegoString) {
        documentNode = JSON_MAPPER.readTree(((RegoString) document).getValue());
      } else {
        String json = JSON_MAPPER.writeValueAsString(document);
        documentNode = JSON_MAPPER.readTree(json);
      }

      JsonNode schemaNode;
      if (schemaInput instanceof RegoString) {
        schemaNode = JSON_MAPPER.readTree(((RegoString) schemaInput).getValue());
      } else {
        String schemaJson = JSON_MAPPER.writeValueAsString(schemaInput);
        schemaNode = JSON_MAPPER.readTree(schemaJson);
      }

      // Validate
      JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);
      JsonSchema schema = factory.getSchema(schemaNode);
      Set<ValidationMessage> errors = schema.validate(documentNode);

      // Build result array
      RegoArray result = new RegoArray();
      if (errors.isEmpty()) {
        result.addValue(RegoBoolean.TRUE);
        result.addValue(new RegoArray());
      } else {
        result.addValue(RegoBoolean.FALSE);
        RegoArray errorArray = new RegoArray();
        for (ValidationMessage error : errors) {
          RegoObject errorObj = new RegoObject();
          errorObj.setProp(new RegoString("message"), new RegoString(error.getMessage()));
          errorObj.setProp(
                  new RegoString("path"), new RegoString(error.getEvaluationPath().toString()));
          errorObj.setProp(new RegoString("type"), new RegoString(error.getType()));
          errorArray.addValue(errorObj);
        }
        result.addValue(errorArray);
      }

      return result;
    } catch (Exception e) {
      throw new BuiltinError("json.match_schema: " + e.getMessage());
    }
  }

  @OpaBuiltin(
          name = "json.verify_schema",
          description = "Verifies the input is a valid JSON schema.",
          categories = {"encoding"},
          args = {@OpaType(name = "schema", description = "schema to verify")},
          result =
          @OpaType(
                  name = "result",
                  description =
                          "`result` is of the form `[valid, error]`. If the schema is valid, then `valid`"
                                  + " is `true`, and `error` is an empty string. Otherwise, `valid` is `false`"
                                  + " and `error` is a string describing the error."))
  public RegoValue verify_schema(EvaluationContext ctx, RegoValue[] args) {
    RegoValue schemaInput = args[0];

    try {
      // Convert schema to JsonNode
      JsonNode schemaNode;
      if (schemaInput instanceof RegoString) {
        schemaNode = JSON_MAPPER.readTree(((RegoString) schemaInput).getValue());
      } else {
        String schemaJson = JSON_MAPPER.writeValueAsString(schemaInput);
        schemaNode = JSON_MAPPER.readTree(schemaJson);
      }

      // Try to create a schema - if it succeeds, it's valid
      JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);
      factory.getSchema(schemaNode);

      // Build success result
      RegoArray result = new RegoArray();
      result.addValue(RegoBoolean.TRUE);
      result.addValue(RegoNull.INSTANCE);

      return result;
    } catch (Exception e) {
      // Build error result
      RegoArray result = new RegoArray();
      result.addValue(RegoBoolean.FALSE);
      result.addValue(new RegoString(e.getMessage()));
      return result;
    }
  }

  @OpaBuiltin(
          name = "yaml.marshal",
          description = "Serializes the input term to YAML.",
          categories = {"encoding"},
          args = {@OpaType(type = "any", name = "x", description = "the term to serialize")},
          result =
          @OpaType(
                  type = "string",
                  name = "y",
                  description = "the YAML string representation of `x`"))
  public RegoString yamlMarshal(EvaluationContext ctx, RegoValue[] args) {
    RegoValue input = args[0];

    try {
      String yaml = YAML_MAPPER.writeValueAsString(input);
      return new RegoString(yaml);
    } catch (JsonProcessingException e) {
      throw new BuiltinError("yaml.marshal: " + e.getMessage());
    }
  }

  @OpaBuiltin(
          name = "yaml.unmarshal",
          description = "Deserializes the input YAML string.",
          categories = {"encoding"},
          args = {@OpaType(type = "string", name = "x", description = "a YAML string")},
          result = @OpaType(type = "any", name = "y", description = "the term deserialized from `x`"))
  public RegoValue yamlUnmarshal(EvaluationContext ctx, RegoValue[] args) {
    String yamlInput = getArg(args, 0, RegoString.class).getValue();

    try {
      Object parsed = YAML_MAPPER.readValue(yamlInput, Object.class);
      return convertToRegoValueFromYaml(parsed);
    } catch (IOException e) {
      throw new BuiltinError("yaml.unmarshal: " + e.getMessage());
    }
  }

  /**
   * Converts a Java object (from Jackson YAML deserialization) to a RegoValue. In YAML, empty
   * values are treated as null, so we convert empty strings to RegoNull.
   */
  private RegoValue convertToRegoValueFromYaml(Object obj) {
    return convertToRegoValue(obj, true);
  }

  /** Converts a Java object (from Jackson deserialization) to a RegoValue. */
  private RegoValue convertToRegoValue(Object obj) {
    return convertToRegoValue(obj, false);
  }

  /**
   * Converts a Java object (from Jackson deserialization) to a RegoValue.
   *
   * @param obj the object to convert
   * @param yamlMode if true, apply YAML-specific null handling for strings
   * @return the converted RegoValue
   */
  private RegoValue convertToRegoValue(Object obj, boolean yamlMode) {
    if (obj == null) {
      return RegoNull.INSTANCE;
    } else if (obj instanceof String) {
      return convertStringToRegoValue((String) obj, yamlMode);
    } else if (obj instanceof Boolean) {
      return RegoBoolean.of((Boolean) obj);
    } else if (obj instanceof Integer) {
      return RegoInt32.of((Integer) obj);
    } else if (obj instanceof Long) {
      return new RegoBigInt((Long) obj);
    } else if (obj instanceof Double || obj instanceof Float) {
      return new RegoDecimal(((Number) obj).doubleValue());
    } else if (obj instanceof java.util.List) {
      RegoArray array = new RegoArray();
      for (Object item : (java.util.List<?>) obj) {
        array.addValue(convertToRegoValue(item, yamlMode));
      }
      return array;
    } else if (obj instanceof java.util.Map) {
      RegoObject regoObj = new RegoObject();
      for (java.util.Map.Entry<?, ?> entry : ((java.util.Map<?, ?>) obj).entrySet()) {
        String key = entry.getKey().toString();
        regoObj.setProp(new RegoString(key), convertToRegoValue(entry.getValue(), yamlMode));
      }
      return regoObj;
    } else {
      throw new BuiltinError("unsupported type: " + obj.getClass().getName());
    }
  }

  /**
   * Converts a string to a RegoValue with optional YAML-specific null handling.
   *
   * @param str the string to convert
   * @param yamlMode if true, treat empty strings and YAML null literals as RegoNull
   * @return RegoNull for YAML nulls, or RegoString otherwise
   */
  private RegoValue convertStringToRegoValue(String str, boolean yamlMode) {
    if (yamlMode) {
      // In YAML, empty strings (unquoted empty scalars) should be treated as null
      // Only truly quoted empty strings ("") should remain as empty strings
      // However, Jackson doesn't distinguish, so we treat all empty strings as null
      if (str.isEmpty()) {
        return RegoNull.INSTANCE;
      }
      // Also check for YAML null representations
      if ("null".equals(str) || "Null".equals(str) || "NULL".equals(str) || "~".equals(str)) {
        return RegoNull.INSTANCE;
      }
    }
    return new RegoString(str);
  }

  /** Extracts paths from a RegoArray or RegoSet. */
  private Set<String> extractPaths(RegoValue pathsArg, boolean sortedSets) {
    Set<String> paths = sortedSets ? new LinkedHashSet<>() : new HashSet<>();

    if (pathsArg instanceof RegoSet) {
      RegoSet pathsSet = (RegoSet) pathsArg;
      for (RegoValue path : pathsSet.getValue()) {
        if (path instanceof RegoString) {
          paths.add(((RegoString) path).getValue());
        } else if (path instanceof RegoArray) {
          // Path can be an array of path segments
          RegoArray pathArray = (RegoArray) path;
          StringBuilder sb = new StringBuilder();
          for (int i = 0; i < pathArray.length(); i++) {
            RegoValue segment = pathArray.getValues().get(i);
            if (segment instanceof RegoString) {
              if (sb.length() > 0) {
                sb.append("/");
              }
              sb.append(((RegoString) segment).getValue());
            }
          }
          paths.add(sb.toString());
        }
      }
    } else if (pathsArg instanceof RegoArray) {
      RegoArray pathsArray = (RegoArray) pathsArg;
      for (int i = 0; i < pathsArray.length(); i++) {
        RegoValue path = pathsArray.getValues().get(i);
        if (path instanceof RegoString) {
          paths.add(((RegoString) path).getValue());
        } else if (path instanceof RegoArray) {
          RegoArray pathArray = (RegoArray) path;
          StringBuilder sb = new StringBuilder();
          for (int j = 0; j < pathArray.length(); j++) {
            RegoValue segment = pathArray.getValues().get(j);
            if (segment instanceof RegoString) {
              if (sb.length() > 0) {
                sb.append("/");
              }
              sb.append(((RegoString) segment).getValue());
            }
          }
          paths.add(sb.toString());
        }
      }
    }

    return paths;
  }

  /** Filters a JsonNode to only include specified paths. */
  private JsonNode filterNode(JsonNode node, Set<String> paths) {
    if (node.isObject()) {
      com.fasterxml.jackson.databind.node.ObjectNode result = JSON_MAPPER.createObjectNode();

      // Track array index mappings: key is the result array node, value is map of source->result
      // indices
      Map<JsonNode, Map<Integer, Integer>> arrayIndexMaps = new HashMap<>();

      for (String path : paths) {
        if (path.isEmpty()) continue;

        String[] parts = path.split("/");
        List<String> nonEmptyParts = new ArrayList<>();
        for (String part : parts) {
          if (!part.isEmpty()) {
            nonEmptyParts.add(part);
          }
        }

        if (nonEmptyParts.isEmpty()) continue;

        // Navigate to the value in the source, tracking structure types
        JsonNode current = node;
        List<Boolean> isArrayAtLevel = new ArrayList<>();

        for (String part : nonEmptyParts) {
          if (current == null) break;

          if (current.isArray()) {
            isArrayAtLevel.add(true);
            try {
              int index = Integer.parseInt(part);
              if (index >= 0 && index < current.size()) {
                current = current.get(index);
              } else {
                current = null;
              }
            } catch (NumberFormatException e) {
              current = null;
            }
          } else if (current.isObject()) {
            isArrayAtLevel.add(false);
            current = current.get(part);
          } else {
            current = null;
          }
        }

        if (current == null) continue;

        // Build the path in the result using the tracked structure types
        JsonNode resultNode = result;

        for (int i = 0; i < nonEmptyParts.size(); i++) {
          String part = nonEmptyParts.get(i);
          boolean isLast = (i == nonEmptyParts.size() - 1);

          if (isLast) {
            // Last part - set the value
            if (resultNode.isObject()) {
              ((ObjectNode) resultNode).set(part, current.deepCopy());
            } else if (resultNode.isArray()) {
              // For array parent at leaf, use index mapping to avoid null padding
              ArrayNode arrayNode = (ArrayNode) resultNode;
              try {
                int sourceIndex = Integer.parseInt(part);

                // Get or create index mapping for this array
                Map<Integer, Integer> indexMap =
                        arrayIndexMaps.computeIfAbsent(arrayNode, k -> new HashMap<>());

                if (!indexMap.containsKey(sourceIndex)) {
                  // Map this source index to the next available result index
                  int resultIndex = arrayNode.size();
                  indexMap.put(sourceIndex, resultIndex);
                  arrayNode.add(current.deepCopy());
                }
                // If we've already added a value for this source index, we might have a conflict
                // In that case, the first value wins (OPA behavior)
              } catch (NumberFormatException e) {
                // Not a valid array index - just append
                arrayNode.add(current.deepCopy());
              }
            }
          } else {
            // Intermediate part - navigate or create using tracked types
            boolean nextIsArray = (i + 1 < isArrayAtLevel.size()) && isArrayAtLevel.get(i + 1);

            if (resultNode.isObject()) {
              com.fasterxml.jackson.databind.node.ObjectNode objNode =
                      (com.fasterxml.jackson.databind.node.ObjectNode) resultNode;
              if (!objNode.has(part)) {
                if (nextIsArray) {
                  objNode.set(part, JSON_MAPPER.createArrayNode());
                } else {
                  objNode.set(part, JSON_MAPPER.createObjectNode());
                }
              }
              resultNode = objNode.get(part);
            } else if (resultNode.isArray()) {
              ArrayNode arrayNode = (ArrayNode) resultNode;

              try {
                int sourceIndex = Integer.parseInt(part);

                // Get or create index mapping for this array
                Map<Integer, Integer> indexMap =
                        arrayIndexMaps.computeIfAbsent(arrayNode, k -> new HashMap<>());

                int resultIndex;
                if (indexMap.containsKey(sourceIndex)) {
                  // We've already created an element for this source index
                  resultIndex = indexMap.get(sourceIndex);
                } else {
                  // Create a new element for this source index
                  resultIndex = arrayNode.size();
                  indexMap.put(sourceIndex, resultIndex);

                  if (nextIsArray) {
                    arrayNode.add(JSON_MAPPER.createArrayNode());
                  } else {
                    arrayNode.add(JSON_MAPPER.createObjectNode());
                  }
                }

                resultNode = arrayNode.get(resultIndex);
              } catch (NumberFormatException e) {
                // Not a valid index
                break;
              }
            }
          }
        }
      }
      return result;
    } else if (node.isArray()) {
      ArrayNode result = JSON_MAPPER.createArrayNode();
      // For arrays at root, filter by index
      for (String path : paths) {
        try {
          int index = Integer.parseInt(path);
          if (index >= 0 && index < node.size()) {
            result.add(node.get(index).deepCopy());
          }
        } catch (NumberFormatException e) {
          // Skip invalid indices
        }
      }
      return result;
    }
    return node;
  }


  /** Removes specified paths from a JsonNode. */
  private JsonNode removeNodes(JsonNode node, Set<String> paths) {
    JsonNode result = node.deepCopy();

    for (String path : paths) {
      String[] parts = path.split("/");
      JsonNode current = result;
      String lastPart = null;
      JsonNode parent = null;

      for (int i = 0; i < parts.length; i++) {
        String part = parts[i];
        if (part.isEmpty()) continue;

        if (i == parts.length - 1) {
          lastPart = part;
          parent = current;
        } else {
          if (current != null) {
            if (current.isArray()) {
              try {
                int index = Integer.parseInt(part);
                if (index >= 0 && index < current.size()) {
                  current = current.get(index);
                } else {
                  current = null;
                }
              } catch (NumberFormatException e) {
                current = null;
              }
            } else if (current.isObject() && current.has(part)) {
              current = current.get(part);
            } else {
              current = null;
            }
          }
        }
      }

      // Remove the last part
      if (parent != null && lastPart != null) {
        if (parent.isObject() && parent.has(lastPart)) {
          ((ObjectNode) parent).remove(lastPart);
        } else if (parent.isArray()) {
          try {
            int index = Integer.parseInt(lastPart);
            if (index >= 0 && index < parent.size()) {
              ((ArrayNode) parent).remove(index);
            }
          } catch (NumberFormatException e) {
            // Not a valid array index
          }
        }
      }
    }

    return result;
  }
}