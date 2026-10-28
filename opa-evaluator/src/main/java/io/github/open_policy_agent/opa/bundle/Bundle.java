package io.github.open_policy_agent.opa.bundle;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import io.github.open_policy_agent.opa.ir.policy.Policy;

public class Bundle {
  public final Policy irPolicy;
  public final Map<String, Object> manifest;
  public final Map<String, String> rego;

  /**
   * The roots the manifest declares, without surrounding slashes. A manifest without {@code roots}
   * owns the whole document, {@code [""]}; an empty list owns nothing, as in OPA.
   */
  public List<String> roots() {
    if (manifest != null && manifest.get("roots") instanceof List<?> roots) {
      return roots.stream().map(root -> String.valueOf(root).replaceAll("^/+|/+$", "")).toList();
    }
    return List.of("");
  }

  private Bundle(Builder builder) {
    this.rego = builder.rego;
    this.manifest = builder.manifest;
    this.irPolicy = builder.irPolicy;
  }

  public static class Builder {
    private final Map<String, String> rego = new HashMap<>();
    private Map<String, Object> manifest;
    private Policy irPolicy;

    public Builder withRego(String path, String rego) {
      this.rego.put(path, rego);
      return this;
    }

    public Builder withManifest(Map<String, Object> manifest) {
      this.manifest = manifest;
      return this;
    }

    public Builder withIrPolicy(Policy irPolicy) {
      this.irPolicy = irPolicy;
      return this;
    }

    public Builder withPolicy(Policy policy) {
      this.irPolicy = policy;
      return this;
    }

    public Bundle build() {
      return new Bundle(this);
    }
  }
}
