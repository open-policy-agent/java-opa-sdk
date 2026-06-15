package io.github.open_policy_agent.opa.cli;

import io.github.open_policy_agent.opa.tracing.CoverageProfiler;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public class CoverageReporter {

  public void printCoverage(List<CoverageProfiler> allProfilers, String[] fileNames) {
    if (allProfilers.isEmpty()) {
      return;
    }

    final Map<Integer, Set<Integer>> mergedHits = mergeHits(allProfilers);
    if (mergedHits.isEmpty()) {
      return;
    }

    int fileWidth = "FILE".length();
    int hitsWidth = "HITS".length();
    int linesWidth = "COVERED LINES".length();

    final List<CoverageRow> rows = new ArrayList<>();

    for (final Map.Entry<Integer, Set<Integer>> entry : mergedHits.entrySet()) {
      final int fileIdx = entry.getKey();
      final String fileRef = Format.fileRef(fileIdx, fileNames);
      final TreeSet<Integer> sortedRows = new TreeSet<>(entry.getValue());
      final String linesStr = formatRanges(sortedRows);
      final String hitsStr = String.valueOf(sortedRows.size());

      rows.add(new CoverageRow(fileRef, hitsStr, linesStr));

      fileWidth = Math.max(fileWidth, fileRef.length());
      hitsWidth = Math.max(hitsWidth, hitsStr.length());
      linesWidth = Math.max(linesWidth, linesStr.length());
    }

    fileWidth += 2;
    hitsWidth += 2;
    linesWidth += 2;

    rows.sort(Comparator.comparing(a -> a.file));

    printBorder(fileWidth, hitsWidth, linesWidth);
    printRow("FILE", "HITS", "COVERED LINES", fileWidth, hitsWidth, linesWidth);
    printBorder(fileWidth, hitsWidth, linesWidth);

    for (final CoverageRow row : rows) {
      printRow(row.file, row.hits, row.lines, fileWidth, hitsWidth, linesWidth);
    }

    printBorder(fileWidth, hitsWidth, linesWidth);
  }

  private Map<Integer, Set<Integer>> mergeHits(List<CoverageProfiler> profilers) {
    final java.util.HashMap<Integer, Set<Integer>> merged = new java.util.HashMap<>();
    for (final CoverageProfiler profiler : profilers) {
      for (final Map.Entry<Integer, Set<Integer>> entry : profiler.getCoveredLines().entrySet()) {
        merged.computeIfAbsent(entry.getKey(), k -> new TreeSet<>()).addAll(entry.getValue());
      }
    }
    return merged;
  }

  private String formatRanges(TreeSet<Integer> sortedRows) {
    if (sortedRows.isEmpty()) {
      return "";
    }
    final StringBuilder sb = new StringBuilder();
    Integer start = null;
    Integer prev = null;
    for (final Integer row : sortedRows) {
      if (start == null) {
        start = row;
      } else if (row != prev + 1) {
        appendRange(sb, start, prev);
        start = row;
      }
      prev = row;
    }
    appendRange(sb, start, prev);
    return sb.toString();
  }

  private void appendRange(StringBuilder sb, int start, int end) {
    if (sb.length() > 0) {
      sb.append(",");
    }
    if (start == end) {
      sb.append(start);
    } else {
      sb.append(start).append("-").append(end);
    }
  }

  private void printBorder(int col1Width, int col2Width, int col3Width) {
    System.out.print("+");
    System.out.print("-".repeat(col1Width));
    System.out.print("+");
    System.out.print("-".repeat(col2Width));
    System.out.print("+");
    System.out.print("-".repeat(col3Width));
    System.out.println("+");
  }

  private void printRow(
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

  private static class CoverageRow {
    final String file;
    final String hits;
    final String lines;

    CoverageRow(String file, String hits, String lines) {
      this.file = file;
      this.hits = hits;
      this.lines = lines;
    }
  }
}
