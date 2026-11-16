// This file contains a Java translation of code from the Go project:
//   golang.org/x/crypto/cryptobyte (asn1.go, string.go), Copyright 2017 The Go Authors.
// That code is used under the BSD-style license reproduced in
// src/main/resources/META-INF/licenses/LICENSE-golang.txt (Copyright 2009 The Go Authors).
// The translation and the rest of this file are licensed under the Apache License 2.0.
package io.github.open_policy_agent.opa.ast.builtin.impls;

import io.github.open_policy_agent.opa.ast.builtin.BuiltinError;
import java.math.BigInteger;
import java.util.Arrays;

/**
 * A strict DER reader over a slice of a byte array, mirroring the semantics of Go's {@code
 * golang.org/x/crypto/cryptobyte} as used by {@code crypto/x509}. Every read either consumes a
 * complete, minimally encoded element and returns a non-null value, or returns null; callers turn a
 * null into the exact error Go reports at the same point, usually with {@link #req}.
 */
final class X509Der {

  static final int TAG_BOOLEAN = 0x01;
  static final int TAG_INTEGER = 0x02;
  static final int TAG_BIT_STRING = 0x03;
  static final int TAG_OCTET_STRING = 0x04;
  static final int TAG_NULL = 0x05;
  static final int TAG_OID = 0x06;
  static final int TAG_UTF8_STRING = 0x0c;
  static final int TAG_SEQUENCE = 0x30;
  static final int TAG_SET = 0x31;
  static final int TAG_NUMERIC_STRING = 0x12;
  static final int TAG_PRINTABLE_STRING = 0x13;
  static final int TAG_T61_STRING = 0x14;
  static final int TAG_IA5_STRING = 0x16;
  static final int TAG_UTC_TIME = 0x17;
  static final int TAG_GENERALIZED_TIME = 0x18;
  static final int TAG_BMP_STRING = 0x1e;

  private final byte[] buf;
  private int pos;
  private final int end;

  X509Der(byte[] buf) {
    this(buf, 0, buf.length);
  }

  X509Der(byte[] buf, int pos, int end) {
    this.buf = buf;
    this.pos = pos;
    this.end = end;
  }

  /** Returns {@code value}, or throws Go's error message when a read failed. */
  static <T> T req(T value, String goError) {
    if (value == null) {
      throw new BuiltinError(goError);
    }
    return value;
  }

  static int contextSpecific(int tag) {
    return 0x80 | tag;
  }

  static int contextSpecificConstructed(int tag) {
    return 0xa0 | tag;
  }

  boolean isEmpty() {
    return pos >= end;
  }

  int length() {
    return end - pos;
  }

  /** Returns a copy of the unread bytes. */
  byte[] bytes() {
    return Arrays.copyOfRange(buf, pos, end);
  }

  boolean peekTag(int tag) {
    return pos < end && (buf[pos] & 0xff) == tag;
  }

  /**
   * Reads one element; returns its contents (or, with {@code header}, the whole element), or null.
   * When {@code tag} is negative any tag is accepted and stored in {@code outTag[0]}.
   */
  private X509Der readElement(int tag, int[] outTag, boolean header) {
    if (end - pos < 2 || (buf[pos] & 0x1f) == 0x1f) {
      // High-tag-number form is not supported, as in cryptobyte.
      return null;
    }
    int actualTag = buf[pos] & 0xff;
    int lenByte = buf[pos + 1] & 0xff;
    long length = lenByte;
    int headerLen = 2;
    if ((lenByte & 0x80) != 0) {
      int lenLen = lenByte & 0x7f;
      if (lenLen == 0 || lenLen > 4 || end - pos < 2 + lenLen) {
        return null;
      }
      length = 0;
      for (int i = 0; i < lenLen; i++) {
        length = (length << 8) | (buf[pos + 2 + i] & 0xff);
      }
      // DER requires the minimal length encoding.
      if (length < 128 || (length >> ((lenLen - 1) * 8)) == 0) {
        return null;
      }
      headerLen += lenLen;
    }
    if (headerLen + length > end - pos || tag >= 0 && actualTag != tag) {
      return null;
    }
    int start = pos;
    pos += headerLen + (int) length;
    if (outTag != null) {
      outTag[0] = actualTag;
    }
    return new X509Der(buf, header ? start : start + headerLen, pos);
  }

  /** Reads the contents of the next element of any tag, storing the tag in {@code outTag[0]}. */
  X509Der readAnyContents(int[] outTag) {
    return readElement(-1, outTag, false);
  }

  /** Reads the next element of any tag including its header, storing the tag in {@code outTag[0]}. */
  X509Der readAnyElement(int[] outTag) {
    return readElement(-1, outTag, true);
  }

  /** Reads the contents of the next element, which must have the given tag. */
  X509Der read(int tag) {
    return readElement(tag, null, false);
  }

  /** Reads the next element including its header, which must have the given tag. */
  X509Der readElement(int tag) {
    return readElement(tag, null, true);
  }

  /**
   * Reads an optional element with the given tag. Returns an empty reader (with {@code
   * present[0]=false}) when the next element has a different tag, and null on malformed input.
   */
  X509Der readOptional(int tag, boolean[] present) {
    present[0] = peekTag(tag);
    return present[0] ? read(tag) : new X509Der(buf, pos, pos);
  }

  /** Skips an optional element with the given tag; returns false on malformed input. */
  boolean skipOptional(int tag) {
    return !peekTag(tag) || read(tag) != null;
  }

  /** The contents of an INTEGER under {@code tag}, if minimally encoded per X.690; else null. */
  private byte[] readIntegerBytes(int tag) {
    X509Der contents = read(tag);
    byte[] b = contents == null ? null : contents.bytes();
    if (b == null || b.length == 0) {
      return null;
    }
    boolean minimal = b.length == 1 || !(b[0] == 0 && (b[1] & 0x80) == 0 || b[0] == (byte) 0xff && (b[1] & 0x80) == 0x80);
    return minimal ? b : null;
  }

