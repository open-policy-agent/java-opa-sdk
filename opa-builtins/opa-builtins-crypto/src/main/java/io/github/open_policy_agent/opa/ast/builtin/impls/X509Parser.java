// This file contains a Java translation of code from the Go project:
//   crypto/x509 (parser.go), Copyright 2021 The Go Authors.
//   crypto/x509 (x509.go), Copyright 2009 The Go Authors.
//   crypto/x509 (oid.go), Copyright 2023 The Go Authors.
//   crypto/x509/pkix (pkix.go), Copyright 2011 The Go Authors.
// That code is used under the BSD-style license reproduced in
// src/main/resources/META-INF/licenses/LICENSE-golang.txt (Copyright 2009 The Go Authors).
// The translation and the rest of this file are licensed under the Apache License 2.0.
package io.github.open_policy_agent.opa.ast.builtin.impls;

import io.github.open_policy_agent.opa.ast.builtin.BuiltinError;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.IntPredicate;
import java.util.stream.Collectors;

/**
 * A port of Go 1.27's {@code crypto/x509} certificate parser ({@code parser.go}). It accepts and
 * rejects the same inputs, reports the same error messages and fills the same fields, so that the
 * builtins return what OPA returns. Cryptographic checks are not done here; see {@link
 * X509Verifier}.
 */
final class X509Parser {

  private static final int SEQUENCE = X509Der.TAG_SEQUENCE;
  private static final byte[] ASN1_NULL = {0x05, 0x00};
  private static final String BAD_ATTRIBUTE = "x509: invalid RDNSequence: invalid attribute value: ";

  private static final int[] OID_RSA = {1, 2, 840, 113549, 1, 1, 1};
  private static final int[] OID_DSA = {1, 2, 840, 10040, 4, 1};
  private static final int[] OID_ECDSA = {1, 2, 840, 10045, 2, 1};
  private static final int[] OID_ED25519 = {1, 3, 101, 112};
  private static final int[][] OID_MLDSA = {
    {2, 16, 840, 1, 101, 3, 4, 3, 17}, {2, 16, 840, 1, 101, 3, 4, 3, 18}, {2, 16, 840, 1, 101, 3, 4, 3, 19}
  };
  private static final int[] OID_RSA_PSS = {1, 2, 840, 113549, 1, 1, 10};
  private static final int[] OID_MGF1 = {1, 2, 840, 113549, 1, 1, 8};
  private static final int[] OID_AIA = {1, 3, 6, 1, 5, 5, 7, 1, 1};
  private static final int[] OID_AIA_OCSP = {1, 3, 6, 1, 5, 5, 7, 48, 1};
  private static final int[] OID_AIA_ISSUERS = {1, 3, 6, 1, 5, 5, 7, 48, 2};
  /** SHA-256, SHA-384 and SHA-512, the hashes Go accepts for RSA-PSS. */
  private static final int[][] OID_PSS_HASHES = {
    {2, 16, 840, 1, 101, 3, 4, 2, 1}, {2, 16, 840, 1, 101, 3, 4, 2, 2}, {2, 16, 840, 1, 101, 3, 4, 2, 3}
  };

  /** A signature algorithm OID and its x509.SignatureAlgorithm. */
  private record SignatureOid(int algorithm, int[] oid) {}

  /** Signature algorithm OIDs, in the order of Go's signatureAlgorithmDetails table. */
  private static final SignatureOid[] SIGNATURE_OIDS = {
    new SignatureOid(X509Cert.SIG_MD5_RSA, new int[] {1, 2, 840, 113549, 1, 1, 4}),
    new SignatureOid(X509Cert.SIG_SHA1_RSA, new int[] {1, 2, 840, 113549, 1, 1, 5}),
    new SignatureOid(X509Cert.SIG_SHA1_RSA, new int[] {1, 3, 14, 3, 2, 29}),
    new SignatureOid(X509Cert.SIG_SHA256_RSA, new int[] {1, 2, 840, 113549, 1, 1, 11}),
    new SignatureOid(X509Cert.SIG_SHA384_RSA, new int[] {1, 2, 840, 113549, 1, 1, 12}),
    new SignatureOid(X509Cert.SIG_SHA512_RSA, new int[] {1, 2, 840, 113549, 1, 1, 13}),
    new SignatureOid(X509Cert.SIG_DSA_SHA1, new int[] {1, 2, 840, 10040, 4, 3}),
    new SignatureOid(X509Cert.SIG_DSA_SHA256, new int[] {2, 16, 840, 1, 101, 3, 4, 3, 2}),
    new SignatureOid(X509Cert.SIG_ECDSA_SHA1, new int[] {1, 2, 840, 10045, 4, 1}),
    new SignatureOid(X509Cert.SIG_ECDSA_SHA256, new int[] {1, 2, 840, 10045, 4, 3, 2}),
    new SignatureOid(X509Cert.SIG_ECDSA_SHA384, new int[] {1, 2, 840, 10045, 4, 3, 3}),
    new SignatureOid(X509Cert.SIG_ECDSA_SHA512, new int[] {1, 2, 840, 10045, 4, 3, 4}),
    new SignatureOid(X509Cert.SIG_ED25519, OID_ED25519),
    new SignatureOid(X509Cert.SIG_MLDSA44, OID_MLDSA[0]),
    new SignatureOid(X509Cert.SIG_MLDSA65, OID_MLDSA[1]),
    new SignatureOid(X509Cert.SIG_MLDSA87, OID_MLDSA[2]),
  };

