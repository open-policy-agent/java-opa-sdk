package io.github.open_policy_agent.opa.ir;

import java.math.BigInteger;
import java.util.*;
import java.util.stream.Collectors;
import io.github.open_policy_agent.opa.ast.types.*;
import io.github.open_policy_agent.opa.ir.policy.*;
import io.github.open_policy_agent.opa.ir.stmts.*;
import io.github.open_policy_agent.opa.ir.vals.BoolVal;
import io.github.open_policy_agent.opa.ir.vals.LocalVal;
import io.github.open_policy_agent.opa.ir.vals.StringIndexVal;
import io.github.open_policy_agent.opa.rego.EvaluationContext;
import io.github.open_policy_agent.opa.rego.TypeError;

public class Evaluator implements io.github.open_policy_agent.opa.rego.Evaluator {

  private final Policy policy;
  private final Map<String, Func> funcRegistry;
  private final ArrayList<String> staticStrings;

  // private constructor to enforce the use of the Builder
  private Evaluator(Builder builder) {
    this.policy = builder.policy;
    this.funcRegistry = builder.funcRegistry;
    this.staticStrings = builder.staticStrings;
  }

  public RegoValue[] evaluate(EvaluationContext ctx, RegoValue input, RegoObject data) {
    Plan plan = policy.getPlans().getPlanByName(ctx.entrypoint);

    if (plan == null) {
      throw new PolicyNotFoundException(ctx.entrypoint)
          .withContext("availablePlans", policy.getPlans().getPlans());
    }

    IREvaluationContext irCtx =
        new IREvaluationContext.Builder()
            .withContext(ctx)
            .withFunctions(this.funcRegistry)
            .withStaticStrings(this.staticStrings)
            .build();

    // Ensure frame capacity is at least 2 for input (index 0) and data (index 1)
    int capacity = plan.getMaxLocals() + 1;
    Frame frame = new Frame.Builder().withLocalCapacity(Math.max(capacity, 2)).build();
    frame.setLocal(0, input);
    frame.setLocal(1, data);
    frame.markDataDerived(1);

    int blockIndex = 0;
    for (Block block : plan.getBlocks()) {
      evaluateBlock(block, blockIndex, frame, irCtx);
      if (frame.shouldBreak()) {
        ctx.traceBreak();
        frame.decrementBreakTarget();
        break;
      }
      blockIndex++;
    }

    if (frame.getResultSet() != null) {
      return frame.getResultSet().toArray(new RegoValue[0]);
    }
    if (frame.getResult() == null) {
      return new RegoValue[0];
    }
    return new RegoValue[] {frame.getResult()};
  }

  /**
   * Optimized evaluation using a pre-warmed plan. This method skips plan lookup, frame capacity
   * calculation, and functionsByPath map construction by using pre-computed values from the
   * PreparedPlan.
   *
   * @param preparedPlan the pre-warmed plan containing cached values
   * @param ctx the evaluation context
   * @param input the input value
   * @param data the data object
   * @return array of result values
   */
  public RegoValue[] evaluate(
      PreparedPlan preparedPlan, EvaluationContext ctx, RegoValue input, RegoObject data) {
    // Use pre-computed functionsByPath map instead of building it every time
    IREvaluationContext irCtx =
        new IREvaluationContext.Builder()
            .withContext(ctx)
            .withFunctions(preparedPlan.getFuncRegistry())
            .withFunctionsByPath(preparedPlan.getFunctionsByPath())
            .withStaticStrings(preparedPlan.getStaticStrings())
            .build();

    // Use pre-computed frame capacity
    Frame frame = new Frame.Builder().withLocalCapacity(preparedPlan.getFrameCapacity()).build();
    frame.setLocal(0, input);
    frame.setLocal(1, data);
    frame.markDataDerived(1);

    // Use pre-looked-up plan
    int blockIndex = 0;
    for (Block block : preparedPlan.getPlan().getBlocks()) {
      evaluateBlock(block, blockIndex, frame, irCtx);
      if (frame.shouldBreak()) {
        ctx.traceBreak();
        frame.decrementBreakTarget();
        break;

      }
      blockIndex++;
    }

    if (frame.getResultSet() != null) {
      return frame.getResultSet().toArray(new RegoValue[0]);
    }
    if (frame.getResult() == null) {
      return new RegoValue[0];
    }
    return new RegoValue[] {frame.getResult()};
  }

