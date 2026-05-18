# opa-services

Full OPA runtime with plugin support for bundle management, decision logging, status reporting, and service discovery.

## Overview

The services module provides the `Opa` class, which wraps the core evaluator with a complete plugin system. It handles automatic bundle downloading and activation, decision audit logging, status reporting, and dynamic configuration via discovery.

## Usage

### Configuration File

```java
import io.github.open_policy_agent.opa.Opa;

Opa opa = new Opa.Builder()
    .withConfigFile("opa-config.yaml")
    .withDefaultEntrypoint("example/allow")
    .build();

Opa.DecisionResult result = opa.makeDecision("{\"user\": \"alice\"}");

boolean allowed = result.getResult().asBoolean();
String decisionId = result.getId();
```

### Programmatic Configuration

```java
import io.github.open_policy_agent.opa.Opa;
import io.github.open_policy_agent.opa.config.Config;

Config config = new Config()
    .addService(new Config.ServiceConfig()
        .setName("local")
        .setUrl("file://"))
    .addBundle("authz", new Config.BundleConfig()
        .setService("local")
        .setResource("/path/to/bundle.tar.gz"))
    .setDecisionLogs(new Config.DecisionLogsConfig()
        .setConsole(true));

Opa opa = new Opa.Builder()
    .withConfig(config)
    .withDefaultEntrypoint("example/allow")
    .build();
```

### Minimal Configuration

For local evaluation without remote services:

```java
Config config = new Config()
    .addService(new Config.ServiceConfig()
        .setName("local")
        .setUrl("file://"))
    .addBundle("authz", new Config.BundleConfig()
        .setService("local")
        .setResource("/path/to/bundle.tar.gz"));

Opa opa = new Opa.Builder()
    .withConfig(config)
    .withDefaultEntrypoint("example/allow")
    .build();
```

### With Remote Services

```yaml
# opa-config.yaml
services:
  acmecorp:
    url: https://example.com/control-plane-api/v1
    credentials:
      bearer:
        token: "my-secret-token"

bundles:
  authz:
    service: acmecorp
    resource: /bundles/authz.tar.gz
    polling:
      min_delay_seconds: 60
      max_delay_seconds: 120

decision_logs:
  service: acmecorp

status:
  service: acmecorp
```

### Lifecycle Management

```java
Opa opa = new Opa.Builder()
    .withConfigFile("opa-config.yaml")
    .withDefaultEntrypoint("example/allow")
    .withWaitForPlugins(false) // don't block on build
    .build();

// Check readiness
while (!opa.ready()) {
    Thread.sleep(100);
}

// Use the instance...

// Clean shutdown
opa.close();
```

## Plugins

| Plugin | Description |
|--------|-------------|
| **ServicePlugin** | Manages HTTP clients for remote service communication |
| **BundlePlugin** | Downloads and activates OPA policy/data bundles |
| **DecisionLogPlugin** | Logs decisions to console or uploads to a remote service |
| **StatusPlugin** | Reports instance status (bundle revisions, plugin health, labels) |
| **DiscoveryPlugin** | Downloads configuration bundles for dynamic configuration updates |

### Custom Plugins

```java
import io.github.open_policy_agent.opa.plugins.Plugin;
import io.github.open_policy_agent.opa.plugins.PluginManager;

Plugin myPlugin = new Plugin() {
    @Override
    public Set<String> validate(PluginManager manager) {
        return Collections.emptySet();
    }

    @Override
    public Plugin initialize(PluginManager manager) {
        return this;
    }

    @Override
    public void start() {
        // Plugin startup logic
    }

    @Override
    public void stop() {
        // Cleanup
    }
};

Opa opa = new Opa.Builder()
    .withConfigFile("opa-config.yaml")
    .withDefaultEntrypoint("example/allow")
    .withPlugin("my-plugin", myPlugin)
    .build();
```

## Decision Options

For fine-grained control over individual decisions:

```java
Opa.DecisionOptions options = new Opa.DecisionOptions()
    .setInput(inputJson)
    .setPath("example/allow")
    .setDecisionID("custom-id")
    .showMetrics()
    .setProfiler(new Profiler());

Opa.DecisionResult result = opa.makeDecision(options);
```

## Hot Reload

The Opa runtime handles policy and data hot-reloading automatically. When the bundle plugin polls and detects an updated bundle, it activates the new bundle in the store and the engine's compiled policy is refreshed transparently. No application code changes are needed.

- **Data changes**: visible immediately on the next `makeDecision()` call (data is read live from the store on every evaluation).
- **Policy changes**: visible on the next `makeDecision()` call after the bundle plugin activates the new bundle. The plugin fires a `BundleActivationListener` callback that triggers `engine.refresh()`.

This matches upstream OPA behavior, where the Go SDK clears its query cache on bundle commit and reads data per-evaluation via store transactions.