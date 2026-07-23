package io.github.open_policy_agent.opa.ast.builtin.impls;

import io.github.open_policy_agent.opa.ast.builtin.BuiltinError;
import io.github.open_policy_agent.opa.ast.builtin.OpaBuiltin;
import io.github.open_policy_agent.opa.ast.builtin.OpaDynamic;
import io.github.open_policy_agent.opa.ast.builtin.OpaType;
import io.github.open_policy_agent.opa.ast.types.RegoArray;
import io.github.open_policy_agent.opa.ast.types.RegoBoolean;
import io.github.open_policy_agent.opa.ast.types.RegoSet;
import io.github.open_policy_agent.opa.ast.types.RegoString;
import io.github.open_policy_agent.opa.ast.types.RegoValue;
import io.github.open_policy_agent.opa.rego.EvaluationContext;
import io.github.open_policy_agent.opa.rego.PrintHook;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;

/**
 * Implements the OPA {@code internal.print} builtin.
 *
 * <p>The OPA compiler rewrites {@code print(a, b, c)} into
 * {@code internal.print([{v | v = a}, {v | v = b}, {v | v = c}])}.
 * Each argument is wrapped in a set comprehension so that undefined values produce
 * an empty set rather than short-circuiting evaluation.
 *
 * <p>This implementation matches the Go OPA behavior in {@code topdown/print.go}:
 * <ul>
 *   <li>Empty set operand renders as {@code "<undefined>"}
 *   <li>String values are printed without quotes
 *   <li>All other values use their OPA-style string representation
 *   <li>Multiple values in a set produce a cross-product of output lines
 *   <li>If no {@link PrintHook} is configured, print is a silent no-op
 *   <li>Print always succeeds (never causes evaluation failure)
 * </ul>
 */
public class PrintBuiltins {

  public static Map<String, BiFunction<EvaluationContext, RegoValue[], RegoValue>> builtins() {
    PrintBuiltins instance = new PrintBuiltins();
    return Map.of("internal.print", instance::internalPrint);
  }

  @OpaBuiltin(
      name = "internal.print",
      description = "Print the operands. Used internally by the compiler when it rewrites print() calls.",
      args = {
        @OpaType(
            type = "array",
            name = "operands",
            description = "Array of set operands from compiler rewrite",
            dynamic = @OpaDynamic(type = "any"))
      },
      result = @OpaType(type = "boolean", description = "Always true"))
  public RegoValue internalPrint(EvaluationContext ctx, RegoValue[] args) {
    PrintHook hook = ctx.getPrintHook();
    if (hook == null) {
      // No hook configured — silently succeed, matching Go OPA behavior
      return RegoBoolean.TRUE;
    }

    if (args == null || args.length == 0) {
      return RegoBoolean.TRUE;
    }

    RegoArray operands;
    if (args[0] instanceof RegoArray) {
      operands = (RegoArray) args[0];
    } else {
      return RegoBoolean.TRUE;
    }

    List<RegoValue> sets = operands.getValue();
    if (sets.isEmpty()) {
      return RegoBoolean.TRUE;
    }

    // Build formatted operand lists for cross-product
    List<List<String>> formattedOperands = new ArrayList<>(sets.size());
    for (RegoValue operand : sets) {
      formattedOperands.add(formatOperand(operand));
    }

    // Generate cross-product of all operand values and print each combination
    crossProduct(formattedOperands, 0, new String[sets.size()], hook, ctx.isStrictBuiltinErrors());

    return RegoBoolean.TRUE;
  }

  /**
   * Format a single operand (expected to be a set from the compiler rewrite).
   *
   * <p>Matches Go OPA {@code builtinPrintCrossProductOperands} formatting:
   * <ul>
   *   <li>Empty set → ["&lt;undefined&gt;"]
   *   <li>RegoString → raw string value (no quotes)
   *   <li>All other types → OPA-style string representation
   * </ul>
   */
  private List<String> formatOperand(RegoValue operand) {
    if (operand instanceof RegoSet) {
      RegoSet set = (RegoSet) operand;
      Set<RegoValue> values = set.getValue();
      if (values.isEmpty()) {
        return List.of("<undefined>");
      }
      List<String> formatted = new ArrayList<>(values.size());
      for (RegoValue val : values) {
        formatted.add(formatValue(val));
      }
      return formatted;
    }
    // Direct value (not wrapped in set) — format it directly
    return List.of(formatValue(operand));
  }

  /**
   * Format a single value for print output.
   * Strings are printed without quotes; all other types use their OPA-style toString representation.
   */
  private String formatValue(RegoValue value) {
    if (value instanceof RegoString) {
      return ((RegoString) value).getValue();
    }
    if (value == null) {
      return "null";
    }
    // toString() produces OPA-style representations:
    // RegoNull -> "null", RegoBoolean -> "true"/"false",
    // RegoObject -> {"key":"val"}, RegoArray -> [1, 2], etc.
    return value.toString();
  }

  /**
   * Generate cross-product of formatted operand values and call the print hook for each combination.
   * Matches Go OPA behavior where sets with multiple values produce one line per combination.
   * In strict builtin errors mode, hook exceptions propagate as BuiltinError.
   */
  private void crossProduct(
      List<List<String>> operands, int depth, String[] current,
      PrintHook hook, boolean strict) {
    if (depth == operands.size()) {
      try {
        hook.print(String.join(" ", current));
      } catch (Exception e) {
        if (strict) {
          throw new BuiltinError(e.getMessage());
        }
        // Non-strict: swallow, matching Go OPA behavior
      }
      return;
    }
    for (String val : operands.get(depth)) {
      current[depth] = val;
      crossProduct(operands, depth + 1, current, hook, strict);
    }
  }
}
