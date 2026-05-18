package io.github.open_policy_agent.opa.jackson;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import io.github.open_policy_agent.opa.mapper.AnnotationIntrospector;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

/**
 * Jackson-backed {@link AnnotationIntrospector}. Reads {@code @JsonProperty},
 * {@code @JsonIgnore}, {@code @JsonInclude}, {@code @JsonCreator}, {@code @JsonAutoDetect}, and
 * {@code @JsonValue} from user POJOs so the evaluator's {@code RegoMapper} can mirror Jackson's
 * binding rules.
 *
 * <p>Discovered via {@link java.util.ServiceLoader}; consumers don't reference this class
 * directly.
 */
public class JacksonAnnotationIntrospector implements AnnotationIntrospector {

  @Override
  public String findPropertyName(Method getter, Field backingField) {
    if (getter != null) {
      JsonProperty jp = getter.getAnnotation(JsonProperty.class);
      if (jp != null && !jp.value().isEmpty()) {
        return jp.value();
      }
    }
    if (backingField != null) {
      JsonProperty jp = backingField.getAnnotation(JsonProperty.class);
      if (jp != null && !jp.value().isEmpty()) {
        return jp.value();
      }
      // Even an empty @JsonProperty marks a field as discovered (without renaming it).
      if (backingField.isAnnotationPresent(JsonProperty.class)) {
        return backingField.getName();
      }
    }
    return null;
  }

  @Override
  public boolean isIgnored(Method getter, Field backingField) {
    return hasAnnotation(getter, backingField, JsonIgnore.class);
  }

  @Override
  public boolean isNonNullInclude(Method getter, Field backingField) {
    if (getter != null) {
      JsonInclude ji = getter.getAnnotation(JsonInclude.class);
      if (ji != null && ji.value() == JsonInclude.Include.NON_NULL) {
        return true;
      }
    }
    if (backingField != null) {
      JsonInclude ji = backingField.getAnnotation(JsonInclude.class);
      return ji != null && ji.value() == JsonInclude.Include.NON_NULL;
    }
    return false;
  }

  @Override
  public String findCreatorParamName(Parameter param) {
    JsonProperty jp = param.getAnnotation(JsonProperty.class);
    return jp != null ? jp.value() : null;
  }

  @Override
  public boolean isJsonCreator(Constructor<?> ctor) {
    JsonCreator jc = ctor.getAnnotation(JsonCreator.class);
    return jc != null && jc.mode() != JsonCreator.Mode.DELEGATING;
  }

  @Override
  public boolean isJsonCreator(Method method) {
    JsonCreator jc = method.getAnnotation(JsonCreator.class);
    return jc != null && jc.mode() != JsonCreator.Mode.DELEGATING;
  }

  @Override
  public Visibility findFieldVisibility(Class<?> clazz) {
    JsonAutoDetect ann = clazz.getAnnotation(JsonAutoDetect.class);
    if (ann == null || ann.fieldVisibility() == JsonAutoDetect.Visibility.DEFAULT) {
      return null;
    }
    switch (ann.fieldVisibility()) {
      case ANY:
        return Visibility.ANY;
      case NON_PRIVATE:
        return Visibility.NON_PRIVATE;
      case PROTECTED_AND_PUBLIC:
        return Visibility.PROTECTED_AND_PUBLIC;
      case NONE:
        return Visibility.NONE;
      case PUBLIC_ONLY:
      default:
        return Visibility.PUBLIC_ONLY;
    }
  }

  @Override
  public boolean isJsonValue(Method method) {
    return method.isAnnotationPresent(JsonValue.class);
  }

  private static <A extends Annotation> boolean hasAnnotation(
      Method getter, Field field, Class<A> annotationType) {
    if (getter != null && getter.isAnnotationPresent(annotationType)) {
      return true;
    }
    return field != null && field.isAnnotationPresent(annotationType);
  }
}
