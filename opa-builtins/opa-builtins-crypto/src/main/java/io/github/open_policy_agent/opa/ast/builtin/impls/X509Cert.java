// This file contains a Java translation of code from the Go project:
//   crypto/x509 (x509.go), Copyright 2009 The Go Authors.
//   crypto/x509/pkix (pkix.go), Copyright 2011 The Go Authors.
// That code is used under the BSD-style license reproduced in
// src/main/resources/META-INF/licenses/LICENSE-golang.txt (Copyright 2009 The Go Authors).
// The translation and the rest of this file are licensed under the Apache License 2.0.
package io.github.open_policy_agent.opa.ast.builtin.impls;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/**
 * A certificate as parsed by {@link X509Parser}, holding the same fields as Go's {@code
 * x509.Certificate}. Lists are null where Go leaves a nil slice, since Go's JSON encoding
 * distinguishes nil ({@code null}) from empty ({@code []}).
 */
final class X509Cert {

  // Go's x509.SignatureAlgorithm values (MD2WithRSA = 1 is never produced by the parser).
  static final int SIG_UNKNOWN = 0;
  static final int SIG_MD5_RSA = 2;
  static final int SIG_SHA1_RSA = 3;
  static final int SIG_SHA256_RSA = 4;
  static final int SIG_SHA384_RSA = 5;
  static final int SIG_SHA512_RSA = 6;
  static final int SIG_DSA_SHA1 = 7;
  static final int SIG_DSA_SHA256 = 8;
  static final int SIG_ECDSA_SHA1 = 9;
  static final int SIG_ECDSA_SHA256 = 10;
  static final int SIG_ECDSA_SHA384 = 11;
  static final int SIG_ECDSA_SHA512 = 12;
  static final int SIG_SHA256_RSAPSS = 13;
  static final int SIG_SHA384_RSAPSS = 14;
  static final int SIG_SHA512_RSAPSS = 15;
  static final int SIG_ED25519 = 16;
  static final int SIG_MLDSA44 = 17;
  static final int SIG_MLDSA65 = 18;
  static final int SIG_MLDSA87 = 19;

  // Go's x509.PublicKeyAlgorithm values.
  static final int PK_UNKNOWN = 0;
  static final int PK_RSA = 1;
  static final int PK_DSA = 2;
  static final int PK_ECDSA = 3;
  static final int PK_ED25519 = 4;
  static final int PK_MLDSA = 5;

  byte[] raw;
  byte[] rawTbsCertificate;
  byte[] rawSubjectPublicKeyInfo;
  byte[] rawSubject;
  byte[] rawIssuer;
  byte[] rawSignatureAlgorithm;
  byte[] signature;
  int signatureAlgorithm;
  int publicKeyAlgorithm;
  /** One of {@link RsaKey}, {@link EcKey}, {@link Ed25519Key}, {@link DsaKey}, {@link MlDsaKey}, or null. */
  Object publicKey;
  long version;
  BigInteger serialNumber;
  Name issuer = new Name();
  Name subject = new Name();
  X509Time notBefore;
  X509Time notAfter;
  long keyUsage;
  List<Extension> extensions;
  List<int[]> unhandledCriticalExtensions;
  List<Long> extKeyUsage;
  List<int[]> unknownExtKeyUsage;
  boolean basicConstraintsValid;
  boolean isCa;
  long maxPathLen;
  boolean maxPathLenZero;
  byte[] subjectKeyId;
  byte[] authorityKeyId;
  // Go strings with unvalidated bytes, held as ISO-8859-1.
  List<String> ocspServer;
  List<String> issuingCertificateUrl;
  List<String> crlDistributionPoints;
  // SAN values; the strings are IA5 (ASCII).
  List<String> dnsNames;
  List<String> emailAddresses;
  List<byte[]> ipAddresses;
  List<X509Url> uris;
  boolean permittedDnsDomainsCritical;
  List<String> permittedDnsDomains;
  List<String> excludedDnsDomains;
  List<IpNet> permittedIpRanges;
  List<IpNet> excludedIpRanges;
  List<String> permittedEmailAddresses;
  List<String> excludedEmailAddresses;
  List<String> permittedUriDomains;
  List<String> excludedUriDomains;
  List<int[]> policyIdentifiers;
  /** DER contents of each policy OID (Go's x509.OID). */
  List<byte[]> policies;
  long inhibitAnyPolicy;
  boolean inhibitAnyPolicyZero;
  long inhibitPolicyMapping;
  boolean inhibitPolicyMappingZero;
  long requireExplicitPolicy;
  boolean requireExplicitPolicyZero;
  /** Pairs of {issuerDomainPolicy, subjectDomainPolicy} OID contents. */
  List<byte[][]> policyMappings;

  /** Go's pkix.Name. */
  static final class Name {
    List<String> country;
    List<String> organization;
    List<String> organizationalUnit;
    List<String> locality;
    List<String> province;
    List<String> streetAddress;
    List<String> postalCode;
    String serialNumber = "";
    String commonName = "";
    List<AttributeTypeAndValue> names;
  }

  /** Go's pkix.AttributeTypeAndValue; see {@link X509Parser} for the possible value types. */
  record AttributeTypeAndValue(int[] type, Object value) {}

  /** Go's asn1.RawValue, for attribute values of types Go does not decode. */
  record RawValue(int cls, int tag, boolean compound, byte[] bytes, byte[] fullBytes) {}

  /** Go's pkix.Extension. */
  record Extension(int[] id, boolean critical, byte[] value) {}

  /** Go's net.IPNet. */
  record IpNet(byte[] ip, byte[] mask) {}

  record RsaKey(BigInteger n, long e) {}

  /** {@code curve} is "P-224", "P-256", "P-384" or "P-521". */
  record EcKey(String curve, BigInteger x, BigInteger y) {}

  record Ed25519Key(byte[] key) {}

  record DsaKey(BigInteger p, BigInteger q, BigInteger g, BigInteger y) {}

  /** An ML-DSA key; Go serializes it as an empty object, and Java 17 cannot verify with it. */
  record MlDsaKey() {}

  /** Appends to a list that may still be null, matching how Go appends to a nil slice. */
  static <T> List<T> add(List<T> list, T value) {
    List<T> l = list == null ? new ArrayList<>() : list;
    l.add(value);
    return l;
  }
}
