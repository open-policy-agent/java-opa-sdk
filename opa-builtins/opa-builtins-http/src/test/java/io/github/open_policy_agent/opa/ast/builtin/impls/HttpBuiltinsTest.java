package io.github.open_policy_agent.opa.ast.builtin.impls;

import static io.github.open_policy_agent.opa.ast.builtin.impls.HttpTestSupport.get;
import static io.github.open_policy_agent.opa.ast.builtin.impls.HttpTestSupport.intValue;
import static io.github.open_policy_agent.opa.ast.builtin.impls.HttpTestSupport.newContext;
import static io.github.open_policy_agent.opa.ast.builtin.impls.HttpTestSupport.obj;
import static io.github.open_policy_agent.opa.ast.builtin.impls.HttpTestSupport.respond;
import static io.github.open_policy_agent.opa.ast.builtin.impls.HttpTestSupport.send;
import static io.github.open_policy_agent.opa.ast.builtin.impls.HttpTestSupport.text;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.List;
import io.github.open_policy_agent.opa.ast.builtin.BuiltinError;
import io.github.open_policy_agent.opa.ast.builtin.CapabilitiesGenerator;
import io.github.open_policy_agent.opa.ast.builtin.Descriptor;
import io.github.open_policy_agent.opa.ast.types.RegoArray;
import io.github.open_policy_agent.opa.ast.types.RegoNull;
import io.github.open_policy_agent.opa.ast.types.RegoObject;
import io.github.open_policy_agent.opa.ast.types.RegoString;
import io.github.open_policy_agent.opa.ast.types.RegoValue;
import io.github.open_policy_agent.opa.cache.NdBuiltinCache;
import io.github.open_policy_agent.opa.rego.EvaluationContext;
import io.github.open_policy_agent.opa.rego.TypeError;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("http.send")
class HttpBuiltinsTest {

  private static final String JSON = "application/json";

  private static HttpTestSupport.Server jsonServer() throws IOException {
    return new HttpTestSupport.Server()
        .reply("/json", 200, JSON, "{\"hello\": \"world\", \"n\": [1, 2.5, null, true]}")
        .start();
  }

  private static RegoValue fetch(HttpTestSupport.Server server, String path, Object... extra) {
    Object[] request = new Object[4 + extra.length];
    request[0] = "method";
    request[1] = "get";
    request[2] = "url";
    request[3] = server.url(path);
    System.arraycopy(extra, 0, request, 4, extra.length);
    return send(newContext(), obj(request));
  }

  // ---------------------------------------------------------------------------------------------
  // Response mapping
  // ---------------------------------------------------------------------------------------------

  @Test
  @DisplayName("maps status, status_code, decoded body, raw_body and lower-cased headers")
  void mapsResponse() throws Exception {
    try (HttpTestSupport.Server server = jsonServer()) {
      RegoValue resp = fetch(server, "/json");

      assertEquals("200 OK", text(get(resp, "status")));
      assertEquals(200, intValue(get(resp, "status_code")));
      assertEquals("{\"hello\": \"world\", \"n\": [1, 2.5, null, true]}", text(get(resp, "raw_body")));
      RegoValue body = get(resp, "body");
      assertEquals("world", text(get(body, "hello")));
      assertEquals(4, ((RegoArray) get(body, "n")).length());
      RegoArray contentType = (RegoArray) get(get(resp, "headers"), "content-type");
      assertEquals(JSON, text(contentType.getValues().get(0)));
    }
  }

  @Test
  @DisplayName("sends the request line, headers and body the policy asked for")
  void sendsRequest() throws Exception {
    try (HttpTestSupport.Server server = new HttpTestSupport.Server().reply("/echo", 200, "text/plain", "ok").start()) {
      send(
          newContext(),
          obj(
              "method", "post",
              "url", server.url("/echo?q=1"),
              "headers", obj("x-custom", "yes", "content-type", JSON),
              "body", obj("a", List.of(1, "b"))));

      HttpTestSupport.Seen seen = server.last();
      assertEquals("POST", seen.method);
      assertEquals("/echo?q=1", seen.path);
      assertEquals("yes", seen.header("X-Custom"));
      assertEquals(JSON, seen.header("Content-Type"));
      assertEquals("{\"a\":[1,\"b\"]}", seen.body);
    }
  }

