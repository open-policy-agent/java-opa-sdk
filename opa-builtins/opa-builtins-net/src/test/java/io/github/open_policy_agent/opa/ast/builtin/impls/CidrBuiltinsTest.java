package io.github.open_policy_agent.opa.ast.builtin.impls;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.open_policy_agent.opa.ast.builtin.BuiltinError;
import io.github.open_policy_agent.opa.ast.builtin.BuiltinProvider;
import io.github.open_policy_agent.opa.ast.types.RegoArray;
import io.github.open_policy_agent.opa.ast.types.RegoBoolean;
import io.github.open_policy_agent.opa.ast.types.RegoSet;
import io.github.open_policy_agent.opa.ast.types.RegoString;
import io.github.open_policy_agent.opa.ast.types.RegoValue;
import io.github.open_policy_agent.opa.rego.EvaluationContext;
import java.util.Arrays;
import java.util.List;
import java.util.ServiceLoader;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class CidrBuiltinsTest {

  private final CidrBuiltins builtins = new CidrBuiltins();
  private final EvaluationContext ctx = new EvaluationContext.Builder().build();

  /**
   * The provider entry in META-INF/services was commented out, so none of these builtins were
   * reachable for consumers even though the module was documented as supported.
   */
  @Test
  void providerIsDiscoverableViaServiceLoader() {
    boolean found =
        ServiceLoader.load(BuiltinProvider.class, CidrBuiltins.class.getClassLoader()).stream()
            .anyMatch(p -> p.type().equals(CidrBuiltins.class));

    assertTrue(found, "CidrBuiltins should be registered as a BuiltinProvider");
  }

  @Test
  void registersAllNetBuiltins() {
    assertTrue(
        builtins
            .builtins()
            .keySet()
            .containsAll(
                List.of(
                    "net.cidr_contains",
                    "net.cidr_contains_matches",
                    "net.cidr_expand",
                    "net.cidr_intersects",
                    "net.cidr_is_valid",
                    "net.cidr_merge",
                    "net.lookup_ip_addr")));
  }

  // Go's net.ParseCIDR requires a prefix, so a bare address is not valid CIDR notation.
  @Test
  void cidrIsValidRequiresAPrefix() {
    assertEquals(RegoBoolean.TRUE, isValid("192.168.1.0/24"));
    assertEquals(RegoBoolean.TRUE, isValid("2002::1234:abcd:ffff:c0a8:101/64"));
    assertEquals(RegoBoolean.FALSE, isValid("192.168.1.2"));
    assertEquals(RegoBoolean.FALSE, isValid(""));
    assertEquals(RegoBoolean.FALSE, isValid("there goes a string"));
  }

  // A bare IPv4 address takes Go's classful DefaultMask, not a /32.
  @Test
  void mergeAppliesClassfulDefaultMaskToBareIpv4() {
    assertEquals(List.of("192.0.128.0/23"), merge("192.0.128.0", "192.0.129.0"));
    assertEquals(
        List.of("192.0.2.0/24"), merge("192.0.2.112", "192.0.2.116/31", "192.0.2.118/31"));
  }

  @Test
  void mergeCollapsesOverlappingIpv6Prefixes() {
    assertEquals(
        List.of("2601:600:8a80:207e::/64"),
        merge(
            "2601:600:8a80:207e:a57d:7567:e2c9:e7b3/64",
            "2601:600:8a80:207e:a57d:7567:e2c9:e7b3/128"));
  }

  // mergeToPrefixBlocks cannot mix versions, so the two families are merged independently.
  @Test
  void mergeHandlesMixedIpv4AndIpv6() {
    assertEquals(
        List.of("192.0.2.0/23", "192.0.4.0/24", "fe80::/120"),
        merge("fe80::/120", "192.0.2.0/24", "192.0.3.0/24", "192.0.4.0/25", "192.0.4.128/25"));
  }

  @Test
  void mergeRejectsBareIpv6() {
    BuiltinError e =
        assertThrows(
            BuiltinError.class, () -> merge("2601:600:8a80:207e:a57d:7567:e2c9:e7b3"));

    // The evaluator prepends the builtin name, so the message must not repeat it.
    assertEquals("eval_builtin_error: IPv6 invalid: needs prefix length", e.getMessage());
  }

  @Test
  void mergeRejectsMalformedInput() {
    for (String bad : List.of("not-an-address", "999.1.1.1", "192.168.1.1/33")) {
      BuiltinError e = assertThrows(BuiltinError.class, () -> merge(bad), bad);
      assertTrue(e.getMessage().contains(bad), e.getMessage());
    }
  }

  // The parser accepts "" and resolves it to the loopback address unless allowEmpty(false) is
  // set, which made net.cidr_contains("", "127.0.0.1") answer true. Go's parsers reject it.
  @Test
  void emptyStringIsNotAnAddress() {
    assertEquals(RegoBoolean.FALSE, isValid(""));
    assertThrows(BuiltinError.class, () -> merge(""));
    assertThrows(
        BuiltinError.class,
        () -> builtins.expand(ctx, new RegoValue[] {new RegoString("")}));
    assertThrows(
        BuiltinError.class,
        () -> builtins.contains(ctx, new RegoValue[] {new RegoString(""), new RegoString("127.0.0.1")}));
    assertThrows(
        BuiltinError.class,
        () ->
            builtins.intersects(
                ctx, new RegoValue[] {new RegoString(""), new RegoString("127.0.0.0/8")}));
  }

  @Test
  void expandReportsGoStyleMessageForInvalidMask() {
    BuiltinError e =
        assertThrows(
            BuiltinError.class,
            () -> builtins.expand(ctx, new RegoValue[] {new RegoString("192.168.1.1/33")}));

    assertTrue(
        e.getMessage().contains("net.cidr_expand: invalid CIDR address: 192.168.1.1/33"),
        e.getMessage());
  }

  @Test
  void containsMatchesUsesColonSeparatedOperandMessage() {
    RegoValue[] args = {
      new RegoArray(List.of(new RegoString("1.1.1.0/24"))),
      new RegoArray(List.of(RegoBoolean.TRUE))
    };

    BuiltinError e = assertThrows(BuiltinError.class, () -> builtins.containsMatches(ctx, args));

    assertTrue(
        e.getMessage()
            .contains("net.cidr_contains_matches: operand 2: element must be string or non-empty array"),
        e.getMessage());
  }

  private RegoValue isValid(String cidr) {
    return builtins.isValid(ctx, new RegoValue[] {new RegoString(cidr)});
  }

  private List<String> merge(String... addrs) {
    RegoArray input =
        new RegoArray(Arrays.stream(addrs).map(RegoString::new).collect(Collectors.toList()));
    RegoSet result = (RegoSet) builtins.merge(ctx, new RegoValue[] {input});
    return result.getValue().stream()
        .map(v -> ((RegoString) v).getValue())
        .sorted()
        .collect(Collectors.toList());
  }
}
