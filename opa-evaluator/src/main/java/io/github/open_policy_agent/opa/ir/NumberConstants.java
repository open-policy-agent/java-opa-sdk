package io.github.open_policy_agent.opa.ir;

import io.github.open_policy_agent.opa.ast.types.RegoValue;
import io.github.open_policy_agent.opa.ast.types.TypeUtils;
import java.util.List;
import java.util.concurrent.atomic.AtomicReferenceArray;

/**
 * The number constants of one plan, each parsed on first use and then shared by every evaluation.
 * Nothing in the evaluator changes a number once it exists, which is also what lets the small
 * integers be shared. Two threads that parse the same constant at once store equal values.
 */
final class NumberConstants {

  private final AtomicReferenceArray<RegoValue> parsed;

  NumberConstants(int size) {
    this.parsed = new AtomicReferenceArray<>(size);
  }

  RegoValue get(int index, List<String> staticStrings) {
    RegoValue value = parsed.get(index);
    if (value == null) {
      final RegoValue fresh = TypeUtils.parseStringToNumber(staticStrings.get(index));
      value = parsed.compareAndSet(index, null, fresh) ? fresh : parsed.get(index);
    }
    return value;
  }
}
