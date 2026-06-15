package io.github.open_policy_agent.opa.tls;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PEM parsing utilities for X.509 certificates and private keys, using only the JDK standard
 * library.
 *
 * <p>Supports PKCS#8 PEM ({@code -----BEGIN PRIVATE KEY-----}) keys with any algorithm the JDK
 * KeyFactory recognises (RSA, EC, DSA). Encrypted PKCS#8 ({@code BEGIN ENCRYPTED PRIVATE KEY}),
 * PKCS#1 RSA ({@code BEGIN RSA PRIVATE KEY}), and SEC1 EC ({@code BEGIN EC PRIVATE KEY}) are
 * <em>not</em> supported — convert to unencrypted PKCS#8 first, or supply credentials via a JKS /
 * PKCS#12 keystore (see {@code credentials.client_tls.keystore}).
 */
public final class PemLoader {

  private static final Pattern PEM_BLOCK =
      Pattern.compile(
          "-----BEGIN ([A-Z0-9 ]+?)-----\\s*([A-Za-z0-9+/=\\r\\n\\s]+?)\\s*-----END \\1-----");

  private PemLoader() {}

  /**
   * Load all X.509 certificates from a PEM file (cert chain).
   *
   * @param path path to a PEM file containing one or more {@code CERTIFICATE} blocks
   * @return the certificates, in file order
   */
  public static List<X509Certificate> loadCertificates(Path path) throws IOException {
    return parseCertificates(Files.readAllBytes(path), path.toString());
  }

  static List<X509Certificate> parseCertificates(byte[] data, String source) throws IOException {
    List<X509Certificate> certs = new ArrayList<>();
    CertificateFactory factory;
    try {
      factory = CertificateFactory.getInstance("X.509");
    } catch (CertificateException e) {
      throw new IOException("X.509 CertificateFactory unavailable", e);
    }

    Matcher m = PEM_BLOCK.matcher(new String(data, StandardCharsets.UTF_8));
    while (m.find()) {
      String label = m.group(1).trim();
      if (!label.equals("CERTIFICATE")) {
        continue;
      }
      byte[] der = decodeBase64(m.group(2), source);
      try {
        Collection<? extends java.security.cert.Certificate> parsed =
            factory.generateCertificates(new java.io.ByteArrayInputStream(der));
        for (java.security.cert.Certificate c : parsed) {
          certs.add((X509Certificate) c);
        }
      } catch (CertificateException e) {
        throw new IOException(
            "Failed to parse certificate in " + source + ": " + e.getMessage(), e);
      }
    }
    if (certs.isEmpty()) {
      throw new IOException("No CERTIFICATE blocks found in " + source);
    }
    return certs;
  }

  /**
   * Load an unencrypted PKCS#8 private key from a PEM file.
   *
   * @param path path to a PEM file
   * @param passphrase reserved for future use; encrypted PEM keys are not supported by this
   *     loader. If non-null, an {@link IOException} is thrown so misconfiguration surfaces early
   *     rather than silently ignoring the passphrase.
   * @return the parsed private key
   */
  public static PrivateKey loadPrivateKey(Path path, char[] passphrase) throws IOException {
    return parsePrivateKey(Files.readAllBytes(path), passphrase, path.toString());
  }

  static PrivateKey parsePrivateKey(byte[] data, char[] passphrase, String source)
      throws IOException {
    Matcher m = PEM_BLOCK.matcher(new String(data, StandardCharsets.UTF_8));
    while (m.find()) {
      String label = m.group(1).trim();
      switch (label) {
        case "PRIVATE KEY":
          if (passphrase != null) {
            throw new IOException(
                "Key in "
                    + source
                    + " is unencrypted PKCS#8 but a passphrase was supplied. Either remove the"
                    + " passphrase or supply credentials via a JKS / PKCS#12 keystore.");
          }
          return decodePkcs8(decodeBase64(m.group(2), source), source);
        case "ENCRYPTED PRIVATE KEY":
          throw new IOException(
              "Encrypted PEM private keys are not supported in "
                  + source
                  + ". Convert to unencrypted PKCS#8 (openssl pkcs8 -topk8 -nocrypt) or use a"
                  + " JKS / PKCS#12 keystore via credentials.client_tls.keystore.");
        case "RSA PRIVATE KEY":
        case "EC PRIVATE KEY":
          throw new IOException(
              "PKCS#1 / SEC1 PEM private keys are not supported in "
                  + source
                  + " (found '"
                  + label
                  + "'). Convert to PKCS#8 with"
                  + " 'openssl pkcs8 -topk8 -nocrypt -in key.pem -out key-pkcs8.pem'.");
        default:
          // Skip unrelated blocks (e.g. CERTIFICATE) when key+chain share a file.
      }
    }
    throw new IOException("No private-key PEM block found in " + source);
  }

  private static PrivateKey decodePkcs8(byte[] der, String source) throws IOException {
    PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(der);
    // Try the algorithms KeyFactory commonly supports. The DER itself encodes the algorithm OID,
    // so KeyFactory.generatePrivate validates it; we just need to pick the right factory.
    for (String alg : new String[] {"RSA", "EC", "DSA"}) {
      try {
        return KeyFactory.getInstance(alg).generatePrivate(spec);
      } catch (InvalidKeySpecException ignored) {
        // try next
      } catch (GeneralSecurityException e) {
        throw new IOException("Failed to load private key from " + source + ": " + e.getMessage(), e);
      }
    }
    throw new IOException(
        "Unsupported private-key algorithm in " + source + " (expected RSA, EC, or DSA PKCS#8)");
  }

  private static byte[] decodeBase64(String body, String source) throws IOException {
    try {
      return Base64.getMimeDecoder().decode(body);
    } catch (IllegalArgumentException e) {
      throw new IOException("Malformed base64 in PEM block in " + source + ": " + e.getMessage(), e);
    }
  }
}
