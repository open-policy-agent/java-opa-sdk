package io.github.open_policy_agent.opa.ast.builtin.impls;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import io.github.open_policy_agent.opa.ast.builtin.BuiltinError;
import io.github.open_policy_agent.opa.ast.types.RegoArray;
import io.github.open_policy_agent.opa.ast.types.RegoBigInt;
import io.github.open_policy_agent.opa.ast.types.RegoBoolean;
import io.github.open_policy_agent.opa.ast.types.RegoInt32;
import io.github.open_policy_agent.opa.ast.types.RegoNull;
import io.github.open_policy_agent.opa.ast.types.RegoNumber;
import io.github.open_policy_agent.opa.ast.types.RegoObject;
import io.github.open_policy_agent.opa.ast.types.RegoString;
import io.github.open_policy_agent.opa.ast.types.RegoValue;

/**
 * An {@code http.send} request object, validated and converted to the values the HTTP client needs.
 *
 * <p>The checks and their order follow {@code validateHTTPRequestOperand} and {@code
 * createHTTPRequest} in OPA's {@code v1/topdown/http.go}.
 */
final class HttpSendRequest {

  /** Default request timeout, matching OPA. */
  static final long DEFAULT_TIMEOUT_NANOS = 5_000_000_000L;

  private static final Set<String> ALLOWED_KEYS =
      Set.of(
          "method",
          "url",
          "body",
          "enable_redirect",
          "force_json_decode",
          "force_yaml_decode",
          "headers",
          "raw_body",
          "tls_use_system_certs",
          "tls_ca_cert",
          "tls_ca_cert_file",
          "tls_ca_cert_env_variable",
          "tls_client_cert",
          "tls_client_cert_file",
          "tls_client_cert_env_variable",
          "tls_client_key",
          "tls_client_key_file",
          "tls_client_key_env_variable",
          "tls_insecure_skip_verify",
          "tls_server_name",
          "timeout",
          "cache",
          "force_cache",
          "force_cache_duration_seconds",
          "raise_error",
          "caching_mode",
          "max_retry_attempts",
          "cache_ignored_headers");

  private static final List<String> REQUIRED_KEYS = List.of("method", "url");

  /** Parameters that must be strings if present. */
  private static final Set<String> STRING_KEYS =
      Set.of(
          "method",
          "url",
          "raw_body",
          "tls_ca_cert",
          "tls_ca_cert_file",
          "tls_ca_cert_env_variable",
          "tls_client_cert",
          "tls_client_cert_file",
          "tls_client_cert_env_variable",
          "tls_client_key",
          "tls_client_key_file",
          "tls_client_key_env_variable",
          "tls_server_name");

  String method = "GET";
  String url = "";
  URI uri;
  byte[] body = new byte[0];
  /** Request headers by canonical name ({@code Content-Type}). */
  final Map<String, String> headers = new LinkedHashMap<>();
  long timeoutNanos = DEFAULT_TIMEOUT_NANOS;
  boolean enableRedirect;
  int maxRetryAttempts;
  boolean forceJsonDecode;
  boolean forceYamlDecode;
  final HttpSendTls tls = new HttpSendTls();

  private HttpSendRequest() {}

  // ---------------------------------------------------------------------------------------------
  // Operand checks done before anything else (validateHTTPRequestOperand)
  // ---------------------------------------------------------------------------------------------

  /** Rejects unknown keys first, then missing {@code method}/{@code url}. */
  static void validateKeys(RegoObject obj) {
    Set<String> invalid = new TreeSet<>();
    for (RegoValue key : obj.getProperties().keySet()) {
      if (!(key instanceof RegoString) || !ALLOWED_KEYS.contains(((RegoString) key).getValue())) {
        invalid.add(key instanceof RegoString ? ((RegoString) key).getValue() : key.toString());
      }
    }
    if (!invalid.isEmpty()) {
      throw HttpSendFailure.operand("operand 1 invalid request parameters(s): " + formatKeySet(invalid));
    }

    Set<String> missing = new TreeSet<>();
    for (String key : REQUIRED_KEYS) {
      if (obj.getProperty(key) == null) {
        missing.add(key);
      }
    }
    if (!missing.isEmpty()) {
      throw HttpSendFailure.operand("operand 1 missing required request parameters(s): " + formatKeySet(missing));
    }
  }