  @Test
  @DisplayName("raw_body wins over body, and the method is case-insensitive")
  void rawBodyWinsOverBody() throws Exception {
    try (HttpTestSupport.Server server = new HttpTestSupport.Server().reply("/echo", 200, "text/plain", "ok").start()) {
      for (String method : List.of("put", "Patch", "DELETE")) {
        send(newContext(), obj("method", method, "url", server.url("/echo"), "body", obj("x", 1), "raw_body", "raw"));
        assertEquals(method.toUpperCase(), server.last().method);
        assertEquals("raw", server.last().body);
      }
    }
  }

  @Test
  @DisplayName("decodes JSON and YAML bodies by content type or when forced, and leaves a bad body null")
  void decodesBodies() throws Exception {
    try (HttpTestSupport.Server server =
        new HttpTestSupport.Server()
            .reply("/charset", 200, "application/json; charset=utf-8", "{\"a\": 1}")
            .reply("/vendor", 200, "application/vnd.api+json", "{\"a\": 1}")
            .reply("/text", 200, "text/plain", "{\"a\": 1}")
            .reply("/yaml", 200, "application/x-yaml", "a: 1\nb:\n  - x\n")
            .reply("/bad", 200, JSON, "{\"a\": ")
            .start()) {
      assertEquals(RegoNull.INSTANCE, get(fetch(server, "/text"), "body"));
      assertNotNull(get(get(fetch(server, "/charset"), "body"), "a"));
      assertNotNull(get(get(fetch(server, "/vendor"), "body"), "a"));
      assertNotNull(get(get(fetch(server, "/text", "force_json_decode", true), "body"), "a"));
      assertNotNull(get(get(fetch(server, "/text", "force_yaml_decode", true), "body"), "a"));
      assertEquals(2, ((RegoObject) get(fetch(server, "/yaml"), "body")).length());

      RegoValue bad = fetch(server, "/bad");
      assertEquals(RegoNull.INSTANCE, get(bad, "body"));
      assertEquals("{\"a\": ", text(get(bad, "raw_body")));
    }
  }

  @Test
  @DisplayName("returns non-2xx responses as results")
  void nonSuccessStatuses() throws Exception {
    try (HttpTestSupport.Server server =
        new HttpTestSupport.Server()
            .reply("/missing", 404, "text/plain", "nope")
            .reply("/empty", 204, null, "")
            .reply("/boom", 500, null, "boom")
            .start()) {
      RegoValue missing = fetch(server, "/missing");
      assertEquals("404 Not Found", text(get(missing, "status")));
      assertEquals("nope", text(get(missing, "raw_body")));

      RegoValue empty = fetch(server, "/empty");
      assertEquals(204, intValue(get(empty, "status_code")));
      assertEquals("", text(get(empty, "raw_body")));

      assertEquals("500 Internal Server Error", text(get(fetch(server, "/boom"), "status")));
    }
  }

  @Test
  @DisplayName("keeps every value of a repeated response header")
  void repeatedResponseHeaders() throws Exception {
    try (HttpTestSupport.Server server =
        new HttpTestSupport.Server()
            .on(
                "/multi",
                exchange -> {
                  exchange.getResponseHeaders().add("Set-Cookie", "a=1");
                  exchange.getResponseHeaders().add("Set-Cookie", "b=2");
                  respond(exchange, 200, "text/plain", "x");
                })
            .start()) {
      RegoArray cookies = (RegoArray) get(get(fetch(server, "/multi"), "headers"), "set-cookie");
      assertEquals(2, cookies.length());
    }
  }

  // ---------------------------------------------------------------------------------------------
  // Redirects
  // ---------------------------------------------------------------------------------------------

  @Test
  @DisplayName("returns a redirect unless enable_redirect is set")
  void redirects() throws Exception {
    try (HttpTestSupport.Server server =
        new HttpTestSupport.Server()
            .on(
                "/old",
                exchange -> {
                  exchange.getResponseHeaders().add("Location", "/new");
                  respond(exchange, 302, null, "");
                })
            .reply("/new", 200, "text/plain", "arrived")
            .start()) {
      RegoValue plain = fetch(server, "/old");
      assertEquals(302, intValue(get(plain, "status_code")));
      assertEquals("/new", text(((RegoArray) get(get(plain, "headers"), "location")).getValues().get(0)));

      RegoValue followed = fetch(server, "/old", "enable_redirect", true);
      assertEquals(200, intValue(get(followed, "status_code")));
      assertEquals("arrived", text(get(followed, "raw_body")));
    }
  }

