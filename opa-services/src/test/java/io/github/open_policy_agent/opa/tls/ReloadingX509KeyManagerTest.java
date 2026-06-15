package io.github.open_policy_agent.opa.tls;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import io.github.open_policy_agent.opa.logging.Logger;

class ReloadingX509KeyManagerTest {

  @TempDir static Path dir;
  static TlsFixtures fx;

  private ScheduledExecutorService scheduler;

  @BeforeAll
  static void setupFixtures() throws Exception {
    fx = TlsFixtures.generate(dir);
  }

  @BeforeEach
  void setUp() {
    scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
      Thread t = new Thread(r, "reload-test");
      t.setDaemon(true);
      return t;
    });
  }

  @AfterEach
  void tearDown() throws Exception {
    scheduler.shutdownNow();
    scheduler.awaitTermination(2, TimeUnit.SECONDS);
  }

  @Test
  void reload_bytesUnchanged_delegateNotSwapped() throws Exception {
    Logger logger = mock(Logger.class);
    ReloadingX509KeyManager km =
        ReloadingX509KeyManager.create(
            fx.client, fx.clientKey, null, scheduler, /*interval*/ Duration.ofDays(365), logger, "svc");

    X509Certificate[] first = km.getCertificateChain("key");
    assertNotNull(first);

    km.reloadIfChanged(); // no bytes change → should be a no-op

    X509Certificate[] second = km.getCertificateChain("key");
    // The JDK key manager returns a fresh array per call, so assertSame on arrays would be
    // wrong. Assert the cert contents are unchanged.
    assertArrayEquals(
        first,
        second,
        "delegate's cert chain should be unchanged (delegate not rebuilt when bytes are unchanged)");

    // No info log, no error log — reload was a silent no-op.
    verifyNoMoreInteractions(logger);
  }

  @Test
  void reload_bytesChanged_delegateSwapped() throws Exception {
    // Make a writable copy of the fixture cert/key so we can rewrite them mid-test.
    Path certCopy = dir.resolve("client-mutable.pem");
    Path keyCopy = dir.resolve("client-mutable-key.pem");
    Files.copy(fx.client, certCopy);
    Files.copy(fx.clientKey, keyCopy);

    Logger logger = mock(Logger.class);
    ReloadingX509KeyManager km =
        ReloadingX509KeyManager.create(
            certCopy, keyCopy, null, scheduler, Duration.ofDays(365), logger, "svc");

    X509Certificate[] before = km.getCertificateChain("key");
    assertNotNull(before);

    // Generate a fresh client cert/key pair signed by the same CA, then overwrite the files.
    Path ca = fx.ca;
    Path caKey = fx.caKey;
    Path csr = dir.resolve("rotated.csr");
    runOpenssl(
        "openssl",
        "genpkey",
        "-algorithm",
        "RSA",
        "-out",
        keyCopy.toString(),
        "-pkeyopt",
        "rsa_keygen_bits:2048");
    runOpenssl(
        "openssl",
        "req",
        "-new",
        "-key",
        keyCopy.toString(),
        "-out",
        csr.toString(),
        "-subj",
        "/CN=opa-test-client-rotated");
    runOpenssl(
        "openssl",
        "x509",
        "-req",
        "-in",
        csr.toString(),
        "-CA",
        ca.toString(),
        "-CAkey",
        caKey.toString(),
        "-CAcreateserial",
        "-CAserial",
        dir.resolve("ca.srl").toString(),
        "-out",
        certCopy.toString(),
        "-days",
        "1");

    km.reloadIfChanged();

    X509Certificate[] after = km.getCertificateChain("key");
    assertNotNull(after);
    assertNotEquals(
        before[0].getSubjectX500Principal(),
        after[0].getSubjectX500Principal(),
        "subject should differ after rotation");

    verify(logger).info(eq("Service '%s': reloaded client TLS certificate"), eq("svc"));
  }

  @Test
  void reload_partialRotation_certOnly_delegateSwapped() throws Exception {
    // Verifies the comment at ReloadingX509KeyManager.reloadIfChanged: "Require BOTH hashes to
    // match before skipping. If only one file has been rewritten ... we need to rebuild." The
    // rebuild produces a mismatched cert+key pair (JCA doesn't validate this at build time, only
    // at handshake), but detecting the partial state is the contract being tested here.
    Path certCopy = dir.resolve("client-partial.pem");
    Path keyCopy = dir.resolve("client-partial-key.pem");
    Files.copy(fx.client, certCopy);
    Files.copy(fx.clientKey, keyCopy);

    Logger logger = mock(Logger.class);
    ReloadingX509KeyManager km =
        ReloadingX509KeyManager.create(
            certCopy, keyCopy, null, scheduler, Duration.ofDays(365), logger, "svc");

    X509Certificate[] before = km.getCertificateChain("key");
    assertNotNull(before);

    // Generate a fresh keypair signed by the same CA, write only the new cert (NOT the key).
    Path freshKey = dir.resolve("partial-fresh.key");
    Path csr = dir.resolve("partial.csr");
    runOpenssl(
        "openssl",
        "genpkey",
        "-algorithm",
        "RSA",
        "-out",
        freshKey.toString(),
        "-pkeyopt",
        "rsa_keygen_bits:2048");
    runOpenssl(
        "openssl",
        "req",
        "-new",
        "-key",
        freshKey.toString(),
        "-out",
        csr.toString(),
        "-subj",
        "/CN=opa-test-client-partial");
    runOpenssl(
        "openssl",
        "x509",
        "-req",
        "-in",
        csr.toString(),
        "-CA",
        fx.ca.toString(),
        "-CAkey",
        fx.caKey.toString(),
        "-CAcreateserial",
        "-CAserial",
        dir.resolve("ca.srl").toString(),
        "-out",
        certCopy.toString(),
        "-days",
        "1");

    km.reloadIfChanged();

    X509Certificate[] after = km.getCertificateChain("key");
    assertNotEquals(
        before[0].getSubjectX500Principal(),
        after[0].getSubjectX500Principal(),
        "rebuild must trigger when only the cert file changes; the next reload tick recovers"
            + " once the key file catches up");
  }

  @Test
  void reload_corruptedFile_delegateRetained() throws Exception {
    Path certCopy = dir.resolve("client-bad-test.pem");
    Path keyCopy = dir.resolve("client-bad-test-key.pem");
    Files.copy(fx.client, certCopy);
    Files.copy(fx.clientKey, keyCopy);

    Logger logger = mock(Logger.class);
    ReloadingX509KeyManager km =
        ReloadingX509KeyManager.create(
            certCopy, keyCopy, null, scheduler, Duration.ofDays(365), logger, "svc");

    X509Certificate[] before = km.getCertificateChain("key");
    assertNotNull(before);

    // Corrupt the cert file. The reload should fail internally, log an error, and keep the
    // previous valid delegate.
    Files.writeString(certCopy, "-----BEGIN CERTIFICATE-----\nnope\n-----END CERTIFICATE-----\n");

    km.reloadIfChanged();

    X509Certificate[] after = km.getCertificateChain("key");
    assertArrayEquals(
        before,
        after,
        "delegate must not be swapped when reload fails — old cert still serves handshakes");
    verify(logger, atLeastOnce())
        .error(
            eq("Service '%s': failed to reload client TLS certificate: %s"), eq("svc"), anyString());
  }

  private static void runOpenssl(String... command) throws Exception {
    ProcessBuilder pb = new ProcessBuilder(command).directory(dir.toFile()).redirectErrorStream(true);
    Process p = pb.start();
    byte[] out = p.getInputStream().readAllBytes();
    int rc = p.waitFor();
    if (rc != 0) {
      throw new RuntimeException(
          "openssl failed (rc=" + rc + "): " + Arrays.toString(command) + "\n" + new String(out));
    }
  }
}
