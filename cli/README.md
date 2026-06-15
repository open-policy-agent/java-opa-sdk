# cli (`regoj`)

> ## ⚠️ WARNING
> **This CLI is for testing and benchmarking only.** It is not a supported
> distribution artifact and is not published. Do not embed it in production
> tooling or shell scripts that ship to users. Use the OPA CLI
> (https://www.openpolicyagent.org/docs/cli) for production workflows.

`regoj` is a small command-line driver for the Java OPA SDK. It loads a
pre-compiled rego plan bundle, evaluates an entrypoint against an input
document, and (optionally) prints metrics, traces, and per-statement /
per-location profiling tables.

## Building & running

The module is wired into the standard Gradle multi-module build:

```bash
./gradlew :cli:build
./gradlew :cli:run --args="eval --help"
```

To produce a runnable distribution:

```bash
./gradlew :cli:installDist
./cli/build/install/regoj/bin/regoj eval --help
```

## `eval` subcommand

```
regoj eval [OPTIONS] [ENTRYPOINT]
```

Loads one or more bundles, prepares the entrypoint, evaluates it against an
input document, and prints the JSON result. The `ENTRYPOINT` positional
argument is the entrypoint name as it appears in the compiled plan (for
example, `authz/allow`); it can also be provided via `-e`/`--entrypoint`.

### Options

| Flag | Description |
| ---- | ----------- |
| `-b`, `--bundle <path>` | **Required** (except with `--capabilities-current`). Bundle to load. Either a `.tar.gz`/`.tgz` produced by `opa build -t plan ...` or an unpacked directory containing `plan.json` (and optional `data.json`, `*.rego`). May be repeated. |
| `-e`, `--entrypoint <name>` | Entrypoint name. Overrides the positional `ENTRYPOINT` if both are given. |
| `-i`, `--input <path>` | Path to the JSON input document. Required unless `-I` is used. |
| `-I`, `--stdin-input` | Read the input document from stdin instead of `-i`. |
| `-f`, `--format <fmt>` | Output format: `json` (default, single line) or `pretty` (indented). Any other value exits 2. |
| `--capabilities-current` | Print the capabilities JSON for the currently registered builtins and exit. When set, `-b`, `-i`/`-I`, and `ENTRYPOINT` are not required. |
| `--metrics` | Print a metrics table after evaluation (parse / build / prepare / eval timings). |
| `--instrument` | Alias for `--metrics`. |
| `--profile` | Print per-location and per-statement timing tables. Implies `--metrics`. |
| `--profile-limit <n>` | Cap the profile table to the top `n` rows (default `10`). |
| `--profile-sort <key>` | Profiler sort key: `total_time` (default), `num_eval`, or `location`. Any other value exits 2. |
| `--coverage` | Print a per-file table of executed source lines after evaluation. |
| `--explain` | Print a step-by-step trace of statements entered and exited during evaluation. With `--count > 1`, only the first run's trace is printed. |
| `--fail` | Exit with a non-zero status if the result is undefined / empty. |
| `--fail-defined` | Exit with a non-zero status if the result is defined / non-empty. |
| `--count <n>` | Repeat the prepare + evaluate loop `n` times. With `--metrics` / `--profile`, the report switches from a single-run table to a min / max / mean / p90 / p99 table across the runs. By default, bundle load and engine build happen once before the loop; only prepare + eval are repeated. Useful for rough benchmarking. |
| `--count-includes-load` | Include bundle load and engine build inside each `--count` iteration. Restores the legacy behavior where every repetition re-loads bundles and rebuilds the engine. |

`print()` output from policies is forwarded to stderr, line-by-line.

### Examples

The test resources include a small unpacked plan bundle in
`ir_simple_dir/`, plus a sample `input.json`. All examples are run from the
repository root.

Evaluate against the directory bundle:

```bash
./gradlew :cli:run --args="\
  eval \
  -b cli/src/test/resources/ir_simple_dir \
  -i cli/src/test/resources/input.json \
  authz/allow"
```

Output:

```
[{"result":true}]
```

Pretty-print the result:

```bash
./gradlew :cli:run --args="\
  eval \
  -b cli/src/test/resources/ir_simple_dir \
  -i cli/src/test/resources/input.json \
  --format pretty \
  authz/allow"
```

To run against a tarball, build one from the directory bundle (or compile your
own with `opa build -t plan -o bundle.tar.gz <rego-source-dirs>`) and pass that
path:

```bash
tar -C cli/src/test/resources/ir_simple_dir -czf /tmp/ir_simple.tar.gz .
./gradlew :cli:run --args="\
  eval -b /tmp/ir_simple.tar.gz -i cli/src/test/resources/input.json authz/allow"
```

Read input from stdin:

```bash
echo '{"user":{"id":"alicex","groups":["super"]}}' | ./gradlew --quiet :cli:run --args="\
  eval -b cli/src/test/resources/ir_simple_dir -I authz/allow"
```

Use the result for shell exit-code gating (e.g., a deny-by-default check):

```bash
./gradlew :cli:run --args="\
  eval -b cli/src/test/resources/ir_simple_dir -i cli/src/test/resources/input.json \
  --fail-defined authz/allow"
```

Coverage report:

```bash
./gradlew :cli:run --args="\
  eval -b cli/src/test/resources/ir_simple_dir -i cli/src/test/resources/input.json \
  --coverage authz/allow"
```

Profile (top-3 rows by total time, repeated 5 times):

```bash
./gradlew :cli:run --args="\
  eval -b cli/src/test/resources/ir_simple_dir -i cli/src/test/resources/input.json \
  --profile --profile-limit 3 --count 5 authz/allow"
```

Sample metrics output:

```
[{"result":true}]
+---------------------------+-----------+-----------+-----------+-----------+-----------+
| METRIC                    | MIN       | MAX       | MEAN      | 90%       | 99%       |
+---------------------------+-----------+-----------+-----------+-----------+-----------+
| cli_capabilities_register | 672.750µs | 211.377ms | 70.994ms  | 211.377ms | 211.377ms |
| cli_engine_build          | 46.833µs  | 2.121ms   | 740.458µs | 2.121ms   | 2.121ms   |
| ...                       |           |           |           |           |           |
+---------------------------+-----------+-----------+-----------+-----------+-----------+
```

### Bundle format

The SDK loads `.tar.gz` plan bundles or unpacked directories (with
`plan.json` and optional `data.json`/`*.rego` siblings). Compile a Rego
project to a plan bundle with the standard OPA CLI:

```bash
opa build -t plan -o bundle.tar.gz <rego-source-dirs>
```

### Contributions
Contributions toward feature parity with OPA's `eval` are welcome,
but keep the warning at the top of this file in mind: the goal is a tool for
local testing and benchmarking, not a user-facing CLI.
