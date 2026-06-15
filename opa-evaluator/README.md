# opa-evaluator

Core OPA plan evaluator that executes compiled OPA Intermediate Representation (IR) policies in Java.

## Overview

The evaluator module provides the `Engine` class for direct policy evaluation. It loads compiled OPA bundles, parses the IR plan, and evaluates queries against input data. This is the lowest-level API and has minimal dependencies.

## Usage

```java
import io.github.open_policy_agent.opa.rego.Engine;
import io.github.open_policy_agent.opa.bundle.FileSystemBundleLoader;
import java.util.List;
import java.util.Map;

Engine engine = new Engine.Builder()
    .withBundleLoader(new FileSystemBundleLoader("authz", Path.of("policy")))
    .withEntrypoint("example/allow")
    .build();

Engine.PreparedQuery query = engine.prepareForEvaluation().build();

Map<String, Object> input = Map.of("user", "alice");
List<Object> results = query.eval(input);
```

### POJO Input/Output

The Engine supports typed input and output:

```java
List<MyResult> results = engine.prepareForEvaluation()
    .build()
    .eval(myPojoInput, MyResult.class);
```

### Multiple Queries

```java
Map<String, Object> input = Map.of("user", "alice");

// Default query
Engine.PreparedQuery allowQuery = engine.prepareForEvaluation().build();
List<Object> allowResults = allowQuery.eval(input);

// Override with a different query
Engine.PreparedQuery denyQuery = engine.prepareForEvaluation()
    .withEntrypoint("example/deny")
    .build();
List<Object> denyResults = denyQuery.eval(input);
```

### Metrics and Profiling

```java
import io.github.open_policy_agent.opa.metrics.Metrics;
import io.github.open_policy_agent.opa.tracing.Profiler;

Metrics metrics = new SimpleMetrics();
Profiler profiler = new Profiler();

List<Object> results = engine.prepareForEvaluation()
    .withMetrics(metrics)
    .withProfiler(profiler)
    .build()
    .eval(input);
```

### Custom Builtins

```java
import io.github.open_policy_agent.opa.ast.types.*;

Engine engine = new Engine.Builder()
    .withBundleLoader(new FileSystemBundleLoader("authz", Path.of("/policy")))
    .withEntrypoint("example/allow")
    .withBuiltin("custom.reverse", (ctx, args) -> {
        String s = ((RegoString) args[0]).getValue();
        return new RegoString(new StringBuilder(s).reverse().toString());
    })
    .build();
```

## Extension Points

### BuiltinProvider SPI

Implement `BuiltinProvider` to contribute builtins via ServiceLoader:

```java
public class MyBuiltinProvider implements BuiltinProvider {
    @Override
    public Map<String, BiFunction<EvaluationContext, RegoValue[], RegoValue>> builtins() {
        return Map.of(
            "custom.myfunction", (ctx, args) -> new RegoString("result")
        );
    }
}
```

Register in `META-INF/services/io.github.open_policy_agent.opa.ast.builtin.BuiltinProvider`.

### PolicyReader SPI

The `PolicyReader` interface allows alternative IR deserialization implementations. The default Jackson-based implementation is provided by the `opa-jackson` module and discovered via ServiceLoader.

### Custom Store

Implement the `Store` interface for alternative policy/data storage backends. The default `InMem` store keeps everything in memory.

## Hot Reload

The Engine supports hot-reloading of policies and data, following the same semantics as upstream OPA:

- **Data** is read live from the store on every evaluation. Changes to data in the store are visible immediately without any action.
- **Policy** is compiled into an internal evaluator at build time. Policy changes in the store require calling `engine.refresh()` to take effect.
- **PreparedQuery** captures the policy at preparation time. After `engine.refresh()`, existing PreparedQuery instances still use the old policy (data remains live). Call `engine.prepareForEvaluation().build()` to create a new PreparedQuery with the updated policy.

```java
// After loading a new bundle into the store:
engine.refresh();  // picks up new policy from store

// Direct evaluate uses the new policy immediately
List<Object> results = engine.evaluate(ctx, input);

// Existing PreparedQuery still uses old policy -- re-prepare to pick up changes
Engine.PreparedQuery freshPq = engine.prepareForEvaluation().build();
```

When using the `Opa` runtime (services module), `refresh()` is called automatically on bundle activation.