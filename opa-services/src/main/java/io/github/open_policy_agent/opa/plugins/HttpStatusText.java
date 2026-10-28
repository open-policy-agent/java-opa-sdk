package io.github.open_policy_agent.opa.plugins;

import java.util.Map;

/** Reason phrases for the HTTP statuses a bundle server is likely to answer with, as Go spells them. */
final class HttpStatusText {

  private static final Map<Integer, String> TEXT =
      Map.ofEntries(
          Map.entry(400, "Bad Request"),
          Map.entry(401, "Unauthorized"),
          Map.entry(403, "Forbidden"),
          Map.entry(404, "Not Found"),
          Map.entry(405, "Method Not Allowed"),
          Map.entry(406, "Not Acceptable"),
          Map.entry(408, "Request Timeout"),
          Map.entry(409, "Conflict"),
          Map.entry(410, "Gone"),
          Map.entry(413, "Request Entity Too Large"),
          Map.entry(415, "Unsupported Media Type"),
          Map.entry(429, "Too Many Requests"),
          Map.entry(500, "Internal Server Error"),
          Map.entry(501, "Not Implemented"),
          Map.entry(502, "Bad Gateway"),
          Map.entry(503, "Service Unavailable"),
          Map.entry(504, "Gateway Timeout"));

  private HttpStatusText() {}

  /** The reason phrase for {@code code}, or the number itself when it is not in the table. */
  static String of(int code) {
    return TEXT.getOrDefault(code, String.valueOf(code));
  }
}
