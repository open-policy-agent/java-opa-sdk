package io.github.open_policy_agent.opa.jackson;

import io.github.open_policy_agent.opa.ir.stmts.ArrayAppendStmt;
import io.github.open_policy_agent.opa.ir.stmts.AssignIntStmt;
import io.github.open_policy_agent.opa.ir.stmts.AssignVarOnceStmt;
import io.github.open_policy_agent.opa.ir.stmts.AssignVarStmt;
import io.github.open_policy_agent.opa.ir.stmts.BlockStmt;
import io.github.open_policy_agent.opa.ir.stmts.BreakStmt;
import io.github.open_policy_agent.opa.ir.stmts.CallDynamicStmt;
import io.github.open_policy_agent.opa.ir.stmts.CallStmt;
import io.github.open_policy_agent.opa.ir.stmts.DotStmt;
import io.github.open_policy_agent.opa.ir.stmts.EqualStmt;
import io.github.open_policy_agent.opa.ir.stmts.IsArrayStmt;
import io.github.open_policy_agent.opa.ir.stmts.IsDefinedStmt;
import io.github.open_policy_agent.opa.ir.stmts.IsObjectStmt;
import io.github.open_policy_agent.opa.ir.stmts.IsSetStmt;
import io.github.open_policy_agent.opa.ir.stmts.IsUndefinedStmt;
import io.github.open_policy_agent.opa.ir.stmts.LenStmt;
import io.github.open_policy_agent.opa.ir.stmts.MakeArrayStmt;
import io.github.open_policy_agent.opa.ir.stmts.MakeNullStmt;
import io.github.open_policy_agent.opa.ir.stmts.MakeNumberIntStmt;
import io.github.open_policy_agent.opa.ir.stmts.MakeNumberRefStmt;
import io.github.open_policy_agent.opa.ir.stmts.MakeObjectStmt;
import io.github.open_policy_agent.opa.ir.stmts.MakeSetStmt;
import io.github.open_policy_agent.opa.ir.stmts.NopStmt;
import io.github.open_policy_agent.opa.ir.stmts.NotEqualStmt;
import io.github.open_policy_agent.opa.ir.stmts.NotStmt;
import io.github.open_policy_agent.opa.ir.stmts.ObjectInsertOnceStmt;
import io.github.open_policy_agent.opa.ir.stmts.ObjectInsertStmt;
import io.github.open_policy_agent.opa.ir.stmts.ObjectMergeStmt;
import io.github.open_policy_agent.opa.ir.stmts.ResetLocalStmt;
import io.github.open_policy_agent.opa.ir.stmts.ResultSetAddStmt;
import io.github.open_policy_agent.opa.ir.stmts.ReturnLocalStmt;
import io.github.open_policy_agent.opa.ir.stmts.ScanStmt;
import io.github.open_policy_agent.opa.ir.stmts.SetAddStmt;
import io.github.open_policy_agent.opa.ir.stmts.Stmt;
import io.github.open_policy_agent.opa.ir.stmts.Stmt.StmtType;
import io.github.open_policy_agent.opa.ir.stmts.WithStmt;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DatabindException;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ValueDeserializer;

import java.util.HashMap;
import java.util.Map;

