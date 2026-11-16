package io.github.open_policy_agent.opa.ast.types;

public interface RegoValue extends Comparable<RegoValue> {
    default int length() {
        throw new UnsupportedOperationException("length() not implemented for " + this.getClass().getName());
    }

    Object nativeValue();

    // This is used to simplify the names, used for compiling with expected error messaging
    String getTypeName();

    @Override
    default int compareTo(RegoValue other) {
        if (other == null) {
            return 1;
        }

        // Values of different types are ordered as in Rego:
        // null < boolean < number < string < array < object < set
        int rankComparison = Integer.compare(typeRank(this), typeRank(other));
        if (rankComparison != 0) {
            return rankComparison;
        }
        // Types outside that order are grouped by name
        int typeComparison = this.getTypeName().compareTo(other.getTypeName());
        if (typeComparison != 0) {
            return typeComparison;
        }

        // Same type name - delegate to type-specific comparison
        // Handle cross-type numeric comparisons (RegoBigInt vs RegoInt32, etc.)
        if (this instanceof RegoNumber && other instanceof RegoNumber) {
            RegoNumber a = (RegoNumber) this;
            RegoNumber b = (RegoNumber) other;
            if (a.isDecimal() || b.isDecimal()) {
                return Double.compare(a.getDecimalValue(), b.getDecimalValue());
            } else {
                return a.getBigIntValue().compareTo(b.getBigIntValue());
            }
        } else if (this instanceof RegoString && other instanceof RegoString) {
            return ((RegoString) this).getValue().compareTo(((RegoString) other).getValue());
        } else if (this instanceof RegoInt32 && other instanceof RegoInt32) {
            return Integer.compare(((RegoInt32) this).getValue(), ((RegoInt32) other).getValue());
        } else if (this instanceof RegoBigInt && other instanceof RegoBigInt) {
            return ((RegoBigInt) this).getValue().compareTo(((RegoBigInt) other).getValue());
        } else if (this instanceof RegoDecimal && other instanceof RegoDecimal) {
            return Double.compare(((RegoDecimal) this).getValue(), ((RegoDecimal) other).getValue());
        } else if (this instanceof RegoNumber && other instanceof RegoNumber) {
            return Double.compare(((RegoNumber) this).getDecimalValue(), ((RegoNumber) other).getDecimalValue());
        } else if (this instanceof RegoBoolean && other instanceof RegoBoolean) {
            return Boolean.compare(((RegoBoolean) this).getValue(), ((RegoBoolean) other).getValue());
        } else if (this instanceof RegoNull && other instanceof RegoNull) {
            return 0; // All nulls are equal
        } else if (this instanceof RegoArray && other instanceof RegoArray) {
            // Compare arrays lexicographically
            RegoArray thisArray = (RegoArray) this;
            RegoArray otherArray = (RegoArray) other;
            int minLength = Math.min(thisArray.length(), otherArray.length());

            for (int i = 0; i < minLength; i++) {
                int elementComparison = thisArray.getValue().get(i).compareTo(otherArray.getValue().get(i));
                if (elementComparison != 0) {
                    return elementComparison;
                }
            }
            return Integer.compare(thisArray.length(), otherArray.length());
        } else if (this instanceof RegoObject && other instanceof RegoObject) {
            // Compare objects by their string representation for consistency
            return this.toString().compareTo(other.toString());
        } else if (this instanceof RegoSet && other instanceof RegoSet) {
            // Compare sets by size first, then by string representation
            RegoSet thisSet = (RegoSet) this;
            RegoSet otherSet = (RegoSet) other;
            int sizeComparison = Integer.compare(thisSet.length(), otherSet.length());
            if (sizeComparison != 0) {
                return sizeComparison;
            }
            return this.toString().compareTo(other.toString());
        }

        // Fallback to string representation comparison
        return this.toString().compareTo(other.toString());
    }

    private static int typeRank(RegoValue value) {
        if (value instanceof RegoNull) {
            return 0;
        } else if (value instanceof RegoBoolean) {
            return 1;
        } else if (value instanceof RegoNumber) {
            return 2;
        } else if (value instanceof RegoString) {
            return 3;
        } else if (value instanceof RegoArray) {
            return 4;
        } else if (value instanceof RegoObject) {
            return 5;
        } else if (value instanceof RegoSet) {
            return 6;
        }
        return -1;
    }
}
