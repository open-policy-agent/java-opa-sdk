package io.github.open_policy_agent.opa.mapper;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Cached introspection metadata for a JavaBean class. Built once per class, then reused for all
 * subsequent conversions.
 *
 * <p>Property discovery follows JavaBean conventions:
 *
 * <ol>
 *   <li>JavaBean getters ({@code getX()}/{@code isX()}) — always discovered
 *   <li>Public fields — discovered if no getter exists for the same property
 *   <li>Annotation-tagged fields (per the active {@link AnnotationIntrospector}) — discovered
 *       regardless of visibility
 * </ol>
 *
 * <p>Annotation-driven overrides (property name, ignore, NON_NULL inclusion, creator, visibility)
 * are resolved through the {@link AnnotationIntrospector} SPI so this class has no direct
 * dependency on a specific JSON library.
 */
final class ClassInfo {
  private static final AnnotationIntrospector INTROSPECTOR = AnnotationIntrospectors.get();

  private final List<PropertyInfo> properties;
  private final Constructor<?> noArgConstructor;
  private final CreatorInfo creatorInfo;

  private ClassInfo(
      List<PropertyInfo> properties, Constructor<?> noArgConstructor, CreatorInfo creatorInfo) {
    this.properties = Collections.unmodifiableList(properties);
    this.noArgConstructor = noArgConstructor;
    this.creatorInfo = creatorInfo;
  }

  List<PropertyInfo> getProperties() {
    return properties;
  }

  Constructor<?> getNoArgConstructor() {
    return noArgConstructor;
  }

  CreatorInfo getCreatorInfo() {
    return creatorInfo;
  }

  /**
   * Introspect a class and build its ClassInfo. Scans for JavaBean getters first, then fields that
   * weren't already discovered via getters.
   */
  static ClassInfo buildFor(Class<?> clazz) {
    Constructor<?> ctor = findNoArgConstructor(clazz);
    CreatorInfo creatorInfo = findJsonCreator(clazz);

    List<PropertyInfo> props = new ArrayList<>();
    Set<String> discoveredNames = new HashSet<>();
    Set<String> claimedFieldNames = new HashSet<>(); // fields claimed by getters (for dedup)

    // Phase 1: Discover properties via JavaBean getters (getX/isX)
    for (Method method : clazz.getMethods()) {
      if (!isGetter(method)) {
        continue;
      }

      String propertyName = derivePropertyName(method);
      Field backingField = findBackingField(clazz, propertyName);

      // For boolean is* getters, also look for the is-prefixed backing field.
      // e.g., isTrusted() derives "trusted" but the field is named "isTrusted".
      if (backingField == null && method.getName().startsWith("is")) {
        backingField = findBackingField(clazz, method.getName());
      }

      // Track claimed fields so Phase 2 doesn't re-discover them under a different name
      if (backingField != null) {
        claimedFieldNames.add(backingField.getName());
      }

      // Check if the property is annotated as ignored (via getter or backing field)
      if (INTROSPECTOR.isIgnored(method, backingField)) {
        continue;
      }

      // Resolve JSON name: annotation override beats derived name
      String jsonName = resolveJsonName(method, backingField, propertyName);

      // Check if NON_NULL inclusion is annotated on getter or field
      boolean includeNonNull = INTROSPECTOR.isNonNullInclude(method, backingField);

      Class<?> rawType = method.getReturnType();
      Type genericType = method.getGenericReturnType();

      Method setter = findSetter(clazz, propertyName, rawType);
      setAccessibleQuietly(setter);
      setAccessibleQuietly(method);
      if (backingField != null && !setAccessibleQuietly(backingField)) {
        backingField = null; // can't access field (module system) — still use getter
      }

      props.add(
          new PropertyInfo(
              jsonName, method, setter, backingField, rawType, genericType, includeNonNull));
      discoveredNames.add(jsonName);
    }

    // Phase 2: Discover properties via fields not already found through getters.
    // Field visibility comes from the introspector when present; otherwise PUBLIC_ONLY.
    AnnotationIntrospector.Visibility fieldVisibility = resolveFieldVisibility(clazz);
    for (Field field : getAllFields(clazz)) {
      if (INTROSPECTOR.isIgnored(null, field)) {
        continue;
      }
      if (Modifier.isStatic(field.getModifiers())) {
        continue;
      }

      // Skip fields already claimed by a getter (avoids isTrusted field + isTrusted() getter dupe)
      if (claimedFieldNames.contains(field.getName())) {
        continue;
      }

      String annotatedName = INTROSPECTOR.findPropertyName(null, field);
      boolean hasAnnotatedName = annotatedName != null && !annotatedName.isEmpty();
      if (!hasAnnotatedName && !isFieldVisible(field, fieldVisibility)) {
        continue;
      }

      String jsonName = hasAnnotatedName ? annotatedName : field.getName();

      if (discoveredNames.contains(jsonName)) {
        continue;
      }

      boolean includeNonNull = INTROSPECTOR.isNonNullInclude(null, field);

      if (!setAccessibleQuietly(field)) {
        continue;
      }

      Method setter = findSetter(clazz, field.getName(), field.getType());
      setAccessibleQuietly(setter);

      props.add(
          new PropertyInfo(
              jsonName, null, setter, field, field.getType(), field.getGenericType(),
              includeNonNull));
      discoveredNames.add(jsonName);
    }

    return new ClassInfo(props, ctor, creatorInfo);
  }

