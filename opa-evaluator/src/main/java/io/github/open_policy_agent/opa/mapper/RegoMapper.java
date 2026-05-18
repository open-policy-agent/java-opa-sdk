package io.github.open_policy_agent.opa.mapper;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import io.github.open_policy_agent.opa.ast.types.RegoArray;
import io.github.open_policy_agent.opa.ast.types.RegoBoolean;
import io.github.open_policy_agent.opa.ast.types.RegoBigInt;
import io.github.open_policy_agent.opa.ast.types.RegoDecimal;
import io.github.open_policy_agent.opa.ast.types.RegoInt32;
import io.github.open_policy_agent.opa.ast.types.RegoNull;
import io.github.open_policy_agent.opa.ast.types.RegoNumber;
import io.github.open_policy_agent.opa.ast.types.RegoObject;
import io.github.open_policy_agent.opa.ast.types.RegoSet;
import io.github.open_policy_agent.opa.ast.types.RegoString;
import io.github.open_policy_agent.opa.ast.types.RegoValue;

/**
 * Converts POJOs to and from {@link RegoValue} instances directly via cached reflection, bypassing
 * intermediate Jackson {@code JsonNode} or {@code Map} representations.
 *
 * <p>This mapper follows JavaBean conventions for property discovery and honors Jackson annotations:
 *
 * <ul>
 *   <li>{@code @JsonProperty("name")} — override the RegoObject key name
 *   <li>{@code @JsonIgnore} — skip a property
 *   <li>{@code @JsonInclude(NON_NULL)} — omit null-valued properties
 *   <li>{@code @JsonValue} — on enum methods, controls the serialized value
 * </ul>
 *
 * <p>Instances are thread-safe and designed to be reused. The per-class reflection metadata is
 * cached in a {@link ConcurrentHashMap} so the first conversion pays the introspection cost and all
 * subsequent conversions for the same class skip reflection entirely.
 *
 * <p><b>Example usage:</b>
 *
 * <pre>{@code
 * RegoMapper mapper = new RegoMapper();
 *
 * // Forward: POJO → RegoObject
 * MyInput input = new MyInput();
 * input.setUser("alice");
 * RegoObject regoInput = mapper.toRegoObject(input);
 *
 * // Reverse: RegoValue → POJO
 * MyResult result = mapper.fromRegoValue(regoValue, MyResult.class);
 * }</pre>
 */
public class RegoMapper {

  private final ConcurrentHashMap<Class<?>, ClassInfo> cache = new ConcurrentHashMap<>();

  /** Convert any Java value to the corresponding {@link RegoValue}. */
  public RegoValue toRegoValue(Object obj) {
    if (obj == null) {
      return RegoNull.INSTANCE;
    }
    if (obj instanceof RegoValue) {
      return (RegoValue) obj;
    }

    // Primitives and boxed types
    if (obj instanceof String) {
      return new RegoString((String) obj);
    }
    if (obj instanceof Boolean) {
      return RegoBoolean.of((Boolean) obj);
    }
    if (obj instanceof Integer) {
      return RegoInt32.of((Integer) obj);
    }
    if (obj instanceof Long) {
      return new RegoBigInt((Long) obj);
    }
    if (obj instanceof BigInteger) {
      return new RegoBigInt((BigInteger) obj);
    }
    if (obj instanceof Double) {
      return new RegoDecimal((Double) obj);
    }
    if (obj instanceof Float) {
      return new RegoDecimal(((Float) obj).doubleValue());
    }
    if (obj instanceof BigDecimal) {
      return new RegoDecimal(((BigDecimal) obj).doubleValue());
    }

    // Enum
    if (obj instanceof Enum) {
      return enumToRegoValue((Enum<?>) obj);
    }

    // Map
    if (obj instanceof Map) {
      return mapToRegoObject((Map<?, ?>) obj);
    }

    // Set (before Collection since Set extends Collection)
    if (obj instanceof Set) {
      return setToRegoSet((Set<?>) obj);
    }

    // Collection (List, etc.)
    if (obj instanceof Collection) {
      return collectionToRegoArray((Collection<?>) obj);
    }

    // Arrays
    if (obj.getClass().isArray()) {
      return arrayToRegoArray(obj);
    }

    // JDK and platform types (java.*, javax.*) are not user POJOs — serialize via toString().
    // This handles URI, URL, Path, InetAddress, etc. which have getters but should be
    // treated as scalar values, not decomposed into their internal fields.
    String className = obj.getClass().getName();
    if (className.startsWith("java.") || className.startsWith("javax.")) {
      return new RegoString(obj.toString());
    }

    // POJO
    return pojoToRegoObject(obj);
  }

