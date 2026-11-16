// This file contains a Java translation of code from the Go project:
//   crypto/x509 (constraints.go), Copyright 2025 The Go Authors.
// That code is used under the BSD-style license reproduced in
// src/main/resources/META-INF/licenses/LICENSE-golang.txt (Copyright 2009 The Go Authors).
// The translation and the rest of this file are licensed under the Apache License 2.0.
package io.github.open_policy_agent.opa.ast.builtin.impls;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.function.ToIntBiFunction;

/**
 * Checks that the names in a certificate chain satisfy the name constraints (RFC 5280, 4.2.1.10)
 * of the CAs above them, as Go 1.27's {@code constraints.go} does, including its sorted and
 * pruned constraint sets.
 */
final class X509ChainConstraints {

  private static final int[] OID_NAME_CONSTRAINTS = {2, 5, 29, 30};
  private static final int[] OID_SUBJECT_ALT_NAME = {2, 5, 29, 17};

  private X509ChainConstraints() {}

  /**
   * Go's {@code checkChainConstraints}: every SAN of each certificate must be permitted, and not
   * excluded, by the name constraints of every certificate above it in the chain.
   */
  static boolean chainSatisfiesConstraints(List<X509Cert> chain) {
    List<ChainConstraints> all = new ArrayList<>();
    for (int i = 0; i < chain.size(); i++) {
      X509Cert c = chain.get(i);
      if (c.extension(OID_NAME_CONSTRAINTS) != null) {
        all.add(new ChainConstraints(c, i));
      }
    }
    if (all.isEmpty()) {
      return true;
    }
    int current = 0;
    for (int i = 0; i < chain.size(); i++) {
      X509Cert c = chain.get(i);
      if (c.extension(OID_SUBJECT_ALT_NAME) == null) {
        continue;
      }
      while (all.get(current).index <= i) {
        if (current == all.size() - 1) {
          return true;
        }
        current++;
      }
      List<String> uriDomains = new ArrayList<>();
      if (c.uris != null) {
        for (X509Url uri : c.uris) {
          String domain = uriDomain(uri);
          if (domain == null) {
            return false;
          }
          uriDomains.add(domain);
        }
      }
      List<String[]> mailboxes = new ArrayList<>();
      if (c.emailAddresses != null) {
        for (String email : c.emailAddresses) {
          String[] mailbox = X509Strings.parseMailbox(email);
          if (mailbox == null) {
            return false;
          }
          mailbox[1] = lower(mailbox[1]);
          mailboxes.add(mailbox);
        }
      }
      for (int n = current; n < all.size(); n++) {
        if (!all.get(n).check(c, uriDomains, mailboxes)) {
          return false;
        }
      }
    }
    return true;
  }

  /** Go's parseURIs for one URI: the host to match, or null when it cannot be matched. */
  private static String uriDomain(X509Url uri) {
    String host = lower(uri.host);
    if (host.isEmpty()) {
      return null;
    }
    if (host.indexOf(':') >= 0 && !host.endsWith("]")) {
      host = splitHostPortHost(uri.host);
      if (host == null) {
        return null;
      }
    }
    boolean bracketed = host.startsWith("[") && host.endsWith("]");
    try {
      X509Strings.parseAddr(host, true);
      return null;
    } catch (IllegalArgumentException e) {
      return bracketed ? null : host;
    }
  }

  /** The host part of Go's net.SplitHostPort, or null when it reports an error. */
  private static String splitHostPortHost(String hostport) {
    int i = hostport.lastIndexOf(':');
    if (i < 0) {
      return null;
    }
    String host;
    int j = 0;
    int k = 0;
    if (hostport.charAt(0) == '[') {
      int end = hostport.indexOf(']');
      if (end < 0 || end + 1 == hostport.length() || end + 1 != i) {
        return null;
      }
      host = hostport.substring(1, end);
      j = 1;
      k = end + 1;
    } else {
      host = hostport.substring(0, i);
      if (host.indexOf(':') >= 0) {
        return null;
      }
    }
    if (hostport.indexOf('[', j) >= 0 || hostport.indexOf(']', k) >= 0) {
      return null;
    }
    return host;
  }

  private static String lower(String s) {
    return s.toLowerCase(Locale.ROOT);
  }

  /** The constraints of one CA certificate in the chain, at {@code index}. */
  private static final class ChainConstraints {
    final int index;
    final Predicate<byte[]> ipPermitted;
    final Predicate<byte[]> ipExcluded;
    final Predicate<String> dnsPermitted;
    final Predicate<String> dnsExcluded;
    final Predicate<String> uriPermitted;
    final Predicate<String> uriExcluded;
    final Predicate<String[]> emailPermitted;
    final Predicate<String[]> emailExcluded;

