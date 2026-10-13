package io.github.open_policy_agent.opa.jackson;

import tools.jackson.databind.node.ObjectNode;
import io.github.open_policy_agent.opa.tracing.CoverageReport;
import io.github.open_policy_agent.opa.tracing.CoverageReportWriter;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * The {@code opa-jackson} implementation of {@link CoverageReportWriter}, encoding a {@link
 * CoverageReport} as {@link OpaCoverageReport} JSON and writing it out. It lives here rather than
 * beside the interface because {@code opa-evaluator} has no Jackson dependency;
 * {@code META-INF/services} lets {@code CoverageRecorder} discover it at runtime if present.
 */
public final class JacksonCoverageReportWriter implements CoverageReportWriter {

  @Override
  public void write(CoverageReport report, Path outputDir, String baseName) throws IOException {
    ObjectNode json = OpaCoverageReport.toJson(report);

    // Write beside the target and move into place to avoid readers getting broken JSON.
    Path target = outputDir.resolve(baseName + ".json");
    Path tmp = outputDir.resolve(baseName + ".json.tmp");
    try {
      Files.writeString(tmp, json.toString());
      try {
        Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE);
      } catch (AtomicMoveNotSupportedException e) {
        Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
      }
    } catch (IOException e) {
      // Invisible to the glob, so it would linger unnoticed until something else cleaned up.
      try {
        Files.deleteIfExists(tmp);
      } catch (IOException ignored) {
        // Nothing useful left to do.
      }
      throw e;
    }
  }
}
