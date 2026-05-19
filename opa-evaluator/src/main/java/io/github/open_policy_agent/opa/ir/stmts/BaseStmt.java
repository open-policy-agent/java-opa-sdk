package io.github.open_policy_agent.opa.ir.stmts;

import io.github.open_policy_agent.opa.ir.Location;
import io.github.open_policy_agent.opa.ir.Operand;
import io.github.open_policy_agent.opa.ir.vals.LocalVal;

public abstract class BaseStmt implements Stmt {
    private int file; // index of source filename
    private int col; // column in the source file
    private int row; // row in the source file
    private Location cachedLocation;

    protected BaseStmt(int file, int col, int row) {
        this.file = file;
        this.col = col;
        this.row = row;
    }

    protected BaseStmt() {
    }

    @Override
    public Location setLocation(int file, int row, int col) {
        this.file = file;
        this.col = col;
        this.row = row;
        Location loc = new Location(file, col, row);
        this.cachedLocation = loc;
        return loc;
    }

    @Override
    public Location getLocation() {
        Location loc = cachedLocation;
        if (loc == null) {
            loc = new Location(file, col, row);
            cachedLocation = loc;
        }
        return loc;
    }

    public int getFile() {
        return file;
    }

    public void setFile(int file) {
        this.file = file;
        this.cachedLocation = null;
    }

    public int getCol() {
        return col;
    }

    public void setCol(int col) {
        this.col = col;
        this.cachedLocation = null;
    }

    public int getRow() {
        return row;
    }

    public void setRow(int row) {
        this.row = row;
        this.cachedLocation = null;
    }

  /** Helper method to extract local value from an Operand if it contains a LocalVal */
  protected int getLocalFromOperand(Operand operand) {
    if (operand != null && operand.getValue() instanceof LocalVal) {
      return ((LocalVal) operand.getValue()).getValue();
    }
    return -1;
  }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        BaseStmt baseStmt = (BaseStmt) o;

        if (file != baseStmt.file) return false;
        if (col != baseStmt.col) return false;
        return row == baseStmt.row;
    }

    @Override
    public int hashCode() {
        int result = file;
        result = 31 * result + col;
        result = 31 * result + row;
        return result;
    }
}
