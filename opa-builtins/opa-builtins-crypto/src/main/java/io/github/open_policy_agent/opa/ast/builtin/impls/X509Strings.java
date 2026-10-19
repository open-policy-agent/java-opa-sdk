// This file contains a Java translation of code from the Go project:
//   net/netip (netip.go), Copyright 2020 The Go Authors.
//   strconv (quote.go), Copyright 2009 The Go Authors.
//   unicode/utf8 (utf8.go), Copyright 2009 The Go Authors.
//   crypto/x509 (parser.go, verify.go), Copyright 2011-2021 The Go Authors.
// That code is used under the BSD-style license reproduced in
// src/main/resources/META-INF/licenses/LICENSE-golang.txt (Copyright 2009 The Go Authors).
// The translation and the rest of this file are licensed under the Apache License 2.0.
package io.github.open_policy_agent.opa.ast.builtin.impls;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;

/**
 * String and address helpers that reproduce Go semantics needed by the crypto.x509 builtins: Go
 * strings are byte strings (held here as ISO-8859-1 Java strings, one byte per char), Go's JSON
 * encoder replaces invalid UTF-8, and several x509 checks are defined over bytes.
 */
final class X509Strings {

  /** The Unicode general categories of Go's unicode.IsPrint (letters, marks, numbers, punctuation, symbols). */
  private static final long PRINT_CATEGORIES =
      1L << Character.UPPERCASE_LETTER | 1L << Character.LOWERCASE_LETTER | 1L << Character.TITLECASE_LETTER
          | 1L << Character.MODIFIER_LETTER | 1L << Character.OTHER_LETTER | 1L << Character.NON_SPACING_MARK
          | 1L << Character.ENCLOSING_MARK | 1L << Character.COMBINING_SPACING_MARK | 1L << Character.DECIMAL_DIGIT_NUMBER
          | 1L << Character.LETTER_NUMBER | 1L << Character.OTHER_NUMBER | 1L << Character.CONNECTOR_PUNCTUATION
          | 1L << Character.DASH_PUNCTUATION | 1L << Character.START_PUNCTUATION | 1L << Character.END_PUNCTUATION
          | 1L << Character.INITIAL_QUOTE_PUNCTUATION | 1L << Character.FINAL_QUOTE_PUNCTUATION
          | 1L << Character.OTHER_PUNCTUATION | 1L << Character.MATH_SYMBOL | 1L << Character.CURRENCY_SYMBOL
          | 1L << Character.MODIFIER_SYMBOL | 1L << Character.OTHER_SYMBOL;