  /** Extended key usage OIDs, indexed by Go's x509.ExtKeyUsage value. */
  private static final int[][] EXT_KEY_USAGES = {
    {2, 5, 29, 37, 0},
    {1, 3, 6, 1, 5, 5, 7, 3, 1},
    {1, 3, 6, 1, 5, 5, 7, 3, 2},
    {1, 3, 6, 1, 5, 5, 7, 3, 3},
    {1, 3, 6, 1, 5, 5, 7, 3, 4},
    {1, 3, 6, 1, 5, 5, 7, 3, 5},
    {1, 3, 6, 1, 5, 5, 7, 3, 6},
    {1, 3, 6, 1, 5, 5, 7, 3, 7},
    {1, 3, 6, 1, 5, 5, 7, 3, 8},
    {1, 3, 6, 1, 5, 5, 7, 3, 9},
    {1, 3, 6, 1, 4, 1, 311, 10, 3, 3},
    {2, 16, 840, 1, 113730, 4, 1},
    {1, 3, 6, 1, 4, 1, 311, 2, 1, 22},
    {1, 3, 6, 1, 4, 1, 311, 61, 1, 1},
  };

  private X509Parser() {}

  /** An AlgorithmIdentifier; {@code params} is the full parameters element, or empty if absent. */
  private record AlgorithmIdentifier(int[] algorithm, byte[] params) {}

  private static <T> T req(T value, String goError) {
    return X509Der.req(value, goError);
  }

  private static BuiltinError fail(String goError) {
    return new BuiltinError(goError);
  }

  /** Reads the SEQUENCE an extension value starts with. */
  private static X509Der sequence(byte[] value, String goError) {
    return req(new X509Der(value).read(SEQUENCE), goError);
  }

  private static int ctx(int tag) {
    return X509Der.contextSpecific(tag);
  }

  private static int ctxConstructed(int tag) {
    return X509Der.contextSpecificConstructed(tag);
  }

  /** The index of {@code oid} in {@code oids}, or -1. */
  private static int indexOf(int[][] oids, int[] oid) {
    for (int i = 0; i < oids.length; i++) {
      if (Arrays.equals(oids[i], oid)) {
        return i;
      }
    }
    return -1;
  }

