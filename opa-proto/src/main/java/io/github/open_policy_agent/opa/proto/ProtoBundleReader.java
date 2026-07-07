package io.github.open_policy_agent.opa.proto;

import io.github.open_policy_agent.opa.bundle.ProtoBundleDecoder;
import io.github.open_policy_agent.opa.ir.policy.Policy;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

/**
 * Protobuf-based {@link ProtoBundleDecoder} implementation.
 *
 * <p>Decodes the {@code plan.pb} and {@code .manifest.pb} files of a proto-format bundle (produced
 * by {@code opa build --format=proto}) into the same in-memory model the JSON readers produce, per
 * OPA's {@code v1/ir/plan.proto} and {@code v1/bundle/manifest.proto} schemas.
 *
 * <p>This class is discovered automatically via {@link java.util.ServiceLoader} — consumers only
 * need {@code opa-proto} on the classpath, they do not reference it directly.
 */
public final class ProtoBundleReader implements ProtoBundleDecoder {

  @Override
  public Policy decodePlan(InputStream in) throws IOException {
    opa.ir.v1.Policy proto = opa.ir.v1.Policy.parseFrom(in);
    try {
      return PlanMapper.toPolicy(proto);
    } catch (IllegalArgumentException e) {
      // PlanMapper rejects malformed plans (e.g. an unset operand value or an unknown statement
      // kind) with IllegalArgumentException; surface these as IOException so callers see one
      // consistent "unreadable plan.pb" contract alongside protobuf's own parse failures.
      throw new IOException("malformed proto plan (plan.pb): " + e.getMessage(), e);
    }
  }

  @Override
  public Map<String, Object> decodeManifest(InputStream in) throws IOException {
    return ManifestMapper.toMap(opa.bundle.v1.Manifest.parseFrom(in));
  }
}
