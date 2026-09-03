package io.github.open_policy_agent.opa.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.open_policy_agent.opa.metrics.Metrics.Counter;
import io.github.open_policy_agent.opa.metrics.Metrics.Histogram;
import io.github.open_policy_agent.opa.metrics.Metrics.Metric;
import io.github.open_policy_agent.opa.metrics.Metrics.Timer;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SimpleMetricsTest {

  @Test
  void counter_isRegisteredOnceAndAccumulates() {
    SimpleMetrics metrics = new SimpleMetrics();

    Counter counter = metrics.counter("hits");
    assertNotNull(counter, "counter(String) must not return null");
    assertSame(counter, metrics.counter("hits"), "repeated lookups must return the same counter");

    assertEquals(0, counter.value());
    counter.incr();
    counter.add(41);
    assertEquals(42, counter.value());
  }

  @Test
  void histogram_isRegisteredOnce() {
    SimpleMetrics metrics = new SimpleMetrics();

    Histogram histogram = metrics.histogram("sizes");
    assertNotNull(histogram, "histogram(String) must not return null");
    assertSame(
        histogram, metrics.histogram("sizes"), "repeated lookups must return the same histogram");
  }

  @Test
  void histogram_withoutUpdates_reportsZeros() {
    Histogram.Values values = new SimpleMetrics().histogram("sizes").value();

    assertEquals(0, values.count);
    assertEquals(0, values.min);
    assertEquals(0, values.max);
    assertEquals(0, values.mean);
    assertEquals(0, values.stddev);
    assertEquals(0, values.median);
    assertNotNull(values.percentiles);
  }

  @Test
  void histogram_reportsStatsMatchingGoMetrics() {
    SimpleMetrics metrics = new SimpleMetrics();
    Histogram histogram = metrics.histogram("sizes");
    for (int i = 1; i <= 9; i++) {
      histogram.update(i);
    }

    Histogram.Values values = histogram.value();

    assertEquals(9, values.count);
    assertEquals(1, values.min);
    assertEquals(9, values.max);
    assertEquals(5, values.mean);
    // Population stddev of 1..9 is sqrt(60/9) = 2.58, rounded to 3.
    assertEquals(3, values.stddev);
    assertEquals(5, values.median);
    // go-metrics interpolates: the 75th percentile sits at position 0.75 * (9 + 1) = 7.5,
    // i.e. halfway between the 7th and 8th samples.
    assertEquals(8, values.percentiles.get("75%"));
    // Positions at or past the sample count clamp to the largest sample.
    assertEquals(9, values.percentiles.get("90%"));
    assertEquals(9, values.percentiles.get("95%"));
    assertEquals(9, values.percentiles.get("99%"));
    assertEquals(9, values.percentiles.get("99.9%"));
    assertEquals(9, values.percentiles.get("99.99%"));
  }

  @Test
  void histogram_beyondReservoirCapacity_keepsCountingUpdates() {
    Histogram histogram = new SimpleMetrics().histogram("sizes");
    for (int i = 0; i < 5000; i++) {
      histogram.update(7);
    }

    Histogram.Values values = histogram.value();

    assertEquals(5000, values.count);
    assertEquals(7, values.min);
    assertEquals(7, values.max);
    assertEquals(7, values.median);
  }

  @Test
  void all_containsEveryRegisteredMetricType() {
    SimpleMetrics metrics = new SimpleMetrics();
    metrics.timer("rego_query_eval");
    metrics.counter("hits");
    metrics.histogram("sizes");

    Map<String, Metric> all = metrics.all();

    assertTrue(all.get("rego_query_eval") instanceof Timer, "timer missing from all()");
    assertTrue(all.get("hits") instanceof Counter, "counter missing from all()");
    assertTrue(all.get("sizes") instanceof Histogram, "histogram missing from all()");
  }

  @Test
  void clear_removesEveryMetricType() {
    SimpleMetrics metrics = new SimpleMetrics();
    metrics.timer("rego_query_eval");
    metrics.counter("hits");
    metrics.histogram("sizes");

    metrics.clear();

    assertTrue(metrics.all().isEmpty(), "clear() must drop timers, counters and histograms");
  }

  @Test
  void printer_rendersCounterAndHistogramRows() {
    SimpleMetrics metrics = new SimpleMetrics();
    metrics.counter("hits").add(3);
    metrics.histogram("sizes").update(11);

    String table = MetricsPrinter.metricsToString(metrics);

    assertTrue(table.contains("counter_hits"), table);
    assertTrue(table.contains("histogram_sizes_count"), table);
    assertTrue(table.contains("histogram_sizes_99.99%"), table);
  }
}
