package io.github.open_policy_agent.opa.ir.stmts;

import java.util.Objects;
import io.github.open_policy_agent.opa.ir.policy.Block;

/**
 * NotStmt represents a negated statement.
 */
public class NotStmt extends BaseStmt {

    private Block block;

    public NotStmt() {
    }

    public NotStmt(Block block) {
        this.block = block;
    }


    public Block getBlock() {
        return block;
    }

    public void setBlock(Block block) {
        this.block = block;
    }

  @Override
  public StmtType getType() {
    return StmtType.NOT;
    }

  @Override
  public int maxLocal() {
    if (block != null) {
      return block.maxLocal();
    }
    return -1;
  }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        NotStmt notStmt = (NotStmt) o;

        if (getFile() != notStmt.getFile()) return false;
        if (getCol() != notStmt.getCol()) return false;
        if (getRow() != notStmt.getRow()) return false;
        return Objects.equals(block, notStmt.block);
    }

    @Override
    public int hashCode() {
        int result = block != null ? block.hashCode() : 0;
        result = 31 * result + getFile();
        result = 31 * result + getCol();
        result = 31 * result + getRow();
        return result;
    }

    @Override
    public String toString() {
        return "NotStmt{"
                + "block="
                + block
                + ", file="
                + getFile()
                + ", col="
                + getCol()
                + ", row="
                + getRow()
                + '}';
    }
}
