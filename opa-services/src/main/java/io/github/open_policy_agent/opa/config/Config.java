package io.github.open_policy_agent.opa.config;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.util.HashMap;
import java.util.Map;
import javax.net.ssl.SSLContext;

public class Config {

  @JsonProperty("decision_logs")
  private DecisionLogsConfig decisionLogs;

  private Map<String, BundleConfig> bundles;

  @JsonProperty("default_decision")
  private String defaultDecision = "system/main";

  @JsonProperty("nd_builtin_cache")
  private NdBuiltinCacheConfig ndBuiltinCache;

  private StatusConfig status = new StatusConfig();

  private Map<String, String> labels;

  @JsonDeserialize(using = ServicesDeserializer.class)
  private Map<String, ServiceConfig> services;

  private DiscoveryConfig discovery;

  public DiscoveryConfig getDiscovery() {
    return discovery;
  }

  public Config setDiscovery(DiscoveryConfig discovery) {
    this.discovery = discovery;
    return this;
  }

  public DecisionLogsConfig getDecisionLogs() {
    return decisionLogs;
  }

  public Config setDecisionLogs(DecisionLogsConfig decisionLogs) {
    this.decisionLogs = decisionLogs;
    return this;
  }

  public Map<String, BundleConfig> getBundles() {
    return bundles;
  }

  public Config setBundles(Map<String, BundleConfig> bundles) {
    this.bundles = bundles;
    return this;
  }

  public Config addBundle(String name, BundleConfig bundle) {
    if (this.bundles == null) {
      this.bundles = new HashMap<>();
    }
    this.bundles.put(name, bundle);
    return this;
  }

  public String getDefaultDecision() {
    return defaultDecision;
  }

  public Config setDefaultDecision(String defaultDecision) {
    this.defaultDecision = defaultDecision;
    return this;
  }

  public NdBuiltinCacheConfig getNdBuiltinCache() {
    return ndBuiltinCache;
  }

  public Config setNdBuiltinCache(NdBuiltinCacheConfig ndBuiltinCache) {
    this.ndBuiltinCache = ndBuiltinCache;
    return this;
  }

  public StatusConfig getStatus() {
    return status;
  }

  public Config setStatus(StatusConfig status) {
    this.status = status;
    return this;
  }

  public Map<String, String> getLabels() {
    return labels;
  }

  public Config setLabels(Map<String, String> labels) {
    this.labels = labels;
    return this;
  }

  public Config addLabel(String key, String value) {
    if (this.labels == null) {
      this.labels = new HashMap<>();
    }
    this.labels.put(key, value);
    return this;
  }

  public ServiceConfig getService(String name) {
    if (services == null) {
      return null;
    }
    return services.get(name);
  }

  public Map<String, ServiceConfig> getServices() {
    return services;
  }

  public Config setServices(Map<String, ServiceConfig> services) {
    this.services = services;
    return this;
  }

  public Config addService(ServiceConfig service) {
    if (this.services == null) {
      this.services = new HashMap<>();
    }
    this.services.put(service.getName(), service);
    return this;
  }

  @Override
  public String toString() {
    return "OPAConfig{"
        + "decisionLogs="
        + decisionLogs
        + ", bundles="
        + bundles
        + ", defaultDecision='"
        + defaultDecision
        + '\''
        + ", ndBuiltinCache="
        + ndBuiltinCache
        + ", status="
        + status
        + ", labels="
        + labels
        + ", services="
        + services
        + ", discovery="
        + discovery
        + '}';
  }

  public static class DecisionLogsConfig {
    private Boolean console = false;

    private ReportingConfig reporting;

    @JsonProperty("mask_decision")
    private String maskDecision = "system/log/mask";

    @JsonProperty("drop_decision")
    private String dropDedcision = "system/log/drop";

    @JsonProperty("min_delay_seconds")
    private Integer minDelaySeconds = 300;

    @JsonProperty("max_delay_seconds")
    private Integer maxDelaySeconds = 600;

