package io.github.open_policy_agent.opa.mapper;

import java.util.ServiceLoader;

/**
 * Static accessor for the active {@link AnnotationIntrospector}. Discovers an implementation via
 * {@link ServiceLoader}; if none is registered, falls back to {@link DefaultAnnotationIntrospector}
 * (which performs no annotation lookups and so honors only JavaBean conventions).
 */
final class AnnotationIntrospectors {

  private static final AnnotationIntrospector INSTANCE = load();

  private AnnotationIntrospectors() {}

  static AnnotationIntrospector get() {
    return INSTANCE;
  }

  private static AnnotationIntrospector load() {
    return ServiceLoader.load(AnnotationIntrospector.class)
        .findFirst()
        .orElseGet(DefaultAnnotationIntrospector::new);
  }
}
