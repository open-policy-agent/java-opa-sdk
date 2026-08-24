package io.github.open_policy_agent.opa.ir.stmts;

import io.github.open_policy_agent.opa.ir.Location;

public interface LocationStmt {
    /**
     * Sets the  source location range for statement.
     */
    void setLocation(int file, int row, int col, int endRow, int endCol);

    /**
     * Sets the source location without an explicit end
     */
    default void setLocation(int file, int row, int col) {
        setLocation(file, row, col, row, col);
    }

    Location getLocation();
}
