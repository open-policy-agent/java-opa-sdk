package io.github.open_policy_agent.opa.proto;

import com.google.protobuf.ListValue;
import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Converts protobuf {@code google.protobuf.Struct}/{@code Value} trees (used for the free-form
 * {@code metadata}, {@code custom}, and {@code labels} fields of the proto manifest) into plain Java
 * objects, mirroring how the equivalent JSON would deserialize.
 *
 * <p>Objects become {@link LinkedHashMap} (preserving field order), arrays become {@link ArrayList},
 * numbers become {@link Double}, and null/string/bool map to their obvious Java counterparts.
 */
final class StructConverter {

  private StructConverter() {}

  static Map<String, Object> toMap(Struct struct) {
    Map<String, Object> out = new LinkedHashMap<>();
    for (Map.Entry<String, Value> e : struct.getFieldsMap().entrySet()) {
      out.put(e.getKey(), toObject(e.getValue()));
    }
    return out;
  }

  static Object toObject(Value value) {
    switch (value.getKindCase()) {
      case NULL_VALUE:
        return null;
      case NUMBER_VALUE:
        return number(value.getNumberValue());
      case STRING_VALUE:
        return value.getStringValue();
      case BOOL_VALUE:
        return value.getBoolValue();
      case STRUCT_VALUE:
        return toMap(value.getStructValue());
      case LIST_VALUE:
        return toList(value.getListValue());
      case KIND_NOT_SET:
      default:
        return null;
    }
  }

  /**
   * Maps a protobuf Struct number (always a double) to the Java type the JSON {@code .manifest}
   * reader would produce for the same value: an integral value becomes {@link Integer} (or {@link
   * Long} when it overflows {@code int}), and a fractional value stays a {@link Double}. Without
   * this, {@code {"version": 3}} would decode as {@code 3.0} from a proto bundle but {@code 3} from
   * a JSON bundle, breaking metadata parity between the two formats.
   */
  private static Object number(double d) {
    if (d == Math.rint(d) && !Double.isInfinite(d)) {
      long asLong = (long) d;
      if (asLong == d) {
        if (asLong >= Integer.MIN_VALUE && asLong <= Integer.MAX_VALUE) {
          return (int) asLong;
        }
        return asLong;
      }
    }
    return d;
  }

  private static List<Object> toList(ListValue listValue) {
    List<Object> out = new ArrayList<>(listValue.getValuesCount());
    for (Value v : listValue.getValuesList()) {
      out.add(toObject(v));
    }
    return out;
  }
}
