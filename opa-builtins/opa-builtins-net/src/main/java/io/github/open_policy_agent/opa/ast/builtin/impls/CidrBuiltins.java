package io.github.open_policy_agent.opa.ast.builtin.impls;

import inet.ipaddr.AddressStringException;
import inet.ipaddr.IPAddress;
import inet.ipaddr.IPAddressString;
import inet.ipaddr.ipv4.IPv4Address;
import inet.ipaddr.ipv6.IPv6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import io.github.open_policy_agent.opa.ast.types.RegoArray;
import io.github.open_policy_agent.opa.ast.types.RegoBoolean;
import io.github.open_policy_agent.opa.ast.types.RegoInt32;
import io.github.open_policy_agent.opa.ast.types.RegoObject;
import io.github.open_policy_agent.opa.ast.types.RegoSet;
import io.github.open_policy_agent.opa.ast.types.RegoString;
import io.github.open_policy_agent.opa.ast.types.RegoValue;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Comparator;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import io.github.open_policy_agent.opa.ast.builtin.BuiltinError;
import io.github.open_policy_agent.opa.ast.builtin.BuiltinProvider;
import io.github.open_policy_agent.opa.ast.builtin.OpaBuiltin;
import io.github.open_policy_agent.opa.ast.builtin.OpaDynamic;
import io.github.open_policy_agent.opa.ast.builtin.OpaType;
import io.github.open_policy_agent.opa.rego.EvaluationContext;

import static io.github.open_policy_agent.opa.ast.builtin.impls.utils.ArgHelper.getArg;

public class CidrBuiltins implements BuiltinProvider {

  @Override
  public Map<String, BiFunction<EvaluationContext, RegoValue[], RegoValue>> builtins() {
    CidrBuiltins instance = new CidrBuiltins();
    // @formatter:off
    return Map.of(
        "net.cidr_contains", instance::contains,
        "net.cidr_contains_matches", instance::containsMatches,
        "net.cidr_expand", instance::expand,
        "net.cidr_intersects", instance::intersects,
        "net.cidr_is_valid", instance::isValid,
        "net.cidr_merge", instance::merge,
        "net.lookup_ip_addr", instance::lookupIpAddr);
    // @formatter:on
  }

  /** Parse an IP address or CIDR from a string. */
  private IPAddress parseAddress(String str) throws AddressStringException {
    return new IPAddressString(str).toAddress();
  }

  /** Parse an IP address or CIDR, throwing a BuiltinError with the builtin name on failure. */
  private IPAddress parseAddressOrThrow(String str, String builtinName)
      throws AddressStringException {
    IPAddressString addrStr = new IPAddressString(str);
    if (!addrStr.isValid()) {
      throw new BuiltinError(
          builtinName + ": not a valid textual representation of an IP address or CIDR: " + str);
    }
    return addrStr.toAddress();
  }

  @OpaBuiltin(
      name = "net.cidr_contains",
      description = "Checks if a CIDR or IP address is contained within another CIDR",
      categories = {"network"},
      args = {
        @OpaType(type = "string", name = "cidr", description = "CIDR to check against"),
        @OpaType(type = "string", name = "cidr_or_ip", description = "CIDR or IP to check")
      },
      result =
          @OpaType(
              type = "boolean",
              name = "result",
              description = "`true` if `cidr_or_ip` is contained within `cidr`"))
  public RegoValue contains(EvaluationContext ctx, RegoValue[] args) {
    try {
      String cidrStr = getArg(args, 0, RegoString.class).getValue();
      String cidrOrIpStr = getArg(args, 1, RegoString.class).getValue();

      // Parse the first CIDR
      IPAddress cidrAddr = parseAddress(cidrStr).toPrefixBlock();

      // Try parsing as a plain IP first (no CIDR notation)
      // In Go, net.ParseIP only accepts plain IPs without /prefix
      if (!cidrOrIpStr.contains("/")) {
        IPAddress ip = parseAddressOrThrow(cidrOrIpStr, "net.cidr_contains");
        return RegoBoolean.of(cidrAddr.contains(ip));
      }

      // It has CIDR notation, parse as CIDR
      IPAddress checkAddr = parseAddressOrThrow(cidrOrIpStr, "net.cidr_contains").toPrefixBlock();

      // Check if cidrAddr contains both the start and end of checkAddr's range
      boolean containsStart = cidrAddr.contains(checkAddr.getLower());
      if (!containsStart) {
        return RegoBoolean.FALSE;
      }

      return RegoBoolean.of(cidrAddr.contains(checkAddr.getUpper()));

    } catch (AddressStringException e) {
      throw new BuiltinError("net.cidr_contains: " + e.getMessage());
    }
  }

