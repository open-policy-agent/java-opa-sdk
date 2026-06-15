package io.github.open_policy_agent.opa.cli;

import io.github.open_policy_agent.opa.profiling.StatementProfiler;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class StatementReporter {

  public void printStatementOutput(List<StatementProfiler> allProfilers) {
    if (allProfilers.isEmpty()) {
      return;
    }

    final Map<String, List<StatementProfiler.StatementSummary>> summariesByStatement =
        new HashMap<>();

    for (final StatementProfiler profiler : allProfilers) {
      final Map<String, StatementProfiler.StatementSummary> summaries =
          profiler.getStatementSummaries();
      for (final Map.Entry<String, StatementProfiler.StatementSummary> entry :
          summaries.entrySet()) {
        summariesByStatement
            .computeIfAbsent(entry.getKey(), k -> new ArrayList<>())
            .add(entry.getValue());
      }
    }

    if (summariesByStatement.isEmpty()) {
      return;
    }

    if (allProfilers.size() == 1) {
      printSingleStatementTable(summariesByStatement);
    } else {
      printStatisticalStatementTable(summariesByStatement);
    }
  }

  private void printSingleStatementTable(
      Map<String, List<StatementProfiler.StatementSummary>> summariesByStatement) {
    int totalTimeWidth = "TOTAL TIME".length();
    int avgTimeWidth = "AVG TIME".length();
    int countWidth = "COUNT".length();
    int statementWidth = "STATEMENT".length();

    final List<StatementRow> tableData = new ArrayList<>();

    for (final Map.Entry<String, List<StatementProfiler.StatementSummary>> entry :
        summariesByStatement.entrySet()) {
      final String statement = entry.getKey();
      final StatementProfiler.StatementSummary summary = entry.getValue().get(0);

      final int count = summary.getCount();
      final Duration totalDuration = summary.getDuration();
      final long totalNanos = totalDuration.toNanos();

      final long avgNanos = count > 0 ? totalNanos / count : 0;
      final String totalTimeStr = Format.duration(totalNanos);
      final String avgTimeStr = Format.duration(avgNanos);

      final StatementRow row =
          new StatementRow(totalTimeStr, avgTimeStr, count, statement, totalNanos, avgNanos);
      tableData.add(row);

      totalTimeWidth = Math.max(totalTimeWidth, totalTimeStr.length());
      avgTimeWidth = Math.max(avgTimeWidth, avgTimeStr.length());
      countWidth = Math.max(countWidth, String.valueOf(count).length());
      statementWidth = Math.max(statementWidth, statement.length());
    }

    totalTimeWidth += 2;
    avgTimeWidth += 2;
    countWidth += 2;
    statementWidth += 2;

    tableData.sort((a, b) -> Long.compare(b.getTotalNanos(), a.getTotalNanos()));

    printStatementBorder(totalTimeWidth, avgTimeWidth, countWidth, statementWidth);
    printStatementRow(
        "TOTAL TIME",
        "AVG TIME",
        "COUNT",
        "STATEMENT",
        totalTimeWidth,
        avgTimeWidth,
        countWidth,
        statementWidth);
    printStatementBorder(totalTimeWidth, avgTimeWidth, countWidth, statementWidth);

    for (final StatementRow row : tableData) {
      printStatementRow(
          row.totalTime,
          row.avgTime,
          String.valueOf(row.count),
          row.statement,
          totalTimeWidth,
          avgTimeWidth,
          countWidth,
          statementWidth);
    }

    printStatementBorder(totalTimeWidth, avgTimeWidth, countWidth, statementWidth);
  }

  private void printStatisticalStatementTable(
      Map<String, List<StatementProfiler.StatementSummary>> summariesByStatement) {
    final Map<String, List<Long>> totalTimesByStatement = new HashMap<>();
    final Map<String, List<Long>> avgTimesByStatement = new HashMap<>();
    final Map<String, List<Integer>> countsByStatement = new HashMap<>();

    for (final Map.Entry<String, List<StatementProfiler.StatementSummary>> entry :
        summariesByStatement.entrySet()) {
      final String statement = entry.getKey();

      final List<Long> totalTimes = new ArrayList<>();
      final List<Long> avgTimes = new ArrayList<>();
      final List<Integer> counts = new ArrayList<>();

      for (final StatementProfiler.StatementSummary summary : entry.getValue()) {
        final int count = summary.getCount();
        final Duration totalDuration = summary.getDuration();
        final long totalNanos = totalDuration.toNanos();
        final long avgNanos = count > 0 ? totalNanos / count : 0;

        totalTimes.add(totalNanos);
        avgTimes.add(avgNanos);
        counts.add(count);
      }

      totalTimesByStatement.put(statement, totalTimes);
      avgTimesByStatement.put(statement, avgTimes);
      countsByStatement.put(statement, counts);
    }

    int statementWidth = "STATEMENT".length();
    int avgCountWidth = "COUNT".length();
    int totalTimeWidth = "MEAN TOTAL".length();
    int minTimeWidth = "MIN".length();
    int maxTimeWidth = "MAX".length();
    int meanTimeWidth = "MEAN".length();
    int p90TimeWidth = "90%".length();
    int p99TimeWidth = "99%".length();

    final Map<String, StatementStatistics> statsByStatement = new HashMap<>();
    for (final Map.Entry<String, List<Long>> entry : totalTimesByStatement.entrySet()) {
      final String statement = entry.getKey();
      final List<Long> totalTimes = entry.getValue();
      final List<Long> avgTimes = avgTimesByStatement.get(statement);
      final List<Integer> counts = countsByStatement.get(statement);

      final StatementStatistics stats =
          calculateStatementStatistics(totalTimes, avgTimes, counts);
      statsByStatement.put(statement, stats);

      statementWidth = Math.max(statementWidth, statement.length());
      avgCountWidth = Math.max(avgCountWidth, String.valueOf(stats.avgCount).length());
      totalTimeWidth = Math.max(totalTimeWidth, Format.duration(stats.meanTotalTime).length());
      minTimeWidth = Math.max(minTimeWidth, Format.duration(stats.minTime).length());
      maxTimeWidth = Math.max(maxTimeWidth, Format.duration(stats.maxTime).length());
      meanTimeWidth = Math.max(meanTimeWidth, Format.duration(stats.meanTime).length());
      p90TimeWidth = Math.max(p90TimeWidth, Format.duration(stats.p90Time).length());
      p99TimeWidth = Math.max(p99TimeWidth, Format.duration(stats.p99Time).length());
    }

    statementWidth += 2;
    avgCountWidth += 2;
    totalTimeWidth += 2;
    minTimeWidth += 2;
    maxTimeWidth += 2;
    meanTimeWidth += 2;
    p90TimeWidth += 2;
    p99TimeWidth += 2;

    final List<Map.Entry<String, StatementStatistics>> sortedEntries =
        statsByStatement.entrySet().stream()
            .sorted((a, b) -> Long.compare(b.getValue().meanTotalTime, a.getValue().meanTotalTime))
            .collect(Collectors.toList());

    printStatisticalStatementBorder(
        statementWidth,
        avgCountWidth,
        totalTimeWidth,
        minTimeWidth,
        maxTimeWidth,
        meanTimeWidth,
        p90TimeWidth,
        p99TimeWidth);
    printStatisticalStatementRow(
        "STATEMENT",
        "COUNT",
        "MEAN TOTAL",
        "MIN",
        "MAX",
        "MEAN",
        "90%",
        "99%",
        statementWidth,
        avgCountWidth,
        totalTimeWidth,
        minTimeWidth,
        maxTimeWidth,
        meanTimeWidth,
        p90TimeWidth,
        p99TimeWidth);
    printStatisticalStatementBorder(
        statementWidth,
        avgCountWidth,
        totalTimeWidth,
        minTimeWidth,
        maxTimeWidth,
        meanTimeWidth,
        p90TimeWidth,
        p99TimeWidth);

    for (final Map.Entry<String, StatementStatistics> entry : sortedEntries) {
      final StatementStatistics stats = entry.getValue();
      printStatisticalStatementRow(
          entry.getKey(),
          String.valueOf(stats.avgCount),
          Format.duration(stats.meanTotalTime),
          Format.duration(stats.minTime),
          Format.duration(stats.maxTime),
          Format.duration(stats.meanTime),
          Format.duration(stats.p90Time),
          Format.duration(stats.p99Time),
          statementWidth,
          avgCountWidth,
          totalTimeWidth,
          minTimeWidth,
          maxTimeWidth,
          meanTimeWidth,
          p90TimeWidth,
          p99TimeWidth);
    }

    printStatisticalStatementBorder(
        statementWidth,
        avgCountWidth,
        totalTimeWidth,
        minTimeWidth,
        maxTimeWidth,
        meanTimeWidth,
        p90TimeWidth,
        p99TimeWidth);
  }

  private StatementStatistics calculateStatementStatistics(
      List<Long> totalTimes, List<Long> avgTimes, List<Integer> counts) {
    if (avgTimes.isEmpty()) {
      return new StatementStatistics(0, 0, 0, 0, 0, 0, 0);
    }

    final List<Long> sortedTimes = avgTimes.stream().sorted().collect(Collectors.toList());
    final long minTime = sortedTimes.get(0);
    final long maxTime = sortedTimes.get(sortedTimes.size() - 1);
    final long meanTime = (long) avgTimes.stream().mapToLong(v -> v).average().orElse(0);

    final int p90Index = (int) Math.ceil(0.90 * sortedTimes.size()) - 1;
    final int p99Index = (int) Math.ceil(0.99 * sortedTimes.size()) - 1;
    final long p90Time = sortedTimes.get(Math.max(0, Math.min(p90Index, sortedTimes.size() - 1)));
    final long p99Time = sortedTimes.get(Math.max(0, Math.min(p99Index, sortedTimes.size() - 1)));

    final int avgCount = (int) counts.stream().mapToInt(v -> v).average().orElse(0);
    final long meanTotalTime = (long) totalTimes.stream().mapToLong(v -> v).average().orElse(0);

    return new StatementStatistics(
        avgCount, meanTotalTime, minTime, maxTime, meanTime, p90Time, p99Time);
  }

  private void printStatementBorder(int col1Width, int col2Width, int col3Width, int col4Width) {
    System.out.print("+");
    System.out.print("-".repeat(col1Width));
    System.out.print("+");
    System.out.print("-".repeat(col2Width));
    System.out.print("+");
    System.out.print("-".repeat(col3Width));
    System.out.print("+");
    System.out.print("-".repeat(col4Width));
    System.out.println("+");
  }

  private void printStatementRow(
      String col1,
      String col2,
      String col3,
      String col4,
      int col1Width,
      int col2Width,
      int col3Width,
      int col4Width) {
    System.out.printf(
        "| %-"
            + (col1Width - 2)
            + "s | %-"
            + (col2Width - 2)
            + "s | %-"
            + (col3Width - 2)
            + "s | %-"
            + (col4Width - 2)
            + "s |%n",
        col1,
        col2,
        col3,
        col4);
  }

  private void printStatisticalStatementBorder(
      int col1Width,
      int col2Width,
      int col3Width,
      int col4Width,
      int col5Width,
      int col6Width,
      int col7Width,
      int col8Width) {
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
    System.out.print("+");
    System.out.print("-".repeat(col7Width));
    System.out.print("+");
    System.out.print("-".repeat(col8Width));
    System.out.println("+");
  }

  private void printStatisticalStatementRow(
      String col1,
      String col2,
      String col3,
      String col4,
      String col5,
      String col6,
      String col7,
      String col8,
      int col1Width,
      int col2Width,
      int col3Width,
      int col4Width,
      int col5Width,
      int col6Width,
      int col7Width,
      int col8Width) {
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
            + "s | %-"
            + (col7Width - 2)
            + "s | %-"
            + (col8Width - 2)
            + "s |%n",
        col1,
        col2,
        col3,
        col4,
        col5,
        col6,
        col7,
        col8);
  }

  private static class StatementRow {
    final String totalTime;
    final String avgTime;
    final int count;
    final String statement;
    final long totalNanos;
    final long avgNanos;

    StatementRow(
        String totalTime,
        String avgTime,
        int count,
        String statement,
        long totalNanos,
        long avgNanos) {
      this.totalTime = totalTime;
      this.avgTime = avgTime;
      this.count = count;
      this.statement = statement;
      this.totalNanos = totalNanos;
      this.avgNanos = avgNanos;
    }

    public long getTotalNanos() {
      return totalNanos;
    }
  }

  private static class StatementStatistics {
    final int avgCount;
    final long meanTotalTime;
    final long minTime;
    final long maxTime;
    final long meanTime;
    final long p90Time;
    final long p99Time;

    StatementStatistics(
        int avgCount,
        long meanTotalTime,
        long minTime,
        long maxTime,
        long meanTime,
        long p90Time,
        long p99Time) {
      this.avgCount = avgCount;
      this.meanTotalTime = meanTotalTime;
      this.minTime = minTime;
      this.maxTime = maxTime;
      this.meanTime = meanTime;
      this.p90Time = p90Time;
      this.p99Time = p99Time;
    }
  }
}
