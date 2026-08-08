package io.github.open_policy_agent.opa.proto;

import io.github.open_policy_agent.opa.ir.Operand;
import io.github.open_policy_agent.opa.ir.policy.Block;
import io.github.open_policy_agent.opa.ir.policy.BuiltinFunc;
import io.github.open_policy_agent.opa.ir.policy.Func;
import io.github.open_policy_agent.opa.ir.policy.Funcs;
import io.github.open_policy_agent.opa.ir.policy.Plan;
import io.github.open_policy_agent.opa.ir.policy.Plans;
import io.github.open_policy_agent.opa.ir.policy.Policy;
import io.github.open_policy_agent.opa.ir.policy.Static;
import io.github.open_policy_agent.opa.ir.policy.StringConst;
import io.github.open_policy_agent.opa.ir.stmts.ArrayAppendStmt;
import io.github.open_policy_agent.opa.ir.stmts.AssignIntStmt;
import io.github.open_policy_agent.opa.ir.stmts.AssignVarOnceStmt;
import io.github.open_policy_agent.opa.ir.stmts.AssignVarStmt;
import io.github.open_policy_agent.opa.ir.stmts.BaseStmt;
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
import io.github.open_policy_agent.opa.ir.stmts.WithStmt;
import io.github.open_policy_agent.opa.ir.vals.BoolVal;
import io.github.open_policy_agent.opa.ir.vals.LocalVal;
import io.github.open_policy_agent.opa.ir.vals.StringIndexVal;
import io.github.open_policy_agent.opa.ir.vals.Val;
import java.util.ArrayList;
import java.util.List;

/**
 * Maps a decoded proto {@code opa.ir.v1.Policy} (from {@code plan.pb}) into the SDK's IR {@link
 * Policy} model — the same model the JSON {@code PolicyReader} produces — so the evaluator runs
 * identically regardless of the plan's wire format.
 *
 * <p>The proto types (generated under {@code opa.ir.v1}) collide by simple name with several SDK
 * model types ({@code Policy}, {@code Static}, {@code Block}, {@code Stmt}, {@code Operand}, {@code
 * Val}). To keep the SDK types imported and readable, proto payloads are bound with {@code var}
 * rather than named explicitly.
 */
final class PlanMapper {

  private PlanMapper() {}

  static Policy toPolicy(opa.ir.v1.Policy proto) {
    Static staticField = toStatic(proto.getStatic());
    Plans plans = toPlans(proto.getPlans());
    Funcs funcs = toFuncs(proto.getFuncs());
    return new Policy(staticField, plans, funcs);
  }

  private static Static toStatic(opa.ir.v1.Static proto) {
    List<StringConst> strings = new ArrayList<>(proto.getStringsCount());
    for (var s : proto.getStringsList()) {
      strings.add(new StringConst(s.getValue()));
    }
    List<BuiltinFunc> builtinFuncs = new ArrayList<>(proto.getBuiltinFuncsCount());
    for (var b : proto.getBuiltinFuncsList()) {
      // The proto intentionally omits the builtin's type signature (Decl); consumers dispatch
      // builtins through their own registry. Leave decl null, matching a JSON plan without a decl.
      builtinFuncs.add(new BuiltinFunc(b.getName(), null));
    }
    List<StringConst> files = new ArrayList<>(proto.getFilesCount());
    for (var f : proto.getFilesList()) {
      files.add(new StringConst(f.getValue()));
    }
    return new Static(strings, builtinFuncs, files);
  }

  private static Plans toPlans(opa.ir.v1.Plans proto) {
    List<Plan> plans = new ArrayList<>(proto.getPlansCount());
    for (var p : proto.getPlansList()) {
      plans.add(new Plan(p.getName(), toBlocks(p.getBlocksList())));
    }
    return new Plans(plans);
  }

