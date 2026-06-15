package io.github.open_policy_agent.opa.tls;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ScheduledExecutorService;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import io.github.open_policy_agent.opa.config.Config;
import io.github.open_policy_agent.opa.logging.Logger;

/**
 * Builds per-service {@link SSLContext} and {@link SSLParameters} from a {@link
 * Config.ServiceConfig}.
 *
 * <p>Precedence, highest first:
 *
 * <ol>
 *   <li>Programmatic override ({@link Config.ServiceConfig#getSslContext()}).
 *   <li>{@code allow_insecure_tls: true} — trust-all context (development only).
 *   <li>File / keystore-based config ({@code tls.ca_cert}, {@code tls.truststore}, {@code
 *       credentials.client_tls.cert} / {@code .keystore}).
 *   <li>Nothing configured → {@code null} (caller keeps the HttpClient default).
 * </ol>
 *
 * <p>SSL parameters always pin the minimum TLS version to 1.2, matching Go-OPA's {@code
 * DefaultMinTLSVersion} and swift-opa-sdk.
 */
public final class SslContextBuilder {

  private static final String[] MIN_TLS_PROTOCOLS = {"TLSv1.2", "TLSv1.3"};
  private static final String[] APPLICATION_PROTOCOLS = {"h2", "http/1.1"};

  private SslContextBuilder() {}

  public static Tls build(
      Config.ServiceConfig service,
      ScheduledExecutorService reloadScheduler,
      Logger logger)
      throws IOException, GeneralSecurityException {

    SSLParameters params = new SSLParameters();
    params.setProtocols(MIN_TLS_PROTOCOLS);
    // ALPN must be set explicitly because HttpClient.Builder#sslParameters replaces Java's
    // built-in defaults wholesale. Without "h2" in this list, an HTTP/2 client that negotiates
    // ALPN with the server still ends up with no application protocol agreed, causing client-cert
    // re-presentation to silently fail under TLS 1.3 — server returns 401 with no body. Listing
    // both lets the same SSLContext serve HTTP/1.1 and HTTP/2 clients identically.
    params.setApplicationProtocols(APPLICATION_PROTOCOLS);

    SSLContext programmatic = service.getSslContext();
    if (programmatic != null) {
      return new Tls(programmatic, params);
    }

    if (service.isAllowInsecureTLS()) {
      SSLContext ctx = SSLContext.getInstance("TLS");
      ctx.init(null, new TrustManager[] {TrustAllManager.INSTANCE}, new SecureRandom());
      return new Tls(ctx, params);
    }

    boolean hasServerTls = hasServerTls(service);
    boolean hasClientTls = hasClientTls(service);

    if (!hasServerTls && !hasClientTls) {
      return new Tls(null, params);
    }

    KeyManager[] keyManagers = hasClientTls
        ? buildKeyManagers(service, reloadScheduler, logger)
        : null;
    TrustManager[] trustManagers = hasServerTls
        ? buildTrustManagers(service.getTls())
        : null;

    SSLContext ctx = SSLContext.getInstance("TLS");
    ctx.init(keyManagers, trustManagers, new SecureRandom());
    return new Tls(ctx, params);
  }

  /** True when the service configures any form of trust roots (PEM CA or keystore). */
  public static boolean hasServerTls(Config.ServiceConfig service) {
    Config.TlsConfig tls = service.getTls();
    if (tls == null) {
      return false;
    }
    if (tls.getCaCert() != null && !tls.getCaCert().isEmpty()) {
      return true;
    }
    Config.TruststoreConfig ts = tls.getTruststore();
    return ts != null && (ts.getKeyStore() != null || (ts.getPath() != null && !ts.getPath().isEmpty()));
  }

  /** True when the service configures a client certificate (PEM or keystore) for mTLS. */
  public static boolean hasClientTls(Config.ServiceConfig service) {
    if (service.getCredentials() == null) {
      return false;
    }
    Config.ClientTlsConfig clientTls = service.getCredentials().getClientTls();
    if (clientTls == null) {
      return false;
    }
    if (clientTls.getCert() != null && !clientTls.getCert().isEmpty()) {
      return true;
    }
    Config.KeystoreConfig ks = clientTls.getKeystore();
    return ks != null && (ks.getKeyStore() != null || (ks.getPath() != null && !ks.getPath().isEmpty()));
  }

