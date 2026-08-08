package io.github.open_policy_agent.opa.mapper;

import io.github.open_policy_agent.opa.spi.Services;

/**
 * Static accessor for the active {@link AnnotationIntrospector}. Discovers an implementation via
 * {@link java.util.ServiceLoader}; if none is registered, falls back to {@link
 * DefaultAnnotationIntrospector} (which performs no annotation lookups and so honors only JavaBean
 * conventions). Throws if more than one implementation is registered, to avoid an ambiguous
 * runtime.
 */
final class AnnotationIntrospectors {

  private static final AnnotationIntrospector INSTANCE = load();

  private AnnotationIntrospectors() {}

  static AnnotationIntrospector get() {
    return INSTANCE;
  }

  private static AnnotationIntrospector load() {
    return Services.loadAtMostOne(AnnotationIntrospector.class)
        .orElseGet(DefaultAnnotationIntrospector::new);
  }
}