    @JsonProperty("resource")
    private String resource = "/logs";

    private String service;

    public Boolean getConsole() {
      return console;
    }

    public DecisionLogsConfig setConsole(Boolean console) {
      this.console = console;
      return this;
    }

    public ReportingConfig getReporting() {
      return reporting;
    }

    public DecisionLogsConfig setReporting(ReportingConfig reporting) {
      this.reporting = reporting;
      return this;
    }

    public String getService() {
      return service;
    }

    public DecisionLogsConfig setService(String service) {
      this.service = service;
      return this;
    }

    public String getMaskDecision() {
      return maskDecision;
    }

    public DecisionLogsConfig setMaskDecision(String maskDecision) {
      this.maskDecision = maskDecision;
      return this;
    }

    public String getDropDedcision() {
      return dropDedcision;
    }

    public DecisionLogsConfig setDropDedcision(String dropDedcision) {
      this.dropDedcision = dropDedcision;
      return this;
    }

    public Integer getMinDelaySeconds() {
      return minDelaySeconds;
    }

    public DecisionLogsConfig setMinDelaySeconds(Integer minDelaySeconds) {
      this.minDelaySeconds = minDelaySeconds;
      return this;
    }

    public Integer getMaxDelaySeconds() {
      return maxDelaySeconds;
    }

    public DecisionLogsConfig setMaxDelaySeconds(Integer maxDelaySeconds) {
      this.maxDelaySeconds = maxDelaySeconds;
      return this;
    }

    public String getResource() {
      return resource;
    }

    public DecisionLogsConfig setResource(String resource) {
      this.resource = resource;
      return this;
    }

    @Override
    public String toString() {
      return "DecisionLogsConfig{"
          + "console="
          + console
          + ", reporting="
          + reporting
          + ", service='"
          + service
          + '\''
          + '}';
    }
  }

  public static class ReportingConfig {
    @JsonProperty("max_delay_seconds")
    private Integer maxDelaySeconds;

    @JsonProperty("min_delay_seconds")
    private Integer minDelaySeconds;

    @JsonProperty("upload_size_limit_bytes")
    private Long uploadSizeLimitBytes;

    @JsonProperty("buffer_size_limit_events")
    private Integer bufferSizeLimitEvents = 10000;

    public Integer getMaxDelaySeconds() {
      return maxDelaySeconds;
    }

    public ReportingConfig setMaxDelaySeconds(Integer maxDelaySeconds) {
      this.maxDelaySeconds = maxDelaySeconds;
      return this;
    }

    public Integer getMinDelaySeconds() {
      return minDelaySeconds;
    }

    public ReportingConfig setMinDelaySeconds(Integer minDelaySeconds) {
      this.minDelaySeconds = minDelaySeconds;
      return this;
    }

    public Long getUploadSizeLimitBytes() {
      return uploadSizeLimitBytes;
    }

    public ReportingConfig setUploadSizeLimitBytes(Long uploadSizeLimitBytes) {
      this.uploadSizeLimitBytes = uploadSizeLimitBytes;
      return this;
    }

    public Integer getBufferSizeLimitEvents() {
      return bufferSizeLimitEvents;
    }

    public ReportingConfig setBufferSizeLimitEvents(Integer bufferSizeLimitEvents) {
      this.bufferSizeLimitEvents = bufferSizeLimitEvents;
      return this;
    }

    @Override
    public String toString() {
      return "ReportingConfig{"
          + "maxDelaySeconds="
          + maxDelaySeconds
          + ", minDelaySeconds="
          + minDelaySeconds
          + ", uploadSizeLimitBytes="
          + uploadSizeLimitBytes
          + '}';
    }
  }

  public static class BundleConfig {
    private PollingConfig polling;
    private String service;
    private String resource;

    public PollingConfig getPolling() {
      return polling;
    }

    public BundleConfig setPolling(PollingConfig polling) {
      this.polling = polling;
      return this;
    }

    public String getService() {
      return service;
    }

    public BundleConfig setService(String service) {
      this.service = service;
      return this;
    }