  /** Parses one certificate starting at {@code offset}; trailing data is left for the caller. */
  static X509Cert parse(byte[] buf, int offset) {
    X509Cert cert = new X509Cert();
    X509Der input = req(new X509Der(buf, offset, buf.length).readElement(SEQUENCE), "x509: malformed certificate");
    cert.raw = input.bytes();
    input = input.read(SEQUENCE);

    X509Der tbsElement = req(input.readElement(SEQUENCE), "x509: malformed tbs certificate");
    cert.rawTbsCertificate = tbsElement.bytes();
    X509Der tbs = tbsElement.read(SEQUENCE);

    boolean[] present = new boolean[1];
    X509Der versionDer = req(tbs.readOptional(ctxConstructed(0), present), "x509: malformed version");
    long version = 0;
    if (present[0]) {
      Long v = versionDer.readInt64();
      if (v == null || !versionDer.isEmpty() || v < 0) {
        throw fail("x509: malformed version");
      }
      version = v;
    }
    // Version is one-indexed, as in Go (including Go's wrap-around on overflow).
    version++;
    if (version > 3) {
      throw fail("x509: invalid version");
    }
    cert.version = version;

    cert.serialNumber = req(tbs.readBigInteger(), "x509: malformed serial number");
    if (cert.serialNumber.signum() < 0) {
      throw fail("x509: negative serial number");
    }

    X509Der sigAiElement = req(tbs.readElement(SEQUENCE), "x509: malformed signature algorithm identifier");
    cert.rawSignatureAlgorithm = sigAiElement.bytes();
    X509Der sigAi = sigAiElement.read(SEQUENCE);
    X509Der outerSigAi = req(input.read(SEQUENCE), "x509: malformed algorithm identifier");
    if (!Arrays.equals(outerSigAi.bytes(), sigAi.bytes())) {
      throw fail("x509: inner and outer signature algorithm identifiers don't match");
    }
    cert.signatureAlgorithm = signatureAlgorithm(parseAlgorithmIdentifier(sigAi));

    X509Der issuer = req(tbs.readElement(SEQUENCE), "x509: malformed issuer");
    cert.rawIssuer = issuer.bytes();
    fillName(cert.issuer, issuer);

    X509Der validity = req(tbs.read(SEQUENCE), "x509: malformed validity");
    cert.notBefore = readTime(validity);
    cert.notAfter = readTime(validity);

    // Go reports a malformed subject as a malformed issuer.
    X509Der subject = req(tbs.readElement(SEQUENCE), "x509: malformed issuer");
    cert.rawSubject = subject.bytes();
    fillName(cert.subject, subject);

    X509Der spkiElement = req(tbs.readElement(SEQUENCE), "x509: malformed spki");
    cert.rawSubjectPublicKeyInfo = spkiElement.bytes();
    X509Der spki = spkiElement.read(SEQUENCE);
    X509Der pkAiSeq = req(spki.read(SEQUENCE), "x509: malformed public key algorithm identifier");
    AlgorithmIdentifier pkAi = parseAlgorithmIdentifier(pkAiSeq);
    cert.publicKeyAlgorithm = publicKeyAlgorithm(pkAi.algorithm());
    X509Der.BitString spk = req(spki.readBitString(), "x509: malformed subjectPublicKey");
    if (cert.publicKeyAlgorithm != X509Cert.PK_UNKNOWN) {
      cert.publicKey = parsePublicKey(cert.publicKeyAlgorithm, pkAi, spk.rightAlign());
    }

    if (cert.version > 1) {
      if (!tbs.skipOptional(ctx(1))) {
        throw fail("x509: malformed issuerUniqueID");
      }
      if (!tbs.skipOptional(ctx(2))) {
        throw fail("x509: malformed subjectUniqueID");
      }
      if (cert.version == 3) {
        X509Der extensions = req(tbs.readOptional(ctxConstructed(3), present), "x509: malformed extensions");
        if (present[0]) {
          parseExtensions(cert, extensions);
        }
      }
    }

    cert.signature = req(input.readBitString(), "x509: malformed signature").rightAlign();
    return cert;
  }

  private static void parseExtensions(X509Cert cert, X509Der wrapper) {
    X509Der extensions = req(wrapper.read(SEQUENCE), "x509: malformed extensions");
    Set<String> seen = new HashSet<>();
    while (!extensions.isEmpty()) {
      X509Der der = req(extensions.read(SEQUENCE), "x509: malformed extension");
      int[] id = req(der.readOid(), "x509: malformed extension OID field");
      boolean critical = der.peekTag(X509Der.TAG_BOOLEAN) && req(der.readBoolean(), "x509: malformed extension critical field");
      X509Der value = req(der.read(X509Der.TAG_OCTET_STRING), "x509: malformed extension value field");
      String oid = Arrays.stream(id).mapToObj(String::valueOf).collect(Collectors.joining("."));
      if (!seen.add(oid)) {
        throw fail("x509: certificate contains duplicate extension with OID " + X509Strings.quote(oid));
      }
      cert.extensions = X509Cert.add(cert.extensions, new X509Cert.Extension(id, critical, value.bytes()));
    }
    if (cert.extensions != null) {
      for (X509Cert.Extension ext : cert.extensions) {
        processExtension(cert, ext);
      }
    }
  }

  private static void processExtension(X509Cert out, X509Cert.Extension e) {
    boolean unhandled = false;
    int[] id = e.id();
    byte[] value = e.value();
    if (id.length == 4 && id[0] == 2 && id[1] == 5 && id[2] == 29) {
      switch (id[3]) {
        case 15 -> out.keyUsage = parseKeyUsage(value);
        case 19 -> parseBasicConstraints(out, value);
        case 17 -> {
          parseSubjectAltNames(out, value);
          unhandled = out.dnsNames == null && out.emailAddresses == null && out.ipAddresses == null && out.uris == null;
        }
        case 30 -> unhandled = X509NameConstraints.parse(out, e);
        case 31 -> parseCrlDistributionPoints(out, value);
        case 35 -> {
          if (e.critical()) {
            throw fail("x509: authority key identifier incorrectly marked critical");
          }
          X509Der akid = sequence(value, "x509: invalid authority key identifier");
          if (akid.peekTag(ctx(0))) {
            out.authorityKeyId = req(akid.read(ctx(0)), "x509: invalid authority key identifier").bytes();
          }
        }
        case 36 -> parsePolicyConstraints(out, value);
        case 37 -> parseExtKeyUsage(out, value);
        case 14 -> {
          if (e.critical()) {
            throw fail("x509: subject key identifier incorrectly marked critical");
          }
          out.subjectKeyId = req(new X509Der(value).read(X509Der.TAG_OCTET_STRING), "x509: invalid subject key identifier").bytes();
        }
        case 32 -> parseCertificatePolicies(out, value);
        case 33 -> parsePolicyMappings(out, value);
        case 54 -> {
          out.inhibitAnyPolicy = req(new X509Der(value).readInt64(), "x509: invalid inhibit any policy extension");
          out.inhibitAnyPolicyZero = out.inhibitAnyPolicy == 0;
        }
        // Unknown extensions are recorded if critical.
        default -> unhandled = true;
      }
    } else if (Arrays.equals(id, OID_AIA)) {
      parseAuthorityInfoAccess(out, e);
    } else {
      unhandled = true;
    }
    if (e.critical() && unhandled) {
      out.unhandledCriticalExtensions = X509Cert.add(out.unhandledCriticalExtensions, id);
    }
  }

