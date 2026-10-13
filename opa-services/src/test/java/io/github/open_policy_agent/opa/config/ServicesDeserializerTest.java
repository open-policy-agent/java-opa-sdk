package io.github.open_policy_agent.opa.config;

import static org.junit.jupiter.api.Assertions.*;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.Test;

class ServicesDeserializerTest {

  private final ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

  @Test
  void deserialize_mapFormat_success() throws Exception {
    String yaml =
        "services:\n"
            + "  acmecorp:\n"
            + "    url: https://example.com/api\n"
            + "    credentials:\n"
            + "      bearer:\n"
            + "        token: secret123\n"
            + "  other:\n"
            + "    url: https://other.com/api\n";

    Config config = mapper.readValue(yaml, Config.class);

    assertNotNull(config.getServices());
    assertEquals(2, config.getServices().size());

    // Verify acmecorp service
    Config.ServiceConfig acmecorp = config.getService("acmecorp");
    assertNotNull(acmecorp);
    assertEquals("acmecorp", acmecorp.getName());
    assertEquals("https://example.com/api", acmecorp.getUrl());
    assertNotNull(acmecorp.getCredentials());
    assertNotNull(acmecorp.getCredentials().getBearer());
    assertEquals("secret123", acmecorp.getCredentials().getBearer().getToken());

    // Verify other service
    Config.ServiceConfig other = config.getService("other");
    assertNotNull(other);
    assertEquals("other", other.getName());
    assertEquals("https://other.com/api", other.getUrl());
  }

  @Test
  void deserialize_arrayFormat_success() throws Exception {
    String yaml =
        "services:\n"
            + "  - name: acmecorp\n"
            + "    url: https://example.com/api\n"
            + "    credentials:\n"
            + "      bearer:\n"
            + "        token: secret123\n"
            + "  - name: other\n"
            + "    url: https://other.com/api\n";

    Config config = mapper.readValue(yaml, Config.class);

    assertNotNull(config.getServices());
    assertEquals(2, config.getServices().size());

    // Verify acmecorp service
    Config.ServiceConfig acmecorp = config.getService("acmecorp");
    assertNotNull(acmecorp);
    assertEquals("acmecorp", acmecorp.getName());
    assertEquals("https://example.com/api", acmecorp.getUrl());
    assertNotNull(acmecorp.getCredentials());
    assertNotNull(acmecorp.getCredentials().getBearer());
    assertEquals("secret123", acmecorp.getCredentials().getBearer().getToken());

    // Verify other service
    Config.ServiceConfig other = config.getService("other");
    assertNotNull(other);
    assertEquals("other", other.getName());
    assertEquals("https://other.com/api", other.getUrl());
  }

  @Test
  void deserialize_arrayFormat_missingName_throwsException() {
    String yaml = "services:\n" + "  - url: https://example.com/api\n" + "  - name: other\n";

    assertThrows(Exception.class, () -> mapper.readValue(yaml, Config.class));
  }

  @Test
  void deserialize_emptyServices_success() throws Exception {
    String yaml = "services: {}\n";

    Config config = mapper.readValue(yaml, Config.class);

    assertNotNull(config.getServices());
    assertEquals(0, config.getServices().size());
  }

  @Test
  void deserialize_emptyServicesArray_success() throws Exception {
    String yaml = "services: []\n";

    Config config = mapper.readValue(yaml, Config.class);

    assertNotNull(config.getServices());
    assertEquals(0, config.getServices().size());
  }

  @Test
  void deserialize_noServices_success() throws Exception {
    String yaml = "labels:\n" + "  environment: test\n";

    Config config = mapper.readValue(yaml, Config.class);

    assertNull(config.getServices());
  }

  @Test
  void deserialize_mapFormatWithTimeouts_success() throws Exception {
    String yaml =
        "services:\n"
            + "  acmecorp:\n"
            + "    url: https://example.com/api\n"
            + "    response_header_timeout_seconds: 30\n"
            + "    allow_insecure_tls: true\n";

    Config config = mapper.readValue(yaml, Config.class);

    Config.ServiceConfig acmecorp = config.getService("acmecorp");
    assertNotNull(acmecorp);
    assertEquals(30, acmecorp.getResponseHeaderTimeoutSeconds());
    assertTrue(acmecorp.isAllowInsecureTLS());
  }

  @Test
  void deserialize_arrayFormatWithTimeouts_success() throws Exception {
    String yaml =
        "services:\n"
            + "  - name: acmecorp\n"
            + "    url: https://example.com/api\n"
            + "    response_header_timeout_seconds: 30\n"
            + "    allow_insecure_tls: true\n";

    Config config = mapper.readValue(yaml, Config.class);

    Config.ServiceConfig acmecorp = config.getService("acmecorp");
    assertNotNull(acmecorp);
    assertEquals(30, acmecorp.getResponseHeaderTimeoutSeconds());
    assertTrue(acmecorp.isAllowInsecureTLS());
  }
}