  /** Resolve field visibility from the introspector, defaulting to PUBLIC_ONLY. */
  private static AnnotationIntrospector.Visibility resolveFieldVisibility(Class<?> clazz) {
    AnnotationIntrospector.Visibility v = INTROSPECTOR.findFieldVisibility(clazz);
    return v != null ? v : AnnotationIntrospector.Visibility.PUBLIC_ONLY;
  }

  /** Check if a field is visible under the given visibility level. */
  private static boolean isFieldVisible(Field field, AnnotationIntrospector.Visibility visibility) {
    switch (visibility) {
      case ANY:
        return true;
      case NON_PRIVATE:
        return !Modifier.isPrivate(field.getModifiers());
      case PROTECTED_AND_PUBLIC:
        return Modifier.isPublic(field.getModifiers())
            || Modifier.isProtected(field.getModifiers());
      case NONE:
        return false;
      case PUBLIC_ONLY:
      default:
        return Modifier.isPublic(field.getModifiers());
    }
  }

  /** Collect all declared fields from the class and its superclasses. */
  private static List<Field> getAllFields(Class<?> clazz) {
    List<Field> fields = new ArrayList<>();
    Class<?> current = clazz;
    while (current != null && current != Object.class) {
      for (Field f : current.getDeclaredFields()) {
        fields.add(f);
      }
      current = current.getSuperclass();
    }
    return fields;
  }

  private static boolean isGetter(Method method) {
    if (method.getParameterCount() != 0) {
      return false;
    }
    if (method.getReturnType() == void.class) {
      return false;
    }
    int mods = method.getModifiers();
    if (!Modifier.isPublic(mods) || Modifier.isStatic(mods)) {
      return false;
    }
    // Skip Object methods
    if (method.getDeclaringClass() == Object.class) {
      return false;
    }

    String name = method.getName();
    if (name.startsWith("get") && name.length() > 3) {
      return true;
    }
    if (name.startsWith("is")
        && name.length() > 2
        && (method.getReturnType() == boolean.class
            || method.getReturnType() == Boolean.class)) {
      return true;
    }
    return false;
  }

  /**
   * Derive property name from a getter method, matching Jackson's default (legacy) mangling.
   * Lowercases the entire leading uppercase run, stopping when a lowercase char is found.
   *
   * <p>Examples: {@code getName() → "name"}, {@code getURL() → "url"}, {@code isCKIdentityValid()
   * → "ckidentityValid"}, {@code isTrusted() → "trusted"}
   */
  private static String derivePropertyName(Method getter) {
    String name = getter.getName();
    int offset = name.startsWith("get") ? 3 : 2; // "get" or "is"
    int len = name.length();

    if (offset >= len) {
      return "";
    }

    // Single char after prefix — just lowercase it
    if (len - offset == 1) {
      return name.substring(offset).toLowerCase();
    }

    // Jackson's legacyManglePropertyName: lowercase the entire leading uppercase run
    char first = name.charAt(offset);
    char firstLower = Character.toLowerCase(first);
    if (first == firstLower) {
      // Already lowercase — return as-is
      return name.substring(offset);
    }

    StringBuilder sb = new StringBuilder(len - offset);
    sb.append(firstLower);
    for (int i = offset + 1; i < len; i++) {
      char c = name.charAt(i);
      char lower = Character.toLowerCase(c);
      if (c == lower) {
        // Hit a lowercase char — append the rest of the string unchanged and stop
        sb.append(name, i, len);
        break;
      }
      sb.append(lower);
    }
    return sb.toString();
  }