  /**
   * Convert a POJO or Map to a {@link RegoObject}. Throws if the input maps to a primitive
   * RegoValue.
   */
  public RegoObject toRegoObject(Object obj) {
    if (obj == null) {
      throw new RegoMappingException("Cannot convert null to RegoObject");
    }
    RegoValue value = toRegoValue(obj);
    if (value instanceof RegoObject) {
      return (RegoObject) value;
    }
    throw new RegoMappingException(
        "Expected RegoObject but got " + value.getTypeName()
            + " for type " + obj.getClass().getName());
  }

  /**
   * Convert a {@link RegoValue} to the specified Java type.
   *
   * <p>For POJOs, the target class must have either an accessible no-arg constructor or a
   * {@code @JsonCreator}-annotated constructor (or static factory method) with
   * {@code @JsonProperty}-annotated parameters. When a {@code @JsonCreator} is present, it takes
   * precedence. Properties not covered by constructor parameters are set via matching setters.
   */
  @SuppressWarnings("unchecked")
  public <T> T fromRegoValue(RegoValue value, Class<T> type) {
    return (T) fromRegoValueInternal(value, type);
  }

  // --- Forward conversion helpers ---

  private RegoValue enumToRegoValue(Enum<?> e) {
    // Check for an @JsonValue (or equivalent) method on the enum class via the introspector
    AnnotationIntrospector introspector = AnnotationIntrospectors.get();
    for (Method method : e.getClass().getDeclaredMethods()) {
      if (introspector.isJsonValue(method) && method.getParameterCount() == 0) {
        try {
          method.setAccessible(true);
          Object result = method.invoke(e);
          return toRegoValue(result);
        } catch (Exception ex) {
          throw new RegoMappingException(
              "Failed to invoke @JsonValue method on enum " + e.getClass().getName(), ex);
        }
      }
    }
    return new RegoString(e.name());
  }

  private RegoObject mapToRegoObject(Map<?, ?> map) {
    RegoObject obj = new RegoObject();
    for (Map.Entry<?, ?> entry : map.entrySet()) {
      String key = String.valueOf(entry.getKey());
      obj.setProp(new RegoString(key), toRegoValue(entry.getValue()));
    }
    return obj;
  }

  private RegoSet setToRegoSet(Set<?> set) {
    RegoSet regoSet = new RegoSet(false);
    for (Object item : set) {
      regoSet.addValue(toRegoValue(item));
    }
    return regoSet;
  }

  private RegoArray collectionToRegoArray(Collection<?> coll) {
    RegoArray array = new RegoArray(coll.size());
    for (Object item : coll) {
      array.addValue(toRegoValue(item));
    }
    return array;
  }

  private RegoArray arrayToRegoArray(Object array) {
    int len = Array.getLength(array);
    RegoArray regoArray = new RegoArray(len);
    for (int i = 0; i < len; i++) {
      regoArray.addValue(toRegoValue(Array.get(array, i)));
    }
    return regoArray;
  }

  private RegoObject pojoToRegoObject(Object pojo) {
    ClassInfo info = getClassInfo(pojo.getClass());
    RegoObject obj = new RegoObject();

    for (PropertyInfo prop : info.getProperties()) {
      if (!prop.isReadable()) {
        continue;
      }
      Object value;
      try {
        value = prop.readValue(pojo);
      } catch (Exception e) {
        throw new RegoMappingException(
            "Failed to read property '" + prop.getJsonName()
                + "' from " + pojo.getClass().getName(),
            e);
      }

      // Honor @JsonInclude(NON_NULL)
      if (value == null && prop.isIncludeNonNull()) {
        continue;
      }

      obj.setProp(new RegoString(prop.getJsonName()), toRegoValue(value));
    }
    return obj;
  }

