package io.github.open_policy_agent.opa.bundle;

import java.io.IOException;
import java.io.InputStream;

/**
 * Supplies a fresh {@link InputStream} for a single bundle artifact.
 *
 * <p>Lets {@link BundleLoader} implementations hand {@link BundleAssembler} a lazily-opened stream
 * (e.g. a file opened on demand, or an in-memory buffer) without exposing how the bytes are
 * sourced. Unlike {@link java.util.function.Supplier}, {@link #open()} may throw {@link IOException}.
 */
@FunctionalInterface
public interface InputStreamSource {

  /**
   * Opens a new stream over the artifact's bytes. The caller closes it.
   *
   * @return the opened stream
   * @throws IOException if the stream cannot be opened
   */
  InputStream open() throws IOException;
}
