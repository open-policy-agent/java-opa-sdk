package io.github.open_policy_agent.opa.ir.stmts;

import java.util.List;
import java.util.Objects;
import io.github.open_policy_agent.opa.ir.policy.Block;

/**
 * BlockStmt represents a nested block. Nested blocks and break statements can be used to
 * short-circuit execution.
 */
public class BlockStmt extends BaseStmt {

    private List<Block> blocks;

    public BlockStmt() {
    }

    public BlockStmt(List<Block> blocks) {
        this.blocks = blocks;
    }

    public BlockStmt(int file, int col, int row) {
        super(file, col, row);
    }


    public List<Block> getBlocks() {
        return blocks;
    }

    public void setBlocks(List<Block> blocks) {
        this.blocks = blocks;
    }

  @Override
  public StmtType getType() {
    return StmtType.BLOCK;
    }

  @Override
  public int maxLocal() {
    if (blocks == null) {
      return -1;
    }
    return blocks.stream()
        .filter(Objects::nonNull)
        .map(Block::maxLocal)
        .max(Integer::compare)
        .orElse(-1);
  }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        BlockStmt blockStmt = (BlockStmt) o;

        if (getFile() != blockStmt.getFile()) return false;
        if (getCol() != blockStmt.getCol()) return false;
        if (getRow() != blockStmt.getRow()) return false;
        return Objects.equals(blocks, blockStmt.blocks);
    }

    @Override
    public int hashCode() {
        int result = blocks != null ? blocks.hashCode() : 0;
        result = 31 * result + getFile();
        result = 31 * result + getCol();
        result = 31 * result + getRow();
        return result;
    }

    @Override
    public String toString() {
        return "BlockStmt{"
                + "blocks="
                + blocks
                + ", file="
                + getFile()
                + ", col="
                + getCol()
                + ", row="
                + getRow()
                + '}';
    }
}
