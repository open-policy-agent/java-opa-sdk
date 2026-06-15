package io.github.open_policy_agent.opa.cli;

import io.github.open_policy_agent.opa.metrics.Metrics;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class MetricsReporter {

  public void printMetricsTable(List<Metrics> allMetrics) {
    if (allMetrics.isEmpty()) {
      return;
    }

    final Map<String, Metrics.Metric> metricsData = allMetrics.get(0).all();
    if (metricsData.isEmpty()) {
      return;
    }

    if (allMetrics.size() == 1) {
      printSingleMetricsTable(allMetrics);
    } else {
      printStatisticalMetricsTable(allMetrics);
    }
  }

  private void printSingleMetricsTable(List<Metrics> allMetrics) {
    final Map<String, Metrics.Metric> metricsData = allMetrics.get(0).all();

    int maxMetricNameWidth = "METRIC".length();
    int maxValueWidth = "TIME".length();

    for (final Map.Entry<String, Metrics.Metric> entry : metricsData.entrySet()) {
      final String metricName = entry.getKey();
      maxMetricNameWidth = Math.max(maxMetricNameWidth, metricName.length());

      String value = "";
      if (entry.getValue() instanceof Metrics.Timer) {
        final Metrics.Timer timer = (Metrics.Timer) entry.getValue();
        value = Format.duration(timer.value().toNanos());
      }
      maxValueWidth = Math.max(maxValueWidth, value.length());
    }

    maxMetricNameWidth += 2;
    maxValueWidth += 2;

    printBorder(maxMetricNameWidth, maxValueWidth);
    printRow("METRIC", "TIME", maxMetricNameWidth, maxValueWidth);
    printBorder(maxMetricNameWidth, maxValueWidth);

    for (final Map.Entry<String, Metrics.Metric> entry : metricsData.entrySet()) {
      final String metricName = entry.getKey();
      String value = "";

      if (entry.getValue() instanceof Metrics.Timer) {
        final Metrics.Timer timer = (Metrics.Timer) entry.getValue();
        value = Format.duration(timer.value().toNanos());
      }

      printRow(metricName, value, maxMetricNameWidth, maxValueWidth);
    }

    printBorder(maxMetricNameWidth, maxValueWidth);
  }

  private void printStatisticalMetricsTable(List<Metrics> allMetrics) {
    final Map<String, List<Long>> timerValuesByName = new LinkedHashMap<>();

    final Map<String, Metrics.Metric> firstMetrics = allMetrics.get(0).all();
    for (final Map.Entry<String, Metrics.Metric> entry : firstMetrics.entrySet()) {
      if (entry.getValue() instanceof Metrics.Timer) {
        timerValuesByName.put(entry.getKey(), new ArrayList<>());
      }
    }

    for (final Metrics metrics : allMetrics) {
      for (final Map.Entry<String, Metrics.Metric> entry : metrics.all().entrySet()) {
        if (entry.getValue() instanceof Metrics.Timer) {
          final Metrics.Timer timer = (Metrics.Timer) entry.getValue();
          final List<Long> values = timerValuesByName.get(entry.getKey());
          if (values != null) {
            values.add(timer.value().toNanos());
          }
        }
      }
    }

    if (timerValuesByName.isEmpty()) {
      return;
    }

    int metricWidth = "METRIC".length();
    int minWidth = "MIN".length();
    int maxWidth = "MAX".length();
    int meanWidth = "MEAN".length();
    int p90Width = "90%".length();
    int p99Width = "99%".length();

    final Map<String, Statistics> statsByName = new LinkedHashMap<>();
    for (final Map.Entry<String, List<Long>> entry : timerValuesByName.entrySet()) {
      final String metricName = entry.getKey();
      final Statistics stats = calculateStatistics(entry.getValue());
      statsByName.put(metricName, stats);

      metricWidth = Math.max(metricWidth, metricName.length());
      minWidth = Math.max(minWidth, Format.duration(stats.min).length());
      maxWidth = Math.max(maxWidth, Format.duration(stats.max).length());
      meanWidth = Math.max(meanWidth, Format.duration(stats.mean).length());
      p90Width = Math.max(p90Width, Format.duration(stats.p90).length());
      p99Width = Math.max(p99Width, Format.duration(stats.p99).length());
    }

    metricWidth += 2;
    minWidth += 2;
    maxWidth += 2;
    meanWidth += 2;
    p90Width += 2;
    p99Width += 2;

    printStatisticalBorder(metricWidth, minWidth, maxWidth, meanWidth, p90Width, p99Width);
    printStatisticalRow(
        "METRIC",
        "MIN",
        "MAX",
        "MEAN",
        "90%",
        "99%",
        metricWidth,
        minWidth,
        maxWidth,
        meanWidth,
        p90Width,
        p99Width);
    printStatisticalBorder(metricWidth, minWidth, maxWidth, meanWidth, p90Width, p99Width);

    for (final Map.Entry<String, Statistics> entry : statsByName.entrySet()) {
      final Statistics stats = entry.getValue();
      printStatisticalRow(
          entry.getKey(),
          Format.duration(stats.min),
          Format.duration(stats.max),
          Format.duration(stats.mean),
          Format.duration(stats.p90),
          Format.duration(stats.p99),
          metricWidth,
          minWidth,
          maxWidth,
          meanWidth,
          p90Width,
          p99Width);
    }

    printStatisticalBorder(metricWidth, minWidth, maxWidth, meanWidth, p90Width, p99Width);
  }

  private Statistics calculateStatistics(List<Long> values) {
    if (values.isEmpty()) {
      return new Statistics(0, 0, 0, 0, 0);
    }

    final List<Long> sorted = values.stream().sorted().collect(Collectors.toList());
    final long min = sorted.get(0);
    final long max = sorted.get(sorted.size() - 1);
    final long mean = (long) values.stream().mapToLong(v -> v).average().orElse(0);

    final int p90Index = (int) Math.ceil(0.90 * sorted.size()) - 1;
    final int p99Index = (int) Math.ceil(0.99 * sorted.size()) - 1;
    final long p90 = sorted.get(Math.max(0, Math.min(p90Index, sorted.size() - 1)));
    final long p99 = sorted.get(Math.max(0, Math.min(p99Index, sorted.size() - 1)));

    return new Statistics(min, max, mean, p90, p99);
  }

  private void printBorder(int col1Width, int col2Width) {
    System.out.print("+");
    System.out.print("-".repeat(col1Width));
    System.out.print("+");
    System.out.print("-".repeat(col2Width));
    System.out.println("+");
  }

  private void printRow(String col1, String col2, int col1Width, int col2Width) {
    System.out.printf("| %-" + (col1Width - 2) + "s | %-" + (col2Width - 2) + "s |%n", col1, col2);
  }

  private void printStatisticalBorder(
      int col1Width, int col2Width, int col3Width, int col4Width, int col5Width, int col6Width) {
    System.out.print("+");
    System.out.print("-".repeat(col1Width));
    System.out.print("+");
    System.out.print("-".repeat(col2Width));
    System.out.print("+");
    System.out.print("-".repeat(col3Width));
    System.out.print("+");
    System.out.print("-".repeat(col4Width));
    System.out.print("+");
    System.out.print("-".repeat(col5Width));
    System.out.print("+");
    System.out.print("-".repeat(col6Width));
    System.out.println("+");
  }

  private void printStatisticalRow(
      String col1,
      String col2,
      String col3,
      String col4,
      String col5,
      String col6,
      int col1Width,
      int col2Width,
      int col3Width,
      int col4Width,
      int col5Width,
      int col6Width) {
    System.out.printf(
        "| %-"
            + (col1Width - 2)
            + "s | %-"
            + (col2Width - 2)
            + "s | %-"
            + (col3Width - 2)
            + "s | %-"
            + (col4Width - 2)
            + "s | %-"
            + (col5Width - 2)
            + "s | %-"
            + (col6Width - 2)
            + "s |%n",
        col1,
        col2,
        col3,
        col4,
        col5,
        col6);
  }

  private static class Statistics {
    final long min;
    final long max;
    final long mean;
    final long p90;
    final long p99;

    Statistics(long min, long max, long mean, long p90, long p99) {
      this.min = min;
      this.max = max;
      this.mean = mean;
      this.p90 = p90;
      this.p99 = p99;
    }
  }
}
