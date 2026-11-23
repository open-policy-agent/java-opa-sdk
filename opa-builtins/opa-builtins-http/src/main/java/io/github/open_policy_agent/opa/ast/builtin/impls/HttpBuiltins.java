package io.github.open_policy_agent.opa.ast.builtin.impls;

import java.io.IOException;
import java.net.ConnectException;
import java.net.ProxySelector;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.channels.UnresolvedAddressException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiFunction;
import javax.net.ssl.SSLException;
import io.github.open_policy_agent.opa.ast.builtin.BuiltinProvider;
import io.github.open_policy_agent.opa.ast.builtin.OpaBuiltin;
import io.github.open_policy_agent.opa.ast.builtin.OpaDynamic;
import io.github.open_policy_agent.opa.ast.builtin.OpaType;
import io.github.open_policy_agent.opa.ast.types.RegoInt32;
import io.github.open_policy_agent.opa.ast.types.RegoObject;
import io.github.open_policy_agent.opa.ast.types.RegoString;
import io.github.open_policy_agent.opa.ast.types.RegoValue;
import io.github.open_policy_agent.opa.cache.NdBuiltinCache;
import io.github.open_policy_agent.opa.rego.EvaluationContext;
import io.github.open_policy_agent.opa.rego.TypeError;

/**
 * Implements {@code http.send}, which sends an HTTP request and returns the response.
 *
 * <p>The behavior mirrors the reference OPA implementation in {@code v1/topdown/http.go}: the same
 * request parameters and validation, {@code raise_error}, retries, redirects, TLS options and
 * response object, and the same per-query caching of responses. {@code http.send} is
 * nondeterministic, so results also go through the nd_builtin_cache when one is configured.
 *
 * <p>Differences from OPA, all of which fail with an explicit error rather than being ignored:
 * {@code tls_server_name}, a {@code Host} header (and the other headers the JDK HTTP client
 * reserves) and {@code unix://} URLs are not supported. The inter-query cache ({@code cache},
 * {@code force_cache}) is not available; those parameters are validated and responses are cached
 * for the duration of the query only.
 */
public class HttpBuiltins implements BuiltinProvider {

  private static final String HTTP_SEND = "http.send";
  private static final String METRIC_KEY = "rego_builtin_http_send";
  private static final String NETWORK_REQUESTS_METRIC = METRIC_KEY + "_network_requests";

  /** Go's http.Client gives up after this many consecutive redirects. */
  private static final int MAX_REDIRECTS = 10;

  private static final long MIN_RETRY_DELAY_NANOS = 100_000_000L;
  private static final long MAX_RETRY_DELAY_NANOS = 60_000_000_000L;
  private static final double RETRY_BACKOFF_FACTOR = 1.6;
  private static final double RETRY_JITTER = 0.2;

  /** Headers that must not follow a redirect to a different host. */
  private static final Set<String> SENSITIVE_HEADERS = Set.of("Authorization", "Www-Authenticate", "Cookie", "Cookie2");

  /** Headers describing a request body, dropped when a redirect turns the request into a GET. */
  private static final Set<String> BODY_HEADERS = Set.of("Content-Encoding", "Content-Language", "Content-Location", "Content-Type");

  /**
   * Responses remembered for the duration of one query, keyed by the evaluation context, which is
   * created per query. OPA keeps these in the builtin context's cache so that repeated identical
   * calls in a query see the same result.
   */
  private static final Map<EvaluationContext, QueryCache> QUERY_CACHES = Collections.synchronizedMap(new WeakHashMap<>());

  /** Shared by requests that need no custom TLS setup. */
  private static final class DefaultClient {
    static final HttpClient INSTANCE = clientBuilder().build();
  }