  private boolean evaluateBlock(Block block, int blockIndex, Frame frame, IREvaluationContext ctx) {
    ctx.traceEnterBlock();

    try {
      int stmtIndex = 0;
      for (Stmt stmt : block.getStmts()) {
        if (stmt == null) {
          stmtIndex++;
          continue;
        }
        long startTime = System.nanoTime();
        ctx.statementProfiler.startStatement(stmt);
        try {
          ctx.traceEnterEvent(stmt, blockIndex, stmtIndex);
          switch (stmt.getType()) {
            case MAKE_OBJECT: {
              MakeObjectStmt mos = (MakeObjectStmt) stmt;
              frame.setLocal(mos.getTarget(), new RegoObject());
              break;
            }
            case BLOCK: {
              BlockStmt bs = (BlockStmt) stmt;
              if (bs.getBlocks() != null) {
                int nestedBlockIndex = 0;
                for (Block b : bs.getBlocks()) {
                  if (b != null) {
                    evaluateBlock(b, nestedBlockIndex, frame, ctx);
                    if (frame.shouldBreak()) {
                      ctx.traceBreak();
                      frame.decrementBreakTarget();
                      return true;
                    }
                  }
                  nestedBlockIndex++;
                }
              }
              break;
            }
            case CALL_DYNAMIC: {
              CallDynamicStmt cds = (CallDynamicStmt) stmt;
              String funcKey =
                      cds.getPath().stream()
                              .map(o -> resolveOperand(o, ctx, frame))
                              .map(o -> o.nativeValue().toString())
                              .collect(Collectors.joining("."));
              Func func = ctx.functionsByPath.get(funcKey);
              if (func != null) {
                // Build locals array from integer arg indices
                List<Integer> funcParams = func.getParams();
                List<Integer> args = cds.getArgs();
                RegoValue[] frameLocals = new RegoValue[func.getMaxLocalForFunction() + 1];
                for (int i = 0; i < args.size(); i++) {
                  frameLocals[funcParams.get(i)] = frame.getLocal(args.get(i));
                }
                if (!invokeFunction(func, frameLocals, cds.getResult(), ctx, frame)) {
                  return false;
                }
              } else {
                return false;
              }
              break;
            }
            case CALL: {
              CallStmt cs = (CallStmt) stmt;
              Func func = Optional.ofNullable(ctx.functions).orElse(Collections.emptyMap()).get(cs.getFunc());
              if (func != null) {
                RegoValue[] frameLocals = newLocals(ctx, frame, cs.getArgs(), func);
                if (!invokeFunction(func, frameLocals, cs.getResult(), ctx, frame)) {
                  return false;
                }
              } else if (!invokeBuiltin(cs.getFunc(), cs.getArgs(), cs.getResult(), ctx, frame)) {
                return false;
              }
              break;
            }
            case RETURN_LOCAL: {
              ReturnLocalStmt rls = (ReturnLocalStmt) stmt;
              if (frame.getLocal(rls.getSource()) == null) {
                return false;
              }
              frame.setResult(frame.getLocal(rls.getSource()));
              break;
            }
            case RESET_LOCAL: {
              ResetLocalStmt rls = (ResetLocalStmt) stmt;
              frame.setLocal(rls.getTarget(), null);
              break;
            }
            case ASSIGN_VAR_ONCE: {
              AssignVarOnceStmt avos = (AssignVarOnceStmt) stmt;
              RegoValue newValue = resolveOperand(avos.getSource(), ctx, frame);
              RegoValue oldValue = frame.getLocal(avos.getTarget());
              if (oldValue != null && oldValue.compareTo(newValue) != 0) {
                if (frame.isUserFunction()) {
                  throw new MultipleAssignmentsError("functions must not produce multiple outputs for same inputs");
                }
                throw new MultipleAssignmentsError();
              }
              frame.setLocal(avos.getTarget(), resolveOperand(avos.getSource(), ctx, frame));
              break;
            }
            case IS_DEFINED: {
              IsDefinedStmt ids = (IsDefinedStmt) stmt;
              RegoValue value = frame.getLocal(ids.getSource());
              if (value == null || value instanceof RegoUndefined) {
                return false;
              }
              break;
            }
            case OBJECT_INSERT: {
              ObjectInsertStmt ois = (ObjectInsertStmt) stmt;
              RegoValue target = frame.getLocal(ois.getObject());
              if (target == null) {
                throw new EvaluationException("OBJECT_INSERT target is null")
                        .withContext("statement", stmt)
                        .withContext("targetIndex", ois.getObject());
              }
              if (!(target instanceof RegoObject)) {
                throw new TypeMismatchException(new String[]{"object"}, target.getTypeName())
                        .withContext("statement", stmt)
                        .withContext("targetValue", target);
              }
              RegoObject targetObject = (RegoObject) target;
              RegoValue key = resolveOperand(ois.getKey(), ctx, frame);
              RegoValue insertValue = resolveOperand(ois.getValue(), ctx, frame);
              targetObject.setProp(key, insertValue);
              break;
            }
            case DOT: {
              DotStmt ds = (DotStmt) stmt;
              RegoValue source = resolveOperand(ds.getSource(), ctx, frame);
              RegoValue key = resolveOperand(ds.getKey(), ctx, frame);
              if (source == null || key == null) {
                return false;
              }
              // Determine if the source local is data-derived (i.e., obtained via direct data
              // reference traversal). AssignVarStmt does NOT propagate this flag.
              boolean srcDataDerived = (ds.getSource().getValue() instanceof LocalVal)
                  && frame.isDataDerived(((LocalVal) ds.getSource().getValue()).getValue());
              if (source instanceof RegoString) {
                return false;
              } else if (source instanceof RegoSet) {
                RegoSet rs = (RegoSet) source;
                if (rs.contains(key)) {
                  frame.setLocal(ds.getTarget(), key);
                  if (srcDataDerived) frame.markDataDerived(ds.getTarget());
                } else {
                  return false; // Key not in set - fail the statement
                }
              } else if (source instanceof RegoObject) {
                RegoObject ro = (RegoObject) source;
                RegoValue effectiveKey = key;
                // For data-derived objects, coerce numeric keys to their string representation.
                // This matches OPA semantics: JSON data has string keys, and integer refs like
                // data.foo[2] are coerced to "2" during traversal. However, when an object is
                // bound to a Rego variable via AssignVarStmt, the data-derived flag is cleared,
                // so obj[2] (where obj := data.foo) performs strict typed lookup and fails.
                if (!ro.hasProperty(key) && srcDataDerived && key instanceof RegoNumber) {
                  effectiveKey = new RegoString(key.toString());
                }
                if (!ro.hasProperty(effectiveKey)) {
                  return false;
                }
                RegoValue value = ro.getProperty(effectiveKey);
                if (value == null) value = RegoNull.INSTANCE;
                frame.setLocal(ds.getTarget(), value);
                if (srcDataDerived) frame.markDataDerived(ds.getTarget());
              } else if (source instanceof RegoArray) {
                RegoArray ra = (RegoArray) source;
                RegoValue value;
                if (key instanceof RegoInt32) {
                  RegoInt32 k = (RegoInt32) key;
                  if (k.getValue() >= ra.getValue().size()) {
                    return false;
                  }
                  value = ra.getValue().get(k.getValue());
                } else if (key instanceof RegoBigInt) {
                  RegoBigInt k = (RegoBigInt) key;
                  if (k.getValue().intValue() >= ra.getValue().size()) {
                    return false;
                  }

                  value = ra.getValue().get(k.getValue().intValue());
                } else if (key instanceof RegoArray) {
                  if (ra.getValue().contains(key)) {
                    frame.setLocal(ds.getTarget(), key);
                  } else {
                    frame.setLocal(ds.getTarget(), null);
                  }
                  break;
                } else if (key instanceof RegoObject) {
                  if (ra.getValue().contains(key)) {
                    frame.setLocal(ds.getTarget(), key);
                  } else {
                    frame.setLocal(ds.getTarget(), null);
                  }
                  break;
                } else if (key instanceof RegoString) {
                  return false;
                } else if (key instanceof RegoBoolean) {
                  return false;
                } else if (key instanceof RegoNumber) {
                  return false;
                } else if (key instanceof RegoUndefined) {
                  return false;
                } else {
                  throw new TypeMismatchException(
                          new String[]{"string", "number"}, key.getTypeName())
                          .withContext("statement", stmt);
                }
                if (value == null) {
                  value = RegoNull.INSTANCE;
                }
                frame.setLocal(ds.getTarget(), value);
                if (srcDataDerived) frame.markDataDerived(ds.getTarget());
              } else if (source instanceof RegoNumber) {
                return false;
              } else if (source instanceof RegoBoolean) {
                return false;
              } else if (source instanceof RegoNull) {
                return false;
              } else if (source instanceof RegoUndefined) {
                return false;
              } else {
                throw new TypeMismatchException(
                        new String[]{"object", "array", "set"}, source.getTypeName())
                        .withContext("statement", stmt);
              }
              break;
            }
            case OBJECT_MERGE: {
              ObjectMergeStmt oms = (ObjectMergeStmt) stmt;
              RegoValue b = frame.getLocal(oms.getB());
              if (frame.getLocal(oms.getA()) instanceof RegoNull) {
                frame.setLocal(oms.getTarget(), b);
                break;
              }
              var o = frame.getLocal(oms.getA());
              if (!(o instanceof RegoObject)) {
                return false;
              }
              RegoObject a = (RegoObject) o;
              if (b != null) {
                // For base/virtual merge: b (virtual) calls merge with a (base) as parameter
                // Since merge() makes the parameter win, base will win on conflicts
                frame.setLocal(oms.getTarget(), ((RegoObject) b).merge(a));
              }
              break;
            }
            case BREAK: {
              BreakStmt bs = (BreakStmt) stmt;
              // index 0 means "just fail this block, don't propagate break upward"
              if (bs.getIndex() == 0) {
                return false;
              }
              frame.setBreakTarget(bs.getIndex() - 1);
              return true;
            }
            case ASSIGN_VAR: {
              AssignVarStmt avs = (AssignVarStmt) stmt;
              frame.setLocal(avs.getTarget(), resolveOperand(avs.getSource(), ctx, frame));
              break;
            }
            case RESULT_SET_ADD: {
              ResultSetAddStmt rsas = (ResultSetAddStmt) stmt;
              frame.addToResultSet(frame.getLocal(rsas.getValue()));
              break;
            }
            case MAKE_NUMBER_REF: {
              MakeNumberRefStmt mnrs = (MakeNumberRefStmt) stmt;
              frame.setLocal(
                      mnrs.getTarget(),
                      TypeUtils.parseStringToNumber(ctx.staticStrings.get(mnrs.getIndex())));
              break;
            }
            case EQUAL: {
              EqualStmt es = (EqualStmt) stmt;
              RegoValue a = resolveOperand(es.getA(), ctx, frame);
              RegoValue b = resolveOperand(es.getB(), ctx, frame);
              // null doesn't equal null except in Rego :)
              if (a == null && b == null) {
                // continue with next statement
              } else if (a == null || !a.equals(b)) {
                return false;
              }
              break;
            }
            case MAKE_SET: {
              MakeSetStmt mss = (MakeSetStmt) stmt;
              frame.setLocal(mss.getTarget(), new RegoSet(ctx.sortSets));
              break;
            }
            case SCAN: {
              ScanStmt ss = (ScanStmt) stmt;
              if (frame.getLocal(ss.getSource()) == null) {
                return false;
              }
              RegoValue source = frame.getLocal(ss.getSource());
              if (source instanceof RegoSet) {
                RegoSet rs = (RegoSet) source;
                if (rs.getValue().isEmpty()) {
                  break;
                }
                for (RegoValue v : rs.getValue()) {
                  frame.setLocal(ss.getKey(), v);
                  frame.setLocal(ss.getValue(), v);
                  if (!evaluateBlock(ss.getBlock(), 0, frame, ctx)) {
                    continue; // Block failed - skip this iteration
                  }
                }
              } else if (source instanceof RegoArray) {
                RegoArray ra = (RegoArray) source;
                if (ra.getValue().isEmpty()) {
                  break;
                }
                for (int i = 0; i < ra.getValue().size(); i++) {
                  frame.setLocal(ss.getKey(), RegoInt32.of(i));
                  // objects
                  frame.setLocal(ss.getValue(), ra.getValue().get(i));
                  if (!evaluateBlock(ss.getBlock(), 0, frame, ctx)) {
                    continue; // Block failed - skip this iteration
                  }
                }
              } else if (source instanceof RegoObject) {
                RegoObject ro = (RegoObject) source;
                if (ro.getProperties().isEmpty()) {
                  break;
                }
                for (Map.Entry<RegoValue, RegoValue> entry : ro.getProperties().entrySet()) {
                  frame.setLocal(ss.getKey(), entry.getKey());
                  frame.setLocal(ss.getValue(), entry.getValue());
                  if (!evaluateBlock(ss.getBlock(), 0, frame, ctx)) {
                    continue; // Block failed - skip this iteration
                  }
                }
              } else if (source instanceof RegoString) {
                return false;
              } else if (source instanceof RegoBoolean) {
                return false;
              } else if (source instanceof RegoInt32) {
                return false;
              } else if (source instanceof RegoBigInt) {
                return false;
              } else if (source instanceof RegoDecimal) {
                return false;
              } else if (source instanceof RegoNull) {
                return false;
              } else if (source instanceof RegoUndefined) {
                return false;
              } else {
                throw new TypeMismatchException(
                        new String[]{"object", "array", "set"}, source.getTypeName())
                        .withContext("statement", stmt);
              }
              break;
            }
            case IS_OBJECT: {
              IsObjectStmt ios = (IsObjectStmt) stmt;
              if (!(resolveOperand(ios.getSource(), ctx, frame) instanceof RegoObject)) {
                return false;
              }
              break;
            }
            case LEN: {
              LenStmt ls = (LenStmt) stmt;
              RegoValue source = resolveOperand(ls.getSource(), ctx, frame);
              if (source == null) {
                return false;
              }
              frame.setLocal(ls.getTarget(), RegoInt32.of(source.length()));
              break;
            }
            case MAKE_NUMBER_INT: {
              MakeNumberIntStmt mnis = (MakeNumberIntStmt) stmt;
              frame.setLocal(mnis.getTarget(), new RegoBigInt(BigInteger.valueOf(mnis.getValue())));
              break;
            }
            case MAKE_ARRAY: {
              MakeArrayStmt mas = (MakeArrayStmt) stmt;
              frame.setLocal(mas.getTarget(), new RegoArray(mas.getCapacity()));
              break;
            }
            case ARRAY_APPEND: {
              ArrayAppendStmt aas = (ArrayAppendStmt) stmt;
              RegoValue value = resolveOperand(aas.getValue(), ctx, frame);
              // Reject null (unset local) and undefined - but RegoNull.INSTANCE is valid
              if (value == null || value instanceof RegoUndefined) {
                return false;
              }
              ((RegoArray) frame.getLocal(aas.getArray())).addValue(value);
              break;
            }
            case SET_ADD: {
              SetAddStmt sas = (SetAddStmt) stmt;
              RegoValue value = resolveOperand(sas.getValue(), ctx, frame);
              // Reject null (unset local) and undefined - but RegoNull.INSTANCE is valid
              if (value == null || value instanceof RegoUndefined) {
                return false;
              }
              ((RegoSet) frame.getLocal(sas.getSet())).addValue(value);
              break;
            }
            case IS_ARRAY: {
              IsArrayStmt ias = (IsArrayStmt) stmt;
              if (!(resolveOperand(ias.getSource(), ctx, frame) instanceof RegoArray)) {
                return false;
              }
              break;
            }
            case ASSIGN_INT: {
              AssignIntStmt ais = (AssignIntStmt) stmt;
              frame.setLocal(ais.getTarget(), new RegoBigInt(BigInteger.valueOf(ais.getValue())));
              break;
            }
            case MAKE_NULL: {
              MakeNullStmt mns = (MakeNullStmt) stmt;
              frame.setLocal(mns.getTarget(), RegoNull.INSTANCE);
              break;
            }
            case OBJECT_INSERT_ONCE: {
              ObjectInsertOnceStmt oios = (ObjectInsertOnceStmt) stmt;
              if (oios.getKey() == null || oios.getValue() == null) {
                return false;
              }

              RegoValue key = resolveOperand(oios.getKey(), ctx, frame);

              RegoValue value = resolveOperand(oios.getValue(), ctx, frame);
              RegoObject object = (RegoObject) frame.getLocal(oios.getObject());
              if (object.getProperty(key) != null && !value.equals(object.getProperty(key))) {
                throw new EvalConflictError("object keys must be unique")
                        .withContext("key", key)
                        .withContext("object", object)
                        .withContext("statement", stmt);
              }
              object.setProp(key, value);
              break;
            }
            case NOT_EQUAL: {
              NotEqualStmt nes = (NotEqualStmt) stmt;
              RegoValue a = resolveOperand(nes.getA(), ctx, frame);
              RegoValue b = resolveOperand(nes.getB(), ctx, frame);
              // Treat undefined as falsy - if either operand is undefined, fail
              if (a instanceof RegoUndefined || b instanceof RegoUndefined) return false;
              if (a == null || b == null || a.equals(b)) return false;
              break;
            }
            case NOT: {
              NotStmt ns = (NotStmt) stmt;
              boolean blockResult = evaluateBlock(ns.getBlock(), 0, frame, ctx);
              // If a break occurred, the block effectively failed
              if (frame.shouldBreak()) {
                ctx.traceBreak();
                frame.decrementBreakTarget();
                blockResult = false;
              }
              if (blockResult) {
                return false;
              }
              break;
            }
            case IS_UNDEFINED: {
              IsUndefinedStmt ius = (IsUndefinedStmt) stmt;
              if (frame.getLocal(ius.getSource()) != null) {
                return false;
              }
              break;
            }
            case IS_SET: {
              IsSetStmt iss = (IsSetStmt) stmt;
              if (!(resolveOperand(iss.getSource(), ctx, frame) instanceof RegoSet)) {
                return false;
              }
              break;
            }
            case WITH: {
              WithStmt ws = (WithStmt) stmt;
              Block withBlock = ws.getBlock();

              String[] path = null;
              if (ws.getPath() != null) {
                path =
                        ws.getPath().stream().map(i -> ctx.staticStrings.get(i)).toArray(String[]::new);
              }
              RegoValue patch = resolveOperand(ws.getValue(), ctx, frame);
              int replaceLocal = ws.getLocal();
              RegoValue patchedLocal = patchRegoValue(frame.getLocal(replaceLocal), patch, path);

              RegoValue oldLocal = frame.setLocal(replaceLocal, patchedLocal);
              boolean success = evaluateBlock(withBlock, 0, frame, ctx);
              frame.setLocal(replaceLocal, oldLocal);
              if (!success) {
                return false;
              }
              break;
            }
            case NOP: {
              break;
            }
            default: {
              throw new EvaluationException("Statement type not implemented: " + stmt.getType())
                      .withContext("statement", stmt);
            }
          }
        } finally {
          long duration = System.nanoTime() - startTime;
          ctx.statementProfiler.stopStatement(stmt, duration);
          ctx.traceExitEvent(stmt, blockIndex, stmtIndex, duration);
          stmtIndex++;
        }
      }
    } finally {
      ctx.traceExitBlock();
    }
    return true;
  }