class StmtDeserializer extends ValueDeserializer<Stmt> {
  // Maps JSON type strings to statement classes.
  // String values match the OPA IR spec and the StmtType enum's typeName values.
  private static final Map<String, Class<? extends Stmt>> STMT_REGISTRY =
      new HashMap<>() {
        {
          put(StmtType.ARRAY_APPEND.getTypeName(),       ArrayAppendStmt.class);
          put(StmtType.ASSIGN_VAR_ONCE.getTypeName(),     AssignVarOnceStmt.class);
          put(StmtType.ASSIGN_INT.getTypeName(),         AssignIntStmt.class);
          put(StmtType.ASSIGN_VAR.getTypeName(),         AssignVarStmt.class);
          put(StmtType.BLOCK.getTypeName(),             BlockStmt.class);
          put(StmtType.BREAK.getTypeName(),             BreakStmt.class);
          put(StmtType.CALL_DYNAMIC.getTypeName(),       CallDynamicStmt.class);
          put(StmtType.CALL.getTypeName(),              CallStmt.class);
          put(StmtType.DOT.getTypeName(),               DotStmt.class);
          put(StmtType.EQUAL.getTypeName(),             EqualStmt.class);
          put(StmtType.IS_ARRAY.getTypeName(),           IsArrayStmt.class);
          put(StmtType.IS_DEFINED.getTypeName(),         IsDefinedStmt.class);
          put(StmtType.IS_OBJECT.getTypeName(),          IsObjectStmt.class);
          put(StmtType.IS_UNDEFINED.getTypeName(),       IsUndefinedStmt.class);
          put(StmtType.IS_SET.getTypeName(),             IsSetStmt.class);
          put(StmtType.LEN.getTypeName(),               LenStmt.class);
          put(StmtType.MAKE_ARRAY.getTypeName(),         MakeArrayStmt.class);
          put(StmtType.MAKE_NULL.getTypeName(),          MakeNullStmt.class);
          put(StmtType.MAKE_NUMBER_INT.getTypeName(),     MakeNumberIntStmt.class);
          put(StmtType.MAKE_NUMBER_REF.getTypeName(),     MakeNumberRefStmt.class);
          put(StmtType.MAKE_OBJECT.getTypeName(),        MakeObjectStmt.class);
          put(StmtType.MAKE_SET.getTypeName(),           MakeSetStmt.class);
          put(StmtType.NOT_EQUAL.getTypeName(),          NotEqualStmt.class);
          put(StmtType.NOT.getTypeName(),               NotStmt.class);
          put(StmtType.OBJECT_INSERT_ONCE.getTypeName(),  ObjectInsertOnceStmt.class);
          put(StmtType.OBJECT_INSERT.getTypeName(),      ObjectInsertStmt.class);
          put(StmtType.OBJECT_MERGE.getTypeName(),       ObjectMergeStmt.class);
          put(StmtType.RESET_LOCAL.getTypeName(),        ResetLocalStmt.class);
          put(StmtType.RESULT_SET_ADD.getTypeName(),      ResultSetAddStmt.class);
          put(StmtType.RETURN_LOCAL.getTypeName(),       ReturnLocalStmt.class);
          put(StmtType.SCAN.getTypeName(),              ScanStmt.class);
          put(StmtType.SET_ADD.getTypeName(),            SetAddStmt.class);
          put(StmtType.WITH.getTypeName(),              WithStmt.class);
          put(StmtType.NOP.getTypeName(),               NopStmt.class);
        }
      };

  @Override
  public Stmt deserialize(JsonParser jp, DeserializationContext ctx) {
    JsonNode root = ctx.readTree(jp);
    JsonNode typeNode = root.get("type");
    if (typeNode == null) {
      return null;
    }

    String stmtType = typeNode.asText();
    Class<? extends Stmt> stmtClass = STMT_REGISTRY.get(stmtType);
    if (stmtClass == null) {
      throw DatabindException.from(jp, "unknown stmt type: " + stmtType);
    }

    JsonNode stmtNode = root.get("stmt");
    if (stmtNode == null) {
      throw DatabindException.from(jp, "missing stmt field for stmt: " + stmtType);
    }

    Stmt stmt = ctx.readTreeAsValue(stmtNode, stmtClass);

    JsonNode file = stmtNode.get("file");
    JsonNode row = stmtNode.get("row");
    JsonNode col = stmtNode.get("col");

    if (file != null && row != null && col != null) {
      stmt.setLocation(file.asInt(), row.asInt(), col.asInt());
    }

    return stmt;
  }
}
