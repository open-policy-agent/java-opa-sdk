package io.github.open_policy_agent.opa.tracing;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import io.github.open_policy_agent.opa.ir.policy.Policy;
import io.github.open_policy_agent.opa.logging.Logger;
import io.github.open_policy_agent.opa.spi.Services;

/**
 * Records Rego coverage reports for the running JVM and writes them out once completed.
 *
 * <p>Off unless {@code -Dopa.coverage.output=<dir>} is set. Callers that do not ask for coverage
 * see no behaviour change. When on, {@link
 * io.github.open_policy_agent.opa.rego.EvaluationContext.Builder#build()} installs a profiler on
 * every evaluation, which lets an unmodified test suite be measured without a JVM agent or
 * changes to the code being tested.
 *
 * <p>One profiler per {@link Policy}, keyed by identity: coverage locations are file
 * indices into that plan's own static table, so a single shared profiler would resolve one
 * policy's indices against another policy's files. Each policy has its own report.
 *
 * <p>A policy's profiler is shared across every evaluation of that policy, which may run
 * concurrently, so {@link CoverageProfiler} is itself thread-safe.
 *
 * <p>Reports are written by a {@link CoverageReportWriter} found via {@link Services#loadAtMostOne},
 * the same single-provider policy the SDK's other SPIs use. Without a JSON library on the
 * classpath, recording stays off rather than failing at exit.
 */
public final class CoverageRecorder {

  /**
   * System property for the directory to write reports to (absent means disabled)
   */
  public static final String OUTPUT_DIR_PROPERTY = "opa.coverage.output";

  private static final Logger LOGGER = new Logger.StandardLogger();
  private static final Object LOCK = new Object();

  private static volatile CoverageRecorder instance;
  private static volatile boolean initialised;

  private final Path outputDir;
  private final CoverageReportWriter writer;
  private final String runId;
  private final Map<Policy, CoverageProfiler> profilers = new IdentityHashMap<>();

  private CoverageRecorder(Path outputDir, CoverageReportWriter writer) {
    this.outputDir = outputDir;
    this.writer = writer;
    // A random id keeps concurrent and successive JVMs from colliding in a shared output directory.
    this.runId = UUID.randomUUID().toString();
  }

  /**
   * The profiler to record {@code policy}'s coverage. This is created on first use.
   *
   * @param policy the policy being evaluated
   * @return the profiler, empty when coverage recording is off or {@code policy} is null
   */
  public static Optional<Profiler> profilerFor(Policy policy) {
    if (policy == null) {
      return Optional.empty();
    }
    CoverageRecorder recorder = instance();
    if (recorder == null) {
      return Optional.empty();
    }
    synchronized (LOCK) {
      return Optional.of(
          recorder.profilers.computeIfAbsent(policy, p -> new CoverageProfiler()));
    }
  }

  /**
   * True when {@link #OUTPUT_DIR_PROPERTY} is set and a writer was found.
   */
  public static boolean isEnabled() {
    return instance() != null;
  }

  private static CoverageRecorder instance() {
    if (initialised) {
      return instance;
    }
    // evaluations can run concurrently, but setup must happen exactly once.
    synchronized (LOCK) {
      if (initialised) {
        return instance;
      }
      initialised = true;
      instance = create();
      if (instance != null) {
        CoverageRecorder started = instance;
        Runtime.getRuntime()
            .addShutdownHook(new Thread(started::writeReports, "opa-coverage-shutdown"));
      }
      return instance;
    }
  }

  private static CoverageRecorder create() {
    String configured = System.getProperty(OUTPUT_DIR_PROPERTY);
    if (configured == null || configured.isBlank()) {
      return null;
    }

    CoverageReportWriter writer =
        Services.loadAtMostOne(CoverageReportWriter.class).orElse(null);
    if (writer == null) {
      LOGGER.error(
          "%s is set but no CoverageReportWriter is on the classpath; add opa-jackson to record"
              + " coverage. Continuing without it.",
          OUTPUT_DIR_PROPERTY);
      return null;
    }

    Path dir = Path.of(configured.trim());
    try {
      Files.createDirectories(dir);
    } catch (IOException e) {
      LOGGER.error("cannot create coverage output directory %s; continuing without: %s", dir, e);
      return null;
    }
    return new CoverageRecorder(dir, writer);
  }

  private void writeReports() {
    List<Map.Entry<Policy, CoverageProfiler>> snapshot;
    synchronized (LOCK) {
      snapshot = new ArrayList<>(profilers.entrySet());
    }
    int index = 0;
    for (Map.Entry<Policy, CoverageProfiler> entry : snapshot) {
      // Index disambiguates policies within this JVM; runId disambiguates JVMs.
      String baseName = "report-" + runId + "-" + index++;
      try {
        Policy policy = entry.getKey();
        CoverageReport report = CoverageReport.from(entry.getValue(), policy);
        writer.write(report, outputDir, baseName);
      } catch (Exception e) {
        // A shutdown hook cannot usefully propagate error. One bad report does not impact others.
        LOGGER.error("failed to write coverage report %s: %s", baseName, e);
      }
    }
  }
}
