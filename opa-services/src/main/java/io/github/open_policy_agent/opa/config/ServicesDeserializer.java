package io.github.open_policy_agent.opa.config;

import java.util.LinkedHashMap;
import java.util.Map;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DatabindException;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

/**
 * Custom deserializer for services configuration.
 *
 * <p>OPA supports two formats for services:
 *
 * <p>1. Map/Object format:
 *
 * <pre>{@code
 * services:
 *   acmecorp:
 *     url: https://example.com
 *   other:
 *     url: https://other.com
 * }</pre>
 *
 * <p>2. Array format:
 *
 * <pre>{@code
 * services:
 *   - name: acmecorp
 *     url: https://example.com
 *   - name: other
 *     url: https://other.com
 * }</pre>
 *
 * <p>This deserializer handles both formats and converts them to Map&lt;String, ServiceConfig&gt;.
 */
public class ServicesDeserializer extends ValueDeserializer<Map<String, Config.ServiceConfig>> {

  @Override
  public Map<String, Config.ServiceConfig> deserialize(
      JsonParser parser, DeserializationContext ctx) {

    Map<String, Config.ServiceConfig> services = new LinkedHashMap<>();

    JsonToken token = parser.currentToken();

    if (token == JsonToken.START_OBJECT) {
      // Map format: services: { acmecorp: {...}, other: {...} }
      parser.nextToken(); // Move to first field name
      while (parser.currentToken() != JsonToken.END_OBJECT) {
        String serviceName = parser.currentName();
        parser.nextToken(); // Move to the service config object
        Config.ServiceConfig serviceConfig = ctx.readValue(parser, Config.ServiceConfig.class);
        serviceConfig.setName(serviceName);
        services.put(serviceName, serviceConfig);
        parser.nextToken(); // Move to next field or END_OBJECT
      }
    } else if (token == JsonToken.START_ARRAY) {
      // Array format: services: [ {name: acmecorp, ...}, {name: other, ...} ]
      parser.nextToken(); // Move to first array element
      while (parser.currentToken() != JsonToken.END_ARRAY) {
        Config.ServiceConfig serviceConfig = ctx.readValue(parser, Config.ServiceConfig.class);
        String serviceName = serviceConfig.getName();
        if (serviceName == null || serviceName.isEmpty()) {
          throw DatabindException.from(
              parser,
              "Service name is required when using array format for services configuration");
        }
        services.put(serviceName, serviceConfig);
        parser.nextToken(); // Move to next array element or END_ARRAY
      }
    } else {
      throw DatabindException.from(parser, "Expected object or array for services, got: " + token);
    }

    return services;
  }
}