  private static KeyManager[] buildKeyManagers(
      Config.ServiceConfig service, ScheduledExecutorService reloadScheduler, Logger logger)
      throws IOException, GeneralSecurityException {
    Config.ClientTlsConfig clientTls = service.getCredentials().getClientTls();

    if (clientTls.getKeystore() != null) {
      return keyManagersFromKeystore(clientTls.getKeystore());
    }

    Path certPath = Paths.get(clientTls.getCert());
    Path keyPath = Paths.get(clientTls.getPrivateKey());
    char[] passphrase =
        clientTls.getPrivateKeyPassphrase() == null
            ? null
            : clientTls.getPrivateKeyPassphrase().toCharArray();
    Integer reread = clientTls.getCertRereadIntervalSeconds();

    if (reread != null && reread > 0) {
      ReloadingX509KeyManager km =
          ReloadingX509KeyManager.create(
              certPath,
              keyPath,
              passphrase,
              reloadScheduler,
              java.time.Duration.ofSeconds(reread),
              logger,
              service.getName());
      return new KeyManager[] {km};
    }

    return KeyStores.keyManagers(
        PemLoader.loadCertificates(certPath), PemLoader.loadPrivateKey(keyPath, passphrase));
  }

  private static KeyManager[] keyManagersFromKeystore(Config.KeystoreConfig cfg)
      throws IOException, GeneralSecurityException {
    KeyStore ks = loadKeyStore(cfg.getKeyStore(), cfg.getPath(), cfg.getType(), cfg.getPassword());
    char[] keyPass =
        (cfg.getKeyPassword() != null ? cfg.getKeyPassword() : cfg.getPassword() == null ? "" : cfg.getPassword())
            .toCharArray();
    KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
    kmf.init(ks, keyPass);
    return kmf.getKeyManagers();
  }

  // Package-private for tests.
  static TrustManager[] buildTrustManagers(Config.TlsConfig tls)
      throws IOException, GeneralSecurityException {
    KeyStore ts;
    if (tls.getTruststore() != null) {
      ts = loadKeyStore(
          tls.getTruststore().getKeyStore(),
          tls.getTruststore().getPath(),
          tls.getTruststore().getType(),
          tls.getTruststore().getPassword());
    } else {
      Path caPath = Paths.get(tls.getCaCert());
      List<X509Certificate> userCAs = PemLoader.loadCertificates(caPath);
      ts = KeyStore.getInstance("PKCS12");
      ts.load(null, new char[0]);
      int idx = 0;
      for (X509Certificate ca : userCAs) {
        ts.setCertificateEntry("user-ca-" + idx++, ca);
      }
    }

    // system_ca_required=true: merge system trust anchors into the same keystore so a single
    // PKIX validator chains through either set. Composing two trust managers with try/catch is
    // tempting but unsafe: a revoked or expired cert that fails the user check would silently
    // fall through to the system check and could be accepted if the system happened to chain it.
    if (tls.isSystemCaRequired()) {
      TrustManagerFactory sysTmf =
          TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
      sysTmf.init((KeyStore) null);
      int idx = 0;
      for (TrustManager tm : sysTmf.getTrustManagers()) {
        if (tm instanceof X509TrustManager) {
          for (X509Certificate sysCa : ((X509TrustManager) tm).getAcceptedIssuers()) {
            ts.setCertificateEntry("sys-ca-" + idx++, sysCa);
          }
        }
      }
    }

    TrustManagerFactory tmf =
        TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
    tmf.init(ts);
    return tmf.getTrustManagers();
  }

  private static KeyStore loadKeyStore(KeyStore programmatic, String path, String type, String password)
      throws IOException, GeneralSecurityException {
    if (programmatic != null) {
      return programmatic;
    }
    String resolvedType =
        type != null && !type.isEmpty()
            ? type
            : (path != null && path.toLowerCase(Locale.ROOT).endsWith(".jks") ? "JKS" : "PKCS12");
    KeyStore ks = KeyStore.getInstance(resolvedType);
    char[] pw = password == null ? new char[0] : password.toCharArray();
    try (InputStream in = Files.newInputStream(Paths.get(path))) {
      ks.load(in, pw);
    }
    return ks;
  }

  /** Container for a configured {@link SSLContext} and its {@link SSLParameters}. */
  public static final class Tls {
    private final SSLContext sslContext;
    private final SSLParameters sslParameters;

    Tls(SSLContext sslContext, SSLParameters sslParameters) {
      this.sslContext = sslContext;
      this.sslParameters = sslParameters;
    }

    public SSLContext getSslContext() {
      return sslContext;
    }

    public SSLParameters getSslParameters() {
      return sslParameters;
    }
  }

  private static final class TrustAllManager implements X509TrustManager {
    static final TrustAllManager INSTANCE = new TrustAllManager();

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType) {}

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType) {}

    @Override
    public X509Certificate[] getAcceptedIssuers() {
      return new X509Certificate[0];
    }
  }
}
