package io.github.open_policy_agent.opa.ast.builtin.impls;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import io.github.open_policy_agent.opa.ast.builtin.BuiltinProvider;
import io.github.open_policy_agent.opa.ast.builtin.OpaBuiltin;
import io.github.open_policy_agent.opa.ast.builtin.OpaType;
import io.github.open_policy_agent.opa.ast.types.RegoArray;
import io.github.open_policy_agent.opa.ast.types.RegoBigInt;
import io.github.open_policy_agent.opa.ast.types.RegoBoolean;
import io.github.open_policy_agent.opa.ast.types.RegoDecimal;
import io.github.open_policy_agent.opa.ast.types.RegoInt32;
import io.github.open_policy_agent.opa.ast.types.RegoNull;
import io.github.open_policy_agent.opa.ast.types.RegoNumber;
import io.github.open_policy_agent.opa.ast.types.RegoObject;
import io.github.open_policy_agent.opa.ast.types.RegoSet;
import io.github.open_policy_agent.opa.ast.types.RegoString;
import io.github.open_policy_agent.opa.ast.types.RegoValue;
import io.github.open_policy_agent.opa.rego.EvaluationContext;
import io.github.open_policy_agent.opa.rego.TypeError;

/**
 * Implements the {@code providers.aws.sign_req} builtin, which signs an HTTP request object using
 * the AWS Signature Version 4 (header-based, single-chunk) scheme.
 *
 * <p>The behavior mirrors the reference OPA implementation in {@code topdown/providers.go} and
 * {@code internal/providers/aws/signing_v4.go}.
 */
public class ProvidersAwsBuiltins implements BuiltinProvider {

