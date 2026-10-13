package io.github.open_policy_agent.opa.ast.builtin.impls;

import static io.github.open_policy_agent.opa.ast.builtin.impls.utils.ArgHelper.getArg;

import com.networknt.schema.Error;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import org.snakeyaml.engine.v2.exceptions.MarkedYamlEngineException;
import io.github.open_policy_agent.opa.ast.types.RegoArray;
import io.github.open_policy_agent.opa.ast.types.RegoBigInt;
import io.github.open_policy_agent.opa.ast.types.RegoBoolean;
import io.github.open_policy_agent.opa.ast.types.RegoCollection;
import io.github.open_policy_agent.opa.ast.types.RegoDecimal;
import io.github.open_policy_agent.opa.ast.types.RegoInt32;
import io.github.open_policy_agent.opa.ast.types.RegoNull;
import io.github.open_policy_agent.opa.ast.types.RegoObject;
import io.github.open_policy_agent.opa.ast.types.RegoSet;
import io.github.open_policy_agent.opa.ast.types.RegoString;
import io.github.open_policy_agent.opa.ast.types.RegoUndefined;
import io.github.open_policy_agent.opa.ast.types.RegoValue;
import io.github.open_policy_agent.opa.ast.types.TypeUtils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import io.github.open_policy_agent.opa.ast.builtin.BuiltinError;
import io.github.open_policy_agent.opa.ast.builtin.BuiltinProvider;
import io.github.open_policy_agent.opa.ast.builtin.OpaBuiltin;
import io.github.open_policy_agent.opa.ast.builtin.OpaType;
import io.github.open_policy_agent.opa.rego.EvaluationContext;
import io.github.open_policy_agent.opa.rego.TypeError;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.core.util.DefaultIndenter;
import tools.jackson.core.util.DefaultPrettyPrinter;
import tools.jackson.core.util.Separators;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ObjectWriter;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.dataformat.yaml.YAMLMapper;
import tools.jackson.dataformat.yaml.YAMLWriteFeature;

public class JsonBuiltins implements BuiltinProvider {

  private static final ObjectMapper JSON_MAPPER;
  private static final ObjectMapper YAML_MAPPER;

  // Shared serializers for Rego numeric types
  private static final ValueSerializer<RegoDecimal> REGO_DECIMAL_SERIALIZER =
      new ValueSerializer<>() {
        @Override
        public void serialize(RegoDecimal value, JsonGenerator gen, SerializationContext ctx) {
          Double d = value.getValue();
          // If the double is a whole number that fits in a long, serialize as integer
          if (d != null && TypeUtils.isWholeNumberInLongRange(d)) {
            gen.writeNumber(d.longValue());
          } else {
            gen.writeNumber(d);
          }
        }
      };

  private static final ValueSerializer<RegoBigInt> REGO_BIG_INT_SERIALIZER =
      new ValueSerializer<>() {
        @Override
        public void serialize(RegoBigInt value, JsonGenerator gen, SerializationContext ctx) {
          gen.writeNumber(value.getValue());
        }
      };

  private static final ValueSerializer<RegoInt32> REGO_INT32_SERIALIZER =
      new ValueSerializer<>() {
        @Override
        public void serialize(RegoInt32 value, JsonGenerator gen, SerializationContext ctx) {
          gen.writeNumber(value.getValue());
        }
      };

  private static final ValueSerializer<RegoNull> REGO_NULL_SERIALIZER =
      new ValueSerializer<>() {
        @Override
        public void serialize(RegoNull value, JsonGenerator gen, SerializationContext ctx) {
          gen.writeNull();
        }
      };