  // --- Reverse conversion ---

  @SuppressWarnings("unchecked")
  private Object fromRegoValueInternal(RegoValue value, Type targetType) {
    if (value == null || value instanceof RegoNull) {
      return null;
    }

    Class<?> rawClass = getRawClass(targetType);

    // If target is RegoValue or a subtype, return as-is
    if (RegoValue.class.isAssignableFrom(rawClass)) {
      return value;
    }

    // If target is Object (raw generic), use nativeValue() for natural Java types
    if (rawClass == Object.class) {
      return value.nativeValue();
    }

    // String
    if (rawClass == String.class) {
      if (value instanceof RegoString) {
        return ((RegoString) value).getValue();
      }
      // Coerce other types to string
      return value.nativeValue() != null ? value.nativeValue().toString() : null;
    }

    // Boolean
    if (rawClass == boolean.class || rawClass == Boolean.class) {
      if (value instanceof RegoBoolean) {
        return ((RegoBoolean) value).getValue();
      }
      throw typeMismatch(value, rawClass);
    }

    // Integer
    if (rawClass == int.class || rawClass == Integer.class) {
      return toInt(value, rawClass);
    }

    // Long
    if (rawClass == long.class || rawClass == Long.class) {
      return toLong(value, rawClass);
    }

    // Double
    if (rawClass == double.class || rawClass == Double.class) {
      return toDouble(value, rawClass);
    }

    // Float
    if (rawClass == float.class || rawClass == Float.class) {
      return toFloat(value, rawClass);
    }

    // BigInteger
    if (rawClass == BigInteger.class) {
      if (value instanceof RegoNumber) {
        return ((RegoNumber) value).getBigIntValue();
      }
      throw typeMismatch(value, rawClass);
    }

    // BigDecimal
    if (rawClass == BigDecimal.class) {
      if (value instanceof RegoNumber) {
        return BigDecimal.valueOf(((RegoNumber) value).getDecimalValue());
      }
      throw typeMismatch(value, rawClass);
    }

    // Enum
    if (rawClass.isEnum()) {
      return toEnum(value, (Class<? extends Enum>) rawClass);
    }

    // Map
    if (Map.class.isAssignableFrom(rawClass)) {
      if (value instanceof RegoObject) {
        return regoObjectToMap((RegoObject) value, targetType);
      }
      throw typeMismatch(value, rawClass);
    }

    // Set
    if (Set.class.isAssignableFrom(rawClass)) {
      if (value instanceof RegoSet) {
        return regoSetToSet((RegoSet) value, targetType);
      }
      if (value instanceof RegoArray) {
        return regoArrayToSet((RegoArray) value, targetType);
      }
      throw typeMismatch(value, rawClass);
    }

    // List / Collection
    if (List.class.isAssignableFrom(rawClass) || Collection.class.isAssignableFrom(rawClass)) {
      if (value instanceof RegoArray) {
        return regoArrayToList((RegoArray) value, targetType);
      }
      throw typeMismatch(value, rawClass);
    }

    // Array
    if (rawClass.isArray()) {
      if (value instanceof RegoArray) {
        return regoArrayToJavaArray((RegoArray) value, rawClass.getComponentType());
      }
      throw typeMismatch(value, rawClass);
    }

    // POJO
    if (value instanceof RegoObject) {
      return regoObjectToPojo((RegoObject) value, rawClass);
    }

    throw typeMismatch(value, rawClass);
  }

