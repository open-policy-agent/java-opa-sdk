package io.github.open_policy_agent.opa.ir.stmts;

import java.util.List;
import java.util.Objects;
import io.github.open_policy_agent.opa.ir.Operand;
import io.github.open_policy_agent.opa.ir.policy.Block;

/**
 * WithStmt replaces the Local or a portion of the document referred to by the Local with the Value
 * and executes the contained block. If the Path is non-empty, the Value is upserted into the Local.
 * If the intermediate nodes in the Local referred to by the Path do not exist, they will be
 * created. When the WithStmt finishes, the Local is reset to its original value.
 */
public class WithStmt extends BaseStmt {

    private int local;

    private List<Integer> path;

    private Operand value;

    private Block block;

    public WithStmt() {
    }

    public WithStmt(int local, List<Integer> path, Operand value, Block block) {
        this.local = local;
        this.path = path;
        this.value = value;
        this.block = block;
    }

    public int getLocal() {
        return local;
    }

    public void setLocal(int local) {
        this.local = local;
    }

    public List<Integer> getPath() {
        return path;
    }

    public void setPath(List<Integer> path) {
        this.path = path;
    }

    public Operand getValue() {
        return value;
    }

    public void setValue(Operand value) {
        this.value = value;
    }

    public Block getBlock() {
        return block;
    }

    public void setBlock(Block block) {
        this.block = block;
    }

  @Override
  public StmtType getType() {
    return StmtType.WITH;
    }

  @Override
  public int maxLocal() {
    int max = local;
    int valueLocal = getLocalFromOperand(value);
    if (valueLocal > max) {
      max = valueLocal;
    }
    if (block != null) {
      int blockMax = block.maxLocal();
      if (blockMax > max) {
        max = blockMax;
      }
    }
    return max;
  }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        WithStmt withStmt = (WithStmt) o;

        if (local != withStmt.local) return false;
        if (getFile() != withStmt.getFile()) return false;
        if (getCol() != withStmt.getCol()) return false;
        if (getRow() != withStmt.getRow()) return false;
        if (!Objects.equals(path, withStmt.path)) return false;
        if (!Objects.equals(value, withStmt.value)) return false;
        return Objects.equals(block, withStmt.block);
    }

    @Override
    public int hashCode() {
        int result = local;
        result = 31 * result + (path != null ? path.hashCode() : 0);
        result = 31 * result + (value != null ? value.hashCode() : 0);
        result = 31 * result + (block != null ? block.hashCode() : 0);
        result = 31 * result + getFile();
        result = 31 * result + getCol();
        result = 31 * result + getRow();
        return result;
    }

    @Override
    public String toString() {
        return "WithStmt{"
                + "local="
                + local
                + ", path="
                + path
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
}
