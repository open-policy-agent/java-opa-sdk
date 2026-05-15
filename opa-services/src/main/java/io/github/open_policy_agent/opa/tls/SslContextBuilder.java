package io.github.open_policy_agent.opa.tls;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import javax.net.ssl.KeyManager;
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
 *   <li>File-based config ({@code tls.ca_cert}, {@code credentials.client_tls.*}).
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

  /** True when the service configures a custom CA for server trust. */
  public static boolean hasServerTls(Config.ServiceConfig service) {
    Config.TlsConfig tls = service.getTls();
    return tls != null && tls.getCaCert() != null && !tls.getCaCert().isEmpty();
  }

  /** True when the service configures a client certificate for mTLS. */
  public static boolean hasClientTls(Config.ServiceConfig service) {
    if (service.getCredentials() == null) {
      return false;
    }
    Config.ClientTlsConfig clientTls = service.getCredentials().getClientTls();
    return clientTls != null && clientTls.getCert() != null && !clientTls.getCert().isEmpty();
  }

  private static KeyManager[] buildKeyManagers(
      Config.ServiceConfig service, ScheduledExecutorService reloadScheduler, Logger logger)
      throws IOException, GeneralSecurityException {
    Config.ClientTlsConfig clientTls = service.getCredentials().getClientTls();
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
              certPath, keyPath, passphrase, reloadScheduler, reread, logger, service.getName());
      return new KeyManager[] {km};
    }

    return KeyStores.keyManagers(
        PemLoader.loadCertificates(certPath), PemLoader.loadPrivateKey(keyPath, passphrase));
  }

  // Package-private for tests.
  static TrustManager[] buildTrustManagers(Config.TlsConfig tls)
      throws IOException, GeneralSecurityException {
    Path caPath = Paths.get(tls.getCaCert());
    List<X509Certificate> userCAs = PemLoader.loadCertificates(caPath);

    char[] storePass = new char[0];
    KeyStore ts = KeyStore.getInstance("PKCS12");
    ts.load(null, storePass);
    int idx = 0;
    for (X509Certificate ca : userCAs) {
      ts.setCertificateEntry("user-ca-" + idx++, ca);
    }

    // system_ca_required=true: merge system trust anchors into the same keystore so a single
    // PKIX validator chains through either set. Composing two trust managers with try/catch is
    // tempting but unsafe: a revoked or expired cert that fails the user check would silently
    // fall through to the system check and could be accepted if the system happened to chain it.
    if (tls.isSystemCaRequired()) {
      TrustManagerFactory sysTmf =
          TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
      sysTmf.init((KeyStore) null);
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
