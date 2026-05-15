package io.github.open_policy_agent.opa.tls;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.PrivateKey;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMEncryptedKeyPair;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.openssl.jcajce.JceOpenSSLPKCS8DecryptorProviderBuilder;
import org.bouncycastle.openssl.jcajce.JcePEMDecryptorProviderBuilder;
import org.bouncycastle.operator.InputDecryptorProvider;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.pkcs.PKCS8EncryptedPrivateKeyInfo;
import org.bouncycastle.pkcs.PKCSException;

/**
 * PEM parsing utilities for X.509 certificates and private keys.
 *
 * <p>Backed by Bouncy Castle: handles PKCS#8 (encrypted and unencrypted), PKCS#1 RSA, SEC1 EC, and
 * legacy OpenSSL-style encrypted PEM ({@code Proc-Type: 4,ENCRYPTED}).
 */
public final class PemLoader {

  private static final BouncyCastleProvider BC_PROVIDER = new BouncyCastleProvider();

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
    JcaX509CertificateConverter converter = new JcaX509CertificateConverter();
    try (PEMParser parser = new PEMParser(reader(data))) {
      Object obj;
      while ((obj = parser.readObject()) != null) {
        if (obj instanceof X509CertificateHolder) {
          try {
            certs.add(converter.getCertificate((X509CertificateHolder) obj));
          } catch (CertificateException e) {
            throw new IOException(
                "Failed to parse certificate in " + source + ": " + e.getMessage(), e);
          }
        }
      }
    }
    if (certs.isEmpty()) {
      throw new IOException("No CERTIFICATE blocks found in " + source);
    }
    return certs;
  }

  /**
   * Load a private key from a PEM file.
   *
   * @param path path to a PEM file
   * @param passphrase passphrase for encrypted keys; ignored for unencrypted keys (pass {@code
   *     null} when the key is unencrypted)
   * @return the parsed private key
   */
  public static PrivateKey loadPrivateKey(Path path, char[] passphrase) throws IOException {
    return parsePrivateKey(Files.readAllBytes(path), passphrase, path.toString());
  }

  static PrivateKey parsePrivateKey(byte[] data, char[] passphrase, String source)
      throws IOException {
    JcaPEMKeyConverter converter = new JcaPEMKeyConverter();
    try (PEMParser parser = new PEMParser(reader(data))) {
      Object obj;
      while ((obj = parser.readObject()) != null) {
        try {
          if (obj instanceof PEMEncryptedKeyPair) {
            requirePassphrase(passphrase, source);
            PEMKeyPair kp =
                ((PEMEncryptedKeyPair) obj)
                    .decryptKeyPair(
                        new JcePEMDecryptorProviderBuilder()
                            .setProvider(BC_PROVIDER)
                            .build(passphrase));
            return converter.getKeyPair(kp).getPrivate();
          }
          if (obj instanceof PKCS8EncryptedPrivateKeyInfo) {
            requirePassphrase(passphrase, source);
            InputDecryptorProvider decryptor =
                new JceOpenSSLPKCS8DecryptorProviderBuilder()
                    .setProvider(BC_PROVIDER)
                    .build(passphrase);
            PrivateKeyInfo info =
                ((PKCS8EncryptedPrivateKeyInfo) obj).decryptPrivateKeyInfo(decryptor);
            return converter.getPrivateKey(info);
          }
          if (obj instanceof PEMKeyPair) {
            return converter.getKeyPair((PEMKeyPair) obj).getPrivate();
          }
          if (obj instanceof PrivateKeyInfo) {
            return converter.getPrivateKey((PrivateKeyInfo) obj);
          }
          // Anything else (e.g. an X509CertificateHolder when cert+key share a file) is skipped.
        } catch (OperatorCreationException | PKCSException e) {
          throw new IOException(
              "Failed to decrypt key in "
                  + source
                  + " (wrong passphrase or unsupported algorithm): "
                  + e.getMessage(),
              e);
        }
      }
    }
    throw new IOException("No private-key PEM block found in " + source);
  }

  private static void requirePassphrase(char[] passphrase, String source) throws IOException {
    if (passphrase == null) {
      throw new IOException(
          "Key in " + source + " is encrypted but no private_key_passphrase was provided");
    }
  }

  private static StringReader reader(byte[] data) {
    // PEM is ASCII-only by spec, but reading as UTF-8 tolerates a BOM or stray non-ASCII
    // bytes outside the base64 blocks (e.g. comments) without failing parsing.
    return new StringReader(new String(data, StandardCharsets.UTF_8));
  }
}
