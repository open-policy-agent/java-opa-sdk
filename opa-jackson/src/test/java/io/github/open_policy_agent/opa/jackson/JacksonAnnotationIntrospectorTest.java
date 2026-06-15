package io.github.open_policy_agent.opa.jackson;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import io.github.open_policy_agent.opa.mapper.AnnotationIntrospector;
import io.github.open_policy_agent.opa.mapper.AnnotationIntrospector.Visibility;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import org.junit.jupiter.api.Test;

class JacksonAnnotationIntrospectorTest {

  private final AnnotationIntrospector introspector = new JacksonAnnotationIntrospector();

  // --- findPropertyName ---

  static class GetterRenamed {
    private String value;

    @JsonProperty("renamed")
    public String getValue() {
      return value;
    }
  }

  @Test
  void findPropertyName_readsRenameOnGetter() throws Exception {
    Method getter = GetterRenamed.class.getMethod("getValue");
    assertThat(introspector.findPropertyName(getter, null)).isEqualTo("renamed");
  }

  static class FieldRenamed {
    @JsonProperty("alt") public String value;
  }

  @Test
  void findPropertyName_readsRenameOnField() throws Exception {
    Field field = FieldRenamed.class.getField("value");
    assertThat(introspector.findPropertyName(null, field)).isEqualTo("alt");
  }

  static class FieldMarkedDiscovered {
    @JsonProperty public String value;
  }

  @Test
  void findPropertyName_emptyJsonPropertyOnFieldUsesFieldName() throws Exception {
    Field field = FieldMarkedDiscovered.class.getField("value");
    assertThat(introspector.findPropertyName(null, field)).isEqualTo("value");
  }

  static class NoAnnotation {
    public String value;

    public String getValue() {
      return value;
    }
  }

  @Test
  void findPropertyName_returnsNullWhenUnannotated() throws Exception {
    Method getter = NoAnnotation.class.getMethod("getValue");
    Field field = NoAnnotation.class.getField("value");
    assertThat(introspector.findPropertyName(getter, field)).isNull();
    assertThat(introspector.findPropertyName(null, null)).isNull();
  }

  // --- isIgnored ---

  static class IgnoredGetter {
    @JsonIgnore
    public String getValue() {
      return null;
    }
  }

  static class IgnoredField {
    @JsonIgnore public String value;
  }

  @Test
  void isIgnored_detectsAnnotationOnGetterOrField() throws Exception {
    assertThat(introspector.isIgnored(IgnoredGetter.class.getMethod("getValue"), null)).isTrue();
    assertThat(introspector.isIgnored(null, IgnoredField.class.getField("value"))).isTrue();
    assertThat(introspector.isIgnored(NoAnnotation.class.getMethod("getValue"), null)).isFalse();
    assertThat(introspector.isIgnored(null, null)).isFalse();
  }

  // --- isNonNullInclude ---

  static class NonNullGetter {
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String getValue() {
      return null;
    }
  }

  static class NonNullField {
    @JsonInclude(JsonInclude.Include.NON_NULL) public String value;
  }

  static class AlwaysIncludedGetter {
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public String getValue() {
      return null;
    }
  }

  @Test
  void isNonNullInclude_truthOnlyWhenNonNullPolicySet() throws Exception {
    assertThat(introspector.isNonNullInclude(NonNullGetter.class.getMethod("getValue"), null))
        .isTrue();
    assertThat(introspector.isNonNullInclude(null, NonNullField.class.getField("value"))).isTrue();
    assertThat(
            introspector.isNonNullInclude(
                AlwaysIncludedGetter.class.getMethod("getValue"), null))
        .isFalse();
    assertThat(introspector.isNonNullInclude(null, null)).isFalse();
  }

  // --- findCreatorParamName ---

  static class WithCreator {
    public WithCreator(@JsonProperty("foo") String foo, String bar) {}
  }

