package io.github.open_policy_agent.opa.ir.policy;

import java.util.List;
import java.util.Objects;

/**
 * Represents an ordered series of blocks to execute.
 *
 * <p>Plan execution stops when a return statement is reached. Blocks are executed in-order.
 */
public class Plan {

  private int maxLocal = Integer.MIN_VALUE;

  private String name;

  private List<Block> blocks;

  public Plan(String name, List<Block> blocks) {
    this.name = name;
    this.blocks = blocks;
  }

  public Plan() {}

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public List<Block> getBlocks() {
    return blocks;
  }

  public void setBlocks(List<Block> blocks) {
    this.blocks = blocks;
  }

  public int getMaxLocals() {
    if (maxLocal == Integer.MIN_VALUE) {
      maxLocal = blocks.stream().map(Block::maxLocal).max(Integer::compare).orElse(-1);
    }
    return maxLocal;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    Plan plan = (Plan) o;

    if (!Objects.equals(name, plan.name)) return false;
    return Objects.equals(blocks, plan.blocks);
  }

  @Override
  public int hashCode() {
    int result = name != null ? name.hashCode() : 0;
    result = 31 * result + (blocks != null ? blocks.hashCode() : 0);
    return result;
  }

  @Override
  public String toString() {
    return "Plan{" + "name='" + name + '\'' + ", blocks=" + blocks + '}';
  }
}
