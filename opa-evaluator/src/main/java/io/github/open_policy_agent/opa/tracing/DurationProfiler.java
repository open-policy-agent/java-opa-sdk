package io.github.open_policy_agent.opa.tracing;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import io.github.open_policy_agent.opa.ir.Location;

/**
 * {@link Profiler} implementation that records exclusive execution time per source location.
 *
 * <p>Uses a backoff stack so the time reported for a statement excludes time spent in nested child
 * statements, matching what users expect from a "time spent on line N" view.
 */
public class DurationProfiler implements Profiler {
  private final Map<Loc, EvalTotal> durations = new HashMap<>();

  private final Deque<Duration> backoffs = new ArrayDeque<>();

  @Override
  public void addStart() {
    backoffs.push(Duration.ZERO);
  }

  @Override
  public void addEntry(Location location, long duration) {
    // need to back off all the child times so just the actual statement time is reported
    Duration backoff = backoffs.pop();
    if (!backoffs.isEmpty()) {
      Duration adjusted = backoffs.pop().plus(Duration.ofNanos(duration));
      backoffs.push(adjusted);
    }
    Duration myTime = Duration.ofNanos(duration).minus(backoff);
    Loc loc = Loc.fromLocation(location);
    if (!durations.containsKey(loc)) {
      durations.put(loc, new EvalTotal());
    }
    durations.put(loc, durations.get(loc).plus(myTime));
  }

  public Map<Loc, EvalTotal> getDurations() {
    return durations;
  }

  public static final class Loc {
    private final int file;
    private final int row;

    Loc(int file, int row) {
      this.file = file;
      this.row = row;
    }

    static Loc fromLocation(Location location) {
      return new Loc(location.getFile(), location.getRow());
    }

    public int getFile() {
      return file;
    }

    public int getRow() {
      return row;
    }

    @Override
    public boolean equals(Object o) {
      if (!(o instanceof Loc)) return false;

      Loc loc = (Loc) o;
      return file == loc.file && row == loc.row;
    }

    @Override
    public int hashCode() {
      int result = file;
      result = 31 * result + row;
      return result;
    }
  }

  public static final class EvalTotal {
    private int count = 0;
    private Duration totalDuration = Duration.ZERO;

    public EvalTotal plus(Duration duration) {
      count++;
      totalDuration = totalDuration.plus(duration);
      return this;
    }

    public int getCount() {
      return count;
    }

    public Duration getTotalDuration() {
      return totalDuration;
    }
  }
}