  /**
   * Patches a RegoValue by applying a value at a specific path within the data structure.
   *
   * <p>This method is used to implement Rego's {@code with} statement, which temporarily replaces
   * values in the input or data during policy evaluation. The {@code with} statement is primarily
   * used in unit tests to mock input data, but can also be used in production policies.
   *
   * <p><b>PERFORMANCE WARNING:</b> This implementation creates full deep copies of RegoObject,
   * RegoArray, and RegoSet structures. This can significantly increase memory usage, especially
   * with large datasets. For example, modifying a single field in a 10MB object will create an
   * additional 10MB copy in memory.
   *
   * <p>Future optimization opportunities:
   *
   * <ul>
   *   <li>Implement copy-on-write semantics to share unchanged portions of data structures
   *   <li>Use persistent data structures with structural sharing
   *   <li>Consider pooling/reusing temporary objects
   * </ul>
   *
   * <p>For now, users should be aware that extensive use of {@code with} statements on large
   * datasets may impact memory consumption. Since {@code with} is primarily used in unit tests,
   * this is typically not a concern in production environments.
   *
   * @param value the original RegoValue to patch
   * @param patch the value to insert at the specified path
   * @param path the path within the value where the patch should be applied (e.g., ["foo", "bar"]
   *     to patch value.foo.bar)
   * @return a new RegoValue with the patch applied
   */
  private RegoValue patchRegoValue(RegoValue value, RegoValue patch, String[] path) {
    // Supported RegoValues are RegoObject, RegoArray and RegoSet
    // RegoObjects the key path should be a string
    // RegoArrays the key path should be an int
    // RegoSets the key path should be a value in the set

    if (path == null || path.length == 0) {
      return patch;
    }

    if (value instanceof RegoObject) {
      RegoObject original = (RegoObject) value;
      RegoObject result = new RegoObject();

      // Copy all existing properties
      RegoString pathKey = new RegoString(path[0]);
      for (Map.Entry<RegoValue, RegoValue> entry : original.getProperties().entrySet()) {
        if (entry.getKey().equals(pathKey)) {
          // Recursively patch the matching property
          String[] remainingPath = Arrays.copyOfRange(path, 1, path.length);
          RegoValue patchedValue = patchRegoValue(entry.getValue(), patch, remainingPath);
          result.setProp(entry.getKey(), patchedValue);
        } else {
          result.setProp(entry.getKey(), entry.getValue());
        }
      }

      // If the key doesn't exist, add it
      if (!original.hasProperty(pathKey)) {
        String[] remainingPath = Arrays.copyOfRange(path, 1, path.length);
        RegoValue patchedValue = patchRegoValue(new RegoObject(), patch, remainingPath);
        result.setProp(pathKey, patchedValue);
      }

      return result;

    } else if (value instanceof RegoArray) {
      RegoArray original = (RegoArray) value;
      RegoArray result = new RegoArray();

      try {
        int index = Integer.parseInt(path[0]);

        // Copy all existing values
        for (int i = 0; i < original.getValue().size(); i++) {
          if (i == index) {
            // Recursively patch the matching index
            String[] remainingPath = Arrays.copyOfRange(path, 1, path.length);
            RegoValue patchedValue =
                patchRegoValue(original.getValue().get(i), patch, remainingPath);
            result.addValue(patchedValue);
          } else {
            result.addValue(original.getValue().get(i));
          }
        }

        // If index is beyond array size, extend array with nulls and add the patch
        while (result.getValue().size() <= index) {
          if (result.getValue().size() == index) {
            String[] remainingPath = Arrays.copyOfRange(path, 1, path.length);
            RegoValue patchedValue = patchRegoValue(new RegoObject(), patch, remainingPath);
            result.addValue(patchedValue);
          } else {
            result.addValue(RegoNull.INSTANCE);
          }
        }

        return result;

      } catch (NumberFormatException e) {
        throw new EvaluationException("Array path element must be numeric: " + path[0])
            .withContext("path", path[0])
            .withContext("value", value)
            .withContext("patch", patch);
      }

    } else if (value instanceof RegoSet) {
      RegoSet original = (RegoSet) value;
      RegoSet result = new RegoSet(original.getSorted());

      // For sets, the path element should match a value in the set
      RegoValue pathValue = new RegoString(path[0]);
      boolean found = false;

      for (RegoValue setValue : original.getValue()) {
        if (setValue.equals(pathValue)) {
          // Recursively patch the matching set value
          String[] remainingPath = Arrays.copyOfRange(path, 1, path.length);
          RegoValue patchedValue = patchRegoValue(setValue, patch, remainingPath);
          result.addValue(patchedValue);
          found = true;
        } else {
          result.addValue(setValue);
        }
      }

      // If the value doesn't exist in the set, add it
      if (!found) {
        String[] remainingPath = Arrays.copyOfRange(path, 1, path.length);
        RegoValue patchedValue = patchRegoValue(pathValue, patch, remainingPath);
        result.addValue(patchedValue);
      }

      return result;

    } else {
      // For other types, replace with the patch if path is empty, otherwise create an object
      if (path.length == 1) {
        RegoObject result = new RegoObject();
        result.setProp(new RegoString(path[0]), patch);
        return result;
      } else {
        RegoObject intermediate = new RegoObject();
        String[] remainingPath = Arrays.copyOfRange(path, 1, path.length);
        RegoValue patchedValue = patchRegoValue(new RegoObject(), patch, remainingPath);
        intermediate.setProp(new RegoString(path[0]), patchedValue);
        return intermediate;
      }
    }
  }