  /** Formats keys like OPA prints a set: {@code {"a", "b"}}. */
  private static String formatKeySet(Collection<String> sortedKeys) {
    return sortedKeys.stream().map(k -> "\"" + k + "\"").collect(Collectors.joining(", ", "{", "}"));
  }

  // ---------------------------------------------------------------------------------------------
  // Parameters read outside of createHTTPRequest
  // ---------------------------------------------------------------------------------------------

  /** {@code raise_error}, true unless the request says otherwise. */
  static boolean raiseError(RegoObject obj) {
    RegoValue v = obj.getProperty("raise_error");
    if (v == null) {
      return true;
    }
    if (v instanceof RegoBoolean) {
      return ((RegoBoolean) v).getValue();
    }
    throw new BuiltinError("invalid value for raise_error field");
  }

  /**
   * Checks the caching parameters the way OPA does before it looks at the cache: {@code cache} and
   * {@code force_cache} must be booleans, and {@code force_cache} needs a 32-bit {@code
   * force_cache_duration_seconds}. There is no inter-query cache in this SDK, so nothing else
   * about them has an effect.
   */
  static void validateCacheParameters(RegoObject obj) {
    optionalBoolean(obj, "cache");
    if (optionalBoolean(obj, "force_cache")) {
      RegoValue seconds = obj.getProperty("force_cache_duration_seconds");
      if (seconds == null) {
        throw HttpSendFailure.internal("'force_cache' set but 'force_cache_duration_seconds' parameter is missing");
      }
      try {
        Integer.parseInt(seconds.toString());
      } catch (NumberFormatException e) {
        throw HttpSendFailure.internal(
            "strconv.ParseInt: parsing \"" + seconds + "\": " + (seconds.toString().matches("-?\\d+") ? "value out of range" : "invalid syntax"));
      }
    }
  }

  static boolean optionalBoolean(RegoObject obj, String key) {
    RegoValue v = obj.getProperty(key);
    if (v == null) {
      return false;
    }
    if (v instanceof RegoBoolean) {
      return ((RegoBoolean) v).getValue();
    }
    throw HttpSendFailure.internal("invalid value for \"" + key + "\" field");
  }

  static int retryAttempts(RegoObject obj) {
    RegoValue v = obj.getProperty("max_retry_attempts");
    if (v == null) {
      return 0;
    }
    if (v instanceof RegoInt32 && ((RegoInt32) v).getValue() >= 0) {
      return ((RegoInt32) v).getValue();
    }
    if (v instanceof RegoBigInt && ((RegoBigInt) v).getValue().signum() >= 0 && ((RegoBigInt) v).getValue().bitLength() < 32) {
      return ((RegoBigInt) v).getValue().intValue();
    }
    throw HttpSendFailure.internal("invalid value " + v + " for field \"max_retry_attempts\"");
  }

  /**
   * The cache key of a request: the request without the headers listed in {@code
   * cache_ignored_headers}, so that requests differing only in those headers share a result.
   */
  static RegoObject cacheKey(RegoObject obj) {
    Map<RegoValue, RegoValue> key = new LinkedHashMap<>(obj.getProperties());
    RegoValue ignored = obj.getProperty("cache_ignored_headers");
    RegoValue headers = obj.getProperty("headers");
    if (ignored != null && headers != null) {
      if (!(ignored instanceof RegoArray) || !((RegoArray) ignored).getValues().stream().allMatch(RegoString.class::isInstance)) {
        throw HttpSendFailure.internal("cache_ignored_headers must be an array of strings");
      }
      if (!(headers instanceof RegoObject)) {
        throw HttpSendFailure.internal("invalid type for headers key");
      }
      List<RegoValue> ignoredNames = ((RegoArray) ignored).getValues();
      RegoObject kept = new RegoObject();
      for (Map.Entry<RegoValue, RegoValue> header : ((RegoObject) headers).getProperties().entrySet()) {
        if (!ignoredNames.contains(header.getKey())) {
          kept.setProp(header.getKey(), header.getValue());
        }
      }
      key.put(new RegoString("headers"), kept);
    }
    key.put(new RegoString("cache_ignored_headers"), RegoNull.INSTANCE);
    return new RegoObject(key);
  }

