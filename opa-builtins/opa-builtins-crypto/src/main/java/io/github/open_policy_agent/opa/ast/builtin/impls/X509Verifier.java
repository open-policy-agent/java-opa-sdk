// This file contains a Java translation of code from the Go project:
//   crypto/x509 (verify.go, cert_pool.go), Copyright 2011 The Go Authors.
//   crypto/x509 (x509.go), Copyright 2009 The Go Authors.
//   crypto/internal/fips140/rsa (rsa.go, pkcs1v15.go), Copyright 2009-2024 The Go Authors.
// That code is used under the BSD-style license reproduced in
// src/main/resources/META-INF/licenses/LICENSE-golang.txt (Copyright 2009 The Go Authors).
// The translation and the rest of this file are licensed under the Apache License 2.0.
package io.github.open_policy_agent.opa.ast.builtin.impls;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.PSSParameterSpec;
import java.security.spec.RSAPublicKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import javax.crypto.Cipher;

/**
 * Verifies a certificate chain the way OPA's {@code crypto.x509.parse_and_verify_certificates}
 * does: the first certificate is the only trust anchor, the last is the leaf, and everything in
 * between may be used as an intermediate. Chain building and checks are ported from Go 1.27's
 * {@code x509.Certificate.Verify} with empty {@code VerifyOptions}, which notably means the leaf
 * must be usable for server authentication, SHA-1 and MD5 signatures are rejected, and the root's
 * own signature is not checked. Signatures themselves are verified with the JCA.
 */
final class X509Verifier {

  private static final int[] OID_SUBJECT_ALT_NAME = {2, 5, 29, 17};
  private static final int MAX_SIGNATURE_CHECKS = 100;

  private static final BigInteger ED25519_ORDER =
      new BigInteger("1000000000000000000000000000000014def9dea2f79cd65812631a5cf5d3ed", 16);
  private static final BigInteger ED25519_P = BigInteger.ONE.shiftLeft(255).subtract(BigInteger.valueOf(19));
  /** DER DigestInfo prefixes of EMSA-PKCS1-v1_5 (RFC 8017, 9.2, note 1), with NULL parameters. */
  private static final byte[] DIGEST_INFO_SHA256 = HexFormat.of().parseHex("3031300d060960864801650304020105000420");
  private static final byte[] DIGEST_INFO_SHA384 = HexFormat.of().parseHex("3041300d060960864801650304020205000430");
  private static final byte[] DIGEST_INFO_SHA512 = HexFormat.of().parseHex("3051300d060960864801650304020305000440");
  /** DER prefix of an Ed25519 SubjectPublicKeyInfo (RFC 8410), followed by the 32-byte key. */
  private static final byte[] ED25519_SPKI_PREFIX = {
    0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70, 0x03, 0x21, 0x00
  };

  private final List<X509Cert> roots;
  private final List<X509Cert> intermediates;
  private final Instant now;
  private int signatureChecks;

  private X509Verifier(List<X509Cert> roots, List<X509Cert> intermediates, Instant now) {
    this.roots = roots;
    this.intermediates = intermediates;
    this.now = now;
  }

  /**
   * Returns the first verified chain (leaf first), or null when the certificates do not form a
   * valid chain at {@code now}.
   */
  static List<X509Cert> verify(List<X509Cert> certs, Instant now) {
    if (certs.size() < 2) {
      return null;
    }
    List<X509Cert> roots = List.of(certs.get(0));
    // Go's CertPool.AddCert ignores a certificate that is already in the pool.
    List<X509Cert> intermediates = new ArrayList<>();
    Set<ByteBuffer> seen = new HashSet<>();
    for (int i = 1; i < certs.size() - 1; i++) {
      if (seen.add(ByteBuffer.wrap(certs.get(i).raw))) {
        intermediates.add(certs.get(i));
      }
    }
    X509Cert leaf = certs.get(certs.size() - 1);
    return new X509Verifier(roots, intermediates, now).verifyLeaf(leaf);
  }

  private List<X509Cert> verifyLeaf(X509Cert leaf) {
    if (!isValid(leaf, CertType.LEAF, List.of())) {
      return null;
    }
    List<List<X509Cert>> candidates =
        Arrays.equals(roots.get(0).raw, leaf.raw) ? List.of(List.of(leaf)) : buildChains(leaf, List.of(leaf)).chains();
    for (List<X509Cert> chain : candidates) {
      if (X509Policies.valid(chain) && chainAllowsServerAuth(chain) && X509ChainConstraints.chainSatisfiesConstraints(chain)) {
        return chain;
      }
    }
    return null;
  }

