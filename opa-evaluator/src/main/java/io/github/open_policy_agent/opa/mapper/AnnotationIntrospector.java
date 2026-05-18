package io.github.open_policy_agent.opa.mapper;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

/**
 * SPI for resolving annotation-driven property metadata on user POJOs without binding the
 * evaluator to a specific JSON library. Implementations may inspect Jackson, Gson, or any other
 * annotation set; the evaluator's mapper consults this introspector when discovering properties,
 * creators, and visibility.
 *
 * <p>Discovered via {@link java.util.ServiceLoader}. The {@code opa-jackson} module provides a
 * Jackson-aware implementation. If no implementation is found, {@link DefaultAnnotationIntrospector}
 * is used, which honors only standard JavaBean conventions.
 */
public interface AnnotationIntrospector {

  /**
   * Return the JSON name override for a property, or {@code null} if no annotation overrides the
   * default (bean-derived) name. Either {@code getter} or {@code backingField} may be null.
   */
  String findPropertyName(Method getter, Field backingField);

  /** Return true if a property is annotated as ignored. Either argument may be null. */
  boolean isIgnored(Method getter, Field backingField);

  /** Return true if a property is annotated to be omitted when null. Either argument may be null. */
  boolean isNonNullInclude(Method getter, Field backingField);

  /** Return the JSON name for a constructor/factory parameter, or {@code null} if not annotated. */
  String findCreatorParamName(Parameter param);

  /** Return true if a constructor is annotated as a JSON creator (in non-delegating mode). */
  boolean isJsonCreator(Constructor<?> ctor);

  /** Return true if a static factory method is annotated as a JSON creator (in non-delegating mode). */
  boolean isJsonCreator(Method method);

  /** Return field-visibility override declared on the class, or {@code null} for the default. */
  Visibility findFieldVisibility(Class<?> clazz);

  /** Return true if a method is marked as the single value for the enclosing type. */
  boolean isJsonValue(Method method);

  /** Field-visibility levels (matches Jackson's {@code JsonAutoDetect.Visibility} semantics). */
  enum Visibility {
    ANY,
    NON_PRIVATE,
    PROTECTED_AND_PUBLIC,
    PUBLIC_ONLY,
    NONE
  }
}
