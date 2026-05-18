package io.github.open_policy_agent.opa.jackson;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.open_policy_agent.opa.ast.types.RegoObject;
import io.github.open_policy_agent.opa.bundle.BundleParser;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

/**
 * Jackson-backed {@link BundleParser}.
 *
 * <p>Discovered via {@link java.util.ServiceLoader} — consumers don't need to instantiate this
 * class directly.
 */
public class JacksonBundleParser implements BundleParser {

  private static final ObjectMapper MAPPER =
      new ObjectMapper().registerModule(new RegoValueModule());
  private static final TypeReference<Map<String, Object>> MAP_TYPE =
      new TypeReference<>() {};

  @Override
  public RegoObject parseData(InputStream in) throws IOException {
    return MAPPER.readValue(in, RegoObject.class);
  }

  @Override
  public Map<String, Object> parseManifest(InputStream in) throws IOException {
    return MAPPER.readValue(in, MAP_TYPE);
  }
}
