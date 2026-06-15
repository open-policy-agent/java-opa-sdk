package io.github.open_policy_agent.opa.mapper;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

/**
 * Bean-only {@link AnnotationIntrospector}: returns no overrides, so callers fall back to standard
 * JavaBean property discovery. Used when no Jackson implementation is on the classpath.
 */
final class DefaultAnnotationIntrospector implements AnnotationIntrospector {

  @Override
  public String findPropertyName(Method getter, Field backingField) {
    return null;
  }

  @Override
  public boolean isIgnored(Method getter, Field backingField) {
    return false;
  }

  @Override
  public boolean isNonNullInclude(Method getter, Field backingField) {
    return false;
  }

  @Override
  public String findCreatorParamName(Parameter param) {
    return null;
  }

  @Override
  public boolean isJsonCreator(Constructor<?> ctor) {
    return false;
  }

  @Override
  public boolean isJsonCreator(Method method) {
    return false;
  }

  @Override
  public Visibility findFieldVisibility(Class<?> clazz) {
    return null;
  }

  @Override
  public boolean isJsonValue(Method method) {
    return false;
  }
}