  private static long parseKeyUsage(byte[] value) {
    X509Der.BitString bits = req(new X509Der(value).readBitString(), "x509: invalid key usage");
    long usage = 0;
    for (int i = 0; i < 9; i++) {
      usage |= (long) bits.at(i) << i;
    }
    return usage;
  }

  private static void parseBasicConstraints(X509Cert out, byte[] value) {
    String error = "x509: invalid basic constraints";
    X509Der der = sequence(value, error);
    boolean isCa = der.peekTag(X509Der.TAG_BOOLEAN) && req(der.readBoolean(), error);
    long maxPathLen = -1;
    if (der.peekTag(X509Der.TAG_INTEGER)) {
      BigInteger mpl = der.readUint64();
      if (mpl == null || mpl.bitLength() > 63) {
        throw fail(error);
      }
      maxPathLen = mpl.longValue();
    }
    out.isCa = isCa;
    out.maxPathLen = maxPathLen;
    out.basicConstraintsValid = true;
    out.maxPathLenZero = maxPathLen == 0;
  }

  private static void parseSubjectAltNames(X509Cert out, byte[] value) {
    X509Der der = sequence(value, "x509: invalid subject alternative names");
    int[] tag = new int[1];
    while (!der.isEmpty()) {
      byte[] data = req(der.readAnyContents(tag), "x509: invalid subject alternative name").bytes();
      switch (tag[0] ^ 0x80) {
        case 1 -> out.emailAddresses = X509Cert.add(out.emailAddresses, sanString(data, "rfc822Name"));
        case 2 -> out.dnsNames = X509Cert.add(out.dnsNames, sanString(data, "dNSName"));
        case 6 -> out.uris = X509Cert.add(out.uris, parseUri(sanString(data, "uniformResourceIdentifier")));
        case 7 -> {
          if (data.length == 16 && X509Strings.isIpv4Mapped(data)) {
            throw fail("x509: SAN iPAddress contains IPv4-mapped IPv6 address");
          } else if (data.length != 16 && data.length != 4) {
            throw fail("x509: cannot parse IP address of length " + data.length);
          }
          out.ipAddresses = X509Cert.add(out.ipAddresses, data);
        }
        default -> {
          // Other name types are ignored, as in Go.
        }
      }
    }
  }

  private static String sanString(byte[] data, String type) {
    if (!X509Strings.isIa5(data)) {
      throw fail("x509: SAN " + type + " is malformed");
    }
    return X509Strings.latin1(data);
  }

  private static X509Url parseUri(String uri) {
    X509Url url;
    try {
      url = X509Url.parse(uri);
    } catch (X509Url.UrlException e) {
      throw fail("x509: cannot parse URI " + X509Strings.quote(uri) + ": " + e.getMessage());
    }
    if (!url.host.isEmpty() && !X509Strings.domainNameValid(url.host, false)) {
      throw fail("x509: cannot parse URI " + X509Strings.quote(uri) + ": invalid domain");
    }
    return url;
  }

  private static void parseCrlDistributionPoints(X509Cert out, byte[] value) {
    X509Der val = sequence(value, "x509: invalid CRL distribution points");
    String error = "x509: invalid CRL distribution point";
    boolean[] present = new boolean[1];
    while (!val.isEmpty()) {
      X509Der dp = req(val.read(SEQUENCE), error);
      X509Der dpName = req(dp.readOptional(ctxConstructed(0), present), error);
      if (present[0]) {
        dpName = req(dpName.read(ctxConstructed(0)), error);
        while (dpName.peekTag(ctx(6))) {
          byte[] uri = req(dpName.read(ctx(6)), error).bytes();
          out.crlDistributionPoints = X509Cert.add(out.crlDistributionPoints, X509Strings.latin1(uri));
        }
      }
    }
  }