  // ---------------------------------------------------------------------------------------------
  // createHTTPRequest
  // ---------------------------------------------------------------------------------------------

  /** Converts a validated request object. */
  static HttpSendRequest parse(RegoObject obj) {
    HttpSendRequest req = new HttpSendRequest();
    req.maxRetryAttempts = retryAttempts(obj);
    req.forceJsonDecode = optionalBoolean(obj, "force_json_decode");
    req.forceYamlDecode = optionalBoolean(obj, "force_yaml_decode");

    // OPA walks the keys in sorted order, which makes the first error deterministic.
    TreeMap<String, RegoValue> params = new TreeMap<>();
    for (Map.Entry<RegoValue, RegoValue> e : obj.getProperties().entrySet()) {
      params.put(((RegoString) e.getKey()).getValue(), e.getValue());
    }

    byte[] jsonBody = null;
    byte[] rawBody = null;
    for (Map.Entry<String, RegoValue> param : params.entrySet()) {
      String key = param.getKey();
      RegoValue val = param.getValue();

      String strVal = "";
      if (val instanceof RegoString) {
        strVal = trimQuotes(((RegoString) val).getValue());
      } else if (STRING_KEYS.contains(key)) {
        throw HttpSendFailure.internal("\"" + key + "\" must be a string");
      }

      switch (key) {
        case "method":
          req.method = strVal.toUpperCase(Locale.ROOT);
          break;
        case "url":
          req.url = strVal;
          break;
        case "enable_redirect":
          req.enableRedirect = parseBool(val);
          break;
        case "body":
          jsonBody = jsonBody(val);
          break;
        case "raw_body":
          rawBody = strVal.getBytes(StandardCharsets.UTF_8);
          break;
        case "tls_use_system_certs":
          req.tls.useSystemCerts = parseBool(val);
          break;
        case "tls_ca_cert":
          req.tls.caCert = strVal;
          break;
        case "tls_ca_cert_file":
          req.tls.caCertFile = strVal;
          break;
        case "tls_ca_cert_env_variable":
          req.tls.caCertEnv = strVal;
          break;
        case "tls_client_cert":
          req.tls.clientCert = strVal;
          break;
        case "tls_client_cert_file":
          req.tls.clientCertFile = strVal;
          break;
        case "tls_client_cert_env_variable":
          req.tls.clientCertEnv = strVal;
          break;
        case "tls_client_key":
          req.tls.clientKey = strVal;
          break;
        case "tls_client_key_file":
          req.tls.clientKeyFile = strVal;
          break;
        case "tls_client_key_env_variable":
          req.tls.clientKeyEnv = strVal;
          break;
        case "tls_server_name":
          // The JDK HttpClient picks the SNI name from the URL host, so it cannot be overridden.
          if (!strVal.isEmpty()) {
            throw HttpSendFailure.internal("tls_server_name is not supported");
          }
          break;
        case "headers":
          req.readHeaders(val);
          break;
        case "tls_insecure_skip_verify":
          req.tls.insecureSkipVerify = parseBool(val);
          break;
        case "timeout":
          req.timeoutNanos = parseTimeout(val);
          break;
        default:
          // cache, caching_mode, force_cache, force_cache_duration_seconds, force_json_decode,
          // force_yaml_decode, raise_error, max_retry_attempts and cache_ignored_headers are read
          // elsewhere and have no effect on the request itself.
          break;
      }
    }

    // raw_body wins over body, whichever key comes first.
    if (rawBody != null) {
      req.body = rawBody;
    } else if (jsonBody != null) {
      req.body = jsonBody;
    }

    if (req.method.isEmpty()) {
      req.method = "GET";
    }
    req.uri = parseUri(req.url, req.method);
    return req;
  }

  /** Loads the JSON builtins on first use, so requests without a {@code body} never pay for it. */
  private static final class Encoder {
    static final JsonBuiltins INSTANCE = new JsonBuiltins();
  }

  private static byte[] jsonBody(RegoValue val) {
    try {
      return Encoder.INSTANCE.marshal(null, new RegoValue[] {val}).getValue().getBytes(StandardCharsets.UTF_8);
    } catch (BuiltinError e) {
      throw HttpSendFailure.internal(e.getMessage());
    }
  }

