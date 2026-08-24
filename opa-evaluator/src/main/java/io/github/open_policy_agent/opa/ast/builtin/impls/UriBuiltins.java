package io.github.open_policy_agent.opa.ast.builtin.impls;

import io.github.open_policy_agent.opa.ast.builtin.BuiltinError;
import io.github.open_policy_agent.opa.ast.builtin.OpaBuiltin;
import io.github.open_policy_agent.opa.ast.builtin.OpaType;
import io.github.open_policy_agent.opa.ast.types.RegoBoolean;
import io.github.open_policy_agent.opa.ast.types.RegoObject;
import io.github.open_policy_agent.opa.ast.types.RegoString;
import io.github.open_policy_agent.opa.ast.types.RegoValue;
import io.github.open_policy_agent.opa.rego.EvaluationContext;

import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiFunction;

import static io.github.open_policy_agent.opa.ast.builtin.impls.utils.ArgHelper.getArg;

public class UriBuiltins {
  private static final char[] HEX = "0123456789ABCDEF".toCharArray();

  public static Map<String, BiFunction<EvaluationContext, RegoValue[], RegoValue>> builtins() {
    UriBuiltins instance = new UriBuiltins();

    return Map.of(
        "uri.parse", instance::parse,
        "uri.is_valid", instance::isValid);
  }

  @OpaBuiltin(
      name = "uri.is_valid",
      description = "Returns true if the input string is a valid URI",
      categories = {"encoding"},
      args = {@OpaType(name = "x", description = "URI string to validate")},
      result = @OpaType(name = "result", description = "true if x is a valid URI"))
  public RegoBoolean isValid(EvaluationContext ctx, RegoValue[] args) {
    if (!(args[0] instanceof RegoString input)) {
      return RegoBoolean.FALSE;
    }

    if (input.getValue().isEmpty()) {
      return RegoBoolean.FALSE;
    }

    try {
      parseGoCompatibleUri(input.getValue());
      return RegoBoolean.TRUE;
    } catch (URISyntaxException e) {
      return RegoBoolean.FALSE;
    }
  }

  @OpaBuiltin(
      name = "uri.parse",
      description = "Parses an input URI and returns its components.",
      categories = {"encoding"},
      args = {@OpaType(name = "x", description = "URI string to parse")},
      result = @OpaType(name = "result", description = "Parsed URI components"))
  public RegoObject parse(EvaluationContext ctx, RegoValue[] args) {
    String input = getArg(args, 0, RegoString.class).getValue();

    try {
      ParsedUri uri = parseGoCompatibleUri(input);
      RegoObject result = new RegoObject();

      if (uri.scheme() != null) {
        result.setProperty("scheme", new RegoString(uri.scheme()));
      }

      if (uri.hostname() != null) {
        result.setProperty("hostname", new RegoString(uri.hostname()));
      }

      if (uri.port() != null) {
        result.setProperty("port", new RegoString(uri.port()));
      }

      if (uri.path() != null) {
        result.setProperty("path", new RegoString(uri.path()));
        result.setProperty("raw_path", new RegoString(uri.rawPath()));
      }

      if (uri.rawQuery() != null) {
        result.setProperty("raw_query", new RegoString(uri.rawQuery()));
      }

      if (uri.fragment() != null) {
        result.setProperty("fragment", new RegoString(uri.fragment()));
      }

      return result;
    } catch (URISyntaxException e) {
      // matches go OPA's errors
      throw new BuiltinError("parse \"" + input + "\": " + e.getReason());
    }
  }

