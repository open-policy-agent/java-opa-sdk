package io.github.open_policy_agent.opa.ast.builtin.impls.utils;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import io.github.open_policy_agent.opa.ast.types.*;

/**
 * *** This implementation was "borrowed" from the OpaSwift code, so changes made here might need to
 * be made there
 *
 * <p>Utility class that implements Go's fmt.Sprintf-like formatting for OPA's sprintf() builtin.
 *
 * <p>This implementation attempts to faithfully follow the Go fmt package behavior: - Reference: <a
 * href="https://pkg.go.dev/fmt">...</a> - Implementation: <a
 * href="https://cs.opensource.google/go/go/+/refs/tags/go1.23.5:src/fmt/print.go;l=1019">...</a>
 *
 * <p>Supports a subset of types that OPA's sprintf() builtin can process. All arguments are
 * AST.RegoValues which constrains complexity compared to real fmt.Sprintf().
 */
public class SprintfUtil {

    private final String format;
    private final RegoValue[] args;
    private final StringBuilder result;
    private final FmtFlags flags = new FmtFlags();
    private int argIdx = 0;
    private int fmtIdx = 0;
    private SprintfUtil(String format, RegoValue[] args) {
        this.format = format;
        this.args = args;
        this.result = new StringBuilder();
    }

    public static String sprintf(String format, RegoValue[] args) {
    // Handle null args array - Java varargs treats sprintf("%v", null) as null array
    // We need to treat this as an array with one null element
    if (args == null) {
      args = new RegoValue[] {null};
    }
        return new SprintfUtil(format, args).print();
    }

    private Character currentChar() {
        return fmtIdx < format.length() ? format.charAt(fmtIdx) : null;
    }

    private Character next() {
        fmtIdx++;
        return currentChar();
    }

    private RegoValue currentArg() {
        return argIdx >= 0 && argIdx < args.length ? args[argIdx] : null;
    }

  private void nextArg() {
        argIdx++;
    }

    @SuppressWarnings("PMD.AvoidBranchingStatementAsLastInLoop")
    private String print() {
        if (format.isEmpty()) {
            return "";
        }

        boolean firstIteration = true;
        while (fmtIdx < format.length()) {
            Character c = firstIteration ? currentChar() : next();
            if (c == null) {
        break;
            }

            if (firstIteration) {
                firstIteration = false;
            }

            if (c != '%') {
                result.append(c);
                continue;
            }

            // Process the optional flags and verb following the % sign
            flags.reset();

            // Parse flags
            while (fmtIdx < format.length()) {
                c = next();
                if (c == null) {
                    break;
                }

                switch (c) {
                    case '#': flags.sharp = true; continue;
                    case '0': flags.zero = true; continue;
                    case '+': flags.plus = true; continue;
                    case '-': flags.minus = true; continue;
                    case ' ': flags.space = true; continue;
                    default: break;
                }
                break;
            }

            // Try parse arg index
            tryParseArgIndex();

            c = currentChar();
            if (c == null) {
                result.append(StringConsts.NO_VERB);
                continue;
            }

            // Check for width field
            if (c == '*') {
                RegoValue widthArg = currentArg();
                if (widthArg instanceof RegoNumber) {
          flags.width = ((RegoNumber) widthArg).getBigIntValue().intValue();

                    if (flags.width < 0) {
                        flags.width = Math.abs(flags.width);
                        flags.minus = true;
                        flags.zero = false;
                    }
                } else {
                    result.append(StringConsts.BAD_WIDTH);
                }
                nextArg();
                flags.afterArgIndex = false;

                if (next() == null) {
                    result.append(StringConsts.NO_VERB);
                    continue;
                }
            } else {
                Integer width = tryParseInt();
                if (width != null) {
                    flags.width = width;
                    if (flags.afterArgIndex) {
                        flags.invalidArgIndex = true;
                    }
                }
            }

            c = currentChar();
            if (c == null) {
                result.append(StringConsts.NO_VERB);
                continue;
            }

            // Check for precision
            if (c == '.') {
                if (flags.afterArgIndex) {
                    flags.invalidArgIndex = true;
                }

                if (next() == null) {
                    result.append(StringConsts.NO_VERB);
                    continue;
                }

                tryParseArgIndex();

                c = currentChar();
                if (c == null) {
                    result.append(StringConsts.NO_VERB);
                    continue;
                }

                if (c == '*') {
                    RegoValue precArg = currentArg();
                    if (precArg instanceof RegoNumber) {
            flags.precision = ((RegoNumber) precArg).getBigIntValue().intValue();

                        if (flags.precision < 0) {
                            flags.precision = null;
                        }
                    } else {
                        result.append(StringConsts.BAD_PRECISION);
                    }
                    nextArg();

                    if (next() == null) {
                        result.append(StringConsts.NO_VERB);
                        continue;
                    }

                    flags.afterArgIndex = false;
                } else {
                    flags.precision = tryParseInt();
                    if (flags.precision == null) {
                        flags.precision = 0;
                    }
                }
            }

            c = currentChar();
            if (c == null) {
                result.append(StringConsts.NO_VERB);
                continue;
            }

            if (!flags.afterArgIndex) {
                tryParseArgIndex();
            }

            char verb = currentChar() != null ? currentChar() : '\0';
            if (verb == '\0') {
                result.append(StringConsts.NO_VERB);
                continue;
            }

            // Process verb
            switch (verb) {
                case '%':
                    result.append('%');
                    break;
                case 'v':
                case 'w':
                        flags.sharpV = flags.sharp;
                        flags.sharp = false;
                        flags.plusV = flags.plus;
                        flags.plus = false;
                    // fallthrough
                default:
                    if (flags.invalidArgIndex) {
                        result.append(StringConsts.PERCENT_BANG);
                        result.append(verb);
                        result.append(StringConsts.BAD_INDEX);
                    } else if (argIdx >= args.length) {
                        result.append(StringConsts.PERCENT_BANG);
                        result.append(verb);
                        result.append(StringConsts.MISSING);
                    } else {
                        printCurrentArg(verb);
                    }
                    break;
            }
        }

    // Check for leftover args
    if (!flags.reorderedArgs && argIdx < args.length) {
            result.append(StringConsts.EXTRA);
            boolean first = true;
      while (argIdx < args.length) {
        RegoValue arg = currentArg();
                if (first) {
                    first = false;
                } else {
                    result.append(", ");
                }
        if (arg == null) {
          result.append("<nil>=");
        } else {
          result.append(arg.getTypeName()).append("=");
        }
        // Don't call printCurrentArg here as it will increment argIdx twice
        // Just append the value directly
        if (arg == null) {
          result.append(StringConsts.NIL_ANGLE);
        } else {
          result.append(arg);
        }
                nextArg();
            }
            result.append(")");
        }

        return result.toString();
    }

