// This file contains a Java translation of code from the Go project:
//   net/url (url.go, gen_encoding_table.go), Copyright 2009 The Go Authors.
// That code is used under the BSD-style license reproduced in
// src/main/resources/META-INF/licenses/LICENSE-golang.txt (Copyright 2009 The Go Authors).
// The translation and the rest of this file are licensed under the Apache License 2.0.
package io.github.open_policy_agent.opa.ast.builtin.impls;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * A parsed URI from a certificate SAN, as Go's {@code net/url.Parse} produces it. Go's {@code
 * url.URL} is serialized field by field into the certificate's {@code URIs}, and {@link
 * #toString()} gives the {@code URIStrings} entries, so this is a direct port of the parts of {@code
 * net/url} (Go 1.27) that x509 uses.
 *
 * <p>Go strings are byte strings and percent-decoding can produce bytes that are not valid UTF-8,
 * so every string field here holds one byte per char (ISO-8859-1). Use {@link
 * X509Strings#fromLatin1Bytes(String)} to turn a field into the string Go's JSON encoder would emit.
 */
final class X509Url {

  // Escaping modes, as in net/url.
  private static final int ENCODE_PATH = 1;
  private static final int ENCODE_HOST = 3;
  private static final int ENCODE_ZONE = 4;
  private static final int ENCODE_USER_PASSWORD = 5;
  private static final int ENCODE_FRAGMENT = 7;

  String scheme = "";
  String opaque = "";
  /** Null when the URL has no userinfo; otherwise {username, password-or-null}. */
  String[] user;
  String host = "";
  String path = "";
  String fragment = "";
  String rawQuery = "";
  String rawPath = "";
  String rawFragment = "";
  boolean forceQuery;
  boolean omitHost;

  /** Thrown with the message Go's {@code url.Parse} would return. */
  static final class UrlException extends Exception {
    private static final long serialVersionUID = 1L;

    UrlException(String message) {
      super(message);
    }
  }

  /** Go's {@code url.Parse}. */
  static X509Url parse(String rawUrl) throws UrlException {
    int hash = rawUrl.indexOf('#');
    String u = hash < 0 ? rawUrl : rawUrl.substring(0, hash);
    String frag = hash < 0 ? "" : rawUrl.substring(hash + 1);
    X509Url url;
    try {
      url = parseWithoutFragment(u);
    } catch (UrlException e) {
      throw new UrlException("parse " + X509Strings.quote(u) + ": " + e.getMessage());
    }
    if (frag.isEmpty()) {
      return url;
    }
    try {
      url.fragment = unescape(frag, ENCODE_FRAGMENT);
    } catch (UrlException e) {
      throw new UrlException("parse " + X509Strings.quote(rawUrl) + ": " + e.getMessage());
    }
    url.rawFragment = frag.equals(escape(url.fragment, ENCODE_FRAGMENT)) ? "" : frag;
    return url;
  }

  private static X509Url parseWithoutFragment(String rawUrl) throws UrlException {
    for (int i = 0; i < rawUrl.length(); i++) {
      char c = rawUrl.charAt(i);
      if (c < ' ' || c == 0x7f) {
        throw new UrlException("net/url: invalid control character in URL");
      }
    }
    X509Url url = new X509Url();
    if ("*".equals(rawUrl)) {
      url.path = "*";
      return url;
    }
    String rest = rawUrl;
    int schemeEnd = schemeEnd(rawUrl);
    if (schemeEnd == 0) {
      throw new UrlException("missing protocol scheme");
    }
    if (schemeEnd > 0) {
      // The scheme is ASCII letters, digits, "+", "-" and ".", so a root-locale lowercase is ASCII-only.
      url.scheme = rawUrl.substring(0, schemeEnd).toLowerCase(Locale.ROOT);
      rest = rawUrl.substring(schemeEnd + 1);
    }

    if (rest.endsWith("?") && rest.indexOf('?') == rest.length() - 1) {
      url.forceQuery = true;
      rest = rest.substring(0, rest.length() - 1);
    } else {
      int q = rest.indexOf('?');
      if (q >= 0) {
        url.rawQuery = rest.substring(q + 1);
        rest = rest.substring(0, q);
      }
    }

    if (!rest.startsWith("/")) {
      if (!url.scheme.isEmpty()) {
        // Rootless paths are opaque per RFC 3986.
        url.opaque = rest;
        return url;
      }
      int slash = rest.indexOf('/');
      String segment = slash < 0 ? rest : rest.substring(0, slash);
      if (segment.indexOf(':') >= 0) {
        throw new UrlException("first path segment in URL cannot contain colon");
      }
    }

    if ((!url.scheme.isEmpty() || !rest.startsWith("///")) && rest.startsWith("//")) {
      String authority = rest.substring(2);
      rest = "";
      int slash = authority.indexOf('/');
      if (slash >= 0) {
        rest = authority.substring(slash);
        authority = authority.substring(0, slash);
      }
      parseAuthority(url, authority);
    } else if (!url.scheme.isEmpty() && rest.startsWith("/")) {
      url.omitHost = true;
    }

    url.path = unescape(rest, ENCODE_PATH);
    url.rawPath = rest.equals(escape(url.path, ENCODE_PATH)) ? "" : rest;
    return url;
  }

  /** Returns the index of the scheme's ':' (0 if a ':' starts the URL), or -1 if there is none. */
  private static int schemeEnd(String rawUrl) {
    int firstInvalid = rawUrl.length();
    for (int i = 0; i < rawUrl.length() && firstInvalid == rawUrl.length(); i++) {
      char c = rawUrl.charAt(i);
      if (c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z') {
        continue;
      }
      if (c >= '0' && c <= '9' || c == '+' || c == '-' || c == '.') {
        if (i == 0) {
          return -1;
        }
        continue;
      }
      firstInvalid = i;
    }
    return firstInvalid < rawUrl.length() && rawUrl.charAt(firstInvalid) == ':' ? firstInvalid : -1;
  }

  private static void parseAuthority(X509Url url, String authority) throws UrlException {
    int at = authority.lastIndexOf('@');
    url.host = parseHost(url.scheme, at < 0 ? authority : authority.substring(at + 1));
    if (at < 0) {
      return;
    }
    String userinfo = authority.substring(0, at);
    if (!validUserinfo(userinfo)) {
      throw new UrlException("net/url: invalid userinfo");
    }
    int colon = userinfo.indexOf(':');
    String name = unescape(colon < 0 ? userinfo : userinfo.substring(0, colon), ENCODE_USER_PASSWORD);
    url.user = new String[] {name, colon < 0 ? null : unescape(userinfo.substring(colon + 1), ENCODE_USER_PASSWORD)};
  }

  private static String parseHost(String scheme, String host) throws UrlException {
    int openBracket = host.lastIndexOf('[');
    if (openBracket > 0) {
      throw new UrlException("invalid IP-literal");
    }
    if (openBracket == 0) {
      int closeBracket = host.lastIndexOf(']');
      if (closeBracket < 0) {
        throw new UrlException("missing ']' in host");
      }
      String colonPort = host.substring(closeBracket + 1);
      if (!validOptionalPort(colonPort)) {
        throw new UrlException("invalid port " + X509Strings.quote(colonPort) + " after host");
      }
      String unescapedColonPort = unescape(colonPort, ENCODE_HOST);
      String hostname = host.substring(1, closeBracket);
      int zone = hostname.indexOf("%25");
      String unescapedHostname = zone < 0
          ? unescape(hostname, ENCODE_HOST)
          : unescape(hostname.substring(0, zone), ENCODE_HOST) + unescape(hostname.substring(zone), ENCODE_ZONE);
      byte[] addr;
      try {
        addr = X509Strings.parseAddr(unescapedHostname, true);
      } catch (IllegalArgumentException e) {
        throw new UrlException("invalid host: " + e.getMessage());
      }
      if (addr.length == 4) {
        throw new UrlException("invalid IP-literal");
      }
      return "[" + unescapedHostname + "]" + unescapedColonPort;
    }
    int colon = host.indexOf(':');
    if (colon >= 0) {
      int lastColon = host.lastIndexOf(':');
      // Go only keeps the first colon (and so rejects the port) for http and https.
      if (lastColon != colon && !"http".equals(scheme) && !"https".equals(scheme)) {
        colon = lastColon;
      }
      String colonPort = host.substring(colon);
      if (!validOptionalPort(colonPort)) {
        throw new UrlException("invalid port " + X509Strings.quote(colonPort) + " after host");
      }
    }
    return unescape(host, ENCODE_HOST);
  }

  private static boolean validOptionalPort(String port) {
    return port.isEmpty() || port.matches(":[0-9]*");
  }

  private static boolean validUserinfo(String s) {
    return s.matches("[A-Za-z0-9\\-._:~!$&'()*+,;=%@]*");
  }

  private static boolean isHex(int c) {
    return c >= '0' && c <= '9' || c >= 'a' && c <= 'f' || c >= 'A' && c <= 'F';
  }

  private static int unhex(int c) {
    return c <= '9' ? c - '0' : (c | 0x20) - 'a' + 10;
  }

  /** Go's url.unescape. The input is ASCII (SAN URIs are IA5Strings). */
  private static String unescape(String s, int mode) throws UrlException {
    int n = 0;
    int i = 0;
    while (i < s.length()) {
      char c = s.charAt(i);
      if (c == '%') {
        n++;
        if (i + 2 >= s.length() || !isHex(s.charAt(i + 1)) || !isHex(s.charAt(i + 2))) {
          String bad = s.substring(i, Math.min(s.length(), i + 3));
          throw new UrlException("invalid URL escape " + X509Strings.quote(bad));
        }
        if (mode == ENCODE_HOST && unhex(s.charAt(i + 1)) < 8 && !s.startsWith("%25", i)) {
          throw new UrlException("invalid URL escape " + X509Strings.quote(s.substring(i, i + 3)));
        }
        if (mode == ENCODE_ZONE) {
          int v = unhex(s.charAt(i + 1)) << 4 | unhex(s.charAt(i + 2));
          if (!s.startsWith("%25", i) && v != ' ' && shouldEscape(v, ENCODE_HOST)) {
            throw new UrlException("invalid URL escape " + X509Strings.quote(s.substring(i, i + 3)));
          }
        }
        i += 3;
      } else {
        if ((mode == ENCODE_HOST || mode == ENCODE_ZONE) && c < 0x80 && shouldEscape(c, mode)) {
          throw new UrlException("invalid character " + X509Strings.quote(String.valueOf(c)) + " in host name");
        }
        i++;
      }
    }
    if (n == 0) {
      return s;
    }
    byte[] out = new byte[s.length()];
    int len = 0;
    i = 0;
    while (i < s.length()) {
      char c = s.charAt(i);
      if (c == '%') {
        out[len++] = (byte) (unhex(s.charAt(i + 1)) << 4 | unhex(s.charAt(i + 2)));
        i += 3;
      } else {
        out[len++] = (byte) c;
        i++;
      }
    }
    return new String(out, 0, len, StandardCharsets.ISO_8859_1);
  }

  /** Go's url.shouldEscape (gen_encoding_table.go). */
  private static boolean shouldEscape(int c, int mode) {
    if (c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c >= '0' && c <= '9') {
      return false;
    }
    if (mode == ENCODE_HOST || mode == ENCODE_ZONE) {
      if ("!$&'()*+,;=:[]<>\"".indexOf(c) >= 0) {
        return false;
      }
    }
    if (c == '-' || c == '_' || c == '.' || c == '~') {
      return false;
    }
    if ("$&+,/:;=?@".indexOf(c) >= 0) {
      switch (mode) {
        case ENCODE_PATH:
          return c == '?';
        case ENCODE_USER_PASSWORD:
          return c == '@' || c == '/' || c == '?' || c == ':';
        case ENCODE_FRAGMENT:
          return false;
        default:
          break;
      }
    }
    if (mode == ENCODE_FRAGMENT && "!()*".indexOf(c) >= 0) {
      return false;
    }
    return true;
  }

  /** Go's url.escape, over a byte string. */
  private static String escape(String s, int mode) {
    StringBuilder sb = new StringBuilder(s.length());
    boolean changed = false;
    for (int i = 0; i < s.length(); i++) {
      int c = s.charAt(i) & 0xff;
      if (shouldEscape(c, mode)) {
        sb.append('%').append("0123456789ABCDEF".charAt(c >> 4)).append("0123456789ABCDEF".charAt(c & 15));
        changed = true;
      } else {
        sb.append((char) c);
      }
    }
    return changed ? sb.toString() : s;
  }

  private static boolean validEncoded(String s, int mode) {
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if ("!$&'()*+,;=:@[]%".indexOf(c) >= 0) {
        continue;
      }
      if (shouldEscape(c, mode)) {
        return false;
      }
    }
    return true;
  }

  /** Go's EscapedPath and EscapedFragment: the raw form if it is valid and decodes to the value. */
  private static String escaped(String raw, String value, int mode) {
    if (!raw.isEmpty() && validEncoded(raw, mode)) {
      try {
        if (unescape(raw, mode).equals(value)) {
          return raw;
        }
      } catch (UrlException e) {
        // Fall through to computing the escaping, as Go does.
      }
    }
    return mode == ENCODE_PATH && "*".equals(value) ? "*" : escape(value, mode);
  }

  /** Go's {@code url.URL.String}. */
  @Override
  public String toString() {
    StringBuilder buf = new StringBuilder();
    if (!scheme.isEmpty()) {
      buf.append(scheme).append(':');
    }
    if (!opaque.isEmpty()) {
      buf.append(opaque);
    } else {
      if (!scheme.isEmpty() || !host.isEmpty() || user != null) {
        if (!(omitHost && host.isEmpty() && user == null)) {
          if (!host.isEmpty() || !path.isEmpty() || user != null) {
            buf.append("//");
          }
          if (user != null) {
            buf.append(escape(user[0], ENCODE_USER_PASSWORD));
            if (user[1] != null) {
              buf.append(':').append(escape(user[1], ENCODE_USER_PASSWORD));
            }
            buf.append('@');
          }
          if (!host.isEmpty()) {
            buf.append(escape(host, ENCODE_HOST));
          }
        }
      }
      String p = escaped(rawPath, path, ENCODE_PATH);
      if (omitHost && host.isEmpty() && user == null && p.startsWith("//")) {
        buf.append("%2F");
        p = p.substring(1);
      }
      if (!p.isEmpty() && p.charAt(0) != '/' && !host.isEmpty()) {
        buf.append('/');
      }
      if (buf.length() == 0) {
        int slash = p.indexOf('/');
        String segment = slash < 0 ? p : p.substring(0, slash);
        if (segment.indexOf(':') >= 0) {
          buf.append("./");
        }
      }
      buf.append(p);
    }
    if (forceQuery || !rawQuery.isEmpty()) {
      buf.append('?').append(rawQuery);
    }
    if (!fragment.isEmpty()) {
      buf.append('#').append(escaped(rawFragment, fragment, ENCODE_FRAGMENT));
    }
    return buf.toString();
  }
}