  private static void parsePolicyConstraints(X509Cert out, byte[] value) {
    String error = "x509: invalid policy constraints extension";
    X509Der val = sequence(value, error);
    if (val.peekTag(ctx(0))) {
      out.requireExplicitPolicy = req(val.readInt64(ctx(0)), error);
      out.requireExplicitPolicyZero = out.requireExplicitPolicy == 0;
    }
    if (val.peekTag(ctx(1))) {
      out.inhibitPolicyMapping = req(val.readInt64(ctx(1)), error);
      out.inhibitPolicyMappingZero = out.inhibitPolicyMapping == 0;
    }
  }

  private static void parseExtKeyUsage(X509Cert out, byte[] value) {
    String error = "x509: invalid extended key usages";
    X509Der der = sequence(value, error);
    while (!der.isEmpty()) {
      int[] eku = req(der.readOid(), error);
      int usage = indexOf(EXT_KEY_USAGES, eku);
      if (usage >= 0) {
        out.extKeyUsage = X509Cert.add(out.extKeyUsage, (long) usage);
      } else {
        out.unknownExtKeyUsage = X509Cert.add(out.unknownExtKeyUsage, eku);
      }
    }
  }

  private static void parseCertificatePolicies(X509Cert out, byte[] value) {
    String error = "x509: invalid certificate policies";
    X509Der der = sequence(value, error);
    Set<String> seen = new HashSet<>();
    while (!der.isEmpty()) {
      X509Der cp = req(der.read(SEQUENCE), error);
      byte[] oid = req(cp.read(X509Der.TAG_OID), error).bytes();
      if (!seen.add(X509Strings.latin1(oid)) || !validOidDer(oid)) {
        throw fail(error);
      }
      out.policies = X509Cert.add(out.policies, oid);
    }
    // Go's OID.toASN1OID: identifiers with an arc wider than 31 bits are left out.
    out.policyIdentifiers = new ArrayList<>();
    for (byte[] oid : out.policies == null ? List.<byte[]>of() : out.policies) {
      int[] asn1 = new X509Der(oid).oidContents();
      if (asn1 != null) {
        out.policyIdentifiers.add(asn1);
      }
    }
  }

  private static void parsePolicyMappings(X509Cert out, byte[] value) {
    String error = "x509: invalid policy mappings extension";
    X509Der val = sequence(value, error);
    while (!val.isEmpty()) {
      X509Der s = req(val.read(SEQUENCE), error);
      byte[] issuer = req(s.read(X509Der.TAG_OID), error).bytes();
      byte[] subject = req(s.read(X509Der.TAG_OID), error).bytes();
      out.policyMappings = X509Cert.add(out.policyMappings, new byte[][] {issuer, subject});
    }
  }

  private static void parseAuthorityInfoAccess(X509Cert out, X509Cert.Extension e) {
    if (e.critical()) {
      throw fail("x509: authority info access incorrectly marked critical");
    }
    String error = "x509: invalid authority info access";
    X509Der val = sequence(e.value(), error);
    while (!val.isEmpty()) {
      X509Der aia = req(val.read(SEQUENCE), error);
      int[] method = req(aia.readOid(), error);
      if (aia.peekTag(ctx(6))) {
        String location = X509Strings.latin1(req(aia.read(ctx(6)), error).bytes());
        if (Arrays.equals(method, OID_AIA_OCSP)) {
          out.ocspServer = X509Cert.add(out.ocspServer, location);
        } else if (Arrays.equals(method, OID_AIA_ISSUERS)) {
          out.issuingCertificateUrl = X509Cert.add(out.issuingCertificateUrl, location);
        }
      }
    }
  }

  private static AlgorithmIdentifier parseAlgorithmIdentifier(X509Der der) {
    int[] algorithm = req(der.readOid(), "x509: malformed OID");
    if (der.isEmpty()) {
      return new AlgorithmIdentifier(algorithm, new byte[0]);
    }
    return new AlgorithmIdentifier(algorithm, req(der.readAnyElement(new int[1]), "x509: malformed parameters").bytes());
  }

  /** As {@link #parseAlgorithmIdentifier}, but returns null instead of throwing. */
  private static AlgorithmIdentifier algorithmIdentifierOrNull(X509Der der) {
    try {
      return der == null ? null : parseAlgorithmIdentifier(der);
    } catch (BuiltinError e) {
      return null;
    }
  }