  private RegoValue resolveOperand(Operand op, IREvaluationContext ctx, Frame frame) {
    if (op.getValue() instanceof BoolVal) {
      BoolVal bv = (BoolVal) op.getValue();
      return RegoBoolean.of(bv.isValue());
    } else if (op.getValue() instanceof StringIndexVal) {
      StringIndexVal siv = (StringIndexVal) op.getValue();
      return new RegoString(ctx.staticStrings.get(siv.getValue()));
    } else if (op.getValue() instanceof LocalVal) {
      LocalVal lv = (LocalVal) op.getValue();
      return frame.getLocal(lv.getValue());
    } else {
      throw new EvaluationException("Unexpected value: " + op.getValue());
    }
  }

  // Would like to move this into Frame, but resolveOperand makes that challenging
  private RegoValue[] newLocals(
      IREvaluationContext ctx, Frame frame, List<Operand> args, Func func) {

    List<Integer> funcParams = func.getParams();
    RegoValue[] locals = new RegoValue[func.getMaxLocalForFunction() + 1];
    for (int i = 0; i < args.size(); i++) {
      // funcParams may not be the index, so we need to use the value to set the correct index
      locals[funcParams.get(i)] = resolveOperand(args.get(i), ctx, frame);
    }
    return locals;
  }

