package io.github.open_policy_agent.opa.tracing;

import java.util.List;

public interface QueryTracer {
  void traceEvent(Event event);

  List<Event> getEvents();
}
