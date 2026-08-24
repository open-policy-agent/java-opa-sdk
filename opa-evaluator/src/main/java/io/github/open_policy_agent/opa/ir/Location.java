package io.github.open_policy_agent.opa.ir;

import io.github.open_policy_agent.opa.ir.stmts.LocationStmt;

/**
 * Records the file index, and the row and column inside that file that a statement can be connected
 * to.
 */
public class Location implements LocationStmt {
  private int file; // index of source filename where this statement originated

  private int col; // column in the source file where this statement originated

  private int row; // row in the source file where this statement originated

  private int endCol; // column one past the last rune of this statement's source text

  private int endRow; // row of the last rune of this statement's source text

  public Location(int file, int col, int row) {
    // For callers that do not have end location data.
    this(file, col, row, col, row);
  }

  public Location(int file, int col, int row, int endCol, int endRow) {
    this.file = file;
    this.col = col;
    this.row = row;
    this.endCol = endCol;
    this.endRow = endRow;
  }

  public Location() {}

  public int getFile() {
    return file;
  }

  public Location setFile(int file) {
    this.file = file;
    return this;
  }

  public int getCol() {
    return col;
  }

  public Location setCol(int col) {
    this.col = col;
    return this;
  }

  public int getRow() {
    return row;
  }

  public Location setRow(int row) {
    this.row = row;
    return this;
  }

  public int getEndCol() {
    return endCol;
  }

  public int getEndRow() {
    return endRow;
  }

  @Override
  public void setLocation(int file, int row, int col, int endRow, int endCol) {
    this.file = file;
    this.col = col;
    this.row = row;
    this.endCol = endCol;
    this.endRow = endRow;
  }

  @Override
  public Location getLocation() {
    return this;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    Location location = (Location) o;
    if (file != location.file) {
      return false;
    }
    if (col != location.col) {
      return false;
    }
    if (row != location.row) {
      return false;
    }
    if (endCol != location.endCol) {
      return false;
    }
    return endRow == location.endRow;
  }

  @Override
  public int hashCode() {
    int result = file;
    result = 31 * result + col;
    result = 31 * result + row;
    result = 31 * result + endCol;
    result = 31 * result + endRow;
    return result;
  }

  @Override
  public String toString() {
    return "Location{"
        + "file=" + file
        + ", col=" + col
        + ", row=" + row
        + ", endCol=" + endCol
        + ", endRow=" + endRow
        + '}';
  }
}