  @Test
  void findCreatorParamName_readsAnnotationOnParameter() throws NoSuchMethodException {
    Constructor<?> ctor = WithCreator.class.getConstructor(String.class, String.class);
    Parameter[] params = ctor.getParameters();
    assertThat(introspector.findCreatorParamName(params[0])).isEqualTo("foo");
    // Unannotated parameter -> null.
    assertThat(introspector.findCreatorParamName(params[1])).isNull();
  }

  // --- isJsonCreator (constructor) ---

  static class CreatorPropsCtor {
    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public CreatorPropsCtor(@JsonProperty("x") String x) {}
  }

  static class CreatorDelegatingCtor {
    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public CreatorDelegatingCtor(String x) {}
  }

  static class NoCreatorCtor {
    public NoCreatorCtor(String x) {}
  }

  @Test
  void isJsonCreator_constructor_acceptsPropertiesNotDelegating() throws Exception {
    assertThat(
            introspector.isJsonCreator(
                CreatorPropsCtor.class.getConstructor(String.class)))
        .isTrue();
    assertThat(
            introspector.isJsonCreator(
                CreatorDelegatingCtor.class.getConstructor(String.class)))
        .isFalse();
    assertThat(introspector.isJsonCreator(NoCreatorCtor.class.getConstructor(String.class)))
        .isFalse();
  }

  // --- isJsonCreator (method) ---

  static class CreatorFactory {
    @JsonCreator
    public static CreatorFactory of(@JsonProperty("v") String v) {
      return new CreatorFactory();
    }

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static CreatorFactory delegating(String v) {
      return new CreatorFactory();
    }

    public static CreatorFactory plain(String v) {
      return new CreatorFactory();
    }
  }

  @Test
  void isJsonCreator_method_acceptsPropertiesNotDelegating() throws NoSuchMethodException {
    assertThat(introspector.isJsonCreator(CreatorFactory.class.getMethod("of", String.class)))
        .isTrue();
    assertThat(
            introspector.isJsonCreator(
                CreatorFactory.class.getMethod("delegating", String.class)))
        .isFalse();
    assertThat(introspector.isJsonCreator(CreatorFactory.class.getMethod("plain", String.class)))
        .isFalse();
  }

  // --- findFieldVisibility ---

  @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
  static class AnyVisibility {}

  @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.NON_PRIVATE)
  static class NonPrivateVisibility {}

  @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.PROTECTED_AND_PUBLIC)
  static class ProtectedAndPublicVisibility {}

  @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.NONE)
  static class NoneVisibility {}

  @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.PUBLIC_ONLY)
  static class PublicOnlyVisibility {}

  @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.DEFAULT)
  static class DefaultVisibility {}

  static class Unannotated {}

  @Test
  void findFieldVisibility_mapsAllJacksonLevels() {
    assertThat(introspector.findFieldVisibility(AnyVisibility.class)).isEqualTo(Visibility.ANY);
    assertThat(introspector.findFieldVisibility(NonPrivateVisibility.class))
        .isEqualTo(Visibility.NON_PRIVATE);
    assertThat(introspector.findFieldVisibility(ProtectedAndPublicVisibility.class))
        .isEqualTo(Visibility.PROTECTED_AND_PUBLIC);
    assertThat(introspector.findFieldVisibility(NoneVisibility.class)).isEqualTo(Visibility.NONE);
    assertThat(introspector.findFieldVisibility(PublicOnlyVisibility.class))
        .isEqualTo(Visibility.PUBLIC_ONLY);
    // DEFAULT should yield null so the caller falls back to JavaBean conventions.
    assertThat(introspector.findFieldVisibility(DefaultVisibility.class)).isNull();
    // Class without @JsonAutoDetect.
    assertThat(introspector.findFieldVisibility(Unannotated.class)).isNull();
  }

  // --- isJsonValue ---

  static class WithJsonValue {
    @JsonValue
    public String single() {
      return "x";
    }

    public String other() {
      return "y";
    }
  }

  @Test
  void isJsonValue_detectsAnnotation() throws NoSuchMethodException {
    assertThat(introspector.isJsonValue(WithJsonValue.class.getMethod("single"))).isTrue();
    assertThat(introspector.isJsonValue(WithJsonValue.class.getMethod("other"))).isFalse();
  }
}
