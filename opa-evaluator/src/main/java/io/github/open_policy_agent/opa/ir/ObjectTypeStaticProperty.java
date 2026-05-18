package io.github.open_policy_agent.opa.ir;

import java.util.Objects;
import io.github.open_policy_agent.opa.ir.policy.types.Type;

public class ObjectTypeStaticProperty {
  private Object key;

  private Type value;

  public ObjectTypeStaticProperty() {}

  public ObjectTypeStaticProperty(Object key, Type value) {
    this.key = key;
    this.value = value;
  }

  public Object getKey() {
    return key;
  }

  public void setKey(Object key) {
    this.key = key;
  }

  public Type getValue() {
    return value;
  }

  public void setValue(Type value) {
    this.value = value;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    ObjectTypeStaticProperty that = (ObjectTypeStaticProperty) o;

    if (!Objects.equals(key, that.key)) return false;
    return Objects.equals(value, that.value);
  }

  @Override
  public int hashCode() {
    int result = key != null ? key.hashCode() : 0;
    result = 31 * result + (value != null ? value.hashCode() : 0);
    return result;
  }

  @Override
  public String toString() {
    return "ObjectTypeStaticProperty{" + "key=" + key + ", value=" + value + '}';
  }
}
