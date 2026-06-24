package io.github.open_policy_agent.opa.ir.stmts;

/**
 * Represents an operation (e.g., comparison, loop, dot, etc.) to execute.
 */
public interface Stmt extends LocationStmt {

  StmtType getType();

  enum StmtType {
    ARRAY_APPEND("ArrayAppendStmt"),
    ASSIGN_INT("AssignIntStmt"),
    ASSIGN_VAR_ONCE("AssignVarOnceStmt"),
    ASSIGN_VAR("AssignVarStmt"),
    BLOCK("BlockStmt"),
    BREAK("BreakStmt"),
    CALL_DYNAMIC("CallDynamicStmt"),
    CALL("CallStmt"),
    DOT("DotStmt"),
    EQUAL("EqualStmt"),
    IS_ARRAY("IsArrayStmt"),
    IS_DEFINED("IsDefinedStmt"),
    IS_OBJECT("IsObjectStmt"),
    IS_SET("IsSetStmt"),
    IS_UNDEFINED("IsUndefinedStmt"),
    LEN("LenStmt"),
    MAKE_ARRAY("MakeArrayStmt"),
    MAKE_NULL("MakeNullStmt"),
    MAKE_NUMBER_INT("MakeNumberIntStmt"),
    MAKE_NUMBER_REF("MakeNumberRefStmt"),
    MAKE_OBJECT("MakeObjectStmt"),
    MAKE_SET("MakeSetStmt"),
    NOP("NopStmt"),
    NOT_EQUAL("NotEqualStmt"),
    NOT("NotStmt"),
    OBJECT_INSERT_ONCE("ObjectInsertOnceStmt"),
    OBJECT_INSERT("ObjectInsertStmt"),
    OBJECT_MERGE("ObjectMergeStmt"),
    RESET_LOCAL("ResetLocalStmt"),
    RESULT_SET_ADD("ResultSetAddStmt"),
    RETURN_LOCAL("ReturnLocalStmt"),
    SCAN("ScanStmt"),
    SET_ADD("SetAddStmt"),
    WITH("WithStmt");

    private final String typeName;

    StmtType(String typeName) {
      this.typeName = typeName;
    }

    public static StmtType fromTypeName(String typeName) {
      for (StmtType type : StmtType.values()) {
        if (type.typeName.equals(typeName)) {
          return type;
        }
      }
      return null;
    }

    public String getTypeName() {
      return typeName;
    }
  }

  int maxLocal();
}