  @OpaBuiltin(
      name = "net.cidr_intersects",
      description = "Checks if two CIDRs intersect",
      categories = {"network"},
      args = {
        @OpaType(type = "string", name = "cidr1", description = "first CIDR"),
        @OpaType(type = "string", name = "cidr2", description = "second CIDR")
      },
      result =
          @OpaType(
              type = "boolean",
              name = "result",
              description = "`true` if `cidr1` intersects with `cidr2`"))
  public RegoValue intersects(EvaluationContext ctx, RegoValue[] args) {
    try {
      String cidr1Str = getArg(args, 0, RegoString.class).getValue();
      String cidr2Str = getArg(args, 1, RegoString.class).getValue();

      IPAddress addr1 = parseAddress(cidr1Str);
      IPAddress addr2 = parseAddress(cidr2Str);

      // Two CIDRs intersect if either contains the other's starting IP
      boolean intersects = addr1.contains(addr2.getLower()) || addr2.contains(addr1.getLower());

      return RegoBoolean.of(intersects);
    } catch (AddressStringException e) {
      throw new BuiltinError("net.cidr_intersects: " + e.getMessage());
    }
  }

  @OpaBuiltin(
      name = "net.cidr_contains_matches",
      description =
          "Returns matches for all pairs from two collections where the first contains the second",
      categories = {"network"},
      args = {
        @OpaType(
            type = "any",
            name = "cidrs",
            description = "CIDRs to check against",
            dynamic = @OpaDynamic(type = "any")),
        @OpaType(
            type = "any",
            name = "cidrs",
            description = "CIDRs to check",
            dynamic = @OpaDynamic(type = "any"))
      },
      result =
          @OpaType(
              type = "set",
              name = "matches",
              description =
                  "set of [index1, index2] arrays where cidrs[index1] contains cidrs[index2]",
              dynamic = @OpaDynamic(type = "array")))
  public RegoValue containsMatches(EvaluationContext ctx, RegoValue[] args) {

    Set<RegoValue> resultSet = ctx.sortSets ? new LinkedHashSet<>() : new HashSet<>();

    // Process operand 1
    List<CidrMatch> cidrs1 = processCidrOperand(args[0], 1);
    List<CidrMatch> cidrs2 = processCidrOperand(args[1], 2);

    for (CidrMatch match1 : cidrs1) {
      for (CidrMatch match2 : cidrs2) {
        // Check if cidr1 contains cidr2
        RegoValue containsResult = contains(ctx, new RegoValue[] {match1.cidr, match2.cidr});
        if (containsResult instanceof RegoBoolean && ((RegoBoolean) containsResult).getValue()) {
          // Add [index1, index2] to result
          resultSet.add(new RegoArray(Arrays.asList(match1.index, match2.index)));
        }
      }
    }

    return new RegoSet(ctx.sortSets, resultSet);
  }

  private List<CidrMatch> processCidrOperand(RegoValue operand, int operandNum) {
    List<CidrMatch> result = new ArrayList<>();

    if (operand instanceof RegoString) {
      result.add(new CidrMatch((RegoString) operand, operand));
    } else if (operand instanceof RegoArray) {
      RegoArray arr = (RegoArray) operand;
      for (int i = 0; i < arr.getValue().size(); i++) {
        RegoValue elem = arr.getValue().get(i);
        RegoString cidr = getCidrFromElement(elem, operandNum);
        result.add(new CidrMatch(cidr, RegoInt32.of(i)));
      }
    } else if (operand instanceof RegoSet) {
      RegoSet set = (RegoSet) operand;
      for (RegoValue elem : set.getValue()) {
        RegoString cidr = getCidrFromElement(elem, operandNum);
        result.add(new CidrMatch(cidr, elem));
      }
    } else if (operand instanceof RegoObject) {
      RegoObject obj = (RegoObject) operand;
      for (Map.Entry<RegoValue, RegoValue> entry : obj.getProperties().entrySet()) {
        RegoString cidr = getCidrFromElement(entry.getValue(), operandNum);
        result.add(new CidrMatch(cidr, entry.getKey()));
      }
    }

    return result;
  }

