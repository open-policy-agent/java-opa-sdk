package io.github.open_policy_agent.opa.metrics;

import java.time.Duration;
import java.util.*;

public class SimpleMetrics implements Metrics {

  private final Map<String, Timer> timers = new TreeMap<>();

  @Override
  public String name() {
    return "";
  }

  @Override
  public Timer timer(String name) {
    if (!timers.containsKey(name)) {
      timers.put(
          name,
          new Timer() {
            long start;
            long end;

            public void start() {
              start = System.nanoTime();
            }

            @Override
            public void stop() {
              end = System.nanoTime();
            }

            @Override
            public Duration value() {
              return Duration.ofNanos(end - start);
            }
          });
    }
    return timers.get(name);
  }

  @Override
  public Histogram histogram(String name) {
    return null;
  }

  @Override
  public Counter counter(String name) {
    return null;
  }

  @Override
  public Map<String, Metric> all() {
    return new TreeMap<>(timers);
  }

  @Override
  public void clear() {
    timers.clear();
  }
}
