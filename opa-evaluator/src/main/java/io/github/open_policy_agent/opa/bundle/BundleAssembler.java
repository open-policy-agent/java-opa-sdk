package io.github.open_policy_agent.opa.bundle;

import io.github.open_policy_agent.opa.ast.types.RegoObject;
import io.github.open_policy_agent.opa.ast.types.RegoString;
import io.github.open_policy_agent.opa.ir.PolicyReader;
import io.github.open_policy_agent.opa.storage.Store;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

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

  /**
   * Loads exactly one implementation of the given SPI from the classpath. Throws if zero or more
   * than one implementation is registered, since either case produces an ambiguous runtime.
   */
  private static <T> T loadSingleton(Class<T> spi) {
    List<T> impls = new ArrayList<>();
    for (T impl : ServiceLoader.load(spi)) {
      impls.add(impl);
    }
    if (impls.isEmpty()) {
      throw new IllegalStateException(
          "No "
              + spi.getSimpleName()
              + " implementation found on the classpath. Add a module that provides "
              + spi.getSimpleName()
              + " (e.g. opa-jackson).");
    }
    if (impls.size() > 1) {
      StringBuilder names = new StringBuilder();
      for (int i = 0; i < impls.size(); i++) {
        if (i > 0) names.append(", ");
        names.append(impls.get(i).getClass().getName());
      }
      throw new IllegalStateException(
          "Multiple "
              + spi.getSimpleName()
              + " implementations found on the classpath: "
              + names
              + ". Only one provider may be registered.");
    }
    return impls.get(0);
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
