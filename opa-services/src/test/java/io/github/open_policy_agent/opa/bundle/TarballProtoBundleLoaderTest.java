package io.github.open_policy_agent.opa.bundle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.open_policy_agent.opa.storage.InMem;
import io.github.open_policy_agent.opa.storage.Store;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.junit.jupiter.api.Test;

/**
 * Proto-format tarball bundles ({@code plan.pb} / {@code .manifest.pb}) load through {@link
 * TarballBundleLoader} exactly like their JSON counterparts. Fixtures are real {@code opa build}
 * output at the pinned OPA commit, differing only in wire format.
 */
class TarballProtoBundleLoaderTest {

  private static Path fixture(String name) {
    return Paths.get(
        Objects.requireNonNull(
                TarballProtoBundleLoaderTest.class
                    .getClassLoader()
                    .getResource("bundles/" + name))
            .getPath());
  }

  @Test
  void load_protoTarball_populatesPolicyAndManifest() throws IOException {
    byte[] tarball = Files.readAllBytes(fixture("bundle-proto.tar.gz"));

    Store store = new InMem();
    Bundle bundle = new TarballBundleLoader("proto", tarball).load(store);

    assertNotNull(bundle.irPolicy, "proto plan.pb should decode into an IR policy");
    assertNotNull(bundle.irPolicy.getPlans().getPlanByName("authz/allow"));
    assertEquals(1, ((Number) bundle.manifest.get("rego_version")).intValue());
  }

  @Test
  void load_protoAndJsonTarballs_yieldEquivalentPolicies() throws IOException {
    Bundle proto =
        new TarballBundleLoader("proto", Files.readAllBytes(fixture("bundle-proto.tar.gz")))
            .load(new InMem());
    Bundle json =
        new TarballBundleLoader("json", Files.readAllBytes(fixture("bundle-json.tar.gz")))
            .load(new InMem());

    // Plans/funcs carry the full statement structure; they must match across formats.
    assertEquals(json.irPolicy.getPlans(), proto.irPolicy.getPlans());
    assertEquals(json.irPolicy.getFuncs(), proto.irPolicy.getFuncs());
  }

  @Test
  void load_mixedFormatTarball_rejected() throws IOException {
    // proto plan bytes paired with a JSON manifest — OPA rejects this, so must the loader.
    byte[] planPb =
        Files.readAllBytes(fixture("bundle-proto.tar.gz")); // any bytes; only the entry name matters
    byte[] tarball =
        new ByteArrayBinaryTarball()
            .add("plan.pb", planPb)
            .add(".manifest", "{\"revision\":\"\"}".getBytes())
            .build();

    IllegalArgumentException ex =
        assertThrows(
            IllegalArgumentException.class,
            () -> new TarballBundleLoader("mixed", tarball).load(new InMem()));
    assertTrue(ex.getMessage().contains("mixes"), ex.getMessage());
  }

  /** Minimal gzip+tar builder that accepts binary entry content (unlike the JSON-only helper). */
  private static final class ByteArrayBinaryTarball {
    private final ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
    private final java.util.zip.GZIPOutputStream gzipOut;
    private final TarArchiveOutputStream tarOut;

    ByteArrayBinaryTarball() throws IOException {
      gzipOut = new java.util.zip.GZIPOutputStream(byteOut);
      tarOut = new TarArchiveOutputStream(gzipOut);
      tarOut.setLongFileMode(TarArchiveOutputStream.LONGFILE_GNU);
    }

    ByteArrayBinaryTarball add(String name, byte[] content) throws IOException {
      TarArchiveEntry entry = new TarArchiveEntry(name);
      entry.setSize(content.length);
      tarOut.putArchiveEntry(entry);
      tarOut.write(content);
      tarOut.closeArchiveEntry();
      return this;
    }

    byte[] build() throws IOException {
      tarOut.finish();
      tarOut.close();
      gzipOut.close();
      return byteOut.toByteArray();
    }
  }
}
