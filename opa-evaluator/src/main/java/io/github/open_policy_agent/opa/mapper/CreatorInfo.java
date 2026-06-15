package io.github.open_policy_agent.opa.mapper;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.List;

/**
 * Metadata for an annotated creator constructor or static factory method: the callable itself plus
 * the ordered list of parameter names and types. Annotation-driven name resolution goes through
 * the {@link AnnotationIntrospector} SPI so this class doesn't depend on a specific JSON library.
 */
final class CreatorInfo {
  private final Constructor<?> constructor;
  private final Method factoryMethod;
  private final List<CreatorParam> params;

  private CreatorInfo(
      Constructor<?> constructor, Method factoryMethod, List<CreatorParam> params) {
    this.constructor = constructor;
    this.factoryMethod = factoryMethod;
    this.params = Collections.unmodifiableList(params);
  }

  static CreatorInfo forConstructor(Constructor<?> constructor, List<CreatorParam> params) {
    return new CreatorInfo(constructor, null, params);
  }

  static CreatorInfo forFactory(Method factoryMethod, List<CreatorParam> params) {
    return new CreatorInfo(null, factoryMethod, params);
  }

  boolean isFactoryMethod() {
    return factoryMethod != null;
  }

  Constructor<?> getConstructor() {
    return constructor;
  }

  Method getFactoryMethod() {
    return factoryMethod;
  }

  List<CreatorParam> getParams() {
    return params;
  }

  Object newInstance(Object[] args) throws ReflectiveOperationException {
    if (factoryMethod != null) {
      return factoryMethod.invoke(null, args);
    }
    return constructor.newInstance(args);
  }

  static final class CreatorParam {
    private final String jsonName;
    private final Class<?> rawType;
    private final Type genericType;

    CreatorParam(String jsonName, Class<?> rawType, Type genericType) {
      this.jsonName = jsonName;
      this.rawType = rawType;
      this.genericType = genericType;
    }

    String getJsonName() {
      return jsonName;
    }

    Class<?> getRawType() {
      return rawType;
    }

    Type getGenericType() {
      return genericType;
    }

    /**
     * Resolve the JSON property name from the active {@link AnnotationIntrospector}. Returns null
     * if no annotated name is present.
     */
    static CreatorParam fromParameter(Parameter param) {
      String name = AnnotationIntrospectors.get().findCreatorParamName(param);
      if (name == null || name.isEmpty()) {
        return null;
      }
      return new CreatorParam(name, param.getType(), param.getParameterizedType());
    }
  }
}