  static {
    SimpleModule module = new SimpleModule();
    module.addSerializer(RegoDecimal.class, REGO_DECIMAL_SERIALIZER);
    module.addSerializer(RegoBigInt.class, REGO_BIG_INT_SERIALIZER);
    module.addSerializer(RegoInt32.class, REGO_INT32_SERIALIZER);

    // findAndAddModules picks up RegoValueModule (from opa-jackson) via SPI so
    // RegoString/RegoArray/RegoObject etc. (de)serialize without annotations on the AST types.
    JSON_MAPPER = JsonMapper.builder().findAndAddModules().addModule(module).build();

    // YAML mapper needs all serializers including null handling
    SimpleModule yamlModule = new SimpleModule();
    yamlModule.addSerializer(RegoDecimal.class, REGO_DECIMAL_SERIALIZER);
    yamlModule.addSerializer(RegoBigInt.class, REGO_BIG_INT_SERIALIZER);
    yamlModule.addSerializer(RegoInt32.class, REGO_INT32_SERIALIZER);
    yamlModule.addSerializer(RegoNull.class, REGO_NULL_SERIALIZER);

    YAML_MAPPER =
        YAMLMapper.builder()
            .disable(YAMLWriteFeature.WRITE_DOC_START_MARKER)
            .findAndAddModules()
            .addModule(yamlModule)
            .build();
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
    result.put("yaml.is_valid", instance::yamlIsValid);
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
    } catch (JacksonException e) {
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

    // Go's implementation rejects unknown keys before looking at any of them.
    for (RegoValue key : options.getProperties().keySet()) {
      String name = key instanceof RegoString ? ((RegoString) key).getValue() : String.valueOf(key);
      if (!MARSHAL_OPTION_KEYS.contains(name)) {
        throw new TypeError("object contained unknown key \"" + name + "\"");
      }
    }

    RegoValue prettyValue = options.getProperty(new RegoString("pretty"));
    RegoValue indentValue = options.getProperty(new RegoString("indent"));
    RegoValue prefixValue = options.getProperty(new RegoString("prefix"));

    String indent = indentValue instanceof RegoString ? ((RegoString) indentValue).getValue() : "\t";
    String prefix = prefixValue instanceof RegoString ? ((RegoString) prefixValue).getValue() : "";

    // "pretty" is only a default: supplying "indent" or "prefix" implies pretty output, but an
    // explicit "pretty": false disables it and makes the other two options inert.
    boolean pretty = indentValue != null || prefixValue != null;
    if (prettyValue instanceof RegoBoolean) {
      pretty = ((RegoBoolean) prettyValue).getValue();
    }

    try {
      if (!pretty) {
        return new RegoString(JSON_MAPPER.writeValueAsString(input));
      }

      // Go composes the result as prefix + json.MarshalIndent(v, prefix, indent), so the prefix
      // leads the document and is repeated at the start of every subsequent line.
      ObjectWriter writer = JSON_MAPPER.writer().with(new GoPrettyPrinter(prefix, indent));

      return new RegoString(prefix + writer.writeValueAsString(input));
    } catch (JacksonException e) {
      throw new BuiltinError("json.marshal_with_options: " + e.getMessage());
    }
  }

  private static final Set<String> MARSHAL_OPTION_KEYS = Set.of("pretty", "indent", "prefix");

  /**
   * Reproduces Go's json.MarshalIndent layout: newline-separated, each line indented by the
   * prefix followed by the indent repeated per nesting level, and `": "` between key and value.
   * Jackson's DefaultPrettyPrinter otherwise emits spaces around the colon and no line prefix.
   */
  private static final class GoPrettyPrinter extends DefaultPrettyPrinter {
    private static final long serialVersionUID = 1L;

    GoPrettyPrinter(String prefix, String indent) {
      super(Separators.createDefaultInstance().withObjectNameValueSpacing(Separators.Spacing.AFTER));
      DefaultIndenter indenter = new DefaultIndenter(indent, DefaultIndenter.SYS_LF + prefix);
      indentArraysWith(indenter);
      indentObjectsWith(indenter);
    }

    private GoPrettyPrinter(GoPrettyPrinter base) {
      super(base);
    }

    @Override
    public DefaultPrettyPrinter createInstance() {
      return new GoPrettyPrinter(this);
    }

    @Override
    public void writeEndObject(JsonGenerator g, int nrOfEntries) {
      // Go emits {} for an empty object rather than opening a new indented line. Track the
      // nesting level down as super would, or the enclosing containers close over-indented.
      if (nrOfEntries == 0) {
        --_nesting;
        g.writeRaw('}');
        return;
      }
      super.writeEndObject(g, nrOfEntries);
    }

