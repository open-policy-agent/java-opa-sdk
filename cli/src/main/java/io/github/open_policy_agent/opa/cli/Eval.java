package io.github.open_policy_agent.opa.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import io.github.open_policy_agent.opa.ast.builtin.BuiltinRegistry;
import io.github.open_policy_agent.opa.bundle.FileSystemBundleLoader;
import io.github.open_policy_agent.opa.bundle.TarballBundleLoader;
import io.github.open_policy_agent.opa.ir.policy.Policy;
import io.github.open_policy_agent.opa.ir.policy.StringConst;
import io.github.open_policy_agent.opa.metrics.Metrics;
import io.github.open_policy_agent.opa.metrics.NoOpMetrics;
import io.github.open_policy_agent.opa.metrics.SimpleMetrics;
import io.github.open_policy_agent.opa.profiling.NoOpStatementProfiler;
import io.github.open_policy_agent.opa.profiling.SimpleStatementProfiler;
import io.github.open_policy_agent.opa.profiling.StatementProfiler;
import io.github.open_policy_agent.opa.rego.Capabilities;
import io.github.open_policy_agent.opa.rego.Engine;
import io.github.open_policy_agent.opa.rego.PrintHook;
import io.github.open_policy_agent.opa.storage.InMem;
import io.github.open_policy_agent.opa.storage.Store;
import io.github.open_policy_agent.opa.tracing.BufferedQueryTracer;
import io.github.open_policy_agent.opa.tracing.CoverageProfiler;
import io.github.open_policy_agent.opa.tracing.DurationProfiler;
import io.github.open_policy_agent.opa.tracing.QueryTracer;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "eval", mixinStandardHelpOptions = true)
public class Eval implements Callable<Integer> {

  @Option(
      names = {"-b", "--bundle"},
      description = "set bundle file or directory path(s). This flag can be repeated.")
  private List<Path> bundleFilePaths;

  @Option(
      names = {"--capabilities-current"},
      description = "displays current capabilities JSON and ends process")
  private boolean showCapabilities;

  @Option(
      names = {"--coverage"},
      description = "report coverage of policy lines executed during evaluation")
  private boolean showCoverage;

  @Option(
      names = {"--explain"},
      description =
          "enable query explanations (when --count > 1, only the first run's trace is printed)")
  private boolean explain;

  @Option(
      names = {"--fail"},
      description = "exit with non-zero exit code on undefined/empty result")
  private boolean fail;

  @Option(
      names = {"--fail-defined"},
      description = "exit with non-zero exit code on defined/non-empty result")
  private boolean failDefined;

  @Option(
      names = {"-f", "--format"},
      description = "set output format: json (default) or pretty")
  private String format = "json";

  @Option(
      names = {"-i", "--input"},
      description = "set input file path; required unless --stdin-input is used")
  private Path input;

  @Option(
      names = {"--instrument"},
      description = "alias for --metrics")
  private boolean instrument;

  @Option(
      names = {"--metrics"},
      description = "report query performance metrics")
  private boolean showMetrics;

  @Option(
      names = {"--profile"},
      description = "perform expression profiling")
  private boolean showProfile;

  @Option(
      names = {"--profile-limit"},
      description = "set number of profiling results to show (default 10)")
  private int profileLimit = 10;

  @Option(
      names = {"--profile-sort"},
      description =
          "sort key for profiler output: total_time (default), num_eval, or location")
  private String profileSort = "total_time";

  @Option(
      names = {"-I", "--stdin-input"},
      description = "read input document from stdin")
  private boolean stdinInput;

  @Option(
      names = {"--count"},
      description = "number of times to repeat each benchmark (default 1)")
  private int count = 1;

  @Option(
      names = {"--count-includes-load"},
      description =
          "include bundle load and engine build in each --count iteration "
              + "(default: load and build once, then repeat only prepare + eval)")
  private boolean countIncludesLoad;

  @Option(
      names = {"-e", "--entrypoint"},
      description = "set entrypoint name (overrides positional ENTRYPOINT)")
  private String entrypointFlag;

  @Parameters(arity = "0..1", paramLabel = "ENTRYPOINT")
  private String entrypointPositional;