  private static URI parseUri(String url, String method) {
    URI uri;
    try {
      uri = new URI(url);
    } catch (URISyntaxException e) {
      throw HttpSendFailure.internal("parse \"" + url + "\": " + e.getReason());
    }
    String scheme = uri.getScheme();
    if (scheme == null || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) {
      throw HttpSendFailure.network(method, url, false, "unsupported protocol scheme \"" + (scheme == null ? "" : scheme) + "\"");
    }
    if (uri.getHost() == null) {
      throw HttpSendFailure.network(method, url, false, "http: no Host in request URL");
    }
    return uri;
  }

  private void readHeaders(RegoValue value) {
    if (!(value instanceof RegoObject)) {
      throw HttpSendFailure.internal("invalid type for headers key");
    }
    for (Map.Entry<RegoValue, RegoValue> e : ((RegoObject) value).getProperties().entrySet()) {
      if (!(e.getKey() instanceof RegoString)) {
        throw HttpSendFailure.internal("invalid type for headers key");
      }
      if (!(e.getValue() instanceof RegoString)) {
        throw HttpSendFailure.internal("invalid type for headers value " + e.getValue());
      }
      headers.put(canonicalHeaderName(((RegoString) e.getKey()).getValue()), ((RegoString) e.getValue()).getValue());
    }
  }

  /** Go's {@code http.CanonicalHeaderKey}: {@code content-type} becomes {@code Content-Type}. */
  static String canonicalHeaderName(String name) {
    for (int i = 0; i < name.length(); i++) {
      char c = name.charAt(i);
      boolean token = c > ' ' && c < 0x7f && "()<>@,;:\\\"/[]?={}".indexOf(c) < 0;
      if (!token) {
        return name;
      }
    }
    StringBuilder out = new StringBuilder(name.length());
    boolean upper = true;
    for (int i = 0; i < name.length(); i++) {
      char c = name.charAt(i);
      out.append(upper ? Character.toUpperCase(c) : Character.toLowerCase(c));
      upper = c == '-';
    }
    return out.toString();
  }

  /** Go's {@code strings.Trim(s, "\"")}. */
  private static String trimQuotes(String s) {
    int start = 0;
    int end = s.length();
    while (start < end && s.charAt(start) == '"') {
      start++;
    }
    while (end > start && s.charAt(end - 1) == '"') {
      end--;
    }
    return s.substring(start, end);
  }

  /** {@code strconv.ParseBool(val.String())}: booleans, and the numbers 1 and 0. */
  private static boolean parseBool(RegoValue val) {
    String text = val.toString();
    switch (text) {
      case "1":
      case "true":
        return true;
      case "0":
      case "false":
        return false;
      default:
        throw HttpSendFailure.internal("strconv.ParseBool: parsing \"" + text.replace("\"", "\\\"") + "\": invalid syntax");
    }
  }

  /** A number is nanoseconds; a string is nanoseconds or a Go duration like {@code "5s"}. */
  private static long parseTimeout(RegoValue val) {
    if (val instanceof RegoInt32) {
      return ((RegoInt32) val).getValue();
    }
    if (val instanceof RegoBigInt) {
      try {
        return ((RegoBigInt) val).getValue().longValueExact();
      } catch (ArithmeticException e) {
        throw HttpSendFailure.internal("invalid timeout number value " + val + ", must be int64");
      }
    }
    if (val instanceof RegoNumber) {
      throw HttpSendFailure.internal("invalid timeout number value " + val + ", must be int64");
    }
    if (val instanceof RegoString) {
      String text = ((RegoString) val).getValue();
      try {
        return Long.parseLong(text);
      } catch (NumberFormatException e) {
        // Not a plain number of nanoseconds; try a duration.
      }
      try {
        return GoDuration.parseNanos(text);
      } catch (IllegalArgumentException e) {
        throw HttpSendFailure.internal("invalid timeout value \"" + text + "\": " + e.getMessage());
      }
    }
    throw HttpSendFailure.operand("operand 1 'timeout' must be one of {string, number} but got " + val.getTypeName());
  }
}