  private int toInt(RegoValue value, Class<?> target) {
    if (value instanceof RegoInt32) {
      return ((RegoInt32) value).getValue();
    }
    if (value instanceof RegoBigInt) {
      long l = ((RegoBigInt) value).getValue().longValueExact();
      if (l < Integer.MIN_VALUE || l > Integer.MAX_VALUE) {
        throw new RegoMappingException("Value " + l + " out of range for int");
      }
      return (int) l;
    }
    if (value instanceof RegoDecimal) {
      double d = ((RegoDecimal) value).getValue();
      if (d != Math.floor(d) || d < Integer.MIN_VALUE || d > Integer.MAX_VALUE) {
        throw new RegoMappingException("Value " + d + " cannot be converted to int");
      }
      return (int) d;
    }
    throw typeMismatch(value, target);
  }

  private long toLong(RegoValue value, Class<?> target) {
    if (value instanceof RegoInt32) {
      return ((RegoInt32) value).getValue().longValue();
    }
    if (value instanceof RegoBigInt) {
      return ((RegoBigInt) value).getValue().longValueExact();
    }
    if (value instanceof RegoDecimal) {
      double d = ((RegoDecimal) value).getValue();
      if (d != Math.floor(d) || d < Long.MIN_VALUE || d > Long.MAX_VALUE) {
        throw new RegoMappingException("Value " + d + " cannot be converted to long");
      }
      return (long) d;
    }
    throw typeMismatch(value, target);
  }

  private double toDouble(RegoValue value, Class<?> target) {
    if (value instanceof RegoNumber) {
      return ((RegoNumber) value).getDecimalValue();
    }
    throw typeMismatch(value, target);
  }