  private static final byte[] V4_IN_V6_PREFIX = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, (byte) 0xff, (byte) 0xff};

  private X509Strings() {}

  /** Converts Go string bytes to the string Go's JSON encoder emits (invalid bytes become U+FFFD). */
  static String goString(byte[] b) {
    StringBuilder sb = new StringBuilder(b.length);
    int i = 0;
    while (i < b.length) {
      int[] rune = decodeRune(b, i);
      sb.appendCodePoint(rune[0]);
      i += rune[1];
    }
    return sb.toString();
  }

  /** {@link #goString(byte[])} for a byte string held as ISO-8859-1. */
  static String fromLatin1Bytes(String latin1) {
    return goString(latin1.getBytes(StandardCharsets.ISO_8859_1));
  }

  static String latin1(byte[] b) {
    return new String(b, StandardCharsets.ISO_8859_1);
  }

  /**
   * Go's utf8.DecodeRune: returns {rune, size}; invalid input yields {U+FFFD, 1}.
   */
  private static int[] decodeRune(byte[] b, int i) {
    int x = b[i] & 0xff;
    if (x < 0x80) {
      return new int[] {x, 1};
    }
    int size;
    int lo = 0x80;
    int hi = 0xbf;
    if (x >= 0xc2 && x <= 0xdf) {
      size = 2;
    } else if (x >= 0xe0 && x <= 0xef) {
      size = 3;
      if (x == 0xe0) {
        lo = 0xa0;
      } else if (x == 0xed) {
        hi = 0x9f;
      }
    } else if (x >= 0xf0 && x <= 0xf4) {
      size = 4;
      if (x == 0xf0) {
        lo = 0x90;
      } else if (x == 0xf4) {
        hi = 0x8f;
      }
    } else {
      return new int[] {0xfffd, 1};
    }
    if (b.length - i < size) {
      return new int[] {0xfffd, 1};
    }
    int b1 = b[i + 1] & 0xff;
    if (b1 < lo || b1 > hi) {
      return new int[] {0xfffd, 1};
    }
    int r = (x & (0xff >> (size + 1))) << 6 | (b1 & 0x3f);
    for (int k = 2; k < size; k++) {
      int bk = b[i + k] & 0xff;
      if (bk < 0x80 || bk > 0xbf) {
        return new int[] {0xfffd, 1};
      }
      r = r << 6 | (bk & 0x3f);
    }
    return new int[] {r, size};
  }

  /** Go's strconv.Quote of a byte string held as ISO-8859-1. */
  static String quote(String latin1) {
    byte[] b = latin1.getBytes(StandardCharsets.ISO_8859_1);
    StringBuilder sb = new StringBuilder(b.length + 2).append('"');
    int i = 0;
    while (i < b.length) {
      int[] rune = decodeRune(b, i);
      int r = rune[0];
      int escape = "\u0007\b\f\n\r\t\u000b".indexOf(r);
      if (r == 0xfffd && rune[1] == 1) {
        sb.append(String.format(Locale.ROOT, "\\x%02x", b[i] & 0xff));
      } else if (r == '"' || r == '\\') {
        sb.append('\\').append((char) r);
      } else if (isPrint(r)) {
        sb.appendCodePoint(r);
      } else if (escape >= 0) {
        sb.append('\\').append("abfnrtv".charAt(escape));
      } else if (r < ' ' || r == 0x7f) {
        sb.append(String.format(Locale.ROOT, "\\x%02x", r));
      } else {
        sb.append(String.format(Locale.ROOT, r < 0x10000 ? "\\u%04x" : "\\U%08x", r));
      }
      i += rune[1];
    }
    return sb.append('"').toString();
  }

  /** Approximates Go's unicode.IsPrint: the categories above and ' '. */
  private static boolean isPrint(int r) {
    return r == ' ' || (PRINT_CATEGORIES >> Character.getType(r) & 1) != 0;
  }

  /** Go's x509 isIA5String: every byte must be ASCII. */
  static boolean isIa5(byte[] b) {
    for (byte c : b) {
      if (c < 0) {
        return false;
      }
    }
    return true;
  }

  /** Go's x509 domainNameValid (Go 1.27), over a byte string. */
  static boolean domainNameValid(String s, boolean constraint) {
    if (s.isEmpty()) {
      return true;
    }
    if (s.charAt(s.length() - 1) == '.') {
      return false;
    }
    String name = constraint && s.charAt(0) == '.' ? s.substring(1) : s;
    int lastDot = -1;
    for (int i = 0; i <= name.length(); i++) {
      if (i < name.length() && (name.charAt(i) < 33 || name.charAt(i) > 126)) {
        return false;
      }
      if (i == name.length() || name.charAt(i) == '.') {
        int labelLen = i;
        if (lastDot >= 0) {
          labelLen -= lastDot + 1;
        }
        if (labelLen == 0) {
          return false;
        }
        lastDot = i;
      }
    }
    return true;
  }

  /**
   * Go's x509 parseRFC2821Mailbox over a byte string. Returns {local, domain}, or null when the
   * address is not a valid RFC 2821 mailbox.
   */
  static String[] parseMailbox(String in) {
    if (in.isEmpty()) {
      return null;
    }
    StringBuilder local = new StringBuilder();
    int i = 0;
    if (in.charAt(0) == '"') {
      i = 1;
      while (true) {
        if (i >= in.length()) {
          return null;
        }
        int c = in.charAt(i++);
        if (c == '"') {
          break;
        }
        if (c == '\\') {
          if (i >= in.length()) {
            return null;
          }
          int n = in.charAt(i);
          if (n == 11 || n == 12 || n >= 1 && n <= 9 || n >= 14 && n <= 127) {
            local.append((char) n);
            i++;
          } else {
            return null;
          }
        } else if (c == 11 || c == 12 || c == 32 || c == 33 || c == 127 || c >= 1 && c <= 8 || c >= 14 && c <= 31
            || c >= 35 && c <= 91 || c >= 93 && c <= 126) {
          local.append((char) c);
        } else {
          return null;
        }
      }
    } else {
      while (i < in.length()) {
        int c = in.charAt(i);
        if (c == '\\') {
          i++;
          if (i >= in.length()) {
            return null;
          }
          local.append(in.charAt(i));
          i++;
        } else if (c >= '0' && c <= '9' || c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || "!#$%&'*+-/=?^_`{|}~.".indexOf(c) >= 0) {
          local.append((char) c);
          i++;
        } else {
          break;
        }
      }
      if (local.length() == 0) {
        return null;
      }
      String l = local.toString();
      if (l.charAt(0) == '.' || l.charAt(l.length() - 1) == '.' || l.contains("..")) {
        return null;
      }
    }
    if (i >= in.length() || in.charAt(i) != '@') {
      return null;
    }
    String domain = in.substring(i + 1);
    if (!domainNameValid(domain, false) || domain.indexOf('@') >= 0) {
      return null;
    }
    return new String[] {local.toString(), domain};
  }

  /**
   * Go's netip.ParseAddr over a byte string. Returns the 4- or 16-byte address; throws {@link
   * IllegalArgumentException} with Go's error message. Zones are accepted only if {@code
   * allowZone}; when rejected the address is reported as unparseable, as net.ParseIP does.
   */
  static byte[] parseAddr(String s, boolean allowZone) {
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (c == '.') {
        byte[] ip = new byte[4];
        parseIpv4Fields(s, 0, s.length(), ip, 0);
        return ip;
      }
      if (c == ':') {
        return parseIpv6(s, allowZone);
      }
      if (c == '%') {
        throw addrError(s, "missing IPv6 address", null);
      }
    }
    throw addrError(s, "unable to parse IP", null);
  }

  private static IllegalArgumentException addrError(String in, String msg, String at) {
    String m = "ParseAddr(" + quote(in) + "): " + msg;
    if (at != null && !at.isEmpty()) {
      m += " (at " + quote(at) + ")";
    }
    return new IllegalArgumentException(m);
  }

  private static void parseIpv4Fields(String in, int off, int end, byte[] fields, int fieldsOff) {
    String s = in.substring(off, end);
    int val = 0;
    int pos = 0;
    int digLen = 0;
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (c >= '0' && c <= '9') {
        if (digLen == 1 && val == 0) {
          throw addrError(in, "IPv4 field has octet with leading zero", null);
        }
        val = val * 10 + c - '0';
        digLen++;
        if (val > 255) {
          throw addrError(in, "IPv4 field has value >255", null);
        }
      } else if (c == '.') {
        if (i == 0 || i == s.length() - 1 || s.charAt(i - 1) == '.') {
          throw addrError(in, "IPv4 field must have at least one digit", s.substring(i));
        }
        if (pos == 3) {
          throw addrError(in, "IPv4 address too long", null);
        }
        fields[fieldsOff + pos] = (byte) val;
        pos++;
        val = 0;
        digLen = 0;
      } else {
        throw addrError(in, "unexpected character", s.substring(i));
      }
    }
    if (pos < 3) {
      throw addrError(in, "IPv4 address too short", null);
    }
    fields[fieldsOff + 3] = (byte) val;
  }

  private static byte[] parseIpv6(String in, boolean allowZone) {
    String s = in;
    String zone = "";
    int pct = s.indexOf('%');
    if (pct >= 0) {
      zone = s.substring(pct + 1);
      s = s.substring(0, pct);
      if (zone.isEmpty()) {
        throw addrError(in, "zone must be a non-empty string", null);
      }
    }
    byte[] ip = new byte[16];
    int ellipsis = -1;
    if (s.length() >= 2 && s.charAt(0) == ':' && s.charAt(1) == ':') {
      ellipsis = 0;
      s = s.substring(2);
      if (s.isEmpty()) {
        return checkZone(in, ip, zone, allowZone);
      }
    }
    int i = 0;
    while (i < 16) {
      int off = 0;
      int acc = 0;
      while (off < s.length()) {
        char c = s.charAt(off);
        int d;
        if (c >= '0' && c <= '9') {
          d = c - '0';
        } else if (c >= 'a' && c <= 'f') {
          d = c - 'a' + 10;
        } else if (c >= 'A' && c <= 'F') {
          d = c - 'A' + 10;
        } else {
          break;
        }
        acc = (acc << 4) + d;
        if (off > 3) {
          throw addrError(in, "each group must have 4 or less digits", s);
        }
        if (acc > 0xffff) {
          throw addrError(in, "IPv6 field has value >=2^16", s);
        }
        off++;
      }
      if (off == 0) {
        throw addrError(in, "each colon-separated field must have at least one digit", s);
      }
      if (off < s.length() && s.charAt(off) == '.') {
        if (ellipsis < 0 && i != 12) {
          throw addrError(in, "embedded IPv4 address must replace the final 2 fields of the address", s);
        }
        if (i + 4 > 16) {
          throw addrError(in, "too many hex fields to fit an embedded IPv4 at the end of the address", s);
        }
        int end = in.length();
        if (!zone.isEmpty()) {
          end -= zone.length() + 1;
        }
        parseIpv4Fields(in, end - s.length(), end, ip, i);
        s = "";
        i += 4;
        break;
      }
      ip[i] = (byte) (acc >> 8);
      ip[i + 1] = (byte) acc;
      i += 2;
      s = s.substring(off);
      if (s.isEmpty()) {
        break;
      }
      if (s.charAt(0) != ':') {
        throw addrError(in, "unexpected character, want colon", s);
      } else if (s.length() == 1) {
        throw addrError(in, "colon must be followed by more characters", s);
      }
      s = s.substring(1);
      if (s.charAt(0) == ':') {
        if (ellipsis >= 0) {
          throw addrError(in, "multiple :: in address", s);
        }
        ellipsis = i;
        s = s.substring(1);
        if (s.isEmpty()) {
          break;
        }
      }
    }
    if (!s.isEmpty()) {
      throw addrError(in, "trailing garbage after address", s);
    }
    if (i < 16) {
      if (ellipsis < 0) {
        throw addrError(in, "address string too short", null);
      }
      int n = 16 - i;
      for (int j = i - 1; j >= ellipsis; j--) {
        ip[j + n] = ip[j];
      }
      for (int j = ellipsis; j < ellipsis + n; j++) {
        ip[j] = 0;
      }
    } else if (ellipsis >= 0) {
      throw addrError(in, "the :: must expand to at least one field of zeros", null);
    }
    return checkZone(in, ip, zone, allowZone);
  }

  private static byte[] checkZone(String in, byte[] ip, String zone, boolean allowZone) {
    if (!allowZone && !zone.isEmpty()) {
      throw new IllegalArgumentException("ParseAddr(" + quote(in) + "): IPv6 zone not allowed");
    }
    return ip;
  }

  /** Go's net.IP.String for a 4- or 16-byte address that is not IPv4-mapped. */
  static String ipToString(byte[] ip) {
    if (ip.length == 4 || isIpv4Mapped(ip)) {
      int o = ip.length - 4;
      return (ip[o] & 0xff) + "." + (ip[o + 1] & 0xff) + "." + (ip[o + 2] & 0xff) + "." + (ip[o + 3] & 0xff);
    }
    int zeroStart = 255;
    int zeroEnd = 255;
    for (int i = 0; i < 8; i++) {
      int j = i;
      while (j < 8 && group(ip, j) == 0) {
        j++;
      }
      int l = j - i;
      if (l >= 2 && l > zeroEnd - zeroStart) {
        zeroStart = i;
        zeroEnd = j;
      }
    }
    StringBuilder sb = new StringBuilder(39);
    int i = 0;
    while (i < 8) {
      if (i == zeroStart) {
        sb.append("::");
        i = zeroEnd;
        if (i >= 8) {
          break;
        }
      } else if (i > 0) {
        sb.append(':');
      }
      sb.append(Integer.toHexString(group(ip, i)));
      i++;
    }
    return sb.toString();
  }

  private static int group(byte[] ip, int i) {
    return (ip[2 * i] & 0xff) << 8 | (ip[2 * i + 1] & 0xff);
  }

  /** Whether a 16-byte address is an IPv4-mapped IPv6 address (net.IP.To4 succeeds). */
  static boolean isIpv4Mapped(byte[] ip) {
    return ip.length == 16 && Arrays.equals(ip, 0, 12, V4_IN_V6_PREFIX, 0, 12);
  }
}
