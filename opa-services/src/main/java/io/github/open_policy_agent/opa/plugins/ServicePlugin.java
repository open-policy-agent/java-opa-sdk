package io.github.open_policy_agent.opa.plugins;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.Builder;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import io.github.open_policy_agent.opa.config.Config;
import io.github.open_policy_agent.opa.logging.Logger;
import io.github.open_policy_agent.opa.tls.SslContextBuilder;

public final class ServicePlugin implements Plugin {

  private final Map<String, Service> services = new HashMap<>();
  private PluginManager manager;
  private ScheduledExecutorService certReloadScheduler;

  public ServicePlugin() {}

    public Set<String> validate(PluginManager manager) {
    Set<String> errors = new HashSet<>();

    if (manager.getConfig().getServices() == null || manager.getConfig().getServices().isEmpty()) {
      return errors; // No services configured is valid
    }

    for (Map.Entry<String, Config.ServiceConfig> entry :
        manager.getConfig().getServices().entrySet()) {
      String serviceName = entry.getKey();
      Config.ServiceConfig service = entry.getValue();

      // Set the name from the map key if not already set
      if (service.getName() == null || service.getName().isEmpty()) {
        service.setName(serviceName);
      }

      if (service.getUrl() == null || service.getUrl().isEmpty()) {
        errors.add("Service '" + serviceName + "' has missing or empty URL");
      }
      Credential credential = getCredential(service);
      if (credential != null && credential.getType().equals(Credential.Type.NOT_SUPPORTED)) {
        errors.add("Service '" + serviceName + "' has unsupported credential type");
      }

      errors.addAll(validateTls(serviceName, service));
    }
    return errors;
  }

  private Set<String> validateTls(String serviceName, Config.ServiceConfig service) {
    Set<String> errors = new HashSet<>();
    java.util.function.Function<String, String> err =
        msg -> "Service '" + serviceName + "' " + msg;

    Config.ClientTlsConfig clientTls =
        service.getCredentials() == null ? null : service.getCredentials().getClientTls();
    boolean hasServerTls = SslContextBuilder.hasServerTls(service);
    boolean hasClientTls = SslContextBuilder.hasClientTls(service);
    boolean hasProgrammatic = service.getSslContext() != null;

    if (service.isAllowInsecureTLS() && (hasServerTls || hasClientTls || hasProgrammatic)) {
      errors.add(err.apply("sets allow_insecure_tls=true alongside other TLS config; remove one"));
    }

    if (hasProgrammatic && (hasServerTls || hasClientTls)) {
      errors.add(err.apply("sets programmatic SSLContext alongside file-based TLS config; remove one"));
    }

    Config.TlsConfig tlsBlock = service.getTls();
    if (tlsBlock != null
        && (tlsBlock.getCaCert() == null || tlsBlock.getCaCert().isEmpty())
        && !tlsBlock.isSystemCaRequired()) {
      errors.add(
          err.apply(
              "tls block has no effect: set ca_cert or system_ca_required=true, or remove the"
                  + " block"));
    }

    if (clientTls != null) {
      boolean certSet = clientTls.getCert() != null && !clientTls.getCert().isEmpty();
      boolean keySet = clientTls.getPrivateKey() != null && !clientTls.getPrivateKey().isEmpty();
      if (certSet != keySet) {
        errors.add(err.apply("credentials.client_tls must set both cert and private_key"));
      }
      if (!keySet
          && clientTls.getPrivateKeyPassphrase() != null
          && !clientTls.getPrivateKeyPassphrase().isEmpty()) {
        errors.add(err.apply("credentials.client_tls.private_key_passphrase requires private_key"));
      }
      if (clientTls.getCertRereadIntervalSeconds() != null
          && clientTls.getCertRereadIntervalSeconds() < 0) {
        errors.add(err.apply("credentials.client_tls.cert_reread_interval_seconds must be >= 0"));
      }
      if (clientTls.getCertRereadIntervalSeconds() != null
          && clientTls.getCertRereadIntervalSeconds() > 0
          && (!certSet || !keySet)) {
        errors.add(
            err.apply(
                "credentials.client_tls.cert_reread_interval_seconds requires both cert"
                    + " and private_key"));
      }
    }

    if (service.getCredentials() != null
        && service.getCredentials().getBearer() != null
        && hasClientTls) {
      errors.add(err.apply("sets both bearer and client_tls credentials; only one is allowed"));
    }

    // Path existence is intentionally NOT checked here. Certificate files may be rotated into
    // place after startup (short-lived CM2 certs, discovery-driven config). The builder
    // fails fast with a clear error on the first download if a path is missing or unreadable.

    return errors;
  }

