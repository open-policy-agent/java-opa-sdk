package io.github.open_policy_agent.opa.bundle;

import io.github.open_policy_agent.opa.ast.types.RegoObject;
import io.github.open_policy_agent.opa.ast.types.RegoString;
import io.github.open_policy_agent.opa.ir.PolicyReader;
import io.github.open_policy_agent.opa.spi.Services;
import io.github.open_policy_agent.opa.storage.Store;

import java.io.IOException;
import java.io.InputStream;

/**
 * Processes individual bundle files and assembles them into a {@link Bundle}.
 *
 * <p>This class encapsulates the shared processing logic used by (file based) {@link BundleLoader}
 * implementations. Loaders are responsible for file discovery/extraction and delegate the actual
 * content processing here.
 *
 * <p>Supports nested {@code data.json} files: a file at {@code roles/data.json} contributes its
 * contents under the {@code roles} key in the merged data tree.
 *
 * <p>Usage:
 *
 * <pre>{@code
 * BundleAssembler assembler = new BundleAssembler();
 * assembler.loadPlan(planInputStream);
 * assembler.loadData("", rootDataStream);
 * assembler.loadData("roles", nestedDataStream);
 * assembler.loadManifest(manifestInputStream);
 * assembler.addRego("policy.rego", regoSource);
 * Bundle bundle = assembler.finish("myBundle", store);
 * }</pre>
 */
public class BundleAssembler {
  static final PolicyReader POLICY_READER = loadSingleton(PolicyReader.class);

  static final BundleParser BUNDLE_PARSER = loadSingleton(BundleParser.class);

  // Optional: null when opa-proto is absent (JSON-only bundles still work), the single
  // implementation when exactly one is registered, and loadOptional throws on duplicates — a
  // classpath error we want to surface early, just like POLICY_READER and BUNDLE_PARSER.
  static final ProtoBundleDecoder PROTO_DECODER = loadOptional(ProtoBundleDecoder.class);

