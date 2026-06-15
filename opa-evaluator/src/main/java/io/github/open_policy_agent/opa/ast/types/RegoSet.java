package io.github.open_policy_agent.opa.ast.types;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class RegoSet implements RegoValue, RegoCollection {
  private final Set<RegoValue> value;
  private final boolean sorted;

  public RegoSet(boolean sorted) {
    // Always use LinkedHashSet to preserve insertion order
    // Sorting will happen on getValue() if needed
    this.value = new LinkedHashSet<>();
    this.sorted = sorted;
    }

  public RegoSet(boolean sorted, Set<RegoValue> values) {
    this(sorted);
        this.value.addAll(values);
    }

  public boolean contains(RegoValue value) {
    // Use utility methods for composite reference lookups
    if (value instanceof RegoSet) {
      return CompositeReferenceUtil.containsSet(this.value, (RegoSet) value);
    } else if (value instanceof RegoArray) {
      return CompositeReferenceUtil.containsArray(this.value, (RegoArray) value);
    } else if (value instanceof RegoObject) {
      return CompositeReferenceUtil.containsObject(this.value, (RegoObject) value);
    } else {
      return this.value.contains(value);
    }
  }

    @SuppressWarnings("PMD.CompareObjectsWithEquals")
    public Set<RegoValue> getValue() {
    if (!sorted) {
      return value;
    }

    // Sort on demand: primitives by value, collections maintain insertion order within type
    List<RegoValue> sortedList = new ArrayList<>(value);

    // Create a map to track insertion order for collections
    Map<RegoValue, Integer> insertionOrder = new HashMap<>();
    int index = 0;
    for (RegoValue v : value) {
      if (isCollectionType(v)) {
        insertionOrder.put(v, index++);
      }
    }

    sortedList.sort(
        (a, b) -> {
          if (a == b) return 0;
          if (a == null) return b == null ? 0 : -1;
          if (b == null) return 1;

          // Get type precedence
          int typeA = getTypePrecedence(a);
          int typeB = getTypePrecedence(b);

          // Different types: sort by type
          if (typeA != typeB) {
            return Integer.compare(typeA, typeB);
          }

          // Same type: collections use insertion order, primitives use natural order
          if (isCollectionType(a)) {
            return Integer.compare(insertionOrder.get(a), insertionOrder.get(b));
          }

          return a.compareTo(b);
        });

    // Return as LinkedHashSet to preserve the sorted order
    return new LinkedHashSet<>(sortedList);
  }

  private boolean isCollectionType(RegoValue value) {
    return value instanceof RegoArray || value instanceof RegoSet || value instanceof RegoObject;
  }

  private int getTypePrecedence(RegoValue value) {
    if (value instanceof RegoUndefined) {
      return 0;
    } else if (value instanceof RegoNull) {
      return 1;
    } else if (value instanceof RegoBoolean) {
      return 2;
    } else if (value instanceof RegoNumber) {
      return 3;
    } else if (value instanceof RegoString) {
      return 4;
    } else if (value instanceof RegoArray) {
      return 5;
    } else if (value instanceof RegoSet) {
      return 6;
    } else if (value instanceof RegoObject) {
      return 7;
    } else {
      return 8;
    }
  }

    @Override
    public Object nativeValue() {
        return value.stream().map(RegoValue::nativeValue).collect(Collectors.toList());
    }

    public void addValue(RegoValue value) {
        this.value.add(value);
    }

    @Override
    public int length() {
        return value.size();
    }

    public Stream<RegoValue> valueStream() {
        return value.stream();
    }

    public String getTypeName() { return "set";}

  public boolean getSorted() {
    return sorted;
  }

    @Override
    public final boolean equals(Object o) {
        if (!(o instanceof RegoSet)) return false;
        RegoSet regoSet = (RegoSet) o;

        return value.equals(regoSet.value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}