  private ParsedUri parseGoCompatibleUri(String input) throws URISyntaxException {
    if (containsControlCharacter(input)) {
      throw new URISyntaxException(input, "invalid control character in URI");
    }

    String remaining = input;
    String fragment = null;

    int fragmentSeparator = remaining.indexOf('#');
    if (fragmentSeparator != -1) {
      String rawFragment = remaining.substring(fragmentSeparator + 1);
      remaining = remaining.substring(0, fragmentSeparator);

      if (!rawFragment.isEmpty()) {
        fragment = decodeComponent(rawFragment, input);
      }
    }

    int schemeSeparator = findSchemeSeparator(remaining);
    String scheme = null;

    if (schemeSeparator != -1) {
      scheme = remaining.substring(0, schemeSeparator).toLowerCase(Locale.ROOT);
      remaining = remaining.substring(schemeSeparator + 1);
    }

    String rawQuery = null;
    int querySeparator = remaining.indexOf('?');

    if (querySeparator != -1) {
      rawQuery = remaining.substring(querySeparator + 1);
      remaining = remaining.substring(0, querySeparator);

      if (rawQuery.isEmpty()) {
        rawQuery = null;
      }
    }

    // Go treats scheme:value as an opaque URI. OPA does not expose the opaque value.
    if (scheme != null && !remaining.startsWith("/")) {
      return new ParsedUri(scheme, null, null, null, null, rawQuery, fragment);
    }

    String hostname = null;
    String port = null;

    boolean hasAuthority =
        remaining.startsWith("//")
            && (scheme != null || !remaining.startsWith("///"));

    if (hasAuthority) {
      int pathStart = remaining.indexOf('/', 2);
      String authority;

      if (pathStart == -1) {
        authority = remaining.substring(2);
        remaining = "";
      } else {
        authority = remaining.substring(2, pathStart);
        remaining = remaining.substring(pathStart);
      }

      ParsedAuthority parsedAuthority = parseAuthority(authority, input);
      hostname = parsedAuthority.hostname();
      port = parsedAuthority.port();

      if (hostname.isEmpty()) {
        hostname = null;
      }

      if (port != null && port.isEmpty()) {
        port = null;
      }
    } else if (scheme == null && !remaining.startsWith("/")) {
      int firstSlash = remaining.indexOf('/');
      String firstSegment =
          firstSlash == -1 ? remaining : remaining.substring(0, firstSlash);

      if (firstSegment.contains(":")) {
        throw new URISyntaxException(
            input, "first path segment in URI cannot contain colon");
      }
    }

    String path = null;
    String rawPath = null;

    if (!remaining.isEmpty()) {
      path = decodeComponent(remaining, input);

      // Go OPA keeps RawPath when the input path is not the canonical escaping
      // of the decoded path.
      rawPath = remaining.equals(goEscapePath(path)) ? path : remaining;
    }

    return new ParsedUri(
        scheme,
        hostname,
        port,
        path,
        rawPath,
        rawQuery,
        fragment);
  }

  private boolean containsControlCharacter(String input) {
    for (int i = 0; i < input.length(); i++) {
      char c = input.charAt(i);
      if (c <= 31 || c == 127) {
        return true;
      }
    }

    return false;
  }

  private int findSchemeSeparator(String input) throws URISyntaxException {
    for (int i = 0; i < input.length(); i++) {
      char c = input.charAt(i);

      if (c == ':') {
        if (i == 0) {
          throw new URISyntaxException(input, "missing protocol scheme");
        }

        return i;
      }

      boolean letter = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
      boolean digit = c >= '0' && c <= '9';
      boolean valid = letter || (i > 0 && (digit || c == '+' || c == '-' || c == '.'));

      if (!valid) {
        return -1;
      }
    }

    return -1;
  }

  private static String decodeComponent(String value, String input) throws URISyntaxException {
    try {
      // URLDecoder normally converts '+' to a space. Go path decoding preserves '+'.
      return URLDecoder.decode(value.replace("+", "%2B"), StandardCharsets.UTF_8);
    } catch (IllegalArgumentException e) {
      throw new URISyntaxException(input, e.getMessage());
    }
  }

