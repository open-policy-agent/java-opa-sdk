// This file contains a Java translation of code from the Go project:
//   crypto/ecdsa (ecdsa.go) and crypto/internal/fips140/nistec, Copyright 2011-2022 The Go Authors.
// That code is used under the BSD-style license reproduced in
// src/main/resources/META-INF/licenses/LICENSE-golang.txt (Copyright 2009 The Go Authors).
// The translation and the rest of this file are licensed under the Apache License 2.0.
package io.github.open_policy_agent.opa.ast.builtin.impls;

import io.github.open_policy_agent.opa.ast.builtin.BuiltinError;
import java.math.BigInteger;
import java.util.Arrays;

/**
 * The NIST curves Go's x509 supports for ECDSA keys, with the public key validation of Go's {@code
 * ecdsa.ParseUncompressedPublicKey}: uncompressed encoding only, reduced coordinates, and the point
 * on the curve.
 */
final class X509Curves {

  static final Curve P224 =
      new Curve(
          "P-224",
          new int[] {1, 3, 132, 0, 33},
          "ffffffffffffffffffffffffffffffff000000000000000000000001",
          "b4050a850c04b3abf54132565044b0b7d7bfd8ba270b39432355ffb4",
          "invalid P224Element encoding");
  static final Curve P256 =
      new Curve(
          "P-256",
          new int[] {1, 2, 840, 10045, 3, 1, 7},
          "ffffffff00000001000000000000000000000000ffffffffffffffffffffffff",
          "5ac635d8aa3a93e7b3ebbd55769886bc651d06b0cc53b0f63bce3c3e27d2604b",
          // Go's assembly P-256 implementation (amd64, arm64) reports this message.
          "invalid P256 element encoding");
  static final Curve P384 =
      new Curve(
          "P-384",
          new int[] {1, 3, 132, 0, 34},
          "fffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffeffffffff0000000000000000ffffffff",
          "b3312fa7e23ee7e4988e056be3f82d19181d9c6efe8141120314088f5013875ac656398d8a2ed19d2a85c8edd3ec2aef",
          "invalid P384Element encoding");
  static final Curve P521 =
      new Curve(
          "P-521",
          new int[] {1, 3, 132, 0, 35},
          "01ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff",
          "0051953eb9618e1c9a1f929a21a0b68540eea2da725b99b315f3b8b489918ef109e156193951ec7e937b1652c0bd3bb1bf073573df883d2c34f1ef451fd46b503f00",
          "invalid P521Element encoding");

  private static final Curve[] CURVES = {P224, P256, P384, P521};

  private X509Curves() {}

  static Curve byOid(int[] oid) {
    for (Curve c : CURVES) {
      if (Arrays.equals(c.oid(), oid)) {
        return c;
      }
    }
    return null;
  }


  /** A short Weierstrass curve y^2 = x^3 - 3x + b over GF(p). */
  record Curve(String name, int[] oid, BigInteger p, BigInteger b, String elementError) {

    Curve(String name, int[] oid, String p, String b, String elementError) {
      this(name, oid, new BigInteger(p, 16), new BigInteger(b, 16), elementError);
    }

    private String shortName() {
      return name.replace("-", "");
    }

    X509Cert.EcKey parseUncompressed(byte[] data) {
      if (data.length < 1 || data[0] != 4) {
        throw new BuiltinError("ecdsa: invalid uncompressed public key");
      }
      int byteLength = (p.bitLength() + 7) / 8;
      if (data.length != 1 + 2 * byteLength) {
        throw new BuiltinError("invalid " + shortName() + " point encoding");
      }
      BigInteger x = new BigInteger(1, Arrays.copyOfRange(data, 1, 1 + byteLength));
      BigInteger y = new BigInteger(1, Arrays.copyOfRange(data, 1 + byteLength, data.length));
      if (x.compareTo(p) >= 0 || y.compareTo(p) >= 0) {
        throw new BuiltinError(elementError);
      }
      BigInteger rhs = x.pow(3).subtract(x.multiply(BigInteger.valueOf(3))).add(b).mod(p);
      if (!y.multiply(y).mod(p).equals(rhs)) {
        throw new BuiltinError(shortName() + " point not on curve");
      }
      return new X509Cert.EcKey(name, x, y);
    }
  }
}