  @Test
  @DisplayName("follows 307 with the method and body, and turns 302 into a bodiless GET")
  void redirectMethods() throws Exception {
    try (HttpTestSupport.Server server =
        new HttpTestSupport.Server()
            .on(
                "/temporary",
                exchange -> {
                  exchange.getResponseHeaders().add("Location", "/target");
                  respond(exchange, 307, null, "");
                })
            .on(
                "/found",
                exchange -> {
                  exchange.getResponseHeaders().add("Location", "/target");
                  respond(exchange, 302, null, "");
                })
            .reply("/target", 200, "text/plain", "done")
            .start()) {
      send(newContext(), obj("method", "post", "url", server.url("/temporary"), "raw_body", "payload", "enable_redirect", true));
      assertEquals("POST", server.last().method);
      assertEquals("payload", server.last().body);

      send(newContext(), obj("method", "post", "url", server.url("/found"), "raw_body", "payload", "enable_redirect", true));
      assertEquals("GET", server.last().method);
      assertEquals("", server.last().body);
    }
  }

  @Test
  @DisplayName("does not forward Authorization to a different host")
  void redirectDropsCredentialsAcrossHosts() throws Exception {
    try (HttpTestSupport.Server server =
        new HttpTestSupport.Server()
            .on(
                "/same",
                exchange -> {
                  exchange.getResponseHeaders().add("Location", "/echo");
                  respond(exchange, 302, null, "");
                })
            .on(
                "/other",
                exchange -> {
                  exchange.getResponseHeaders().add("Location", "http://localhost:" + exchange.getLocalAddress().getPort() + "/echo");
                  respond(exchange, 302, null, "");
                })
            .reply("/echo", 200, "text/plain", "ok")
            .start()) {
      send(newContext(), obj("method", "get", "url", server.url("/same"), "headers", obj("authorization", "secret"), "enable_redirect", true));
      assertEquals("secret", server.last().header("Authorization"));

      send(newContext(), obj("method", "get", "url", server.url("/other"), "headers", obj("authorization", "secret"), "enable_redirect", true));
      assertEquals("/echo", server.last().path);
      assertNull(server.last().header("Authorization"));
    }
  }

  @Test
  @DisplayName("gives up on a redirect loop")
  void redirectLoop() throws Exception {
    try (HttpTestSupport.Server server =
        new HttpTestSupport.Server()
            .on(
                "/loop",
                exchange -> {
                  exchange.getResponseHeaders().add("Location", "/loop");
                  respond(exchange, 302, null, "");
                })
            .start()) {
      RegoValue result = fetch(server, "/loop", "enable_redirect", true, "raise_error", false);
      assertEquals(0, intValue(get(result, "status_code")));
      assertEquals("eval_http_send_network_error", text(get(get(result, "error"), "code")));
      assertTrue(text(get(get(result, "error"), "message")).contains("stopped after 10 redirects"));
    }
  }

  // ---------------------------------------------------------------------------------------------
  // Validation
  // ---------------------------------------------------------------------------------------------

  @Test
  @DisplayName("rejects a request that is not an object")
  void requestMustBeObject() {
    TypeError e = assertThrows(TypeError.class, () -> new HttpBuiltins().send(newContext(), new RegoValue[] {new RegoString("http://x")}));
    assertTrue(e.getMessage().contains("operand 1 must be object but got string"), e.getMessage());
  }

  @Test
  @DisplayName("lists invalid and missing parameters the way OPA does")
  void invalidAndMissingKeys() {
    TypeError invalid = assertThrows(TypeError.class, () -> send(newContext(), obj("method", "get", "url", "http://x", "bad_key", 1, "another", 2)));
    assertTrue(invalid.getMessage().contains("operand 1 invalid request parameters(s): {\"another\", \"bad_key\"}"), invalid.getMessage());

    TypeError missing = assertThrows(TypeError.class, () -> send(newContext(), obj("method", "get")));
    assertTrue(missing.getMessage().contains("operand 1 missing required request parameters(s): {\"url\"}"), missing.getMessage());

    TypeError neither = assertThrows(TypeError.class, () -> send(newContext(), obj()));
    assertTrue(neither.getMessage().contains("{\"method\", \"url\"}"), neither.getMessage());
  }