    private void tryParseArgIndex() {
        if (currentChar() == null || currentChar() != '[') {
            return;
        }

        next();
        int errIdx = fmtIdx;

    if (fmtIdx >= format.length() || format.length() - fmtIdx <= 2) {
            return;
        }

        Integer argIdxParsed = tryParseIntArg();
        if (argIdxParsed == null) {
            fmtIdx = errIdx;
            return;
        }

        int nextArgIdx = argIdxParsed - 1;

        if (nextArgIdx < 0 || nextArgIdx >= args.length) {
            flags.invalidArgIndex = true;
            return;
        }

        argIdx = nextArgIdx;
        flags.reorderedArgs = true;
    }

    private Integer tryParseIntArg() {
        int n = 0;
        while (fmtIdx < format.length()) {
            Character c = currentChar();
            if (c == null) {
                return null;
            }

            if (c == ']') {
                next();
                return n;
            }

            if (c < '0' || c > '9') {
                return null;
            }

            if (tooLarge(n)) {
                return null;
            }

            n = n * 10 + (c - '0');
            next();
        }
        return null;
    }

    private Integer tryParseInt() {
        int n = 0;
        boolean foundNum = false;

        while (fmtIdx < format.length()) {
            Character c = currentChar();
            if (c == null) {
                return null;
            }

            if (c < '0' || c > '9') {
                break;
            }

            foundNum = true;

            if (tooLarge(n)) {
                return null;
            }

            n = n * 10 + (c - '0');
            next();
        }

        return foundNum ? n : null;
    }

    private boolean tooLarge(int x) {
        return x > 100000;
    }