    @Override
    public void writeEndArray(JsonGenerator g, int nrOfValues) {
      if (nrOfValues == 0) {
        --_nesting;
        g.writeRaw(']');
        return;
      }
      super.writeEndArray(g, nrOfValues);
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
    } catch (JacksonException e) {
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
    // Go returns false for a non-string operand rather than raising a type error.
    if (!(args[0] instanceof RegoString)) {
      return RegoBoolean.FALSE;
    }
    String jsonInput = ((RegoString) args[0]).getValue();

    try {
      JSON_MAPPER.readTree(jsonInput);
      return RegoBoolean.TRUE;
    } catch (JacksonException e) {
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

  /**
   * Records the JSON Pointer of every set inside `value`. Sets serialize as arrays, so json.patch
   * has to remember where they were: paths into a set address members by value rather than index,
   * and the patched result has to be turned back into a set.
   */
  private static void collectSetPaths(RegoValue value, String pointer, Set<String> out) {
    if (value instanceof RegoSet) {
      out.add(pointer);
      int i = 0;
      for (RegoValue member : ((RegoSet) value).getValue()) {
        collectSetPaths(member, pointer + "/" + i++, out);
      }
    } else if (value instanceof RegoArray) {
      List<RegoValue> values = ((RegoArray) value).getValue();
      for (int i = 0; i < values.size(); i++) {
        collectSetPaths(values.get(i), pointer + "/" + i, out);
      }
    } else if (value instanceof RegoObject) {
      for (Map.Entry<RegoValue, RegoValue> e : ((RegoObject) value).getProperties().entrySet()) {
        if (e.getKey() instanceof RegoString) {
          String key = ((RegoString) e.getKey()).getValue();
          collectSetPaths(e.getValue(), pointer + "/" + escapePointerSegment(key), out);
        }
      }
    }
  }

  private static String escapePointerSegment(String segment) {
    return segment.replace("~", "~0").replace("/", "~1");
  }

  /**
   * Reads a path segment as an array index, returning null when it is not one. Digits that
   * overflow an int are not a usable index either, so they are rejected rather than allowed to
   * throw out of the builtin.
   */
  private static Integer asArrayIndex(JsonNode segment) {
    if (segment.isIntegralNumber()) {
      return segment.canConvertToInt() ? Integer.valueOf(segment.asInt()) : null;
    }
    if (segment.isTextual() && segment.asText().matches("-?\\d+")) {
      try {
        return Integer.valueOf(segment.asText());
      } catch (NumberFormatException e) {
        return null;
      }
    }
    return null;
  }

  /**
   * Splits a patch path into segments. A path given as an array can carry non-string segments —
   * a set member is addressed by its value, which may itself be an array or object — so segments
   * stay as nodes rather than being flattened to a string up front.
   */
  private List<JsonNode> pathSegments(JsonNode pathNode) {
    List<JsonNode> segments = new ArrayList<>();
    if (pathNode.isArray()) {
      for (JsonNode segment : pathNode) {
        segments.add(segment);
      }
      return segments;
    }

    String path = pathNode.asString("");
    if (path.startsWith("/")) {
      path = path.substring(1);
    }
    if (path.isEmpty()) {
      return segments;
    }
    for (String raw : path.split("/", -1)) {
      segments.add(JSON_MAPPER.getNodeFactory().textNode(raw.replace("~1", "/").replace("~0", "~")));
    }
    return segments;
  }

  /**
   * Resolves a patch path against the document, turning value lookups into indices. Arrays already
   * allow addressing an element by value; a set additionally has no inherent order, so a member is
   * located by deep equality and a member that is not present resolves to the append token.
   */
  private PathResolution resolvePath(JsonNode pathNode, JsonNode document, Set<String> setPaths) {
    // Documents without sets keep the long-standing resolution untouched: set support only needs
    // to change how a member is addressed, and this is by far the hotter path.
    if (setPaths.isEmpty()) {
      return new PathResolution(normalizeAndResolveJsonPointerPath(pathNode, document), null);
    }

    StringBuilder resolved = new StringBuilder();
    String currentPointer = "";
    JsonNode current = document;
    JsonNode appendedMember = null;

    for (JsonNode segment : pathSegments(pathNode)) {
      boolean isSet = setPaths.contains(currentPointer);
      String literal =
              segment.isTextual() ? segment.asText() : segment.toString();

      if (current != null && current.isArray()) {
        int index = -1;
        if (isSet) {
          // A set has no order, so a member is addressed by its value.
          for (int i = 0; i < current.size(); i++) {
            if (current.get(i).equals(segment)) {
              index = i;
              break;
            }
          }
          if (index < 0) {
            // Absent member: for "add" this means append, which RFC 6902 spells "-".
            resolved.append("/-");
            appendedMember = segment;
            current = null;
            currentPointer = null;
            continue;
          }
        } else {
          Integer parsed = asArrayIndex(segment);
          if (parsed == null) {
            // Not an index into a plain array. Keep the segment so the patch library reports it.
            resolved.append('/').append(escapePointerSegment(literal));
            current = null;
            currentPointer = null;
            continue;
          }
          index = parsed;
        }

        resolved.append('/').append(index);
        current = index >= 0 && index < current.size() ? current.get(index) : null;
        currentPointer = currentPointer == null ? null : currentPointer + "/" + index;
      } else if (current != null && current.isObject()) {
        resolved.append('/').append(escapePointerSegment(literal));
        current = current.get(literal);
        currentPointer = currentPointer == null ? null : currentPointer + "/" + escapePointerSegment(literal);
      } else {
        resolved.append('/').append(escapePointerSegment(literal));
        current = null;
        currentPointer = null;
      }
    }

    return new PathResolution(resolved.toString(), appendedMember);
  }

  /**
   * A resolved patch path. `appendedSetMember` is the addressed value when the path appends to a
   * set, which lets `add` check that the value matches: in a set the path segment *is* the member,
   * so `{"op": "add", "path": "foo/d", "value": "e"}` is incoherent and undefined in OPA.
   */
  private static final class PathResolution {
    private final String path;
    private final JsonNode appendedSetMember;

    PathResolution(String path, JsonNode appendedSetMember) {
      this.path = path;
      this.appendedSetMember = appendedSetMember;
    }
  }

  /** Rebuilds a patched document, restoring the sets recorded by {@link #collectSetPaths}. */
  private RegoValue restoreSets(JsonNode node, String pointer, Set<String> setPaths, boolean sorted) {
    if (node.isArray() && setPaths.contains(pointer)) {
      Set<RegoValue> members = sorted ? new LinkedHashSet<>() : new HashSet<>();
      int i = 0;
      for (JsonNode element : node) {
        members.add(restoreSets(element, pointer + "/" + i++, setPaths, sorted));
      }
      return new RegoSet(sorted, members);
    }
    if (node.isArray()) {
      RegoArray array = new RegoArray();
      int i = 0;
      for (JsonNode element : node) {
        array.addValue(restoreSets(element, pointer + "/" + i++, setPaths, sorted));
      }
      return array;
    }
    if (node.isObject()) {
      RegoObject object = new RegoObject();
      for (Map.Entry<String, JsonNode> e : node.properties()) {
        object.setProp(
                new RegoString(e.getKey()),
                restoreSets(
                        e.getValue(),
                        pointer + "/" + escapePointerSegment(e.getKey()),
                        setPaths,
                        sorted));
      }
      return object;
    }
    try {
      return convertToRegoValue(JSON_MAPPER.treeToValue(node, Object.class));
    } catch (JacksonException e) {
      throw new BuiltinError("json.patch: " + e.getMessage());
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

      // Sets serialize as arrays, so remember where they were before the document becomes JSON.
      Set<String> setPaths = new HashSet<>();
      collectSetPaths(object, "", setPaths);

      // Normalize paths - ensure they start with "/" and convert array paths to strings
      // Also resolve array value lookups to indices
      if (patchesNode.isArray()) {
        ArrayNode normalizedPatches = JSON_MAPPER.createArrayNode();
        for (JsonNode patchOp : patchesNode) {
          if (patchOp.isObject()) {
            ObjectNode normalizedOp = (ObjectNode) patchOp.deepCopy();

            // Verify required fields exist - if not, return undefined (OPA behavior)
            if (!normalizedOp.has("op") || !normalizedOp.has("path")) {
              return RegoUndefined.INSTANCE;
            }

            // Check if path or op are null
            if (normalizedOp.get("op").isNull() || normalizedOp.get("path").isNull()) {
              return RegoUndefined.INSTANCE;
            }

            // For add/replace/test operations, verify "value" field exists
            String op = normalizedOp.get("op").asString("");
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

            // Validate op type - return undefined for unknown ops (OPA behavior)
            if (!isValidPatchOp(op)) {
              return RegoUndefined.INSTANCE;
            }

            // Normalize "path" field
            JsonNode pathNode = normalizedOp.get("path");
            if (pathNode != null) {
              // Validate the original path BEFORE normalization
              String originalPath = normalizeJsonPointerPath(pathNode);
              if (!isValidJsonPointerPath(originalPath)) {
                return RegoUndefined.INSTANCE;
              }

              PathResolution resolvedPath = resolvePath(pathNode, objectNode, setPaths);
              // Appending to a set means the segment is the member, so an "add" whose value
              // differs from the segment is incoherent and undefined.
              if (resolvedPath.appendedSetMember != null
                      && "add".equals(op)
                      && !resolvedPath.appendedSetMember.equals(normalizedOp.get("value"))) {
                return RegoUndefined.INSTANCE;
              }
              normalizedOp.put("path", resolvedPath.path);
            }

            // Also normalize "from" field for move/copy operations
            JsonNode fromNode = normalizedOp.get("from");
            if (fromNode != null) {
              // Validate the original path BEFORE normalization
              String originalFrom = normalizeJsonPointerPath(fromNode);
              if (!isValidJsonPointerPath(originalFrom)) {
                return RegoUndefined.INSTANCE;
              }

              normalizedOp.put("from", resolvePath(fromNode, objectNode, setPaths).path);
            }

            normalizedPatches.add(normalizedOp);
          } else {
            normalizedPatches.add(patchOp);
          }
        }
        patchesNode = normalizedPatches;
      }

      // Apply the patch
      JsonNode patched;
      try {
        patched = applyJsonPatch((ArrayNode) patchesNode, objectNode);
      } catch (RuntimeException e) {
        // Return undefined on patch failure (OPA behavior).
        // This happens when the patch operation is invalid (e.g., removing non-existent path,
        // index out of range, or test mismatch).
        return RegoUndefined.INSTANCE;
      }

      // Convert back to RegoValue, restoring any sets the document started with.
      if (!setPaths.isEmpty()) {
        return restoreSets(patched, "", setPaths, ctx.sortSets);
      }
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

  private static boolean isValidPatchOp(String op) {
    return "add".equals(op)
        || "remove".equals(op)
        || "replace".equals(op)
        || "move".equals(op)
        || "copy".equals(op)
        || "test".equals(op);
  }

  // RFC 6902 JSON Patch application. Each op mutates a deep copy of the source; on any failure
  // (missing path, index out of range, test mismatch, unknown op), throws a RuntimeException
  // which json.patch translates to RegoUndefined per OPA's contract.
  private static JsonNode applyJsonPatch(ArrayNode patches, JsonNode source) {
    JsonNode result = source.deepCopy();
    for (JsonNode op : patches) {
      result = applyJsonPatchOp(result, op);
    }
    return result;
  }

  private static JsonNode applyJsonPatchOp(JsonNode root, JsonNode op) {
    String operation = op.get("op").asString();
    List<String> path = parseJsonPointer(op.get("path").asString());
    switch (operation) {
      case "add":
        return performAdd(root, path, op.get("value").deepCopy());
      case "remove":
        return performRemove(root, path);
      case "replace":
        return performReplace(root, path, op.get("value").deepCopy());
      case "test":
        performTest(root, path, op.get("value"));
        return root;
      case "move": {
        List<String> from = parseJsonPointer(op.get("from").asString());
        if (from.size() < path.size() && isPrefix(from, path)) {
          // RFC 6902 §4.4: from MUST NOT be a proper prefix of path. Moving a value onto itself
          // is allowed and leaves the document unchanged.
          throw new IllegalArgumentException("move: from is a prefix of path");
        }
        JsonNode value = readAt(root, from);
        root = performRemove(root, from);
        return performAdd(root, path, value);
      }
      case "copy": {
        List<String> from = parseJsonPointer(op.get("from").asString());
        JsonNode value = readAt(root, from).deepCopy();
        return performAdd(root, path, value);
      }
      default:
        throw new IllegalArgumentException("unknown op: " + operation);
    }
  }

  // RFC 6901 JSON Pointer: "" -> root (empty list); "/foo/0" -> ["foo", "0"];
  // "~1" decodes to "/", "~0" decodes to "~".
  private static List<String> parseJsonPointer(String pointer) {
    if (pointer.isEmpty()) {
      return new ArrayList<>();
    }
    if (!pointer.startsWith("/")) {
      throw new IllegalArgumentException("invalid JSON pointer: " + pointer);
    }
    String[] parts = pointer.substring(1).split("/", -1);
    List<String> tokens = new ArrayList<>(parts.length);
    for (String part : parts) {
      tokens.add(part.replace("~1", "/").replace("~0", "~"));
    }
    return tokens;
  }

  private static JsonNode readAt(JsonNode root, List<String> tokens) {
    JsonNode current = root;
    for (String token : tokens) {
      if (current.isArray()) {
        current = current.get(parseArrayIndex(token, current.size(), false));
      } else if (current.isObject()) {
        if (!current.has(token)) {
          throw new IllegalArgumentException("path does not exist: " + token);
        }
        current = current.get(token);
      } else {
        throw new IllegalArgumentException("cannot navigate into non-container at: " + token);
      }
    }
    return current;
  }

  private static JsonNode performAdd(JsonNode root, List<String> tokens, JsonNode value) {
    if (tokens.isEmpty()) {
      // Replace the whole document.
      return value;
    }
    JsonNode parent = readAt(root, tokens.subList(0, tokens.size() - 1));
    String last = tokens.get(tokens.size() - 1);
    if (parent.isArray()) {
      ArrayNode arr = (ArrayNode) parent;
      int idx = "-".equals(last) ? arr.size() : parseArrayIndex(last, arr.size(), true);
      if (idx == arr.size()) {
        arr.add(value);
      } else {
        arr.insert(idx, value);
      }
    } else if (parent.isObject()) {
      // RFC 6902 §4.1: existing member is replaced; new member is added.
      ((ObjectNode) parent).set(last, value);
    } else {
      throw new IllegalArgumentException("add: parent is not a container");
    }
    return root;
  }

  private static JsonNode performRemove(JsonNode root, List<String> tokens) {
    if (tokens.isEmpty()) {
      throw new IllegalArgumentException("remove: cannot remove root");
    }
    JsonNode parent = readAt(root, tokens.subList(0, tokens.size() - 1));
    String last = tokens.get(tokens.size() - 1);
    if (parent.isArray()) {
      ArrayNode arr = (ArrayNode) parent;
      arr.remove(parseArrayIndex(last, arr.size(), false));
    } else if (parent.isObject()) {
      ObjectNode obj = (ObjectNode) parent;
      if (!obj.has(last)) {
        throw new IllegalArgumentException("remove: path does not exist: " + last);
      }
      obj.remove(last);
    } else {
      throw new IllegalArgumentException("remove: parent is not a container");
    }
    return root;
  }

  private static JsonNode performReplace(JsonNode root, List<String> tokens, JsonNode value) {
    if (tokens.isEmpty()) {
      return value;
    }
    JsonNode parent = readAt(root, tokens.subList(0, tokens.size() - 1));
    String last = tokens.get(tokens.size() - 1);
    if (parent.isArray()) {
      ArrayNode arr = (ArrayNode) parent;
      arr.set(parseArrayIndex(last, arr.size(), false), value);
    } else if (parent.isObject()) {
      ObjectNode obj = (ObjectNode) parent;
      if (!obj.has(last)) {
        throw new IllegalArgumentException("replace: path does not exist: " + last);
      }
      obj.set(last, value);
    } else {
      throw new IllegalArgumentException("replace: parent is not a container");
    }
    return root;
  }

  private static void performTest(JsonNode root, List<String> tokens, JsonNode expected) {
    JsonNode actual = readAt(root, tokens);
    if (!actual.equals(expected)) {
      throw new IllegalArgumentException("test: value mismatch");
    }
  }

  // For "remove"/"replace"/"read", index must be in [0, size). For "add"/"insert", [0, size]
  // (since size is "append at end").
  private static int parseArrayIndex(String token, int size, boolean inclusiveUpper) {
    int idx;
    try {
      idx = Integer.parseInt(token);
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("invalid array index: " + token);
    }
    int limit = inclusiveUpper ? size : size - 1;
    if (idx < 0 || idx > limit) {
      throw new IllegalArgumentException("array index out of bounds: " + idx);
    }
    return idx;
  }

  private static boolean isPrefix(List<String> prefix, List<String> path) {
    if (prefix.size() > path.size()) {
      return false;
    }
    for (int i = 0; i < prefix.size(); i++) {
      if (!prefix.get(i).equals(path.get(i))) {
        return false;
      }
    }
    return true;
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
            if (element.isString() && element.asString().equals(unescaped)) {
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
    if (pathNode.isString()) {
      String path = pathNode.asString();
      if (path.isEmpty() || path.equals("/")) {
        return path;
      }
      return path.startsWith("/") ? path : "/" + path;
    } else if (pathNode.isArray()) {
      // Convert array of path segments to JSON Pointer string
      StringBuilder sb = new StringBuilder();
      for (JsonNode segment : pathNode) {
        sb.append("/");
        if (segment.isString()) {
          String segmentStr = segment.asString();
          // Escape special characters per RFC 6901
          segmentStr = segmentStr.replace("~", "~0").replace("/", "~1");
          sb.append(segmentStr);
        } else {
          // For numeric indices, just append as-is
          sb.append(segment.asString(""));
        }
      }
      return sb.toString();
    } else {
      // Fallback: convert to string
      return "/" + pathNode.asString("");
    }
  }

  /** JSON Schema's primitive type names, in the order Go lists them when rejecting a bad one. */
  private static final List<String> VALID_SCHEMA_TYPES =
          List.of("array", "boolean", "integer", "number", "null", "object", "string");

  /**
   * Walks a schema for `"type"` values that are not JSON Schema primitives. The validator accepts
   * an unknown type and simply never matches, whereas Go rejects the schema outright, so the check
   * has to happen before validation. Returns the Go-style complaint, or null when the schema is
   * fine.
   */
  private static String findInvalidSchemaType(JsonNode node) {
    if (node == null) {
      return null;
    }
    if (node.isObject()) {
      JsonNode type = node.get("type");
      if (type != null && type.isTextual() && !VALID_SCHEMA_TYPES.contains(type.asText())) {
        return "has a primitive type that is NOT VALID -- given: /"
                + type.asText()
                + "/ Expected valid values are:["
                + String.join(" ", VALID_SCHEMA_TYPES)
                + "]";
      }
    }
    if (node.isObject() || node.isArray()) {
      for (JsonNode child : node) {
        String found = findInvalidSchemaType(child);
        if (found != null) {
          return found;
        }
      }
    }
    return null;
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
      String invalidType = findInvalidSchemaType(schemaNode);
      if (invalidType != null) {
        throw new BuiltinError(invalidType);
      }
      SchemaRegistry registry = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_7);
      Schema schema = registry.getSchema(schemaNode);
      List<Error> errors = schema.validate(documentNode);

      // Build result array
      RegoArray result = new RegoArray();
      if (errors.isEmpty()) {
        result.addValue(RegoBoolean.TRUE);
        result.addValue(new RegoArray());
      } else {
        result.addValue(RegoBoolean.FALSE);
        RegoArray errorArray = new RegoArray();
        for (Error error : errors) {
          // Go surfaces gojsonschema's ResultError fields: type, field, desc and the combined
          // "field: desc" as error.
          String field = instanceLocationToField(error.getInstanceLocation());
          String desc = goStyleDescription(error);
          RegoObject errorObj = new RegoObject();
          errorObj.setProp(new RegoString("desc"), new RegoString(desc));
          errorObj.setProp(new RegoString("error"), new RegoString(field + ": " + desc));
          errorObj.setProp(new RegoString("field"), new RegoString(field));
          errorObj.setProp(new RegoString("type"), new RegoString(goStyleType(error.getKeyword())));
          errorArray.addValue(errorObj);
        }
        result.addValue(errorArray);
      }

      return result;
    } catch (BuiltinError e) {
      throw e;
    } catch (Exception e) {
      throw new BuiltinError("json.match_schema: " + e.getMessage());
    }
  }

  /** gojsonschema names the root "(root)" and drops the leading slash from a pointer. */
  private static String instanceLocationToField(Object instanceLocation) {
    String path = instanceLocation == null ? "" : instanceLocation.toString();
    if (path.isEmpty() || "/".equals(path)) {
      return "(root)";
    }
    return path.startsWith("/") ? path.substring(1).replace('/', '.') : path;
  }

  /** gojsonschema reports the keyword as e.g. "invalid_type" for a "type" mismatch. */
  private static String goStyleType(String keyword) {
    return "type".equals(keyword) ? "invalid_type" : keyword;
  }

  /**
   * networknt phrases a type mismatch as "string found, integer expected"; gojsonschema, which
   * Go's json.match_schema returns, uses "Invalid type. Expected: integer, given: string".
   */
  private static String goStyleDescription(Error error) {
    String message = error.getMessage();
    if ("type".equals(error.getKeyword())) {
      java.util.regex.Matcher m =
              java.util.regex.Pattern.compile("^(\\S+) found, (\\S+) expected$").matcher(message);
      if (m.matches()) {
        return "Invalid type. Expected: " + m.group(2) + ", given: " + m.group(1);
      }
    }
    return message;
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
      String invalidType = findInvalidSchemaType(schemaNode);
      if (invalidType != null) {
        RegoArray invalid = new RegoArray();
        invalid.addValue(RegoBoolean.FALSE);
        invalid.addValue(new RegoString("jsonschema: " + invalidType));
        return invalid;
      }
      SchemaRegistry registry = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_7);
      registry.getSchema(schemaNode);

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
    } catch (JacksonException e) {
      throw new BuiltinError("yaml.marshal: " + e.getMessage());
    }
  }

  @OpaBuiltin(
          name = "yaml.is_valid",
          description = "Verifies the input string is a valid YAML document.",
          categories = {"encoding"},
          args = {@OpaType(type = "string", name = "x", description = "a YAML string")},
          result =
          @OpaType(
                  name = "result",
                  description = "`true` if `x` is valid YAML, `false` otherwise"))
  public RegoBoolean yamlIsValid(EvaluationContext ctx, RegoValue[] args) {
    if (!(args[0] instanceof RegoString yamlInput)) {
      return RegoBoolean.FALSE;
    }

    // Read the whole stream: readTree stops after the first document, missing errors in content
    // that follows it (including later documents), which Go's parser still rejects.
    try (JsonParser parser = YAML_MAPPER.createParser(yamlInput.getValue())) {
      while (parser.nextToken() != null) {
        // consume
      }
      return RegoBoolean.TRUE;
    } catch (JacksonException e) {
      return RegoBoolean.FALSE;
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
    } catch (JacksonException e) {
      MarkedYamlEngineException marked = findMarkedYamlException(e);
      if (marked != null) {
        // Go's yaml.v2 reports "yaml: line N: <problem>". Jackson's default message is a multi-line
        // snippet with carets, so rebuild the terse form from the structured fields.
        throw new BuiltinError("yaml: " + describeYamlError(marked));
      }
      throw new BuiltinError("yaml.unmarshal: " + e.getMessage());
    }
  }

  /** Jackson wraps SnakeYAML's parse failures, so look for the structured cause. */
  private static MarkedYamlEngineException findMarkedYamlException(Throwable e) {
    for (Throwable t = e; t != null; t = t.getCause()) {
      if (t instanceof MarkedYamlEngineException) {
        return (MarkedYamlEngineException) t;
      }
    }
    return null;
  }

  private static String describeYamlError(MarkedYamlEngineException e) {
    String problem = e.getProblem() != null ? e.getProblem() : e.getMessage();
    if (e.getProblemMark().isEmpty()) {
      return problem;
    }
    // SnakeYAML lines are 0-based; Go reports them 1-based.
    return "line " + (e.getProblemMark().get().getLine() + 1) + ": " + problem;
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
      ObjectNode result = JSON_MAPPER.createObjectNode();

      // Track array index mappings: key is the result array node, value is map of source->result
      // indices
      Map<JsonNode, Map<Integer, Integer>> arrayIndexMaps = new HashMap<>();

      for (String path : paths) {
          if (path.isEmpty()) {
              continue;
          }

        String[] parts = path.split("/");
        List<String> nonEmptyParts = new ArrayList<>();
        for (String part : parts) {
          if (!part.isEmpty()) {
            nonEmptyParts.add(part);
          }
        }

          if (nonEmptyParts.isEmpty()) {
              continue;
          }

        // Navigate to the value in the source, tracking structure types
        JsonNode current = node;
        List<Boolean> isArrayAtLevel = new ArrayList<>();

        for (String part : nonEmptyParts) {
            if (current == null) {
                break;
            }

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

          if (current == null) {
              continue;
          }

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
              ObjectNode objNode = (ObjectNode) resultNode;
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
          if (part.isEmpty()) {
              continue;
          }

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