  private enum CertType {
    LEAF,
    INTERMEDIATE,
    ROOT
  }

  /** The outcome of Go's buildChains: the chains found, and whether the check budget ran out. */
  private record BuildResult(List<List<X509Cert>> chains, boolean signatureLimitReached) {}

  private BuildResult buildChains(X509Cert cert, List<X509Cert> currentChain) {
    List<List<X509Cert>> chains = new ArrayList<>();
    // Go stops building once too many signatures have been checked, here or below.
    boolean limitReached = false;
    List<X509Cert> rootCandidates = findPotentialParents(roots, cert);
    List<X509Cert> intermediateCandidates = findPotentialParents(intermediates, cert);
    for (int pass = 0; pass < 2 && !limitReached; pass++) {
      CertType type = pass == 0 ? CertType.ROOT : CertType.INTERMEDIATE;
      for (X509Cert candidate : pass == 0 ? rootCandidates : intermediateCandidates) {
        signatureChecks++;
        if (signatureChecks > MAX_SIGNATURE_CHECKS) {
          limitReached = true;
          break;
        }
        if (candidate.publicKey == null || alreadyInChain(candidate, currentChain)) {
          continue;
        }
        if (!checkSignatureFrom(cert, candidate)) {
          continue;
        }
        if (!isValid(candidate, type, currentChain)) {
          continue;
        }
        List<X509Cert> extended = new ArrayList<>(currentChain);
        extended.add(candidate);
        if (type == CertType.ROOT) {
          chains.add(extended);
        } else {
          BuildResult child = buildChains(candidate, extended);
          chains.addAll(child.chains());
          limitReached = child.signatureLimitReached();
          if (limitReached) {
            break;
          }
        }
      }
    }
    return new BuildResult(chains, chains.isEmpty() && limitReached);
  }

  /**
   * Go's CertPool.findPotentialParents: certificates whose subject is the issuer of {@code cert},
   * ordered so that matching key identifiers are tried first.
   */
  private static List<X509Cert> findPotentialParents(List<X509Cert> pool, X509Cert cert) {
    List<X509Cert> matching = new ArrayList<>();
    List<X509Cert> oneKeyId = new ArrayList<>();
    List<X509Cert> mismatch = new ArrayList<>();
    byte[] akid = cert.authorityKeyId == null ? new byte[0] : cert.authorityKeyId;
    for (X509Cert candidate : pool) {
      if (!Arrays.equals(candidate.rawSubject, cert.rawIssuer)) {
        continue;
      }
      byte[] skid = candidate.subjectKeyId == null ? new byte[0] : candidate.subjectKeyId;
      if (Arrays.equals(skid, akid)) {
        matching.add(candidate);
      } else if (skid.length == 0 || akid.length == 0) {
        oneKeyId.add(candidate);
      } else {
        mismatch.add(candidate);
      }
    }
    matching.addAll(oneKeyId);
    matching.addAll(mismatch);
    return matching;
  }

  /**
   * Go's alreadyInChain: a candidate with the same subject, public key and SAN extension as a
   * certificate already in the chain would create a loop.
   */
  private static boolean alreadyInChain(X509Cert candidate, List<X509Cert> chain) {
    X509Cert.Extension candidateSan = candidate.extension(OID_SUBJECT_ALT_NAME);
    for (X509Cert cert : chain) {
      if (!Arrays.equals(candidate.rawSubject, cert.rawSubject)
          || !Arrays.equals(candidate.rawSubjectPublicKeyInfo, cert.rawSubjectPublicKeyInfo)) {
        continue;
      }
      X509Cert.Extension certSan = cert.extension(OID_SUBJECT_ALT_NAME);
      if (candidateSan == null && certSan == null) {
        return true;
      } else if (candidateSan == null || certSan == null) {
        return false;
      }
      if (Arrays.equals(candidateSan.value(), certSan.value())) {
        return true;
      }
    }
    return false;
  }

  /** Go's Certificate.isValid for the given role and chain built so far. */
  private boolean isValid(X509Cert c, CertType type, List<X509Cert> currentChain) {
    if (c.unhandledCriticalExtensions != null) {
      return false;
    }
    if (!currentChain.isEmpty() && !Arrays.equals(currentChain.get(currentChain.size() - 1).rawIssuer, c.rawSubject)) {
      return false;
    }
    if (now.isBefore(c.notBefore.instant()) || now.isAfter(c.notAfter.instant())) {
      return false;
    }
    if (type == CertType.INTERMEDIATE && (!c.basicConstraintsValid || !c.isCa)) {
      return false;
    }
    return !(c.basicConstraintsValid && c.maxPathLen >= 0 && currentChain.size() - 1 > c.maxPathLen);
  }