    private void printCurrentArg(char verb) {
        RegoValue arg = currentArg();

        if (arg == null) {
      nextArg();
            switch (verb) {
                case 'T':
                case 'v':
                    result.append(StringConsts.NIL_ANGLE);
                    break;
                default:
                    printBadVerb(verb, null);
                    break;
            }
            return;
        }

        nextArg();

        switch (verb) {
            case 'T':
        // Map Rego types to Go-like type names for compatibility
        if (arg instanceof RegoInt32 || arg instanceof RegoBigInt) {
          result.append("int");
        } else if (arg instanceof RegoDecimal) {
          result.append("float64");
        } else if (arg instanceof RegoString) {
          result.append("string");
        } else if (arg instanceof RegoBoolean) {
          result.append("bool");
        } else if (arg instanceof RegoArray) {
          result.append("array");
        } else if (arg instanceof RegoObject) {
          result.append("object");
        } else if (arg instanceof RegoSet) {
          result.append("set");
        } else if (arg instanceof RegoNull) {
          result.append("null");
        } else {
          result.append(arg.getTypeName());
        }
                return;
            case 'p':
                printBadVerb(verb, arg);
                return;
            default:
                break;
        }

        // Format based on type
        if (arg instanceof RegoNumber) {
            RegoNumber num = (RegoNumber) arg;
            if (num.isDecimal()) {
                fmtFloat(num, verb);
            } else {
        // For integers that fit in long, use long formatting
        // For BigIntegers that don't fit, handle specially
        try {
          long longValue = num.getBigIntValue().longValueExact();
          fmtInt(longValue, verb);
        } catch (ArithmeticException e) {
          // BigInteger doesn't fit in long - format using BigInteger methods
          fmtBigInt(num.getBigIntValue(), verb);
        }
            }
        } else if (arg instanceof RegoString) {
            fmtString(((RegoString) arg).getValue(), verb);
        } else if (arg instanceof RegoSet) {
            // Format sets with curly braces and sorted order (by type, then value)
            fmtString(formatSet((RegoSet) arg), verb);
        } else if (arg instanceof RegoObject) {
            // Format objects with curly braces and sorted keys
            fmtString(formatObject((RegoObject) arg), verb);
        } else {
            // Stringify other types
            fmtString(arg.toString(), verb);
        }
    }

    private String formatSet(RegoSet set) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");

        // Sort elements by type precedence: decimal, integer, string, others
        List<RegoValue> sortedValues = new ArrayList<>(set.getValue());
        sortedValues.sort((a, b) -> {
            int typeOrder = getTypePrecedence(a) - getTypePrecedence(b);
            if (typeOrder != 0) {
                return typeOrder;
            }
            // Same type, compare values
            return a.compareTo(b);
        });

