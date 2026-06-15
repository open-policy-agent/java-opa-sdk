package io.github.open_policy_agent.opa.ir.policy.types;

import java.util.List;
import java.util.Objects;

public class ArrayType implements Type {
  public static final String TypeMarker = "array";

  // Field is named "staticItems" because "static" is a Java reserved word.
  // Accessors are named getStatic / setStatic so Jackson auto-detects the JSON "static" key.
  private List<Type> staticItems;

  private Type dynamic;

  public ArrayType() {}

  public ArrayType(List<Type> staticItems, Type dynamic) {
    this.staticItems = staticItems;
    this.dynamic = dynamic;
  }

  @Override
  public String typeMarker() {
    return TypeMarker;
  }

  public List<Type> getStatic() {
    return staticItems;
  }

  public void setStatic(List<Type> staticItems) {
    this.staticItems = staticItems;
  }

  public Type getDynamic() {
    return dynamic;
  }

  public void setDynamic(Type dynamic) {
    this.dynamic = dynamic;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    ArrayType arrayType = (ArrayType) o;

    if (!Objects.equals(staticItems, arrayType.staticItems)) return false;
    return Objects.equals(dynamic, arrayType.dynamic);
  }

  @Override
  public int hashCode() {
    int result = staticItems != null ? staticItems.hashCode() : 0;
    result = 31 * result + (dynamic != null ? dynamic.hashCode() : 0);
    return result;
  }

  @Override
  public String toString() {
    return "ArrayType{" + "staticItems=" + staticItems + ", dynamic=" + dynamic + '}';
  }
}