  @Test
  @DisplayName("reports bad parameter values as builtin errors")
  void badParameterValues() {
    assertBuiltinError("\"method\" must be a string", obj("method", 1, "url", "http://x"));
    assertBuiltinError("\"url\" must be a string", obj("method", "get", "url", true));
    assertBuiltinError("invalid type for headers key", obj("method", "get", "url", "http://127.0.0.1:1", "headers", "nope"));
    assertBuiltinError("invalid type for headers value 1", obj("method", "get", "url", "http://127.0.0.1:1", "headers", obj("a", 1)));
    assertBuiltinError("invalid timeout value \"5x\": time: unknown unit \"x\" in duration \"5x\"", obj("method", "get", "url", "http://x", "timeout", "5x"));
    assertBuiltinError("invalid timeout number value 1.5, must be int64", obj("method", "get", "url", "http://x", "timeout", new io.github.open_policy_agent.opa.ast.types.RegoDecimal(1.5)));
    assertBuiltinError("strconv.ParseBool", obj("method", "get", "url", "http://x", "enable_redirect", "yes"));
    assertBuiltinError("invalid value for \"cache\" field", obj("method", "get", "url", "http://x", "cache", "yes"));
    assertBuiltinError("invalid value for \"force_json_decode\" field", obj("method", "get", "url", "http://x", "force_json_decode", "yes"));
    assertBuiltinError("invalid value -1 for field \"max_retry_attempts\"", obj("method", "get", "url", "http://x", "max_retry_attempts", -1));
    assertBuiltinError("cache_ignored_headers must be an array of strings", obj("method", "get", "url", "http://x", "headers", obj("a", "b"), "cache_ignored_headers", "a"));
    assertBuiltinError("'force_cache' set but 'force_cache_duration_seconds' parameter is missing", obj("method", "get", "url", "http://x", "force_cache", true));
    assertBuiltinError("unsupported protocol scheme \"ftp\"", obj("method", "get", "url", "ftp://127.0.0.1/x"));
    assertBuiltinError("restricted header name", obj("method", "get", "url", "http://127.0.0.1:1", "headers", obj("Host", "example.com")));
    assertBuiltinError("unsupported protocol scheme \"unix\"", obj("method", "get", "url", "unix://localhost/path?socket=%2Ftmp%2Fhttp.sock"));
    assertBuiltinError("tls_server_name is not supported", obj("method", "get", "url", "https://x", "tls_server_name", "example.com"));
  }

  @Test
  @DisplayName("raise_error must be a boolean, and is always raised")
  void raiseErrorMustBeBoolean() {
    BuiltinError e = assertThrows(BuiltinError.class, () -> send(newContext(), obj("method", "get", "url", "http://x", "raise_error", "no")));
    assertTrue(e.getMessage().contains("invalid value for raise_error field"), e.getMessage());
  }

  @Test
  @DisplayName("timeout accepts numbers (ns), numeric strings and Go durations")
  void timeoutForms() throws Exception {
    try (HttpTestSupport.Server server = jsonServer()) {
      for (Object timeout : new Object[] {5_000_000_000L, "5000000000", "5s", "1m", "1500ms"}) {
        assertEquals(200, intValue(get(fetch(server, "/json", "timeout", timeout), "status_code")), String.valueOf(timeout));
      }
    }
  }

  // ---------------------------------------------------------------------------------------------
  // Errors
  // ---------------------------------------------------------------------------------------------

  @Test
  @DisplayName("raises network errors by default")
  void raisesNetworkErrors() {
    BuiltinError refused = assertThrows(BuiltinError.class, () -> send(newContext(), obj("method", "get", "url", "http://127.0.0.1:1/x")));
    assertTrue(refused.getMessage().contains("Get \"http://127.0.0.1:1/x\": dial tcp 127.0.0.1:1"), refused.getMessage());
  }

