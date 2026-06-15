package io.github.open_policy_agent.opa.jackson;

import io.github.open_policy_agent.opa.ir.PolicyReader;
import io.github.open_policy_agent.opa.ir.policy.Policy;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.io.InputStream;

/**
 * Jackson-based {@link PolicyReader} implementation.
 *
 * <p>Parses OPA IR policy documents (JSON format) using a pre-configured
 * {@link ObjectMapper} with the {@link IrModule} registered.
 *
 * <p>This class is discovered automatically via {@link java.util.ServiceLoader}
 * (or JPMS {@code provides} declarations) — consumers do not need to reference it directly.
 */
public class JacksonPolicyReader implements PolicyReader {

  private static final ObjectMapper MAPPER =
      new ObjectMapper()
          .registerModule(new IrModule())
          .registerModule(new JavaTimeModule())
          .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
          // OPA IR uses "Index" (capitalized) for MakeNumberRefStmt.index — accept
          // case-insensitive property names so the Java field can stay conventional.
          .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES)
          // OPA IR uses snake_case for fields like Static.builtin_funcs.
          .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

  @Override
  public Policy read(InputStream in) throws IOException {
    return MAPPER.readValue(in, Policy.class);
  }
}