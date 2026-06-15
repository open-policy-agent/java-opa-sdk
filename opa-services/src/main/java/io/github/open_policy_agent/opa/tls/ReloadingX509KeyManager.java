package io.github.open_policy_agent.opa.tls;

import java.io.IOException;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.X509ExtendedKeyManager;
import io.github.open_policy_agent.opa.logging.Logger;

/**
 * An {@link X509ExtendedKeyManager} that periodically re-reads its cert and private-key files and
 * rebuilds the underlying delegate when the on-disk bytes change.
 *
 * <p>Designed for short-lived certificates (e.g. cert-manager issues 24-hour certs) where the
 * deployment refreshes them in place. Polled reload, SHA-256 dedupe, parse only on change.
 *
 * <p>The key manager delegate is swapped atomically, so in-flight handshakes keep using the old
 * delegate and new handshakes pick up the new one.
 *
 * <p>Call {@link #close()} to cancel the reload schedule when the owning service is torn down or
 * its config is reloaded; otherwise the task continues until the scheduler itself shuts down.
 */
public final class ReloadingX509KeyManager extends X509ExtendedKeyManager implements AutoCloseable {

  private final Path certPath;
  private final Path keyPath;
  private final char[] keyPassphrase;
  private final Logger logger;
  private final String serviceName;

  private final AtomicReference<State> state = new AtomicReference<>();
  private final AtomicReference<ScheduledFuture<?>> reloadFuture = new AtomicReference<>();

  /**
   * Build and start a reloading key manager.
   *
   * @param certPath PEM cert-chain path
   * @param keyPath PEM PKCS#8 private-key path
   * @param keyPassphrase reserved; encrypted PEM keys are not supported (pass {@code null})
   * @param scheduler executor used to schedule periodic reloads
   * @param rereadInterval how often to check the on-disk bytes (must be positive)
   * @param logger logger for reload events
   * @param serviceName name of the owning service (for log messages)
   */
  public static ReloadingX509KeyManager create(
      Path certPath,
      Path keyPath,
      char[] keyPassphrase,
      ScheduledExecutorService scheduler,
      Duration rereadInterval,
      Logger logger,
      String serviceName)
      throws IOException, GeneralSecurityException {
    if (rereadInterval == null || rereadInterval.isNegative() || rereadInterval.isZero()) {
      throw new IllegalArgumentException("rereadInterval must be positive");
    }
    ReloadingX509KeyManager mgr =
        new ReloadingX509KeyManager(certPath, keyPath, keyPassphrase, logger, serviceName);
    mgr.loadOrThrow();
    long seconds = rereadInterval.getSeconds();
    if (seconds < 1) {
      seconds = 1;
    }
    ScheduledFuture<?> future =
        scheduler.scheduleAtFixedRate(mgr::reloadIfChanged, seconds, seconds, TimeUnit.SECONDS);
    mgr.reloadFuture.set(future);
    return mgr;
  }

  private ReloadingX509KeyManager(
      Path certPath, Path keyPath, char[] keyPassphrase, Logger logger, String serviceName) {
    this.certPath = certPath;
    this.keyPath = keyPath;
    this.keyPassphrase = keyPassphrase == null ? null : keyPassphrase.clone();
    this.logger = logger;
    this.serviceName = serviceName;
  }

  private void loadOrThrow() throws IOException, GeneralSecurityException {
    byte[] certBytes = Files.readAllBytes(certPath);
    byte[] keyBytes = Files.readAllBytes(keyPath);
    state.set(build(certBytes, keyBytes, sha256(certBytes), sha256(keyBytes)));
  }

