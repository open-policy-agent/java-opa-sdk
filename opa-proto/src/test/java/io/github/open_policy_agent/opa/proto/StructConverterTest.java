package io.github.open_policy_agent.opa.proto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import com.google.protobuf.ListValue;
import com.google.protobuf.NullValue;
import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Verifies that protobuf Struct/Value conversion matches the Java types the JSON {@code .manifest}
 * reader produces — in particular that integral numbers become Integer/Long (not Double), so
 * metadata is identical across the two bundle wire formats.
 */
class StructConverterTest {

  static Stream<Arguments> numberConversions() {
    // proto stores all numbers as double, but the JSON reader yields the narrowest type that fits:
    // Integer for int-range values, Long for larger integrals, Double only for fractionals.
    return Stream.of(
        arguments("integral fits int", 3d, 3, Integer.class),
        arguments("integral exceeds int", 10_000_000_000d, 10_000_000_000L, Long.class),
        arguments("fractional", 3.5d, 3.5, Double.class));
  }

  @ParameterizedTest(name = "{0} -> {3}")
  @MethodSource("numberConversions")
  void numbersConvertToNarrowestType(String label, double input, Object expected, Class<?> type) {
    assertThat(StructConverter.toObject(Value.newBuilder().setNumberValue(input).build()))
        .as(label)
        .isEqualTo(expected)
        .isInstanceOf(type);
  }

  @Test
  void scalarsAndContainersConvert() {
    assertThat(StructConverter.toObject(Value.newBuilder().setStringValue("x").build())).isEqualTo("x");
    assertThat(StructConverter.toObject(Value.newBuilder().setBoolValue(true).build())).isEqualTo(true);
    assertThat(
            StructConverter.toObject(
                Value.newBuilder().setNullValue(NullValue.NULL_VALUE).build()))
        .isNull();

    Value list =
        Value.newBuilder()
            .setListValue(
                ListValue.newBuilder()
                    .addValues(Value.newBuilder().setNumberValue(1).build())
                    .addValues(Value.newBuilder().setStringValue("a").build()))
            .build();
    assertThat(StructConverter.toObject(list)).isEqualTo(List.of(1, "a"));
  }

  @Test
  void nestedStructPreservesOrderAndTypes() {
    Struct struct =
        Struct.newBuilder()
            .putFields("version", Value.newBuilder().setNumberValue(2).build())
            .putFields("ratio", Value.newBuilder().setNumberValue(0.25).build())
            .putFields("name", Value.newBuilder().setStringValue("bundle").build())
            .build();

    Map<String, Object> result = StructConverter.toMap(struct);

    assertThat(result).containsEntry("version", 2).containsEntry("ratio", 0.25).containsEntry("name", "bundle");
    assertThat(result.get("version")).isInstanceOf(Integer.class);
  }
}
