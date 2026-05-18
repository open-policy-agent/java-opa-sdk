package io.github.open_policy_agent.opa.ir.policy.types;

import java.util.List;
import java.util.Objects;
import io.github.open_policy_agent.opa.ir.ObjectTypeDynamicProperty;
import io.github.open_policy_agent.opa.ir.ObjectTypeStaticProperty;

public class ObjectType implements Type {
  public static final String TypeMarker = "object";

  // Fields are named "statics" / "dynamics" because "static" / "dynamic" are Java reserved words.
  // Accessors are named getStatic / setStatic / getDynamic / setDynamic so Jackson auto-detects
  // the JSON "static" and "dynamic" keys.
  private List<ObjectTypeStaticProperty> statics;

  private ObjectTypeDynamicProperty dynamics;

  public ObjectType() {}

  public ObjectType(List<ObjectTypeStaticProperty> statics, ObjectTypeDynamicProperty dynamics) {
    this.statics = statics;
    this.dynamics = dynamics;
  }

  @Override
  public String typeMarker() {
    return TypeMarker;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    ObjectType that = (ObjectType) o;

    if (!Objects.equals(statics, that.statics)) return false;
    return Objects.equals(dynamics, that.dynamics);
  }

  @Override
  public int hashCode() {
    int result = statics != null ? statics.hashCode() : 0;
    result = 31 * result + (dynamics != null ? dynamics.hashCode() : 0);
    return result;
  }

  public List<ObjectTypeStaticProperty> getStatic() {
    return statics;
  }

  public void setStatic(List<ObjectTypeStaticProperty> statics) {
    this.statics = statics;
  }

  public ObjectTypeDynamicProperty getDynamic() {
    return dynamics;
  }

  public void setDynamic(ObjectTypeDynamicProperty dynamics) {
    this.dynamics = dynamics;
  }

  @Override
  public String toString() {
    return "ObjectType{" + "statics=" + statics + ", dynamics=" + dynamics + '}';
  }
}