  public Plugin initialize(PluginManager manager) {
    ServicePlugin plugin = new ServicePlugin();
    plugin.manager = manager;

    if (manager.getConfig().getServices() == null || manager.getConfig().getServices().isEmpty()) {
      return plugin;
    }

    // Two threads so a slow reload (e.g. an NFS stall) on one service doesn't stall the others.
    // This is a deliberate small pool; bump only if many services rotate.
    plugin.certReloadScheduler =
        BundleDownloader.newPollScheduler(2, "opa-service-cert-reload");

    try {
      for (Map.Entry<String, Config.ServiceConfig> entry :
          manager.getConfig().getServices().entrySet()) {
        String serviceName = entry.getKey();
        Config.ServiceConfig service = entry.getValue();

        if (service.getName() == null || service.getName().isEmpty()) {
          service.setName(serviceName);
        }

        HttpClient.Builder clientBuilder =
            HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .version(HttpClient.Version.HTTP_2)
                .connectTimeout(Duration.ofSeconds(service.getResponseHeaderTimeoutSeconds()));

        try {
          SslContextBuilder.Tls tls =
              SslContextBuilder.build(service, plugin.certReloadScheduler, manager.getLogger());
          if (tls.getSslContext() != null) {
            clientBuilder.sslContext(tls.getSslContext());
            if (service.isAllowInsecureTLS()) {
              manager
                  .getLogger()
                  .warn(
                      "Service '%s' has insecure TLS enabled - this should only be used in development",
                      serviceName);
            }
          }
          clientBuilder.sslParameters(tls.getSslParameters());
        } catch (Exception e) {
          throw new PluginInitializationException(
                  "Failed to configure TLS for service '" + serviceName + "': " + e.getMessage(), e)
              .withContext("serviceName", serviceName);
        }

        HttpClient client = clientBuilder.build();

        plugin.services.put(
            service.getName(),
            new Service(client, manager.getLogger())
                .setName(service.getName())
                .setUrl(service.getUrl())
                .setCredentials(getCredential(service)));
      }
    } catch (RuntimeException e) {
      // Partial init failed: shut down the scheduler so any reload tasks already scheduled
      // for earlier services don't outlive this initialize() call.
      plugin.certReloadScheduler.shutdownNow();
      plugin.certReloadScheduler = null;
      throw e;
    }

    return plugin;
  }

  public void start() {
    // Services plugin is ready immediately as it's stateless
    manager.updatePluginStatus("services", PluginManager.Status.OK);
  }

  @Override
  public void stop() {
    manager.getLogger().info("Stopping services plugin...");
    if (certReloadScheduler != null) {
      certReloadScheduler.shutdown();
      try {
        if (!certReloadScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
          certReloadScheduler.shutdownNow();
        }
      } catch (InterruptedException e) {
        certReloadScheduler.shutdownNow();
        Thread.currentThread().interrupt();
      }
    }
  }

  /**
   * Get a service by name.
   *
   * @param serviceName the name of the service
   * @return the Service instance or null if not found
   */
  public Service getService(String serviceName) {
    return services.get(serviceName);
  }

  private Credential getCredential(Config.ServiceConfig service) {
    if (service.getCredentials() == null) {
      return null;
    }

    if (service.getCredentials().getBearer() != null) {
      return new BearerCredential().setToken(service.getCredentials().getBearer().getToken());
    }
    // client_tls is handled at the SSLContext layer (not a per-request modifier), so it maps
    // to no-op credentials at the HTTP level.
    if (service.getCredentials().getClientTls() != null) {
      return null;
    }
    return new Credential() {
      @Override
      public Type getType() {
        return Type.NOT_SUPPORTED;
      }

      @Override
      public Builder modifyRequest(Builder builder) {
        throw new UnsupportedOperationException("Credential Not supported.");
      }
    };
  }

  interface Credential {

    Type getType();

    Builder modifyRequest(Builder builder);

