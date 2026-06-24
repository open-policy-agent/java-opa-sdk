package io.github.open_policy_agent.opa.ir.stmts;

import java.util.Arrays;
import java.util.Objects;
import io.github.open_policy_agent.opa.ir.policy.Block;

/**
 * ScanStmt represents a linear scan over a composite value. The source may be a scalar in which
 * case the block will never execute.
 */
public class ScanStmt extends BaseStmt {

    private int source;

    private int key;

    private int value;

    private Block block;

    public ScanStmt() {
    }

    public ScanStmt(int source, int key, int value, Block block) {
        this.source = source;
        this.key = key;
        this.value = value;
        this.block = block;
    }

  @Override
  public StmtType getType() {
    return StmtType.SCAN;
    }

  @Override
  public int maxLocal() {

    assert block != null;
    return Arrays.stream(new int[] {source, key, value, block.maxLocal()}).max().orElse(-1);
  }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ScanStmt scanStmt = (ScanStmt) o;

        if (source != scanStmt.source) return false;
        if (key != scanStmt.key) return false;
        if (value != scanStmt.value) return false;
        if (getFile() != scanStmt.getFile()) return false;
        if (getCol() != scanStmt.getCol()) return false;
        if (getRow() != scanStmt.getRow()) return false;
        return Objects.equals(block, scanStmt.block);
    }

    @Override
    public int hashCode() {
        int result = source;
        result = 31 * result + key;
        result = 31 * result + value;
        result = 31 * result + (block != null ? block.hashCode() : 0);
        result = 31 * result + getFile();
        result = 31 * result + getCol();
        result = 31 * result + getRow();
        return result;
    }

    @Override
    public String toString() {
        return "ScanStmt{"
                + "source="
                + source
                + ", key="
                + key
                + ", value="
                + value
                + ", block="
                + block
                + ", file="
                + getFile()
                + ", col="
                + getCol()
                + ", row="
                + getRow()
                + '}';
    }

    public int getSource() {
        return source;
    }

    public void setSource(int source) {
        this.source = source;
    }

    public int getKey() {
        return key;
    }

    public void setKey(int key) {
        this.key = key;
    }

    public int getValue() {
        return value;
    }

    public void setValue(int value) {
        this.value = value;
    }

    public Block getBlock() {
        return block;
    }

    public void setBlock(Block block) {
        this.block = block;
    }
}