  private static Funcs toFuncs(opa.ir.v1.Funcs proto) {
    List<Func> funcs = new ArrayList<>(proto.getFuncsCount());
    for (var f : proto.getFuncsList()) {
      funcs.add(
          new Func(
              f.getName(),
              copyInts(f.getParamsList()),
              f.getResult(),
              toBlocks(f.getBlocksList()),
              new ArrayList<>(f.getPathList())));
    }
    return new Funcs(funcs);
  }

  private static List<Block> toBlocks(List<opa.ir.v1.Block> protoBlocks) {
    List<Block> blocks = new ArrayList<>(protoBlocks.size());
    for (var b : protoBlocks) {
      blocks.add(toBlock(b));
    }
    return blocks;
  }

  private static Block toBlock(opa.ir.v1.Block proto) {
    List<Stmt> stmts = new ArrayList<>(proto.getStmtsCount());
    for (var s : proto.getStmtsList()) {
      stmts.add(toStmt(s));
    }
    return new Block(stmts);
  }

  private static Stmt toStmt(opa.ir.v1.Stmt s) {
    Stmt out;
    switch (s.getKindCase()) {
      case ARRAY_APPEND_STMT -> {
        var x = s.getArrayAppendStmt();
        out = new ArrayAppendStmt(operand(x.getValue()), x.getArray());
      }
      case ASSIGN_INT_STMT -> {
        var x = s.getAssignIntStmt();
        out = new AssignIntStmt(x.getValue(), x.getTarget());
      }
      case ASSIGN_VAR_ONCE_STMT -> {
        var x = s.getAssignVarOnceStmt();
        out = new AssignVarOnceStmt(operand(x.getSource()), x.getTarget());
      }
      case ASSIGN_VAR_STMT -> {
        var x = s.getAssignVarStmt();
        out = new AssignVarStmt(operand(x.getSource()), x.getTarget());
      }
      case BLOCK_STMT -> {
        var x = s.getBlockStmt();
        out = new BlockStmt(toBlocks(x.getBlocksList()));
      }
      case BREAK_STMT -> {
        var x = s.getBreakStmt();
        out = new BreakStmt(x.getIndex());
      }
      case CALL_DYNAMIC_STMT -> {
        var x = s.getCallDynamicStmt();
        out = new CallDynamicStmt(operands(x.getPathList()), copyInts(x.getArgsList()), x.getResult());
      }
      case CALL_STMT -> {
        var x = s.getCallStmt();
        out = new CallStmt(x.getFunction(), operands(x.getArgsList()), x.getResult());
      }
      case DOT_STMT -> {
        var x = s.getDotStmt();
        out = new DotStmt(operand(x.getSource()), operand(x.getKey()), x.getTarget());
      }
      case EQUAL_STMT -> {
        var x = s.getEqualStmt();
        out = new EqualStmt(operand(x.getA()), operand(x.getB()));
      }
      case IS_ARRAY_STMT -> {
        var x = s.getIsArrayStmt();
        out = new IsArrayStmt(operand(x.getSource()));
      }
      case IS_DEFINED_STMT -> {
        var x = s.getIsDefinedStmt();
        out = new IsDefinedStmt(x.getSource());
      }
      case IS_OBJECT_STMT -> {
        var x = s.getIsObjectStmt();
        out = new IsObjectStmt(operand(x.getSource()));
      }
      case IS_SET_STMT -> {
        var x = s.getIsSetStmt();
        out = new IsSetStmt(operand(x.getSource()));
      }
      case IS_UNDEFINED_STMT -> {
        var x = s.getIsUndefinedStmt();
        out = new IsUndefinedStmt(x.getSource());
      }
      case LEN_STMT -> {
        var x = s.getLenStmt();
        out = new LenStmt(operand(x.getSource()), x.getTarget());
      }
      case MAKE_ARRAY_STMT -> {
        var x = s.getMakeArrayStmt();
        out = new MakeArrayStmt(x.getCapacity(), x.getTarget());
      }
      case MAKE_NULL_STMT -> {
        var x = s.getMakeNullStmt();
        out = new MakeNullStmt(x.getTarget());
      }
      case MAKE_NUMBER_INT_STMT -> {
        var x = s.getMakeNumberIntStmt();
        out = new MakeNumberIntStmt(x.getValue(), x.getTarget());
      }
      case MAKE_NUMBER_REF_STMT -> {
        var x = s.getMakeNumberRefStmt();
        out = new MakeNumberRefStmt(x.getIndex(), x.getTarget());
      }
      case MAKE_OBJECT_STMT -> {
        var x = s.getMakeObjectStmt();
        out = new MakeObjectStmt(x.getTarget());
      }
      case MAKE_SET_STMT -> {
        var x = s.getMakeSetStmt();
        out = new MakeSetStmt(x.getTarget());
      }
      case NOP_STMT -> out = new NopStmt();
      case NOT_EQUAL_STMT -> {
        var x = s.getNotEqualStmt();
        out = new NotEqualStmt(operand(x.getA()), operand(x.getB()));
      }
      case NOT_STMT -> {
        var x = s.getNotStmt();
        out = new NotStmt(toBlock(x.getBlock()));
      }
      case OBJECT_INSERT_ONCE_STMT -> {
        var x = s.getObjectInsertOnceStmt();
        out = new ObjectInsertOnceStmt(operand(x.getKey()), operand(x.getValue()), x.getObject());
      }
      case OBJECT_INSERT_STMT -> {
        var x = s.getObjectInsertStmt();
        out = new ObjectInsertStmt(operand(x.getKey()), operand(x.getValue()), x.getObject());
      }
      case OBJECT_MERGE_STMT -> {
        var x = s.getObjectMergeStmt();
        out = new ObjectMergeStmt(x.getA(), x.getB(), x.getTarget());
      }
      case RESET_LOCAL_STMT -> {
        var x = s.getResetLocalStmt();
        out = new ResetLocalStmt(x.getTarget());
      }
      case RESULT_SET_ADD_STMT -> {
        var x = s.getResultSetAddStmt();
        out = new ResultSetAddStmt(x.getValue());
      }
      case RETURN_LOCAL_STMT -> {
        var x = s.getReturnLocalStmt();
        out = new ReturnLocalStmt(x.getSource());
      }
      case SCAN_STMT -> {
        var x = s.getScanStmt();
        out = new ScanStmt(x.getSource(), x.getKey(), x.getValue(), toBlock(x.getBlock()));
      }
      case SET_ADD_STMT -> {
        var x = s.getSetAddStmt();
        out = new SetAddStmt(operand(x.getValue()), x.getSet());
      }
      case WITH_STMT -> {
        var x = s.getWithStmt();
        out =
            new WithStmt(
                x.getLocal(), copyInts(x.getPathList()), operand(x.getValue()), toBlock(x.getBlock()));
      }
      case KIND_NOT_SET -> throw new IllegalArgumentException("statement has no kind set");
      default -> throw new IllegalArgumentException("unsupported statement kind: " + s.getKindCase());
    }

    // The source-location triple lives on the proto Stmt envelope; the SDK carries it on BaseStmt.
    if (out instanceof BaseStmt base) {
      base.setFile(s.getFile());
      base.setCol(s.getCol());
      base.setRow(s.getRow());
    }
    return out;
  }

  private static List<Operand> operands(List<opa.ir.v1.Operand> protoOperands) {
    List<Operand> out = new ArrayList<>(protoOperands.size());
    for (var o : protoOperands) {
      out.add(operand(o));
    }
    return out;
  }

  private static Operand operand(opa.ir.v1.Operand proto) {
    return new Operand(val(proto.getValue()));
  }

  private static Val val(opa.ir.v1.Val v) {
    return switch (v.getKindCase()) {
      case BOOL -> new BoolVal(v.getBool());
      case LOCAL -> new LocalVal(v.getLocal());
      case STRING_INDEX -> new StringIndexVal(v.getStringIndex());
      case KIND_NOT_SET -> throw new IllegalArgumentException("operand value has no kind set");
    };
  }

  private static List<Integer> copyInts(List<Integer> ints) {
    return new ArrayList<>(ints);
  }
}
