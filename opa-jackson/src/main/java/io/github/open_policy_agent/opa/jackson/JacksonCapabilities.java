package io.github.open_policy_agent.opa.jackson;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.open_policy_agent.opa.rego.Capabilities;
import java.io.IOException;

/**
 * Jackson-backed JSON IO for {@link Capabilities}.
 *
 * <p>The evaluator's {@code Capabilities} class is a pure POJO with no Jackson dependency.
 * This helper provides the read/write convenience using Jackson.
 */
public final class JacksonCapabilities {

  private static final ObjectMapper MAPPER =
      new ObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL);

  private JacksonCapabilities() {}

  /** Parse a {@link Capabilities} from a JSON string. */
  public static Capabilities fromJson(String json) throws IOException {
    return MAPPER.readValue(json, Capabilities.class);
  }

  /** Serialize a {@link Capabilities} to a pretty-printed JSON string. */
  public static String toJson(Capabilities capabilities) throws IOException {
    return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(capabilities);
  }
}