  private boolean invokeFunction(
      Func func, RegoValue[] frameLocals, int result, IREvaluationContext ctx, Frame frame) {
    Frame newFrame = frame.subFrame(frameLocals);
    boolean isUserFunction = func.getParams() != null && func.getParams().size() > 2;
    newFrame.setUserFunction(isUserFunction);
    // Mark the data parameter (index 1 in params list) as data-derived in the new frame.
    // By OPA IR convention, params[0] = input and params[1] = data.
    if (func.getParams() != null && func.getParams().size() >= 2) {
      newFrame.markDataDerived(func.getParams().get(1));
    }
    for (Block cBlock : func.getBlocks()) {
      evaluateBlock(cBlock, 0, newFrame, ctx);
      if (newFrame.shouldBreak()) {
        ctx.traceBreak();
        newFrame.decrementBreakTarget();
        break;
      }
    }
    if (newFrame.getResult() == null) {
      return false;
    }
    frame.setLocal(result, newFrame.getResult());
    return true;
  }

  private boolean invokeBuiltin(
      String name, List<Operand> args, int result, IREvaluationContext ctx, Frame frame) {
    RegoValue[] vArgs = null;
    if (args != null) {
      vArgs = args.stream().map(o -> resolveOperand(o, ctx, frame)).toArray(RegoValue[]::new);
    }
    // TODO: [ENTERPRISE] [MED] Implement nd_builtin_cache support - non-deterministic builtin
    // caching for time.now_ns(), opa.runtime(), etc.
    if (ctx.builtinRegistry != null && ctx.builtinRegistry.hasBuiltIn(name)) {
      try {
        RegoValue value = ctx.builtinRegistry.getBuiltIn(name).apply(ctx, vArgs);
        if (value == null) {
          return false;
        }
        frame.setLocal(result, value);
        return true;
      } catch (TypeError e) {
        if (ctx.isStrictBuiltinErrors()) {
          throw new TypeError(name, e);
        }
        return false;
      } catch (io.github.open_policy_agent.opa.ast.builtin.BuiltinError e) {
        if (ctx.isStrictBuiltinErrors()) {
          throw e;
        }
        return false;
      }
    } else {
      throw new FunctionNotFoundError(name)
          .withContext("availableBuiltins", ctx.builtinRegistry.registeredBuiltins());
    }
  }

