package io.github.open_policy_agent.opa.bundle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.open_policy_agent.opa.ast.types.RegoObject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.zip.GZIPOutputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import io.github.open_policy_agent.opa.storage.InMem;
import io.github.open_policy_agent.opa.storage.Store;

class TarballBundleLoaderTest {

  private static final String MINIMAL_PLAN =
      "{\"static\":{\"strings\":[],\"files\":[]},"
          + "\"plans\":{\"plans\":[{\"name\":\"main/main\",\"blocks\":[]}]},"
          + "\"funcs\":{\"funcs\":[]}}";

  @Test
  void load_withPlanAndData() throws IOException {
    byte[] tarball = new TarballBuilder()
        .addEntry("plan.json", MINIMAL_PLAN)
        .addEntry("data.json", "{\"key\":\"value\"}")
        .build();

    Store store = new InMem();
    Bundle bundle = new TarballBundleLoader("test", tarball).load(store);

    assertNotNull(bundle.irPolicy);
    assertNotNull(store.getBundles().get("test"));
    assertNotNull(store.currentData());
  }

  @Test
  void load_withPlanOnly() throws IOException {
    byte[] tarball = new TarballBuilder()
        .addEntry("plan.json", MINIMAL_PLAN)
        .build();

    Store store = new InMem();
    Bundle bundle = new TarballBundleLoader("test", tarball).load(store);

    assertNotNull(bundle.irPolicy);
  }

  @Test
  void load_withDataOnly() throws IOException {
    byte[] tarball = new TarballBuilder()
        .addEntry("data.json", "{}")
        .build();

    Store store = new InMem();
    Bundle bundle = new TarballBundleLoader("test", tarball).load(store);

    assertNull(bundle.irPolicy);
    assertNotNull(store.getBundles().get("test"));
  }

  @Test
  void load_withManifest() throws IOException {
    byte[] tarball = new TarballBuilder()
        .addEntry("plan.json", MINIMAL_PLAN)
        .addEntry(".manifest", "{\"revision\":\"abc123\",\"roots\":[\"\"]}")
        .build();

    Store store = new InMem();
    Bundle bundle = new TarballBundleLoader("test", tarball).load(store);

    assertNotNull(bundle.manifest);
    assertEquals("abc123", bundle.manifest.get("revision"));
  }

  @Test
  void load_withRegoFiles() throws IOException {
    byte[] tarball = new TarballBuilder()
        .addEntry("plan.json", MINIMAL_PLAN)
        .addEntry("policy.rego", "package main")
        .build();

    Store store = new InMem();
    Bundle bundle = new TarballBundleLoader("test", tarball).load(store);

    assertEquals(1, bundle.rego.size());
    assertTrue(bundle.rego.containsKey("policy.rego"));
    assertEquals("package main", bundle.rego.get("policy.rego"));
  }

  @Test
  void load_withRegoInSubdirectories() throws IOException {
    byte[] tarball = new TarballBuilder()
        .addEntry("plan.json", MINIMAL_PLAN)
        .addEntry("root.rego", "package root")
        .addEntry("policies/allow.rego", "package policies")
        .addEntry("policies/admin/admin.rego", "package admin")
        .addEntry("policies/admin/super/super.rego", "package super_admin")
        .build();

    Store store = new InMem();
    Bundle bundle = new TarballBundleLoader("test", tarball).load(store);

    assertEquals(4, bundle.rego.size());
    assertTrue(bundle.rego.containsKey("root.rego"));
    assertTrue(bundle.rego.containsKey("policies/allow.rego"));
    assertTrue(bundle.rego.containsKey("policies/admin/admin.rego"));
    assertTrue(bundle.rego.containsKey("policies/admin/super/super.rego"));
    assertEquals("package super_admin", bundle.rego.get("policies/admin/super/super.rego"));
  }

  @Test
  void load_withAbsolutePathEntries() throws IOException {
    byte[] tarball = new TarballBuilder()
        .addEntry("/plan.json", MINIMAL_PLAN)
        .addEntry("/data.json", "{}")
        .addEntry("/.manifest", "{\"revision\":\"\",\"roots\":[\"\"]}")
        .addEntry("/policies/allow.rego", "package allow")
        .build();

    Store store = new InMem();
    Bundle bundle = new TarballBundleLoader("test", tarball).load(store);

    assertNotNull(bundle.irPolicy);
    assertNotNull(bundle.manifest);
    assertEquals(1, bundle.rego.size());
    assertTrue(bundle.rego.containsKey("policies/allow.rego"));
  }