  private float toFloat(RegoValue value, Class<?> target) {
    if (value instanceof RegoNumber) {
      return ((RegoNumber) value).getDecimalValue().floatValue();
    }
    throw typeMismatch(value, target);
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private Object toEnum(RegoValue value, Class<? extends Enum> enumClass) {
    if (value instanceof RegoString) {
      String name = ((RegoString) value).getValue();
      try {
        return Enum.valueOf(enumClass, name);
      } catch (IllegalArgumentException e) {
        throw new RegoMappingException(
            "No enum constant " + enumClass.getName() + "." + name, e);
      }
    }
    throw typeMismatch(value, enumClass);
  }

  private Map<String, Object> regoObjectToMap(RegoObject obj, Type targetType) {
    Type valueType = getTypeArgument(targetType, 1);
    Map<String, Object> map = new LinkedHashMap<>();
    for (Map.Entry<RegoValue, RegoValue> entry : obj.getProperties().entrySet()) {
      String key;
      if (entry.getKey() instanceof RegoString) {
        key = ((RegoString) entry.getKey()).getValue();
      } else {
        key = entry.getKey().nativeValue().toString();
      }
      map.put(key, fromRegoValueInternal(entry.getValue(), valueType));
    }
    return map;
  }

  private List<Object> regoArrayToList(RegoArray array, Type targetType) {
    Type elementType = getTypeArgument(targetType, 0);
    List<Object> list = new ArrayList<>(array.length());
    for (RegoValue item : array.getValue()) {
      list.add(fromRegoValueInternal(item, elementType));
    }
    return list;
  }

  private Set<Object> regoSetToSet(RegoSet set, Type targetType) {
    Type elementType = getTypeArgument(targetType, 0);
    Set<Object> result = new LinkedHashSet<>();
    for (RegoValue item : set.getValue()) {
      result.add(fromRegoValueInternal(item, elementType));
    }
    return result;
  }

  private Set<Object> regoArrayToSet(RegoArray array, Type targetType) {
    Type elementType = getTypeArgument(targetType, 0);
    Set<Object> result = new LinkedHashSet<>();
    for (RegoValue item : array.getValue()) {
      result.add(fromRegoValueInternal(item, elementType));
    }
    return result;
  }

  private Object regoArrayToJavaArray(RegoArray array, Class<?> componentType) {
    List<RegoValue> values = array.getValue();
    Object javaArray = Array.newInstance(componentType, values.size());
    for (int i = 0; i < values.size(); i++) {
      Array.set(javaArray, i, fromRegoValueInternal(values.get(i), componentType));
    }
    return javaArray;
  }

  private Object regoObjectToPojo(RegoObject obj, Class<?> pojoClass) {
    ClassInfo info = getClassInfo(pojoClass);

    Object pojo;
    Set<String> ctorPropertyNames;

    if (info.getCreatorInfo() != null) {
      // @JsonCreator path (constructor or static factory)
      CreatorInfo creator = info.getCreatorInfo();
      List<CreatorInfo.CreatorParam> params = creator.getParams();
      Object[] args = new Object[params.size()];
      ctorPropertyNames = new HashSet<>();

      for (int i = 0; i < params.size(); i++) {
        CreatorInfo.CreatorParam param = params.get(i);
        ctorPropertyNames.add(param.getJsonName());
        RegoValue propValue = obj.getProperty(param.getJsonName());
        if (propValue == null || propValue instanceof RegoNull) {
          args[i] = defaultForType(param.getRawType());
        } else {
          args[i] = fromRegoValueInternal(propValue, param.getGenericType());
        }
      }

      try {
        pojo = creator.newInstance(args);
      } catch (RegoMappingException e) {
        throw e;
      } catch (Exception e) {
        throw new RegoMappingException(
            "Failed to instantiate " + pojoClass.getName() + " via @JsonCreator", e);
      }
    } else if (info.getNoArgConstructor() != null) {
      // No-arg constructor path
      ctorPropertyNames = Collections.emptySet();
      try {
        pojo = info.getNoArgConstructor().newInstance();
      } catch (Exception e) {
        throw new RegoMappingException("Failed to instantiate " + pojoClass.getName(), e);
      }
    } else {
      throw new RegoMappingException(
          "Class "
              + pojoClass.getName()
              + " has no accessible no-arg constructor or @JsonCreator");
    }

    // Apply setters/fields for remaining properties
    for (PropertyInfo prop : info.getProperties()) {
      if (!prop.isWritable()) {
        continue;
      }
      if (ctorPropertyNames.contains(prop.getJsonName())) {
        continue;
      }
      RegoValue propValue = obj.getProperty(prop.getJsonName());
      if (propValue == null) {
        continue;
      }
      try {
        Object javaValue = fromRegoValueInternal(propValue, prop.getGenericType());
        prop.writeValue(pojo, javaValue);
      } catch (RegoMappingException e) {
        throw e;
      } catch (Exception e) {
        throw new RegoMappingException(
            "Failed to set property '" + prop.getJsonName() + "' on " + pojoClass.getName(), e);
      }
    }
    return pojo;
  }

  private static Object defaultForType(Class<?> type) {
    if (type == boolean.class) return false;
    if (type == int.class) return 0;
    if (type == long.class) return 0L;
    if (type == double.class) return 0.0;
    if (type == float.class) return 0.0f;
    if (type == byte.class) return (byte) 0;
    if (type == short.class) return (short) 0;
    if (type == char.class) return '\0';
    return null;
  }

  // --- Utility methods ---

  private ClassInfo getClassInfo(Class<?> clazz) {
    return cache.computeIfAbsent(clazz, ClassInfo::buildFor);
  }

  private static Class<?> getRawClass(Type type) {
    if (type instanceof Class) {
      return (Class<?>) type;
    }
    if (type instanceof ParameterizedType) {
      return (Class<?>) ((ParameterizedType) type).getRawType();
    }
    return Object.class;
  }

  /**
   * Get the type argument at the given index from a parameterized type. Returns Object.class if the
   * type is not parameterized or the index is out of bounds.
   */
  private static Type getTypeArgument(Type type, int index) {
    if (type instanceof ParameterizedType) {
      Type[] args = ((ParameterizedType) type).getActualTypeArguments();
      if (index < args.length) {
        return args[index];
      }
    }
    return Object.class;
  }

  private static RegoMappingException typeMismatch(RegoValue value, Class<?> target) {
    return new RegoMappingException(
        "Cannot convert " + value.getTypeName() + " to " + target.getName());
  }
}
