package io.github.open_policy_agent.opa.tls;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PemLoaderTest {

  @TempDir static Path dir;
  static TlsFixtures fx;

  @BeforeAll
  static void setup() throws Exception {
    fx = TlsFixtures.generate(dir);
  }

  @Test
  void loadCertificates_chain() throws IOException {
    List<X509Certificate> certs = PemLoader.loadCertificates(fx.client);
    assertEquals(1, certs.size());
    assertTrue(certs.get(0).getSubjectX500Principal().getName().contains("opa-test-client"));
  }

  @Test
  void loadCertificates_emptyFile_throws() throws IOException {
    Path empty = Files.createTempFile(dir, "empty", ".pem");
    IOException e = assertThrows(IOException.class, () -> PemLoader.loadCertificates(empty));
    assertTrue(e.getMessage().contains("No CERTIFICATE blocks"));
  }

  @Test
  void loadPrivateKey_pkcs8Unencrypted() throws IOException {
    PrivateKey key = PemLoader.loadPrivateKey(fx.clientKey, null);
    assertNotNull(key);
    assertEquals("RSA", key.getAlgorithm());
  }

  @Test
  void loadPrivateKey_encryptedPkcs8_throwsWithGuidance() {
    // Encrypted PEM keys are intentionally unsupported — users are expected to convert to
    // unencrypted PKCS#8 or supply a JKS / PKCS#12 keystore via credentials.client_tls.keystore.
    IOException e =
        assertThrows(
            IOException.class,
            () -> PemLoader.loadPrivateKey(fx.clientKeyEncrypted, fx.clientKeyPassphrase.toCharArray()));
    assertTrue(
        e.getMessage().contains("Encrypted PEM private keys are not supported"),
        "expected encrypted-key guidance, got: " + e.getMessage());
  }

  @Test
  void loadPrivateKey_passphraseOnUnencrypted_throws() {
    // A passphrase supplied for an unencrypted key is almost certainly a misconfiguration —
    // refuse it loudly so a stale passphrase doesn't silently get ignored.
    IOException e =
        assertThrows(
            IOException.class,
            () -> PemLoader.loadPrivateKey(fx.clientKey, "ignored".toCharArray()));
    assertTrue(e.getMessage().contains("unencrypted PKCS#8 but a passphrase was supplied"));
  }

  @Test
  void loadPrivateKey_pkcs1Rsa_throwsWithConversionHint() throws IOException, InterruptedException {
    Path pkcs1 = dir.resolve("pkcs1.pem");
    int rc1 =
        new ProcessBuilder(
                "openssl", "rsa", "-in", fx.clientKey.toString(), "-out", pkcs1.toString())
            .redirectErrorStream(true)
            .start()
            .waitFor();
    if (!Files.isReadable(pkcs1)
        || rc1 != 0
        || !Files.readString(pkcs1).contains("BEGIN RSA PRIVATE KEY")) {
      new ProcessBuilder(
              "openssl",
              "rsa",
              "-traditional",
              "-in",
              fx.clientKey.toString(),
              "-out",
              pkcs1.toString())
          .redirectErrorStream(true)
          .start()
          .waitFor();
    }
    String header = Files.readString(pkcs1);
    assertTrue(
        header.contains("BEGIN RSA PRIVATE KEY"),
        "expected PKCS#1 key but got:\n" + header.substring(0, Math.min(header.length(), 120)));

    IOException e = assertThrows(IOException.class, () -> PemLoader.loadPrivateKey(pkcs1, null));
    assertTrue(
        e.getMessage().contains("Convert to PKCS#8"),
        "expected conversion hint, got: " + e.getMessage());
  }

  @Test
  void loadPrivateKey_malformed_throws() throws IOException {
    Path bad = Files.createTempFile(dir, "bad", ".pem");
    Files.writeString(bad, "-----BEGIN PRIVATE KEY-----\nnot-real-base64!!!\n-----END PRIVATE KEY-----\n");
    assertThrows(Exception.class, () -> PemLoader.loadPrivateKey(bad, null));
  }
}