  // Package-private accessors for plan warming
  Policy getPolicy() {
    return policy;
  }

  Map<String, Func> getFuncRegistry() {
    return funcRegistry;
  }

  ArrayList<String> getStaticStrings() {
    return staticStrings;
  }

  public static class Builder {
    private Policy policy;
    private Map<String, Func> funcRegistry;
    private ArrayList<String> staticStrings = new ArrayList<>();
    private io.github.open_policy_agent.opa.ast.builtin.BuiltinRegistry builtinRegistry;

    public Builder withPolicy(Policy policy) {
      this.policy = policy;
      return this;
    }

    public Builder withBuiltinRegistry(
        io.github.open_policy_agent.opa.ast.builtin.BuiltinRegistry registry) {
      this.builtinRegistry = registry;
      return this;
    }

    public Evaluator build() {
      if (this.policy == null) {
        throw new PolicyNotFoundException("null");
      }

      // Validate that all required builtins are available
      if (builtinRegistry != null) {
        io.github.open_policy_agent.opa.rego.Engine.Builder.validateRequiredBuiltins(
            policy, builtinRegistry);
      }

      List<Func> funcs = policy.getFuncs() != null ? policy.getFuncs().getFuncs() : null;
      if (funcs != null) {
        this.funcRegistry = new HashMap<>(funcs.size());
        for (Func f : funcs) {
          if (f != null && f.getName() != null) {
            this.funcRegistry.put(f.getName(), f);
          }
        }
      }

      List<StringConst> strings =
          policy.getStatic() != null ? policy.getStatic().getStrings() : null;
      if (strings != null) {
        this.staticStrings = new ArrayList<>(strings.size());
        for (StringConst sc : strings) {
          this.staticStrings.add(sc.getValue());
        }
      }

      return new Evaluator(this);
    }
  }
}