  private static int signatureAlgorithm(AlgorithmIdentifier ai) {
    boolean noParamsAllowed = Arrays.equals(ai.algorithm(), OID_ED25519) || indexOf(OID_MLDSA, ai.algorithm()) >= 0;
    if (noParamsAllowed && ai.params().length != 0) {
      return X509Cert.SIG_UNKNOWN;
    }
    if (Arrays.equals(ai.algorithm(), OID_RSA_PSS)) {
      return rsaPssAlgorithm(ai.params());
    }
    for (SignatureOid details : SIGNATURE_OIDS) {
      if (Arrays.equals(details.oid(), ai.algorithm())) {
        return details.algorithm();
      }
    }
    return X509Cert.SIG_UNKNOWN;
  }

  /**
   * Go accepts RSA-PSS only with matching hash and MGF1 hash, a NULL or absent hash parameter, the
   * standard trailer field and a salt as long as the hash.
   */
  private static int rsaPssAlgorithm(byte[] params) {
    X509Der seq = new X509Der(params).read(SEQUENCE);
    AlgorithmIdentifier hash = seq == null ? null : explicitAlgorithmIdentifier(seq, 0);
    AlgorithmIdentifier mgf = hash == null ? null : explicitAlgorithmIdentifier(seq, 1);
    if (mgf == null) {
      return X509Cert.SIG_UNKNOWN;
    }
    Long saltLength = explicitInt64(seq, 2);
    Long trailerField = seq.peekTag(ctxConstructed(3)) ? explicitInt64(seq, 3) : Long.valueOf(1);
    AlgorithmIdentifier mgfHash = algorithmIdentifierOrNull(new X509Der(mgf.params()).read(SEQUENCE));
    if (saltLength == null
        || trailerField == null
        || mgfHash == null
        || hash.params().length != 0 && !Arrays.equals(hash.params(), ASN1_NULL)
        || !Arrays.equals(mgf.algorithm(), OID_MGF1)
        || !Arrays.equals(mgfHash.algorithm(), hash.algorithm())
        || mgfHash.params().length != 0 && !Arrays.equals(mgfHash.params(), ASN1_NULL)
        || trailerField != 1) {
      return X509Cert.SIG_UNKNOWN;
    }
    // SHA-256, SHA-384 and SHA-512 with salts of 32, 48 and 64 bytes.
    int i = indexOf(OID_PSS_HASHES, hash.algorithm());
    return i >= 0 && saltLength == 32 + 16 * i ? X509Cert.SIG_SHA256_RSAPSS + i : X509Cert.SIG_UNKNOWN;
  }

  private static AlgorithmIdentifier explicitAlgorithmIdentifier(X509Der seq, int tag) {
    X509Der wrapper = seq.read(ctxConstructed(tag));
    X509Der ai = wrapper == null ? null : wrapper.read(SEQUENCE);
    return ai == null || !wrapper.isEmpty() ? null : algorithmIdentifierOrNull(ai);
  }

  private static Long explicitInt64(X509Der seq, int tag) {
    X509Der wrapper = seq.read(ctxConstructed(tag));
    Long value = wrapper == null ? null : wrapper.readInt64();
    return value == null || !wrapper.isEmpty() ? null : value;
  }

  private static int publicKeyAlgorithm(int[] oid) {
    if (Arrays.equals(oid, OID_RSA)) {
      return X509Cert.PK_RSA;
    } else if (Arrays.equals(oid, OID_DSA)) {
      return X509Cert.PK_DSA;
    } else if (Arrays.equals(oid, OID_ECDSA)) {
      return X509Cert.PK_ECDSA;
    } else if (Arrays.equals(oid, OID_ED25519)) {
      return X509Cert.PK_ED25519;
    }
    return indexOf(OID_MLDSA, oid) >= 0 ? X509Cert.PK_MLDSA : X509Cert.PK_UNKNOWN;
  }

