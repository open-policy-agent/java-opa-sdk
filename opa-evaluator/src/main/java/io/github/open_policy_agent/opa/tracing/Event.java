package io.github.open_policy_agent.opa.tracing;

import java.util.Objects;
import io.github.open_policy_agent.opa.ir.Location;

public class Event {
  private final Operation op;
  private final Location location;

  public Event(Operation op, Location location) {
    this.op = op;
    this.location = location;
  }

  public Operation getOp() {
    return op;
  }

  public Location getLocation() {
    return location;
  }

  @Override
  public boolean equals(Object o) {
      if (this == o) {
          return true;
      }
      if (o == null || getClass() != o.getClass()) {
          return false;
      }

    Event event = (Event) o;

      if (op != event.op) {
          return false;
      }
    return Objects.equals(location, event.location);
  }

  @Override
  public String toString() {
    if (location == null) {
      return "";
    }
    return "op="
        + op
        + ",file="
        + location.getFile()
        + ",row="
        + location.getRow()
        + ",col="
        + location.getCol();
  }

  @Override
  public int hashCode() {
    int result = op != null ? op.hashCode() : 0;
    result = 31 * result + (location != null ? location.hashCode() : 0);
    return result;
  }
}
