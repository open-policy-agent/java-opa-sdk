package io.github.open_policy_agent.opa.metrics;

import io.github.open_policy_agent.opa.metrics.Metrics.Counter;
import io.github.open_policy_agent.opa.metrics.Metrics.Histogram;
import io.github.open_policy_agent.opa.metrics.Metrics.Metric;
import io.github.open_policy_agent.opa.metrics.Metrics.Timer;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Pretty-prints a {@link Metrics} snapshot as a box-drawn two-column table that resembles the
 * output of {@code opa eval --metrics}.
 *
 * <p>Row keys follow the OPA CLI naming convention:
 *
 * <ul>
 *   <li>Timers are emitted as {@code timer_<key>_ns} with the duration in nanoseconds.
 *   <li>Counters are emitted as {@code counter_<key>} with the integer value.
 *   <li>Histograms are exploded into one row per stat: {@code histogram_<key>_count},
 *       {@code _min}, {@code _max}, {@code _mean}, {@code _stddev}, {@code _median}, plus one row
 *       per percentile ({@code _75%}, {@code _99%}, …).
 * </ul>
 *
 * <p>Rows are sorted alphabetically by display name. Column widths are sized to the widest cell.
 */
public class MetricsPrinter {

  private static final String NAME_HEADER = "Metric";
  private static final String VALUE_HEADER = "Value";

  public static void printMetrics(Metrics metrics, OutputStream out) {
    PrintWriter writer = new PrintWriter(out);

    SortedMap<String, String> rows = collectRows(metrics);
    int nameWidth = Math.max(NAME_HEADER.length(), maxLen(rows.keySet())) + 2;
    int valueWidth = Math.max(VALUE_HEADER.length(), maxLen(rows.values())) + 2;

    writer.append(border('┌', '┬', '┐', nameWidth, valueWidth)).append("\n");
    writer
        .append("│")
        .append(center(NAME_HEADER, nameWidth))
        .append("│")
        .append(center(VALUE_HEADER, valueWidth))
        .append("│\n");
    writer.append(border('├', '┼', '┤', nameWidth, valueWidth)).append("\n");
    for (Map.Entry<String, String> e : rows.entrySet()) {
      writer
          .append("│")
          .append(leftCell(e.getKey(), nameWidth))
          .append("│")
          .append(leftCell(e.getValue(), valueWidth))
          .append("│\n");
    }
    writer.append(border('└', '┴', '┘', nameWidth, valueWidth)).append("\n");

    writer.flush();
  }

  public static String metricsToString(Metrics metrics) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    printMetrics(metrics, out);
    return out.toString();
  }

  private static SortedMap<String, String> collectRows(Metrics metrics) {
    SortedMap<String, String> rows = new TreeMap<>();
    Map<String, Metric> all = metrics.all();
    if (all == null) {
      return rows;
    }
    for (Map.Entry<String, Metric> entry : all.entrySet()) {
      String key = entry.getKey();
      Metric m = entry.getValue();
      if (m instanceof Timer) {
        rows.put("timer_" + key + "_ns", String.valueOf(((Timer) m).value().toNanos()));
      } else if (m instanceof Counter) {
        rows.put("counter_" + key, String.valueOf(((Counter) m).value()));
      } else if (m instanceof Histogram) {
        Histogram.Values v = ((Histogram) m).value();
        if (v == null) {
          continue;
        }
        String prefix = "histogram_" + key + "_";
        rows.put(prefix + "count", String.valueOf(v.count));
        rows.put(prefix + "min", String.valueOf(v.min));
        rows.put(prefix + "max", String.valueOf(v.max));
        rows.put(prefix + "mean", String.valueOf(v.mean));
        rows.put(prefix + "stddev", String.valueOf(v.stddev));
        rows.put(prefix + "median", String.valueOf(v.median));
        if (v.percentiles != null) {
          for (Map.Entry<String, Integer> p : v.percentiles.entrySet()) {
            rows.put(prefix + p.getKey(), String.valueOf(p.getValue()));
          }
        }
      }
    }
    return rows;
  }

  private static int maxLen(Iterable<String> values) {
    int max = 0;
    for (String s : values) {
      if (s != null && s.length() > max) {
        max = s.length();
      }
    }
    return max;
  }

  private static String border(char left, char mid, char right, int nameWidth, int valueWidth) {
    return left + repeat('─', nameWidth) + mid + repeat('─', valueWidth) + right;
  }

  private static String center(String s, int width) {
    int total = width - s.length();
    int leftPad = total / 2;
    int rightPad = total - leftPad;
    return repeat(' ', leftPad) + s + repeat(' ', rightPad);
  }

  private static String leftCell(String s, int width) {
    // 1 leading space + content + trailing spaces, matching OPA CLI alignment.
    return " " + s + repeat(' ', width - 1 - s.length());
  }

  private static String repeat(char c, int n) {
    StringBuilder sb = new StringBuilder(n);
    for (int i = 0; i < n; i++) {
      sb.append(c);
    }
    return sb.toString();
  }
}
