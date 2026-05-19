package io.github.open_policy_agent.opa.gson;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import io.github.open_policy_agent.opa.mapper.AnnotationIntrospector;
import io.github.open_policy_agent.opa.mapper.AnnotationIntrospector.Visibility;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import org.junit.jupiter.api.Test;

class GsonAnnotationIntrospectorTest {

  private final AnnotationIntrospector introspector = new GsonAnnotationIntrospector();

  // --- findPropertyName ---

  static class FieldRenamed {
    @SerializedName("alt") public String value;
  }

  @Test
  void findPropertyName_readsSerializedNameOnField() throws Exception {
    Field field = FieldRenamed.class.getField("value");
    assertThat(introspector.findPropertyName(null, field)).isEqualTo("alt");
  }

  static class GetterRenamed {
    private String value;

    @SerializedName("ignored") // Gson does not read getters for the property name.
    public String getValue() {
      return value;
    }
  }

  @Test
  void findPropertyName_ignoresAnnotationOnGetter() throws Exception {
    // Documents the SPI gap: Gson's annotation discovery model is field-only, so a
    // @SerializedName on a getter does not influence property naming. The Jackson
    // introspector reads getters; Gson's does not.
    Method getter = GetterRenamed.class.getMethod("getValue");
    assertThat(introspector.findPropertyName(getter, null)).isNull();
  }

  static class NoAnnotation {
    public String value;

    public String getValue() {
      return value;
    }
  }

  @Test
  void findPropertyName_returnsNullWhenUnannotated() throws Exception {
    Field field = NoAnnotation.class.getField("value");
    assertThat(introspector.findPropertyName(null, field)).isNull();
    assertThat(introspector.findPropertyName(null, null)).isNull();
  }

  // --- isIgnored ---

  static class TransientField {
    public transient String secret;
    public String visible;
  }

  @Test
  void isIgnored_respectsTransientModifier() throws Exception {
    Field secret = TransientField.class.getField("secret");
    Field visible = TransientField.class.getField("visible");
    assertThat(introspector.isIgnored(null, secret)).isTrue();
    assertThat(introspector.isIgnored(null, visible)).isFalse();
  }

  @Test
  void isIgnored_returnsFalseWhenFieldNullOrUnannotated() throws Exception {
    // No equivalent of @JsonIgnore in Gson's annotation set — getters can't be ignored
    // through annotations, only through ExclusionStrategy on the builder.
    Method getter = NoAnnotation.class.getMethod("getValue");
    assertThat(introspector.isIgnored(getter, null)).isFalse();
    assertThat(introspector.isIgnored(null, null)).isFalse();
  }

  // --- Methods Gson cannot express ---

  @Test
  void isNonNullInclude_alwaysFalse_gsonControlsThisGlobally() throws Exception {
    // Documents that the SPI question "should this property omit nulls?" cannot be answered
    // from Gson annotations — null inclusion is a builder-level concern.
    Field field = NoAnnotation.class.getField("value");
    assertThat(introspector.isNonNullInclude(null, field)).isFalse();
    assertThat(introspector.isNonNullInclude(null, null)).isFalse();
  }

  @Test
  void isJsonCreator_alwaysFalse_gsonHasNoCreatorAnnotation() throws Exception {
    Constructor<?> ctor = NoAnnotation.class.getDeclaredConstructor();
    Method method = NoAnnotation.class.getMethod("getValue");
    assertThat(introspector.isJsonCreator(ctor)).isFalse();
    assertThat(introspector.isJsonCreator(method)).isFalse();
  }

  @Test
  void findFieldVisibility_alwaysNull_gsonHasNoVisibilityAnnotation() {
    // Defers to RegoMapper's JavaBean defaults; visibility configuration on Gson lives on
    // the GsonBuilder, not in annotations.
    assertThat(introspector.findFieldVisibility(NoAnnotation.class)).isNull();
    assertThat(introspector.findFieldVisibility(FieldRenamed.class)).isNull();
  }

  @Test
  void isJsonValue_alwaysFalse_gsonUsesTypeAdapters() throws Exception {
    Method method = NoAnnotation.class.getMethod("getValue");
    assertThat(introspector.isJsonValue(method)).isFalse();
  }

  // --- findCreatorParamName ---

  static class WithCreator {
    public WithCreator(String foo, String bar) {}
  }

  @Test
  void findCreatorParamName_alwaysNull_gsonHasNoParamAnnotation() throws NoSuchMethodException {
    // Documents the SPI gap: Gson's @SerializedName cannot target parameters (its declared
    // @Target is FIELD, METHOD only), and Gson's annotation set has no @JsonCreator analogue.
    // Constructor injection is configured via InstanceCreator on the GsonBuilder, not via
    // annotations, so this method has no annotation to read.
    Constructor<?> ctor = WithCreator.class.getConstructor(String.class, String.class);
    Parameter[] params = ctor.getParameters();
    assertThat(introspector.findCreatorParamName(params[0])).isNull();
    assertThat(introspector.findCreatorParamName(params[1])).isNull();
  }

  // --- @Expose helper (out-of-SPI but useful for diagnostics) ---

  static class ExposedFields {
    @Expose public String visible;
    public String hidden;
  }

  @Test
  void hasExpose_packagePrivateHelperReturnsAnnotationPresence() throws Exception {
    assertThat(GsonAnnotationIntrospector.hasExpose(ExposedFields.class.getField("visible")))
        .isTrue();
    assertThat(GsonAnnotationIntrospector.hasExpose(ExposedFields.class.getField("hidden")))
        .isFalse();
    assertThat(GsonAnnotationIntrospector.hasExpose(null)).isFalse();
  }
}
