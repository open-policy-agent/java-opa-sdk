package io.github.open_policy_agent.opa.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;
import java.util.zip.GZIPOutputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

public class CliTest {

  private static final String BUNDLE_DIR = "src/test/resources/ir_simple_dir";
  private static final String INPUT_JSON = "src/test/resources/input.json";
  private static final String ENTRYPOINT = "authz/allow";

  @TempDir static Path sharedTempDir;

  private static String bundleTgz;

  @BeforeAll
  static void buildBundleTarball() throws IOException {
    final Path source = Path.of(BUNDLE_DIR);
    final Path target = sharedTempDir.resolve("ir_simple.tar.gz");
    try (OutputStream fileOut = Files.newOutputStream(target);
        BufferedOutputStream buffered = new BufferedOutputStream(fileOut);
        GZIPOutputStream gzipOut = new GZIPOutputStream(buffered);
        TarArchiveOutputStream tarOut = new TarArchiveOutputStream(gzipOut)) {
      tarOut.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
      try (Stream<Path> walk = Files.walk(source)) {
        final Iterable<Path> ordered =
            walk.sorted(Comparator.naturalOrder())::iterator;
        for (final Path entry : ordered) {
          if (Files.isDirectory(entry)) {
            continue;
          }
          final String entryName = source.relativize(entry).toString().replace('\\', '/');
          final TarArchiveEntry tarEntry = new TarArchiveEntry(entry.toFile(), entryName);
          tarOut.putArchiveEntry(tarEntry);
          Files.copy(entry, tarOut);
          tarOut.closeArchiveEntry();
        }
      }
      tarOut.finish();
    }
    bundleTgz = target.toString();
  }

  private final ByteArrayOutputStream outContent = new ByteArrayOutputStream();
  private final ByteArrayOutputStream errContent = new ByteArrayOutputStream();
  private PrintStream originalOut;
  private PrintStream originalErr;
  private InputStream originalIn;

  @BeforeEach
  void captureStreams() {
    originalOut = System.out;
    originalErr = System.err;
    originalIn = System.in;
    System.setOut(new PrintStream(outContent, true, StandardCharsets.UTF_8));
    System.setErr(new PrintStream(errContent, true, StandardCharsets.UTF_8));
  }

  @AfterEach
  void restoreStreams() {
    System.setOut(originalOut);
    System.setErr(originalErr);
    System.setIn(originalIn);
  }

  private int run(String... args) {
    return new CommandLine(new Regoj()).setCaseInsensitiveEnumValuesAllowed(true).execute(args);
  }

  @Test
  void evalCommandExitsNonZeroWhenBundleMissing() {
    final int exitCode =
        run("eval", "-b", "/tmp/does-not-exist.tar.gz", "-i", INPUT_JSON, ENTRYPOINT);
    assertThat(exitCode).isNotZero();
  }

  @Test
  void evalCommandPrintsResultJsonByDefault() {
    final int exitCode = run("eval", "-b", bundleTgz, "-i", INPUT_JSON, ENTRYPOINT);
    assertThat(exitCode).isZero();
    final String out = outContent.toString(StandardCharsets.UTF_8);
    assertThat(out).contains("\"result\":true");
    assertThat(out.trim().lines().count()).isEqualTo(1);
  }

  @Test
  void evalCommandPrettyFormatProducesMultipleLines() {
    final int exitCode =
        run("eval", "-b", bundleTgz, "-i", INPUT_JSON, "--format", "pretty", ENTRYPOINT);
    assertThat(exitCode).isZero();
    final String out = outContent.toString(StandardCharsets.UTF_8);
    assertThat(out).contains("\"result\" : true");
    assertThat(out.lines().count()).isGreaterThan(1);
  }

  @Test
  void evalCommandReadsInputFromStdin() {
    final byte[] inputBytes =
        "{\"user\":{\"id\":\"alicex\",\"groups\":[\"super\"]}}"
            .getBytes(StandardCharsets.UTF_8);
    System.setIn(new ByteArrayInputStream(inputBytes));

    final int exitCode = run("eval", "-b", bundleTgz, "-I", ENTRYPOINT);
    assertThat(exitCode).isZero();
    assertThat(outContent.toString(StandardCharsets.UTF_8)).contains("\"result\":true");
  }

  @Test
  void evalCommandLoadsDirectoryBundle() {
    final int exitCode = run("eval", "-b", BUNDLE_DIR, "-i", INPUT_JSON, ENTRYPOINT);
    assertThat(exitCode).isZero();
    assertThat(outContent.toString(StandardCharsets.UTF_8)).contains("\"result\":true");
  }