  /** Redirects are followed by {@link #attempt}, which knows when credentials must be dropped. */
  private static HttpClient.Builder clientBuilder() {
    HttpClient.Builder builder = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER);
    ProxySelector proxies = ProxySelector.getDefault();
    return proxies == null ? builder : builder.proxy(proxies);
  }

  private static final int MAX_CUSTOM_CLIENTS = 16;

  /** Least recently used clients for requests with TLS options, by their TLS material. */
  private static final Map<HttpSendTls.Material, HttpClient> CUSTOM_CLIENTS =
      Collections.synchronizedMap(
          new LinkedHashMap<HttpSendTls.Material, HttpClient>(16, 0.75f, true) {
            private static final long serialVersionUID = 1L;

            @Override
            protected boolean removeEldestEntry(Map.Entry<HttpSendTls.Material, HttpClient> eldest) {
              return size() > MAX_CUSTOM_CLIENTS;
            }
          });

  /** The outcome of an earlier request in the same query: a response, or why there was none. */
  private static final class CacheEntry {
    final RegoValue response;
    final HttpSendFailure failure;

    CacheEntry(RegoValue response, HttpSendFailure failure) {
      this.response = response;
      this.failure = failure;
    }
  }

  private static final class QueryCache {
    final Map<RegoObject, CacheEntry> entries = new ConcurrentHashMap<>();
  }

  @Override
  public Map<String, BiFunction<EvaluationContext, RegoValue[], RegoValue>> builtins() {
    HttpBuiltins instance = new HttpBuiltins();
    return Map.of(HTTP_SEND, instance::send);
  }

  @OpaBuiltin(
      name = HTTP_SEND,
      description = "Returns a HTTP response to the given HTTP request.",
      args = {
        @OpaType(
            type = "object",
            name = "request",
            description = "the HTTP request object",
            dynamic = @OpaDynamic(keyType = "string", valueType = "any"))
      },
      result =
          @OpaType(
              type = "object",
              name = "response",
              description = "the HTTP response object",
              dynamic = @OpaDynamic(keyType = "any", valueType = "any")),
      nondeterministic = true)
  public RegoValue send(EvaluationContext ctx, RegoValue[] args) {
    NdBuiltinCache ndCache = ctx.getNdBuiltinCache();
    if (ndCache != null) {
      RegoValue cached = ndCache.get(HTTP_SEND, args);
      if (cached != null) {
        return cached;
      }
    }

    RegoValue result = sendRequest(ctx, args);

    // Cache and record this nondeterministic value for decision logging
    if (ndCache != null) {
      ndCache.put(HTTP_SEND, args, result);
    }
    ctx.recordNdCacheValue(HTTP_SEND, args, result);
    return result;
  }

  private RegoValue sendRequest(EvaluationContext ctx, RegoValue[] args) {
    if (!(args[0] instanceof RegoObject)) {
      throw new TypeError("operand 1 must be object but got " + args[0].getTypeName());
    }
    RegoObject obj = (RegoObject) args[0];

    boolean raiseError = HttpSendRequest.raiseError(obj);
    try {
      HttpSendRequest.validateKeys(obj);
      return timedResponse(ctx, obj);
    } catch (HttpSendFailure failure) {
      if (raiseError) {
        throw failure.toOpaException();
      }
      return errorResult(failure);
    }
  }

  /** {@code {"status_code": 0, "error": {"code": ..., "message": ...}}}, the result of a failed call with raise_error false. */
  private static RegoObject errorResult(HttpSendFailure failure) {
    RegoObject error = new RegoObject();
    error.setProperty("code", new RegoString(failure.code()));
    error.setProperty("message", new RegoString(failure.getMessage()));
    RegoObject result = new RegoObject();
    result.setProperty("status_code", RegoInt32.of(0));
    result.setProperty("error", error);
    return result;
  }

  private RegoValue timedResponse(EvaluationContext ctx, RegoObject obj) {
    boolean timed = ctx.metrics != null;
    if (timed) {
      ctx.metrics.timer(METRIC_KEY).start();
    }
    try {
      return response(ctx, obj);
    } finally {
      if (timed) {
        ctx.metrics.timer(METRIC_KEY).stop();
      }
    }
  }

  private RegoValue response(EvaluationContext ctx, RegoObject obj) {
    RegoObject key = HttpSendRequest.cacheKey(obj);
    HttpSendRequest.validateCacheParameters(obj);

    QueryCache cache = QUERY_CACHES.computeIfAbsent(ctx, c -> new QueryCache());
    CacheEntry cached = cache.entries.get(key);
    if (cached != null) {
      if (cached.failure != null) {
        throw cached.failure;
      }
      return cached.response;
    }

    try {
      HttpSendRequest request = HttpSendRequest.parse(obj);
      if (ctx.metrics != null) {
        ctx.metrics.counter(NETWORK_REQUESTS_METRIC).incr();
      }
      HttpResponse<byte[]> raw = execute(request);
      RegoObject result =
          HttpSendResponse.toRego(
              ctx, raw.statusCode(), raw.headers().map(), raw.body(), request.forceJsonDecode, request.forceYamlDecode);
      if (HttpSendResponse.isCacheable(raw.statusCode())) {
        cache.entries.put(key, new CacheEntry(result, null));
      }
      return result;
    } catch (HttpSendFailure failure) {
      cache.entries.put(key, new CacheEntry(null, failure));
      throw failure;
    }
  }

  // ---------------------------------------------------------------------------------------------
  // Sending
  // ---------------------------------------------------------------------------------------------

  /** Sends the request, retrying failed attempts up to {@code max_retry_attempts} times. */
  private static HttpResponse<byte[]> execute(HttpSendRequest request) {
    HttpClient client = request.tls.isCustom() ? customClient(request.tls) : DefaultClient.INSTANCE;
    for (int attempt = 0; ; attempt++) {
      try {
        return attempt(client, request);
      } catch (HttpSendFailure failure) {
        if (attempt >= request.maxRetryAttempts || !HttpSendFailure.NETWORK_ERROR_CODE.equals(failure.code())) {
          throw failure;
        }
        pause(retryDelayNanos(attempt), request);
      }
    }
  }

  /**
   * The client for a request with TLS options. Clients with the same TLS material are shared: each
   * client owns threads that only go away once it is garbage collected, so one per request would pile
   * them up under load.
   */
  private static HttpClient customClient(HttpSendTls tls) {
    HttpSendTls.Material material = tls.resolve();
    return CUSTOM_CLIENTS.computeIfAbsent(material, m -> clientBuilder().sslContext(HttpSendTls.sslContext(m)).build());
  }

  /** One attempt: the request plus, with {@code enable_redirect}, the redirects it leads to. */
  private static HttpResponse<byte[]> attempt(HttpClient client, HttpSendRequest request) {
    long deadline = request.timeoutNanos > 0 ? System.nanoTime() + request.timeoutNanos : 0;
    String method = request.method;
    byte[] body = request.body;
    Map<String, String> headers = request.headers;
    URI uri = request.uri;

    for (int redirects = 0; ; redirects++) {
      HttpResponse<byte[]> response = exchange(client, request, method, uri, headers, body, deadline);
      int status = response.statusCode();
      boolean redirect = status == 301 || status == 302 || status == 303 || status == 307 || status == 308;
      String location = response.headers().firstValue("Location").orElse("");
      if (!request.enableRedirect || !redirect || location.isEmpty()) {
        return response;
      }
      if (redirects + 1 >= MAX_REDIRECTS) {
        throw HttpSendFailure.network(request.method, request.url, false, "stopped after " + MAX_REDIRECTS + " redirects");
      }

      URI target;
      try {
        target = uri.resolve(location);
      } catch (IllegalArgumentException e) {
        throw HttpSendFailure.network(request.method, request.url, false, "failed to parse Location header \"" + location + "\"");
      }
      headers = redirectHeaders(headers, uri, target, status == 307 || status == 308);
      if (status != 307 && status != 308) {
        // 301, 302 and 303 turn into a GET without a body, except for HEAD.
        if (!"HEAD".equals(method)) {
          method = "GET";
        }
        body = new byte[0];
      }
      uri = target;
    }
  }

  private static HttpResponse<byte[]> exchange(
      HttpClient client, HttpSendRequest request, String method, URI uri, Map<String, String> headers, byte[] body, long deadline) {
    HttpRequest.Builder builder = HttpRequest.newBuilder(uri);
    // Plain HTTP stays on HTTP/1.1 like Go's client: the JDK would otherwise offer an h2c upgrade.
    builder.version("https".equalsIgnoreCase(uri.getScheme()) ? HttpClient.Version.HTTP_2 : HttpClient.Version.HTTP_1_1);
    try {
      builder.method(method, body.length == 0 ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofByteArray(body));
      for (Map.Entry<String, String> header : headers.entrySet()) {
        builder.header(header.getKey(), header.getValue());
      }
    } catch (IllegalArgumentException e) {
      throw HttpSendFailure.internal(e.getMessage());
    }
    HttpRequest httpRequest = builder.build();

    CompletableFuture<HttpResponse<byte[]>> future = client.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofByteArray());
    try {
      if (deadline == 0) {
        return future.get();
      }
      long remaining = deadline - System.nanoTime();
      if (remaining <= 0) {
        throw new TimeoutException();
      }
      return future.get(remaining, TimeUnit.NANOSECONDS);
    } catch (TimeoutException e) {
      future.cancel(true);
      throw HttpSendFailure.network(
          request.method, request.url, true, "context deadline exceeded (Client.Timeout exceeded while awaiting headers)");
    } catch (InterruptedException e) {
      future.cancel(true);
      Thread.currentThread().interrupt();
      throw HttpSendFailure.internal("http.send: request cancelled");
    } catch (ExecutionException e) {
      throw networkFailure(request, e.getCause() == null ? e : e.getCause());
    }
  }

  private static HttpSendFailure networkFailure(HttpSendRequest request, Throwable cause) {
    String host = request.uri.getHost();
    int port = request.uri.getPort() >= 0 ? request.uri.getPort() : ("https".equalsIgnoreCase(request.uri.getScheme()) ? 443 : 80);
    if (cause instanceof HttpTimeoutException || cause instanceof HttpConnectTimeoutException) {
      return HttpSendFailure.network(request.method, request.url, true, "context deadline exceeded (Client.Timeout exceeded while awaiting headers)");
    }
    if (cause instanceof UnknownHostException || cause instanceof UnresolvedAddressException) {
      return HttpSendFailure.network(request.method, request.url, false, "dial tcp: lookup " + host + ": no such host");
    }
    if (cause instanceof ConnectException) {
      String reason = cause.getMessage() == null ? "connection refused" : cause.getMessage().toLowerCase(Locale.ROOT);
      return HttpSendFailure.network(request.method, request.url, false, "dial tcp " + host + ":" + port + ": connect: " + reason);
    }
    if (cause instanceof SSLException) {
      return HttpSendFailure.network(request.method, request.url, false, "tls: " + cause.getMessage());
    }
    if (cause instanceof IOException || cause instanceof IllegalArgumentException) {
      String message = cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
      return HttpSendFailure.network(request.method, request.url, false, message);
    }
    return HttpSendFailure.internal(String.valueOf(cause));
  }

  /**
   * The headers to send to a redirect target. As in Go, credentials are not forwarded to a different
   * host (a sub-domain of the original counts as the same), and a redirect that becomes a GET drops
   * the headers that describe the body.
   */
  private static Map<String, String> redirectHeaders(Map<String, String> headers, URI from, URI to, boolean keepBody) {
    boolean sameDomain = isDomainOrSubdomain(to.getHost(), from.getHost());
    Map<String, String> out = new LinkedHashMap<>();
    for (Map.Entry<String, String> header : headers.entrySet()) {
      if (!sameDomain && SENSITIVE_HEADERS.contains(header.getKey())) {
        continue;
      }
      if (!keepBody && BODY_HEADERS.contains(header.getKey())) {
        continue;
      }
      out.put(header.getKey(), header.getValue());
    }
    return out;
  }

  private static boolean isDomainOrSubdomain(String sub, String parent) {
    if (sub == null || parent == null) {
      return false;
    }
    String s = sub.toLowerCase(Locale.ROOT);
    String p = parent.toLowerCase(Locale.ROOT);
    return s.equals(p) || s.endsWith("." + p);
  }

  private static long retryDelayNanos(int attempt) {
    double delay = Math.min(MIN_RETRY_DELAY_NANOS * Math.pow(RETRY_BACKOFF_FACTOR, attempt), MAX_RETRY_DELAY_NANOS);
    double jitter = 1 + RETRY_JITTER * (2 * Math.random() - 1);
    return (long) (delay * jitter);
  }

  private static void pause(long nanos, HttpSendRequest request) {
    try {
      TimeUnit.NANOSECONDS.sleep(nanos);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw HttpSendFailure.network(request.method, request.url, false, "context canceled");
    }
  }
}