  /**
   * Go's checkChainForKeyUsage with the default requested usage, server authentication: every
   * certificate that lists extended key usages must include it or anyExtendedKeyUsage.
   */
  private static boolean chainAllowsServerAuth(List<X509Cert> chain) {
    for (X509Cert cert : chain) {
      if (cert.extKeyUsage == null && cert.unknownExtKeyUsage == null) {
        continue;
      }
      List<Long> usages = cert.extKeyUsage == null ? List.of() : cert.extKeyUsage;
      if (!usages.contains((long) X509Cert.EKU_ANY) && !usages.contains((long) X509Cert.EKU_SERVER_AUTH)) {
        return false;
      }
    }
    return true;
  }

  /** Go's Certificate.CheckSignatureFrom (SHA-1 not allowed). */
  private static boolean checkSignatureFrom(X509Cert child, X509Cert parent) {
    if (parent.version == 3 && !parent.basicConstraintsValid || parent.basicConstraintsValid && !parent.isCa) {
      return false;
    }
    if (parent.keyUsage != 0 && (parent.keyUsage & X509Cert.KEY_USAGE_CERT_SIGN) == 0) {
      return false;
    }
    if (parent.publicKeyAlgorithm == X509Cert.PK_UNKNOWN) {
      return false;
    }
    try {
      return checkSignature(child.signatureAlgorithm, child.rawTbsCertificate, child.signature, parent);
    } catch (GeneralSecurityException | RuntimeException e) {
      return false;
    }
  }

  private static boolean checkSignature(int algorithm, byte[] signed, byte[] sig, X509Cert parent)
      throws GeneralSecurityException {
    // MD5 and SHA-1 are insecure, DSA is unsupported by Go, and ML-DSA needs Java 24+, so those fail.
    if (parent.publicKey instanceof X509Cert.RsaKey key) {
      return switch (algorithm) {
        case X509Cert.SIG_SHA256_RSA -> verifyPkcs1v15(key, "SHA-256", DIGEST_INFO_SHA256, signed, sig);
        case X509Cert.SIG_SHA384_RSA -> verifyPkcs1v15(key, "SHA-384", DIGEST_INFO_SHA384, signed, sig);
        case X509Cert.SIG_SHA512_RSA -> verifyPkcs1v15(key, "SHA-512", DIGEST_INFO_SHA512, signed, sig);
        case X509Cert.SIG_SHA256_RSAPSS -> verifyPss(key, "SHA-256", MGF1ParameterSpec.SHA256, 32, signed, sig);
        case X509Cert.SIG_SHA384_RSAPSS -> verifyPss(key, "SHA-384", MGF1ParameterSpec.SHA384, 48, signed, sig);
        case X509Cert.SIG_SHA512_RSAPSS -> verifyPss(key, "SHA-512", MGF1ParameterSpec.SHA512, 64, signed, sig);
        default -> false;
      };
    } else if (parent.publicKey instanceof X509Cert.EcKey key) {
      return switch (algorithm) {
        case X509Cert.SIG_ECDSA_SHA256 -> verifyEcdsa(key, "SHA256withECDSA", signed, sig);
        case X509Cert.SIG_ECDSA_SHA384 -> verifyEcdsa(key, "SHA384withECDSA", signed, sig);
        case X509Cert.SIG_ECDSA_SHA512 -> verifyEcdsa(key, "SHA512withECDSA", signed, sig);
        default -> false;
      };
    }
    return parent.publicKey instanceof X509Cert.Ed25519Key key && algorithm == X509Cert.SIG_ED25519 && verifyEd25519(key, signed, sig);
  }

  /** Verifies with a JCA signature algorithm (and optional parameters). */
  private static boolean verify(String algorithm, PSSParameterSpec params, PublicKey key, byte[] signed, byte[] sig)
      throws GeneralSecurityException {
    Signature verifier = Signature.getInstance(algorithm);
    if (params != null) {
      verifier.setParameter(params);
    }
    verifier.initVerify(key);
    verifier.update(signed);
    return verifier.verify(sig);
  }

  /** RSA-PSS verification with the public key checks Go's crypto/rsa applies before verifying. */
  private static boolean verifyPss(X509Cert.RsaKey key, String hash, MGF1ParameterSpec mgf, int saltLength, byte[] signed, byte[] sig)
      throws GeneralSecurityException {
    PSSParameterSpec params = new PSSParameterSpec(hash, "MGF1", mgf, saltLength, PSSParameterSpec.TRAILER_FIELD_BC);
    return acceptableRsaKey(key, sig) && verify("RSASSA-PSS", params, rsaPublicKey(key), signed, sig);
  }

