package io.github.open_policy_agent.opa.plugins;

import io.github.open_policy_agent.opa.bundle.Bundle;
import java.time.Instant;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Status of one bundle or of discovery, reported in the shape of OPA's bundle status. Timestamps are
 * RFC 3339 UTC; one that has not happened yet is reported as Go's zero time.
 */
final class BundleStatus {

  private static final Instant ZERO = Instant.parse("0001-01-01T00:00:00Z");
  private static final String ERROR_CODE = "bundle_error";

  private final String name;
  private String activeRevision = "";
  private String type = "";
  private int size;
  private int downloadedSize;
  private Instant lastRequest = ZERO;
  private Instant lastSuccessfulRequest = ZERO;
  private Instant lastSuccessfulDownload = ZERO;
  private Instant lastSuccessfulActivation = ZERO;
  private String code = "";
  private String message = "";
  private int httpCode;

  BundleStatus(String name) {
    this.name = name;
  }

  String name() {
    return name;
  }

  /** A download attempt started. */
  synchronized void request() {
    lastRequest = Instant.now();
  }

  /** The server answered but had nothing new; the current bundle stays active. */
  synchronized void notModified() {
    lastSuccessfulRequest = lastRequest;
    clearError();
  }

  /** A bundle of {@code downloadedBytes} bytes arrived and parsed; its size is reported once it is active. */
  synchronized void downloaded(int downloadedBytes) {
    lastSuccessfulRequest = lastRequest;
    lastSuccessfulDownload = lastRequest;
    type = "snapshot";
    downloadedSize = downloadedBytes;
  }

  /** The downloaded bundle is active; this also clears any earlier error. */
  synchronized void activated(String revision) {
    clearError();
    lastSuccessfulActivation = Instant.now();
    activeRevision = orEmpty(revision);
    size = downloadedSize;
  }

  /** A download or activation failed; the last successful timestamps and revision are kept. */
  synchronized void failed(Throwable cause) {
    code = ERROR_CODE;
    httpCode = cause instanceof BundleDownloader.HttpStatusException
        ? ((BundleDownloader.HttpStatusException) cause).statusCode()
        : 0;
    message = cause.getMessage() != null ? cause.getMessage() : cause.toString();
  }

  synchronized void setMessage(String text) {
    message = orEmpty(text);
  }

  synchronized String message() {
    return message;
  }

  private void clearError() {
    code = "";
    message = "";
    httpCode = 0;
  }

  /** The OPA-shaped JSON; fields OPA omits when empty are omitted. */
  synchronized ObjectNode toJson() {
    final ObjectNode json = JsonMapper.shared().createObjectNode();
    json.put("name", name);
    putIfSet(json, "active_revision", activeRevision);
    json.put("last_successful_activation", lastSuccessfulActivation.toString());
    putIfSet(json, "type", type);
    if (size > 0) {
      json.put("size", size);
    }
    json.put("last_successful_download", lastSuccessfulDownload.toString());
    json.put("last_successful_request", lastSuccessfulRequest.toString());
    json.put("last_request", lastRequest.toString());
    putIfSet(json, "code", code);
    putIfSet(json, "message", message);
    if (httpCode > 0) {
      json.put("http_code", httpCode);
    }
    return json;
  }

  /** The {@code revision} in a bundle's manifest, or null when it has none. */
  static String revisionOf(Bundle bundle) {
    final Object revision = bundle == null || bundle.manifest == null ? null : bundle.manifest.get("revision");
    return revision == null ? null : String.valueOf(revision);
  }

  private static String orEmpty(String text) {
    return text == null ? "" : text;
  }

  private static void putIfSet(ObjectNode json, String field, String value) {
    if (!value.isEmpty()) {
      json.put(field, value);
    }
  }
}
