package io.github.open_policy_agent.opa.jackson;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.open_policy_agent.opa.ast.types.RegoArray;
import io.github.open_policy_agent.opa.ast.types.RegoBigInt;
import io.github.open_policy_agent.opa.ast.types.RegoBoolean;
import io.github.open_policy_agent.opa.ast.types.RegoDecimal;
import io.github.open_policy_agent.opa.ast.types.RegoInt32;
import io.github.open_policy_agent.opa.ast.types.RegoNull;
import io.github.open_policy_agent.opa.ast.types.RegoObject;
import io.github.open_policy_agent.opa.ast.types.RegoSet;
import io.github.open_policy_agent.opa.ast.types.RegoString;
import io.github.open_policy_agent.opa.ast.types.RegoValue;
import java.io.IOException;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RegoValueModuleTest {

  private final ObjectMapper mapper = new ObjectMapper().registerModule(new RegoValueModule());

  // --- Serialization (one test per RegoValue subtype) ---

  @Test
  void serialize_regoString() throws IOException {
    assertThat(mapper.writeValueAsString(new RegoString("hello"))).isEqualTo("\"hello\"");
  }

  @Test
  void serialize_regoInt32() throws IOException {
    assertThat(mapper.writeValueAsString(RegoInt32.of(42))).isEqualTo("42");
  }

  @Test
  void serialize_regoBigInt() throws IOException {
    assertThat(mapper.writeValueAsString(new RegoBigInt(new BigInteger("12345678901234567890"))))
        .isEqualTo("12345678901234567890");
  }

  @Test
  void serialize_regoDecimal() throws IOException {
    assertThat(mapper.writeValueAsString(new RegoDecimal(3.14))).isEqualTo("3.14");
  }

  @Test
  void serialize_regoBoolean() throws IOException {
    assertThat(mapper.writeValueAsString(RegoBoolean.TRUE)).isEqualTo("true");
    assertThat(mapper.writeValueAsString(RegoBoolean.FALSE)).isEqualTo("false");
  }

  @Test
  void serialize_regoNull() throws IOException {
    assertThat(mapper.writeValueAsString(RegoNull.INSTANCE)).isEqualTo("null");
  }

  @Test
  void serialize_regoArray_preservesOrder() throws IOException {
    RegoArray arr = new RegoArray();
    arr.addValue(new RegoString("a"));
    arr.addValue(RegoInt32.of(1));
    arr.addValue(RegoBoolean.TRUE);
    assertThat(mapper.writeValueAsString(arr)).isEqualTo("[\"a\",1,true]");
  }

  @Test
  void serialize_regoSet_emittedAsJsonArray() throws IOException {
    RegoSet set = new RegoSet(false);
    set.addValue(new RegoString("a"));
    String json = mapper.writeValueAsString(set);
    assertThat(json).startsWith("[").endsWith("]");
    assertThat(json).contains("\"a\"");
  }

  @Test
  void serialize_regoObject_sortsKeys() throws IOException {
    RegoObject obj = new RegoObject();
    obj.setProp(new RegoString("z"), new RegoString("last"));
    obj.setProp(new RegoString("a"), new RegoString("first"));
    obj.setProp(new RegoString("m"), new RegoString("middle"));
    // Keys are emitted sorted to match OPA Go runtime output.
    assertThat(mapper.writeValueAsString(obj))
        .isEqualTo("{\"a\":\"first\",\"m\":\"middle\",\"z\":\"last\"}");
  }

  @Test
  void serialize_regoObject_numericKeysCoercedToString() throws IOException {
    RegoObject obj = new RegoObject();
    obj.setProp(new RegoBigInt(BigInteger.valueOf(7)), new RegoString("seven"));
    assertThat(mapper.writeValueAsString(obj)).isEqualTo("{\"7\":\"seven\"}");
  }

  @Test
  void serialize_nestedStructure() throws IOException {
    RegoObject obj = new RegoObject();
    RegoArray arr = new RegoArray();
    arr.addValue(RegoInt32.of(1));
    arr.addValue(RegoNull.INSTANCE);
    obj.setProp(new RegoString("items"), arr);
    obj.setProp(new RegoString("ok"), RegoBoolean.TRUE);
    assertThat(mapper.writeValueAsString(obj))
        .isEqualTo("{\"items\":[1,null],\"ok\":true}");
  }

  // --- Deserialization ---

  @Test
  void deserialize_regoObject_simple() throws IOException {
    RegoObject obj = mapper.readValue("{\"name\":\"alice\",\"age\":30}", RegoObject.class);
    assertThat(((RegoString) obj.getProperty(new RegoString("name"))).getValue()).isEqualTo("alice");
    RegoValue age = obj.getProperty(new RegoString("age"));
    assertThat(age).isInstanceOf(RegoBigInt.class);
    assertThat(((RegoBigInt) age).getValue()).isEqualTo(BigInteger.valueOf(30));
  }

  @Test
  void deserialize_regoObject_nested() throws IOException {
    String json =
        "{\"user\":{\"id\":\"alice\",\"groups\":[\"admin\",\"user\"]},\"active\":true,\"score\":1.5}";
    RegoObject obj = mapper.readValue(json, RegoObject.class);

    RegoValue user = obj.getProperty(new RegoString("user"));
    assertThat(user).isInstanceOf(RegoObject.class);
    RegoObject userObj = (RegoObject) user;
    assertThat(((RegoString) userObj.getProperty(new RegoString("id"))).getValue())
        .isEqualTo("alice");

    RegoValue groups = userObj.getProperty(new RegoString("groups"));
    assertThat(groups).isInstanceOf(RegoArray.class);
    assertThat(((RegoArray) groups).getValues())
        .hasSize(2)
        .containsExactly(new RegoString("admin"), new RegoString("user"));

    assertThat(obj.getProperty(new RegoString("active"))).isEqualTo(RegoBoolean.TRUE);
    assertThat(obj.getProperty(new RegoString("score"))).isInstanceOf(RegoDecimal.class);
  }

  @Test
  void deserialize_regoObject_handlesNullValue() throws IOException {
    RegoObject obj = mapper.readValue("{\"missing\":null}", RegoObject.class);
    assertThat(obj.getProperty(new RegoString("missing"))).isEqualTo(RegoNull.INSTANCE);
  }

  @Test
  void deserialize_regoObject_rejectsNonObjectInput() {
    assertThatThrownBy(() -> mapper.readValue("[1,2,3]", RegoObject.class))
        .isInstanceOf(IOException.class)
        .hasMessageContaining("expected JSON object");
  }

  @Test
  void roundTrip_complexObject() throws IOException {
    RegoObject obj = new RegoObject();
    obj.setProp(new RegoString("name"), new RegoString("alice"));
    obj.setProp(new RegoString("count"), new RegoBigInt(BigInteger.valueOf(7)));
    obj.setProp(new RegoString("active"), RegoBoolean.TRUE);
    RegoArray tags = new RegoArray();
    tags.addValue(new RegoString("admin"));
    tags.addValue(new RegoString("user"));
    obj.setProp(new RegoString("tags"), tags);

    String json = mapper.writeValueAsString(obj);
    RegoObject restored = mapper.readValue(json, RegoObject.class);

    assertThat(((RegoString) restored.getProperty(new RegoString("name"))).getValue())
        .isEqualTo("alice");
    assertThat(((RegoBigInt) restored.getProperty(new RegoString("count"))).getValue())
        .isEqualTo(BigInteger.valueOf(7));
    assertThat(restored.getProperty(new RegoString("active"))).isEqualTo(RegoBoolean.TRUE);
    RegoArray restoredTags = (RegoArray) restored.getProperty(new RegoString("tags"));
    assertThat(restoredTags.getValues())
        .containsExactly(new RegoString("admin"), new RegoString("user"));
  }

  @Test
  void deserialize_viaConvertValue_fromMap() {
    // Useful pattern for tests/bridges that build inputs as plain Maps/Lists.
    Map<String, Object> source = Map.of("a", 1, "b", List.of("x", "y"));
    RegoObject obj = mapper.convertValue(source, RegoObject.class);
    assertThat(obj.getProperty(new RegoString("a"))).isInstanceOf(RegoBigInt.class);
    assertThat(((RegoArray) obj.getProperty(new RegoString("b"))).getValues())
        .containsExactly(new RegoString("x"), new RegoString("y"));
  }
}
