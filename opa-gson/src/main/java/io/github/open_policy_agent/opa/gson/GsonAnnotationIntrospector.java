package io.github.open_policy_agent.opa.gson;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import io.github.open_policy_agent.opa.mapper.AnnotationIntrospector;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;

/**
 * Gson-backed {@link AnnotationIntrospector}.
 *
 * <p>Gson's annotation surface is intentionally narrower than Jackson's. The mapping below
 * documents what's expressible:
 *
 * <table>
 *   <caption>SPI method &rarr; Gson concept</caption>
 *   <tr><th>SPI method</th><th>Gson equivalent</th></tr>
 *   <tr><td>{@code findPropertyName}</td><td>{@code @SerializedName} on field (Gson does not
 *       inspect getters for the property name).</td></tr>
 *   <tr><td>{@code isIgnored}</td><td>The Java {@code transient} keyword on the field, which
 *       Gson honors. Gson has no dedicated {@code @JsonIgnore} analogue; finer-grained
 *       exclusion uses an {@code ExclusionStrategy} configured on the {@code GsonBuilder},
 *       which is out of band of the SPI.</td></tr>
 *   <tr><td>{@code isNonNullInclude}</td><td><b>Not expressible.</b> Gson controls null
 *       inclusion globally via {@code GsonBuilder.serializeNulls()}, not per field.</td></tr>
 *   <tr><td>{@code findCreatorParamName}, {@code isJsonCreator}</td><td><b>Not expressible.</b>
 *       Gson uses {@code InstanceCreator} or {@code TypeAdapter} registered on the
 *       {@code GsonBuilder}, not annotations. {@code @SerializedName} cannot target
 *       parameters either (its declared targets are {@code FIELD} and {@code METHOD}).</td></tr>
 *   <tr><td>{@code findFieldVisibility}</td><td><b>Not expressible.</b> Gson configures
 *       visibility via {@code GsonBuilder.excludeFieldsWithModifiers(...)}, not annotations.
 *       Gson's default is to serialize all non-{@code transient}, non-{@code static} fields,
 *       which corresponds to {@link Visibility#ANY}; we leave that decision to the caller and
 *       return {@code null} so JavaBean defaults apply.</td></tr>
 *   <tr><td>{@code isJsonValue}</td><td><b>Not expressible.</b> Gson uses
 *       {@code @JsonAdapter} or registered {@code TypeAdapter}s, not annotations on a
 *       single value method.</td></tr>
 * </table>
 *
 * <p>The narrow expressible surface is the point of having this implementation: it shows the
 * SPI doesn't <em>force</em> a Gson backend to grow Jackson features &mdash; methods that
 * don't apply return {@code null}/{@code false}, and {@code RegoMapper} falls back to
 * JavaBean defaults for those concerns.
 *
 * <p>Discovered via {@link java.util.ServiceLoader}; consumers don't reference this class
 * directly. Note: only one {@link AnnotationIntrospector} may be registered at a time, so
 * {@code opa-gson} and {@code opa-jackson} are mutually exclusive on the classpath.
 */
public class GsonAnnotationIntrospector implements AnnotationIntrospector {

  @Override
  public String findPropertyName(Method getter, Field backingField) {
    if (backingField != null) {
      SerializedName ann = backingField.getAnnotation(SerializedName.class);
      if (ann != null && !ann.value().isEmpty()) {
        return ann.value();
      }
    }
    // Gson does not consult getters for property names; bean discovery picks them up.
    return null;
  }

  @Override
  public boolean isIgnored(Method getter, Field backingField) {
    // Gson respects the `transient` keyword (and `static`) when serializing fields.
    if (backingField != null) {
      int mods = backingField.getModifiers();
      if (Modifier.isTransient(mods)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean isNonNullInclude(Method getter, Field backingField) {
    // Gson controls null inclusion globally via GsonBuilder.serializeNulls(); the annotation
    // surface carries no per-property signal we can return here.
    return false;
  }

  @Override
  public String findCreatorParamName(Parameter param) {
    // Gson has no @JsonCreator-equivalent and @SerializedName cannot target parameters
    // (Gson's @Target is FIELD, METHOD only). No annotation to inspect here.
    return null;
  }

  @Override
  public boolean isJsonCreator(Constructor<?> ctor) {
    // Gson does not use annotations to mark creators; InstanceCreator is registered on the
    // GsonBuilder. No annotation to inspect.
    return false;
  }

  @Override
  public boolean isJsonCreator(Method method) {
    return false;
  }

  @Override
  public Visibility findFieldVisibility(Class<?> clazz) {
    // Gson has no @JsonAutoDetect-equivalent. Returning null defers to JavaBean defaults in
    // RegoMapper, which is the right behavior: callers configure Gson's visibility on the
    // GsonBuilder, which doesn't propagate to this SPI.
    return null;
  }

  @Override
  public boolean isJsonValue(Method method) {
    // No Gson annotation marks a single value-producing method. Custom TypeAdapter is the
    // Gson-idiomatic way to handle this, registered on the GsonBuilder.
    return false;
  }

  /**
   * Returns true if the field carries Gson's {@link Expose @Expose} annotation. Not part of
   * the SPI &mdash; exposed for tests/diagnostics. {@code @Expose} is only meaningful when
   * the consuming {@code Gson} instance was built with
   * {@code GsonBuilder.excludeFieldsWithoutExposeAnnotation()}, which is configured outside
   * the introspector.
   */
  static boolean hasExpose(Field field) {
    return field != null && field.isAnnotationPresent(Expose.class);
  }
}