  @Test
  void evalCommandFailDefinedReturnsNonZeroWhenResultDefined() {
    final int exitCode =
        run("eval", "-b", bundleTgz, "-i", INPUT_JSON, "--fail-defined", ENTRYPOINT);
    assertThat(exitCode).isOne();
  }

  @Test
  void evalCommandFailReturnsZeroWhenResultDefined() {
    final int exitCode = run("eval", "-b", bundleTgz, "-i", INPUT_JSON, "--fail", ENTRYPOINT);
    assertThat(exitCode).isZero();
  }

  @Test
  void evalCommandEntrypointFlagWorksWithoutPositional() {
    final int exitCode = run("eval", "-b", bundleTgz, "-i", INPUT_JSON, "-e", ENTRYPOINT);
    assertThat(exitCode).isZero();
    assertThat(outContent.toString(StandardCharsets.UTF_8)).contains("\"result\":true");
  }

  @Test
  void evalCommandWithoutEntrypointReturnsTwo() {
    final int exitCode = run("eval", "-b", bundleTgz, "-i", INPUT_JSON);
    assertThat(exitCode).isEqualTo(2);
    assertThat(errContent.toString(StandardCharsets.UTF_8)).contains("ENTRYPOINT");
  }

  @Test
  void evalCommandCoveragePrintsCoverageTable() {
    final int exitCode =
        run("eval", "-b", bundleTgz, "-i", INPUT_JSON, "--coverage", ENTRYPOINT);
    assertThat(exitCode).isZero();
    final String out = outContent.toString(StandardCharsets.UTF_8);
    assertThat(out).contains("FILE").contains("HITS").contains("COVERED LINES");
    assertThat(out).contains("simple.rego");
  }

  @Test
  void evalCommandProfileLimitTruncatesRows() {
    final int exitCode =
        run(
            "eval",
            "-b",
            bundleTgz,
            "-i",
            INPUT_JSON,
            "--profile",
            "--profile-limit",
            "1",
            ENTRYPOINT);
    assertThat(exitCode).isZero();
    final String out = outContent.toString(StandardCharsets.UTF_8);
    final long dataRows =
        out.lines()
            .filter(l -> l.startsWith("|"))
            .filter(l -> !l.contains("TIME") && !l.contains("LOCATION"))
            .filter(l -> !l.contains("METRIC") && !l.contains("STATEMENT"))
            .filter(l -> !l.contains("MEAN") && !l.contains("MIN") && !l.contains("MAX"))
            .count();
    assertThat(dataRows).isGreaterThan(0);
    final long profileLocationRows =
        out.lines().filter(l -> l.contains("simple.rego:") && l.startsWith("|")).count();
    assertThat(profileLocationRows).isEqualTo(1);
  }

  @Test
  void evalCommandCapabilitiesCurrentPrintsCapabilitiesAndExits() {
    final int exitCode = run("eval", "--capabilities-current", ENTRYPOINT);
    assertThat(exitCode).isZero();
    assertThat(outContent.toString(StandardCharsets.UTF_8)).contains("builtins");
  }

  @Test
  void evalCommandInstrumentImpliesMetrics() {
    final int exitCode =
        run("eval", "-b", bundleTgz, "-i", INPUT_JSON, "--instrument", ENTRYPOINT);
    assertThat(exitCode).isZero();
    assertThat(outContent.toString(StandardCharsets.UTF_8)).contains("METRIC");
  }

  @Test
  void evalCommandMetricsPrintsMetricsTable() {
    final int exitCode =
        run("eval", "-b", bundleTgz, "-i", INPUT_JSON, "--metrics", ENTRYPOINT);
    assertThat(exitCode).isZero();
    final String out = outContent.toString(StandardCharsets.UTF_8);
    assertThat(out).contains("METRIC").contains("TIME");
    assertThat(out).contains("cli_prepare_query");
  }

  @Test
  void evalCommandCountGreaterThanOneSwitchesToStatisticalMetrics() {
    final int exitCode =
        run(
            "eval",
            "-b",
            bundleTgz,
            "-i",
            INPUT_JSON,
            "--metrics",
            "--count",
            "3",
            ENTRYPOINT);
    assertThat(exitCode).isZero();
    final String out = outContent.toString(StandardCharsets.UTF_8);
    assertThat(out).contains("MIN").contains("MAX").contains("MEAN").contains("90%").contains("99%");
  }

  @Test
  void evalCommandCountWithProfileExercisesStatementAndProfileStats() {
    final int exitCode =
        run(
            "eval",
            "-b",
            bundleTgz,
            "-i",
            INPUT_JSON,
            "--profile",
            "--count",
            "3",
            ENTRYPOINT);
    assertThat(exitCode).isZero();
    final String out = outContent.toString(StandardCharsets.UTF_8);
    assertThat(out).contains("LOCATION").contains("STATEMENT");
    assertThat(out).contains("MEAN TOTAL");
    assertThat(out).contains("simple.rego:");
  }