  private static final DateTimeFormatter DATE_FMT =
      DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC);
  private static final DateTimeFormatter ISO8601_FMT =
      DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);

  private static final String UNSIGNED_PAYLOAD = "UNSIGNED-PAYLOAD";
  private static final String AMZ_CONTENT_SHA256 = "x-amz-content-sha256";

  // Required AWS config keys (order irrelevant; formatted sorted in error messages).
  private static final List<String> REQUIRED_AWS_KEYS =
      List.of("aws_service", "aws_access_key", "aws_secret_access_key", "aws_region");
  // Required HTTP request keys.
  private static final List<String> REQUIRED_REQUEST_KEYS = List.of("method", "url");

  /**
   * Allowed HTTP request object keys, mirrored from OPA's {@code allowedKeyNames} in {@code
   * v1/topdown/http.go}. {@code providers.aws.sign_req} reuses {@code validateHTTPRequestOperand},
   * so unknown keys must be rejected with the same error as {@code http.send}.
   */
  private static final Set<String> ALLOWED_REQUEST_KEYS =
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

  // Headers that may be mutated in transit and are therefore excluded from the canonical request.
  private static final Set<String> IGNORED_HEADERS =
      Set.of("authorization", "user-agent", "x-amzn-trace-id");

  @Override
  public Map<String, BiFunction<EvaluationContext, RegoValue[], RegoValue>> builtins() {
    ProvidersAwsBuiltins instance = new ProvidersAwsBuiltins();
    return Map.ofEntries(Map.entry("providers.aws.sign_req", instance::signReq));
  }

  @OpaBuiltin(
      name = "providers.aws.sign_req",
      description =
          "Signs an HTTP request object for Amazon Web Services using AWS Signature Version 4.",
      categories = {"providers.aws"},
      args = {
        @OpaType(type = "object", name = "request", description = "HTTP request object"),
        @OpaType(type = "object", name = "aws_config", description = "AWS configuration object"),
        @OpaType(type = "number", name = "time_ns", description = "nanoseconds since the epoch")
      },
      result = @OpaType(type = "object", name = "signed_request", description = "signed request"))
  public RegoValue signReq(EvaluationContext ctx, RegoValue[] args) {
    // Operand 1: request object.
    if (!(args[0] instanceof RegoObject)) {
      throw new TypeError("operand 1 must be object but got " + args[0].getTypeName());
    }
    RegoObject reqObj = (RegoObject) args[0];

    // Operand 2: AWS config object.
    if (!(args[1] instanceof RegoObject)) {
      throw new TypeError("operand 2 must be object but got " + args[1].getTypeName());
    }
    RegoObject awsConfigObj = (RegoObject) args[1];
    validateAwsAuthParameters(awsConfigObj);

    String service = stringFromProperty(awsConfigObj, "aws_service");
    String accessKey = stringFromProperty(awsConfigObj, "aws_access_key");
    String secretKey = stringFromProperty(awsConfigObj, "aws_secret_access_key");
    String region = stringFromProperty(awsConfigObj, "aws_region");
    String sessionToken = stringFromProperty(awsConfigObj, "aws_session_token");

    // Operand 3: timestamp in nanoseconds.
    Long ts = toUnixNanos(args[2]);
    if (ts == null || ts < 0) {
      throw new TypeError("operand 3 could not convert time_ns value into a unix timestamp");
    }
    Instant signingInstant =
        Instant.ofEpochSecond(Math.floorDiv(ts, 1_000_000_000L), Math.floorMod(ts, 1_000_000_000L));

    // Validate required request keys exist.
    validateHttpRequestOperand(reqObj);

    // Required request fields (must be strings).
    RegoValue reqUrl = reqObj.getProperty("url");
    RegoValue reqMethod = reqObj.getProperty("method");
    List<String> invalidRequestParams = new ArrayList<>();
    if (!(reqUrl instanceof RegoString)) {
      invalidRequestParams.add("url");
    }
    if (!(reqMethod instanceof RegoString)) {
      invalidRequestParams.add("method");
    }
    if (!invalidRequestParams.isEmpty()) {
      throw new TypeError(
          "operand 1 invalid values for required request parameters(s): "
              + formatKeySet(invalidRequestParams));
    }

    String method = ((RegoString) reqMethod).getValue();
    URI url = parseUrl(((RegoString) reqUrl).getValue());

    // Optional headers object from the request.
    RegoObject headersObj = new RegoObject();
    RegoValue headersTerm = reqObj.getProperty("headers");
    if (headersTerm != null) {
      if (!(headersTerm instanceof RegoObject)) {
        throw new TypeError("operand 0 must be object but got " + headersTerm.getTypeName());
      }
      headersObj = (RegoObject) headersTerm;
    }

    // disable_payload_signing (optional boolean).
    boolean disablePayloadSigning = false;
    RegoValue dps = awsConfigObj.getProperty("disable_payload_signing");
    if (dps != null) {
      if (dps instanceof RegoBoolean) {
        disablePayloadSigning = ((RegoBoolean) dps).getValue();
      } else {
        throw new TypeError("operand 2 invalid value for 'disable_payload_signing' in AWS config");
      }
    }

    byte[] body = getReqBodyBytes(reqObj);

    // ---- AWS SigV4 signing (mirrors internal/providers/aws.SignV4) ----
    String contentSha256 = disablePayloadSigning ? UNSIGNED_PAYLOAD : sha256Hex(body);
    String dateNow = DATE_FMT.format(signingInstant);
    String iso8601Now = ISO8601_FMT.format(signingInstant);

    // AWS-managed headers.
    Map<String, String> awsHeaders = new LinkedHashMap<>();
    awsHeaders.put("host", hostFromUrl(url));
    awsHeaders.put("x-amz-date", iso8601Now);
    if ("s3".equals(service) || "glacier".equals(service)) {
      awsHeaders.put(AMZ_CONTENT_SHA256, contentSha256);
    }
    if (sessionToken != null && !sessionToken.isEmpty()) {
      awsHeaders.put("x-amz-security-token", sessionToken);
    }

    // Original request headers (original case) restricted to string key/value pairs.
    Map<String, String> requestHeaders = objectToMap(headersObj);

    // Headers to sign: aws headers first, then request headers (lowercased) which overwrite.
    Map<String, String> headersToSign = new LinkedHashMap<>(awsHeaders);
    for (Map.Entry<String, String> e : requestHeaders.entrySet()) {
      String lc = e.getKey().toLowerCase(java.util.Locale.ROOT);
      if (!IGNORED_HEADERS.contains(lc)) {
        headersToSign.put(lc, e.getValue());
      }
    }

    // Canonical request.
    TreeMap<String, String> sortedHeaders = new TreeMap<>(headersToSign);
    StringBuilder canonicalReq = new StringBuilder();
    canonicalReq.append(method).append('\n');
    canonicalReq.append(escapedPath(url)).append('\n');
    canonicalReq.append(rawQuery(url)).append('\n');
    for (Map.Entry<String, String> e : sortedHeaders.entrySet()) {
      canonicalReq.append(e.getKey()).append(':').append(e.getValue()).append('\n');
    }
    canonicalReq.append('\n');
    String headerList = String.join(";", sortedHeaders.keySet());
    canonicalReq.append(headerList).append('\n');
    canonicalReq.append(contentSha256);

    // String to sign.
    String credentialScope = dateNow + "/" + region + "/" + service + "/aws4_request";
    String strToSign =
        "AWS4-HMAC-SHA256\n"
            + iso8601Now
            + "\n"
            + credentialScope
            + "\n"
            + sha256Hex(canonicalReq.toString().getBytes(StandardCharsets.UTF_8));

    // Signing key via HMAC-SHA256 chaining.
    byte[] signingKey = hmacSha256(("AWS4" + secretKey).getBytes(StandardCharsets.UTF_8), dateNow);
    signingKey = hmacSha256(signingKey, region);
    signingKey = hmacSha256(signingKey, service);
    signingKey = hmacSha256(signingKey, "aws4_request");

    String signature = bytesToHex(hmacSha256(signingKey, strToSign));

    String authHeader =
        "AWS4-HMAC-SHA256 Credential="
            + accessKey
            + "/"
            + credentialScope
            + ",SignedHeaders="
            + headerList
            + ",Signature="
            + signature;

    // ---- Build the signed request object ----
    RegoObject signedHeaders = new RegoObject();
    // Restore original request headers (original case).
    for (Map.Entry<String, String> e : requestHeaders.entrySet()) {
      signedHeaders.setProp(new RegoString(e.getKey()), new RegoString(e.getValue()));
    }
    signedHeaders.setProp(new RegoString("Authorization"), new RegoString(authHeader));
    // AWS signature headers overwrite any conflicting request headers.
    for (Map.Entry<String, String> e : awsHeaders.entrySet()) {
      signedHeaders.setProp(new RegoString(e.getKey()), new RegoString(e.getValue()));
    }

    RegoObject out = new RegoObject();
    reqObj.stream().forEach(e -> out.setProp(e.getKey(), e.getValue()));
    out.setProp(new RegoString("headers"), signedHeaders);
    return out;
  }

  // ------------------------------------------------------------------
  // Validation helpers
  // ------------------------------------------------------------------

  private void validateAwsAuthParameters(RegoObject config) {
    List<String> missing = new ArrayList<>();
    for (String key : REQUIRED_AWS_KEYS) {
      if (config.getProperty(key) == null) {
        missing.add(key);
      }
    }
    if (!missing.isEmpty()) {
      throw new TypeError(
          "operand 2 missing required AWS config parameters(s): " + formatKeySet(missing));
    }

    List<String> invalid = new ArrayList<>();
    for (String key : REQUIRED_AWS_KEYS) {
      RegoValue v = config.getProperty(key);
      if (!(v instanceof RegoString)) {
        invalid.add(key);
      }
    }
    if (!invalid.isEmpty()) {
      throw new TypeError(
          "operand 2 invalid values for required AWS config parameters(s): "
              + formatKeySet(invalid));
    }
  }

  private void validateHttpRequestOperand(RegoObject reqObj) {
    // Match OPA's validateHTTPRequestOperand: reject unknown keys before missing-required checks.
    List<String> invalid = new ArrayList<>();
    reqObj
        .stream()
        .forEach(
            e -> {
              RegoValue key = e.getKey();
              if (!(key instanceof RegoString)
                  || !ALLOWED_REQUEST_KEYS.contains(((RegoString) key).getValue())) {
                invalid.add(
                    key instanceof RegoString ? ((RegoString) key).getValue() : key.toString());
              }
            });
    if (!invalid.isEmpty()) {
      throw new TypeError(
          "operand 1 invalid request parameters(s): " + formatKeySet(invalid));
    }

    List<String> missing = new ArrayList<>();
    for (String key : REQUIRED_REQUEST_KEYS) {
      if (reqObj.getProperty(key) == null) {
        missing.add(key);
      }
    }
    if (!missing.isEmpty()) {
      throw new TypeError(
          "operand 1 missing required request parameters(s): " + formatKeySet(missing));
    }
  }

  /** Formats a collection of keys as an OPA set literal: {@code {"a", "b"}} (sorted, quoted). */
  private static String formatKeySet(Collection<String> keys) {
    return keys.stream()
        .sorted()
        .map(k -> "\"" + k + "\"")
        .collect(Collectors.joining(", ", "{", "}"));
  }

  // ------------------------------------------------------------------
  // Timestamp handling
  // ------------------------------------------------------------------

  /**
   * Converts the timestamp operand into an integer number of nanoseconds, or {@code null} if it
   * cannot be represented as a signed 64-bit integer.
   */
  private static Long toUnixNanos(RegoValue value) {
    if (!(value instanceof RegoNumber)) {
      return null;
    }
    if (value instanceof RegoInt32) {
      return (long) ((RegoInt32) value).getValue();
    }
    if (value instanceof RegoBigInt) {
      BigInteger b = ((RegoBigInt) value).getValue();
      if (b.bitLength() > 63) {
        return null;
      }
      return b.longValue();
    }
    if (value instanceof RegoDecimal) {
      Double d = ((RegoDecimal) value).getValue();
      if (d == null || d.isInfinite() || d.isNaN() || d != Math.floor(d)) {
        return null;
      }
      if (d < (double) Long.MIN_VALUE || d > (double) Long.MAX_VALUE) {
        return null;
      }
      return d.longValue();
    }
    return null;
  }

  // ------------------------------------------------------------------
  // Request body / URL helpers
  // ------------------------------------------------------------------

  /** raw_body takes precedence over body; body is JSON-serialized. */
  private byte[] getReqBodyBytes(RegoObject reqObj) {
    RegoValue rawBody = reqObj.getProperty("raw_body");
    if (rawBody != null) {
      String s = (rawBody instanceof RegoString) ? ((RegoString) rawBody).getValue() : "";
      return s.getBytes(StandardCharsets.UTF_8);
    }
    RegoValue body = reqObj.getProperty("body");
    if (body != null) {
      return canonicalJson(body).getBytes(StandardCharsets.UTF_8);
    }
    return new byte[0];
  }

  private static URI parseUrl(String value) {
    try {
      return new URI(value);
    } catch (URISyntaxException e) {
      throw new TypeError("providers.aws.sign_req: could not parse url: " + value);
    }
  }

  private static String hostFromUrl(URI url) {
    String host = url.getHost();
    if (host == null) {
      return "";
    }
    int port = url.getPort();
    if (port == -1) {
      return host;
    }
    return host + ":" + port;
  }

  private static String escapedPath(URI url) {
    String path = url.getRawPath();
    return path == null ? "" : path;
  }

  private static String rawQuery(URI url) {
    String query = url.getRawQuery();
    return query == null ? "" : query;
  }

  /** Extracts string key/value header pairs, preserving original key case. */
  private static Map<String, String> objectToMap(RegoObject obj) {
    Map<String, String> out = new LinkedHashMap<>();
    obj.stream()
        .forEach(
            e -> {
              if (e.getKey() instanceof RegoString) {
                String k = ((RegoString) e.getKey()).getValue();
                String v =
                    (e.getValue() instanceof RegoString)
                        ? ((RegoString) e.getValue()).getValue()
                        : "";
                out.put(k, v);
              }
            });
    return out;
  }

  // ------------------------------------------------------------------
  // Canonical JSON serialization (compact, sorted object keys, sets as arrays)
  // ------------------------------------------------------------------

  private static String canonicalJson(RegoValue value) {
    StringBuilder sb = new StringBuilder();
    writeJson(value, sb);
    return sb.toString();
  }

  private static void writeJson(RegoValue value, StringBuilder sb) {
    if (value == null || value instanceof RegoNull) {
      sb.append("null");
    } else if (value instanceof RegoString) {
      writeJsonString(((RegoString) value).getValue(), sb);
    } else if (value instanceof RegoBoolean) {
      sb.append(((RegoBoolean) value).getValue() ? "true" : "false");
    } else if (value instanceof RegoInt32) {
      sb.append(((RegoInt32) value).getValue());
    } else if (value instanceof RegoBigInt) {
      sb.append(((RegoBigInt) value).getValue().toString());
    } else if (value instanceof RegoDecimal) {
      appendGoJsonFloat(((RegoDecimal) value).getValue(), sb);
    } else if (value instanceof RegoArray) {
      sb.append('[');
      List<RegoValue> items = ((RegoArray) value).getValues();
      for (int i = 0; i < items.size(); i++) {
        if (i > 0) {
          sb.append(',');
        }
        writeJson(items.get(i), sb);
      }
      sb.append(']');
    } else if (value instanceof RegoSet) {
      // A set serializes to a JSON array (elements in the set's sorted order).
      sb.append('[');
      boolean first = true;
      for (RegoValue item : ((RegoSet) value).getValue()) {
        if (!first) {
          sb.append(',');
        }
        first = false;
        writeJson(item, sb);
      }
      sb.append(']');
    } else if (value instanceof RegoObject) {
      // Object keys are sorted lexicographically to match Go's json.Marshal.
      TreeMap<String, RegoValue> sorted = new TreeMap<>();
      ((RegoObject) value)
          .stream()
          .forEach(
              e -> {
                String k =
                    (e.getKey() instanceof RegoString)
                        ? ((RegoString) e.getKey()).getValue()
                        : e.getKey().toString();
                sorted.put(k, e.getValue());
              });
      sb.append('{');
      boolean first = true;
      for (Map.Entry<String, RegoValue> e : sorted.entrySet()) {
        if (!first) {
          sb.append(',');
        }
        first = false;
        writeJsonString(e.getKey(), sb);
        sb.append(':');
        writeJson(e.getValue(), sb);
      }
      sb.append('}');
    } else {
      writeJsonString(value.toString(), sb);
    }
  }

  /**
   * Formats a float64 for canonical JSON, matching Go {@code encoding/json}'s {@code floatEncoder}.
   *
   * <p>Go uses {@code strconv.FormatFloat} with precision {@code -1} and format {@code 'f'} when
   * {@code 1e-6 <= |f| < 1e21}, otherwise format {@code 'e'}, then strips a single leading zero
   * from negative exponents (e.g. {@code e-07} to {@code e-7}). See {@code encode.go} in Go 1.21+.
   */
  private static void appendGoJsonFloat(double f, StringBuilder sb) {
    if (Double.isNaN(f) || Double.isInfinite(f)) {
      sb.append(Double.toString(f));
      return;
    }
    // Go encodes -0.0 as "0" so the sign is not preserved in JSON output.
    if (f == 0.0) {
      sb.append('0');
      return;
    }
    double abs = Math.abs(f);
    boolean useExp = abs < 1e-6 || abs >= 1e21;
    String formatted = useExp ? formatGoJsonFloatE(f) : formatGoJsonFloatF(f);
    if (useExp) {
      formatted = cleanupGoJsonExponent(formatted);
    }
    sb.append(formatted);
  }

  private static String formatGoJsonFloatF(double f) {
    return BigDecimal.valueOf(f).stripTrailingZeros().toPlainString();
  }

  private static String formatGoJsonFloatE(double f) {
    String s = Double.toString(f).replace('E', 'e');
    int eIdx = s.indexOf('e');
    if (eIdx < 0) {
      return s;
    }
    String mantissa = s.substring(0, eIdx);
    String exponent = s.substring(eIdx + 1);
    if (mantissa.endsWith(".0")) {
      mantissa = mantissa.substring(0, mantissa.length() - 2);
    }
    if (!exponent.startsWith("-")) {
      exponent = "+" + exponent;
    }
    return mantissa + 'e' + exponent;
  }

  /** Mirrors Go's post-processing for {@code e-0N} where N is a single digit. */
  private static String cleanupGoJsonExponent(String s) {
    int n = s.length();
    if (n >= 4 && s.charAt(n - 4) == 'e' && s.charAt(n - 3) == '-' && s.charAt(n - 2) == '0') {
      return s.substring(0, n - 3) + s.charAt(n - 1);
    }
    return s;
  }

  private static void writeJsonString(String s, StringBuilder sb) {
    sb.append('"');
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      switch (c) {
        case '"':
          sb.append("\\\"");
          break;
        case '\\':
          sb.append("\\\\");
          break;
        case '\n':
          sb.append("\\n");
          break;
        case '\r':
          sb.append("\\r");
          break;
        case '\t':
          sb.append("\\t");
          break;
        case '&':
          sb.append("\\u0026");
          break;
        case '<':
          sb.append("\\u003c");
          break;
        case '>':
          sb.append("\\u003e");
          break;
        default:
          if (c < 0x20 || c == '\u2028' || c == '\u2029') {
            sb.append(String.format("\\u%04x", (int) c));
          } else {
            sb.append(c);
          }
          break;
      }
    }
    sb.append('"');
  }

  // ------------------------------------------------------------------
  // Crypto helpers (byte-oriented HMAC-SHA256 for key derivation chaining)
  // ------------------------------------------------------------------

  private static byte[] hmacSha256(byte[] key, String message) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(key, "HmacSHA256"));
      return mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
    } catch (NoSuchAlgorithmException e) {
      throw new TypeError("providers.aws.sign_req: HMAC algorithm not available");
    } catch (InvalidKeyException e) {
      throw new TypeError("providers.aws.sign_req: invalid HMAC key");
    }
  }

  private static String sha256Hex(byte[] data) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return bytesToHex(digest.digest(data));
    } catch (NoSuchAlgorithmException e) {
      throw new TypeError("providers.aws.sign_req: SHA-256 algorithm not available");
    }
  }

  private static String bytesToHex(byte[] bytes) {
    StringBuilder hex = new StringBuilder(bytes.length * 2);
    for (byte b : bytes) {
      hex.append(String.format("%02x", b));
    }
    return hex.toString();
  }

  private static String stringFromProperty(RegoObject obj, String key) {
    RegoValue v = obj.getProperty(key);
    return (v instanceof RegoString) ? ((RegoString) v).getValue() : "";
  }
}
