package io.github.open_policy_agent.opa;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.GZIPOutputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import io.github.open_policy_agent.opa.config.Config;

/**
 * Decision provenance through a real {@link Opa} instance, with bundles loaded from disk.
 *
 * <p>OPA reports every bundle name in the store and reads its revision with {@code
 * ReadBundleRevisionFromStore}, which yields an empty string when none was recorded. So a bundle
 * whose manifest has no revision, and a bundle with no manifest at all, both appear with {@code ""}.
 */
class OpaProvenanceTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @TempDir private Path bundleDir;

  @Test
  void makeDecisionProvenanceReportsEveryBundle() throws Exception {
    writeBundle(
        "authz.tar.gz",
        "/mask/plan-authz.json",
        "{\"revision\": \"authz-1\", \"roots\": [\"authz\"]}");
    writeBundle("mask.tar.gz", "/mask/plan.json", "{\"roots\": [\"system\", \"test\"]}");

    Map<String, Opa.Provenance.ProvenanceBundle> bundles = provenanceBundles("authz", "mask");

    assertEquals(2, bundles.size());
    assertEquals("authz-1", bundles.get("authz").getRevision());
    assertEquals("", bundles.get("mask").getRevision());
  }

  @Test
  void makeDecisionProvenanceIncludesBundleWithoutManifest() throws Exception {
    writeBundle("authz.tar.gz", "/mask/plan-authz.json", null);

    Map<String, Opa.Provenance.ProvenanceBundle> bundles = provenanceBundles("authz");

    assertEquals(1, bundles.size());
    assertEquals("", bundles.get("authz").getRevision());
  }

  private Map<String, Opa.Provenance.ProvenanceBundle> provenanceBundles(String... bundleNames) {
    Config config = new Config();
    config.setServices(
        Map.of(
            "local",
            new Config.ServiceConfig().setName("local").setUrl(bundleDir.toUri().toString())));
    Map<String, Config.BundleConfig> bundles = new LinkedHashMap<>();
    for (String name : bundleNames) {
      bundles.put(
          name, new Config.BundleConfig().setService("local").setResource(name + ".tar.gz"));
    }
    config.setBundles(bundles);

    Opa opa = new Opa.Builder().withConfig(config).withDefaultEntrypoint("authz/allow").build();
    try {
      Opa.DecisionResult decision =
          opa.makeDecision(MAPPER.createObjectNode().put("user", "alice"));
      Opa.Provenance provenance = decision.getProvenance();
      assertNotNull(provenance);
      assertNotNull(provenance.getBundles());
      return provenance.getBundles();
    } finally {
      opa.close();
    }
  }

  /** Bundle tarball for BundlePlugin to load over file://; a null manifest omits the file. */
  private void writeBundle(String name, String planResource, String manifest) throws IOException {
    byte[] plan;
    try (InputStream in = getClass().getResourceAsStream(planResource)) {
      assertNotNull(in, "missing plan fixture " + planResource);
      plan = in.readAllBytes();
    }

    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (GZIPOutputStream gzip = new GZIPOutputStream(bytes);
        TarArchiveOutputStream tar = new TarArchiveOutputStream(gzip)) {
      addEntry(tar, "plan.json", plan);
      if (manifest != null) {
        addEntry(tar, ".manifest", manifest.getBytes(StandardCharsets.UTF_8));
      }
      tar.finish();
    }

    Files.write(bundleDir.resolve(name), bytes.toByteArray());
  }

  private static void addEntry(TarArchiveOutputStream tar, String name, byte[] content)
      throws IOException {
    TarArchiveEntry entry = new TarArchiveEntry(name);
    entry.setSize(content.length);
    tar.putArchiveEntry(entry);
    tar.write(content);
    tar.closeArchiveEntry();
  }
}