  @Test
  @DisplayName("returns errors in the result when raise_error is false")
  void returnsErrorsAsResults() {
    RegoValue refused = send(newContext(), obj("method", "get", "url", "http://127.0.0.1:1/x", "raise_error", false));
    assertEquals(0, intValue(get(refused, "status_code")));
    assertEquals("eval_http_send_network_error", text(get(get(refused, "error"), "code")));

    RegoValue invalid = send(newContext(), obj("method", "get", "url", "http://x", "bad_key", 1, "raise_error", false));
    assertEquals(0, intValue(get(invalid, "status_code")));
    assertEquals("eval_http_send_internal_error", text(get(get(invalid, "error"), "code")));
    assertTrue(text(get(get(invalid, "error"), "message")).contains("invalid request parameters(s): {\"bad_key\"}"));
  }

  @Test
  @DisplayName("times out slow responses")
  void timesOut() throws Exception {
    try (HttpTestSupport.Server server = new HttpTestSupport.Server().on("/slow", exchange -> sleepThenRespond(exchange)).start()) {
      BuiltinError e = assertThrows(BuiltinError.class, () -> fetch(server, "/slow", "timeout", "200ms"));
      assertTrue(e.getMessage().endsWith("/slow: request timed out"), e.getMessage());

      RegoValue result = fetch(server, "/slow", "timeout", 200_000_000L, "raise_error", false);
      assertEquals("eval_http_send_network_error", text(get(get(result, "error"), "code")));
      assertTrue(text(get(get(result, "error"), "message")).contains("Client.Timeout exceeded"));
    }
  }