    public String getResource() {
      return resource;
    }

    public BundleConfig setResource(String resource) {
      this.resource = resource;
      return this;
    }

    @Override
    public String toString() {
      return "BundleConfig{"
          + "polling="
          + polling
          + ", service='"
          + service
          + '\''
          + ", resource='"
          + resource
          + '\''
          + '}';
    }
  }

  public static class PollingConfig {
    @JsonProperty("max_delay_seconds")
    private Integer maxDelaySeconds = 120;

    @JsonProperty("min_delay_seconds")
    private Integer minDelaySeconds = 60;

    public Integer getMaxDelaySeconds() {
      return maxDelaySeconds;
    }

    public PollingConfig setMaxDelaySeconds(Integer maxDelaySeconds) {
      this.maxDelaySeconds = maxDelaySeconds;
      return this;
    }

    public Integer getMinDelaySeconds() {
      return minDelaySeconds;
    }

    public PollingConfig setMinDelaySeconds(Integer minDelaySeconds) {
      this.minDelaySeconds = minDelaySeconds;
      return this;
    }

    @Override
    public String toString() {
      return "PollingConfig{"
          + "maxDelaySeconds="
          + maxDelaySeconds
          + ", minDelaySeconds="
          + minDelaySeconds
          + '}';
    }
  }

  public static class StatusConfig {
    private Boolean console = false;
    private String service;

    public Boolean getConsole() {
      return console;
    }

    public StatusConfig setConsole(Boolean console) {
      this.console = console;
      return this;
    }

    public String getService() {
      return service;
    }

    public StatusConfig setService(String service) {
      this.service = service;
      return this;
    }

    @Override
    public String toString() {
      return "StatusConfig{" + "console=" + console + ", service='" + service + '\'' + '}';
    }
  }

  public static class ServiceConfig {
    private CredentialsConfig credentials;
    private String name;
    private String url;

    @JsonProperty("response_header_timeout_seconds")
    private int responseHeaderTimeoutSeconds = 10;

    @JsonProperty("allow_insecure_tls")
    private boolean allowInsecureTLS = false;

    private TlsConfig tls;

    @JsonIgnore private SSLContext sslContext;

    private Map<String, String> headers;

    public int getResponseHeaderTimeoutSeconds() {
      return responseHeaderTimeoutSeconds;
    }

    public ServiceConfig setResponseHeaderTimeoutSeconds(int responseHeaderTimeoutSeconds) {
      this.responseHeaderTimeoutSeconds = responseHeaderTimeoutSeconds;
      return this;
    }

    public boolean isAllowInsecureTLS() {
      return allowInsecureTLS;
    }

    public ServiceConfig setAllowInsecureTLS(boolean allowInsecureTLS) {
      this.allowInsecureTLS = allowInsecureTLS;
      return this;
    }

    public TlsConfig getTls() {
      return tls;
    }

    public ServiceConfig setTls(TlsConfig tls) {
      this.tls = tls;
      return this;
    }

    /**
     * Programmatic override for the per-service {@link SSLContext}.
     *
     * <p>When set, it is used as-is and file-based TLS fields ({@link TlsConfig},
     * {@link ClientTlsConfig}) are rejected during validation. Use this for keystores that can't be
     * expressed in YAML (PKCS12 from an enterprise cert manager, HSM-backed keys, runtime rotation
     * from an external secret manager, etc.).
     */
    public SSLContext getSslContext() {
      return sslContext;
    }

    public ServiceConfig setSslContext(SSLContext sslContext) {
      this.sslContext = sslContext;
      return this;
    }

    public CredentialsConfig getCredentials() {
      return credentials;
    }

    public ServiceConfig setCredentials(CredentialsConfig credentials) {
      this.credentials = credentials;
      return this;
    }

    public String getName() {
      return name;
    }

    public ServiceConfig setName(String name) {
      this.name = name;
      return this;
    }

    public String getUrl() {
      return url;
    }

