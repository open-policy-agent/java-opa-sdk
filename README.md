# Java OPA SDK

[![Maven Central](https://img.shields.io/maven-central/v/io.github.open-policy-agent/opa-services.svg?label=Maven%20Central)](https://central.sonatype.com/namespace/io.github.open-policy-agent)
[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)

A Java SDK for evaluating [Open Policy Agent](https://www.openpolicyagent.org/) (OPA) policies using the Intermediate Representation (IR) format.

## Features

- **Direct Policy Evaluation**: Lightweight API for evaluating OPA policies without the overhead of a full OPA server
- **IR Format Support**: Uses OPA's compiled Intermediate Representation for fast evaluation
- **Comprehensive Builtin Support**: 139+ standard OPA builtins implemented
- **High Performance**: `PreparedQuery` optimization for hot-path evaluations
- **Metrics & Tracing**: Built-in support for performance monitoring and debugging
- **Flexible APIs**: Choose between the lightweight Engine API or the full Opa runtime with plugin support

## Project Structure

| Module | Description | Javadoc |
|--------|-------------|---------|
| **[opa-evaluator](opa-evaluator/)** | Core OPA plan evaluator and Engine API for direct policy evaluation | [javadoc.io](https://javadoc.io/doc/io.github.open-policy-agent/opa-evaluator) |
| **[opa-builtins](opa-builtins/)** | Aggregator that brings in all extended builtin sub-modules | [javadoc.io](https://javadoc.io/doc/io.github.open-policy-agent/opa-builtins) |
| **[opa-jackson](opa-jackson/)** | Jackson-based IR deserialization (auto-discovered via ServiceLoader) | [javadoc.io](https://javadoc.io/doc/io.github.open-policy-agent/opa-jackson) |
| **[opa-gson](opa-gson/)** | Gson-based IR deserialization (alternative to opa-jackson) | [javadoc.io](https://javadoc.io/doc/io.github.open-policy-agent/opa-gson) |
| **[opa-services](opa-services/)** | Full OPA runtime with plugin support (bundles, decision logs, status, discovery) | [javadoc.io](https://javadoc.io/doc/io.github.open-policy-agent/opa-services) |
| **[opa-slf4j](opa-slf4j/)** | SLF4J adapter for the SDK's `Logger` interface | [javadoc.io](https://javadoc.io/doc/io.github.open-policy-agent/opa-slf4j) |

## Building IR Bundles

OPA policies must be compiled to the Intermediate Representation (IR) format before use with this SDK:

```bash
# Install OPA CLI
# macOS
brew install opa

# Compile a single policy file
opa build -t plan -e 'example/allow' example.rego

# Compile a directory of policies
opa build -t plan -e 'example/allow' -b ./policies
```

Key parameters:
- `-t plan`: Target format (use `plan` for IR generation)
- `-e`: Entrypoint (repeatable) for the policy (the query you want to evaluate)
- `-b`: Bundle directory containing `.rego` files and optional `data.json`

The resulting `bundle.tar.gz` file contains the compiled IR plan and any static data.

A pre-built example bundle is included at [`examples/bundle.tar.gz`](examples/), compiled from the policy and data in that directory. The example policy grants access if the user is `"admin"`, or if the action is `"read"` and the user is in the authorized readers list (defined in `data.json`).

## Installation

Artifacts are published to [Maven Central under `io.github.open-policy-agent`](https://central.sonatype.com/namespace/io.github.open-policy-agent).

Most applications should depend on the `opa-services` module, which transitively includes `opa-evaluator` and `opa-jackson`. Add `opa-builtins` if your policies use extended builtins (crypto, JWT, networking, etc.):

**Gradle**

```kotlin
implementation("io.github.open-policy-agent:opa-services:0.1.0")
runtimeOnly("io.github.open-policy-agent:opa-builtins:0.1.0")
```

**Maven**

```xml
<dependency>
    <groupId>io.github.open-policy-agent</groupId>
    <artifactId>opa-services</artifactId>
    <version>0.1.0</version>
</dependency>
<dependency>
    <groupId>io.github.open-policy-agent</groupId>
    <artifactId>opa-builtins</artifactId>
    <version>0.1.0</version>
    <scope>runtime</scope>
</dependency>
```

For lightweight evaluation without the plugin runtime, depend on `opa-evaluator` directly (you'll also need `opa-jackson` at runtime and `opa-builtins` if your policies use extended builtins):

**Gradle**

```kotlin
implementation("io.github.open-policy-agent:opa-evaluator:0.1.0")
runtimeOnly("io.github.open-policy-agent:opa-jackson:0.1.0")
runtimeOnly("io.github.open-policy-agent:opa-builtins:0.1.0")
```

**Maven**

```xml
<dependency>
    <groupId>io.github.open-policy-agent</groupId>
    <artifactId>opa-evaluator</artifactId>
    <version>0.1.0</version>
</dependency>
<dependency>
    <groupId>io.github.open-policy-agent</groupId>
    <artifactId>opa-jackson</artifactId>
    <version>0.1.0</version>
    <scope>runtime</scope>
</dependency>
<dependency>
    <groupId>io.github.open-policy-agent</groupId>
    <artifactId>opa-builtins</artifactId>
    <version>0.1.0</version>
    <scope>runtime</scope>
</dependency>
```

### Optional modules

- **`opa-gson`** — drop-in alternative to `opa-jackson` for projects already using Gson. Swap `opa-jackson` for `opa-gson` in the snippets above.
- **`opa-slf4j`** — routes the SDK's `Logger` interface through SLF4J. Add as a runtime dependency alongside your SLF4J binding of choice.

## Quick Start

### Engine API (Lightweight)

The Engine API provides direct policy evaluation without plugin infrastructure. Best for libraries, embedded evaluation, and cases where you manage bundles yourself.  
(when using FileSystemBundleLoader, the IR plan.json is expected to be in the given path)

```java
import io.github.open_policy_agent.opa.rego.Engine;
import io.github.open_policy_agent.opa.bundle.FileSystemBundleLoader;
import java.util.List;
import java.util.Map;

// Build the engine with the example policy bundle
Engine engine = new Engine.Builder()
    .withBundleLoader(new FileSystemBundleLoader("authz", Path.of("examples/policy")))
    .withEntrypoint("example/allow")
    .build();

// Prepare for evaluation (pre-computes lookups for better performance)
Engine.PreparedQuery query = engine.prepareForEvaluation().build();

// Evaluate with input - alice is an authorized reader
Map<String, Object> input = Map.of("user", "alice", "action", "read");
List<Object> results = query.eval(input);

@SuppressWarnings("unchecked")
boolean allowed = (Boolean) ((Map<String, Object>) results.get(0)).get("result"); // true
```

### Opa API (Full Runtime)
The Opa API provides a complete OPA runtime with plugin support. Best for production applications that need automatic bundle management, decision logging, and status reporting.
(bundle.tar.gz is expected to have the IR plan included)


#### Opa API with Programmatic Config

```java
import io.github.open_policy_agent.opa.Opa;
import io.github.open_policy_agent.opa.config.Config;

Config config = new Config()
    .addService(new Config.ServiceConfig()
        .setName("local")
        .setUrl("file://"))
    .addBundle("example", new Config.BundleConfig()
        .setService("local")
        .setResource("/path/to/bundle.tar.gz"))
    .setDecisionLogs(new Config.DecisionLogsConfig()
        .setConsole(true));

Opa opa = new Opa.Builder()
    .withConfig(config)
    .withDefaultEntrypoint("example/allow")
    .build();

// alice is an authorized reader
Opa.DecisionResult decision = opa.makeDecision("{\"user\": \"alice\", \"action\": \"read\"}");
boolean allowed = decision.getResult().asBoolean(); // true
```

#### Opa API with YAML Config


```java
import io.github.open_policy_agent.opa.Opa;

// Initialize with a YAML configuration file
Opa opa = new Opa.Builder()
    .withConfigFile("opa-config.yaml")
    .withDefaultEntrypoint("example/allow")
    .build();

// Evaluate a policy decision
Opa.DecisionResult decision = opa.makeDecision("{\"user\": \"bob\"}");
boolean allowed = decision.getResult().asBoolean();
```

Example `opa-config.yaml`:

```yaml
services:
  local:
    url: "file://"

bundles:
  authz:
    service: local
    resource: /path/to/bundle.tar.gz

decision_logs:
  console: true
```

## Configuration

The Opa API supports configuration through YAML or JSON files. This implementation closely follows the [OPA configuration specification](https://www.openpolicyagent.org/docs/latest/configuration/).

### Complete Configuration Example

```yaml
# Services define remote endpoints for bundles, decision logs, and status
services:
  acmecorp:
    url: https://example.com/control-plane-api/v1
    credentials:
      bearer:
        token: "my-secret-token"
    response_header_timeout_seconds: 10
    allow_insecure_tls: false

# Bundles configure policy and data distribution
bundles:
  authz:
    service: acmecorp
    resource: /bundles/http/example/authz.tar.gz
    polling:
      min_delay_seconds: 60
      max_delay_seconds: 120

# Decision logs configure audit logging
decision_logs:
  service: acmecorp
  console: false
  reporting:
    min_delay_seconds: 300
    max_delay_seconds: 600

# Status configures status reporting
status:
  service: acmecorp
  console: false

# Discovery enables dynamic configuration updates
discovery:
  name: discovery
  service: acmecorp
  resource: /bundles/discovery.tar.gz

# Labels attach metadata to the OPA instance
labels:
  app: myapp
  environment: production

# Default decision path
default_decision: system/main

# Non-deterministic builtin cache
nd_builtin_cache: true
```

### Configuration Reference

#### Services

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `url` | string | - | Base URL of the service |
| `credentials.bearer.token` | string | - | Bearer token for authentication |
| `credentials.client_tls.cert` | path | - | PEM file path with the client certificate (mTLS) |
| `credentials.client_tls.private_key` | path | - | PKCS#8 PEM file path with the client private key |
| `credentials.client_tls.private_key_passphrase` | string | - | (Reserved — encrypted PEM keys not supported. Use a JKS / PKCS#12 keystore instead.) |
| `credentials.client_tls.cert_reread_interval_seconds` | int | - | Interval to reload the client cert/key from disk for rotation |
| `credentials.client_tls.keystore.path` | path | - | JKS / PKCS#12 keystore path holding the client cert + key |
| `credentials.client_tls.keystore.password` | string | - | Keystore password |
| `credentials.client_tls.keystore.key_password` | string | (defaults to `password`) | Password for the private key entry |
| `credentials.client_tls.keystore.type` | string | inferred from extension, else `PKCS12` | Keystore type (`JKS`, `PKCS12`) |
| `tls.ca_cert` | path | - | PEM file path with trust roots used to verify the server cert |
| `tls.system_ca_required` | boolean | false | Also trust the JVM default trust store in addition to `ca_cert` / `truststore` |
| `tls.truststore.path` | path | - | JKS / PKCS#12 truststore path used to verify the server cert |
| `tls.truststore.password` | string | - | Truststore password |
| `tls.truststore.type` | string | inferred from extension, else `PKCS12` | Truststore type (`JKS`, `PKCS12`) |
| `response_header_timeout_seconds` | int | 10 | HTTP response header timeout |
| `allow_insecure_tls` | boolean | false | Allow insecure TLS (dev only) |

Any string value may reference an environment variable with `${VAR}`; the SDK substitutes it during config load (matches Go-OPA). Use `\${VAR}` to keep a literal `${VAR}` in the file.

See [opa-services/README.md](opa-services/README.md#tls-and-mtls) for a full mTLS walkthrough, including JKS / PKCS#12 keystores and the programmatic `setSslContext` escape hatch for HSM-backed or rotated keys.

#### Bundles

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `service` | string | - | Name of the service to use |
| `resource` | string | - | Resource path for the bundle |
| `polling.min_delay_seconds` | int | 60 | Minimum delay between polls |
| `polling.max_delay_seconds` | int | 120 | Maximum delay between polls |

#### Decision Logs

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `service` | string | - | Service for log uploads |
| `console` | boolean | false | Enable console logging |
| `resource` | string | `/logs` | Resource path for uploads |
| `mask_decision` | string | `system/log/mask` | Policy path for masking |
| `drop_decision` | string | `system/log/drop` | Policy path for filtering |

#### Status

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `service` | string | - | Service for status reports |
| `console` | boolean | false | Enable console output |

#### Discovery

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `name` | string | `discovery` | Plugin name |
| `service` | string | - | Service to use |
| `resource` | string | - | Resource path for discovery bundle |
| `polling.min_delay_seconds` | int | 60 | Minimum delay between polls |
| `polling.max_delay_seconds` | int | 120 | Maximum delay between polls |

### Configuration Loading

```java
// From YAML/JSON file
new Opa.Builder().withConfigFile("opa-config.yaml")

// From a Reader
new Opa.Builder().withConfig(new StringReader(yamlConfig))

// From a Config object
new Opa.Builder().withConfig(config)
```

## API Comparison

| Feature | Engine API | Opa API |
|---------|-----------|---------|
| Use Case | Direct policy evaluation | Full OPA runtime |
| Complexity | Minimal | Full-featured |
| Plugin Support | No | Yes |
| Configuration | Code-based | YAML/JSON or code |
| Custom Builtins | Yes | Yes |
| Metrics | Yes | Yes |
| Bundle Management | Manual | Automatic |
| Decision Logging | No | Automatic |

### Engine API Builder Options

| Method | Description |
|--------|-------------|
| `withBundleLoader(BundleLoader)` | Load a compiled policy bundle (can be called multiple times) |
| `withStore(Store)` | Custom data store (default: in-memory) |
| `withEntrypoint(String)` | Default entrypoint to evaluate (e.g., `"example/allow"`) |
| `withCapabilities(Capabilities)` | Restrict available builtins |
| `withBuiltin(String, BiFunction)` | Register a custom builtin function |

### PreparedQuery Builder Options

| Method | Description |
|--------|-------------|
| `withEntrypoint(String)` | Override the default entrypoint |
| `withMetrics(Metrics)` | Enable performance metrics collection |
| `withProfiler(Profiler)` | Enable query profiling |
| `withTracer(QueryTracer)` | Enable query execution tracing |
| `withPrintHook(PrintHook)` | Capture output from `print()` statements (default: logger) |

### Opa API Builder Options

| Method | Description |
|--------|-------------|
| `withConfigFile(String)` | Load configuration from a YAML or JSON file |
| `withConfig(Config)` | Provide a Config object directly |
| `withConfig(Reader)` | Load configuration from a Reader |
| `withDefaultEntrypoint(String)` | Default entrypoint path for `makeDecision()` calls |
| `withId(String)` | Instance ID for decision logs/status (default: UUID) |
| `withLogger(Logger)` | Custom logger (default: stdout) |
| `withPlugin(String, Plugin)` | Register a custom plugin |
| `withWaitForPlugins(boolean)` | Block until all plugins are ready (default: true) |

## Performance Optimization

For hot-path evaluations, use `PreparedQuery` to pre-compute expensive operations:

```java
// Prepare once
Engine.PreparedQuery query = engine.prepareForEvaluation()
    .withMetrics(metrics)
    .build();

// Evaluate many times
for (Object input : inputs) {
    List<Object> results = query.eval(input);
}
```

`PreparedQuery` optimizes by pre-looking up the plan, pre-computing frame capacity, and pre-building function lookup maps.

## Hot Reload

Both the Engine and Opa APIs support hot-reloading of policies and data, following the same semantics as upstream OPA:

| What changed | Engine API | Opa API | PreparedQuery |
|---|---|---|---|
| **Data** | Visible immediately (read live from store on each eval) | Visible immediately | Visible immediately |
| **Policy** | Call `engine.refresh()` | Automatic (on bundle activation) | Stale -- call `engine.prepareForEvaluation().build()` to re-prepare |

### Opa API (automatic)

The Opa runtime handles hot reload automatically. When the bundle plugin polls and activates a new bundle, the engine's policy is refreshed transparently. No application code changes are needed:

```java
Opa opa = new Opa.Builder()
    .withConfigFile("opa-config.yaml")
    .withDefaultEntrypoint("example/allow")
    .build();

// Policy updates are picked up automatically on the next makeDecision() call
// after the bundle plugin activates a new bundle.
Opa.DecisionResult result = opa.makeDecision(input);
```

### Engine API (manual)

When using the Engine directly, call `refresh()` after updating the store with new policies:

```java
// Initial setup
Engine engine = new Engine.Builder()
    .withStore(store)
    .withEntrypoint("example/allow")
    .build();

// ... later, after loading a new bundle into the store ...
engine.refresh();

// Next evaluation uses the new policy; data is already live
List<Object> results = engine.evaluate(ctx, input);
```

### PreparedQuery behavior

A `PreparedQuery` captures the compiled policy at preparation time. After `engine.refresh()`, existing PreparedQuery instances continue using the old policy (data remains live). To evaluate with the updated policy, create a new PreparedQuery:

```java
// Old PreparedQuery still uses old policy (data is live)
Engine.PreparedQuery oldPq = engine.prepareForEvaluation().build();

engine.refresh();

// Re-prepare to get the new policy
Engine.PreparedQuery newPq = engine.prepareForEvaluation().build();
```

## Custom Builtins

Register custom builtin functions to extend policy capabilities:

```java
import io.github.open_policy_agent.opa.ast.types.*;

Engine engine = new Engine.Builder()
    .withBundleLoader(new FileSystemBundleLoader("authz", Path.of("/policy")))
    .withEntrypoint("example/allow")
    .withBuiltin("custom.hash", (ctx, args) -> {
        RegoString input = (RegoString) args[0];
        String hash = computeHash(input.getValue());
        return new RegoString(hash);
    })
    .build();
```

Then reference it in your Rego policy:

```rego
package example

allow if {
    hash := custom.hash(input.password)
    hash == data.expected_hash
}
```

## Print Debugging

OPA's `print()` builtin is supported for debugging policy evaluation. By default, print output is written via the `Logger` interface. Configure a custom `PrintHook` to redirect output:

```java
import io.github.open_policy_agent.opa.rego.PrintHook;
import io.github.open_policy_agent.opa.logging.Logger;

// Use a Logger instance
Logger myLogger = new Logger.StandardLogger();
Engine.PreparedQuery query = engine.prepareForEvaluation()
    .withPrintHook(PrintHook.logger(myLogger))
    .build();

// Or use a custom hook directly
Engine.PreparedQuery query = engine.prepareForEvaluation()
    .withPrintHook(msg -> System.out.println("opa: " + msg))
    .build();
```

In your Rego policy, use `print()` to output values during evaluation:

```rego
package example

allow if {
    print("evaluating user:", input.user, "action:", input.action)
    input.user == "admin"
}
```

When evaluated, this prints: `evaluating user: alice action: read`

## Error Handling

All SDK exceptions extend `OpaException` and support contextual information via `.withContext()`:

```java
import io.github.open_policy_agent.opa.OpaException;
import io.github.open_policy_agent.opa.ir.PolicyNotFoundException;
import io.github.open_policy_agent.opa.ir.EvaluationException;

try {
    List<Object> results = query.eval(input);
} catch (PolicyNotFoundException e) {
    System.err.println("Policy not found: " + e.getMessage());
} catch (EvaluationException e) {
    System.err.println("Evaluation error: " + e.getMessage());
} catch (OpaException e) {
    System.err.println("OPA error: " + e.getMessage());
}
```

## Supported Builtins

This SDK implements 139+ of OPA's ~180 builtin functions:

| Category | Coverage | Status |
|----------|----------|--------|
| Numbers | 12/12 | 100% |
| Aggregates | 6/6 | 100% |
| Arrays | 3/3 | 100% |
| Sets | 5/5 | 100% |
| Objects | 7/7 | 100% |
| Strings | 24/25 | 96% |
| Regex | 8/8 | 100% |
| Types | 8/8 | 100% |
| Comparison | 6/6 | 100% |
| JSON | 9/9 | 100% |
| Time | 10/10 | 100% |
| JWT | 16/16 | 100% |
| Cryptography | 8/14 | 57% |
| Encoding | 10/19 | 53% |
| Net | 7/7 | 100% |
| Semantic Versions | 2/2 | 100% |
| Conversions | 1/3 | 33% |

For the full detailed list, see the [builtins README](opa-builtins/README.md).

## Releasing

Releases are automated via GitHub Actions. Pushing a tag triggers the [Publish Release](.github/workflows/post-tag.yaml) workflow which runs tests, publishes all modules to Maven Central, and creates a GitHub Release.

### Creating a release

1. Update the version in `gradle.properties`
2. Commit and push to `main`
3. Tag and push:
   ```bash
   git tag v0.1.0
   git push origin v0.1.0
   ```

The workflow will publish these artifacts to Maven Central:
- `io.github.open-policy-agent:opa-evaluator`
- `io.github.open-policy-agent:opa-jackson`
- `io.github.open-policy-agent:opa-gson`
- `io.github.open-policy-agent:opa-services`
- `io.github.open-policy-agent:opa-builtins`
- `io.github.open-policy-agent:opa-builtins-time`
- `io.github.open-policy-agent:opa-builtins-token`
- `io.github.open-policy-agent:opa-builtins-regex`
- `io.github.open-policy-agent:opa-builtins-semver`
- `io.github.open-policy-agent:opa-builtins-net`
- `io.github.open-policy-agent:opa-builtins-crypto`
- `io.github.open-policy-agent:opa-builtins-json`
- `io.github.open-policy-agent:opa-slf4j`

## License

Apache License 2.0