  private static Object parsePublicKey(int algorithm, AlgorithmIdentifier ai, byte[] data) {
    byte[] params = ai.params();
    switch (algorithm) {
      case X509Cert.PK_RSA -> {
        if (!Arrays.equals(params, ASN1_NULL)) {
          throw fail("x509: RSA key missing NULL parameters");
        }
        X509Der der = req(new X509Der(data).read(SEQUENCE), "x509: invalid RSA public key");
        BigInteger n = req(der.readBigInteger(), "x509: invalid RSA modulus");
        long e = req(der.readInt64(), "x509: invalid RSA public exponent");
        if (n.signum() <= 0) {
          throw fail("x509: RSA modulus is not a positive number");
        }
        if (e <= 0) {
          throw fail("x509: RSA public exponent is not a positive number");
        }
        return new X509Cert.RsaKey(n, e);
      }
      case X509Cert.PK_ECDSA -> {
        int[] curveOid = req(new X509Der(params).readOid(), "x509: invalid ECDSA parameters");
        return req(X509Curves.byOid(curveOid), "x509: unsupported elliptic curve").parseUncompressed(data);
      }
      case X509Cert.PK_ED25519 -> {
        if (params.length != 0) {
          throw fail("x509: Ed25519 key encoded with illegal parameters");
        }
        if (data.length != 32) {
          throw fail("x509: wrong Ed25519 public key size");
        }
        return new X509Cert.Ed25519Key(data);
      }
      case X509Cert.PK_MLDSA -> {
        if (params.length != 0) {
          throw fail("x509: ML-DSA key encoded with illegal parameters");
        }
        // ML-DSA-44, -65 and -87 have k = 4, 6 and 8; a public key is 32 + k * 320 bytes.
        int k = 4 + 2 * indexOf(OID_MLDSA, ai.algorithm());
        if (data.length != 32 + k * 256 * 10 / 8) {
          throw fail("mldsa: invalid public key length");
        }
        return new X509Cert.MlDsaKey();
      }
      default -> {
        BigInteger y = req(new X509Der(data).readBigInteger(), "x509: invalid DSA public key");
        X509Der der = new X509Der(params).read(SEQUENCE);
        BigInteger p = der == null ? null : der.readBigInteger();
        BigInteger q = p == null ? null : der.readBigInteger();
        BigInteger g = req(q == null ? null : der.readBigInteger(), "x509: invalid DSA parameters");
        if (y.signum() <= 0 || p.signum() <= 0 || q.signum() <= 0 || g.signum() <= 0) {
          throw fail("x509: zero or negative DSA parameter");
        }
        return new X509Cert.DsaKey(p, q, g, y);
      }
    }
  }

  private static X509Time readTime(X509Der der) {
    if (der.peekTag(X509Der.TAG_UTC_TIME)) {
      return req(der.readTime(X509Der.TAG_UTC_TIME), "x509: malformed UTCTime");
    } else if (der.peekTag(X509Der.TAG_GENERALIZED_TIME)) {
      return req(der.readTime(X509Der.TAG_GENERALIZED_TIME), "x509: malformed GeneralizedTime");
    }
    throw fail("x509: unsupported time format");
  }

  /** Go's parseName followed by pkix.Name.FillFromRDNSequence. */
  private static void fillName(X509Cert.Name name, X509Der element) {
    X509Der raw = req(element.read(SEQUENCE), "x509: invalid RDNSequence");
    while (!raw.isEmpty()) {
      X509Der set = req(raw.read(X509Der.TAG_SET), "x509: invalid RDNSequence");
      while (!set.isEmpty()) {
        X509Der atv = req(set.read(SEQUENCE), "x509: invalid RDNSequence: invalid attribute");
        int[] type = req(atv.readOid(), "x509: invalid RDNSequence: invalid attribute type");
        Object value = readAttributeValue(atv);
        name.names = X509Cert.add(name.names, new X509Cert.AttributeTypeAndValue(type, value));
        if (value instanceof String s && type.length == 4 && type[0] == 2 && type[1] == 5 && type[2] == 4) {
          switch (type[3]) {
            case 3 -> name.commonName = s;
            case 5 -> name.serialNumber = s;
            case 6 -> name.country = X509Cert.add(name.country, s);
            case 7 -> name.locality = X509Cert.add(name.locality, s);
            case 8 -> name.province = X509Cert.add(name.province, s);
            case 9 -> name.streetAddress = X509Cert.add(name.streetAddress, s);
            case 10 -> name.organization = X509Cert.add(name.organization, s);
            case 11 -> name.organizationalUnit = X509Cert.add(name.organizationalUnit, s);
            case 17 -> name.postalCode = X509Cert.add(name.postalCode, s);
            default -> {
              // Other attribute types only appear in Names.
            }
          }
        }
      }
    }
  }

  private static <T> T attribute(T value, String goError) {
    return req(value, BAD_ATTRIBUTE + goError);
  }

  /** Go's x509 readASN1Any; the value types are those X509Values encodes. */
  private static Object readAttributeValue(X509Der der) {
    int[] tag = new int[1];
    X509Der full = attribute(der.readAnyElement(tag), "invalid ASN.1 element");
    int t = tag[0];
    return switch (t) {
      case X509Der.TAG_T61_STRING, X509Der.TAG_PRINTABLE_STRING, X509Der.TAG_UTF8_STRING, X509Der.TAG_BMP_STRING,
          X509Der.TAG_IA5_STRING, X509Der.TAG_NUMERIC_STRING -> parseString(t, full.read(t).bytes());
      case X509Der.TAG_INTEGER -> attribute(full.readInt64(), "invalid ASN.1 integer");
      case X509Der.TAG_BIT_STRING -> attribute(full.readBitString(), "invalid ASN.1 BIT STRING");
      case X509Der.TAG_OCTET_STRING -> full.read(X509Der.TAG_OCTET_STRING).bytes();
      case X509Der.TAG_OID -> attribute(full.readOid(), "invalid ASN.1 OBJECT IDENTIFIER");
      case X509Der.TAG_UTC_TIME -> attribute(full.readTime(t), "x509: malformed UTCTime");
      case X509Der.TAG_GENERALIZED_TIME -> attribute(full.readTime(t), "x509: malformed GeneralizedTime");
      case X509Der.TAG_BOOLEAN -> attribute(full.readBoolean(), "invalid ASN.1 BOOLEAN");
      case X509Der.TAG_NULL -> null;
      default -> {
        byte[] fullBytes = full.bytes();
        byte[] contents = new X509Der(fullBytes).readAnyContents(new int[1]).bytes();
        yield new X509Cert.RawValue(t >> 6, t & 0x1f, (t & 0x20) == 0x20, contents, fullBytes);
      }
    };
  }