    ChainConstraints(X509Cert c, int index) {
      this.index = index;
      ipPermitted = ipConstraints(c.permittedIpRanges);
      ipExcluded = ipConstraints(c.excludedIpRanges);
      dnsPermitted = dnsConstraints(c.permittedDnsDomains, true);
      dnsExcluded = dnsConstraints(c.excludedDnsDomains, false);
      uriPermitted = dnsConstraints(c.permittedUriDomains, true);
      uriExcluded = dnsConstraints(c.excludedUriDomains, false);
      emailPermitted = emailConstraints(c.permittedEmailAddresses, true);
      emailExcluded = emailConstraints(c.excludedEmailAddresses, false);
    }

    boolean check(X509Cert c, List<String> uriDomains, List<String[]> mailboxes) {
      return allowed(c.ipAddresses, ip -> true, ipPermitted, ipExcluded)
          && allowed(c.dnsNames, dns -> X509Strings.domainNameValid(dns, false), dnsPermitted, dnsExcluded)
          && allowed(uriDomains, domain -> X509Strings.domainNameValid(domain, false), uriPermitted, uriExcluded)
          && allowed(mailboxes, mailbox -> X509Strings.domainNameValid(mailbox[1], false), emailPermitted, emailExcluded);
    }
  }

  /**
   * Whether every value is valid, permitted and not excluded; a null constraint query stands for
   * "no constraints of this type".
   */
  private static <V> boolean allowed(List<V> values, Predicate<V> valid, Predicate<V> permitted, Predicate<V> excluded) {
    if (values != null) {
      for (V value : values) {
        if (!valid.test(value) || permitted != null && !permitted.test(value) || excluded != null && excluded.test(value)) {
          return false;
        }
      }
    }
    return true;
  }

  /**
   * Go's nameConstraintsSet: constraints sorted with {@code cmp}, pruned of those contained in a
   * preceding one, and searched by finding the lower bound and testing it (or its predecessor).
   */
  private static final class SortedSet<T, V> {
    final List<T> set;

    SortedSet(List<T> constraints, Comparator<T> cmp, BiPredicate<T, T> subset) {
      List<T> s = new ArrayList<>(constraints);
      if (s.size() >= 2) {
        s.sort(cmp);
        List<T> pruned = new ArrayList<>();
        pruned.add(s.get(0));
        for (int i = 1; i < s.size(); i++) {
          if (!subset.test(pruned.get(pruned.size() - 1), s.get(i))) {
            pruned.add(s.get(i));
          }
        }
        s = pruned;
      }
      this.set = s;
    }

    boolean search(V value, ToIntBiFunction<T, V> cmp, BiPredicate<T, V> match) {
      if (set.isEmpty()) {
        return false;
      }
      int lo = 0;
      int hi = set.size();
      while (lo < hi) {
        int mid = (lo + hi) >>> 1;
        if (cmp.applyAsInt(set.get(mid), value) < 0) {
          lo = mid + 1;
        } else {
          hi = mid;
        }
      }
      if (lo < set.size() && cmp.applyAsInt(set.get(lo), value) == 0) {
        return true;
      }
      T constraint = lo == 0 ? set.get(0) : set.get(lo - 1);
      return match.test(constraint, value);
    }
  }

  // IP constraints -------------------------------------------------------------------------------

  private static Predicate<byte[]> ipConstraints(List<X509Cert.IpNet> list) {
    if (list == null) {
      return null;
    }
    List<X509Cert.IpNet> v4 = new ArrayList<>();
    List<X509Cert.IpNet> v6 = new ArrayList<>();
    for (X509Cert.IpNet n : list) {
      byte[] masked = new byte[n.ip().length];
      for (int i = 0; i < masked.length; i++) {
        masked[i] = (byte) (n.ip()[i] & n.mask()[i]);
      }
      X509Cert.IpNet net = new X509Cert.IpNet(masked, n.mask());
      (net.ip().length == 4 ? v4 : v6).add(net);
    }
    SortedSet<X509Cert.IpNet, byte[]> s4 = v4.isEmpty() ? null : new SortedSet<>(v4, X509ChainConstraints::ipCompare, X509ChainConstraints::ipSubset);
    SortedSet<X509Cert.IpNet, byte[]> s6 = v6.isEmpty() ? null : new SortedSet<>(v6, X509ChainConstraints::ipCompare, X509ChainConstraints::ipSubset);
    return ip -> {
      SortedSet<X509Cert.IpNet, byte[]> s = ip.length == 4 ? s4 : s6;
      return s != null && s.search(ip, (c, t) -> Arrays.compareUnsigned(c.ip(), t), X509ChainConstraints::ipContains);
    };
  }

