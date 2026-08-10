# AGENTS.md

Guidance for AI coding agents (and humans) working in this repository.

## What this project is

`java-opa-sdk` is a Java SDK for evaluating [Open Policy Agent](https://www.openpolicyagent.org/)
(OPA) policies. Policies are compiled by the OPA CLI to the Intermediate
Representation (IR) "plan" format (`opa build -t plan`) and evaluated directly
in the JVM — no OPA server required.

**Parity with OPA is a core value.** Builtins and evaluation semantics must match
OPA's Go reference implementation (see `topdown/` in the OPA repo, and
`builtin_metadata.json`/`capabilities.json`). When implementing or fixing a
builtin, check the corresponding Go behavior for edge cases (key/JWK types,
error vs. false, strict-mode behavior, pre-hashing, etc.) rather than guessing.

## Repository layout

Gradle multi-module build. Modules (see `settings.gradle.kts`):

- `opa-evaluator` — core IR plan evaluator + lightweight `Engine` API
- `opa-builtins` — aggregator over the extended builtin sub-modules:
  `opa-builtins-{time,token,regex,semver,net,crypto,json}`
- `opa-jackson` / `opa-gson` — IR deserialization backends (ServiceLoader-discovered)
- `opa-services` — full OPA runtime with plugins (bundles, decision logs, status, discovery)
- `opa-slf4j` — SLF4J adapter for the SDK's `Logger` interface
- `cli` — command-line entry point (not published)

## Build, test, lint

Use the Gradle wrapper. Java **17** toolchain.

```bash
./gradlew build                 # full build (compile + checks + test)
./gradlew test                  # all tests (JUnit Platform)
./gradlew :opa-services:test    # one module
./gradlew :opa-services:test --tests "io.github.open_policy_agent.opa.plugins.StatusPluginTest"
./gradlew checkstyleMain pmdMain  # static analysis only
```

## Conventions

- **Checkstyle** (`config/checkstyle/checkstyle.xml`) and **PMD**
  (`config/pmd/ruleset.xml`) both run in the build and **fail it** on violation
  (`isIgnoreFailures = false`). Run them before pushing.
- **No wildcard imports** — `AvoidStarImport` is enforced; list imports
  explicitly. `UnusedImports`/`RedundantImport` are also enforced.
- Max line length is 200; use tabs-as-spaces per `FileTabCharacter`.
- Match the style, naming, and comment density of the surrounding code.
- Builtins carry `@OpaBuiltin`/`@OpaType` annotations and are registered in the
  module's `builtins()` map — keep the annotation metadata and registration in
  sync when adding one.

## Testing expectations

- Unit tests use JUnit 5 (`useJUnitPlatform()`).
- Builtin behavior is additionally covered by OPA compliance fixtures. A fixture
  whose builtin the SDK cannot resolve fails `ComplianceTest` unless that builtin
  is listed in
  `opa-evaluator/src/test/resources/compliance/known-missing-builtins.txt`, so an
  unimplemented builtin can no longer make its cases pass silently. The list is a
  ratchet: implementing a builtin (or registering its `BuiltinProvider`) means
  deleting its line in the same change, since the suite also fails on entries no
  fixture reports as missing.
- Add or update tests for the behavior you change; verify against OPA parity for
  builtins.

## Contributing

See [`CONTRIBUTING.md`](CONTRIBUTING.md) for the full contribution guidelines.
In particular, **all commits must be signed off** under the Developer Certificate
of Origin — use `git commit -s`. Follow [Conventional Commits](https://www.conventionalcommits.org/)
for commit subjects (e.g. `feat(token): ...`, `fix: ...`).