  public Integer call() {

    final String entrypoint =
        entrypointFlag != null ? entrypointFlag : entrypointPositional;

    if (showCapabilities) {
      try {
        System.out.println(new ObjectMapper().writeValueAsString(BuiltinRegistry.generateCapabilities()));
      } catch (IOException e) {
        System.err.println(e.getMessage());
        return 1;
      }
      return 0;
    }

    if (entrypoint == null || entrypoint.isEmpty()) {
      System.err.println("Missing required ENTRYPOINT argument (or use -e/--entrypoint)");
      return 2;
    }

    if (count < 1) {
      System.err.println("--count must be >= 1");
      return 2;
    }

    if (!stdinInput && input == null) {
      System.err.println("Either -i/--input or -I/--stdin-input is required");
      return 2;
    }

    if (bundleFilePaths == null || bundleFilePaths.isEmpty()) {
      System.err.println("At least one -b/--bundle path is required");
      return 2;
    }

    if (!"json".equalsIgnoreCase(format) && !"pretty".equalsIgnoreCase(format)) {
      System.err.println("Invalid --format value '" + format + "' (allowed: json, pretty)");
      return 2;
    }

    if (!"total_time".equalsIgnoreCase(profileSort)
        && !"num_eval".equalsIgnoreCase(profileSort)
        && !"location".equalsIgnoreCase(profileSort)) {
      System.err.println(
          "Invalid --profile-sort value '"
              + profileSort
              + "' (allowed: total_time, num_eval, location)");
      return 2;
    }

    if (instrument) {
      showMetrics = true;
    }

    final ObjectMapper objectMapper = new ObjectMapper();

    final List<Metrics> allMetrics = new ArrayList<>(count);
    final List<QueryTracer> allTracers = new ArrayList<>(count);
    final List<DurationProfiler> allProfilers = new ArrayList<>(count);
    final List<StatementProfiler> allStatementProfilers = new ArrayList<>(count);
    final List<CoverageProfiler> allCoverageProfilers = new ArrayList<>(count);
    String[] fileNames = new String[] {};
    List<Object> lastResults = null;

    Engine sharedEngine = null;
    if (!countIncludesLoad) {
      final Capabilities capabilities = BuiltinRegistry.generateCapabilities();
      final Store store = new InMem();
      final Engine.Builder eb =
          new Engine.Builder()
              .withStore(store)
              .withCapabilities(capabilities)
              .withEntrypoint(entrypoint);
      try {
        loadBundles(store);
        sharedEngine = eb.build();
        fileNames = extractFileNamesFromStore(store, entrypoint);
      } catch (RuntimeException e) {
        System.err.println("Error preparing engine: " + e.getMessage());
        return 1;
      }
    }

    final Object inputDoc;
    try {
      if (stdinInput) {
        inputDoc = objectMapper.readValue(System.in, Object.class);
      } else {
        inputDoc = objectMapper.readValue(this.input.toFile(), Object.class);
      }
    } catch (IOException e) {
      System.err.println("Error reading input: " + e.getMessage());
      return 1;
    }

    for (int i = 0; i < count; i++) {
      Metrics metrics = NoOpMetrics.Instance();
      StatementProfiler statementProfiler = new NoOpStatementProfiler();

      if (showMetrics || showProfile) { // profile assumes metrics
        metrics = new SimpleMetrics();
        allMetrics.add(metrics);
      }

      if (showProfile) {
        statementProfiler = new SimpleStatementProfiler();
        allStatementProfilers.add(statementProfiler);
      }

      final Engine engine;
      if (countIncludesLoad) {
        final Metrics.Timer capabilitiesTimer = metrics.timer("cli_capabilities_register");
        capabilitiesTimer.start();
        final Capabilities capabilities = BuiltinRegistry.generateCapabilities();
        capabilitiesTimer.stop();

        final Store store = new InMem();
        final Engine.Builder eb =
            new Engine.Builder()
                .withStore(store)
                .withCapabilities(capabilities)
                .withEntrypoint(entrypoint);

        final Metrics.Timer loadBundlesTimer = metrics.timer("cli_load_bundles");
        loadBundlesTimer.start();
        try {
          loadBundles(store);
        } catch (RuntimeException e) {
          System.err.println("Error loading bundles: " + e.getMessage());
          return 1;
        } finally {
          loadBundlesTimer.stop();
        }

        final Metrics.Timer engineBuildTimer = metrics.timer("cli_engine_build");
        engineBuildTimer.start();
        try {
          engine = eb.build();
        } catch (RuntimeException e) {
          System.err.println("Error building engine: " + e.getMessage());
          return 1;
        } finally {
          engineBuildTimer.stop();
        }

        if (i == 0) {
          fileNames = extractFileNamesFromStore(store, entrypoint);
        }
      } else {
        engine = sharedEngine;
      }

      Engine.PreparedQuery.Builder pqBuilder =
          engine
              .prepareForEvaluation()
              .withEntrypoint(entrypoint)
              .withMetrics(metrics)
              .withStatementProfiler(statementProfiler)
              .withPrintHook(PrintHook.of(System.err));

      if (explain && i == 0) {
        final BufferedQueryTracer tracer = new BufferedQueryTracer();
        allTracers.add(tracer);
        pqBuilder = pqBuilder.withTracer(tracer);
      }

      if (showProfile) {
        final DurationProfiler profiler = new DurationProfiler();
        allProfilers.add(profiler);
        pqBuilder = pqBuilder.withProfiler(profiler);
      }

      if (showCoverage) {
        final CoverageProfiler coverageProfiler = new CoverageProfiler();
        allCoverageProfilers.add(coverageProfiler);
        pqBuilder = pqBuilder.withProfiler(coverageProfiler);
      }

      final Metrics.Timer prepareQueryTimer = metrics.timer("cli_prepare_query");
      prepareQueryTimer.start();
      final Engine.PreparedQuery pq;
      try {
        pq = pqBuilder.build();
      } catch (RuntimeException e) {
        System.err.println("Error preparing query: " + e.getMessage());
        return 1;
      } finally {
        prepareQueryTimer.stop();
      }

      try {
        lastResults = pq.eval(inputDoc);
      } catch (RuntimeException e) {
        System.err.println("Error evaluating query: " + e.getMessage());
        return 1;
      }
    }

    if (lastResults != null) {
      try {
        final ObjectWriter writer =
            "pretty".equalsIgnoreCase(format)
                ? objectMapper.writerWithDefaultPrettyPrinter()
                : objectMapper.writer();
        System.out.println(writer.writeValueAsString(lastResults));
      } catch (IOException e) {
        System.err.println("Error serializing results: " + e.getMessage());
        return 1;
      }
    }

    if (showMetrics || showProfile) {
      new MetricsReporter().printMetricsTable(allMetrics);
    }

    if (explain) {
      new TraceReporter().printTraceOutput(allTracers, fileNames);
    }

    if (showProfile) {
      new ProfileReporter()
          .printProfileOutput(allProfilers, fileNames, profileLimit, profileSort);
      new StatementReporter().printStatementOutput(allStatementProfilers);
    }

    if (showCoverage) {
      new CoverageReporter().printCoverage(allCoverageProfilers, fileNames);
    }

    if (fail && (lastResults == null || lastResults.isEmpty())) {
      return 1;
    }
    if (failDefined && lastResults != null && !lastResults.isEmpty()) {
      return 1;
    }
    return 0;
  }

  private void loadBundles(Store store) {
    if (bundleFilePaths == null) {
      return;
    }
    for (final Path path : bundleFilePaths) {
      if (Files.isDirectory(path)) {
        new FileSystemBundleLoader(path.toString(), path).load(store);
      } else {
        final String name = path.getFileName().toString().toLowerCase();
        if (name.endsWith(".tar.gz") || name.endsWith(".tgz")) {
          new TarballBundleLoader(path.toString(), path).load(store);
        } else {
          throw new IllegalArgumentException(
              "Bundle path must be a directory or a .tar.gz/.tgz file: " + path);
        }
      }
    }
  }

  private String[] extractFileNamesFromStore(Store store, String entrypoint) {
    final Policy policy = store.getIrPolicyForEntrypoint(entrypoint);

    final List<StringConst> files = policy.getStatic().getFiles();

    if (files != null && !files.isEmpty()) {
      return files.stream().map(StringConst::getValue).toArray(String[]::new);
    }
    return new String[0];
  }
}
