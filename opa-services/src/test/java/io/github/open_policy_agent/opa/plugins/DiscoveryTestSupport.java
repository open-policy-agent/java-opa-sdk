package io.github.open_policy_agent.opa.plugins;

import static java.nio.charset.StandardCharsets.UTF_8;

import io.github.open_policy_agent.opa.config.Config;
import io.github.open_policy_agent.opa.logging.Logger;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.zip.GZIPOutputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import tools.jackson.databind.ObjectMapper;

/** Shared builders for discovery bundles and a recording {@link Logger}, for test use only. */
public final class DiscoveryTestSupport {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private DiscoveryTestSupport() {}

  /** Builds a gzipped tarball with the given entries, written in iteration order. */
  public static byte[] tarGz(Map<String, String> entries) throws IOException {
    final ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
    try (GZIPOutputStream gzipOut = new GZIPOutputStream(byteOut);
        TarArchiveOutputStream tarOut = new TarArchiveOutputStream(gzipOut)) {
      for (Map.Entry<String, String> entry : entries.entrySet()) {
        final byte[] bytes = entry.getValue().getBytes(UTF_8);
        final TarArchiveEntry tarEntry = new TarArchiveEntry(entry.getKey());
        tarEntry.setSize(bytes.length);
        tarOut.putArchiveEntry(tarEntry);
        tarOut.write(bytes);
        tarOut.closeArchiveEntry();
      }
      tarOut.finish();
    }
    return byteOut.toByteArray();
  }

  /** A discovery bundle whose {@code data.json} is the given JSON text. */
  public static byte[] dataBundle(String dataJson) throws IOException {
    return tarGz(Map.of("data.json", dataJson));
  }

  /** A discovery bundle whose {@code data.json} is the serialized {@code config}. */
  public static byte[] configBundle(Config config) throws IOException {
    return dataBundle(MAPPER.writeValueAsString(config));
  }

  /** Like {@link #configBundle}, plus a {@code .rego} file, which discovery must reject. */
  public static byte[] configBundleWithRego(Config config) throws IOException {
    return tarGz(
        Map.of(
            "data.json",
            MAPPER.writeValueAsString(config),
            "policy.rego",
            "package discovery\n\nallow = true\n"));
  }

  /** A bundle with a manifest and no {@code data.json}. */
  public static byte[] emptyBundle() throws IOException {
    return tarGz(Map.of(".manifest", "{\"revision\": \"test-123\"}"));
  }

  public static void write(Path dir, String relativePath, byte[] bundle) throws IOException {
    final Path target = dir.resolve(relativePath);
    Files.createDirectories(target.getParent());
    Files.write(target, bundle);
  }

  /** Collects formatted log lines so tests can assert on them without mocking. */
  public static final class RecordingLogger implements Logger {
    private final List<String> lines = new CopyOnWriteArrayList<>();

    public List<String> lines() {
      return lines;
    }

    public boolean contains(String fragment) {
      return lines.stream().anyMatch(line -> line.contains(fragment));
    }

    @Override
    public void debug(String fmtMessage, Object... args) {
      log("DEBUG", fmtMessage, args);
    }

    @Override
    public void info(String fmtMessage, Object... args) {
      log("INFO", fmtMessage, args);
    }

    @Override
    public void warn(String fmtMessage, Object... args) {
      log("WARN", fmtMessage, args);
    }

    @Override
    public void error(String fmtMessage, Object... args) {
      log("ERROR", fmtMessage, args);
    }

    private void log(String level, String fmtMessage, Object[] args) {
      lines.add(level + " " + String.format(fmtMessage, args));
    }
  }
}
