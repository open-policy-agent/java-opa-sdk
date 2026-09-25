package io.github.open_policy_agent.opa.bundle;

import io.github.open_policy_agent.opa.ir.policy.Policy;
import java.io.IOException;
import java.io.InputStream;

/**
 * Optional SPI for decoding protobuf-format bundle files produced by
 * {@code opa build --format=proto}.
 *
 * <p>OPA emits IR plan bundles in two wire formats. The default JSON form stores the plan at
 * {@code /plan.json} and the manifest at {@code /.manifest}; the proto form stores them at
 * {@code /plan.pb} and {@code /.manifest.pb} respectively. This SPI decodes the proto form into the
 * same in-memory model the JSON {@link io.github.open_policy_agent.opa.ir.PolicyReader} produces, so
 * evaluation is identical regardless of the on-disk format.
 *
 * <p>Unlike {@link io.github.open_policy_agent.opa.ir.PolicyReader} and {@link BundleParser} — of
 * which exactly one implementation must be registered — this decoder is <em>optional</em>. The core
 * {@code opa-evaluator} carries no protobuf dependency; a {@link BundleLoader} only requires a
 * decoder when it actually encounters proto-format files. Zero registered implementations is a valid
 * runtime (JSON-only bundles work without it); at most one may be registered.
 *
 * <p>Register implementations via {@link java.util.ServiceLoader} or, when using JPMS, via a
 * {@code provides} declaration in {@code module-info.java}. The {@code opa-proto} module provides
 * one.
 *
 * <p>Note that a proto-format bundle still stores data files as JSON ({@code data.json}); only the
 * plan and manifest change format. Data parsing therefore continues to flow through
 * {@link BundleParser}.
 */
public interface ProtoBundleDecoder {

  /**
   * Decode a compiled IR policy from a {@code plan.pb} stream.
   *
   * @param in the {@code plan.pb} input stream
   * @return the decoded policy
   * @throws IOException if the stream cannot be read or the bytes are not a valid proto plan
   */
  Policy decodePlan(InputStream in) throws IOException;

  /**
   * Decode bundle metadata from a {@code .manifest.pb} stream.
   *
   * @param in the {@code .manifest.pb} input stream
   * @return the decoded manifest
   * @throws IOException if the stream cannot be read or the bytes are not a valid proto manifest
   */
  Manifest decodeManifest(InputStream in) throws IOException;
}
