package io.github.open_policy_agent.opa.bundle;

import io.github.open_policy_agent.opa.ast.types.RegoObject;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

/**
 * SPI for parsing bundle JSON streams without exposing a specific JSON library to the evaluator.
 *
 * <p>Register implementations via {@link java.util.ServiceLoader}. The {@code opa-jackson} module
 * provides a Jackson-based implementation.
 */
public interface BundleParser {

  /** Parse a {@code data.json} stream into a {@link RegoObject}. */
  RegoObject parseData(InputStream in) throws IOException;

  /** Parse a {@code .manifest} stream into a plain Map tree (no third-party JSON types). */
  Map<String, Object> parseManifest(InputStream in) throws IOException;
}