    public ServiceConfig setUrl(String url) {
      this.url = url;
      return this;
    }

    /**
     * Extra HTTP headers applied to every request the SDK sends to this service (bundle
     * downloads, decision-log uploads, status reports). Applied after credentials, so they may
     * override built-in headers (including {@code Authorization}). Use sparingly — bearer/mTLS
     * cover most auth needs; this hook exists for services that require non-standard headers
     * (e.g. caller-identity tokens).
     */
    public Map<String, String> getHeaders() {
      return headers;
    }

    public ServiceConfig setHeaders(Map<String, String> headers) {
      this.headers = headers;
      return this;
    }

    @Override
    public String toString() {
      return "ServiceConfig{"
          + "credentials="
          + credentials
          + ", name='"
          + name
          + '\''
          + ", url='"
          + url
          + '\''
          + ", tls="
          + tls
          + '}';
    }
  }

  /**
   * Server-TLS configuration for a service (trust roots).
   *
   * <p>Mirrors Go-OPA's {@code services.<name>.tls} block.
   */
  public static class TlsConfig {
    @JsonProperty("ca_cert")
    private String caCert;

    @JsonProperty("system_ca_required")
    private boolean systemCaRequired = false;

    public String getCaCert() {
      return caCert;
    }

    public TlsConfig setCaCert(String caCert) {
      this.caCert = caCert;
      return this;
    }

    public boolean isSystemCaRequired() {
      return systemCaRequired;
    }

    public TlsConfig setSystemCaRequired(boolean systemCaRequired) {
      this.systemCaRequired = systemCaRequired;
      return this;
    }

    @Override
    public String toString() {
      return "TlsConfig{caCert='" + caCert + "', systemCaRequired=" + systemCaRequired + '}';
    }
  }

  public static class CredentialsConfig {
    private BearerConfig bearer;

    @JsonProperty("client_tls")
    private ClientTlsConfig clientTls;

    public BearerConfig getBearer() {
      return bearer;
    }

    public CredentialsConfig setBearer(BearerConfig bearer) {
      this.bearer = bearer;
      return this;
    }

    public ClientTlsConfig getClientTls() {
      return clientTls;
    }

    public CredentialsConfig setClientTls(ClientTlsConfig clientTls) {
      this.clientTls = clientTls;
      return this;
    }

    @Override
    public String toString() {
      return "CredentialsConfig{bearer=" + bearer + ", clientTls=" + clientTls + '}';
    }
  }

  /**
   * Client-TLS credentials for mTLS bundle downloads (and all service HTTP traffic).
   *
   * <p>Mirrors Go-OPA's {@code services.<name>.credentials.client_tls} block. Only PKCS#8
   * (encrypted or unencrypted) PEM private keys are supported; convert PKCS#1 keys with {@code
   * openssl pkcs8 -topk8 -nocrypt -in key.pem -out key-pkcs8.pem}.
   */
  public static class ClientTlsConfig {
    private String cert;

    @JsonProperty("private_key")
    private String privateKey;

    @JsonProperty("private_key_passphrase")
    private String privateKeyPassphrase;

    @JsonProperty("cert_reread_interval_seconds")
    private Integer certRereadIntervalSeconds;

    public String getCert() {
      return cert;
    }

    public ClientTlsConfig setCert(String cert) {
      this.cert = cert;
      return this;
    }

    public String getPrivateKey() {
      return privateKey;
    }

    public ClientTlsConfig setPrivateKey(String privateKey) {
      this.privateKey = privateKey;
      return this;
    }

    public String getPrivateKeyPassphrase() {
      return privateKeyPassphrase;
    }

    public ClientTlsConfig setPrivateKeyPassphrase(String privateKeyPassphrase) {
      this.privateKeyPassphrase = privateKeyPassphrase;
      return this;
    }

    public Integer getCertRereadIntervalSeconds() {
      return certRereadIntervalSeconds;
    }

    public ClientTlsConfig setCertRereadIntervalSeconds(Integer certRereadIntervalSeconds) {
      this.certRereadIntervalSeconds = certRereadIntervalSeconds;
      return this;
    }

