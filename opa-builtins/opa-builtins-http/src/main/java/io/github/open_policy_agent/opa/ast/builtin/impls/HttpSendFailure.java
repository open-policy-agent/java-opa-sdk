package io.github.open_policy_agent.opa.ast.builtin.impls;

import java.util.Locale;
import io.github.open_policy_agent.opa.OpaException;
import io.github.open_policy_agent.opa.ast.builtin.BuiltinError;
import io.github.open_policy_agent.opa.rego.TypeError;

/**
 * A failed {@code http.send} call. It is either raised as a builtin error or, when the request sets
 * {@code raise_error: false}, turned into the {@code {"status_code": 0, "error": {...}}} result
 * object. It is also what the intra-query cache remembers for a request that failed, so it carries
 * no stack trace.
 */
final class HttpSendFailure extends RuntimeException {

  private static final long serialVersionUID = 1L;

  static final String NETWORK_ERROR_CODE = "eval_http_send_network_error";
  static final String INTERNAL_ERROR_CODE = "eval_http_send_internal_error";

  private enum Kind {
    /** A problem with the request itself that OPA reports as a type error. */
    OPERAND,
    /** Any other problem found while building or evaluating the request. */
    INTERNAL,
    /** The HTTP client could not complete the exchange. */
    NETWORK
  }

  private final Kind kind;
  private final String raisedMessage;

  private HttpSendFailure(Kind kind, String message, String raisedMessage) {
    super(message, null, false, false);
    this.kind = kind;
    this.raisedMessage = raisedMessage;
  }

  static HttpSendFailure operand(String message) {
    return new HttpSendFailure(Kind.OPERAND, message, message);
  }

  static HttpSendFailure internal(String message) {
    return new HttpSendFailure(Kind.INTERNAL, message, message);
  }

  /**
   * A transport-level failure, worded like Go's {@code *url.Error}: {@code Get "http://x": detail}.
   * Timeouts are raised with a shorter message, matching OPA.
   */
  static HttpSendFailure network(String method, String url, boolean timeout, String detail) {
    String op = method.isEmpty() ? "Get" : method.charAt(0) + method.substring(1).toLowerCase(Locale.ROOT);
    String message = op + " \"" + url + "\": " + detail;
    String raised = timeout ? op + " " + url + ": request timed out" : message;
    return new HttpSendFailure(Kind.NETWORK, message, raised);
  }

  /** The {@code error.code} reported when {@code raise_error} is false. */
  String code() {
    return kind == Kind.NETWORK ? NETWORK_ERROR_CODE : INTERNAL_ERROR_CODE;
  }

  /** The exception to throw when {@code raise_error} is true (the default). */
  OpaException toOpaException() {
    return kind == Kind.OPERAND ? new TypeError(raisedMessage) : new BuiltinError(raisedMessage);
  }
}
