package io.github.open_policy_agent.opa.bundle;

import io.github.open_policy_agent.opa.config.Config;
import io.github.open_policy_agent.opa.storage.Store;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPInputStream;

public class TarballBundleLoader implements BundleLoader {
  private static final long DEFAULT_MAX_DECOMPRESSED_SIZE =
      Config.BundleConfig.DEFAULT_MAX_SIZE_BYTES;

  private final Path path;
  private final byte[] data;
  private final String id;
  private final long maxDecompressedBytes;

  public TarballBundleLoader(String id, Path path) {
    this(id, path, DEFAULT_MAX_DECOMPRESSED_SIZE);
  }

  public TarballBundleLoader(String id, Path path, long maxDecompressedBytes) {
    this.path = path;
    this.data = null;
    this.id = id;
    this.maxDecompressedBytes = maxDecompressedBytes;
  }

  public TarballBundleLoader(String id, byte[] data) {
    this(id, data, DEFAULT_MAX_DECOMPRESSED_SIZE);
  }

  public TarballBundleLoader(String id, byte[] data, long maxDecompressedBytes) {
    this.path = null;
    this.data = data;
    this.id = id;
    this.maxDecompressedBytes = maxDecompressedBytes;
  }

  public Bundle load(Store store) {
    if (data != null) {
      return createBundleFromStream(id, new ByteArrayInputStream(data), store);
    }
    if (path == null) {
      throw new IllegalArgumentException("No bundle path or data provided");
    }
    if (!Files.exists(path)) {
      throw new IllegalArgumentException("The bundle does not exist: " + path);
    }
    if (!Files.isReadable(path)) {
      throw new IllegalArgumentException("The bundle is not readable: " + path);
    }
    try (var in = Files.newInputStream(path)) {
      byte[] magic = new byte[3];
      if (in.read(magic) != 3
          || magic[0] != (byte) 0x1F
          || magic[1] != (byte) 0x8B
          || magic[2] != (byte) 0x08) {
        throw new IllegalArgumentException("The bundle is not a tar.gz file: " + path);
      }
      return createBundleFromStream(id, Files.newInputStream(path), store);
    } catch (IOException e) {
      throw new IllegalArgumentException("Error reading the bundle: " + e.getMessage());
    }
  }

  private Bundle createBundleFromStream(String id, InputStream in, Store store) {
    BundleAssembler assembler = new BundleAssembler();
    try (GZIPInputStream gzipIn = new GZIPInputStream(in);
        TarArchiveInputStream tarIn = new TarArchiveInputStream(gzipIn, true)) {
      org.apache.commons.compress.archivers.tar.TarArchiveEntry entry;
      long totalDecompressedBytes = 0;
      while ((entry = tarIn.getNextTarEntry()) != null) {
        String entryName =
            entry.getName().startsWith("/") ? entry.getName().substring(1) : entry.getName();
        if (!entry.isDirectory() && entryName.equals("plan.json")) {
          byte[] entryBytes = readWithLimit(tarIn, maxDecompressedBytes - totalDecompressedBytes);
          totalDecompressedBytes += entryBytes.length;
          assembler.loadPlan(new ByteArrayInputStream(entryBytes));
        } else if (!entry.isDirectory()
            && (entryName.equals("data.json") || entryName.endsWith("/data.json"))) {
          byte[] entryBytes = readWithLimit(tarIn, maxDecompressedBytes - totalDecompressedBytes);
          totalDecompressedBytes += entryBytes.length;
          int lastSlash = entryName.lastIndexOf('/');
          String dataPath = lastSlash < 0 ? "" : entryName.substring(0, lastSlash);
          assembler.loadData(dataPath, new ByteArrayInputStream(entryBytes));
        } else if (!entry.isDirectory() && entryName.equals(".manifest")) {
          byte[] entryBytes = readWithLimit(tarIn, maxDecompressedBytes - totalDecompressedBytes);
          totalDecompressedBytes += entryBytes.length;
          assembler.loadManifest(new ByteArrayInputStream(entryBytes));
        } else if (!entry.isDirectory() && entryName.endsWith(".rego")) {
          byte[] entryBytes = readWithLimit(tarIn, maxDecompressedBytes - totalDecompressedBytes);
          totalDecompressedBytes += entryBytes.length;
          assembler.addRego(entryName, new String(entryBytes));
        } else if (!entry.isDirectory()) {
          long entrySize = Math.max(0, entry.getSize());
          totalDecompressedBytes += entrySize;
          if (totalDecompressedBytes > maxDecompressedBytes) {
            throw new IOException("Decompressed bundle size exceeds the configured limit");
          }
        }
      }
      return assembler.finish(id, store);
    } catch (IOException e) {
      throw new IllegalArgumentException("Error extracting bundle: " + e.getMessage(), e);
    }
  }

  private static byte[] readWithLimit(InputStream in, long remaining) throws IOException {
    if (remaining <= 0) {
      throw new IOException(
          "Decompressed bundle size exceeds the configured limit");
    }
    int toRead = remaining >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) (remaining + 1);
    byte[] bytes = in.readNBytes(toRead);
    if (bytes.length > remaining) {
      throw new IOException(
          "Decompressed bundle size exceeds the configured limit");
    }
    return bytes;
  }
}