  /**
   * Loads exactly one implementation of the given SPI from the classpath. Throws if zero or more
   * than one implementation is registered, since either case produces an ambiguous runtime.
   */
  private static <T> T loadSingleton(Class<T> spi) {
    return Services.loadAtMostOne(spi)
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "No "
                        + spi.getSimpleName()
                        + " implementation found on the classpath. Add a module that provides "
                        + spi.getSimpleName()
                        + " (e.g. opa-jackson)."));
  }

  /**
   * Loads an optional SPI: returns {@code null} when no implementation is registered, the single
   * implementation when exactly one is, and throws when more than one is registered.
   */
  private static <T> T loadOptional(Class<T> spi) {
    return Services.loadAtMostOne(spi).orElse(null);
  }

  private static ProtoBundleDecoder requireProtoDecoder() {
    if (PROTO_DECODER == null) {
      throw new IllegalStateException(
          "No ProtoBundleDecoder implementation found on the classpath, but the bundle is in proto"
              + " format (plan.pb / .manifest.pb). Add the opa-proto module to read proto-format"
              + " bundles.");
    }
    return PROTO_DECODER;
  }

  private final Bundle.Builder builder = new Bundle.Builder();
  private RegoObject data;
  private boolean hasContent;

  /** Load a compiled IR policy from a {@code plan.json} stream. */
  public void loadPlan(InputStream in) throws IOException {
    builder.withIrPolicy(POLICY_READER.read(in));
    hasContent = true;
  }

  /**
   * Load a compiled IR policy from a protobuf {@code plan.pb} stream.
   *
   * @throws IllegalStateException if no {@link ProtoBundleDecoder} is registered (add opa-proto)
   */
  public void loadPlanProto(InputStream in) throws IOException {
    builder.withIrPolicy(requireProtoDecoder().decodePlan(in));
    hasContent = true;
  }

  /**
   * Load data from a {@code data.json} at the given path within the bundle.
   *
   * <p>An empty path means root data. A path like {@code "roles"} places the parsed contents under
   * {@code data.roles}. Multiple calls merge into the same data tree.
   *
   * @param path the directory path relative to the bundle root (empty string for root)
   * @param in the data.json input stream
   */
  public void loadData(String path, InputStream in) throws IOException {
    RegoObject parsed = BUNDLE_PARSER.parseData(in);

    if (data == null) {
      data = new RegoObject();
    }

    if (path.isEmpty()) {
      data = data.merge(parsed);
    } else {
      String[] parts = path.split("/");
      RegoObject current = data;
      for (int i = 0; i < parts.length - 1; i++) {
        RegoString key = new RegoString(parts[i]);
        io.github.open_policy_agent.opa.ast.types.RegoValue existing = current.getProperty(key);
        if (existing instanceof RegoObject) {
          current = (RegoObject) existing;
        } else {
          RegoObject newObj = new RegoObject();
          current.setProp(key, newObj);
          current = newObj;
        }
      }
      RegoString finalKey = new RegoString(parts[parts.length - 1]);
      io.github.open_policy_agent.opa.ast.types.RegoValue existing = current.getProperty(finalKey);
      if (existing instanceof RegoObject) {
        current.setProp(finalKey, ((RegoObject) existing).merge(parsed));
      } else {
        current.setProp(finalKey, parsed);
      }
    }

    hasContent = true;
  }

  /** Load bundle metadata from a {@code .manifest} stream. */
  public void loadManifest(InputStream in) throws IOException {
    builder.withManifest(BUNDLE_PARSER.parseManifest(in));
  }

  /**
   * Load bundle metadata from a protobuf {@code .manifest.pb} stream.
   *
   * @throws IllegalStateException if no {@link ProtoBundleDecoder} is registered (add opa-proto)
   */
  public void loadManifestProto(InputStream in) throws IOException {
    builder.withManifest(requireProtoDecoder().decodeManifest(in));
  }

  /**
   * Detect the bundle's wire format from which plan/manifest artifacts are present, reject
   * mixed-format bundles, and load the plan and manifest in the correct format.
   *
   * <p>This centralizes the format-detection and routing policy so {@link BundleLoader}
   * implementations only need to locate the four possible artifacts and expose each as an {@link
   * InputStreamSource} (or {@code null} when absent), regardless of whether the bytes come from a
   * directory, a tarball, or elsewhere. Proto takes precedence over JSON for each artifact, matching
   * OPA; a proto plan cannot be paired with a JSON manifest and vice versa (see {@link
   * BundleFormat#validate}).
   *
   * @param planJson source for {@code plan.json}, or {@code null} if absent
   * @param planProto source for {@code plan.pb}, or {@code null} if absent
   * @param manifestJson source for {@code .manifest}, or {@code null} if absent
   * @param manifestProto source for {@code .manifest.pb}, or {@code null} if absent
   * @throws IllegalArgumentException if the bundle mixes wire formats
   * @throws IOException if a source cannot be opened or its contents cannot be parsed
   */
  public void loadPlanAndManifest(
      InputStreamSource planJson,
      InputStreamSource planProto,
      InputStreamSource manifestJson,
      InputStreamSource manifestProto)
      throws IOException {
    BundleFormat.validate(
        planJson != null, planProto != null, manifestJson != null, manifestProto != null);

    if (planProto != null) {
      try (InputStream in = planProto.open()) {
        loadPlanProto(in);
      }
    } else if (planJson != null) {
      try (InputStream in = planJson.open()) {
        loadPlan(in);
      }
    }

    if (manifestProto != null) {
      try (InputStream in = manifestProto.open()) {
        loadManifestProto(in);
      }
    } else if (manifestJson != null) {
      try (InputStream in = manifestJson.open()) {
        loadManifest(in);
      }
    }
  }

  /** Add a Rego source file by its relative path. */
  public void addRego(String path, String content) {
    builder.withRego(path, content);
  }

  /**
   * Validate, build the bundle, and write it to the store.
   *
   * @param id the bundle identifier used as the store key
   * @param store the store to write the bundle and data into
   * @return the assembled bundle
   * @throws IllegalArgumentException if neither plan.json nor data.json was loaded
   */
  public Bundle finish(String id, Store store) {
    if (!hasContent) {
      throw new IllegalArgumentException("bundle must contain plan.json and/or data.json");
    }
    Bundle bundle = builder.build();
    store.write(id, bundle, data != null ? data : new RegoObject());
    return bundle;
  }
}