    @Override
    public String toString() {
      return "ClientTlsConfig{cert='"
          + cert
          + "', privateKey='"
          + privateKey
          + "', privateKeyPassphrase="
          + (privateKeyPassphrase == null ? "null" : "<redacted>")
          + ", certRereadIntervalSeconds="
          + certRereadIntervalSeconds
          + '}';
    }
  }

  public static class BearerConfig {
    private String token;

    public String getToken() {
      return token;
    }

    public BearerConfig setToken(String token) {
      this.token = token;
      return this;
    }

    @Override
    public String toString() {
      return "BearerConfig{token=" + (token == null ? "null" : "<redacted>") + '}';
    }
  }

  /**
   * Configuration for OPA's discovery protocol.
   *
   * <p>Discovery enables dynamic configuration updates. The discovery bundle is downloaded from the
   * specified service and contains the OPA configuration (bundles, decision logs, etc.).
   *
   * <p>Example configuration:
   *
   * <pre>{@code
   * discovery:
   *   name: discovery
   *   service: acmecorp
   *   resource: /bundles/discovery.tar.gz
   *   polling:
   *     min_delay_seconds: 60
   *     max_delay_seconds: 120
   * }</pre>
   */
  public static class DiscoveryConfig {
    private String name;
    private String service;
    private String resource;
    private PollingConfig polling;

    public String getName() {
      return name;
    }

    public DiscoveryConfig setName(String name) {
      this.name = name;
      return this;
    }

    public String getService() {
      return service;
    }

    public DiscoveryConfig setService(String service) {
      this.service = service;
      return this;
    }

    public String getResource() {
      return resource;
    }

    public DiscoveryConfig setResource(String resource) {
      this.resource = resource;
      return this;
    }

    public PollingConfig getPolling() {
      return polling;
    }

    public DiscoveryConfig setPolling(PollingConfig polling) {
      this.polling = polling;
      return this;
    }

    @Override
    public String toString() {
      return "DiscoveryConfig{"
          + "name='"
          + name
          + '\''
          + ", service='"
          + service
          + '\''
          + ", resource='"
          + resource
          + '\''
          + ", polling="
          + polling
          + '}';
    }
  }

  /**
   * Configuration for OPA's non-deterministic builtin cache.
   *
   * <p>The nd_builtin_cache enables caching of results from non-deterministic builtins (like
   * time.now_ns, http.send, rand.intn, etc.) to ensure consistent results within a single
   * evaluation and enable decision replay.
   *
   * <p>Example configuration:
   *
   * <pre>{@code
   * nd_builtin_cache:
   *   max_num_entries: 10000
   *   stale_entry_eviction_period_seconds: 60
   * }</pre>
   */
  public static class NdBuiltinCacheConfig {
    @JsonProperty("max_num_entries")
    private Integer maxNumEntries = 10000;

    @JsonProperty("stale_entry_eviction_period_seconds")
    private Integer staleEntryEvictionPeriodSeconds = 60;

    public Integer getMaxNumEntries() {
      return maxNumEntries;
    }

    public NdBuiltinCacheConfig setMaxNumEntries(Integer maxNumEntries) {
      this.maxNumEntries = maxNumEntries;
      return this;
    }

    public Integer getStaleEntryEvictionPeriodSeconds() {
      return staleEntryEvictionPeriodSeconds;
    }

    public NdBuiltinCacheConfig setStaleEntryEvictionPeriodSeconds(
        Integer staleEntryEvictionPeriodSeconds) {
      this.staleEntryEvictionPeriodSeconds = staleEntryEvictionPeriodSeconds;
      return this;
    }

    @Override
    public String toString() {
      return "NdBuiltinCacheConfig{"
          + "maxNumEntries="
          + maxNumEntries
          + ", staleEntryEvictionPeriodSeconds="
          + staleEntryEvictionPeriodSeconds
          + '}';
    }
  }
}