    enum Type {
      BEARER,
      NOT_SUPPORTED
    }
  }

  public static class Service {
    private final Logger logger;
    private String name;
    private String url;
    private int responseHeaderTimeoutSeconds = 10;
    private boolean allowInsecureTLS = false;
    private Credential credentials;
    private final HttpClient client;

    private Service(HttpClient client, Logger logger) {
      this.client = client;
      this.logger = logger;
    }

    void post(String path, String body) {

      Builder builder =
          HttpRequest.newBuilder()
              .uri(buildUri(path))
              .header("Content-Type", "application/json")
              .header("Accept", "application/json")
              .POST(HttpRequest.BodyPublishers.ofString(body));

      builder = applyCredentials(builder);
      HttpRequest request = builder.build();

      client
          .sendAsync(request, HttpResponse.BodyHandlers.ofString())
          .thenAccept(
              resp -> {
                logger.debug("POST request sent successfully: " + resp.statusCode());
              })
          .exceptionally(
              (e) -> {
                logger.error("Failed to send POST request: " + e.getMessage());
                return null;
              });
    }

    /**
     * Apply this service's credentials to an in-progress request. No-op when no credentials are
     * configured. Exposed so other plugins (e.g. bundle downloads) can use the same auth path as
     * {@link #post}.
     */
    public Builder applyCredentials(Builder builder) {
      if (credentials != null) {
        return credentials.modifyRequest(builder);
      }
      return builder;
    }

    /**
     * Build a properly formatted URI by combining the base URL with the path.
     *
     * <p>Handles trailing/leading slashes and validates the resulting URI.
     *
     * @param path the path to append to the base URL
     * @return a normalized URI
     * @throws IllegalArgumentException if the resulting URI is invalid
     */
    private URI buildUri(String path) {
      String baseUrl = getUrl();

      // Handle null or empty path
      if (path == null || path.isEmpty()) {
        path = "/";
      }

      // Ensure proper slash handling
      if (baseUrl.endsWith("/") && path.startsWith("/")) {
        path = path.substring(1);
      } else if (!baseUrl.endsWith("/") && !path.startsWith("/")) {
        path = "/" + path;
      }

      try {
        return URI.create(baseUrl + path).normalize();
      } catch (IllegalArgumentException e) {
        throw new IllegalArgumentException(
            "Invalid URI: " + baseUrl + path + " - " + e.getMessage(), e);
      }
    }

    public HttpClient getClient() {
      return client;
    }

    public String getName() {
      return name;
    }

    public Service setName(String name) {
      this.name = name;
      return this;
    }

    public String getUrl() {
      return url;
    }

    public Service setUrl(String url) {
      this.url = url;
      return this;
    }

    public int getResponseHeaderTimeoutSeconds() {
      return responseHeaderTimeoutSeconds;
    }

    public Service setResponseHeaderTimeoutSeconds(int responseHeaderTimeoutSeconds) {
      this.responseHeaderTimeoutSeconds = responseHeaderTimeoutSeconds;
      return this;
    }

    public boolean isAllowInsecureTls() {
      return allowInsecureTLS;
    }

    public Service setAllowInsecureTls(boolean allowInsecureTls) {
      this.allowInsecureTLS = allowInsecureTls;
      return this;
    }

    public Credential getCredentials() {
      return credentials;
    }

    public Service setCredentials(Credential credentials) {
      this.credentials = credentials;
      return this;
    }
  }

  static class BearerCredential implements Credential {
    private String token;
    private String tokenPath;
    private String scheme = "Bearer";

    private BearerCredential() {}

    @Override
    public Type getType() {
      return Type.BEARER;
    }

    @Override
    public Builder modifyRequest(Builder builder) {
      if (token != null) {
        builder.header("Authorization", scheme + " " + token);
      }
      return builder;
    }

    public String getToken() {
      return token;
    }

    public BearerCredential setToken(String token) {
      this.token = token;
      return this;
    }

    public String getTokenPath() {
      return tokenPath;
    }

    public BearerCredential setTokenPath(String tokenPath) {
      this.tokenPath = tokenPath;
      return this;
    }

    public String getScheme() {
      return scheme;
    }

    public BearerCredential setScheme(String scheme) {
      this.scheme = scheme;
      return this;
    }
  }
}
