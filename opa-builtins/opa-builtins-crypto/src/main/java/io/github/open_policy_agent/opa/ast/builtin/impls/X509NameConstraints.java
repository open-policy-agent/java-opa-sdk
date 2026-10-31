// This file contains a Java translation of code from the Go project:
//   crypto/x509 (constraints.go), Copyright 2025 The Go Authors.
//   crypto/x509 (parser.go), Copyright 2021 The Go Authors.
// That code is used under the BSD-style license reproduced in
// src/main/resources/META-INF/licenses/LICENSE-golang.txt (Copyright 2009 The Go Authors).
// The translation and the rest of this file are licensed under the Apache License 2.0.
package io.github.open_policy_agent.opa.ast.builtin.impls;

import io.github.open_policy_agent.opa.ast.builtin.BuiltinError;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;

/**
 * Parses the name constraints extension (RFC 5280, 4.2.1.10) into a certificate, as Go's x509
 * {@code parseNameConstraintsExtension} does.
 */
final class X509NameConstraints {

  private X509NameConstraints() {}

  /** Parses the extension into {@code out}; returns whether it holds unhandled name types. */
  static boolean parse(X509Cert out, X509Cert.Extension e) {
    X509Der outer = new X509Der(e.value());
    X509Der toplevel = outer.read(X509Der.TAG_SEQUENCE);
    boolean[] havePermitted = new boolean[1];
    boolean[] haveExcluded = new boolean[1];
    X509Der permitted =
        toplevel == null || !outer.isEmpty()
            ? null
            : toplevel.readOptional(X509Der.contextSpecificConstructed(0), havePermitted);
    X509Der excluded =
        permitted == null ? null : toplevel.readOptional(X509Der.contextSpecificConstructed(1), haveExcluded);
    if (excluded == null || !toplevel.isEmpty()) {
      throw new BuiltinError("x509: invalid NameConstraints extension");
    }
    if (!havePermitted[0] && !haveExcluded[0] || permitted.length() == 0 && excluded.length() == 0) {
      throw new BuiltinError("x509: empty name constraints extension");
    }
    Subtrees p = new Subtrees();
    Subtrees x = new Subtrees();
    // Both permitted and excluded subtrees must be parsed, even if the first contains an unknown name type.
    boolean unhandledPermitted = p.read(permitted);
    boolean unhandledExcluded = x.read(excluded);
    boolean unhandled = unhandledPermitted || unhandledExcluded;
    out.permittedDnsDomains = p.dns;
    out.permittedIpRanges = p.ips;
    out.permittedEmailAddresses = p.emails;
    out.permittedUriDomains = p.uriDomains;
    out.excludedDnsDomains = x.dns;
    out.excludedIpRanges = x.ips;
    out.excludedEmailAddresses = x.emails;
    out.excludedUriDomains = x.uriDomains;
    out.permittedDnsDomainsCritical = e.critical();
    return unhandled;
  }

  /** The values of one GeneralSubtrees; lists stay null when empty, as in Go. */
  private static final class Subtrees {
    List<String> dns;
    List<X509Cert.IpNet> ips;
    List<String> emails;
    List<String> uriDomains;

    boolean read(X509Der subtrees) {
      boolean unhandled = false;
      int[] tag = new int[1];
      while (!subtrees.isEmpty()) {
        X509Der seq = subtrees.read(X509Der.TAG_SEQUENCE);
        byte[] v = X509Der.req(seq == null ? null : seq.readAnyContents(tag), "x509: invalid NameConstraints extension").bytes();
        String s = X509Strings.latin1(v);
        switch (tag[0]) {
          case 0x82 -> dns = X509Cert.add(dns, checked(v, "dnsName", X509Strings.domainNameValid(s, true)));
          case 0x87 -> ips = X509Cert.add(ips, parseIpConstraint(v));
          case 0x81 -> emails = X509Cert.add(emails, checked(v, "rfc822Name",
              s.indexOf('@') >= 0 ? X509Strings.parseMailbox(s) != null : X509Strings.domainNameValid(s, true)));
          case 0x86 -> {
            checkIa5(v, s);
            if (isIp(s)) {
              throw new BuiltinError("x509: failed to parse URI constraint " + X509Strings.quote(s) + ": cannot be IP address");
            }
            uriDomains = X509Cert.add(uriDomains, checked(v, "URI", X509Strings.domainNameValid(s, true)));
          }
          default -> unhandled = true;
        }
      }
      return unhandled;
    }
  }

  /** The constraint as a string, once it is known to be an IA5String and {@code valid}. */
  private static String checked(byte[] v, String type, boolean valid) {
    String s = X509Strings.latin1(v);
    checkIa5(v, s);
    if (!valid) {
      throw new BuiltinError("x509: failed to parse " + type + " constraint " + X509Strings.quote(s));
    }
    return s;
  }

  private static void checkIa5(byte[] v, String s) {
    if (!X509Strings.isIa5(v)) {
      throw new BuiltinError(
          "x509: invalid constraint value: x509: " + X509Strings.quote(s) + " cannot be encoded as an IA5String");
    }
  }

  private static X509Cert.IpNet parseIpConstraint(byte[] v) {
    if (v.length != 8 && v.length != 32) {
      throw new BuiltinError("x509: IP constraint contained value of length " + v.length);
    }
    int half = v.length / 2;
    byte[] ip = Arrays.copyOfRange(v, 0, half);
    byte[] mask = Arrays.copyOfRange(v, half, v.length);
    if (!isValidIpMask(mask)) {
      throw new BuiltinError("x509: IP constraint contained invalid mask " + HexFormat.of().formatHex(mask));
    }
    if (X509Strings.isIpv4Mapped(ip)) {
      throw new BuiltinError("x509: IP constraint contained IPv4-mapped IPv6 address");
    }
    return new X509Cert.IpNet(ip, mask);
  }

  private static boolean isValidIpMask(byte[] mask) {
    boolean seenZero = false;
    for (byte value : mask) {
      int b = value & 0xff;
      if (seenZero) {
        if (b != 0) {
          return false;
        }
        continue;
      }
      if (b == 0x00 || b == 0x80 || b == 0xc0 || b == 0xe0 || b == 0xf0 || b == 0xf8 || b == 0xfc || b == 0xfe) {
        seenZero = true;
      } else if (b != 0xff) {
        return false;
      }
    }
    return true;
  }

  /** Go's {@code net.ParseIP(s) != nil}: a valid IPv4 or IPv6 address without a zone. */
  private static boolean isIp(String s) {
    try {
      X509Strings.parseAddr(s, false);
      return true;
    } catch (IllegalArgumentException e) {
      return false;
    }
  }
}
