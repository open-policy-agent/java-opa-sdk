package io.github.open_policy_agent.opa.ir.stmts;

import java.util.Objects;
import io.github.open_policy_agent.opa.ir.Operand;

/**
 * ObjectInsertStmt represents a dynamic insert operation of a key/value pair into an object.
 */
public class ObjectInsertStmt extends BaseStmt {

    private Operand key;

    private Operand value;

    private int object;

    private int source;

    public ObjectInsertStmt() {
    }

    public ObjectInsertStmt(Operand key, Operand value, int object) {
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
  public StmtType getType() {
    return StmtType.OBJECT_INSERT;
    }

  @Override
  public int maxLocal() {
    int max = object;
    if (source > max) {
      max = source;
    }
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

        ObjectInsertStmt that = (ObjectInsertStmt) o;

        if (object != that.object) return false;
        if (source != that.source) return false;
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
        result = 31 * result + source;
        result = 31 * result + getFile();
        result = 31 * result + getCol();
        result = 31 * result + getRow();
        return result;
    }

    @Override
    public String toString() {
        return "ObjectInsertStmt{"
                + "key="
                + key
                + ", value="
                + value
                + ", object="
                + object
                + ", source="
                + source
                + ", file="
                + getFile()
                + ", col="
                + getCol()
                + ", row="
                + getRow()
                + '}';
    }
}
