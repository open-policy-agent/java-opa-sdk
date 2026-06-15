package io.github.open_policy_agent.opa.plugins;

import java.io.IOException;
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
  protected String etag;
  protected long lastModifiedTime = 0;

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

    scheduler.schedule(this::downloadBundle, 0, TimeUnit.SECONDS);
    scheduleNextPoll(scheduler, minDelay, maxDelay);

    return initialActivation;
  }

  // Re-schedules the next download with a uniformly random delay in [minDelay, maxDelay],
  // matching Go-OPA's jittered polling. ScheduledExecutorService has no built-in jitter, so the
  // task chains itself. RejectedExecutionException after a shutdown breaks the chain cleanly.
  private void scheduleNextPoll(ScheduledExecutorService scheduler, int minDelay, int maxDelay) {
    long delay =
        minDelay >= maxDelay
            ? minDelay
            : ThreadLocalRandom.current().nextLong(minDelay, (long) maxDelay + 1);
    try {
      scheduler.schedule(
          () -> {
            try {
              downloadBundle();
            } catch (Exception e) {
              // downloadBundle() handles its own logging; swallow so the chain keeps polling.
              // Only Exception is caught here — Errors (OOM, etc.) propagate and let the
              // executor's uncaught-exception handler tear down the pool, which is the right
              // outcome for unrecoverable conditions.
            } finally {
              scheduleNextPoll(scheduler, minDelay, maxDelay);
            }
          },
          delay,
          TimeUnit.SECONDS);
    } catch (RejectedExecutionException stopped) {
      // Scheduler was shut down; let the chain end.
    }
  }

  /**
   * Download the bundle from the configured service and resource.
   *
   * <p>Handles HTTP/HTTPS downloads with ETag caching, file:// URIs, and filesystem paths. Calls
   * {@link #activateBundle(byte[])} when new bundle data is available.
   */
  protected void downloadBundle() {
    try {
      Config.ServiceConfig serviceConfig = manager.getConfig().getService(service);
      if (serviceConfig == null) {
        manager.getLogger().error("Bundle '%s': Service '%s' not found", name, service);
        if (!initialActivation.isDone()) {
          initialActivation.completeExceptionally(
              new RuntimeException("Service '" + service + "' not found"));
        }
        return;
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
          return;
        }

        // Handle HTTP/HTTPS URIs
        handleHttpDownload(uri);
      } else {
        // It's a file path (relative or absolute)
        Path basePath = Paths.get(baseUrl);
        Path filePath = basePath.resolve(resource);
        handleFileDownload(filePath);
      }

    } catch (Exception e) {
      manager.getLogger().error("Bundle '%s': Download error: %s", name, e.getMessage());
      if (!initialActivation.isDone()) {
        initialActivation.completeExceptionally(e);
      }
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
   */
  private void handleHttpDownload(URI uri) throws IOException, InterruptedException {
    HttpRequest.Builder requestBuilder =
        HttpRequest.newBuilder()
            .uri(uri)
            .header("Accept", "application/vnd.openpolicyagent.bundles")
            .GET();

    if (etag != null) {
      requestBuilder.header("If-None-Match", etag);
    }

    if (authService != null) {
      requestBuilder = authService.applyCredentials(requestBuilder);
      requestBuilder = authService.applyHeaders(requestBuilder);
    }

    HttpRequest request = requestBuilder.build();
    HttpResponse<byte[]> response =
        httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());

    if (response.statusCode() == 304) {
      manager.getLogger().debug("Bundle '%s': Not modified (ETag match)", name);
      if (!initialActivation.isDone()) {
        initialActivation.complete(null);
      }
      return;
    }

    if (response.statusCode() == 200) {
      response.headers().firstValue("ETag").ifPresent(newEtag -> this.etag = newEtag);
      activateBundle(response.body());
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
   * Process and activate a downloaded bundle.
   *
   * <p>This method is called when new bundle data is available (either from an HTTP download, or
   * when a local file has been modified).
   *
   * @param bundleData the raw bundle bytes (typically a gzipped tarball)
   */
  protected abstract void activateBundle(byte[] bundleData);
}
