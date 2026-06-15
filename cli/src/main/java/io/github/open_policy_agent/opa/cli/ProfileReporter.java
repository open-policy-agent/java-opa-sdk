package io.github.open_policy_agent.opa.cli;

import io.github.open_policy_agent.opa.tracing.DurationProfiler;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ProfileReporter {

  public void printProfileOutput(
      List<DurationProfiler> allProfilers, String[] fileNames, int limit, String sortKey) {
    if (allProfilers.isEmpty()) {
      return;
    }

    if (allProfilers.get(0).getDurations().isEmpty()) {
      return;
    }

    if (allProfilers.size() == 1) {
      printSingleProfileTable(allProfilers, fileNames, limit, sortKey);
    } else {
      printStatisticalProfileTable(allProfilers, fileNames, limit, sortKey);
    }
  }

  private void printSingleProfileTable(
      List<DurationProfiler> allProfilers, String[] fileNames, int limit, String sortKey) {
    int timeWidth = "TIME".length();
    int numEvalWidth = "NUM EVAL".length();
    int locationWidth = "LOCATION".length();

    final List<ProfileRow> tableData = new ArrayList<>();

    for (final Map.Entry<DurationProfiler.Loc, DurationProfiler.EvalTotal> entry :
        allProfilers.get(0).getDurations().entrySet()) {
      final DurationProfiler.Loc loc = entry.getKey();
      final DurationProfiler.EvalTotal evalTotal = entry.getValue();

      final String location = Format.fileRef(loc.getFile(), fileNames) + ":" + loc.getRow();
      final long nanos = evalTotal.getTotalDuration().toNanos();
      final String timeStr = Format.duration(nanos);
      final int numEval = evalTotal.getCount();

      final ProfileRow row = new ProfileRow(timeStr, numEval, location, nanos);
      tableData.add(row);

      timeWidth = Math.max(timeWidth, timeStr.length());
      numEvalWidth = Math.max(numEvalWidth, String.valueOf(numEval).length());
      locationWidth = Math.max(locationWidth, location.length());
    }

    timeWidth += 2;
    numEvalWidth += 2;
    locationWidth += 2;

    tableData.sort(profileRowComparator(sortKey));
    final List<ProfileRow> rows =
        limit > 0 && tableData.size() > limit ? tableData.subList(0, limit) : tableData;

    printProfileBorder(timeWidth, numEvalWidth, locationWidth);
    printProfileRow("TIME", "NUM EVAL", "LOCATION", timeWidth, numEvalWidth, locationWidth);
    printProfileBorder(timeWidth, numEvalWidth, locationWidth);

    for (final ProfileRow row : rows) {
      printProfileRow(
          row.time,
          String.valueOf(row.numEval),
          row.location,
          timeWidth,
          numEvalWidth,
          locationWidth);
    }

    printProfileBorder(timeWidth, numEvalWidth, locationWidth);
  }

  private void printStatisticalProfileTable(
      List<DurationProfiler> allProfilers, String[] fileNames, int limit, String sortKey) {
    final Map<String, List<Long>> durationsByLocation = new HashMap<>();
    final Map<String, List<Integer>> countsByLocation = new HashMap<>();

    for (final DurationProfiler profiler : allProfilers) {
      for (final Map.Entry<DurationProfiler.Loc, DurationProfiler.EvalTotal> entry :
          profiler.getDurations().entrySet()) {
        final DurationProfiler.Loc loc = entry.getKey();
        final DurationProfiler.EvalTotal evalTotal = entry.getValue();
        final String location = Format.fileRef(loc.getFile(), fileNames) + ":" + loc.getRow();

        durationsByLocation
            .computeIfAbsent(location, k -> new ArrayList<>())
            .add(evalTotal.getTotalDuration().toNanos());
        countsByLocation
            .computeIfAbsent(location, k -> new ArrayList<>())
            .add(evalTotal.getCount());
      }
    }

    if (durationsByLocation.isEmpty()) {
      return;
    }

    int locationWidth = "LOCATION".length();
    int minWidth = "MIN".length();
    int maxWidth = "MAX".length();
    int meanWidth = "MEAN".length();
    int p90Width = "90%".length();
    int p99Width = "99%".length();

    final Map<String, ProfileStatistics> statsByLocation = new HashMap<>();
    for (final Map.Entry<String, List<Long>> entry : durationsByLocation.entrySet()) {
      final String location = entry.getKey();
      final List<Integer> counts = countsByLocation.get(location);
      final ProfileStatistics stats = calculateProfileStatistics(entry.getValue(), counts);
      statsByLocation.put(location, stats);

      locationWidth = Math.max(locationWidth, location.length());
      minWidth = Math.max(minWidth, Format.duration(stats.min).length());
      maxWidth = Math.max(maxWidth, Format.duration(stats.max).length());
      meanWidth = Math.max(meanWidth, Format.duration(stats.mean).length());
      p90Width = Math.max(p90Width, Format.duration(stats.p90).length());
      p99Width = Math.max(p99Width, Format.duration(stats.p99).length());
    }

    locationWidth += 2;
    minWidth += 2;
    maxWidth += 2;
    meanWidth += 2;
    p90Width += 2;
    p99Width += 2;

    final List<Map.Entry<String, ProfileStatistics>> sortedEntries =
        statsByLocation.entrySet().stream()
            .sorted(profileStatisticsComparator(sortKey))
            .collect(Collectors.toList());
    final List<Map.Entry<String, ProfileStatistics>> limitedEntries =
        limit > 0 && sortedEntries.size() > limit
            ? sortedEntries.subList(0, limit)
            : sortedEntries;

    printStatisticalProfileBorder(locationWidth, minWidth, maxWidth, meanWidth, p90Width, p99Width);
    printStatisticalProfileRow(
        "LOCATION",
        "MIN",
        "MAX",
        "MEAN",
        "90%",
        "99%",
        locationWidth,
        minWidth,
        maxWidth,
        meanWidth,
        p90Width,
        p99Width);
    printStatisticalProfileBorder(locationWidth, minWidth, maxWidth, meanWidth, p90Width, p99Width);

    for (final Map.Entry<String, ProfileStatistics> entry : limitedEntries) {
      final ProfileStatistics stats = entry.getValue();
      printStatisticalProfileRow(
          entry.getKey(),
          Format.duration(stats.min),
          Format.duration(stats.max),
          Format.duration(stats.mean),
          Format.duration(stats.p90),
          Format.duration(stats.p99),
          locationWidth,
          minWidth,
          maxWidth,
          meanWidth,
          p90Width,
          p99Width);
    }

    printStatisticalProfileBorder(locationWidth, minWidth, maxWidth, meanWidth, p90Width, p99Width);
  }

  private ProfileStatistics calculateProfileStatistics(List<Long> values, List<Integer> counts) {
    if (values.isEmpty()) {
      return new ProfileStatistics(0, 0, 0, 0, 0, 0);
    }

    final List<Long> sorted = values.stream().sorted().collect(Collectors.toList());
    final long min = sorted.get(0);
    final long max = sorted.get(sorted.size() - 1);
    final long mean = (long) values.stream().mapToLong(v -> v).average().orElse(0);

    final int p90Index = (int) Math.ceil(0.90 * sorted.size()) - 1;
    final int p99Index = (int) Math.ceil(0.99 * sorted.size()) - 1;
    final long p90 = sorted.get(Math.max(0, Math.min(p90Index, sorted.size() - 1)));
    final long p99 = sorted.get(Math.max(0, Math.min(p99Index, sorted.size() - 1)));

    final long meanCount =
        counts == null || counts.isEmpty()
            ? 0
            : (long) counts.stream().mapToInt(v -> v).average().orElse(0);

    return new ProfileStatistics(min, max, mean, p90, p99, meanCount);
  }

  private Comparator<ProfileRow> profileRowComparator(String sortKey) {
    switch (sortKey == null ? "total_time" : sortKey.toLowerCase()) {
      case "num_eval":
        return Comparator.comparingInt((ProfileRow r) -> r.numEval).reversed();
      case "location":
        return Comparator.comparing((ProfileRow r) -> r.location);
      case "total_time":
      default:
        return Comparator.comparingLong((ProfileRow r) -> r.durationNanos).reversed();
    }
  }

  private Comparator<Map.Entry<String, ProfileStatistics>> profileStatisticsComparator(
      String sortKey) {
    switch (sortKey == null ? "total_time" : sortKey.toLowerCase()) {
      case "location":
        return Map.Entry.comparingByKey();
      case "num_eval":
        return Comparator.comparingLong(
            (Map.Entry<String, ProfileStatistics> e) -> e.getValue().meanCount)
            .reversed();
      case "total_time":
      default:
        return Comparator.comparingLong(
            (Map.Entry<String, ProfileStatistics> e) -> e.getValue().mean)
            .reversed();
    }
  }

  private void printProfileBorder(int col1Width, int col2Width, int col3Width) {
    System.out.print("+");
    System.out.print("-".repeat(col1Width));
    System.out.print("+");
    System.out.print("-".repeat(col2Width));
    System.out.print("+");
    System.out.print("-".repeat(col3Width));
    System.out.println("+");
  }

  private void printProfileRow(
      String col1, String col2, String col3, int col1Width, int col2Width, int col3Width) {
    System.out.printf(
        "| %-"
            + (col1Width - 2)
            + "s | %-"
            + (col2Width - 2)
            + "s | %-"
            + (col3Width - 2)
            + "s |%n",
        col1,
        col2,
        col3);
  }

  private void printStatisticalProfileBorder(
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

  private void printStatisticalProfileRow(
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

  private static class ProfileRow {
    final String time;
    final int numEval;
    final String location;
    final long durationNanos;

    ProfileRow(String time, int numEval, String location, long durationNanos) {
      this.time = time;
      this.numEval = numEval;
      this.location = location;
      this.durationNanos = durationNanos;
    }
  }

  private static class ProfileStatistics {
    final long min;
    final long max;
    final long mean;
    final long p90;
    final long p99;
    final long meanCount;

    ProfileStatistics(long min, long max, long mean, long p90, long p99, long meanCount) {
      this.min = min;
      this.max = max;
      this.mean = mean;
      this.p90 = p90;
      this.p99 = p99;
      this.meanCount = meanCount;
    }
  }
}
