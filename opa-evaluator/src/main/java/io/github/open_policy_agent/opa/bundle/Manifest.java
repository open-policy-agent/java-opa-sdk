package io.github.open_policy_agent.opa.bundle;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable, JSON-library-independent bundle manifest.
 *
 * <p>The field set mirrors OPA's {@code bundle.Manifest}. Typed accessors apply OPA's defaults without
 * changing the original fields retained by {@link #asMap()}; null collection elements decode to their
 * zero value as Go does. Bundle activation, root ownership, delta patches and signatures are
 * bundle-level concerns in OPA rather than manifest fields, and are handled separately.
 *
 * <p>Equality compares defaulted typed fields and retained extensions; {@link #asMap()} preserves
 * the original field presence. All exposed collections, including nested values, are immutable.
 */
public final class Manifest {

  private static final String DEFAULT_DECISION = "default_decision";
  private static final String FILE_REGO_VERSIONS = "file_rego_versions";
  private static final String METADATA = "metadata";
  private static final String REGO_VERSION = "rego_version";
  private static final String REVISION = "revision";
  private static final String ROOTS = "roots";
  private static final String WASM = "wasm";

  private static final Set<String> KNOWN_FIELDS = Set.of(
      DEFAULT_DECISION,
      FILE_REGO_VERSIONS,
      METADATA,
      REGO_VERSION,
      REVISION,
      ROOTS,
      WASM);

  private final String defaultDecision;
  private final Map<String, Integer> fileRegoVersions;
  private final Map<String, Object> metadata;
  private final Integer regoVersion;
  private final String revision;
  private final List<String> roots;
  private final Map<String, Object> additionalProperties;
  private final Map<String, Object> properties;
  private final List<WasmResolver> wasm;

  private Manifest(Map<String, Object> source) {
    this.properties = immutableObject(source);
    this.revision = Objects.requireNonNullElse(optionalString(properties, REVISION), "");
    this.roots = hasRoots() ? stringList(ROOTS, properties.get(ROOTS)) : List.of("");
    this.wasm = optionalObjectList(properties, WASM).stream().map(WasmResolver::new).toList();
    this.regoVersion = optionalInteger(properties, REGO_VERSION);
    this.fileRegoVersions = optionalIntegerMap(properties, FILE_REGO_VERSIONS);
    this.metadata = optionalObject(properties, METADATA);
    this.defaultDecision = optionalString(properties, DEFAULT_DECISION);

    this.additionalProperties = additionalProperties(properties, KNOWN_FIELDS);
  }

  /** Create a manifest from a plain JSON-compatible object tree. */
  public static Manifest fromMap(Map<String, Object> properties) {
    Objects.requireNonNull(properties, "properties");
    return new Manifest(properties);
  }

  /**
   * Return the bundle revision, defaulting to an empty string when omitted or
   * null.
   */
  public String getRevision() {
    return revision;
  }

  /** Return whether the manifest explicitly declared non-null {@code roots}. */
  public boolean hasRoots() {
    return properties.get(ROOTS) != null;
  }

  /**
   * Return the declared roots, defaulting to the global root ({@code [""]}) when
   * omitted or null.
   *
   * <p>
   * An explicitly empty array remains empty. Paths are not normalized or
   * validated here.
   */
  public List<String> getRoots() {
    return roots;
  }

  /** Return the optional Wasm resolver descriptions. */
  public List<WasmResolver> getWasm() {
    return wasm;
  }

  /**
   * Return the bundle-wide Rego version, or {@code null} when omitted or null.
   */
  public Integer getRegoVersion() {
    return regoVersion;
  }

  /** Return the per-file Rego version overrides. */
  public Map<String, Integer> getFileRegoVersions() {
    return fileRegoVersions;
  }

  /** Return the free-form bundle metadata. */
  public Map<String, Object> getMetadata() {
    return metadata;
  }

  /**
   * Return the SDK-specific default decision, or {@code null} when omitted or
   * null.
   */
  public String getDefaultDecision() {
    return defaultDecision;
  }

  /**
   * Return unknown top-level manifest fields retained for forward compatibility.
   */
  public Map<String, Object> getAdditionalProperties() {
    return additionalProperties;
  }

  /** Return all manifest fields as an immutable, JSON-compatible object tree. */
  public Map<String, Object> asMap() {
    return properties;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof Manifest that)) {
      return false;
    }
    return revision.equals(that.revision)
        && roots.equals(that.roots)
        && wasm.equals(that.wasm)
        && Objects.equals(regoVersion, that.regoVersion)
        && fileRegoVersions.equals(that.fileRegoVersions)
        && Objects.equals(metadata, that.metadata)
        && Objects.equals(defaultDecision, that.defaultDecision)
        && additionalProperties.equals(that.additionalProperties);
  }

  @Override
  public int hashCode() {
    return Objects.hash(revision, roots, wasm, regoVersion, fileRegoVersions, metadata, defaultDecision,
        additionalProperties);
  }

  @Override
  public String toString() {
    return properties.toString();
  }

  /** Immutable Wasm module-to-entrypoint mapping. */
  public static final class WasmResolver {
    private static final Set<String> KNOWN_FIELDS = Set.of("entrypoint", "module", "annotations");

    private final Map<String, Object> properties;
    private final Map<String, Object> additionalProperties;
    private final String entrypoint;
    private final String module;
    private final List<Map<String, Object>> annotations;

    private WasmResolver(Map<String, Object> properties) {
      this.properties = properties;
      this.entrypoint = Objects.requireNonNullElse(optionalString(properties, "entrypoint"), "");
      this.module = Objects.requireNonNullElse(optionalString(properties, "module"), "");
      this.annotations = optionalObjectList(properties, "annotations");
      this.additionalProperties = additionalProperties(properties, KNOWN_FIELDS);
    }

    /**
     * Return the entrypoint, defaulting to an empty string when omitted or null.
     */
    public String getEntrypoint() {
      return entrypoint;
    }

    /**
     * Return the module path, defaulting to an empty string when omitted or null.
     */
    public String getModule() {
      return module;
    }

    /**
     * Return annotation objects (null entries decode to empty maps); their fields
     * remain untyped.
     */
    public List<Map<String, Object>> getAnnotations() {
      return annotations;
    }

    /** Return the original immutable resolver fields, including extensions. */
    public Map<String, Object> asMap() {
      return properties;
    }

    @Override
    public boolean equals(Object other) {
      return other instanceof WasmResolver that
          && entrypoint.equals(that.entrypoint)
          && module.equals(that.module)
          && annotations.equals(that.annotations)
          && additionalProperties.equals(that.additionalProperties);
    }

    @Override
    public int hashCode() {
      return Objects.hash(entrypoint, module, annotations, additionalProperties);
    }

    @Override
    public String toString() {
      return properties.toString();
    }
  }

  private static Map<String, Object> additionalProperties(
      Map<String, Object> properties, Set<String> knownFields) {
    Map<String, Object> additional = new LinkedHashMap<>();
    properties.forEach(
        (name, value) -> {
          if (!knownFields.contains(name)) {
            additional.put(name, value);
          }
        });
    return Collections.unmodifiableMap(additional);
  }

  private static String optionalString(Map<String, Object> source, String field) {
    Object value = source.get(field);
    if (value == null) {
      return null;
    }
    if (!(value instanceof String string)) {
      throw wrongType(field, "string", value);
    }
    return string;
  }

  private static Integer optionalInteger(Map<String, Object> source, String field) {
    Object value = source.get(field);
    if (value == null) {
      return null;
    }
    return requireInteger(field, value);
  }

  private static Integer requireInteger(String field, Object value) {
    if (!(value instanceof Number number)) {
      throw wrongType(field, "integer", value);
    }
    try {
      // JSON providers may represent an integral JSON number as a floating-point value.
      return new BigDecimal(number.toString()).intValueExact();
    } catch (ArithmeticException | NumberFormatException e) {
      throw wrongType(field, "32-bit integer", value);
    }
  }

  private static List<String> stringList(String field, Object value) {
    if (!(value instanceof List<?> list)) {
      throw wrongType(field, "array of strings", value);
    }
    List<String> result = new ArrayList<>();
    for (Object element : list) {
      if (element != null && !(element instanceof String)) {
        throw wrongType(field, "array of strings", element);
      }
      result.add(element == null ? "" : (String) element);
    }
    return Collections.unmodifiableList(result);
  }

  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> optionalObjectList(
      Map<String, Object> source, String field) {
    Object value = source.get(field);
    if (value == null) {
      return List.of();
    }
    if (!(value instanceof List<?> list)) {
      throw wrongType(field, "array of objects", value);
    }
    List<Map<String, Object>> result = new ArrayList<>();
    for (Object element : list) {
      if (element != null && !(element instanceof Map<?, ?>)) {
        throw wrongType(field, "array of objects", element);
      }
      result.add(element == null ? Map.of() : (Map<String, Object>) element);
    }
    return Collections.unmodifiableList(result);
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> optionalObject(Map<String, Object> source, String field) {
    Object value = source.get(field);
    if (value == null) {
      return Map.of();
    }
    if (!(value instanceof Map<?, ?>)) {
      throw wrongType(field, "object", value);
    }
    return (Map<String, Object>) value;
  }

  private static Map<String, Integer> optionalIntegerMap(
      Map<String, Object> source, String field) {
    Map<String, Integer> result = new LinkedHashMap<>();
    optionalObject(source, field).forEach(
        (key, value) -> result.put(key, value == null ? 0 : requireInteger(field, value)));
    return Collections.unmodifiableMap(result);
  }

  private static IllegalArgumentException wrongType(
      String field, String expected, Object actual) {
    return new IllegalArgumentException(
        "Manifest field '" + field + "' must be " + expected + ", got "
            + actual.getClass().getSimpleName());
  }

  private static Map<String, Object> immutableObject(Map<?, ?> source) {
    Map<String, Object> result = new LinkedHashMap<>();
    for (Map.Entry<?, ?> entry : source.entrySet()) {
      if (!(entry.getKey() instanceof String key)) {
        throw new IllegalArgumentException("Manifest keys must be strings");
      }
      result.put(key, immutableValue(entry.getValue()));
    }
    return Collections.unmodifiableMap(result);
  }

  private static Object immutableValue(Object value) {
    if (value instanceof Map<?, ?> map) {
      return immutableObject(map);
    }
    if (value instanceof List<?> list) {
      List<Object> result = new ArrayList<>();
      for (Object element : list) {
        result.add(immutableValue(element));
      }
      return Collections.unmodifiableList(result);
    }
    if (value == null
        || value instanceof String
        || value instanceof Byte
        || value instanceof Short
        || value instanceof Integer
        || value instanceof Long
        || value instanceof BigInteger
        || value instanceof BigDecimal
        || (value instanceof Float number && Float.isFinite(number))
        || (value instanceof Double number && Double.isFinite(number))
        || value instanceof Boolean) {
      return value;
    }
    if (value instanceof Float || value instanceof Double) {
      throw new IllegalArgumentException("Manifest values must be finite numbers, got " + value);
    }
    throw new IllegalArgumentException(
        "Manifest values must use JSON-compatible types, got " + value.getClass().getSimpleName());
  }
}
