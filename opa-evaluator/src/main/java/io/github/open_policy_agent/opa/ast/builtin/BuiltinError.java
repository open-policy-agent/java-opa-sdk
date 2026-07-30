package io.github.open_policy_agent.opa.ast.builtin;

import io.github.open_policy_agent.opa.OpaException;

public class BuiltinError extends OpaException {
    private final String rawCause;

    public BuiltinError(String message) {
    super("eval_builtin_error", message, null);
    this.rawCause = message;
    }

    public BuiltinError(String name, BuiltinError cause) {
    super("eval_builtin_error", name + ": " + cause.rawCause, cause);
    this.rawCause = cause.rawCause;
    }
}