  /**
   * RSASSA-PKCS1-v1_5 verification (RFC 8017, 8.2.2). The JDK's SHA*withRSA signatures also accept
   * a DigestInfo without the NULL hash parameters, which Go rejects, so the encoded message is
   * recovered with the raw RSA public key operation and compared with the one encoding Go accepts.
   */
  private static boolean verifyPkcs1v15(X509Cert.RsaKey key, String hash, byte[] prefix, byte[] signed, byte[] sig)
      throws GeneralSecurityException {
    if (!acceptableRsaKey(key, sig)) {
      return false;
    }
    Cipher rsa = Cipher.getInstance("RSA/ECB/NoPadding");
    rsa.init(Cipher.ENCRYPT_MODE, rsaPublicKey(key));
    byte[] em = rsa.doFinal(sig);
    byte[] digest = MessageDigest.getInstance(hash).digest(signed);
    int k = sig.length;
    int tLen = prefix.length + digest.length;
    if (k < tLen + 11 || em.length > k) {
      return false;
    }
    byte[] expected = new byte[k];
    expected[1] = 0x01;
    Arrays.fill(expected, 2, k - tLen - 1, (byte) 0xff);
    System.arraycopy(prefix, 0, expected, k - tLen, prefix.length);
    System.arraycopy(digest, 0, expected, k - digest.length, digest.length);
    // RSA/ECB/NoPadding may strip leading zero bytes from the result.
    byte[] padded = new byte[k];
    System.arraycopy(em, 0, padded, k - em.length, em.length);
    return MessageDigest.isEqual(expected, padded);
  }

  /** The public key checks of Go's crypto/rsa, and the signature length of RFC 8017. */
  private static boolean acceptableRsaKey(X509Cert.RsaKey key, byte[] sig) {
    return key.n().bitLength() >= 1024
        && key.n().testBit(0)
        && key.e() >= 2
        && (key.e() & 1) == 1
        && key.e() <= Integer.MAX_VALUE
        && sig.length == (key.n().bitLength() + 7) / 8;
  }

  private static PublicKey rsaPublicKey(X509Cert.RsaKey key) throws GeneralSecurityException {
    return KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(key.n(), BigInteger.valueOf(key.e())));
  }

  /** ECDSA verification; the signature must be strict DER, as Go's ecdsa.VerifyASN1 requires. */
  private static boolean verifyEcdsa(X509Cert.EcKey key, String algorithm, byte[] signed, byte[] sig)
      throws GeneralSecurityException {
    X509Der input = new X509Der(sig);
    X509Der inner = input.read(X509Der.TAG_SEQUENCE);
    if (inner == null
        || !input.isEmpty()
        || inner.readUnsignedIntegerBytes() == null
        || inner.readUnsignedIntegerBytes() == null
        || !inner.isEmpty()) {
      return false;
    }
    // Java 17's SunEC provider no longer supports P-224, so such signatures cannot be verified.
    if (X509Curves.P224.name().equals(key.curve())) {
      return false;
    }
    AlgorithmParameters curve = AlgorithmParameters.getInstance("EC");
    curve.init(new ECGenParameterSpec(X509Curves.byName(key.curve()).jcaName()));
    ECPublicKeySpec spec = new ECPublicKeySpec(new ECPoint(key.x(), key.y()), curve.getParameterSpec(ECParameterSpec.class));
    return verify(algorithm, null, KeyFactory.getInstance("EC").generatePublic(spec), signed, sig);
  }

  /**
   * Ed25519 verification. Go additionally requires a canonical scalar S and compares the encoding
   * of R, so non-canonical encodings are rejected up front.
   */
  private static boolean verifyEd25519(X509Cert.Ed25519Key key, byte[] signed, byte[] sig) throws GeneralSecurityException {
    if (sig.length != 64 || (sig[63] & 0xe0) != 0) {
      return false;
    }
    byte[] s = new byte[32];
    byte[] ry = new byte[32];
    for (int i = 0; i < 32; i++) {
      s[i] = sig[63 - i];
      ry[i] = sig[31 - i];
    }
    ry[0] &= 0x7f;
    if (new BigInteger(1, s).compareTo(ED25519_ORDER) >= 0 || new BigInteger(1, ry).compareTo(ED25519_P) >= 0) {
      return false;
    }
    byte[] spki = Arrays.copyOf(ED25519_SPKI_PREFIX, ED25519_SPKI_PREFIX.length + 32);
    System.arraycopy(key.key(), 0, spki, ED25519_SPKI_PREFIX.length, 32);
    return verify("Ed25519", null, KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(spki)), signed, sig);
  }
}
