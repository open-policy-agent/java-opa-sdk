package io.github.open_policy_agent.opa.cli;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.open_policy_agent.opa.metrics.Metrics;
import io.github.open_policy_agent.opa.metrics.SimpleMetrics;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class MetricsReporterTest {

  private final ByteArrayOutputStream outContent = new ByteArrayOutputStream();
  private PrintStream originalOut;

  @BeforeEach
  void captureStdout() {
    originalOut = System.out;
    System.setOut(new PrintStream(outContent, true, StandardCharsets.UTF_8));
  }

  @AfterEach
  void restoreStdout() {
    System.setOut(originalOut);
  }

  private static Metrics metrics(int counter, double... samples) {
    final Metrics metrics = new SimpleMetrics();
    metrics.timer("eval").start();
    metrics.timer("eval").stop();
    metrics.counter("cache_hits").add(counter);
    for (final double sample : samples) {
      metrics.histogram("batch_size").update(sample);
    }
    return metrics;
  }

  private String print(List<Metrics> allMetrics) {
    new MetricsReporter().printMetricsTable(allMetrics);
    return outContent.toString(StandardCharsets.UTF_8);
  }

  private static List<String> cells(String out, String metricName) {
    final Optional<String> line =
        out.lines().filter(l -> l.startsWith("| " + metricName + " ")).findFirst();
    assertThat(line).as("row for %s", metricName).isPresent();
    return List.of(line.get().substring(1, line.get().length() - 1).split("\\|")).stream()
        .map(String::trim)
        .toList();
  }

  @Test
  void singleTablePrintsCountersAndHistograms() {
    final String out = print(List.of(metrics(3, 1, 2, 3, 4)));

    assertThat(cells(out, "METRIC")).containsExactly("METRIC", "VALUE");
    assertThat(cells(out, "timer_eval_ns").get(1)).isNotEmpty();
    assertThat(cells(out, "counter_cache_hits")).containsExactly("counter_cache_hits", "3");
    assertThat(cells(out, "histogram_batch_size_count"))
        .containsExactly("histogram_batch_size_count", "4");
    assertThat(cells(out, "histogram_batch_size_min")).containsExactly("histogram_batch_size_min", "1");
    assertThat(cells(out, "histogram_batch_size_max")).containsExactly("histogram_batch_size_max", "4");
    assertThat(cells(out, "histogram_batch_size_99%")).containsExactly("histogram_batch_size_99%", "4");
  }

  @Test
  void statisticalTableIncludesCountersAndHistograms() {
    final String out = print(List.of(metrics(3, 10), metrics(5, 20), metrics(4, 30)));

    assertThat(cells(out, "METRIC")).containsExactly("METRIC", "MIN", "MAX", "MEAN", "90%", "99%");
    assertThat(cells(out, "timer_eval_ns")).doesNotContain("");
    assertThat(cells(out, "counter_cache_hits"))
        .containsExactly("counter_cache_hits", "3", "5", "4", "5", "5");
    assertThat(cells(out, "histogram_batch_size_max"))
        .containsExactly("histogram_batch_size_max", "10", "30", "20", "30", "30");
  }
}