  private static void sleepThenRespond(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
    try {
      Thread.sleep(1500);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    respond(exchange, 200, "text/plain", "late");
  }

  @Test
  @DisplayName("retries failed attempts up to max_retry_attempts")
  void retries() throws Exception {
    java.util.concurrent.atomic.AtomicInteger attempts = new java.util.concurrent.atomic.AtomicInteger();
    try (HttpTestSupport.Server server =
        new HttpTestSupport.Server()
            .on(
                "/flaky",
                exchange -> {
                  if (attempts.incrementAndGet() < 3) {
                    // Close the connection without answering.
                    exchange.close();
                    return;
                  }
                  respond(exchange, 200, "text/plain", "ok");
                })
            .start()) {
      assertThrows(BuiltinError.class, () -> fetch(server, "/flaky"));

      attempts.set(0);
      RegoValue result = fetch(server, "/flaky", "max_retry_attempts", 5);
      assertEquals("ok", text(get(result, "raw_body")));
    }
  }

  // ---------------------------------------------------------------------------------------------
  // Caching
  // ---------------------------------------------------------------------------------------------

  @Test
  @DisplayName("answers identical requests of one query from memory, but not across queries")
  void cachesWithinAQuery() throws Exception {
    try (HttpTestSupport.Server server = new HttpTestSupport.Server().reply("/data", 200, "text/plain", "x").start()) {
      EvaluationContext query = newContext();
      RegoObject request = obj("method", "get", "url", server.url("/data"));

      RegoValue first = send(query, request);
      RegoValue second = send(query, request);
      assertEquals(first, second);
      assertEquals(1, server.hits("/data"));

      send(newContext(), request);
      assertEquals(2, server.hits("/data"));
    }
  }

  @Test
  @DisplayName("treats requests that differ in a header as different, unless the header is ignored for caching")
  void cacheKeyHeaders() throws Exception {
    try (HttpTestSupport.Server server = new HttpTestSupport.Server().reply("/data", 200, "text/plain", "x").start()) {
      EvaluationContext query = newContext();
      send(query, obj("method", "get", "url", server.url("/data"), "headers", obj("x-a", "1")));
      send(query, obj("method", "get", "url", server.url("/data"), "headers", obj("x-a", "2")));
      assertEquals(2, server.hits("/data"));

      send(query, obj("method", "get", "url", server.url("/data"), "headers", obj("x-b", "1"), "cache_ignored_headers", List.of("x-b")));
      send(query, obj("method", "get", "url", server.url("/data"), "headers", obj("x-b", "2"), "cache_ignored_headers", List.of("x-b")));
      assertEquals(3, server.hits("/data"));
    }
  }

  @Test
  @DisplayName("only remembers responses with a cacheable status")
  void cachesOnlyCacheableStatuses() throws Exception {
    try (HttpTestSupport.Server server =
        new HttpTestSupport.Server().reply("/boom", 500, "text/plain", "x").reply("/gone", 410, "text/plain", "x").start()) {
      EvaluationContext query = newContext();
      send(query, obj("method", "get", "url", server.url("/boom")));
      send(query, obj("method", "get", "url", server.url("/boom")));
      send(query, obj("method", "get", "url", server.url("/gone")));
      send(query, obj("method", "get", "url", server.url("/gone")));
      assertEquals(2, server.hits("/boom"));
      assertEquals(1, server.hits("/gone"));
    }
  }

  @Test
  @DisplayName("repeats a failure within a query instead of retrying the request")
  void cachesFailures() throws Exception {
    java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
    try (HttpTestSupport.Server server =
        new HttpTestSupport.Server()
            .on(
                "/dropped",
                exchange -> {
                  calls.incrementAndGet();
                  exchange.close();
                })
            .start()) {
      EvaluationContext query = newContext();
      RegoObject request = obj("method", "get", "url", server.url("/dropped"));
      assertThrows(BuiltinError.class, () -> send(query, request));
      int afterFirst = calls.get();
      assertThrows(BuiltinError.class, () -> send(query, request));
      assertEquals(afterFirst, calls.get());
    }
  }

  @Test
  @DisplayName("accepts the inter-query cache parameters")
  void acceptsCacheParameters() throws Exception {
    try (HttpTestSupport.Server server = new HttpTestSupport.Server().reply("/data", 200, "text/plain", "x").start()) {
      assertEquals(200, intValue(get(fetch(server, "/data", "cache", true), "status_code")));
      assertEquals(200, intValue(get(fetch(server, "/data", "force_cache", true, "force_cache_duration_seconds", 60), "status_code")));
    }
  }

  // ---------------------------------------------------------------------------------------------
  // nd_builtin_cache and metadata
  // ---------------------------------------------------------------------------------------------

  @Test
  @DisplayName("uses and fills the nd_builtin_cache and records the value for decision logs")
  void ndBuiltinCache() throws Exception {
    NdBuiltinCache cache = new NdBuiltinCache(10, 60);
    try (HttpTestSupport.Server server = new HttpTestSupport.Server().reply("/data", 200, "text/plain", "x").start()) {
      RegoObject request = obj("method", "get", "url", server.url("/data"));
      EvaluationContext first = new EvaluationContext.Builder().withNdBuiltinCache(cache).build();
      RegoValue result = send(first, request);
      assertEquals(1, cache.size());
      assertEquals(1, first.getNdCacheValues().get("http.send").size());

      // A different query still gets the stored response without touching the server.
      EvaluationContext second = new EvaluationContext.Builder().withNdBuiltinCache(cache).build();
      assertEquals(result, send(second, request));
      assertEquals(1, server.hits("/data"));
    } finally {
      cache.shutdown();
    }
  }

  @Test
  @DisplayName("is registered as a nondeterministic builtin with OPA's signature")
  void registeredAndNondeterministic() {
    Descriptor descriptor =
        CapabilitiesGenerator.generateCapabilities(io.github.open_policy_agent.opa.ast.builtin.BuiltinRegistry.AllBuiltIns)
            .builtins
            .stream()
            .filter(d -> "http.send".equals(d.name))
            .findFirst()
            .orElseThrow();
    assertEquals(Boolean.TRUE, descriptor.nondeterministic);
    assertEquals("request", descriptor.decl.args.get(0).name);
    assertEquals("object", descriptor.decl.args.get(0).type);
    assertEquals("response", descriptor.decl.result.name);
  }

  // ---------------------------------------------------------------------------------------------

  private static void assertBuiltinError(String expected, RegoObject request) {
    RuntimeException e = assertThrows(RuntimeException.class, () -> send(newContext(), request));
    assertTrue(e instanceof BuiltinError || e instanceof TypeError, e.getClass().getName());
    assertTrue(e.getMessage().contains(expected), "expected <" + expected + "> in <" + e.getMessage() + ">");
  }
}