  /** Reads an INTEGER of any size, or returns null. */
  BigInteger readBigInteger() {
    byte[] b = readIntegerBytes(TAG_INTEGER);
    return b == null ? null : new BigInteger(b);
  }

  /** Reads an INTEGER that fits in a signed 64-bit value, as Go's ReadASN1Integer(*int64). */
  Long readInt64() {
    return readInt64(TAG_INTEGER);
  }

  /** Reads a signed 64-bit INTEGER under the given (possibly implicit) tag. */
  Long readInt64(int tag) {
    byte[] b = readIntegerBytes(tag);
    return b == null || b.length > 8 ? null : new BigInteger(b).longValue();
  }

  /** Reads a non-negative INTEGER that fits in an unsigned 64-bit value (as a BigInteger). */
  BigInteger readUint64() {
    byte[] b = readIntegerBytes(TAG_INTEGER);
    if (b == null || b.length > 9 || b.length == 9 && b[0] != 0 || (b[0] & 0x80) != 0) {
      return null;
    }
    return new BigInteger(1, b);
  }

  /**
   * Reads an INTEGER into Go's {@code []byte} form (ReadASN1Integer(*[]byte)): negative values are
   * rejected and leading zero bytes are stripped.
   */
  byte[] readUnsignedIntegerBytes() {
    byte[] b = readIntegerBytes(TAG_INTEGER);
    if (b == null || (b[0] & 0x80) == 0x80) {
      return null;
    }
    int i = 0;
    while (b.length - i > 1 && b[i] == 0) {
      i++;
    }
    return Arrays.copyOfRange(b, i, b.length);
  }

  /** Reads a DER BOOLEAN (only 0x00 and 0xff are accepted), or returns null. */
  Boolean readBoolean() {
    X509Der contents = read(TAG_BOOLEAN);
    if (contents == null || contents.length() != 1) {
      return null;
    }
    int v = contents.buf[contents.pos] & 0xff;
    return v == 0 ? Boolean.FALSE : v == 0xff ? Boolean.TRUE : null;
  }

  /**
   * Reads an OBJECT IDENTIFIER into Go's {@code asn1.ObjectIdentifier} form. Each sub-identifier
   * must fit in 31 bits and be minimally encoded, as in cryptobyte.
   */
  int[] readOid() {
    X509Der contents = read(TAG_OID);
    return contents == null ? null : contents.oidContents();
  }

  /** Decodes all unread bytes as OID contents (as {@link #readOid}), or returns null. */
  int[] oidContents() {
    if (isEmpty()) {
      return null;
    }
    int[] components = new int[length() + 1];
    int count = 0;
    while (!isEmpty()) {
      int v = readBase128();
      if (v < 0) {
        return null;
      }
      if (count > 0) {
        components[count++] = v;
      } else {
        // The first sub-identifier is 40 * arc1 + arc2.
        components[0] = v < 80 ? v / 40 : 2;
        components[1] = v < 80 ? v % 40 : v - 80;
        count = 2;
      }
    }
    return Arrays.copyOf(components, count);
  }

  /** Reads one base-128 sub-identifier, or returns -1. */
  private int readBase128() {
    int ret = 0;
    for (int i = 0; pos < end && i < 5 && ret < 1 << (31 - 7); i++) {
      int b = buf[pos++] & 0xff;
      if (i == 0 && b == 0x80) {
        return -1;
      }
      ret = ret << 7 | (b & 0x7f);
      if ((b & 0x80) == 0) {
        return ret;
      }
    }
    return -1;
  }

  /** A DER BIT STRING, as Go's {@code asn1.BitString}. */
  record BitString(byte[] bytes, int bitLength) {

    /** Returns the bit at index i (most significant bit of the first byte is index 0). */
    int at(int i) {
      return i < 0 || i >= bitLength ? 0 : (bytes[i / 8] >> (7 - i % 8)) & 1;
    }

    /** Go's BitString.RightAlign: the bits shifted so that padding is at the front. */
    byte[] rightAlign() {
      int shift = 8 - bitLength % 8;
      if (shift == 8 || bytes.length == 0) {
        return bytes;
      }
      byte[] a = new byte[bytes.length];
      a[0] = (byte) ((bytes[0] & 0xff) >> shift);
      for (int i = 1; i < bytes.length; i++) {
        a[i] = (byte) (((bytes[i - 1] & 0xff) << (8 - shift)) | ((bytes[i] & 0xff) >> shift));
      }
      return a;
    }
  }

  /** Reads a DER BIT STRING, or returns null. */
  BitString readBitString() {
    X509Der contents = read(TAG_BIT_STRING);
    if (contents == null || contents.isEmpty()) {
      return null;
    }
    int paddingBits = contents.buf[contents.pos] & 0xff;
    byte[] data = Arrays.copyOfRange(contents.buf, contents.pos + 1, contents.end);
    if (paddingBits > 7
        || data.length == 0 && paddingBits != 0
        || data.length > 0 && (data[data.length - 1] & ((1 << paddingBits) - 1)) != 0) {
      return null;
    }
    return new BitString(data, data.length * 8 - paddingBits);
  }

  /** Reads a UTCTime or GeneralizedTime with the given tag, or returns null. */
  X509Time readTime(int tag) {
    X509Der contents = read(tag);
    if (contents == null) {
      return null;
    }
    return tag == TAG_UTC_TIME ? X509Time.parseUtcTime(contents.bytes()) : X509Time.parseGeneralizedTime(contents.bytes());
  }
}