        boolean first = true;
        for (RegoValue v : sortedValues) {
            if (!first) {
                sb.append(", ");
            }
            first = false;
            sb.append(formatValueForDisplay(v));
        }
        sb.append("}");
        return sb.toString();
    }

    private String formatObject(RegoObject obj) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");

    // Sort keys for consistent output - need to handle RegoValue keys
    List<RegoValue> sortedKeys = new ArrayList<>(obj.getProperties().keySet());
    sortedKeys.sort(
        (a, b) -> {
          // Sort by type first, then by value
          int typeOrder = getTypePrecedence(a) - getTypePrecedence(b);
          if (typeOrder != 0) {
            return typeOrder;
          }
          return a.compareTo(b);
        });

        boolean first = true;
    for (RegoValue key : sortedKeys) {
            if (!first) {
                sb.append(", ");
            }
            first = false;

            RegoValue value = obj.getProperty(key);

      // Format the key
      sb.append(formatValueForDisplay(key));
            sb.append(": ");
            sb.append(formatValueForDisplay(value));
        }
        sb.append("}");
        return sb.toString();
    }

  private void printArgValue(RegoValue arg) {
        // Format based on type without consuming the arg
        if (arg instanceof RegoNumber) {
            RegoNumber num = (RegoNumber) arg;
            if (num.isDecimal()) {
        fmtFloat(num, 'v');
            } else {
        fmtInt(num.getBigIntValue().longValue(), 'v');
            }
        } else if (arg instanceof RegoString) {
      fmtString(((RegoString) arg).getValue(), 'v');
        } else if (arg instanceof RegoSet) {
      fmtString(formatSet((RegoSet) arg), 'v');
        } else if (arg instanceof RegoObject) {
      fmtString(formatObject((RegoObject) arg), 'v');
        } else {
      // Stringify other types
      fmtString(arg.toString(), 'v');
        }
    }

    private int getTypePrecedence(RegoValue value) {
    // Type order based on Go's fmt output: boolean, decimal, integer, string, array, null, set,
    // object, others
    if (value instanceof RegoBoolean) {
            return 0;
    } else if (value instanceof RegoDecimal) {
            return 1;
    } else if (value instanceof RegoInt32 || value instanceof RegoBigInt) {
            return 2;
    } else if (value instanceof RegoString) {
      return 3;
    } else if (value instanceof RegoArray) {
      return 4;
    } else if (value instanceof RegoNull) {
      return 5;
    } else if (value instanceof RegoSet) {
      return 6;
    } else if (value instanceof RegoObject) {
      return 7;
        } else {
      return 8;
        }
    }

    private String formatValueForDisplay(RegoValue value) {
        if (value instanceof RegoString) {
            return "\"" + ((RegoString) value).getValue() + "\"";
    } else if (value instanceof RegoSet) {
      return formatSet((RegoSet) value);
    } else if (value instanceof RegoObject) {
      return formatObject((RegoObject) value);
    } else if (value instanceof RegoArray) {
      return formatArray((RegoArray) value);
    } else if (value instanceof RegoNull) {
      return "null";
        }
        return value.toString();
  }

  private String formatArray(RegoArray array) {
    StringBuilder sb = new StringBuilder();
    sb.append("[");
    boolean first = true;
    for (RegoValue v : array.getValue()) {
      if (!first) {
        sb.append(", ");
      }
      first = false;
      sb.append(formatValueForDisplay(v));
    }
    sb.append("]");
    return sb.toString();
    }

    private void printBadVerb(char verb, RegoValue arg) {
        result.append(StringConsts.PERCENT_BANG).append(verb).append("(");
        if (arg == null) {
            result.append(StringConsts.NIL_ANGLE);
        } else {
            result.append(regoTypeToGoType(arg)).append("=");
      printArgValue(arg);
        }
        result.append(")");
    }

    private String regoTypeToGoType(RegoValue value) {
        if (value instanceof RegoNumber) {
            RegoNumber num = (RegoNumber) value;
            return num.isDecimal() ? "float64" : "int";
    } else if (value instanceof RegoBoolean) {
      return "string";
        } else if (value instanceof RegoArray) {
            return "string";
        } else if (value instanceof RegoObject) {
            return "string";
        } else if (value instanceof RegoSet) {
            return "string";
    } else if (value instanceof RegoNull) {
      return "string";
        }
        return value.getTypeName();
    }

    private void fmtInt(long n, char verb) {
        switch (verb) {
            case 'v':
            case 'd':
                fmtIntBase(n, 10, verb, false);
                break;
            case 'b':
                fmtIntBase(n, 2, verb, false);
                break;
            case 'o':
            case 'O':
                fmtIntBase(n, 8, verb, false);
                break;
            case 'x':
                fmtIntBase(n, 16, verb, false);
                break;
            case 'X':
                fmtIntBase(n, 16, verb, true);
                break;
            case 'c':
                fmtC((int) n);
                break;
            case 'q':
                fmtQc((int) n);
                break;
            case 'U':
                fmtUnicode((int) n);
                break;
            default:
                printBadVerb(verb, new RegoBigInt(BigInteger.valueOf(n)));
                break;
        }
    }

  private void fmtBigInt(BigInteger n, char verb) {
    switch (verb) {
      case 'v':
      case 'd':
        fmtBigIntBase(n, 10, verb, false);
        break;
      case 'b':
        fmtBigIntBase(n, 2, verb, false);
        break;
      case 'o':
      case 'O':
        fmtBigIntBase(n, 8, verb, false);
        break;
      case 'x':
        fmtBigIntBase(n, 16, verb, false);
        break;
      case 'X':
        fmtBigIntBase(n, 16, verb, true);
        break;
      case 's':
        fmtString(n.toString(), verb);
        break;
      default:
        printBadVerb(verb, new RegoBigInt(n));
        break;
    }
  }

  private void fmtBigIntBase(BigInteger n, int base, char verb, boolean upperCase) {
    boolean isNegative = n.signum() < 0;
    BigInteger absN = n.abs();

    String formatted = upperCase ? absN.toString(base).toUpperCase() : absN.toString(base);

    // Calculate total length of output
    int totalLength = formatted.length();

    // Add sign length
    if (isNegative || flags.plus || flags.space) {
      totalLength += 1;
    }

    // Add prefix length
    if (base == 2 && flags.sharp) {
      totalLength += 2; // "0b"
    } else if (base == 8) {
      if (verb == 'O') {
        totalLength += 2; // "0o"
      } else if (flags.sharp && !formatted.startsWith("0")) {
        totalLength += 1; // "0"
      }
    } else if (base == 16 && flags.sharp) {
      totalLength += 2; // "0x" or "0X"
    }

    // Handle width padding (left padding if not minus flag)
    if (flags.width != null && flags.width > totalLength && !flags.minus && !flags.zero) {
      printPadding(flags.width - totalLength);
    }

    // Sign
    if (isNegative) {
      result.append('-');
    } else if (flags.plus) {
      result.append('+');
    } else if (flags.space) {
      result.append(' ');
    }

    // Prefix
    if (base == 2 && flags.sharp) {
      result.append("0b");
    } else if (base == 8) {
      if (verb == 'O') {
        result.append("0o");
      } else if (flags.sharp && !formatted.startsWith("0")) {
        result.append('0');
      }
    } else if (base == 16 && flags.sharp) {
      result.append(upperCase ? "0X" : "0x");
    }

    result.append(formatted);

    // Handle width padding (right padding if minus flag)
    if (flags.width != null && flags.width > totalLength && flags.minus) {
      printPadding(flags.width - totalLength);
    }
  }

    private void fmtIntBase(long n, int base, char verb, boolean upperCase) {
        boolean isNegative = n < 0;
        long absN = Math.abs(n);

        int precision = 0;
        if (flags.precision != null) {
            precision = flags.precision;
            if (precision == 0 && n == 0) {
                boolean oldZero = flags.zero;
                flags.zero = false;
                printPadding(flags.width != null ? flags.width : 0);
                flags.zero = oldZero;
                return;
            }
        } else if (flags.zero && !flags.minus && flags.width != null) {
            precision = flags.width;
            if (isNegative || flags.plus || flags.space) {
                precision -= 1;
            }
        }

        String formatted = upperCase ?
            Long.toString(absN, base).toUpperCase() :
            Long.toString(absN, base);

    // Calculate total length of output
    int totalLength = formatted.length();

    // Add sign length
    if (isNegative || flags.plus || flags.space) {
      totalLength += 1;
    }

    // Add prefix length
    if (base == 2 && flags.sharp) {
      totalLength += 2; // "0b"
    } else if (base == 8) {
      if (verb == 'O') {
        totalLength += 2; // "0o"
      } else if (flags.sharp && !formatted.startsWith("0")) {
        totalLength += 1; // "0"
      }
    } else if (base == 16 && flags.sharp) {
      totalLength += 2; // "0x" or "0X"
    }

    // Add zero padding from precision
    int zeroPadding = precision - formatted.length();
    if (zeroPadding > 0) {
      totalLength += zeroPadding;
    }

    // Handle width padding (left padding if not minus flag)
    if (flags.width != null && flags.width > totalLength && !flags.minus && !flags.zero) {
      printPadding(flags.width - totalLength);
    }

        // Sign
        if (isNegative) {
            result.append('-');
        } else if (flags.plus) {
            result.append('+');
        } else if (flags.space) {
            result.append(' ');
        }

        // Prefix
        if (base == 2 && flags.sharp) {
            result.append("0b");
    } else if (base == 8) {
            if (verb == 'O') {
        result.append("0o");
      } else if (flags.sharp && !formatted.startsWith("0")) {
        result.append('0');
            }
        } else if (base == 16 && flags.sharp) {
            result.append(upperCase ? "0X" : "0x");
        }

    // Zero padding
    if (zeroPadding > 0) {
      printPadding(zeroPadding);
        }

        result.append(formatted);

    // Handle width padding (right padding if minus flag)
    if (flags.width != null && flags.width > totalLength && flags.minus) {
      printPadding(flags.width - totalLength);
    }
    }

    private void fmtFloat(RegoNumber num, char verb) {
    double value = num.getDecimalValue();

        switch (verb) {
            case 'v':
        // For %v with infinite values, format as Go does: +Inf or -Inf
        if (Double.isInfinite(value)) {
          // Check if we have an original string (for overflow cases like 2e308)
          String str = num.toString();
          if (str.equals("Infinity") || str.equals("-Infinity")) {
            // Standard infinity without original string - format as Go does
            if (value < 0) {
              result.append("-Inf");
            } else {
              result.append("+Inf");
            }
          } else {
            // Has original string (e.g., "2e308"), use it
            fmtS(str);
          }
        } else {
          fmtFloatVerb(value, 'g', -1);
        }
                break;
            case 'b':
                // Go's %b for floats is binary exponent (e.g., -123456p-78)
                // Java doesn't support this, so we use a custom implementation
                fmtFloatBinary(value);
                break;
            case 'g':
            case 'G':
                fmtFloatVerb(value, verb, -1);
                break;
            case 'x':
            case 'X':
                // Java doesn't support %x/%X for floats, convert to hex representation
                fmtFloatHex(value, verb == 'X');
                break;
            case 'f':
            case 'e':
            case 'E':
                fmtFloatVerb(value, verb, 6);
                break;
            case 'F':
                fmtFloatVerb(value, 'f', 6);
                break;
            default:
                printBadVerb(verb, num);
                break;
        }
    }

    private void fmtFloatBinary(double value) {
    // Go's %b format outputs mantissa×2^exp where mantissa is odd
    // This matches Go's strconv.FormatFloat behavior

    if (Double.isNaN(value)) {
            result.append("NaN");
            return;
        }
        if (Double.isInfinite(value)) {
      boolean negative = value < 0;
            if (negative) {
                result.append("-Inf");
            } else if (flags.plus) {
                result.append("+Inf");
            } else if (flags.space) {
                result.append(" Inf");
            } else {
                result.append("+Inf");
            }
            return;
        }
        if (value == 0.0) {
      boolean negative = Double.doubleToRawLongBits(value) < 0;
            if (negative) {
                result.append("-0p+00");
            } else if (flags.plus) {
                result.append("+0p+00");
            } else if (flags.space) {
                result.append(" 0p+00");
            } else {
                result.append("0p+00");
            }
            return;
        }

    boolean negative = value < 0;
    long bits = Double.doubleToLongBits(Math.abs(value));
    int rawExp = (int) ((bits & 0x7FF0000000000000L) >> 52);
    long mant = bits & 0x000FFFFFFFFFFFFFL;

    if (rawExp != 0) {
      mant |= (1L << 52);
        }

    int exp = (rawExp != 0) ? rawExp - 1023 - 52 : 1 - 1023 - 52;

    int zeros = Long.numberOfTrailingZeros(mant);
    mant >>>= zeros;
    exp += zeros;

        String sign = negative ? "-" : (flags.plus ? "+" : (flags.space ? " " : ""));
    result.append(String.format("%s%dp%s%d", sign, mant, exp >= 0 ? "+" : "-", Math.abs(exp)));
    }

    private void fmtFloatHex(double value, boolean uppercase) {
    // Convert double to its hexadecimal representation matching Go's precision
    // Go's %x for floats produces hex with p notation (e.g., 0x1.8b0f27bb2fec5p+03)

    if (Double.isNaN(value)) {
      result.append(uppercase ? "NAN" : "NaN");
      return;
    }

    boolean negative = value < 0 || (value == 0.0 && Double.doubleToRawLongBits(value) < 0);
    if (Double.isInfinite(value)) {
      String sign = negative ? "-" : (flags.plus ? "+" : (flags.space ? " " : ""));
      result.append(sign).append(uppercase ? "INF" : "Inf");
      return;
    }

    if (value == 0.0) {
      String sign = negative ? "-" : (flags.plus ? "+" : (flags.space ? " " : ""));
      String prefix = uppercase ? "0X" : "0x";
      result.append(sign).append(prefix).append("0P+00");
      return;
        }

    // Extract bits
    long bits = Double.doubleToLongBits(Math.abs(value));
    int rawExp = (int) ((bits & 0x7FF0000000000000L) >> 52);
    long mantBits = bits & 0x000FFFFFFFFFFFFFL; // 52 bits of fraction

    // Normalized or denormalized
    int exp;
    if (rawExp != 0) {
      // Normalized number
      exp = rawExp - 1023;
    } else {
      // Denormalized
      exp = 1 - 1023;
    }

    // For normalized numbers, the mantissa in hex format is 1.ffffffffffff
    // where ffffffffffff is the 52-bit fraction in hex (13 hex digits)
    // Format the 52-bit fraction as 13 hex digits
    String fractionalHex = String.format("%013x", mantBits);
    if (uppercase) {
      fractionalHex = fractionalHex.toUpperCase();
        }

    // Build the result: [sign]0x1.mantissap[+/-]exp
    String sign = negative ? "-" : (flags.plus ? "+" : (flags.space ? " " : ""));
    String prefix = uppercase ? "0X" : "0x";

    // Remove trailing zeros from fractional part
    while (!fractionalHex.isEmpty() && fractionalHex.charAt(fractionalHex.length() - 1) == '0') {
      fractionalHex = fractionalHex.substring(0, fractionalHex.length() - 1);
    }

    String expSign = exp >= 0 ? "+" : "";
    String formatted = String.format("%s%s1.%sP%s%02d", sign, prefix, fractionalHex, expSign, exp);

    result.append(formatted);
    }

    private void fmtFloatVerb(double value, char verb, int defaultPrecision) {
        int precision = flags.precision != null ? flags.precision : defaultPrecision;

        // Build basic format string without width/padding flags
        StringBuilder formatBuilder = new StringBuilder("%");

        // Only add sign flags to the Java format string
        if (flags.plus) {
            formatBuilder.append("+");
        } else if (flags.space) {
            formatBuilder.append(" ");
        }

        if (flags.sharp) {
            formatBuilder.append("#");
        }

    // Add precision
    // For %g with default precision (-1), we want compact output
    // Go's default %g precision is 6 significant digits, but removes trailing zeros
    if (precision >= 0) {
            formatBuilder.append(".").append(precision);
    }

        // Add the verb
        formatBuilder.append(verb);

        // Format the number
        String formatted = String.format(formatBuilder.toString(), value);

    // For %g/%G without sharp flag, strip trailing zeros and decimal point if needed
    if ((verb == 'g' || verb == 'G') && !flags.sharp && precision < 0) {
      formatted = stripTrailingZeros(formatted);
    }

        // Handle width and padding manually
        if (flags.width != null && flags.width > formatted.length()) {
            int paddingSize = flags.width - formatted.length();

            if (flags.minus) {
                // Left justify
                result.append(formatted);
                printPadding(paddingSize);
            } else if (flags.zero) {
                // Zero padding: insert zeros after sign
                int signEnd = 0;
        if (!formatted.isEmpty()
            && (formatted.charAt(0) == '+'
                || formatted.charAt(0) == '-'
                || formatted.charAt(0) == ' ')) {
                    result.append(formatted.charAt(0));
                    signEnd = 1;
                }
        result.append("0".repeat(paddingSize));
                result.append(formatted.substring(signEnd));
            } else {
                // Right justify with spaces
                printPadding(paddingSize);
                result.append(formatted);
            }
        } else {
            result.append(formatted);
        }
  }

  private String stripTrailingZeros(String s) {
    // Strip trailing zeros from decimal numbers
    // But preserve at least one digit after decimal point if there is one
    if (!s.contains(".") && !s.contains("e") && !s.contains("E")) {
      return s;
    }

    // Handle scientific notation separately
    int eIndex = s.indexOf('e');
    if (eIndex == -1) {
      eIndex = s.indexOf('E');
    }

    String mantissa;
    String exponent = "";
    if (eIndex != -1) {
      mantissa = s.substring(0, eIndex);
      exponent = s.substring(eIndex);
    } else {
      mantissa = s;
    }

    // Strip trailing zeros from mantissa
    if (mantissa.contains(".")) {
      mantissa = mantissa.replaceAll("0+$", "");
      // Remove trailing decimal point
      mantissa = mantissa.replaceAll("\\.$", "");
    }

    return mantissa + exponent;
    }

    private void fmtString(String string, char verb) {
        switch (verb) {
            case 'v':
                if (flags.sharpV) {
                    fmtQ(string);
                } else {
                    fmtS(string);
                }
                break;
            case 's':
                fmtS(string);
                break;
            case 'x':
                fmtSx(string, false);
                break;
            case 'X':
                fmtSx(string, true);
                break;
            case 'q':
                fmtQ(string);
                break;
            default:
                printBadVerb(verb, new RegoString(string));
                break;
        }
    }

    private void fmtC(int n) {
        printPaddedString(intAsUnicode(n));
    }

    private void fmtQc(int n) {
    // %q for characters uses single quotes
    String ch = intAsUnicode(n);
    StringBuilder escaped = new StringBuilder();
    for (char c : ch.toCharArray()) {
      switch (c) {
        case '\\':
          escaped.append("\\\\");
          break;
        case '\'':
          escaped.append("\\'");
          break;
        default:
          if (c > 127 && flags.plus) {
            escaped.append(String.format("\\u%04X", (int) c));
          } else {
            escaped.append(c);
          }
          break;
      }
    }
    printPaddedString("'" + escaped + "'");
    }

    private void fmtUnicode(int n) {
    // %U format always uses at least 4 hex digits with leading zeros
    String str = String.format("U+%04X", n);
        if (flags.sharp) {
            str += " '" + intAsUnicode(n) + "'";
        }
        printPaddedString(str);
    }

    private String intAsUnicode(int n) {
        if (Character.isValidCodePoint(n)) {
            return String.valueOf(Character.toChars(n));
        }
        return "\uFFFD";
    }

    private void fmtQ(String string) {
        string = truncateString(string);

        if (flags.sharp && canBackquote(string)) {
            printPaddedString("`" + string + "`");
            return;
        }

        StringBuilder escaped = new StringBuilder();
        for (char c : string.toCharArray()) {
            switch (c) {
                case '\\':
                    escaped.append("\\\\");
                    break;
                case '"':
                    escaped.append("\\\"");
                    break;
                default:
                    if (c > 127 && flags.plus) {
                        escaped.append(String.format("\\u%04X", (int) c));
                    } else {
                        escaped.append(c);
                    }
                    break;
            }
        }

        printPaddedString("\"" + escaped + "\"");
    }

    private boolean canBackquote(String string) {
        if (string.isEmpty()) {
            return true;
        }

        for (char c : string.toCharArray()) {
      if (c < ' ' || c == '`' || c == 0x7F || c == 0xFEFF) {
                return false;
            }
        }
        return true;
    }

    private void fmtS(String string) {
        printPaddedString(truncateString(string));
    }

    private void fmtSx(String string, boolean uppercase) {
        byte[] bytes = string.getBytes(java.nio.charset.StandardCharsets.UTF_8);

        int length = flags.precision != null ? flags.precision : string.length();
        int encodedWidth = 2 * length;

        if (encodedWidth <= 0) {
            printPaddedString("");
            return;
        }

        if (flags.space) {
            if (flags.sharp) {
                encodedWidth *= 2;
            }
            encodedWidth += length - 1;
        } else if (flags.sharp) {
            encodedWidth += 2;
        }

        if (!flags.minus && flags.width != null && flags.width > encodedWidth) {
            printPadding(flags.width - encodedWidth);
        }

        String hexPrefix = uppercase ? "0X" : "0x";
        String byteFmt = uppercase ? "%02X" : "%02x";

        boolean firstByte = true;
        for (byte b : bytes) {
            if (firstByte) {
                if (flags.sharp) {
                    result.append(hexPrefix);
                }
                firstByte = false;
            } else if (flags.space) {
                result.append(' ');
                if (flags.sharp) {
                    result.append(hexPrefix);
                }
            }

            result.append(String.format(byteFmt, b & 0xFF));
        }

        if (flags.minus && flags.width != null && flags.width > encodedWidth) {
            printPadding(flags.width - encodedWidth);
        }
    }

    private String truncateString(String string) {
        if (flags.precision == null || flags.precision <= 0) {
            return string;
        }

        return string.length() <= flags.precision ?
            string :
            string.substring(0, flags.precision);
    }

    private void printPaddedString(String string) {
        if (flags.width == null || flags.width <= 0) {
            result.append(string);
            return;
        }

        int paddingSize = flags.width - string.length();

        if (!flags.minus) {
            printPadding(paddingSize);
            result.append(string);
        } else {
            result.append(string);
            printPadding(paddingSize);
        }
    }

    private void printPadding(int count) {
        if (count <= 0) {
            return;
        }

        char padChar = (flags.zero && !flags.minus) ? '0' : ' ';
    result.append(String.valueOf(padChar).repeat(count));
    }

    private static class FmtFlags {
        boolean plus = false;
        boolean minus = false;
        boolean sharp = false;
        boolean space = false;
        boolean zero = false;
        Integer width = null;
        Integer precision = null;

        boolean sharpV = false;
        boolean plusV = false;

        boolean reorderedArgs = false;
        boolean afterArgIndex = false;
        boolean invalidArgIndex = false;

        void reset() {
            plus = minus = sharp = space = zero = false;
            width = precision = null;
            sharpV = plusV = false;
            reorderedArgs = afterArgIndex = invalidArgIndex = false;
        }
    }

    private static class StringConsts {
        static final String NIL_ANGLE = "<nil>";
        static final String PERCENT_BANG = "%!";
        static final String MISSING = "(MISSING)";
        static final String BAD_INDEX = "(BADINDEX)";
        static final String EXTRA = "%!(EXTRA";
        static final String BAD_WIDTH = "%!(BADWIDTH)";
        static final String BAD_PRECISION = "%!(BADPREC)";
        static final String NO_VERB = "%!(NOVERB)";
    }
}
