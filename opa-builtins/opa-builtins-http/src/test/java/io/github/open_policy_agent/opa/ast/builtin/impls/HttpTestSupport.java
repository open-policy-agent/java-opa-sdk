package io.github.open_policy_agent.opa.ast.builtin.impls;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import io.github.open_policy_agent.opa.ast.builtin.BuiltinRegistry;
import io.github.open_policy_agent.opa.ast.types.RegoArray;
import io.github.open_policy_agent.opa.ast.types.RegoBigInt;
import io.github.open_policy_agent.opa.ast.types.RegoBoolean;
import io.github.open_policy_agent.opa.ast.types.RegoInt32;
import io.github.open_policy_agent.opa.ast.types.RegoObject;
import io.github.open_policy_agent.opa.ast.types.RegoString;
import io.github.open_policy_agent.opa.ast.types.RegoValue;
import io.github.open_policy_agent.opa.rego.EvaluationContext;

/** Helpers shared by the http.send tests: a recording local server and Rego value builders. */
final class HttpTestSupport {

  private static final BuiltinRegistry REGISTRY = BuiltinRegistry.allCapabilities();

  private HttpTestSupport() {}

  /** A fresh evaluation context, which is also a fresh per-query http.send cache. */
  static EvaluationContext newContext() {
    return new EvaluationContext.Builder().withBuiltinRegistry(REGISTRY).build();
  }

  static RegoValue send(EvaluationContext ctx, RegoObject request) {
    return new HttpBuiltins().send(ctx, new RegoValue[] {request});
  }

  static RegoObject asObject(RegoValue value) {
    return (RegoObject) value;
  }

  static RegoValue get(RegoValue object, String key) {
    return ((RegoObject) object).getProperty(key);
  }

  static String text(RegoValue value) {
    return ((RegoString) value).getValue();
  }

  static int intValue(RegoValue value) {
    return ((RegoInt32) value).getValue();
  }

  /** Builds an object from alternating keys and values; plain Java values are converted to Rego. */
  static RegoObject obj(Object... keysAndValues) {
    RegoObject out = new RegoObject();
    for (int i = 0; i < keysAndValues.length; i += 2) {
      out.setProperty((String) keysAndValues[i], value(keysAndValues[i + 1]));
    }
    return out;
  }

  static RegoValue value(Object o) {
    if (o instanceof RegoValue) {
      return (RegoValue) o;
    }
    if (o instanceof String) {
      return new RegoString((String) o);
    }
    if (o instanceof Boolean) {
      return RegoBoolean.of((Boolean) o);
    }
    if (o instanceof Integer) {
      return RegoInt32.of((Integer) o);
    }
    if (o instanceof Long) {
      return new RegoBigInt((Long) o);
    }
    if (o instanceof List) {
      List<RegoValue> items = new ArrayList<>();
      for (Object item : (List<?>) o) {
        items.add(value(item));
      }
      return new RegoArray(items);
    }
    throw new IllegalArgumentException("unsupported test value: " + o);
  }

  /** One request as seen by the server. */
  static final class Seen {
    final String method;
    final String path;
    final Map<String, List<String>> headers;
    final String body;

    Seen(String method, String path, Map<String, List<String>> headers, String body) {
      this.method = method;
      this.path = path;
      this.headers = headers;
      this.body = body;
    }

    String header(String name) {
      for (Map.Entry<String, List<String>> e : headers.entrySet()) {
        if (e.getKey().equalsIgnoreCase(name)) {
          return e.getValue().get(0);
        }
      }
      return null;
    }
  }

  /** A local HTTP server on a free port that records what it receives. */
  static final class Server implements AutoCloseable {
    private final HttpServer server;
    private final Map<String, AtomicInteger> hits = new ConcurrentHashMap<>();
    private final List<Seen> seen = new CopyOnWriteArrayList<>();

    Server() throws IOException {
      // The wildcard address lets tests reach the server as both 127.0.0.1 and localhost.
      server = HttpServer.create(new InetSocketAddress(0), 0);
      server.setExecutor(Executors.newCachedThreadPool());
    }

    /** Registers a handler for a path; the request is recorded before the handler runs. */
    Server on(String path, HttpHandler handler) {
      server.createContext(
          path,
          exchange -> {
            hits.computeIfAbsent(exchange.getRequestURI().getPath(), p -> new AtomicInteger()).incrementAndGet();
            seen.add(new Seen(exchange.getRequestMethod(), exchange.getRequestURI().toString(), exchange.getRequestHeaders(), readBody(exchange)));
            handler.handle(exchange);
          });
      return this;
    }

    Server reply(String path, int status, String contentType, String body) {
      return on(path, exchange -> respond(exchange, status, contentType, body));
    }

    Server start() {
      server.start();
      return this;
    }

    int port() {
      return server.getAddress().getPort();
    }

    String url(String path) {
      return "http://127.0.0.1:" + port() + path;
    }

    int hits(String path) {
      AtomicInteger n = hits.get(path);
      return n == null ? 0 : n.get();
    }

    Seen last() {
      return seen.get(seen.size() - 1);
    }

    @Override
    public void close() {
      server.stop(0);
    }
  }

  static void respond(HttpExchange exchange, int status, String contentType, String body) throws IOException {
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    if (contentType != null) {
      exchange.getResponseHeaders().add("Content-Type", contentType);
    }
    // -1 sends no body at all (needed for 204 and HEAD).
    exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
    if (bytes.length > 0) {
      exchange.getResponseBody().write(bytes);
    }
    exchange.close();
  }

  private static String readBody(HttpExchange exchange) throws IOException {
    try (InputStream in = exchange.getRequestBody()) {
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }
}
