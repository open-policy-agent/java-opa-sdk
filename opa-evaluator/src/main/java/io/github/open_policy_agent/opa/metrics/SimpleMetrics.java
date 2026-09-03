package io.github.open_policy_agent.opa.metrics;

import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

public class SimpleMetrics implements Metrics {

  private final Map<String, Timer> timers = new TreeMap<>();
  private final Map<String, Counter> counters = new ConcurrentHashMap<>();
  private final Map<String, Histogram> histograms = new ConcurrentHashMap<>();

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
    return histograms.computeIfAbsent(name, key -> new SimpleHistogram());
  }

  @Override
  public Counter counter(String name) {
    return counters.computeIfAbsent(name, key -> new SimpleCounter());
  }

  @Override
  public Map<String, Metric> all() {
    Map<String, Metric> all = new TreeMap<>(timers);
    all.putAll(counters);
    all.putAll(histograms);
    return all;
  }

  @Override
  public void clear() {
    timers.clear();
    counters.clear();
    histograms.clear();
  }

  private static class SimpleCounter implements Counter {

    private final AtomicInteger value = new AtomicInteger();

    @Override
    public void add(int delta) {
      value.addAndGet(delta);
    }

    @Override
    public void incr() {
      value.incrementAndGet();
    }

    @Override
    public int value() {
      return value.get();
    }
  }

  /**
   * Histogram over a bounded reservoir. Once {@code RESERVOIR_SIZE} samples have been recorded,
   * further updates replace a random slot (Vitter's algorithm R) so a long-lived {@link Metrics}
   * instance does not grow without bound. The reservoir size and the reported percentiles match
   * OPA's Go implementation, which draws both from {@code rcrowley/go-metrics}.
   */
  private static class SimpleHistogram implements Histogram {

    private static final int RESERVOIR_SIZE = 1028;
    private static final double[] PERCENTILES = {0.75, 0.9, 0.95, 0.99, 0.999, 0.9999};
    private static final String[] PERCENTILE_KEYS = {"75%", "90%", "95%", "99%", "99.9%", "99.99%"};

    private final double[] reservoir = new double[RESERVOIR_SIZE];
    private long count;
    private int size;

    @Override
    public synchronized void update(double value) {
      count++;
      if (size < RESERVOIR_SIZE) {
        reservoir[size] = value;
        size++;
        return;
      }
      long slot = ThreadLocalRandom.current().nextLong(count);
      if (slot < RESERVOIR_SIZE) {
        reservoir[(int) slot] = value;
      }
    }

    @Override
    public synchronized Values value() {
      Values values = new Values();
      values.percentiles = new HashMap<>();
      // count is the number of updates, not the reservoir occupancy; saturate rather than wrap.
      values.count = (int) Math.min(count, Integer.MAX_VALUE);
      if (size == 0) {
        return values;
      }

      double[] sorted = Arrays.copyOf(reservoir, size);
      Arrays.sort(sorted);
      double sum = 0;
      for (double sample : sorted) {
        sum += sample;
      }
      double mean = sum / size;
      double squaredDeviations = 0;
      for (double sample : sorted) {
        squaredDeviations += (sample - mean) * (sample - mean);
      }

      values.min = round(sorted[0]);
      values.max = round(sorted[size - 1]);
      values.mean = round(mean);
      // Population standard deviation, as in go-metrics' SampleVariance.
      values.stddev = round(Math.sqrt(squaredDeviations / size));
      values.median = round(percentile(sorted, 0.5));
      for (int i = 0; i < PERCENTILES.length; i++) {
        values.percentiles.put(PERCENTILE_KEYS[i], round(percentile(sorted, PERCENTILES[i])));
      }
      return values;
    }

    /** Linearly interpolated percentile, matching go-metrics' {@code SamplePercentiles}. */
    private static double percentile(double[] sorted, double quantile) {
      double pos = quantile * (sorted.length + 1);
      if (pos < 1.0) {
        return sorted[0];
      }
      if (pos >= sorted.length) {
        return sorted[sorted.length - 1];
      }
      double lower = sorted[(int) pos - 1];
      double upper = sorted[(int) pos];
      return lower + (pos - Math.floor(pos)) * (upper - lower);
    }

    private static int round(double value) {
      return (int) Math.round(value);
    }
  }
}
