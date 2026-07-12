package io.github.open_policy_agent.opa.metrics;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

// TODO: Take another pass through these interfaces and make sure it lines up with what
// we can actually provide. As-is these are largely copied from the Go code (ideally
// there is a path to parity, but needs more investigation).
public interface Metrics {
  String name();

  Timer timer(String name);

  Histogram histogram(String name);

  Counter counter(String name);

  Map<String, Metric> all();

  void clear();

  interface Metric {
    // TODO: Lift out some common things here?
  }

  interface Timer extends Metric {
    void start();

    void stop();

    Duration value();
  }

  interface Counter extends Metric {
    void add(int value);

    void incr();

    int value();
  }

  interface Histogram extends Metric {
    void update(double value);

    Values value();

    class Values {
      public int count;
      public int min;
      public int max;
      public int mean;
      public int stddev;
      public int median;
      public HashMap<String, Integer> percentiles;
    }
  }
}
