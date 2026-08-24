package io.github.open_policy_agent.opa.plugins;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.util.HashSet;
import java.util.Locale;
import java.util.OptionalLong;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import io.github.open_policy_agent.opa.config.Config;

/**
 * Base class for downloading and activating bundles from remote or local sources.
 *
 * <p>Handles common functionality for bundle downloads including:
 *
 * <ul>
 *   <li>HTTP/HTTPS downloads with ETag caching
 *   <li>file:// URI and filesystem path support
 *   <li>File modification time tracking
 *   <li>Polling with configurable intervals
 *   <li>Initial activation futures
 * </ul>
 *
 * <p>Subclasses must implement {@link #activateBundle(byte[])} to define how the downloaded bundle
 * data should be processed.
 */
public abstract class BundleDownloader {
  protected final String name;
  protected final PluginManager manager;
  protected final ServicePlugin.Service authService;
  protected final HttpClient httpClient;
  protected final CompletableFuture<Void> initialActivation;

  protected String service;
  protected String resource;
  protected Config.PollingConfig polling;
  protected volatile String etag;
  protected volatile long lastModifiedTime = 0;
  protected long maxSizeBytes = Config.BundleConfig.DEFAULT_MAX_SIZE_BYTES;

  // Fallback when no service config is available; matches the response_header_timeout_seconds
  // default in Config.ServiceConfig.
  private static final int DEFAULT_RESPONSE_HEADER_TIMEOUT_SECONDS = 10;

  private static final int READ_BUFFER_BYTES = 8192;

  private static final Set<String> ALLOWED_CONTENT_TYPES =
      Set.of(
          "application/vnd.openpolicyagent.bundle.ir.v1+gzip",
          "application/vnd.openpolicyagent.bundles",
          "application/gzip",
          "application/x-gzip",
          "application/octet-stream",
          "binary/octet-stream",
          "application/x-tar");

  /**
   * Default {@code Accept} for bundle requests: the media types this SDK can consume, in preference
   * order, weighted with q-factors.
   *
   * <p>This SDK evaluates the IR ("plan") bundle format, so IR is requested first. The remaining
   * entries keep the request working against servers that don't negotiate — a plain OPA bundle, any
   * gzip, and finally a {@code &#42;/&#42;} catch-all for servers that ignore {@code Accept}
   * entirely and just serve a tarball. Because the weights are only relative, a server that
   * understands none of the named types can still respond rather than returning {@code 406 Not
   * Acceptable}.
   *
   * <p>Applied <em>before</em> the service config's {@code headers} are merged in, so an operator
   * can override it per service — see {@link ServicePlugin.Service#applyHeaders}.
   */
  static final String DEFAULT_ACCEPT_HEADER =
      String.join(
          ", ",
          "application/vnd.openpolicyagent.bundle.ir.v1+gzip;q=1.0",
          "application/vnd.openpolicyagent.bundles;q=0.9",
          "application/gzip;q=0.8",
          "*/*;q=0.1");

  /**
   * Construct a BundleDownloader.
   *
   * @param name bundle name (used in log messages)
   * @param manager the owning plugin manager
   * @param authService the {@link ServicePlugin.Service} that owns the {@link HttpClient} (with
   *     its SSLContext, credentials, and headers) used for HTTP downloads. May be {@code null} if
   *     this downloader only ever handles {@code file://} URIs or filesystem paths; in that case
   *     a default HTTP client is used for any unauthenticated HTTP fallback.
   */
  protected BundleDownloader(
      String name, PluginManager manager, ServicePlugin.Service authService) {
    this.name = name;
    this.manager = manager;
    this.authService = authService;
    this.httpClient = authService != null ? authService.getClient() : defaultHttpClient();
    this.initialActivation = new CompletableFuture<>();
  }

  private static HttpClient defaultHttpClient() {
    return HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .connectTimeout(Duration.ofSeconds(10))
        .build();
  }

  /**
   * Build a daemon scheduler with the "drop pending delayed tasks on shutdown" policy. Suitable
   * for chained-delay polling and for periodic cert/config reloads — in both cases {@code
   * shutdown()} returns promptly without waiting through the next scheduled tick.
   */
  public static ScheduledExecutorService newPollScheduler(int poolSize, String threadName) {
    ScheduledThreadPoolExecutor exec =
        new ScheduledThreadPoolExecutor(
            poolSize,
            r -> {
              Thread t = new Thread(r, threadName);
              t.setDaemon(true);
              return t;
            });
    exec.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
    return exec;
  }