  private RegoString getCidrFromElement(RegoValue elem, int operandNum) {
    if (elem instanceof RegoString) {
      return (RegoString) elem;
    } else if (elem instanceof RegoArray) {
      RegoArray arr = (RegoArray) elem;
      if (arr.getValue().isEmpty()) {
        throw new BuiltinError(
            "net.cidr_contains_matches: operand "
                + operandNum
                + " element must be string or non-empty array");
      }
      RegoValue first = arr.getValue().get(0);
      if (!(first instanceof RegoString)) {
        throw new BuiltinError(
            "net.cidr_contains_matches: operand "
                + operandNum
                + " element must be string or non-empty array");
      }
      return (RegoString) first;
    } else {
      throw new BuiltinError(
          "net.cidr_contains_matches: operand "
              + operandNum
              + " element must be string or non-empty array");
    }
  }

  @OpaBuiltin(
      name = "net.cidr_expand",
      description = "Expands a CIDR to the set of all IP addresses it contains",
      categories = {"network"},
      args = {@OpaType(type = "string", name = "cidr", description = "CIDR to expand")},
      result =
          @OpaType(
              type = "set",
              name = "hosts",
              description = "set of IP addresses the CIDR `cidr` expands to",
              dynamic = @OpaDynamic(type = "string")))
  public RegoValue expand(EvaluationContext ctx, RegoValue[] args) {
    try {
      String cidrStr = getArg(args, 0, RegoString.class).getValue();
      IPAddress addr = parseAddress(cidrStr);

      // Normalize to the network block (like Go's ip.Mask(ipNet.Mask))
      IPAddress network = addr.toPrefixBlock();

      Set<RegoValue> resultSet = new HashSet<>();

      // Iterate through all addresses in the CIDR range
      for (IPAddress ip : network.getIterable()) {
        // Remove prefix length to get just the IP address string
        resultSet.add(new RegoString(ip.withoutPrefixLength().toNormalizedString()));
      }

      return new RegoSet(ctx.sortSets, resultSet);
    } catch (AddressStringException e) {
      throw new BuiltinError("net.cidr_expand: " + e.getMessage());
    }
  }

  @OpaBuiltin(
      name = "net.cidr_is_valid",
      description = "Checks if a string is a valid CIDR notation",
      categories = {"network"},
      args = {@OpaType(type = "string", name = "cidr", description = "CIDR to validate")},
      result =
          @OpaType(
              type = "boolean",
              name = "result",
              description = "`true` if `cidr` is a valid CIDR"))
  public RegoValue isValid(EvaluationContext ctx, RegoValue[] args) {
    try {
      String cidrStr = getArg(args, 0, RegoString.class).getValue();
      IPAddressString addrStr = new IPAddressString(cidrStr);

      if (!addrStr.isValid()) {
        return RegoBoolean.FALSE;
      }

      // Must be a valid CIDR (has a prefix or is a complete address)
      addrStr.toAddress();
      return RegoBoolean.TRUE;

    } catch (Exception e) {
      return RegoBoolean.FALSE;
    }
  }

  @OpaBuiltin(
      name = "net.cidr_merge",
      description =
          "Merges IP addresses and subnets into the smallest possible list of CIDRs, removing duplicates and merging adjacent subnets",
      categories = {"network"},
      args = {
        @OpaType(
            type = "any",
            name = "addrs",
            description = "CIDRs or IP addresses",
            dynamic = @OpaDynamic(type = "any"))
      },
      result =
          @OpaType(
              type = "set",
              name = "output",
              description =
                  "smallest possible set of CIDRs obtained after merging the provided list of IP addresses and subnets in `addrs`",
              dynamic = @OpaDynamic(type = "string")))
  public RegoValue merge(EvaluationContext ctx, RegoValue[] args) {
    try {
      List<IPAddress> addresses =
          extractStringCollection(args[0], "net.cidr_merge").stream()
              .map(
                  str -> {
                    try {
                      return parseIPOrCidr(str);
                    } catch (AddressStringException e) {
                      throw new RuntimeException(e);
                    }
                  })
              .collect(Collectors.toList());

      if (addresses.isEmpty()) {
        return new RegoSet(ctx.sortSets, new HashSet<>());
      }

      // Merge the addresses using the IPAddress library's merge functionality
      List<IPAddress> sortedAddresses = new ArrayList<>(addresses);
      sortedAddresses.sort(Comparator.naturalOrder());

      // Use merging from the first address's type
      IPAddress[] merged =
          sortedAddresses.get(0).mergeToPrefixBlocks(sortedAddresses.toArray(new IPAddress[0]));

      Set<RegoValue> resultSet = new HashSet<>();
      for (IPAddress addr : merged) {
        resultSet.add(new RegoString(addr.toCanonicalString()));
      }

      return new RegoSet(ctx.sortSets, resultSet);

    } catch (RuntimeException e) {
      if (e.getCause() instanceof AddressStringException) {
        throw new BuiltinError("net.cidr_merge: " + e.getCause().getMessage());
      }
      throw e;
    }
  }

