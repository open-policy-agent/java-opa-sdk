package io.github.open_policy_agent.opa.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import java.math.BigDecimal;
import java.math.BigInteger;
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
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class RegoMapperTest {

  private RegoMapper mapper;

  @BeforeEach
  void setUp() {
    mapper = new RegoMapper();
  }

  // ---- Test POJOs ----

  public static class SimplePojo {
    private String name;
    private int age;
    private boolean active;

    SimplePojo() {}

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public int getAge() { return age; }
    public void setAge(int age) { this.age = age; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
  }

  public static class Address {
    private String street;
    private String city;

    Address() {}

    public String getStreet() { return street; }
    public void setStreet(String street) { this.street = street; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
  }

  public static class NestedPojo {
    private String id;
    private Address address;
    private List<String> tags;

    NestedPojo() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public Address getAddress() { return address; }
    public void setAddress(Address address) { this.address = address; }
    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags; }
  }

  public static class AnnotatedPojo {
    @JsonProperty("user_name")
    private String userName;

    @JsonIgnore
    private String secret;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String optional;

    AnnotatedPojo() {}

    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }
    public String getSecret() { return secret; }
    public void setSecret(String secret) { this.secret = secret; }
    public String getOptional() { return optional; }
    public void setOptional(String optional) { this.optional = optional; }
  }

  public static class CollectionPojo {
    private List<Address> addresses;
    private Set<Integer> scores;
    private Map<String, String> metadata;

    CollectionPojo() {}

    public List<Address> getAddresses() { return addresses; }
    public void setAddresses(List<Address> addresses) { this.addresses = addresses; }
    public Set<Integer> getScores() { return scores; }
    public void setScores(Set<Integer> scores) { this.scores = scores; }
    public Map<String, String> getMetadata() { return metadata; }
    public void setMetadata(Map<String, String> metadata) { this.metadata = metadata; }
  }

  public static class NumberPojo {
    private long bigNumber;
    private double decimal;
    private float floatVal;
    private BigInteger huge;
    private BigDecimal precise;

    NumberPojo() {}

    public long getBigNumber() { return bigNumber; }
    public void setBigNumber(long bigNumber) { this.bigNumber = bigNumber; }
    public double getDecimal() { return decimal; }
    public void setDecimal(double decimal) { this.decimal = decimal; }
    public float getFloatVal() { return floatVal; }
    public void setFloatVal(float floatVal) { this.floatVal = floatVal; }
    public BigInteger getHuge() { return huge; }
    public void setHuge(BigInteger huge) { this.huge = huge; }
    public BigDecimal getPrecise() { return precise; }
    public void setPrecise(BigDecimal precise) { this.precise = precise; }
  }

  public enum Color { RED, GREEN, BLUE }

  public enum Status {
    ACTIVE, INACTIVE;

    @JsonValue
    public String toValue() {
      return name().toLowerCase();
    }
  }

  public static class EnumPojo {
    private Color color;
    private Status status;

    EnumPojo() {}

    public Color getColor() { return color; }
    public void setColor(Color color) { this.color = color; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
  }

  public static class ParentPojo {
    private String parentField;

    ParentPojo() {}

    public String getParentField() { return parentField; }
    public void setParentField(String parentField) { this.parentField = parentField; }
  }

  public static class ChildPojo extends ParentPojo {
    private String childField;

    ChildPojo() {}

    public String getChildField() { return childField; }
    public void setChildField(String childField) { this.childField = childField; }
  }

  // ---- Forward Conversion Tests ----

  @Nested
  class ForwardConversion {

    @Test
    void nullValue() {
      assertThat(mapper.toRegoValue(null)).isEqualTo(RegoNull.INSTANCE);
    }

    @Test
    void regoValuePassthrough() {
      RegoString str = new RegoString("hello");
      assertThat(mapper.toRegoValue(str)).isSameAs(str);
    }

    @Test
    void stringValue() {
      RegoValue result = mapper.toRegoValue("hello");
      assertThat(result).isInstanceOf(RegoString.class);
      assertThat(((RegoString) result).getValue()).isEqualTo("hello");
    }

    @Test
    void booleanValue() {
      assertThat(mapper.toRegoValue(true)).isSameAs(RegoBoolean.TRUE);
      assertThat(mapper.toRegoValue(false)).isSameAs(RegoBoolean.FALSE);
    }

    @Test
    void integerValue() {
      RegoValue result = mapper.toRegoValue(42);
      assertThat(result).isInstanceOf(RegoInt32.class);
      assertThat(((RegoInt32) result).getValue()).isEqualTo(42);
    }

    @Test
    void integerFlyweight() {
      // Values 0-20 should use cached instances
      assertThat(mapper.toRegoValue(5)).isSameAs(RegoInt32.of(5));
    }

    @Test
    void longValue() {
      RegoValue result = mapper.toRegoValue(123456789L);
      assertThat(result).isInstanceOf(RegoBigInt.class);
      assertThat(((RegoBigInt) result).getValue()).isEqualTo(BigInteger.valueOf(123456789L));
    }

    @Test
    void doubleValue() {
      RegoValue result = mapper.toRegoValue(3.14);
      assertThat(result).isInstanceOf(RegoDecimal.class);
      assertThat(((RegoDecimal) result).getValue()).isEqualTo(3.14);
    }

    @Test
    void floatValue() {
      RegoValue result = mapper.toRegoValue(2.5f);
      assertThat(result).isInstanceOf(RegoDecimal.class);
      assertThat(((RegoDecimal) result).getValue()).isEqualTo(2.5);
    }

    @Test
    void bigIntegerValue() {
      BigInteger big = new BigInteger("99999999999999999999");
      RegoValue result = mapper.toRegoValue(big);
      assertThat(result).isInstanceOf(RegoBigInt.class);
      assertThat(((RegoBigInt) result).getValue()).isEqualTo(big);
    }

    @Test
    void bigDecimalValue() {
      RegoValue result = mapper.toRegoValue(new BigDecimal("1.23"));
      assertThat(result).isInstanceOf(RegoDecimal.class);
      assertThat(((RegoDecimal) result).getValue()).isEqualTo(1.23);
    }

    @Test
    void simpleEnum() {
      RegoValue result = mapper.toRegoValue(Color.RED);
      assertThat(result).isInstanceOf(RegoString.class);
      assertThat(((RegoString) result).getValue()).isEqualTo("RED");
    }

    @Test
    void enumWithJsonValue() {
      RegoValue result = mapper.toRegoValue(Status.ACTIVE);
      assertThat(result).isInstanceOf(RegoString.class);
      assertThat(((RegoString) result).getValue()).isEqualTo("active");
    }

    @Test
    void listValue() {
      List<String> list = List.of("a", "b", "c");
      RegoValue result = mapper.toRegoValue(list);
      assertThat(result).isInstanceOf(RegoArray.class);
      RegoArray arr = (RegoArray) result;
      assertThat(arr.length()).isEqualTo(3);
      assertThat(arr.getValue().get(0)).isEqualTo(new RegoString("a"));
    }

    @Test
    void setValue() {
      Set<Integer> set = new LinkedHashSet<>(List.of(1, 2, 3));
      RegoValue result = mapper.toRegoValue(set);
      assertThat(result).isInstanceOf(RegoSet.class);
      RegoSet regoSet = (RegoSet) result;
      assertThat(regoSet.length()).isEqualTo(3);
    }

    @Test
    void mapValue() {
      Map<String, Object> map = new LinkedHashMap<>();
      map.put("key", "value");
      map.put("num", 42);
      RegoValue result = mapper.toRegoValue(map);
      assertThat(result).isInstanceOf(RegoObject.class);
      RegoObject obj = (RegoObject) result;
      assertThat(obj.getProperty("key")).isEqualTo(new RegoString("value"));
      assertThat(obj.getProperty("num")).isEqualTo(RegoInt32.of(42));
    }

    @Test
    void objectArray() {
      String[] arr = {"x", "y"};
      RegoValue result = mapper.toRegoValue(arr);
      assertThat(result).isInstanceOf(RegoArray.class);
      assertThat(((RegoArray) result).length()).isEqualTo(2);
    }

    @Test
    void primitiveArray() {
      int[] arr = {10, 20, 30};
      RegoValue result = mapper.toRegoValue(arr);
      assertThat(result).isInstanceOf(RegoArray.class);
      RegoArray regoArr = (RegoArray) result;
      assertThat(regoArr.length()).isEqualTo(3);
      assertThat(regoArr.getValue().get(1)).isEqualTo(RegoInt32.of(20));
    }

    @Test
    void simplePojo() {
      SimplePojo pojo = new SimplePojo();
      pojo.setName("alice");
      pojo.setAge(30);
      pojo.setActive(true);

      RegoObject result = mapper.toRegoObject(pojo);
      assertThat(result.getProperty("name")).isEqualTo(new RegoString("alice"));
      assertThat(result.getProperty("age")).isEqualTo(RegoInt32.of(30));
      assertThat(result.getProperty("active")).isEqualTo(RegoBoolean.TRUE);
    }

    @Test
    void nestedPojo() {
      Address addr = new Address();
      addr.setStreet("123 Main St");
      addr.setCity("Springfield");

      NestedPojo pojo = new NestedPojo();
      pojo.setId("n1");
      pojo.setAddress(addr);
      pojo.setTags(List.of("admin", "user"));

      RegoObject result = mapper.toRegoObject(pojo);
      assertThat(result.getProperty("id")).isEqualTo(new RegoString("n1"));

      RegoObject addrObj = (RegoObject) result.getProperty("address");
      assertThat(addrObj.getProperty("street")).isEqualTo(new RegoString("123 Main St"));

      RegoArray tags = (RegoArray) result.getProperty("tags");
      assertThat(tags.length()).isEqualTo(2);
    }

    @Test
    void jsonPropertyAnnotation() {
      AnnotatedPojo pojo = new AnnotatedPojo();
      pojo.setUserName("bob");
      pojo.setSecret("s3cret");
      pojo.setOptional("present");

      RegoObject result = mapper.toRegoObject(pojo);
      // @JsonProperty("user_name") renames the key
      assertThat(result.getProperty("user_name")).isEqualTo(new RegoString("bob"));
      // @JsonIgnore skips the property
      assertThat(result.getProperty("secret")).isNull();
      // optional is present
      assertThat(result.getProperty("optional")).isEqualTo(new RegoString("present"));
    }

    @Test
    void jsonIgnoreAnnotation() {
      AnnotatedPojo pojo = new AnnotatedPojo();
      pojo.setSecret("hidden");

      RegoObject result = mapper.toRegoObject(pojo);
      assertThat(result.getProperty("secret")).isNull();
    }

    @Test
    void jsonIncludeNonNull_skipsNull() {
      AnnotatedPojo pojo = new AnnotatedPojo();
      pojo.setUserName("test");
      pojo.setOptional(null);

      RegoObject result = mapper.toRegoObject(pojo);
      // @JsonInclude(NON_NULL) should skip null optional
      assertThat(result.getProperty("optional")).isNull();
    }

    @Test
    void jsonIncludeNonNull_includesWhenPresent() {
      AnnotatedPojo pojo = new AnnotatedPojo();
      pojo.setOptional("here");

      RegoObject result = mapper.toRegoObject(pojo);
      assertThat(result.getProperty("optional")).isEqualTo(new RegoString("here"));
    }

    @Test
    void inheritedProperties() {
      ChildPojo child = new ChildPojo();
      child.setParentField("parent");
      child.setChildField("child");

      RegoObject result = mapper.toRegoObject(child);
      assertThat(result.getProperty("parentField")).isEqualTo(new RegoString("parent"));
      assertThat(result.getProperty("childField")).isEqualTo(new RegoString("child"));
    }

    // ---- Field-based discovery forward conversion tests ----

    @Test
    void publicFieldPojo() {
      PublicFieldPojo pojo = new PublicFieldPojo();
      pojo.name = "alice";
      pojo.count = 5;

      RegoObject result = mapper.toRegoObject(pojo);
      assertThat(result.getProperty("name")).isEqualTo(new RegoString("alice"));
      assertThat(result.getProperty("count")).isEqualTo(RegoInt32.of(5));
    }

    @Test
    void annotatedFieldPojo_noGetters() {
      AnnotatedFieldPojo pojo = new AnnotatedFieldPojo("bob", 30);

      RegoObject result = mapper.toRegoObject(pojo);
      assertThat(result.getProperty("user_name")).isEqualTo(new RegoString("bob"));
      assertThat(result.getProperty("age")).isEqualTo(RegoInt32.of(30));
    }

    @Test
    void creatorNoGetters_forwardConversion() {
      CreatorNoGetters pojo = new CreatorNoGetters("x1", List.of("a", "b"));

      RegoObject result = mapper.toRegoObject(pojo);
      assertThat(result.getProperty("id")).isEqualTo(new RegoString("x1"));
      RegoArray items = (RegoArray) result.getProperty("items");
      assertThat(items.length()).isEqualTo(2);
    }

    @Test
    void mixedAccessPojo_bothFieldAndGetterDiscovered() {
      MixedAccessPojo pojo = new MixedAccessPojo();
      pojo.fieldOnly = "from-field";
      pojo.setGetterOnly("from-getter");

      RegoObject result = mapper.toRegoObject(pojo);
      assertThat(result.getProperty("field_only")).isEqualTo(new RegoString("from-field"));
      assertThat(result.getProperty("getterOnly")).isEqualTo(new RegoString("from-getter"));
    }

    @Test
    void computedFieldPojo_allFieldsDiscovered() {
      ComputedFieldPojo pojo =
          new ComputedFieldPojo("/api/v1/docs", Map.of("env", "prod"), "extra-context");

      RegoObject result = mapper.toRegoObject(pojo);

      // facts — computed by constructor
      RegoObject facts = (RegoObject) result.getProperty("facts");
      assertThat(facts).isNotNull();
      assertThat(facts.getProperty("source")).isEqualTo(new RegoString("computed"));

      // parsed_path — @JsonProperty renames from parsedPath
      RegoArray parsedPath = (RegoArray) result.getProperty("parsed_path");
      assertThat(parsedPath).isNotNull();
      assertThat(parsedPath.length()).isEqualTo(3);
      assertThat(parsedPath.getValue().get(0)).isEqualTo(new RegoString("api"));
      assertThat(parsedPath.getValue().get(1)).isEqualTo(new RegoString("v1"));
      assertThat(parsedPath.getValue().get(2)).isEqualTo(new RegoString("docs"));

      // properties — passed through
      RegoObject props = (RegoObject) result.getProperty("properties");
      assertThat(props.getProperty("env")).isEqualTo(new RegoString("prod"));

      // context — arbitrary object serialized as string
      assertThat(result.getProperty("context")).isEqualTo(new RegoString("extra-context"));
    }

    @Test
    void computedFieldPojo_nullableFieldSerializedAsNull() {
      ComputedFieldPojo pojo = new ComputedFieldPojo("/path", Map.of(), null);

      RegoObject result = mapper.toRegoObject(pojo);

      // context is null — should be serialized as RegoNull
      assertThat(result.getProperty("context")).isEqualTo(RegoNull.INSTANCE);
    }

    @Test
    void jdkType_serializedAsString() {
      // Types like URI have no discoverable getters/fields — should fall back to toString()
      java.net.URI uri = java.net.URI.create("https://example.com/path?q=1");
      RegoValue result = mapper.toRegoValue(uri);
      assertThat(result).isInstanceOf(RegoString.class);
      assertThat(((RegoString) result).getValue()).isEqualTo("https://example.com/path?q=1");
    }

    @Test
    void mapWithJdkValues_serializedCorrectly() {
      // Simulates OpaInput's Map<String, Object> containing URI values
      Map<String, Object> map = new LinkedHashMap<>();
      map.put("url", java.net.URI.create("https://example.com"));
      map.put("name", "test");

      RegoValue result = mapper.toRegoValue(map);
      assertThat(result).isInstanceOf(RegoObject.class);
      RegoObject obj = (RegoObject) result;
      assertThat(obj.getProperty("url")).isEqualTo(new RegoString("https://example.com"));
      assertThat(obj.getProperty("name")).isEqualTo(new RegoString("test"));
    }

    // ---- Property naming tests (Jackson legacy mangling compatibility) ----

    @Test
    void consecutiveUppercase_getURL_lowercasesFullRun() {
      ConsecutiveUppercasePojo pojo = new ConsecutiveUppercasePojo();
      pojo.setURL("https://example.com");

      RegoObject result = mapper.toRegoObject(pojo);
      // Jackson legacy: getURL() → "url" (not "uRL")
      assertThat(result.getProperty("url")).isEqualTo(new RegoString("https://example.com"));
    }

    @Test
    void consecutiveUppercase_isCKIdentityValid_lowercasesFullRun() {
      ConsecutiveUppercasePojo pojo = new ConsecutiveUppercasePojo();
      pojo.setCKIdentityValid(true);

      RegoObject result = mapper.toRegoObject(pojo);
      // Jackson legacy: isCKIdentityValid() → "ckidentityValid" (not "cKIdentityValid")
      assertThat(result.getProperty("ckidentityValid")).isEqualTo(RegoBoolean.TRUE);
    }

    @Test
    void consecutiveUppercase_getHTTPHeader() {
      ConsecutiveUppercasePojo pojo = new ConsecutiveUppercasePojo();
      pojo.setHTTPHeader("text/html");

      RegoObject result = mapper.toRegoObject(pojo);
      // Jackson legacy: getHTTPHeader() → "httpheader" (lowercases H,T,T,P,H then 'e' stops)
      assertThat(result.getProperty("httpheader")).isEqualTo(new RegoString("text/html"));
    }

    @Test
    void jsonAutoDetect_allFieldsDiscovered() {
      AutoDetectPojo pojo = new AutoDetectPojo(true, false, "alice");

      RegoObject result = mapper.toRegoObject(pojo);
      // Getter-derived properties
      assertThat(result.getProperty("trusted")).isEqualTo(RegoBoolean.TRUE);
      assertThat(result.getProperty("name")).isEqualTo(new RegoString("alice"));
      // Field-derived property (ckIdentityValid field, not covered by getter name "ckidentityValid")
      assertThat(result.getProperty("ckIdentityValid")).isEqualTo(RegoBoolean.FALSE);
      // @JsonIgnore field should not appear
      assertThat(result.getProperty("secret")).isNull();
    }

    @Test
    void jsonAutoDetect_booleanIsFieldDeduplicated() {
      AutoDetectPojo pojo = new AutoDetectPojo(true, false, "bob");

      RegoObject result = mapper.toRegoObject(pojo);
      // isTrusted() getter claims the isTrusted field — only "trusted" should appear
      assertThat(result.getProperty("trusted")).isEqualTo(RegoBoolean.TRUE);
      assertThat(result.getProperty("isTrusted")).isNull();
    }

    @Test
    void toRegoObject_throwsForPrimitive() {
      assertThatThrownBy(() -> mapper.toRegoObject("hello"))
          .isInstanceOf(RegoMappingException.class);
    }

    @Test
    void toRegoObject_throwsForNull() {
      assertThatThrownBy(() -> mapper.toRegoObject(null))
          .isInstanceOf(RegoMappingException.class);
    }
  }

  // ---- Reverse Conversion Tests ----

  @Nested
  class ReverseConversion {

    @Test
    void nullFromRegoNull() {
      assertThat(mapper.fromRegoValue(RegoNull.INSTANCE, String.class)).isNull();
      assertThat(mapper.fromRegoValue(null, String.class)).isNull();
    }

    @Test
    void stringFromRegoString() {
      String result = mapper.fromRegoValue(new RegoString("hello"), String.class);
      assertThat(result).isEqualTo("hello");
    }

    @Test
    void booleanFromRegoBoolean() {
      boolean result = mapper.fromRegoValue(RegoBoolean.TRUE, boolean.class);
      assertThat(result).isTrue();

      Boolean boxed = mapper.fromRegoValue(RegoBoolean.FALSE, Boolean.class);
      assertThat(boxed).isFalse();
    }

    @Test
    void intFromRegoInt32() {
      int result = mapper.fromRegoValue(RegoInt32.of(42), int.class);
      assertThat(result).isEqualTo(42);
    }

    @Test
    void intFromRegoBigInt() {
      int result = mapper.fromRegoValue(new RegoBigInt(100L), int.class);
      assertThat(result).isEqualTo(100);
    }

    @Test
    void longFromRegoBigInt() {
      long result = mapper.fromRegoValue(new RegoBigInt(9876543210L), long.class);
      assertThat(result).isEqualTo(9876543210L);
    }

    @Test
    void longFromRegoInt32() {
      long result = mapper.fromRegoValue(RegoInt32.of(10), long.class);
      assertThat(result).isEqualTo(10L);
    }

    @Test
    void doubleFromRegoDecimal() {
      double result = mapper.fromRegoValue(new RegoDecimal(3.14), double.class);
      assertThat(result).isEqualTo(3.14);
    }

    @Test
    void floatFromRegoDecimal() {
      float result = mapper.fromRegoValue(new RegoDecimal(2.5), float.class);
      assertThat(result).isEqualTo(2.5f);
    }

    @Test
    void bigIntegerFromRegoBigInt() {
      BigInteger big = new BigInteger("99999999999999999999");
      BigInteger result = mapper.fromRegoValue(new RegoBigInt(big), BigInteger.class);
      assertThat(result).isEqualTo(big);
    }

    @Test
    void bigDecimalFromRegoDecimal() {
      BigDecimal result = mapper.fromRegoValue(new RegoDecimal(1.23), BigDecimal.class);
      assertThat(result.doubleValue()).isEqualTo(1.23);
    }

    @Test
    void enumFromRegoString() {
      Color result = mapper.fromRegoValue(new RegoString("GREEN"), Color.class);
      assertThat(result).isEqualTo(Color.GREEN);
    }

    @Test
    void enumFromRegoString_invalid() {
      assertThatThrownBy(() -> mapper.fromRegoValue(new RegoString("PINK"), Color.class))
          .isInstanceOf(RegoMappingException.class);
    }

    @Test
    void simplePojo() {
      RegoObject obj = new RegoObject();
      obj.setProp(new RegoString("name"), new RegoString("alice"));
      obj.setProp(new RegoString("age"), RegoInt32.of(30));
      obj.setProp(new RegoString("active"), RegoBoolean.TRUE);

      SimplePojo result = mapper.fromRegoValue(obj, SimplePojo.class);
      assertThat(result.getName()).isEqualTo("alice");
      assertThat(result.getAge()).isEqualTo(30);
      assertThat(result.isActive()).isTrue();
    }

    @Test
    void nestedPojo() {
      RegoObject addrObj = new RegoObject();
      addrObj.setProp(new RegoString("street"), new RegoString("123 Main St"));
      addrObj.setProp(new RegoString("city"), new RegoString("Springfield"));

      RegoArray tags = new RegoArray();
      tags.addValue(new RegoString("admin"));
      tags.addValue(new RegoString("user"));

      RegoObject obj = new RegoObject();
      obj.setProp(new RegoString("id"), new RegoString("n1"));
      obj.setProp(new RegoString("address"), addrObj);
      obj.setProp(new RegoString("tags"), tags);

      NestedPojo result = mapper.fromRegoValue(obj, NestedPojo.class);
      assertThat(result.getId()).isEqualTo("n1");
      assertThat(result.getAddress().getStreet()).isEqualTo("123 Main St");
      assertThat(result.getAddress().getCity()).isEqualTo("Springfield");
      assertThat(result.getTags()).containsExactly("admin", "user");
    }

    @Test
    void annotatedPojo_jsonPropertyRename() {
      RegoObject obj = new RegoObject();
      obj.setProp(new RegoString("user_name"), new RegoString("bob"));

      AnnotatedPojo result = mapper.fromRegoValue(obj, AnnotatedPojo.class);
      assertThat(result.getUserName()).isEqualTo("bob");
    }

    @Test
    void missingProperties_useDefaults() {
      RegoObject obj = new RegoObject();
      obj.setProp(new RegoString("name"), new RegoString("alice"));
      // age and active are missing

      SimplePojo result = mapper.fromRegoValue(obj, SimplePojo.class);
      assertThat(result.getName()).isEqualTo("alice");
      assertThat(result.getAge()).isEqualTo(0); // int default
      assertThat(result.isActive()).isFalse(); // boolean default
    }

    @Test
    void mapFromRegoObject() {
      RegoObject obj = new RegoObject();
      obj.setProp(new RegoString("k1"), new RegoString("v1"));
      obj.setProp(new RegoString("k2"), new RegoString("v2"));

      @SuppressWarnings("unchecked")
      Map<String, Object> result = mapper.fromRegoValue(obj, Map.class);
      assertThat(result).containsEntry("k1", "v1").containsEntry("k2", "v2");
    }

    @Test
    void stringArrayFromRegoArray() {
      RegoArray arr = new RegoArray();
      arr.addValue(new RegoString("a"));
      arr.addValue(new RegoString("b"));

      String[] result = mapper.fromRegoValue(arr, String[].class);
      assertThat(result).containsExactly("a", "b");
    }

    @Test
    void intArrayFromRegoArray() {
      RegoArray arr = new RegoArray();
      arr.addValue(RegoInt32.of(10));
      arr.addValue(RegoInt32.of(20));

      int[] result = mapper.fromRegoValue(arr, int[].class);
      assertThat(result).containsExactly(10, 20);
    }

    @Test
    void regoValuePassthrough() {
      RegoString str = new RegoString("test");
      RegoValue result = mapper.fromRegoValue(str, RegoValue.class);
      assertThat(result).isSameAs(str);
    }

    @Test
    void noArgConstructorMissing() {
      assertThatThrownBy(() -> {
        RegoObject obj = new RegoObject();
        mapper.fromRegoValue(obj, NoDefaultCtorPojo.class);
      }).isInstanceOf(RegoMappingException.class)
          .hasMessageContaining("no accessible no-arg constructor or @JsonCreator");
    }

    @Test
    void typeMismatch() {
      assertThatThrownBy(
          () -> mapper.fromRegoValue(new RegoString("hello"), boolean.class))
          .isInstanceOf(RegoMappingException.class);
    }

    @Test
    void inheritedProperties() {
      RegoObject obj = new RegoObject();
      obj.setProp(new RegoString("parentField"), new RegoString("parent"));
      obj.setProp(new RegoString("childField"), new RegoString("child"));

      ChildPojo result = mapper.fromRegoValue(obj, ChildPojo.class);
      assertThat(result.getParentField()).isEqualTo("parent");
      assertThat(result.getChildField()).isEqualTo("child");
    }

    // ---- @JsonCreator reverse conversion tests ----

    @Test
    void jsonCreator_immutablePojo() {
      RegoObject obj = new RegoObject();
      obj.setProp(new RegoString("name"), new RegoString("alice"));
      obj.setProp(new RegoString("count"), RegoInt32.of(5));

      ImmutablePojo result = mapper.fromRegoValue(obj, ImmutablePojo.class);
      assertThat(result.getName()).isEqualTo("alice");
      assertThat(result.getCount()).isEqualTo(5);
    }

    @Test
    void jsonCreator_missingPrimitiveParam_usesDefault() {
      RegoObject obj = new RegoObject();
      obj.setProp(new RegoString("name"), new RegoString("bob"));
      // "count" is missing — should default to 0

      ImmutablePojo result = mapper.fromRegoValue(obj, ImmutablePojo.class);
      assertThat(result.getName()).isEqualTo("bob");
      assertThat(result.getCount()).isEqualTo(0);
    }

    @Test
    void jsonCreator_missingReferenceParam_usesNull() {
      RegoObject obj = new RegoObject();
      obj.setProp(new RegoString("count"), RegoInt32.of(3));
      // "name" is missing — should default to null

      ImmutablePojo result = mapper.fromRegoValue(obj, ImmutablePojo.class);
      assertThat(result.getName()).isNull();
      assertThat(result.getCount()).isEqualTo(3);
    }

    @Test
    void jsonCreator_hybridPojo_constructorAndSetter() {
      RegoObject obj = new RegoObject();
      obj.setProp(new RegoString("id"), new RegoString("h1"));
      obj.setProp(new RegoString("label"), new RegoString("my-label"));

      HybridPojo result = mapper.fromRegoValue(obj, HybridPojo.class);
      assertThat(result.getId()).isEqualTo("h1");
      assertThat(result.getLabel()).isEqualTo("my-label");
    }

    @Test
    void jsonCreator_withCollections() {
      RegoArray roles = new RegoArray();
      roles.addValue(new RegoString("admin"));
      roles.addValue(new RegoString("user"));

      RegoObject obj = new RegoObject();
      obj.setProp(new RegoString("name"), new RegoString("alice"));
      obj.setProp(new RegoString("roles"), roles);

      CreatorWithCollections result = mapper.fromRegoValue(obj, CreatorWithCollections.class);
      assertThat(result.getName()).isEqualTo("alice");
      assertThat(result.getRoles()).containsExactly("admin", "user");
    }

    @Test
    void jsonCreator_withNestedPojo() {
      RegoObject addrObj = new RegoObject();
      addrObj.setProp(new RegoString("street"), new RegoString("123 Main St"));
      addrObj.setProp(new RegoString("city"), new RegoString("Springfield"));

      RegoObject obj = new RegoObject();
      obj.setProp(new RegoString("id"), new RegoString("n1"));
      obj.setProp(new RegoString("address"), addrObj);

      CreatorWithNestedPojo result = mapper.fromRegoValue(obj, CreatorWithNestedPojo.class);
      assertThat(result.getId()).isEqualTo("n1");
      assertThat(result.getAddress().getStreet()).isEqualTo("123 Main St");
      assertThat(result.getAddress().getCity()).isEqualTo("Springfield");
    }

    @Test
    void jsonCreator_staticFactoryMethod() {
      RegoObject obj = new RegoObject();
      obj.setProp(new RegoString("name"), new RegoString("factory"));
      obj.setProp(new RegoString("value"), RegoInt32.of(42));

      FactoryMethodPojo result = mapper.fromRegoValue(obj, FactoryMethodPojo.class);
      assertThat(result.getName()).isEqualTo("factory");
      assertThat(result.getValue()).isEqualTo(42);
    }

    @Test
    void jsonCreator_staticFactoryWithSetter() {
      RegoObject obj = new RegoObject();
      obj.setProp(new RegoString("id"), new RegoString("f1"));
      obj.setProp(new RegoString("description"), new RegoString("test desc"));

      FactoryWithSetterPojo result = mapper.fromRegoValue(obj, FactoryWithSetterPojo.class);
      assertThat(result.getId()).isEqualTo("f1");
      assertThat(result.getDescription()).isEqualTo("test desc");
    }

    // ---- Field-based discovery reverse conversion tests ----

    @Test
    void publicFieldPojo_reverse() {
      RegoObject obj = new RegoObject();
      obj.setProp(new RegoString("name"), new RegoString("alice"));
      obj.setProp(new RegoString("count"), RegoInt32.of(5));

      PublicFieldPojo result = mapper.fromRegoValue(obj, PublicFieldPojo.class);
      assertThat(result.name).isEqualTo("alice");
      assertThat(result.count).isEqualTo(5);
    }

    @Test
    void creatorNoGetters_reverse() {
      RegoObject obj = new RegoObject();
      obj.setProp(new RegoString("id"), new RegoString("x1"));
      RegoArray items = new RegoArray();
      items.addValue(new RegoString("a"));
      items.addValue(new RegoString("b"));
      obj.setProp(new RegoString("items"), items);

      CreatorNoGetters result = mapper.fromRegoValue(obj, CreatorNoGetters.class);
      assertThat(result.id).isEqualTo("x1");
      assertThat(result.items).containsExactly("a", "b");
    }

    @Test
    void mixedAccessPojo_reverse() {
      RegoObject obj = new RegoObject();
      obj.setProp(new RegoString("field_only"), new RegoString("from-field"));
      obj.setProp(new RegoString("getterOnly"), new RegoString("from-getter"));

      MixedAccessPojo result = mapper.fromRegoValue(obj, MixedAccessPojo.class);
      assertThat(result.fieldOnly).isEqualTo("from-field");
      assertThat(result.getGetterOnly()).isEqualTo("from-getter");
    }
  }

  // ---- Round-trip Tests ----

  @Nested
  class RoundTrip {

    @Test
    void simplePojo() {
      SimplePojo original = new SimplePojo();
      original.setName("alice");
      original.setAge(25);
      original.setActive(true);

      RegoValue regoValue = mapper.toRegoValue(original);
      SimplePojo roundTripped = mapper.fromRegoValue(regoValue, SimplePojo.class);

      assertThat(roundTripped.getName()).isEqualTo(original.getName());
      assertThat(roundTripped.getAge()).isEqualTo(original.getAge());
      assertThat(roundTripped.isActive()).isEqualTo(original.isActive());
    }

    @Test
    void nestedPojo() {
      Address addr = new Address();
      addr.setStreet("456 Oak Ave");
      addr.setCity("Portland");

      NestedPojo original = new NestedPojo();
      original.setId("rt1");
      original.setAddress(addr);
      original.setTags(List.of("read", "write"));

      RegoValue regoValue = mapper.toRegoValue(original);
      NestedPojo roundTripped = mapper.fromRegoValue(regoValue, NestedPojo.class);

      assertThat(roundTripped.getId()).isEqualTo("rt1");
      assertThat(roundTripped.getAddress().getStreet()).isEqualTo("456 Oak Ave");
      assertThat(roundTripped.getAddress().getCity()).isEqualTo("Portland");
      assertThat(roundTripped.getTags()).containsExactly("read", "write");
    }

    @Test
    void numberPojo() {
      NumberPojo original = new NumberPojo();
      original.setBigNumber(9876543210L);
      original.setDecimal(3.14);
      original.setFloatVal(2.5f);
      original.setHuge(new BigInteger("123456789012345678901234567890"));
      original.setPrecise(new BigDecimal("99.99"));

      RegoValue regoValue = mapper.toRegoValue(original);
      NumberPojo roundTripped = mapper.fromRegoValue(regoValue, NumberPojo.class);

      assertThat(roundTripped.getBigNumber()).isEqualTo(original.getBigNumber());
      assertThat(roundTripped.getDecimal()).isEqualTo(original.getDecimal());
      assertThat(roundTripped.getFloatVal()).isEqualTo(original.getFloatVal());
      assertThat(roundTripped.getHuge()).isEqualTo(original.getHuge());
      assertThat(roundTripped.getPrecise().doubleValue())
          .isEqualTo(original.getPrecise().doubleValue());
    }

    @Test
    void enumPojo() {
      EnumPojo original = new EnumPojo();
      original.setColor(Color.BLUE);

      RegoValue regoValue = mapper.toRegoValue(original);
      EnumPojo roundTripped = mapper.fromRegoValue(regoValue, EnumPojo.class);

      assertThat(roundTripped.getColor()).isEqualTo(Color.BLUE);
    }

    @Test
    void annotatedPojo() {
      AnnotatedPojo original = new AnnotatedPojo();
      original.setUserName("testuser");
      original.setSecret("hidden");
      original.setOptional("value");

      RegoValue regoValue = mapper.toRegoValue(original);
      AnnotatedPojo roundTripped = mapper.fromRegoValue(regoValue, AnnotatedPojo.class);

      assertThat(roundTripped.getUserName()).isEqualTo("testuser");
      assertThat(roundTripped.getSecret()).isNull(); // @JsonIgnore — not serialized
      assertThat(roundTripped.getOptional()).isEqualTo("value");
    }

    @Test
    void mapRoundTrip() {
      Map<String, Object> original = new LinkedHashMap<>();
      original.put("name", "test");
      original.put("count", 42);
      original.put("active", true);

      RegoValue regoValue = mapper.toRegoValue(original);

      @SuppressWarnings("unchecked")
      Map<String, Object> roundTripped = mapper.fromRegoValue(regoValue, Map.class);

      assertThat(roundTripped.get("name")).isEqualTo("test");
      assertThat(roundTripped.get("active")).isEqualTo(true);
    }

    @Test
    void immutablePojoRoundTrip() {
      ImmutablePojo original = new ImmutablePojo("alice", 10);

      RegoValue regoValue = mapper.toRegoValue(original);
      ImmutablePojo roundTripped = mapper.fromRegoValue(regoValue, ImmutablePojo.class);

      assertThat(roundTripped.getName()).isEqualTo("alice");
      assertThat(roundTripped.getCount()).isEqualTo(10);
    }

    @Test
    void factoryMethodPojoRoundTrip() {
      FactoryMethodPojo original = FactoryMethodPojo.create("factory", 99);

      RegoValue regoValue = mapper.toRegoValue(original);
      FactoryMethodPojo roundTripped = mapper.fromRegoValue(regoValue, FactoryMethodPojo.class);

      assertThat(roundTripped.getName()).isEqualTo("factory");
      assertThat(roundTripped.getValue()).isEqualTo(99);
    }

    @Test
    void publicFieldPojoRoundTrip() {
      PublicFieldPojo original = new PublicFieldPojo();
      original.name = "field-test";
      original.count = 42;

      RegoValue regoValue = mapper.toRegoValue(original);
      PublicFieldPojo roundTripped = mapper.fromRegoValue(regoValue, PublicFieldPojo.class);

      assertThat(roundTripped.name).isEqualTo("field-test");
      assertThat(roundTripped.count).isEqualTo(42);
    }

    @Test
    void creatorNoGettersRoundTrip() {
      CreatorNoGetters original = new CreatorNoGetters("rt1", List.of("x", "y"));

      RegoValue regoValue = mapper.toRegoValue(original);
      CreatorNoGetters roundTripped = mapper.fromRegoValue(regoValue, CreatorNoGetters.class);

      assertThat(roundTripped.id).isEqualTo("rt1");
      assertThat(roundTripped.items).containsExactly("x", "y");
    }
  }

  // Helper class for error tests
  public static class NoDefaultCtorPojo {
    private final String value;
    NoDefaultCtorPojo(String value) { this.value = value; }
    public String getValue() { return value; }
  }

  // ---- @JsonCreator Test POJOs ----

  public static class ImmutablePojo {
    private final String name;
    private final int count;

    @JsonCreator
    ImmutablePojo(@JsonProperty("name") String name, @JsonProperty("count") int count) {
      this.name = name;
      this.count = count;
    }

    public String getName() {
      return name;
    }

    public int getCount() {
      return count;
    }
  }

  public static class HybridPojo {
    private final String id;
    private String label;

    @JsonCreator
    HybridPojo(@JsonProperty("id") String id) {
      this.id = id;
    }

    public String getId() {
      return id;
    }

    public String getLabel() {
      return label;
    }

    public void setLabel(String label) {
      this.label = label;
    }
  }

  public static class CreatorWithCollections {
    private final String name;
    private final List<String> roles;

    @JsonCreator
    CreatorWithCollections(
        @JsonProperty("name") String name, @JsonProperty("roles") List<String> roles) {
      this.name = name;
      this.roles = roles;
    }

    public String getName() {
      return name;
    }

    public List<String> getRoles() {
      return roles;
    }
  }

  public static class CreatorWithNestedPojo {
    private final String id;
    private final Address address;

    @JsonCreator
    CreatorWithNestedPojo(
        @JsonProperty("id") String id, @JsonProperty("address") Address address) {
      this.id = id;
      this.address = address;
    }

    public String getId() {
      return id;
    }

    public Address getAddress() {
      return address;
    }
  }

  public static class FactoryMethodPojo {
    private final String name;
    private final int value;

    private FactoryMethodPojo(String name, int value) {
      this.name = name;
      this.value = value;
    }

    @JsonCreator
    public static FactoryMethodPojo create(
        @JsonProperty("name") String name, @JsonProperty("value") int value) {
      return new FactoryMethodPojo(name, value);
    }

    public String getName() {
      return name;
    }

    public int getValue() {
      return value;
    }
  }

  public static class FactoryWithSetterPojo {
    private final String id;
    private String description;

    private FactoryWithSetterPojo(String id) {
      this.id = id;
    }

    @JsonCreator
    public static FactoryWithSetterPojo of(@JsonProperty("id") String id) {
      return new FactoryWithSetterPojo(id);
    }

    public String getId() {
      return id;
    }

    public String getDescription() {
      return description;
    }

    public void setDescription(String description) {
      this.description = description;
    }
  }

  // ---- Field-based discovery Test POJOs ----

  /** Public fields, no getters — like a simple data carrier. */
  public static class PublicFieldPojo {
    public String name;
    public int count;
  }

  /** @JsonProperty on private fields, no getters — common with @JsonCreator. */
  public static class AnnotatedFieldPojo {
    @JsonProperty("user_name")
    private final String userName;

    @JsonProperty("age")
    private final int age;

    @JsonCreator
    AnnotatedFieldPojo(
        @JsonProperty("user_name") String userName, @JsonProperty("age") int age) {
      this.userName = userName;
      this.age = age;
    }
    // No getters — relies on field access for serialization
  }

  /** @JsonCreator with @JsonProperty fields but no standard getters — only field accessors. */
  public static class CreatorNoGetters {
    @JsonProperty("id")
    private final String id;

    @JsonProperty("items")
    private final List<String> items;

    @JsonCreator
    CreatorNoGetters(
        @JsonProperty("id") String id, @JsonProperty("items") List<String> items) {
      this.id = id;
      this.items = items;
    }
  }

  /** Mix of getter-discovered and field-discovered properties. */
  public static class MixedAccessPojo {
    @JsonProperty("field_only")
    public String fieldOnly;

    private String getterOnly;

    MixedAccessPojo() {}

    public String getGetterOnly() {
      return getterOnly;
    }

    public void setGetterOnly(String getterOnly) {
      this.getterOnly = getterOnly;
    }
  }

  /**
   * Mimics a real-world input class: private final fields with @JsonProperty, no getters, and a
   * non-@JsonCreator constructor that computes field values from different-typed inputs. This is the
   * exact pattern used by OpaInput in downstream services.
   */
  static class ComputedFieldPojo {
    @JsonProperty("facts")
    private final Map<String, Object> facts;

    @JsonProperty("parsed_path")
    private final List<String> parsedPath;

    @JsonProperty("properties")
    private final Map<String, Object> properties;

    @JsonProperty("context")
    private final Object context;

    ComputedFieldPojo(String path, Map<String, Object> properties, Object context) {
      // Constructor computes fields from different-typed inputs — NOT a @JsonCreator
      this.facts = Map.of("source", "computed");
      this.parsedPath =
          Arrays.stream(path.split("/")).filter(s -> !s.isEmpty()).collect(Collectors.toList());
      this.properties = properties;
      this.context = context;
    }
  }

  // ---- Property naming and @JsonAutoDetect test POJOs ----

  /** POJO with consecutive uppercase getters to test Jackson-compatible name mangling. */
  public static class ConsecutiveUppercasePojo {
    private String url;
    private boolean ckIdentityValid;
    private String httpHeader;

    ConsecutiveUppercasePojo() {}

    public String getURL() {
      return url;
    }

    public void setURL(String url) {
      this.url = url;
    }

    public boolean isCKIdentityValid() {
      return ckIdentityValid;
    }

    public void setCKIdentityValid(boolean v) {
      this.ckIdentityValid = v;
    }

    public String getHTTPHeader() {
      return httpHeader;
    }

    public void setHTTPHeader(String h) {
      this.httpHeader = h;
    }
  }

  /** Mimics DSIdentityFacts: @JsonAutoDetect(fieldVisibility=ANY), boolean is-prefixed fields. */
  @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
  public static class AutoDetectPojo {
    @JsonIgnore private final String secret = "hidden";
    private final boolean isTrusted;
    private final boolean ckIdentityValid;
    private final String name;

    AutoDetectPojo(boolean isTrusted, boolean ckIdentityValid, String name) {
      this.isTrusted = isTrusted;
      this.ckIdentityValid = ckIdentityValid;
      this.name = name;
    }

    public boolean isTrusted() {
      return isTrusted;
    }

    public boolean isCKIdentityValid() {
      return ckIdentityValid;
    }

    public String getName() {
      return name;
    }
  }
}
