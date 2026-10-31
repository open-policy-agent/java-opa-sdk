// This file contains a Java translation of code from the Go project:
//   golang.org/x/crypto/cryptobyte (asn1.go), Copyright 2017 The Go Authors.
//   time (format.go, format_rfc3339.go), Copyright 2010 The Go Authors.
// That code is used under the BSD-style license reproduced in
// src/main/resources/META-INF/licenses/LICENSE-golang.txt (Copyright 2009 The Go Authors).
// The translation and the rest of this file are licensed under the Apache License 2.0.
package io.github.open_policy_agent.opa.ast.builtin.impls;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.Locale;

/**
 * An ASN.1 UTCTime/GeneralizedTime value as parsed by Go's cryptobyte. The offset is kept as a
 * number of seconds rather than a {@link java.time.ZoneOffset}, because Go accepts offsets up to +/-24:00,
 * beyond what {@code ZoneOffset} can represent.
 */
record X509Time(LocalDateTime local, int offsetSeconds, boolean utc) {

  /**
   * Formats the time as Go's {@code time.Time.MarshalJSON} (RFC 3339 with nanoseconds omitted when
   * zero). Throws when Go would refuse to marshal the value.
   */
  String toJson() {
    LocalDateTime t = local;
    String s = String.format(
        Locale.ROOT,
        "%04d-%02d-%02dT%02d:%02d:%02d", t.getYear(), t.getMonthValue(), t.getDayOfMonth(), t.getHour(), t.getMinute(), t.getSecond());
    if (utc) {
      return s + "Z";
    }
    int minutes = Math.abs(offsetSeconds / 60);
    if (minutes / 60 >= 24) {
      throw new IllegalArgumentException(
          "json: error calling MarshalJSON for type *time.Time: timezone hour outside of range [0,23]");
    }
    return s + String.format(Locale.ROOT, "%s%02d:%02d", offsetSeconds < 0 ? "-" : "+", minutes / 60, minutes % 60);
  }

  /**
   * Parses UTCTime contents with layout "060102150405Z0700", falling back to the minute-precision
   * layout "0601021504Z0700". Go round-trips the parsed value through the layout, so only the
   * canonical spelling is accepted.
   */
  static X509Time parseUtcTime(byte[] b) {
    X509Time t = parse(b, 2, true);
    if (t == null) {
      t = parse(b, 2, false);
    }
    if (t == null) {
      return null;
    }
    if (t.local.getYear() >= 2050) {
      // RFC 5280: two-digit years 50-99 are 1950-1999.
      return new X509Time(t.local.minusYears(100), t.offsetSeconds, t.utc);
    }
    return t;
  }

  /** Parses GeneralizedTime contents with layout "20060102150405Z0700". */
  static X509Time parseGeneralizedTime(byte[] b) {
    return parse(b, 4, true);
  }

  private static X509Time parse(byte[] b, int yearDigits, boolean withSeconds) {
    int fixed = yearDigits + (withSeconds ? 10 : 8);
    if (b.length < fixed + 1) {
      return null;
    }
    for (int i = 0; i < fixed; i++) {
      if (b[i] < '0' || b[i] > '9') {
        return null;
      }
    }
    int i = 0;
    int year = num(b, i, yearDigits);
    i += yearDigits;
    if (yearDigits == 2) {
      year += year >= 69 ? 1900 : 2000;
    }
    int month = num(b, i, 2);
    int day = num(b, i + 2, 2);
    int hour = num(b, i + 4, 2);
    int minute = num(b, i + 6, 2);
    int second = withSeconds ? num(b, i + 8, 2) : 0;
    i = fixed;
    if (month < 1 || month > 12 || hour >= 24 || minute >= 60 || second >= 60) {
      return null;
    }
    if (day < 1 || day > YearMonth.of(year, month).lengthOfMonth()) {
      return null;
    }
    LocalDateTime local = LocalDateTime.of(year, month, day, hour, minute, second);
    // Zone: "Z", or a non-zero numeric offset "+hhmm"/"-hhmm" in canonical form.
    if (b[i] == 'Z') {
      return b.length == i + 1 ? new X509Time(local, 0, true) : null;
    }
    if (b.length != i + 5 || b[i] != '+' && b[i] != '-') {
      return null;
    }
    for (int j = i + 1; j < i + 5; j++) {
      if (b[j] < '0' || b[j] > '9') {
        return null;
      }
    }
    int hh = num(b, i + 1, 2);
    int mm = num(b, i + 3, 2);
    if (hh > 24 || mm >= 60 || hh == 0 && mm == 0) {
      return null;
    }
    int offset = (hh * 60 + mm) * 60;
    return new X509Time(local, b[i] == '-' ? -offset : offset, false);
  }

  private static int num(byte[] b, int off, int len) {
    int v = 0;
    for (int i = off; i < off + len; i++) {
      v = v * 10 + (b[i] - '0');
    }
    return v;
  }
}
