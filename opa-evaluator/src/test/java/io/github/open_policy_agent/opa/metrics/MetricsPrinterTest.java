package io.github.open_policy_agent.opa.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.open_policy_agent.opa.metrics.Metrics.Counter;
import io.github.open_policy_agent.opa.metrics.Metrics.Histogram;
import io.github.open_policy_agent.opa.metrics.Metrics.Metric;
import io.github.open_policy_agent.opa.metrics.Metrics.Timer;
import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class MetricsPrinterTest {

  static Stream<Arguments> formattingCases() {
    return Stream.of(
        Arguments.of(
            "empty metrics produces just headers",
            metricsOf(Map.of()),
            join(
                "┌────────┬───────┐",
                "│ Metric │ Value │",
                "├────────┼───────┤",
                "└────────┴───────┘")),
        Arguments.of(
            "single timer is keyed timer_<name>_ns and value is in nanoseconds",
            metricsOf(Map.of("foo", fixedTimer(Duration.ofNanos(123)))),
            join(
                "┌──────────────┬───────┐",
                "│    Metric    │ Value │",
                "├──────────────┼───────┤",
                "│ timer_foo_ns │ 123   │",
                "└──────────────┴───────┘")),
        Arguments.of(
            "rows are sorted alphabetically by display name",
            metricsOf(
                linkedMap(
                    "zeta", fixedTimer(Duration.ofNanos(2)),
                    "alpha", fixedTimer(Duration.ofNanos(1)))),
            join(
                "┌────────────────┬───────┐",
                "│     Metric     │ Value │",
                "├────────────────┼───────┤",
                "│ timer_alpha_ns │ 1     │",
                "│ timer_zeta_ns  │ 2     │",
                "└────────────────┴───────┘")),
        Arguments.of(
            "counter is keyed counter_<name>",
            metricsOf(Map.of("hits", fixedCounter(42))),
            join(
                "┌──────────────┬───────┐",
                "│    Metric    │ Value │",
                "├──────────────┼───────┤",
                "│ counter_hits │ 42    │",
                "└──────────────┴───────┘")),
        Arguments.of(
            "histogram explodes into one row per stat plus percentiles",
            metricsOf(
                Map.of(
                    "calls",
                    fixedHistogram(
                        histogramValues(10, 1, 9, 5, 3, 4, linkedMap("75%", 7, "99%", 9))))),
            join(
                "┌────────────────────────┬───────┐",
                "│         Metric         │ Value │",
                "├────────────────────────┼───────┤",
                "│ histogram_calls_75%    │ 7     │",
                "│ histogram_calls_99%    │ 9     │",
                "│ histogram_calls_count  │ 10    │",
                "│ histogram_calls_max    │ 9     │",
                "│ histogram_calls_mean   │ 5     │",
                "│ histogram_calls_median │ 4     │",
                "│ histogram_calls_min    │ 1     │",
                "│ histogram_calls_stddev │ 3     │",
                "└────────────────────────┴───────┘")),
        Arguments.of(
            "column widths size to the widest cell",
            metricsOf(Map.of("a_long_metric_name", fixedTimer(Duration.ofNanos(1234567890L)))),
            join(
                "┌─────────────────────────────┬────────────┐",
                "│           Metric            │   Value    │",
                "├─────────────────────────────┼────────────┤",
                "│ timer_a_long_metric_name_ns │ 1234567890 │",
                "└─────────────────────────────┴────────────┘")));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("formattingCases")
  void produceExpectedOutput(String name, Metrics metrics, String expected) {
    assertEquals(expected, MetricsPrinter.metricsToString(metrics));
  }

  @Test
  void simpleMetrics_unstartedTimers_produceZeroNanosRows() {
    SimpleMetrics metrics = new SimpleMetrics();
    metrics.timer("rego_query_eval");
    metrics.timer("rego_query_parse");

    String out = MetricsPrinter.metricsToString(metrics);

    assertTrue(out.contains("timer_rego_query_eval_ns"), out);
    assertTrue(out.contains("timer_rego_query_parse_ns"), out);
    // rego_query_eval sorts before rego_query_parse alphabetically.
    int evalIdx = out.indexOf("timer_rego_query_eval_ns");
    int parseIdx = out.indexOf("timer_rego_query_parse_ns");
    assertTrue(evalIdx > 0 && parseIdx > evalIdx, "rows are not sorted alphabetically:\n" + out);
  }

  // --- helpers ---

  private static String join(String... lines) {
    StringBuilder sb = new StringBuilder();
    for (String line : lines) {
      sb.append(line).append('\n');
    }
    return sb.toString();
  }

  private static Metrics metricsOf(Map<String, ? extends Metric> entries) {
    Map<String, Metric> all = new LinkedHashMap<>(entries);
    return new Metrics() {
      @Override
      public String name() {
        return "";
      }

      @Override
      public Timer timer(String name) {
        return (Timer) all.get(name);
      }

      @Override
      public Histogram histogram(String name) {
        return (Histogram) all.get(name);
      }

      @Override
      public Counter counter(String name) {
        return (Counter) all.get(name);
      }

      @Override
      public Map<String, Metric> all() {
        return all;
      }

      @Override
      public void clear() {}
    };
  }

  private static Timer fixedTimer(Duration duration) {
    return new Timer() {
      @Override
      public void start() {}

      @Override
      public void stop() {}

      @Override
      public Duration value() {
        return duration;
      }
    };
  }

  private static Counter fixedCounter(int value) {
    return new Counter() {
      @Override
      public void add(int v) {}

      @Override
      public void incr() {}

      @Override
      public int value() {
        return value;
      }
    };
  }

  private static Histogram fixedHistogram(Histogram.Values values) {
    return new Histogram() {
      @Override
      public void update(double v) {}

      @Override
      public Histogram.Values value() {
        return values;
      }
    };
  }

  private static Histogram.Values histogramValues(
      int count, int min, int max, int mean, int stddev, int median, Map<String, Integer> p) {
    Histogram.Values v = new Histogram.Values();
    v.count = count;
    v.min = min;
    v.max = max;
    v.mean = mean;
    v.stddev = stddev;
    v.median = median;
    v.percentiles = new HashMap<>(p);
    return v;
  }

  private static <K, V> Map<K, V> linkedMap(K k1, V v1, K k2, V v2) {
    Map<K, V> m = new LinkedHashMap<>();
    m.put(k1, v1);
    m.put(k2, v2);
    return m;
  }
}
