package io.github.open_policy_agent.opa.proto;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.ListValue;
import com.google.protobuf.NullValue;
import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Verifies that protobuf Struct/Value conversion matches the Java types the JSON {@code .manifest}
 * reader produces — in particular that integral numbers become Integer/Long (not Double), so
 * metadata is identical across the two bundle wire formats.
 */
class StructConverterTest {

  @Test
  void integralNumbersBecomeIntegerNotDouble() {
    // Regression for the parity bug: proto stores all numbers as double, but the JSON reader yields
    // Integer for values that fit an int.
    assertThat(StructConverter.toObject(Value.newBuilder().setNumberValue(3).build()))
        .isEqualTo(3)
        .isInstanceOf(Integer.class);
  }

  @Test
  void largeIntegralNumbersBecomeLong() {
    long big = 10_000_000_000L; // > Integer.MAX_VALUE
    assertThat(StructConverter.toObject(Value.newBuilder().setNumberValue(big).build()))
        .isEqualTo(big)
        .isInstanceOf(Long.class);
  }

  @Test
  void fractionalNumbersStayDouble() {
    assertThat(StructConverter.toObject(Value.newBuilder().setNumberValue(3.5).build()))
        .isEqualTo(3.5)
        .isInstanceOf(Double.class);
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