  public static ScheduledExecutorService newPollScheduler(String threadName) {
    return newPollScheduler(1, threadName);
  }

  /**
   * Validate a {@link Config.PollingConfig}. Each error is prefixed with {@code subjectPrefix}
   * (e.g. {@code "Bundle 'authz'"} or {@code "Discovery"}) so the caller can mix it into its own
   * error set.
   */
  public static Set<String> validatePolling(
      Config.PollingConfig polling, String subjectPrefix) {
    Set<String> errors = new HashSet<>();
    if (polling == null) {
      return errors;
    }
    Integer min = polling.getMinDelaySeconds();
    Integer max = polling.getMaxDelaySeconds();
    if (min != null && min < 0) {
      errors.add(subjectPrefix + " polling.min_delay_seconds must be >= 0");
    }
    if (max != null && max < 0) {
      errors.add(subjectPrefix + " polling.max_delay_seconds must be >= 0");
    }
    if (min != null && max != null && min >= 0 && max >= 0 && min > max) {
      errors.add(
          subjectPrefix
              + " polling.min_delay_seconds ("
              + min
              + ") must be <= max_delay_seconds ("
              + max
              + ")");
    }
    return errors;
  }

  public BundleDownloader setService(String service) {
    this.service = service;
    return this;
  }

  public BundleDownloader setResource(String resource) {
    this.resource = resource;
    return this;
  }

  public BundleDownloader setPolling(Config.PollingConfig polling) {
    this.polling = polling;
    return this;
  }

  public BundleDownloader setMaxSizeBytes(long maxSizeBytes) {
    this.maxSizeBytes = maxSizeBytes;
    return this;
  }

  /**
   * @return a future that completes when the first bundle download succeeds, or completes
   *     exceptionally with the underlying download/activation error
   */
  public CompletableFuture<Void> getInitialActivation() {
    return initialActivation;
  }

  /**
   * Start polling for bundle updates.
   *
   * @param scheduler the scheduler to use for periodic downloads
   * @return a future that completes when the initial bundle is downloaded and activated
   */
  public CompletableFuture<Void> startPolling(ScheduledExecutorService scheduler) {
    int minDelay =
        (polling != null && polling.getMinDelaySeconds() != null)
            ? polling.getMinDelaySeconds()
            : 60;
    int maxDelay =
        (polling != null && polling.getMaxDelaySeconds() != null)
            ? polling.getMaxDelaySeconds()
            : 120;

    scheduleDownload(scheduler, minDelay, maxDelay, 0);

    return initialActivation;
  }

  // Schedules one download after delaySeconds;
  // once that download completes, schedules the next poll with a fresh jittered delay in [minDelay, maxDelay].
  // Because the next poll is chained off the download's completion — not fired on an independent timer —
  // at most one download is ever in flight, matching Go-OPA's jittered polling.
  // ScheduledExecutorService has no built-in jitter, so the task chains itself; a
  // RejectedExecutionException after shutdown ends the chain cleanly.
  private void scheduleDownload(
      ScheduledExecutorService scheduler, int minDelay, int maxDelay, long delaySeconds) {
    try {
      scheduler.schedule(
          () ->
              downloadBundle()
                  .whenComplete(
                      (result, throwable) ->
                          scheduleDownload(
                              scheduler,
                              minDelay,
                              maxDelay,
                              nextDelaySeconds(minDelay, maxDelay))),
          delaySeconds,
          TimeUnit.SECONDS);
    } catch (RejectedExecutionException stopped) {
      // Scheduler was shut down; let the chain end.
    }
  }

  private static long nextDelaySeconds(int minDelay, int maxDelay) {
    return minDelay >= maxDelay
        ? minDelay
        : ThreadLocalRandom.current().nextLong(minDelay, (long) maxDelay + 1);
  }

  // Per-request download timeout.
  private Duration requestTimeout() {
    int seconds =
        authService != null
            ? authService.getResponseHeaderTimeoutSeconds()
            : DEFAULT_RESPONSE_HEADER_TIMEOUT_SECONDS;
    return Duration.ofSeconds(seconds > 0 ? seconds : DEFAULT_RESPONSE_HEADER_TIMEOUT_SECONDS);
  }