  private static Field findBackingField(Class<?> clazz, String propertyName) {
    Class<?> current = clazz;
    while (current != null && current != Object.class) {
      try {
        return current.getDeclaredField(propertyName);
      } catch (NoSuchFieldException e) {
        current = current.getSuperclass();
      }
    }
    return null;
  }

  private static String resolveJsonName(Method getter, Field field, String defaultName) {
    String name = INTROSPECTOR.findPropertyName(getter, field);
    return (name != null && !name.isEmpty()) ? name : defaultName;
  }

  private static Method findSetter(Class<?> clazz, String propertyName, Class<?> type) {
    String setterName = "set" + Character.toUpperCase(propertyName.charAt(0))
        + propertyName.substring(1);
    try {
      return clazz.getMethod(setterName, type);
    } catch (NoSuchMethodException e) {
      // Try with boxed/unboxed variant
      Class<?> alt = boxingAlternative(type);
      if (alt != null) {
        try {
          return clazz.getMethod(setterName, alt);
        } catch (NoSuchMethodException e2) {
          // no setter found
        }
      }
      return null;
    }
  }

  private static Class<?> boxingAlternative(Class<?> type) {
    if (type == boolean.class) return Boolean.class;
    if (type == Boolean.class) return boolean.class;
    if (type == int.class) return Integer.class;
    if (type == Integer.class) return int.class;
    if (type == long.class) return Long.class;
    if (type == Long.class) return long.class;
    if (type == double.class) return Double.class;
    if (type == Double.class) return double.class;
    if (type == float.class) return Float.class;
    if (type == Float.class) return float.class;
    return null;
  }

  /**
   * Scan for an annotated creator constructor or static factory method via the introspector. Each
   * parameter must have an annotated name. Returns null if none found.
   */
  private static CreatorInfo findJsonCreator(Class<?> clazz) {
    // Check constructors first
    for (Constructor<?> ctor : clazz.getDeclaredConstructors()) {
      if (!INTROSPECTOR.isJsonCreator(ctor)) {
        continue;
      }
      List<CreatorInfo.CreatorParam> params = resolveCreatorParams(ctor.getParameters());
      if (params != null && setAccessibleQuietly(ctor)) {
        return CreatorInfo.forConstructor(ctor, params);
      }
    }

    // Check static factory methods
    for (Method method : clazz.getDeclaredMethods()) {
      if (!INTROSPECTOR.isJsonCreator(method)) {
        continue;
      }
      if (!Modifier.isStatic(method.getModifiers())) {
        continue;
      }
      List<CreatorInfo.CreatorParam> params = resolveCreatorParams(method.getParameters());
      if (params != null && setAccessibleQuietly(method)) {
        return CreatorInfo.forFactory(method, params);
      }
    }

    return null;
  }

  /**
   * Resolve creator parameters. Returns null if any parameter lacks an annotated name.
   */
  private static List<CreatorInfo.CreatorParam> resolveCreatorParams(Parameter[] parameters) {
    List<CreatorInfo.CreatorParam> params = new ArrayList<>();
    for (Parameter param : parameters) {
      CreatorInfo.CreatorParam cp = CreatorInfo.CreatorParam.fromParameter(param);
      if (cp == null) {
        return null;
      }
      params.add(cp);
    }
    return params;
  }

  private static Constructor<?> findNoArgConstructor(Class<?> clazz) {
    try {
      Constructor<?> ctor = clazz.getDeclaredConstructor();
      ctor.setAccessible(true);
      return ctor;
    } catch (NoSuchMethodException | RuntimeException e) {
      return null;
    }
  }

  /**
   * Attempt to make an {@link java.lang.reflect.AccessibleObject} accessible. Returns true on
   * success, false if the module system blocks it. Accepts null safely.
   */
  private static boolean setAccessibleQuietly(java.lang.reflect.AccessibleObject obj) {
    if (obj == null) {
      return false;
    }
    try {
      obj.setAccessible(true);
      return true;
    } catch (RuntimeException e) {
      return false;
    }
  }
}
