package io.github.open_policy_agent.opa.rego;

import java.util.ArrayList;
import java.util.List;
import io.github.open_policy_agent.opa.ast.builtin.Descriptor;

/**
 * Represents OPA capabilities, including available builtin functions. Loaded from JSON files
 * matching the opa-cap.json format. Use {@code io.github.open_policy_agent.opa.jackson.JacksonCapabilities}
 * (in the opa-jackson module) for JSON IO.
 */
public class Capabilities {

  public List<Descriptor> builtins = new ArrayList<>();

  public Capabilities() {}

  public Capabilities(List<Descriptor> builtins) {
    this.builtins = builtins;
  }
}