  /**
   * Download the bundle from the configured service and resource.
   *
   * <p>Handles HTTP/HTTPS downloads with ETag caching, file:// URIs, and filesystem paths. Calls
   * {@link #activateBundle(byte[])} when new bundle data is available.
   *
   * @return a future that completes when this download attempt — including any asynchronous
   *     activation — has finished. The poll scheduler awaits it before scheduling the next poll, so
   *     downloads never overlap.
   */
  protected CompletableFuture<Void> downloadBundle() {
    try {
      Config.ServiceConfig serviceConfig = manager.getConfig().getService(service);
      if (serviceConfig == null) {
        manager.getLogger().error("Bundle '%s': Service '%s' not found", name, service);
        if (!initialActivation.isDone()) {
          initialActivation.completeExceptionally(
              new RuntimeException("Service '" + service + "' not found"));
        }
        return CompletableFuture.completedFuture(null);
      }

      String baseUrl = serviceConfig.getUrl();
      String fullPath;

      // Check if baseUrl is a URI scheme (http://, https://, file://)
      if (baseUrl.matches("^[a-zA-Z][a-zA-Z0-9+.-]*://.*")) {
        if (baseUrl.endsWith("/") || resource.startsWith("/")) {
          fullPath = baseUrl + resource;
        } else {
          fullPath = baseUrl + "/" + resource;
        }
        URI uri = URI.create(fullPath);

        // Handle file:// URIs
        if ("file".equalsIgnoreCase(uri.getScheme())) {
          handleFileDownload(Paths.get(uri));
          return CompletableFuture.completedFuture(null);
        }

        // Handle HTTP/HTTPS URIs
        return handleHttpDownload(uri);
      } else {
        // It's a file path (relative or absolute)
        Path basePath = Paths.get(baseUrl);
        Path filePath = basePath.resolve(resource);
        handleFileDownload(filePath);
        return CompletableFuture.completedFuture(null);
      }

    } catch (Exception e) {
      manager.getLogger().error("Bundle '%s': Download error: %s", name, e.getMessage());
      if (!initialActivation.isDone()) {
        initialActivation.completeExceptionally(e);
      }
      return CompletableFuture.completedFuture(null);
    }
  }

  /**
   * Handle downloading from a file:// URI or filesystem path.
   *
   * @param filePath the path to the file
   */
  private void handleFileDownload(Path filePath) throws IOException {
    FileTime currentModTime = Files.getLastModifiedTime(filePath);
    long currentModTimeMillis = currentModTime.toMillis();

    if (lastModifiedTime != 0 && lastModifiedTime == currentModTimeMillis) {
      manager.getLogger().debug("Bundle '%s': File not modified, skipping activation", name);
      if (!initialActivation.isDone()) {
        initialActivation.complete(null);
      }
      return;
    }

    byte[] bundleData = Files.readAllBytes(filePath);
    activateBundle(bundleData);
    lastModifiedTime = currentModTimeMillis;

    if (!initialActivation.isDone()) {
      initialActivation.complete(null);
    }
  }

  /**
   * Handle downloading from an HTTP/HTTPS URI.
   *
   * @param uri the URI to download from
   * @return a future that completes when the response has been handled. The actual outcome is
   *     recorded on {@code initialActivation}.
   */
  private CompletableFuture<Void> handleHttpDownload(URI uri) {
    HttpRequest.Builder requestBuilder =
        HttpRequest.newBuilder()
            .uri(uri)
            .timeout(requestTimeout())
            .header("Accept", DEFAULT_ACCEPT_HEADER)
            .GET();

    if (etag != null) {
      requestBuilder.header("If-None-Match", etag);
    }

    if (authService != null) {
      requestBuilder = authService.applyCredentials(requestBuilder);
      // Applied last, and with setHeader, so a service-config header wins over the defaults above.
      requestBuilder = authService.applyHeaders(requestBuilder);
    }

    HttpRequest request = requestBuilder.build();

    return httpClient
        .sendAsync(request, HttpResponse.BodyHandlers.ofInputStream())
        .handle(
            (response, throwable) -> {
              handleHttpResponse(response, throwable);
              return null;
            });
  }

