package io.github.open_policy_agent.opa.ast.builtin;

import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import io.github.open_policy_agent.opa.ast.builtin.impls.AggregateBuiltins;
import io.github.open_policy_agent.opa.ast.builtin.impls.ArithmeticBuiltins;
import io.github.open_policy_agent.opa.ast.builtin.impls.ArrayBuiltins;
import io.github.open_policy_agent.opa.ast.builtin.impls.CastBuiltins;
import io.github.open_policy_agent.opa.ast.builtin.impls.ComparisonBuiltins;
import io.github.open_policy_agent.opa.ast.builtin.impls.EncodingBuiltins;
import io.github.open_policy_agent.opa.ast.builtin.impls.HexBuiltins;
import io.github.open_policy_agent.opa.ast.builtin.impls.ObjectBuiltins;
import io.github.open_policy_agent.opa.ast.builtin.impls.PrintBuiltins;
import io.github.open_policy_agent.opa.ast.builtin.impls.SetBuiltins;
import io.github.open_policy_agent.opa.ast.builtin.impls.StringBuiltins;
import io.github.open_policy_agent.opa.ast.builtin.impls.TypeBuiltins;
import io.github.open_policy_agent.opa.ast.types.RegoValue;
import io.github.open_policy_agent.opa.rego.Capabilities;
import io.github.open_policy_agent.opa.rego.EvaluationContext;

public class BuiltinRegistry {

  // Core builtin implementation classes that are always available
  static final Class<?>[] BUILTIN_CLASSES = {
    CastBuiltins.class,
    ComparisonBuiltins.class,
    ObjectBuiltins.class,
    SetBuiltins.class,
    TypeBuiltins.class,
    AggregateBuiltins.class,
    ArithmeticBuiltins.class,
    ArrayBuiltins.class,
    EncodingBuiltins.class,
    HexBuiltins.class,
    StringBuiltins.class,
    PrintBuiltins.class,
  };

  public static final Map<String, BiFunction<EvaluationContext, RegoValue[], RegoValue>>
      AllBuiltIns = buildAllBuiltins();

  /** Build the map of all builtins by merging statically-registered and ServiceLoader-discovered providers */
  private static Map<String, BiFunction<EvaluationContext, RegoValue[], RegoValue>>
      buildAllBuiltins() {
    Stream<Map.Entry<String, BiFunction<EvaluationContext, RegoValue[], RegoValue>>> staticEntries =
        Stream.of(BUILTIN_CLASSES)
            .map(BuiltinRegistry::getBuiltinsFromClass)
            .flatMap(map -> map.entrySet().stream());

    Stream<Map.Entry<String, BiFunction<EvaluationContext, RegoValue[], RegoValue>>> serviceEntries =
        StreamSupport.stream(ServiceLoader.load(BuiltinProvider.class).spliterator(), false)
            .map(BuiltinProvider::builtins)
            .flatMap(map -> map.entrySet().stream());

    return Stream.concat(staticEntries, serviceEntries)
        .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  /** Reflectively call the static builtins() method on a builtin class */
  @SuppressWarnings("unchecked")
  private static Map<String, BiFunction<EvaluationContext, RegoValue[], RegoValue>>
      getBuiltinsFromClass(Class<?> clazz) {
    try {
      return (Map<String, BiFunction<EvaluationContext, RegoValue[], RegoValue>>)
          clazz.getMethod("builtins").invoke(null);
    } catch (Exception e) {
      throw new RuntimeException("Failed to get builtins from " + clazz.getName(), e);
    }
  }

  /**
   * Create a BuiltinRegistry with all capabilities populated by CapabilitiesGenerator. This
   * registry contains all implemented builtins with their metadata from annotations.
   */
  public static BuiltinRegistry allCapabilities() {
    Capabilities capabilities = CapabilitiesGenerator.generateCapabilities(AllBuiltIns);
    return fromCapabilities(capabilities);
  }

  private BuiltinRegistry(
      Map<String, BiFunction<EvaluationContext, RegoValue[], RegoValue>> builtIns) {
    this.builtIns = new HashMap<>(builtIns);
  }

  /**
   * Create a BuiltinRegistry from a Capabilities object. Only includes builtins that are both in
   * the capabilities list and implemented in AllBuiltIns.
   */
  public static BuiltinRegistry fromCapabilities(Capabilities capabilities) {
    HashMap<String, BiFunction<EvaluationContext, RegoValue[], RegoValue>> builtIns =
        new HashMap<>();

    for (Descriptor descriptor : capabilities.builtins) {
      if (!AllBuiltIns.containsKey(descriptor.name)) {
        throw new UnsupportedOperationException(
            "Unknown BuiltIn: " + descriptor.name); // TODO: Maybe a custom error type for this?
      }
      builtIns.put(descriptor.name, AllBuiltIns.get(descriptor.name));
    }

    return new BuiltinRegistry(builtIns);
  }

  private final HashMap<String, BiFunction<EvaluationContext, RegoValue[], RegoValue>> builtIns;

  /**
   * Generate a Capabilities object representing all currently implemented builtins.
   * Uses @OpaBuiltin annotations where available.
   */
  public static Capabilities generateCapabilities() {
    return CapabilitiesGenerator.generateCapabilities(AllBuiltIns);
  }

  public Set<String> registeredBuiltins() {
    return builtIns.keySet();
  }

  public void registerBuiltIn(
      // TODO: should this replace or throw an error
      String name, BiFunction<EvaluationContext, RegoValue[], RegoValue> builtIn) {
    builtIns.put(name, builtIn);
  }

  public boolean hasBuiltIn(String name) {
    return builtIns.containsKey(name);
  }

  public BiFunction<EvaluationContext, RegoValue[], RegoValue> getBuiltIn(String name) {
    // TODO: Maybe a custom error or something if it doesn't exist?
    return builtIns.get(name);
  }
}
