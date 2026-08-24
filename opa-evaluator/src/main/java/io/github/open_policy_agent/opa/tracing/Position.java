package io.github.open_policy_agent.opa.tracing;

/** A {@code (row, col)} position in a source file, mirroring {@code Position} in OPA's {@code v1/cover}. */
public final class Position implements Comparable<Position> {
  private final int row;
  private final int col;

  public Position(int row, int col) {
    this.row = row;
    this.col = col;
  }

  public int getRow() {
    return row;
  }

  public int getCol() {
    return col;
  }

  @Override
  public int compareTo(Position other) {
    if (row != other.row) {
      return Integer.compare(row, other.row);
    }
    return Integer.compare(col, other.col);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }

    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    Position position = (Position) o;

    return row == position.row && col == position.col;
  }

  @Override
  public int hashCode() {
    return 31 * row + col;
  }

  @Override
  public String toString() {
    return "(" + row + "," + col + ")";
  }
}