  // Handles a completed HTTP exchange: records success or failure on initialActivation. Runs on the
  // HTTP client's executor thread, but the poll chain never starts the next download until the
  // future returned by handleHttpDownload completes, so activateBundle is never called concurrently.
  private void handleHttpResponse(HttpResponse<InputStream> response, Throwable throwable) {
    if (throwable != null) {
      Throwable cause =
          throwable instanceof java.util.concurrent.CompletionException
              ? throwable.getCause()
              : throwable;
      manager.getLogger().error("Bundle '%s': Download error: %s", name, cause.getMessage());
      if (!initialActivation.isDone()) {
        initialActivation.completeExceptionally(cause);
      }
      return;
    }

    if (response.statusCode() == 304) {
      manager.getLogger().debug("Bundle '%s': Not modified (ETag match)", name);
      if (!initialActivation.isDone()) {
        initialActivation.complete(null);
      }
      return;
    }

    if (response.statusCode() == 200) {
      String contentType = response.headers().firstValue("Content-Type").orElse("");
      if (!isAcceptableContentType(contentType)) {
        String errorMsg = "Unexpected Content-Type: '" + contentType + "'";
        manager.getLogger().error("Bundle '%s': %s", name, errorMsg);
        if (!initialActivation.isDone()) {
          initialActivation.completeExceptionally(new RuntimeException(errorMsg));
        }
        return;
      }
      OptionalLong contentLength = response.headers().firstValueAsLong("Content-Length");
      byte[] body;
      try {
        body = readBodyWithLimit(response.body(), contentLength);
      } catch (Exception e) {
        manager.getLogger().error("Bundle '%s': Download error: %s", name, e.getMessage());
        if (!initialActivation.isDone()) {
          initialActivation.completeExceptionally(e);
        }
        return;
      }
      response.headers().firstValue("ETag").ifPresent(newEtag -> this.etag = newEtag);
      try {
        activateBundle(body);
      } catch (Exception e) {
        manager.getLogger().error("Bundle '%s': Activation failed: %s", name, e.getMessage());
        if (!initialActivation.isDone()) {
          initialActivation.completeExceptionally(e);
        }
        return;
      }
      if (!initialActivation.isDone()) {
        initialActivation.complete(null);
      }
    } else {
      String errorMsg = "Download failed with status " + response.statusCode();
      manager.getLogger().error("Bundle '%s': %s", name, errorMsg);
      if (!initialActivation.isDone()) {
        initialActivation.completeExceptionally(new RuntimeException(errorMsg));
      }
    }
  }

  /**
   * Returns true if the response Content-Type is acceptable for a bundle.
   * A blank/absent value is tolerated.
   */
  private static boolean isAcceptableContentType(String contentType) {
    if (contentType.isBlank()) {
      return true;
    }
    String mediaType = contentType.split(";", 2)[0].strip().toLowerCase(Locale.ROOT);
    return ALLOWED_CONTENT_TYPES.contains(mediaType);
  }

  /**
   * Reads the response body into memory, enforcing {@code maxSizeBytes} both upfront (via
   * Content-Length) and as the bytes arrive.
   *
   * <p>The limit is capped at {@link Integer#MAX_VALUE} because the body is materialized into a
   * {@code byte[]}, whose maximum length is ~2 GB.
   */
  private byte[] readBodyWithLimit(InputStream body, OptionalLong contentLength)
      throws IOException {
    long limit = Math.min(maxSizeBytes, Integer.MAX_VALUE);
    try (InputStream in = body) {
      rejectIfContentLengthExceedsLimit(contentLength, limit);
      return readUpTo(in, limit);
    }
  }

  /**
   * Rejects a response whose advertised Content-Length already exceeds the limit, so an oversized
   * body is never read at all. Servers are turned away before a single byte is transferred.
   */
  private static void rejectIfContentLengthExceedsLimit(OptionalLong contentLength, long limit) {
    if (contentLength.isPresent() && contentLength.getAsLong() > limit) {
      throw new BundleSizeLimitException(
          "Content-Length " + contentLength.getAsLong() + " exceeds limit of " + limit + " bytes");
    }
  }

  /**
   * Reads the stream into a byte array, aborting the moment the cumulative size exceeds the limit.
   * A server that omits Content-Length (or uses chunked encoding) therefore cannot force the client
   * to buffer an unbounded body: the read stops mid-stream and the caller closes the stream, which
   * cancels the exchange so the remaining bytes are never transferred.
   */
  private static byte[] readUpTo(InputStream in, long limit) throws IOException {
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    byte[] chunk = new byte[READ_BUFFER_BYTES];
    long total = 0;
    int read;
    while ((read = in.read(chunk)) != -1) {
      total += read;
      if (total > limit) {
        throw new BundleSizeLimitException("Response body exceeds limit of " + limit + " bytes");
      }
      buffer.write(chunk, 0, read);
    }
    return buffer.toByteArray();
  }

  static class BundleSizeLimitException extends RuntimeException {
    BundleSizeLimitException(String message) {
      super(message);
    }
  }

  /**
   * Process and activate a downloaded bundle.
   *
   * <p>This method is called when new bundle data is available (either from an HTTP download, or
   * when a local file has been modified).
   *
   * @param bundleData the raw bundle bytes (typically a gzipped tarball)
   */
  protected abstract void activateBundle(byte[] bundleData);
}