  @Test
  void load_withNestedDataFiles() throws IOException {
    byte[] tarball = new TarballBuilder()
        .addEntry("plan.json", MINIMAL_PLAN)
        .addEntry("data.json", "{\"root_key\":\"root_val\"}")
        .addEntry("roles/data.json", "{\"admin\":true}")
        .addEntry("users/permissions/data.json", "{\"read\":true}")
        .build();

    Store store = new InMem();
    new TarballBundleLoader("test", tarball).load(store);

    RegoObject data = store.currentData();
    // root-level key
    assertNotNull(data.getProperty("root_key"));
    // roles/data.json -> data.roles.admin
    RegoObject rolesData = (RegoObject) data.getProperty("roles");
    assertNotNull(rolesData);
    assertNotNull(rolesData.getProperty("admin"));
    // users/permissions/data.json -> data.users.permissions.read
    RegoObject usersData = (RegoObject) data.getProperty("users");
    assertNotNull(usersData);
    RegoObject permsData = (RegoObject) usersData.getProperty("permissions");
    assertNotNull(permsData);
    assertNotNull(permsData.getProperty("read"));
  }

  @Test
  void load_withNestedDataOnly() throws IOException {
    // No root data.json — only nested. Should still count as valid bundle.
    byte[] tarball = new TarballBuilder()
        .addEntry("config/data.json", "{\"enabled\":true}")
        .build();

    Store store = new InMem();
    Bundle bundle = new TarballBundleLoader("test", tarball).load(store);

    assertNull(bundle.irPolicy);
    RegoObject data = store.currentData();
    RegoObject configData = (RegoObject) data.getProperty("config");
    assertNotNull(configData);
    assertNotNull(configData.getProperty("enabled"));
  }

  @Test
  void load_withAbsolutePathNestedData() throws IOException {
    byte[] tarball = new TarballBuilder()
        .addEntry("/plan.json", MINIMAL_PLAN)
        .addEntry("/roles/data.json", "{\"editor\":true}")
        .build();

    Store store = new InMem();
    new TarballBundleLoader("test", tarball).load(store);

    RegoObject data = store.currentData();
    RegoObject rolesData = (RegoObject) data.getProperty("roles");
    assertNotNull(rolesData);
    assertNotNull(rolesData.getProperty("editor"));
  }

  @Test
  void load_withoutPlanOrData_throws() throws IOException {
    byte[] tarball = new TarballBuilder()
        .addEntry(".manifest", "{\"revision\":\"abc\"}")
        .build();

    Store store = new InMem();
    TarballBundleLoader loader = new TarballBundleLoader("test", tarball);

    IllegalArgumentException ex =
        assertThrows(IllegalArgumentException.class, () -> loader.load(store));
    assertTrue(ex.getMessage().contains("plan.json and/or data.json"));
  }

  @Test
  void load_nonExistentPath_throws() {
    Path noSuchFile = Paths.get("/no/such/file.tar.gz");
    TarballBundleLoader loader = new TarballBundleLoader("test", noSuchFile);
    Store store = new InMem();

    IllegalArgumentException ex =
        assertThrows(IllegalArgumentException.class, () -> loader.load(store));
    assertTrue(ex.getMessage().contains("does not exist"));
  }

  @Test
  void load_notTarGz_throws(@TempDir Path dir) throws IOException {
    Path file = dir.resolve("not-a-tarball.tar.gz");
    Files.write(file, "this is not a tarball".getBytes());

    TarballBundleLoader loader = new TarballBundleLoader("test", file);
    Store store = new InMem();

    IllegalArgumentException ex =
        assertThrows(IllegalArgumentException.class, () -> loader.load(store));
    assertTrue(ex.getMessage().contains("not a tar.gz"));
  }

  @Test
  void load_fromPath(@TempDir Path dir) throws IOException {
    byte[] tarball = new TarballBuilder()
        .addEntry("plan.json", MINIMAL_PLAN)
        .build();
    Path file = dir.resolve("bundle.tar.gz");
    Files.write(file, tarball);

    Store store = new InMem();
    Bundle bundle = new TarballBundleLoader("test", file).load(store);

    assertNotNull(bundle.irPolicy);
  }

  @Test
  void load_nullPathAndNullData_throws() {
    TarballBundleLoader loader = new TarballBundleLoader("test", (byte[]) null);
    Store store = new InMem();

    IllegalArgumentException ex =
        assertThrows(IllegalArgumentException.class, () -> loader.load(store));
    assertTrue(ex.getMessage().contains("No bundle path or data provided"));
  }

  /** Helper to build tar.gz byte arrays for testing. */
  private static class TarballBuilder {
    private final ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
    private final GZIPOutputStream gzipOut;
    private final TarArchiveOutputStream tarOut;

    TarballBuilder() throws IOException {
      gzipOut = new GZIPOutputStream(byteOut);
      tarOut = new TarArchiveOutputStream(gzipOut);
      tarOut.setLongFileMode(TarArchiveOutputStream.LONGFILE_GNU);
    }

    TarballBuilder addEntry(String name, String content) throws IOException {
      byte[] bytes = content.getBytes();
      TarArchiveEntry entry = new TarArchiveEntry(name);
      entry.setSize(bytes.length);
      tarOut.putArchiveEntry(entry);
      tarOut.write(bytes);
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