  /** Go's x509 parseASN1String. */
  private static String parseString(int tag, byte[] value) {
    return switch (tag) {
      // T61String is treated as Latin-1, as Go and BoringSSL do.
      case X509Der.TAG_T61_STRING -> new String(value, StandardCharsets.ISO_8859_1);
      case X509Der.TAG_PRINTABLE_STRING -> ascii(value, X509Parser::isPrintable, "invalid PrintableString");
      case X509Der.TAG_UTF8_STRING -> utf8(value);
      case X509Der.TAG_BMP_STRING -> parseBmpString(value);
      case X509Der.TAG_IA5_STRING -> ascii(value, b -> b < 0x80, "invalid IA5String");
      default -> ascii(value, b -> b >= '0' && b <= '9' || b == ' ', "invalid NumericString");
    };
  }

  private static String ascii(byte[] value, IntPredicate allowed, String goError) {
    for (byte b : value) {
      if (!allowed.test(b & 0xff)) {
        throw fail(BAD_ATTRIBUTE + goError);
      }
    }
    return new String(value, StandardCharsets.US_ASCII);
  }

  private static String utf8(byte[] value) {
    try {
      return StandardCharsets.UTF_8
          .newDecoder()
          .onMalformedInput(CodingErrorAction.REPORT)
          .onUnmappableCharacter(CodingErrorAction.REPORT)
          .decode(ByteBuffer.wrap(value))
          .toString();
    } catch (CharacterCodingException e) {
      throw fail(BAD_ATTRIBUTE + "invalid UTF-8 string");
    }
  }

  private static String parseBmpString(byte[] value) {
    int len = value.length;
    if (len % 2 != 0) {
      throw fail(BAD_ATTRIBUTE + "invalid BMPString");
    }
    if (len >= 2 && value[len - 1] == 0 && value[len - 2] == 0) {
      len -= 2;
    }
    StringBuilder sb = new StringBuilder(len / 2);
    for (int i = 0; i < len; i += 2) {
      int point = (value[i] & 0xff) << 8 | (value[i + 1] & 0xff);
      if (point == 0xfffe || point == 0xffff || point >= 0xfdd0 && point <= 0xfdef || point >= 0xd800 && point <= 0xdfff) {
        throw fail(BAD_ATTRIBUTE + "invalid BMPString");
      }
      sb.append((char) point);
    }
    return sb.toString();
  }

  private static boolean isPrintable(int b) {
    return b >= 'a' && b <= 'z'
        || b >= 'A' && b <= 'Z'
        || b >= '0' && b <= '9'
        || b >= '\'' && b <= ')'
        || b >= '+' && b <= '/'
        || " :=?*&".indexOf(b) >= 0;
  }

  /** Go's newOIDFromDER validity check for x509.OID contents. */
  private static boolean validOidDer(byte[] der) {
    if (der.length == 0 || (der[der.length - 1] & 0x80) != 0) {
      return false;
    }
    int start = 0;
    for (int i = 0; i < der.length; i++) {
      if (i == start && (der[i] & 0xff) == 0x80) {
        return false;
      }
      if ((der[i] & 0x80) == 0) {
        start = i + 1;
      }
    }
    return true;
  }

  /** Go's x509.OID.String over OID contents, supporting arcs of any size. */
  static String oidDerString(byte[] der) {
    StringBuilder sb = new StringBuilder();
    BigInteger val = BigInteger.ZERO;
    for (byte b : der) {
      val = val.shiftLeft(7).or(BigInteger.valueOf(b & 0x7f));
      if ((b & 0x80) == 0) {
        if (sb.length() > 0) {
          sb.append('.').append(val);
        } else if (val.compareTo(BigInteger.valueOf(80)) < 0) {
          sb.append(val.intValue() / 40).append('.').append(val.intValue() % 40);
        } else {
          sb.append("2.").append(val.subtract(BigInteger.valueOf(80)));
        }
        val = BigInteger.ZERO;
      }
    }
    return sb.toString();
  }
}
