package io.github.open_policy_agent.opa.ast.builtin.impls;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import io.github.open_policy_agent.opa.ast.builtin.BuiltinError;
import io.github.open_policy_agent.opa.ast.types.RegoArray;
import io.github.open_policy_agent.opa.ast.types.RegoInt32;
import io.github.open_policy_agent.opa.ast.types.RegoNull;
import io.github.open_policy_agent.opa.ast.types.RegoObject;
import io.github.open_policy_agent.opa.ast.types.RegoString;
import io.github.open_policy_agent.opa.ast.types.RegoValue;
import io.github.open_policy_agent.opa.rego.EvaluationContext;
import io.github.open_policy_agent.opa.rego.TypeError;

/** Maps an HTTP response to the object {@code http.send} returns. */
final class HttpSendResponse {

  /** Status codes whose responses OPA is willing to remember within a query. */
  private static final List<Integer> CACHEABLE_STATUS_CODES = List.of(200, 203, 204, 206, 300, 301, 404, 405, 410, 414, 501);

  /** The reason phrases of Go's {@code http.StatusText}. */
  private static final Map<Integer, String> STATUS_TEXT = statusText();

  private HttpSendResponse() {}

  static boolean isCacheable(int statusCode) {
    return CACHEABLE_STATUS_CODES.contains(statusCode);
  }

  /**
   * Builds {@code {"status", "status_code", "body", "raw_body", "headers"}}.
   *
   * @param headers response headers with their names as received
   */
  static RegoObject toRego(
      EvaluationContext ctx,
      int statusCode,
      Map<String, List<String>> headers,
      byte[] body,
      boolean forceJsonDecode,
      boolean forceYamlDecode) {
    Map<String, List<String>> lowercased = lowercaseNames(headers);
    String contentType = lowercased.containsKey("content-type") ? lowercased.get("content-type").get(0) : "";
    String rawBody = new String(body, StandardCharsets.UTF_8);

    // A body that cannot be decoded is not an error: "body" is null and "raw_body" still has it.
    RegoValue decoded = RegoNull.INSTANCE;
    if (forceJsonDecode || isJsonType(contentType)) {
      decoded = decode(ctx, rawBody, true);
    } else if (forceYamlDecode || contentType.contains("application/yaml") || contentType.contains("application/x-yaml")) {
      decoded = decode(ctx, rawBody, false);
    }

    RegoObject headerObject = new RegoObject();
    for (Map.Entry<String, List<String>> e : lowercased.entrySet()) {
      List<RegoValue> values = new ArrayList<>();
      for (String v : e.getValue()) {
        values.add(new RegoString(v));
      }
      headerObject.setProperty(e.getKey(), new RegoArray(values));
    }

    RegoObject result = new RegoObject();
    result.setProperty("status", new RegoString(statusCode + " " + STATUS_TEXT.getOrDefault(statusCode, "")));
    result.setProperty("status_code", RegoInt32.of(statusCode));
    result.setProperty("body", decoded);
    result.setProperty("raw_body", new RegoString(rawBody));
    result.setProperty("headers", headerObject);
    return result;
  }

  /** Loads the JSON builtins on first use, so responses that are neither JSON nor YAML never pay for it. */
  private static final class Decoder {
    static final JsonBuiltins INSTANCE = new JsonBuiltins();
  }

  private static RegoValue decode(EvaluationContext ctx, String text, boolean json) {
    RegoValue[] args = {new RegoString(text)};
    try {
      RegoValue value = json ? Decoder.INSTANCE.unmarshal(ctx, args) : Decoder.INSTANCE.yamlUnmarshal(ctx, args);
      return value == null ? RegoNull.INSTANCE : value;
    } catch (BuiltinError | TypeError e) {
      return RegoNull.INSTANCE;
    }
  }

  /** {@code application/json} and {@code application/*+json}, ignoring parameters such as charset. */
  static boolean isJsonType(String contentType) {
    int semicolon = contentType.indexOf(';');
    String mediaType = (semicolon < 0 ? contentType : contentType.substring(0, semicolon)).trim().toLowerCase(Locale.ROOT);
    int slash = mediaType.indexOf('/');
    if (slash < 0 || mediaType.indexOf('/', slash + 1) >= 0) {
      return false;
    }
    String type = mediaType.substring(0, slash);
    String subtype = mediaType.substring(slash + 1);
    return "application".equals(type) && ("json".equals(subtype) || subtype.endsWith("+json"));
  }

  private static Map<String, List<String>> lowercaseNames(Map<String, List<String>> headers) {
    Map<String, List<String>> out = new LinkedHashMap<>();
    for (Map.Entry<String, List<String>> e : headers.entrySet()) {
      out.computeIfAbsent(e.getKey().toLowerCase(Locale.ROOT), k -> new ArrayList<>()).addAll(e.getValue());
    }
    return out;
  }

  private static Map<Integer, String> statusText() {
    Map<Integer, String> m = new LinkedHashMap<>();
    m.put(100, "Continue");
    m.put(101, "Switching Protocols");
    m.put(102, "Processing");
    m.put(103, "Early Hints");
    m.put(200, "OK");
    m.put(201, "Created");
    m.put(202, "Accepted");
    m.put(203, "Non-Authoritative Information");
    m.put(204, "No Content");
    m.put(205, "Reset Content");
    m.put(206, "Partial Content");
    m.put(207, "Multi-Status");
    m.put(208, "Already Reported");
    m.put(226, "IM Used");
    m.put(300, "Multiple Choices");
    m.put(301, "Moved Permanently");
    m.put(302, "Found");
    m.put(303, "See Other");
    m.put(304, "Not Modified");
    m.put(305, "Use Proxy");
    m.put(307, "Temporary Redirect");
    m.put(308, "Permanent Redirect");
    m.put(400, "Bad Request");
    m.put(401, "Unauthorized");
    m.put(402, "Payment Required");
    m.put(403, "Forbidden");
    m.put(404, "Not Found");
    m.put(405, "Method Not Allowed");
    m.put(406, "Not Acceptable");
    m.put(407, "Proxy Authentication Required");
    m.put(408, "Request Timeout");
    m.put(409, "Conflict");
    m.put(410, "Gone");
    m.put(411, "Length Required");
    m.put(412, "Precondition Failed");
    m.put(413, "Request Entity Too Large");
    m.put(414, "Request URI Too Long");
    m.put(415, "Unsupported Media Type");
    m.put(416, "Requested Range Not Satisfiable");
    m.put(417, "Expectation Failed");
    m.put(418, "I'm a teapot");
    m.put(421, "Misdirected Request");
    m.put(422, "Unprocessable Entity");
    m.put(423, "Locked");
    m.put(424, "Failed Dependency");
    m.put(425, "Too Early");
    m.put(426, "Upgrade Required");
    m.put(428, "Precondition Required");
    m.put(429, "Too Many Requests");
    m.put(431, "Request Header Fields Too Large");
    m.put(451, "Unavailable For Legal Reasons");
    m.put(500, "Internal Server Error");
    m.put(501, "Not Implemented");
    m.put(502, "Bad Gateway");
    m.put(503, "Service Unavailable");
    m.put(504, "Gateway Timeout");
    m.put(505, "HTTP Version Not Supported");
    m.put(506, "Variant Also Negotiates");
    m.put(507, "Insufficient Storage");
    m.put(508, "Loop Detected");
    m.put(510, "Not Extended");
    m.put(511, "Network Authentication Required");
    return Map.copyOf(m);
  }
}
