# opa-services

Full OPA runtime with plugin support for bundle management, decision logging, status reporting, and service discovery.

## Overview

The services module provides the `Opa` class, which wraps the core evaluator with a complete plugin system. It handles automatic bundle downloading and activation, decision audit logging, status reporting, and dynamic configuration via discovery.

## Usage

### Configuration File

```java
import io.github.openpolicyagent.opa.Opa;

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
import io.github.openpolicyagent.opa.Opa;
import io.github.openpolicyagent.opa.config.Config;

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

### TLS and mTLS

Services support two related TLS blocks, mirroring Go-OPA:

- `services.<name>.tls` — trust roots used to verify the server certificate.
- `services.<name>.credentials.client_tls` — client certificate and key presented during the TLS handshake (mTLS).

Both apply to all HTTP traffic for the service: bundle downloads, decision-log uploads, status reports, and discovery.

```yaml
services:
  acmecorp:
    url: https://policy.example.com
    tls:
      ca_cert: /etc/ssl/corp-ca.pem
      system_ca_required: true
    credentials:
      client_tls:
        cert: /etc/ssl/client.pem
        private_key: /etc/ssl/client-key.pem
        private_key_passphrase: "key-passphrase"
        cert_reread_interval_seconds: 3600
```

| Field | Description |
|-------|-------------|
| `tls.ca_cert` | PEM file containing one or more trust roots for verifying the server. |
| `tls.system_ca_required` | When `true`, the JVM's default trust store is also trusted in addition to `ca_cert`. |
| `credentials.client_tls.cert` | PEM file containing the client certificate (and any intermediates). |
| `credentials.client_tls.private_key` | PKCS#8 PEM file with the client private key. |
| `credentials.client_tls.private_key_passphrase` | Passphrase for an encrypted PKCS#8 key. Omit for unencrypted keys. |
| `credentials.client_tls.cert_reread_interval_seconds` | If set, the cert and key are reloaded from disk on this interval to support runtime rotation. |

Only PKCS#8 PEM private keys are accepted. Convert PKCS#1 keys with:

```sh
openssl pkcs8 -topk8 -nocrypt -in key.pem -out key-pkcs8.pem
```

Programmatic equivalent:

```java
Config config = new Config()
    .addService(new Config.ServiceConfig()
        .setName("acmecorp")
        .setUrl("https://policy.example.com")
        .setTls(new Config.TlsConfig()
            .setCaCert("/etc/ssl/corp-ca.pem")
            .setSystemCaRequired(true))
        .setCredentials(new Config.CredentialsConfig()
            .setClientTls(new Config.ClientTlsConfig()
                .setCert("/etc/ssl/client.pem")
                .setPrivateKey("/etc/ssl/client-key.pem")
                .setPrivateKeyPassphrase("key-passphrase")
                .setCertRereadIntervalSeconds(3600))));
```

For keystores that cannot be expressed as files (HSM-backed keys, secret-manager-driven rotation, custom `KeyManager` chains), supply a fully constructed `SSLContext` directly. When set, file-based TLS fields are rejected during validation:

```java
SSLContext sslContext = buildSslContextFromHsm();

Config.ServiceConfig service = new Config.ServiceConfig()
    .setName("acmecorp")
    .setUrl("https://policy.example.com")
    .setSslContext(sslContext);
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
import io.github.openpolicyagent.opa.plugins.Plugin;
import io.github.openpolicyagent.opa.plugins.PluginManager;

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