package io.github.open_policy_agent.opa.ir.stmts;

import java.util.Objects;
import io.github.open_policy_agent.opa.ir.Operand;

/**
 * ObjectInsertOnceStmt represents a dynamic insert operation of a key/value pair into an object. If
 * the key already exists and the value differs, execution aborts with a conflict error.
 */
public class ObjectInsertOnceStmt extends BaseStmt {
    public static final String StmtType = "ObjectInsertOnceStmt";

    private Operand key;

    private Operand value;

    private int object;

    public ObjectInsertOnceStmt() {
    }

    public ObjectInsertOnceStmt(Operand key, Operand value, int object) {
        this.key = key;
        this.value = value;
        this.object = object;
    }

    public Operand getKey() {
        return key;
    }

    public void setKey(Operand key) {
        this.key = key;
    }

    public Operand getValue() {
        return value;
    }

    public void setValue(Operand value) {
        this.value = value;
    }

    public int getObject() {
        return object;
    }

    public void setObject(int object) {
        this.object = object;
    }

  @Override
  public STMT_TYPE getType() {
    return STMT_TYPE.OBJECT_INSERT_ONCE;
    }

  @Override
  public int maxLocal() {
    int max = object;
    int keyLocal = getLocalFromOperand(key);
    if (keyLocal > max) {
      max = keyLocal;
    }
    int valueLocal = getLocalFromOperand(value);
    if (valueLocal > max) {
      max = valueLocal;
    }
    return max;
  }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ObjectInsertOnceStmt that = (ObjectInsertOnceStmt) o;

        if (object != that.object) return false;
        if (getFile() != that.getFile()) return false;
        if (getCol() != that.getCol()) return false;
        if (getRow() != that.getRow()) return false;
        if (!Objects.equals(key, that.key)) return false;
        return Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        int result = key != null ? key.hashCode() : 0;
        result = 31 * result + (value != null ? value.hashCode() : 0);
        result = 31 * result + object;
        result = 31 * result + getFile();
        result = 31 * result + getCol();
        result = 31 * result + getRow();
        return result;
    }

    @Override
    public String toString() {
        return "ObjectInsertOnceStmt{"
                + "key="
                + key
                + ", value="
                + value
                + ", object="
                + object
                + ", file="
                + getFile()
                + ", col="
                + getCol()
                + ", row="
                + getRow()
                + '}';
    }
}
