package io.github.open_policy_agent.opa.config;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.security.KeyStore;
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
    private String dropDecision = "system/log/drop";

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

    public String getDropDecision() {
      return dropDecision;
    }

    public DecisionLogsConfig setDropDecision(String dropDecision) {
      this.dropDecision = dropDecision;
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
    /** Default maximum bundle size (compressed download and decompressed contents): 512 MB. */
    public static final long DEFAULT_MAX_SIZE_BYTES = 512L * 1024 * 1024;

    private PollingConfig polling;
    private String service;
    private String resource;

    /**
     * Maximum bundle size in bytes, applied both to the compressed HTTP download and to the
     * decompressed tarball contents. Defaults to {@link #DEFAULT_MAX_SIZE_BYTES} (512 MB).
     *
     * <p><strong>Effective ceiling differs by path</strong>, because both paths buffer payloads
     * into a Java {@code byte[]}, whose maximum length is {@link Integer#MAX_VALUE} (~2 GB):
     *
     * <ul>
     *   <li><strong>HTTP download:</strong> The entire response is buffered in a single {@code
     *       byte[]}, so the effective limit is capped at {@code Integer.MAX_VALUE} (~2 GB).
     *       Configured values above that are silently clamped to {@code Integer.MAX_VALUE} on the
     *       download path.
     *   <li><strong>Decompressed tarball contents:</strong> The cumulative budget is tracked as a
     *       {@code long} and may exceed 2 GB. Only each individual entry is bounded by the {@code
     *       byte[]} ceiling (~2 GB per entry); the sum across all entries can be larger.
     * </ul>
     */
    @JsonProperty("max_size_bytes")
    private long maxSizeBytes = DEFAULT_MAX_SIZE_BYTES;

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

    public long getMaxSizeBytes() {
      return maxSizeBytes;
    }

    /**
     * Set the maximum bundle size in bytes. Note that the HTTP download enforcement is capped at
     * {@link Integer#MAX_VALUE} (~2 GB); values above that are clamped on the download path. See
     * {@link #maxSizeBytes} for details.
     */
    public BundleConfig setMaxSizeBytes(long maxSizeBytes) {
      this.maxSizeBytes = maxSizeBytes;
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
          + ", maxSizeBytes="
          + maxSizeBytes
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

    /**
     * Server-TLS configuration (trust roots) for verifying the service certificate. See {@link
     * TlsConfig} for the YAML-equivalent fields.
     */
    public ServiceConfig setTls(TlsConfig tls) {
      this.tls = tls;
      return this;
    }

    public SSLContext getSslContext() {
      return sslContext;
    }

    /**
     * Programmatic override for the per-service {@link SSLContext}.
     *
     * <p>When set, it is used as-is and file-based TLS fields ({@link TlsConfig}, {@link
     * ClientTlsConfig}) are rejected during validation. Use this for keystores that can't be
     * expressed in YAML (HSM-backed keys, custom KeyManager chains, etc.). For keystores from a
     * secret manager, prefer the more ergonomic {@link TruststoreConfig#setKeyStore(KeyStore)} /
     * {@link KeystoreConfig#setKeyStore(KeyStore)} pass-throughs.
     */
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
   * <p>Mirrors Go-OPA's {@code services.<name>.tls} block. {@code ca_cert} is the file-based PEM
   * path; {@code truststore} is a Java-native JKS / PKCS#12 alternative. The two are mutually
   * exclusive — pick whichever your deployment already manages.
   */
  public static class TlsConfig {
    @JsonProperty("ca_cert")
    private String caCert;

    @JsonProperty("system_ca_required")
    private boolean systemCaRequired = false;

    private TruststoreConfig truststore;

    public String getCaCert() {
      return caCert;
    }

    /**
     * Path to a PEM file containing one or more trust-root certificates used to verify the server
     * certificate. Mutually exclusive with {@link #setTruststore(TruststoreConfig)}.
     */
    public TlsConfig setCaCert(String caCert) {
      this.caCert = caCert;
      return this;
    }

    public boolean isSystemCaRequired() {
      return systemCaRequired;
    }

    /**
     * When {@code true}, the JVM's default trust store is also accepted in addition to the
     * configured {@link #setCaCert(String) ca_cert} or {@link #setTruststore(TruststoreConfig)
     * truststore}.
     */
    public TlsConfig setSystemCaRequired(boolean systemCaRequired) {
      this.systemCaRequired = systemCaRequired;
      return this;
    }

    public TruststoreConfig getTruststore() {
      return truststore;
    }

    /**
     * Java-native JKS / PKCS#12 truststore. Use this when your deployment already manages trust
     * roots in a keystore (typical for Java shops). Mutually exclusive with {@link
     * #setCaCert(String) ca_cert}.
     */
    public TlsConfig setTruststore(TruststoreConfig truststore) {
      this.truststore = truststore;
      return this;
    }

    @Override
    public String toString() {
      return "TlsConfig{caCert='"
          + caCert
          + "', systemCaRequired="
          + systemCaRequired
          + ", truststore="
          + truststore
          + '}';
    }
  }

  /**
   * JKS / PKCS#12 truststore. Either {@link #setPath(String) path} (loaded from disk) or {@link
   * #setKeyStore(KeyStore) keyStore} (programmatic) must be set, but not both.
   */
  public static class TruststoreConfig {
    private String path;
    private String password;
    private String type;

    @JsonIgnore private KeyStore keyStore;

    public String getPath() {
      return path;
    }

    public TruststoreConfig setPath(String path) {
      this.path = path;
      return this;
    }

    public String getPassword() {
      return password;
    }

    public TruststoreConfig setPassword(String password) {
      this.password = password;
      return this;
    }

    public String getType() {
      return type;
    }

    /**
     * Keystore type. Defaults to {@code PKCS12} (or inferred from the file extension when {@link
     * #setPath(String) path} ends in {@code .jks}).
     */
    public TruststoreConfig setType(String type) {
      this.type = type;
      return this;
    }

    public KeyStore getKeyStore() {
      return keyStore;
    }

    /**
     * Programmatic SDK pass-through for an in-memory {@link KeyStore} (e.g. loaded from a secret
     * manager). When set, {@link #setPath(String) path} must be {@code null}.
     */
    public TruststoreConfig setKeyStore(KeyStore keyStore) {
      this.keyStore = keyStore;
      return this;
    }

    @Override
    public String toString() {
      return "TruststoreConfig{path='"
          + path
          + "', type='"
          + type
          + "', password="
          + (password == null ? "null" : "<redacted>")
          + ", keyStore="
          + (keyStore == null ? "null" : "<programmatic>")
          + '}';
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
   * <p>Mirrors Go-OPA's {@code services.<name>.credentials.client_tls} block. Two ways to provide
   * the cert+key pair:
   *
   * <ul>
   *   <li>{@code cert} + {@code private_key}: PEM files on disk, PKCS#8 unencrypted private key.
   *   <li>{@code keystore}: a JKS / PKCS#12 keystore on disk or supplied programmatically.
   * </ul>
   *
   * <p>Pick one — they are mutually exclusive.
   */
  public static class ClientTlsConfig {
    private String cert;

    @JsonProperty("private_key")
    private String privateKey;

    @JsonProperty("private_key_passphrase")
    private String privateKeyPassphrase;

    @JsonProperty("cert_reread_interval_seconds")
    private Integer certRereadIntervalSeconds;

    private KeystoreConfig keystore;

    public String getCert() {
      return cert;
    }

    /**
     * Path to a PEM file with the client certificate chain. Mutually exclusive with {@link
     * #setKeystore(KeystoreConfig)}.
     */
    public ClientTlsConfig setCert(String cert) {
      this.cert = cert;
      return this;
    }

    public String getPrivateKey() {
      return privateKey;
    }

    /**
     * Path to an unencrypted PKCS#8 PEM file containing the client private key. PKCS#1 / SEC1 keys
     * are not supported — convert with {@code openssl pkcs8 -topk8 -nocrypt}, or supply a
     * keystore via {@link #setKeystore(KeystoreConfig)}.
     */
    public ClientTlsConfig setPrivateKey(String privateKey) {
      this.privateKey = privateKey;
      return this;
    }

    public String getPrivateKeyPassphrase() {
      return privateKeyPassphrase;
    }

    /**
     * Reserved. Encrypted PEM keys are not supported by the file-based loader; use a JKS /
     * PKCS#12 keystore for password-protected keys.
     */
    public ClientTlsConfig setPrivateKeyPassphrase(String privateKeyPassphrase) {
      this.privateKeyPassphrase = privateKeyPassphrase;
      return this;
    }

    public Integer getCertRereadIntervalSeconds() {
      return certRereadIntervalSeconds;
    }

    /**
     * If set and {@code > 0}, the cert and private-key files are re-read on this interval (in
     * seconds) so external rotation tools can swap them in place. Only applies to the
     * file-based {@code cert}/{@code private_key} fields, not to keystores.
     */
    public ClientTlsConfig setCertRereadIntervalSeconds(Integer certRereadIntervalSeconds) {
      this.certRereadIntervalSeconds = certRereadIntervalSeconds;
      return this;
    }

    public KeystoreConfig getKeystore() {
      return keystore;
    }

    /**
     * JKS / PKCS#12 keystore providing the client cert and key. Mutually exclusive with {@link
     * #setCert(String) cert} / {@link #setPrivateKey(String) private_key}.
     */
    public ClientTlsConfig setKeystore(KeystoreConfig keystore) {
      this.keystore = keystore;
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
          + ", keystore="
          + keystore
          + '}';
    }
  }

  /**
   * JKS / PKCS#12 keystore holding a client certificate and private key. Either {@link
   * #setPath(String) path} or {@link #setKeyStore(KeyStore) keyStore} must be set, but not both.
   */
  public static class KeystoreConfig {
    private String path;
    private String password;

    @JsonProperty("key_password")
    private String keyPassword;

    private String type;

    @JsonIgnore private KeyStore keyStore;

    public String getPath() {
      return path;
    }

    public KeystoreConfig setPath(String path) {
      this.path = path;
      return this;
    }

    public String getPassword() {
      return password;
    }

    public KeystoreConfig setPassword(String password) {
      this.password = password;
      return this;
    }

    public String getKeyPassword() {
      return keyPassword;
    }

    /**
     * Password for the private key entry within the keystore. Defaults to {@link
     * #setPassword(String) password} when unset, matching standard {@code keytool} usage.
     */
    public KeystoreConfig setKeyPassword(String keyPassword) {
      this.keyPassword = keyPassword;
      return this;
    }

    public String getType() {
      return type;
    }

    /**
     * Keystore type. Defaults to {@code PKCS12} (or inferred from the file extension when {@link
     * #setPath(String) path} ends in {@code .jks}).
     */
    public KeystoreConfig setType(String type) {
      this.type = type;
      return this;
    }

    public KeyStore getKeyStore() {
      return keyStore;
    }

    /**
     * Programmatic SDK pass-through for an in-memory {@link KeyStore}. When set, {@link
     * #setPath(String) path} must be {@code null}.
     */
    public KeystoreConfig setKeyStore(KeyStore keyStore) {
      this.keyStore = keyStore;
      return this;
    }

    @Override
    public String toString() {
      return "KeystoreConfig{path='"
          + path
          + "', type='"
          + type
          + "', password="
          + (password == null ? "null" : "<redacted>")
          + ", keyPassword="
          + (keyPassword == null ? "null" : "<redacted>")
          + ", keyStore="
          + (keyStore == null ? "null" : "<programmatic>")
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
