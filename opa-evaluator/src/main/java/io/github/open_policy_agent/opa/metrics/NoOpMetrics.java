package io.github.open_policy_agent.opa.metrics;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

public class NoOpMetrics implements Metrics {

  private static final NoOpMetrics INSTANCE = new NoOpMetrics();

  public static NoOpMetrics Instance() {
    return INSTANCE;
  }

  private NoOpMetrics() {}

  @Override
  public String name() {
    return "noop";
  }

  @Override
  public Timer timer(String name) {
    return new NoOpTimer();
  }

  @Override
  public Histogram histogram(String name) {
    return new NoOpHistogram();
  }

  @Override
  public Counter counter(String name) {
    return new NoOpCounter();
  }

  @Override
  public Map<String, Metric> all() {
    return new HashMap<>();
  }

  @Override
  public void clear() {
    // No-op
  }

  private static class NoOpTimer implements Timer {
    @Override
    public void start() {
      // No-op
    }

    @Override
    public void stop() {
      // No-op
    }

    @Override
    public Duration value() {
      return Duration.ZERO;
    }
  }

  private static class NoOpCounter implements Counter {
    @Override
    public void add(int value) {
      // No-op
    }

    @Override
    public void incr() {
      // No-op
    }

    @Override
    public int value() {
      return 0;
    }
  }

  private static class NoOpHistogram implements Histogram {
    @Override
    public void update(double value) {
      // No-op
    }

    @Override
    public Values value() {
      Values values = new Values();
      values.count = 0;
      values.min = 0;
      values.max = 0;
      values.mean = 0;
      values.stddev = 0;
      values.median = 0;
      values.percentiles = new HashMap<>();
      return values;
    }
  }
}
