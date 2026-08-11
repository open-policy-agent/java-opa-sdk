package io.github.open_policy_agent.opa.bundle;

/**
 * Constants and validation for the two on-disk bundle wire formats.
 *
 * <p>OPA's {@code opa build} emits plan bundles in either JSON (the default) or protobuf form. The
 * two forms use distinct filenames for the plan and manifest:
 *
 * <table border="1">
 *   <caption>Format-specific filenames</caption>
 *   <tr><th>Artifact</th><th>JSON</th><th>Proto</th></tr>
 *   <tr><td>Plan</td><td>{@code plan.json}</td><td>{@code plan.pb}</td></tr>
 *   <tr><td>Manifest</td><td>{@code .manifest}</td><td>{@code .manifest.pb}</td></tr>
 * </table>
 *
 * <p>Data files ({@code data.json}) are JSON in both forms; only the plan and manifest change.
 *
 * <p>OPA rejects bundles whose plan and manifest formats disagree (e.g. {@code plan.pb} paired with
 * a JSON {@code .manifest}). {@link BundleLoader} implementations call {@link #validate} to enforce
 * the same rule and to reject bundles that ambiguously contain both formats of the same artifact.
 */
public final class BundleFormat {

  /** Filename of a JSON-format IR plan. */
  public static final String PLAN_JSON = "plan.json";

  /** Filename of a protobuf-format IR plan. */
  public static final String PLAN_PROTO = "plan.pb";

  /** Filename of a JSON-format bundle manifest. */
  public static final String MANIFEST_JSON = ".manifest";

  /** Filename of a protobuf-format bundle manifest. */
  public static final String MANIFEST_PROTO = ".manifest.pb";

  private BundleFormat() {}

  /**
   * Reject bundles that mix the two wire formats.
   *
   * <p>Fails if a bundle contains both formats of the same artifact (e.g. both {@code plan.json} and
   * {@code plan.pb}), or if a plan and a manifest are present in disagreeing formats (e.g. a proto
   * plan with a JSON manifest), matching OPA's own auto-detection semantics. A bundle carrying only
   * a plan or only a manifest is always accepted.
   *
   * @param hasPlanJson whether a {@code plan.json} is present
   * @param hasPlanProto whether a {@code plan.pb} is present
   * @param hasManifestJson whether a {@code .manifest} is present
   * @param hasManifestProto whether a {@code .manifest.pb} is present
   * @throws IllegalArgumentException if the bundle mixes formats
   */
  public static void validate(
      boolean hasPlanJson,
      boolean hasPlanProto,
      boolean hasManifestJson,
      boolean hasManifestProto) {
    if (hasPlanJson && hasPlanProto) {
      throw new IllegalArgumentException(
          "Bundle contains both " + PLAN_JSON + " and " + PLAN_PROTO + "; plan format is ambiguous");
    }
    if (hasManifestJson && hasManifestProto) {
      throw new IllegalArgumentException(
          "Bundle contains both "
              + MANIFEST_JSON
              + " and "
              + MANIFEST_PROTO
              + "; manifest format is ambiguous");
    }
    if (hasPlanProto && hasManifestJson) {
      throw new IllegalArgumentException(
          "Bundle mixes plan and manifest formats: proto plan ("
              + PLAN_PROTO
              + ") with JSON manifest ("
              + MANIFEST_JSON
              + ")");
    }
    if (hasPlanJson && hasManifestProto) {
      throw new IllegalArgumentException(
          "Bundle mixes plan and manifest formats: JSON plan ("
              + PLAN_JSON
              + ") with proto manifest ("
              + MANIFEST_PROTO
              + ")");
    }
  }
}
