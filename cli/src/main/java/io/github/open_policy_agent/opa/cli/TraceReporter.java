package io.github.open_policy_agent.opa.cli;

import io.github.open_policy_agent.opa.ir.Location;
import io.github.open_policy_agent.opa.tracing.Event;
import io.github.open_policy_agent.opa.tracing.QueryTracer;
import java.util.ArrayList;
import java.util.List;

public class TraceReporter {

  public void printTraceOutput(List<QueryTracer> allTracers, String[] fileNames) {
    if (allTracers == null || allTracers.isEmpty()) {
      return;
    }
    final List<Event> events = allTracers.get(0).getEvents();
    if (events.isEmpty()) {
      return;
    }

    final List<TraceItem> processedEvents = consolidateEvents(events);

    int nestingLevel = 0;

    for (final TraceItem item : processedEvents) {
      final Event event = item.event;
      final int count = item.count;
      final Location location = event.getLocation();
      final String opValue = event.getOp().getValue();

      final String fileRef =
          location == null
              ? ""
              : Format.fileRef(location.getFile(), fileNames) + ":" + location.getRow();

      String indent = "| ".repeat(nestingLevel);

      final String operationText;
      if (opValue.equals("EnterStmt")) {
        operationText = "Enter";
        nestingLevel += count;
      } else if (opValue.equals("ExitStmt")) {
        nestingLevel = Math.max(0, nestingLevel - count);
        indent = "| ".repeat(nestingLevel);
        operationText = "Exit";
      } else {
        operationText = opValue;
      }

      final String suffix = count > 1 ? " (x" + count + ")" : "";

      System.out.printf("%-15s %s%s%s%n", fileRef, indent, operationText, suffix);
    }
  }

  private List<TraceItem> consolidateEvents(List<Event> events) {
    final List<TraceItem> result = new ArrayList<>();

    if (events.size() <= 2) {
      for (final Event event : events) {
        result.add(new TraceItem(event, 1));
      }
      return result;
    }

    int i = 0;
    while (i < events.size()) {
      final Event currentEvent = events.get(i);
      final Location currentLocation = currentEvent.getLocation();
      final String currentOp = currentEvent.getOp().getValue();

      int j = i + 1;
      while (j < events.size()) {
        final Event nextEvent = events.get(j);
        final Location nextLocation = nextEvent.getLocation();
        if (currentLocation == null
            || nextLocation == null
            || currentLocation.getFile() != nextLocation.getFile()
            || currentLocation.getRow() != nextLocation.getRow()
            || !currentOp.equals(nextEvent.getOp().getValue())) {
          break;
        }
        j++;
      }
      result.add(new TraceItem(currentEvent, j - i));
      i = j;
    }

    return result;
  }

  private static class TraceItem {
    final Event event;
    final int count;

    TraceItem(Event event, int count) {
      this.event = event;
      this.count = count;
    }
  }
}
