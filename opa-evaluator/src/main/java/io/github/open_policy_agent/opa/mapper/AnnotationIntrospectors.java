package io.github.open_policy_agent.opa.mapper;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

/**
 * Static accessor for the active {@link AnnotationIntrospector}. Discovers an implementation via
 * {@link ServiceLoader}; if none is registered, falls back to {@link DefaultAnnotationIntrospector}
 * (which performs no annotation lookups and so honors only JavaBean conventions). Throws if more
 * than one implementation is registered, to avoid an ambiguous runtime.
 */
final class AnnotationIntrospectors {

  private static final AnnotationIntrospector INSTANCE = load();

  private AnnotationIntrospectors() {}

  static AnnotationIntrospector get() {
    return INSTANCE;
  }

  private static AnnotationIntrospector load() {
    List<AnnotationIntrospector> impls = new ArrayList<>();
    for (AnnotationIntrospector impl : ServiceLoader.load(AnnotationIntrospector.class)) {
      impls.add(impl);
    }
    if (impls.isEmpty()) {
      return new DefaultAnnotationIntrospector();
    }
    if (impls.size() > 1) {
      StringBuilder names = new StringBuilder();
      for (int i = 0; i < impls.size(); i++) {
        if (i > 0) names.append(", ");
        names.append(impls.get(i).getClass().getName());
      }
      throw new IllegalStateException(
          "Multiple AnnotationIntrospector implementations found on the classpath: "
              + names
              + ". Only one provider may be registered.");
    }
    return impls.get(0);
  }
}
