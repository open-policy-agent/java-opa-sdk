package io.github.open_policy_agent.opa.ast.builtin;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.TreeSet;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import io.github.open_policy_agent.opa.ast.types.RegoValue;
import io.github.open_policy_agent.opa.rego.Capabilities;
import io.github.open_policy_agent.opa.rego.EvaluationContext;

/** Generates Capabilities from @OpaBuiltin annotations on builtin implementation methods. */
public class CapabilitiesGenerator {

  /** Scan all builtin classes and generate a Capabilities object from their annotations. */
  public static Capabilities generateCapabilities(
      Map<String, BiFunction<EvaluationContext, RegoValue[], RegoValue>> builtinMap) {

    Capabilities capabilities = new Capabilities();
    List<Descriptor> descriptors = new ArrayList<>();

    // Build a map of builtin names to their annotations
    Map<String, OpaBuiltin> annotationMap = scanBuiltinAnnotations();

    // For each builtin in the map, try to find its annotation. Iterate in sorted order so the
    // generated capabilities are byte-for-byte reproducible: capabilities.json is checked in and
    // diffed in CI, and builtinMap has no defined iteration order.
    for (String builtinName : new TreeSet<>(builtinMap.keySet())) {
      OpaBuiltin annotation = annotationMap.get(builtinName);
      if (annotation != null) {
        Descriptor desc = createDescriptorFromAnnotation(annotation);
        descriptors.add(desc);
      } else {
        // Create minimal descriptor for unannotated builtins
        descriptors.add(new Descriptor(builtinName));
      }
    }

    capabilities.builtins = descriptors;

    return capabilities;
  }

  /** Scan all builtin implementation classes for @OpaBuiltin annotations */
  private static Map<String, OpaBuiltin> scanBuiltinAnnotations() {
    Map<String, OpaBuiltin> annotationMap = new HashMap<>();

    // Scan statically-registered builtin classes
    for (Class<?> clazz : BuiltinRegistry.BUILTIN_CLASSES) {
      scanClass(clazz, annotationMap);
    }

    // Scan ServiceLoader-discovered provider classes
    for (BuiltinProvider provider : ServiceLoader.load(BuiltinProvider.class)) {
      scanClass(provider.getClass(), annotationMap);
    }

    return annotationMap;
  }

  private static void scanClass(Class<?> clazz, Map<String, OpaBuiltin> out) {
    for (Method method : clazz.getDeclaredMethods()) {
      OpaBuiltin annotation = method.getAnnotation(OpaBuiltin.class);
      if (annotation != null) {
        out.put(annotation.name(), annotation);
      }
    }
  }

  /** Create a Descriptor from an @OpaBuiltin annotation */
  private static Descriptor createDescriptorFromAnnotation(OpaBuiltin annotation) {
    Descriptor descriptor = new Descriptor();
    descriptor.name = annotation.name();

    // Set description if provided
    String desc = annotation.description();
    if (!desc.isEmpty()) {
      descriptor.description = desc;
    }

    // Set categories if provided
    String[] cats = annotation.categories();
    if (cats.length > 0) {
      descriptor.categories = java.util.Arrays.asList(cats);
    }

    // Create declaration
    Descriptor.Declaration decl = new Descriptor.Declaration();

    // Convert arg annotations to Argument objects
    decl.args = new ArrayList<>();
    OpaType[] argTypes = annotation.args();
    for (OpaType argAnnotation : argTypes) {
      decl.args.add(convertOpaTypeToArgument(argAnnotation));
    }

    // Set result type from OpaType annotation
    OpaType resultAnnotation = annotation.result();
    decl.result = convertOpaTypeToArgument(resultAnnotation);

    // Always set decl, even if args is empty
    descriptor.decl = decl;

    // Set infix if applicable
    String inf = annotation.infix();
    if (!inf.isEmpty()) {
      descriptor.infix = inf;
    }

    // Set nondeterministic flag if true
    if (annotation.nondeterministic()) {
      descriptor.nondeterministic = true;
    }

    return descriptor;
  }

  /** Convert an @OpaType annotation to a Descriptor.Argument */
  private static Descriptor.Argument convertOpaTypeToArgument(OpaType opaType) {
    Descriptor.Argument arg = new Descriptor.Argument();

    // Set name if provided
    if (!opaType.name().isEmpty()) {
      arg.name = opaType.name();
    }

    // Set description if provided
    if (!opaType.description().isEmpty()) {
      arg.description = opaType.description();
    }

    // Handle either single type or union of types
    if (!opaType.type().isEmpty()) {
      // Single type
      arg.type = opaType.type();
    } else if (opaType.of().length > 0) {
      // Union type - create 'of' array
      arg.type = "any"; // Union types use "any" as the base type
      arg.of = new ArrayList<>();
      for (OpaVal val : opaType.of()) {
        Descriptor.Argument unionArg = new Descriptor.Argument();
        unionArg.type = val.value();
        arg.of.add(unionArg);
      }
    } else {
      // Default to "any" if neither is specified
      arg.type = "any";
    }

    // Handle dynamic type if specified
    OpaDynamic dynamicAnnotation = opaType.dynamic();
    if (!dynamicAnnotation.type().isEmpty()) {
      // Simple dynamic type (e.g., array with element type)
      arg.dynamic = new Descriptor.Dynamic(dynamicAnnotation.type());
    } else if (!dynamicAnnotation.keyType().isEmpty() || !dynamicAnnotation.valueType().isEmpty()) {
      // Key-value dynamic type (e.g., object with key/value types)
      Descriptor.Argument keyArg = new Descriptor.Argument(dynamicAnnotation.keyType());
      Descriptor.Argument valueArg = new Descriptor.Argument(dynamicAnnotation.valueType());
      arg.dynamic = new Descriptor.Dynamic(keyArg, valueArg);
    }

    return arg;
  }

  /** Generate a list of all builtin names from annotations */
  public static List<String> getAllBuiltinNames(
      Map<String, BiFunction<EvaluationContext, RegoValue[], RegoValue>> builtinMap) {
    return builtinMap.keySet().stream().sorted().collect(Collectors.toList());
  }
}