  @Test
  void evalCommandCountIncludesLoadReloadsBundleEachIteration() {
    final int exitCode =
        run(
            "eval",
            "-b",
            bundleTgz,
            "-i",
            INPUT_JSON,
            "--metrics",
            "--count",
            "2",
            "--count-includes-load",
            ENTRYPOINT);
    assertThat(exitCode).isZero();
    final String out = outContent.toString(StandardCharsets.UTF_8);
    assertThat(out).contains("cli_load_bundles");
    assertThat(out).contains("cli_engine_build");
    assertThat(out).contains("cli_capabilities_register");
  }

  @Test
  void evalCommandExplainPrintsTraceLines() {
    final int exitCode =
        run("eval", "-b", bundleTgz, "-i", INPUT_JSON, "--explain", ENTRYPOINT);
    assertThat(exitCode).isZero();
    final String out = outContent.toString(StandardCharsets.UTF_8);
    assertThat(out).contains("Enter");
    assertThat(out).contains("simple.rego");
  }

  @Test
  void evalCommandProfileSortByLocationOrdersAlphabetically() {
    final int exitCode =
        run(
            "eval",
            "-b",
            bundleTgz,
            "-i",
            INPUT_JSON,
            "--profile",
            "--profile-sort",
            "location",
            ENTRYPOINT);
    assertThat(exitCode).isZero();
    final String out = outContent.toString(StandardCharsets.UTF_8);
    final java.util.regex.Pattern locationPattern =
        java.util.regex.Pattern.compile("(/simple\\.rego:\\d+)");
    final java.util.List<String> locations =
        out.lines()
            .filter(l -> l.startsWith("|") && l.contains("simple.rego:"))
            .map(
                l -> {
                  final java.util.regex.Matcher m = locationPattern.matcher(l);
                  return m.find() ? m.group(1) : l;
                })
            .collect(java.util.stream.Collectors.toList());
    assertThat(locations).isNotEmpty();
    final java.util.List<String> sorted = new java.util.ArrayList<>(locations);
    sorted.sort(java.util.Comparator.naturalOrder());
    assertThat(locations).isEqualTo(sorted);
  }

  @Test
  void evalCommandRejectsNonPositiveCount() {
    final int exitCode =
        run("eval", "-b", bundleTgz, "-i", INPUT_JSON, "--count", "0", ENTRYPOINT);
    assertThat(exitCode).isEqualTo(2);
    assertThat(errContent.toString(StandardCharsets.UTF_8)).contains("--count");
  }

  @Test
  void evalCommandRejectsMissingInput() {
    final int exitCode = run("eval", "-b", bundleTgz, ENTRYPOINT);
    assertThat(exitCode).isEqualTo(2);
    assertThat(errContent.toString(StandardCharsets.UTF_8)).contains("--input");
  }

  @Test
  void evalCommandRejectsMissingBundle() {
    final int exitCode = run("eval", "-i", INPUT_JSON, ENTRYPOINT);
    assertThat(exitCode).isEqualTo(2);
    assertThat(errContent.toString(StandardCharsets.UTF_8)).contains("--bundle");
  }

  @Test
  void evalCommandUnreadableInputExitsOneWithMessage() {
    final int exitCode =
        run("eval", "-b", bundleTgz, "-i", "/tmp/does-not-exist-input.json", ENTRYPOINT);
    assertThat(exitCode).isOne();
    assertThat(errContent.toString(StandardCharsets.UTF_8)).contains("Error reading input");
  }

  @Test
  void evalCommandRejectsUnknownFormat() {
    final int exitCode =
        run("eval", "-b", bundleTgz, "-i", INPUT_JSON, "--format", "jsno", ENTRYPOINT);
    assertThat(exitCode).isEqualTo(2);
    assertThat(errContent.toString(StandardCharsets.UTF_8))
        .contains("Invalid --format")
        .contains("jsno");
  }

  @Test
  void evalCommandRejectsUnknownProfileSort() {
    final int exitCode =
        run(
            "eval",
            "-b",
            bundleTgz,
            "-i",
            INPUT_JSON,
            "--profile",
            "--profile-sort",
            "bogus",
            ENTRYPOINT);
    assertThat(exitCode).isEqualTo(2);
    assertThat(errContent.toString(StandardCharsets.UTF_8))
        .contains("Invalid --profile-sort")
        .contains("bogus");
  }
}
