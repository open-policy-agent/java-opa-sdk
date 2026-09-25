package io.github.open_policy_agent.opa.jackson;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.open_policy_agent.opa.bundle.Manifest;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JacksonBundleParserTest {

  private Manifest parse(String json) throws IOException {
    return new JacksonBundleParser().parseManifest(
        new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
  }

  @Test
  void appliesOpaDefaults() throws IOException {
    for (String json : List.of("{}", "null", "{\"revision\":null,\"roots\":null}")) {
      Manifest manifest = parse(json);
      assertEquals("", manifest.getRevision());
      assertEquals(List.of(""), manifest.getRoots());
      assertFalse(manifest.hasRoots());
    }
    assertEquals(List.of(), parse("{\"roots\":[]}").getRoots());
  }

  @Test
  void reportsInvalidKnownFieldsAsIoErrors() {
    for (String json : List.of("{\"revision\":123}", "{\"roots\":[1]}", "{\"rego_version\":1.5}",
        "{\"file_rego_versions\":{\"/policy.rego\":1.5}}")) {
      IOException error = assertThrows(IOException.class, () -> parse(json));
      assertInstanceOf(IllegalArgumentException.class, error.getCause());
    }
  }

  @Test
  void acceptsIntegralVersionNumbers() throws IOException {
    for (String number : List.of("1", "1.0", "1e0")) {
      Manifest manifest = parse("{\"rego_version\":" + number + ",\"file_rego_versions\":{\"/policy.rego\":" + number + "}}");
      assertEquals(1, manifest.getRegoVersion());
      assertEquals(Map.of("/policy.rego", 1), manifest.getFileRegoVersions());
    }
  }

  @Test
  void rejectsMalformedWasmResolverFields() {
    for (String resolver : List.of(
        "{\"entrypoint\":123}", "{\"module\":false}",
        "{\"annotations\":{}}", "{\"annotations\":[123]}")) {
      IOException error = assertThrows(IOException.class, () -> parse("{\"wasm\":[" + resolver + "]}"));
      assertInstanceOf(IllegalArgumentException.class, error.getCause());
    }
  }

  @Test
  void rejectsNonObjectDocuments() {
    for (String json : List.of("[]", "1", "\"text\"")) {
      assertThrows(IOException.class, () -> parse(json));
    }
  }
}