  private static boolean ipContains(X509Cert.IpNet n, byte[] ip) {
    if (ip.length != n.ip().length) {
      return false;
    }
    for (int i = 0; i < ip.length; i++) {
      if ((n.ip()[i] & n.mask()[i]) != (ip[i] & n.mask()[i])) {
        return false;
      }
    }
    return true;
  }

  private static boolean ipSubset(X509Cert.IpNet a, X509Cert.IpNet b) {
    if (!ipContains(a, b.ip())) {
      return false;
    }
    byte[] broadcast = new byte[b.ip().length];
    for (int i = 0; i < broadcast.length; i++) {
      broadcast[i] = (byte) (b.ip()[i] | ~b.mask()[i]);
    }
    return ipContains(a, broadcast);
  }

  private static int ipCompare(X509Cert.IpNet a, X509Cert.IpNet b) {
    int c = Arrays.compareUnsigned(a.ip(), b.ip());
    return c != 0 ? c : Arrays.compareUnsigned(a.mask(), b.mask());
  }

  // DNS (and URI host) constraints -----------------------------------------------------------------

  private static Predicate<String> dnsConstraints(List<String> list, boolean permitted) {
    if (list == null) {
      return null;
    }
    for (String n : list) {
      if (n.isEmpty()) {
        return value -> true;
      }
    }
    SortedSet<String, String> set = new SortedSet<>(list, X509ChainConstraints::dnsCompare, X509ChainConstraints::dnsHasSuffix);
    Map<String, String> parents = new HashMap<>();
    if (!permitted) {
      for (String name : set.set) {
        String trimmed = trimFirstLabel(lower(name));
        if (!trimmed.isEmpty()) {
          parents.put(trimmed, name);
        }
      }
    }
    return s -> set.search(s, X509ChainConstraints::dnsCompare, X509ChainConstraints::dnsHasSuffix)
        || !permitted && !s.isEmpty() && s.charAt(0) == '*' && parents.containsKey(trimFirstLabel(lower(s)));
  }

  private static String trimFirstLabel(String name) {
    int dot = name.indexOf('.');
    return dot < 0 ? "" : name.substring(dot);
  }

  /**
   * Go's dnsHasSuffix: whether name {@code b} is {@code a} or a subdomain of it (case-insensitive);
   * a leading '.' in {@code a} requires at least one extra label.
   */
  private static boolean dnsHasSuffix(String a, String b) {
    int lenA = a.length();
    int lenB = b.length();
    if (lenA > lenB) {
      return false;
    }
    int offset = lenA - lenB;
    boolean matched = true;
    for (int i = lenA - 1; i >= 0 && matched; i--) {
      char ar = a.charAt(i);
      char br = b.charAt(i - offset);
      if (ar == br) {
        continue;
      }
      if (br < ar) {
        char t = ar;
        ar = br;
        br = t;
      }
      if (ar >= 'A' && ar <= 'Z' && br == ar + 'a' - 'A') {
        continue;
      }
      matched = false;
    }
    return matched && (a.charAt(0) == '.' || lenB <= lenA || b.charAt(lenB - lenA - 1) == '.');
  }

  /** Go's dnsCompare: compares names from their last character, case-insensitively, '.' lowest. */
  private static int dnsCompare(String a, String b) {
    int ia = a.length() - 1;
    int ib = b.length() - 1;
    while (ia >= 0 && ib >= 0) {
      int ca = dnsCompareKey(a.charAt(ia));
      int cb = dnsCompareKey(b.charAt(ib));
      if (ca != cb) {
        return ca < cb ? -1 : 1;
      }
      ia--;
      ib--;
    }
    return Integer.compare(ia, ib);
  }

  private static int dnsCompareKey(char c) {
    return c == '.' ? 0 : c >= 'A' && c <= 'Z' ? c + 'a' - 'A' : c & 0xff;
  }

  // Email constraints ----------------------------------------------------------------------------

  private static Predicate<String[]> emailConstraints(List<String> list, boolean permitted) {
    if (list == null) {
      return null;
    }
    Set<String> exact = new HashSet<>();
    List<String> domains = new ArrayList<>();
    for (String c : list) {
      if (c.indexOf('@') < 0) {
        domains.add(c);
        continue;
      }
      String[] mailbox = X509Strings.parseMailbox(c);
      if (mailbox != null) {
        exact.add(mailbox[0] + "@" + lower(mailbox[1]));
      }
    }
    Predicate<String> dns = domains.isEmpty() ? null : dnsConstraints(domains, permitted);
    return mailbox -> exact.contains(mailbox[0] + "@" + mailbox[1]) || dns != null && dns.test(mailbox[1]);
  }
}