  /**
   * Visible for testing. In production this is only called from the scheduler.
   *
   * <p>{@code synchronized} so that concurrent callers (e.g. two reload scheduler threads, or
   * a test invoking this directly while the scheduler also fires) can't see a partial-rotation
   * state where the cert file has been rewritten but the key file hasn't. Contention is
   * negligible since reloads are infrequent.
   */
  synchronized void reloadIfChanged() {
    try {
      byte[] certBytes = Files.readAllBytes(certPath);
      byte[] keyBytes = Files.readAllBytes(keyPath);
      byte[] certHash = sha256(certBytes);
      byte[] keyHash = sha256(keyBytes);
      State current = state.get();
      // Require BOTH hashes to match before skipping. If only one file has been rewritten (a
      // partial rotation where cert and key are updated non-atomically), we need to rebuild —
      // reusing the old delegate would mix a new cert with the old key and break handshakes.
      if (Arrays.equals(current.certHash, certHash)
          && Arrays.equals(current.keyHash, keyHash)) {
        return;
      }
      state.set(build(certBytes, keyBytes, certHash, keyHash));
      logger.info("Service '%s': reloaded client TLS certificate", serviceName);
    } catch (IOException | GeneralSecurityException e) {
      // Swallow and log: a transient failure (cert being rewritten, passphrase mismatch during
      // rotation) shouldn't kill the scheduler. The next tick retries with the current delegate
      // still serving in-flight handshakes.
      logger.error(
          "Service '%s': failed to reload client TLS certificate: %s",
          serviceName, e.getMessage());
    }
  }

  /**
   * Cancel the periodic reload task. Idempotent. The currently-loaded delegate keeps serving
   * in-flight handshakes; only future scheduler ticks are stopped.
   */
  @Override
  public void close() {
    ScheduledFuture<?> future = reloadFuture.getAndSet(null);
    if (future != null) {
      future.cancel(false);
    }
  }

  private State build(byte[] certBytes, byte[] keyBytes, byte[] certHash, byte[] keyHash)
      throws IOException, GeneralSecurityException {
    List<X509Certificate> chain =
        PemLoader.parseCertificates(certBytes, certPath.toString());
    PrivateKey key =
        PemLoader.parsePrivateKey(keyBytes, keyPassphrase, keyPath.toString());

    X509ExtendedKeyManager delegate = null;
    for (javax.net.ssl.KeyManager km : KeyStores.keyManagers(chain, key)) {
      if (km instanceof X509ExtendedKeyManager) {
        delegate = (X509ExtendedKeyManager) km;
        break;
      }
    }
    if (delegate == null) {
      throw new IllegalStateException("No X509ExtendedKeyManager available from default provider");
    }
    return new State(delegate, certHash, keyHash);
  }

  private static byte[] sha256(byte[] data) {
    try {
      return MessageDigest.getInstance("SHA-256").digest(data);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 unavailable", e);
    }
  }

  private X509ExtendedKeyManager delegate() {
    return state.get().delegate;
  }

  @Override
  public String[] getClientAliases(String keyType, Principal[] issuers) {
    return delegate().getClientAliases(keyType, issuers);
  }

  @Override
  public String chooseClientAlias(String[] keyType, Principal[] issuers, Socket socket) {
    return delegate().chooseClientAlias(keyType, issuers, socket);
  }

  @Override
  public String[] getServerAliases(String keyType, Principal[] issuers) {
    return delegate().getServerAliases(keyType, issuers);
  }

  @Override
  public String chooseServerAlias(String keyType, Principal[] issuers, Socket socket) {
    return delegate().chooseServerAlias(keyType, issuers, socket);
  }

  @Override
  public X509Certificate[] getCertificateChain(String alias) {
    return delegate().getCertificateChain(alias);
  }

  @Override
  public PrivateKey getPrivateKey(String alias) {
    return delegate().getPrivateKey(alias);
  }

  @Override
  public String chooseEngineClientAlias(String[] keyType, Principal[] issuers, SSLEngine engine) {
    return delegate().chooseEngineClientAlias(keyType, issuers, engine);
  }

  @Override
  public String chooseEngineServerAlias(String keyType, Principal[] issuers, SSLEngine engine) {
    return delegate().chooseEngineServerAlias(keyType, issuers, engine);
  }

  private static final class State {
    final X509ExtendedKeyManager delegate;
    final byte[] certHash;
    final byte[] keyHash;

    State(X509ExtendedKeyManager delegate, byte[] certHash, byte[] keyHash) {
      this.delegate = delegate;
      this.certHash = certHash;
      this.keyHash = keyHash;
    }
  }
}
