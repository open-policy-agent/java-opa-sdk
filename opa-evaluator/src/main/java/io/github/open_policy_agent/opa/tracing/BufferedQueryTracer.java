package io.github.open_policy_agent.opa.tracing;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

public class BufferedQueryTracer implements QueryTracer {
  public interface Filter extends Function<Event, Boolean>{};

  private final List<Event> events;
  private final List<Filter> filters;

  public BufferedQueryTracer(Filter... filters) {
    this.events = new ArrayList<>();
    this.filters = Arrays.stream(filters).collect(Collectors.toList());
  }

  public void TraceEvent(Event event) {
    if (!filters.isEmpty()) {
      for (Filter f : filters) {
        if (!f.apply(event)) {
          return;
        }
      }
    }

    events.add(event);
  }

  public List<Event> getEvents() {
    return new ArrayList<>(events);
  }

  @Override
  public boolean equals(Object o) {
      if (this == o) {
          return true;
      }
      if (o == null || getClass() != o.getClass()) {
          return false;
      }

    BufferedQueryTracer that = (BufferedQueryTracer) o;

    return events.equals(that.events);
  }

  @Override
  public int hashCode() {
    return events.hashCode();
  }

  @Override
  public String toString() {
    return "BufferedStatementTracer{" + "events=" + events + '}';
  }
}