  private ParsedAuthority parseAuthority(String authority, String input)
      throws URISyntaxException {
    int userInfoEnd = authority.lastIndexOf('@');

    if (userInfoEnd != -1 && !validUserinfo(authority.substring(0, userInfoEnd))) {
      throw new URISyntaxException(input, "net/url: invalid userinfo");
    }

    String hostAndPort =
        userInfoEnd == -1 ? authority : authority.substring(userInfoEnd + 1);

    String hostname;
    String port = null;

    if (hostAndPort.startsWith("[")) {
      int closingBracket = hostAndPort.indexOf(']');
      if (closingBracket == -1) {
        throw new URISyntaxException(input, "missing closing bracket in IPv6 address");
      }

      hostname = hostAndPort.substring(1, closingBracket);
      String remainder = hostAndPort.substring(closingBracket + 1);

      if (!remainder.isEmpty()) {
        if (!remainder.startsWith(":")) {
          throw new URISyntaxException(input, "invalid IPv6 authority");
        }

        port = remainder.substring(1);
      }
    } else {
      int portSeparator = hostAndPort.lastIndexOf(':');
      if (portSeparator == -1) {
        hostname = hostAndPort;
      } else {
        hostname = hostAndPort.substring(0, portSeparator);
        port = hostAndPort.substring(portSeparator + 1);

        if (hostname.contains(":")) {
          throw new URISyntaxException(input, "IPv6 address must use brackets");
        }
      }
    }

    // only 0-9 are allowed for ports, not other digits from other char sets
    if (port != null && !port.isEmpty() && !isAsciiDigits(port)) {
      throw new URISyntaxException(input, "invalid port \":" + port + "\" after host");
    }

    hostname = decodeHost(hostname, input);

    return new ParsedAuthority(hostname, port);
  }

  private static boolean isAsciiDigits(String s) {
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (c < '0' || c > '9') {
        return false;
      }
    }
    return true;
  }

  // Mirrors Go's net/url validUserinfo: unreserved characters, sub-delims, ':', '%' and '@'.
  private static boolean validUserinfo(String s) {
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      boolean alnum = (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9');
      if (!alnum && "-._:~!$&'()*+,;=%@".indexOf(c) == -1) {
        return false;
      }
    }
    return true;
  }

  // Decodes a host like Go's unescape(encodeHost): a %XX escape for an ASCII byte is rejected
  // ("invalid URL escape"), except %25 (% itself); escapes of non-ASCII bytes are decoded normally.
  private static String decodeHost(String host, String input) throws URISyntaxException {
    int i = 0;
    while (i < host.length()) {
      if (host.charAt(i) != '%') {
        i++;
        continue;
      }
      boolean validEscape =
          i + 2 < host.length() && isHex(host.charAt(i + 1)) && isHex(host.charAt(i + 2));
      if (!validEscape
          || (unhex(host.charAt(i + 1)) < 8 && !host.regionMatches(i, "%25", 0, 3))) {
        int end = Math.min(i + 3, host.length());
        throw new URISyntaxException(input, "invalid URL escape \"" + host.substring(i, end) + "\"");
      }
      i += 3;
    }
    return decodeComponent(host, input);
  }

  // Re-escapes a decoded path like Go escape(s, encodePath): only unreserved characters and the
  // path-reserved delimiters stay literal; everything else is percent-encoded.
  private static String goEscapePath(String path) {
    byte[] bytes = path.getBytes(StandardCharsets.UTF_8);
    StringBuilder sb = new StringBuilder(bytes.length);
    for (byte b : bytes) {
      int c = b & 0xff;
      if (shouldEscapePath(c)) {
        sb.append('%').append(HEX[c >> 4]).append(HEX[c & 0xf]);
      } else {
        sb.append((char) c);
      }
    }
    return sb.toString();
  }

  private static boolean shouldEscapePath(int c) {
    if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')) {
      return false;
    }
      return switch (c) {
          case '-', '_', '.', '~', '$', '&', '+', ',', '/', ':', ';', '=', '@' -> false;
          default -> true;
      };
  }

  private static boolean isHex(char c) {
    return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
  }

  private static int unhex(char c) {
    if (c >= '0' && c <= '9') {
      return c - '0';
    }
    if (c >= 'a' && c <= 'f') {
      return c - 'a' + 10;
    }
    return c - 'A' + 10;
  }

  private record ParsedUri(
      String scheme,
      String hostname,
      String port,
      String path,
      String rawPath,
      String rawQuery,
      String fragment) {}

  private record ParsedAuthority(String hostname, String port) {}
}
