package io.github.open_policy_agent.opa.tracing;

import io.github.open_policy_agent.opa.ir.Location;

/**
 * A source range spanning {@code [start, end)}, mirroring {@code Range} in OPA's {@code v1/cover}.
 */
public final class Range implements Comparable<Range> {
  private final Position start;
  private final Position end;

  public Range(Position start, Position end) {
    this.start = start;
    this.end = end;
  }

  public Range(int startRow, int startCol, int endRow, int endCol) {
    this(new Position(startRow, startCol), new Position(endRow, endCol));
  }

  /** Builds a range from a statement {@link Location}
   * (end location based on start+text) */
  public static Range of(Location location) {
    return new Range(
        new Position(location.getRow(), location.getCol()),
        new Position(location.getEndRow(), location.getEndCol()));
  }

  public Position getStart() {
    return start;
  }

  public Position getEnd() {
    return end;
  }

  // Mirrors Range.contains in OPA's v1/cover: other is fully contained when this range starts at
  // or before it and ends at or after it (row compared before column, via Position.compareTo).
  public boolean contains(Range other) {
    return start.compareTo(other.start) <= 0 && end.compareTo(other.end) >= 0;
  }

  @Override
  public int compareTo(Range other) {
    int byStart = start.compareTo(other.start);

    if (byStart != 0) {
      return byStart;
    }

    return end.compareTo(other.end);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    Range range = (Range) o;

    return start.equals(range.start) && end.equals(range.end);
  }

  @Override
  public int hashCode() {
    return 31 * start.hashCode() + end.hashCode();
  }

  @Override
  public String toString() {
    return "Range{start=" + start + ", end=" + end + "}";
  }
}
