package io.github.open_policy_agent.opa.ir.policy;

import java.util.List;
import java.util.Objects;
import io.github.open_policy_agent.opa.ir.stmts.Stmt;

/**
 * Represents an ordered sequence of statements to execute.
 *
 * <p>Blocks are executed until one of the following conditions is met:
 *
 * <ul>
 *   <li>A return statement is encountered
 *   <li>A statement is undefined
 *   <li>There are no more statements
 * </ul>
 *
 * <p>If all statements are defined but no return statement is encountered, the block is considered
 * undefined.
 */
public class Block {

  private List<Stmt> stmts;

  public Block(List<Stmt> stmts) {
    this.stmts = stmts;
  }

  public Block() {}

  public List<Stmt> getStmts() {
    return stmts;
  }

  public int maxLocal() {
    if (stmts == null) {
      return -1;
    }
    return stmts.stream()
        .filter(Objects::nonNull)
        .map(Stmt::maxLocal)
        .max(Integer::compare)
        .orElse(-1);
  }

  public void setStmts(List<Stmt> stmts) {
    this.stmts = stmts;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    Block block = (Block) o;

    return Objects.equals(stmts, block.stmts);
  }

  @Override
  public int hashCode() {
    return stmts != null ? stmts.hashCode() : 0;
  }

  @Override
  public String toString() {
    return "Block{" + "stmts=" + stmts + '}';
  }
}