  /** Extract strings from an array or set collection. */
  private List<String> extractStringCollection(RegoValue input, String builtinName) {
    List<String> result = new ArrayList<>();

    if (input instanceof RegoArray) {
      for (RegoValue elem : ((RegoArray) input).getValue()) {
        if (!(elem instanceof RegoString)) {
          throw new BuiltinError(builtinName + ": element must be string");
        }
        result.add(((RegoString) elem).getValue());
      }
    } else if (input instanceof RegoSet) {
      for (RegoValue elem : ((RegoSet) input).getValue()) {
        if (!(elem instanceof RegoString)) {
          throw new BuiltinError(builtinName + ": element must be string");
        }
        result.add(((RegoString) elem).getValue());
      }
    } else {
      throw new BuiltinError(builtinName + ": operand must be an array or set");
    }

    return result;
  }

  private IPAddress parseIPOrCidr(String str) throws AddressStringException {
    IPAddressString addrStr = new IPAddressString(str);

    // Try to parse as CIDR first
    if (addrStr.isValid()) {
      return addrStr.toAddress();
    }

    // Try as plain IP
    addrStr = new IPAddressString(str);
    IPAddress addr = addrStr.toAddress();

    // If it's an IPv4 address without prefix, add default mask
    if (addr instanceof IPv4Address && addr.getNetworkPrefixLength() == null) {
      return addr.setPrefixLength(32, false);
    }

    // IPv6 addresses require a prefix length
    if (addr instanceof IPv6Address && addr.getNetworkPrefixLength() == null) {
      throw new AddressStringException(str, "IPv6 invalid: needs prefix length");
    }

    return addr;
  }

  @OpaBuiltin(
      name = "net.lookup_ip_addr",
      description = "Returns the set of IP addresses that a domain name resolves to",
      categories = {"network"},
      args = {@OpaType(type = "string", name = "name", description = "domain name to resolve")},
      result =
          @OpaType(
              type = "set",
              name = "addrs",
              description = "IP addresses (v4 and v6) that `name` resolves to",
              dynamic = @OpaDynamic(type = "string")))
  public RegoValue lookupIpAddr(EvaluationContext ctx, RegoValue[] args) {
    try {
      String hostname = getArg(args, 0, RegoString.class).getValue();

      // Check if it's already an IP address
      IPAddressString addrStr = new IPAddressString(hostname);
      if (addrStr.isIPAddress()) {
        // If it's an IP, just return it as-is
        Set<RegoValue> resultSet = new HashSet<>();
        resultSet.add(new RegoString(hostname));
        return new RegoSet(ctx.sortSets, resultSet);
      }

      // Otherwise, perform DNS lookup
      InetAddress[] addresses = InetAddress.getAllByName(hostname);
      Set<RegoValue> resultSet = new HashSet<>();

      for (InetAddress addr : addresses) {
        resultSet.add(new RegoString(addr.getHostAddress()));
      }

      return new RegoSet(ctx.sortSets, resultSet);

    } catch (UnknownHostException e) {
      throw new BuiltinError("net.lookup_ip_addr: " + e.getMessage());
    }
  }

  private static class CidrMatch {
    RegoString cidr;
    RegoValue index;

    CidrMatch(RegoString cidr, RegoValue index) {
      this.cidr = cidr;
      this.index = index;
    }
  }
}
