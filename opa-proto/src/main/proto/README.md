# Vendored OPA protobuf schemas

These `.proto` files are copied verbatim from Open Policy Agent and describe the
wire format of proto-format plan bundles produced by `opa build --format=proto`.

| File | Upstream path |
|------|---------------|
| `v1/ir/plan.proto` | `v1/ir/plan.proto` |
| `v1/bundle/manifest.proto` | `v1/bundle/manifest.proto` |

## Source of truth

The OPA revision these schemas are vendored from is **not pinned here** — it is
the OPA version required by
[`tools/generate-compliance-tests/go.mod`](../../../../tools/generate-compliance-tests/go.mod),
which is the single place this SDK declares which OPA revision it targets.

At the time of writing that is `v1.20.1`. Proto plan bundles first shipped in
`v1.19.0`, the first tagged release containing `opa build --format=proto`
([open-policy-agent/opa#8825](https://github.com/open-policy-agent/opa/pull/8825),
schemas from [#8775](https://github.com/open-policy-agent/opa/pull/8775)).

## Updating

Do not edit these files by hand. Change the OPA version in `go.mod` and the
schemas re-vendor themselves:

- **Automatically on build.** Building the module (`./gradlew :opa-proto:build`,
  `:generateProto`, etc.) runs the `vendorProtoSchemas` task, which re-vendors
  from the go.mod-pinned OPA version. It is keyed on `go.mod`/`go.sum`, so it only
  does work when the pin changes and stays UP-TO-DATE (no network) otherwise. If
  the Go toolchain isn't installed the task is skipped and the committed schemas
  are used as-is.
- **On demand**, without a full build:

  ```sh
  ./gradlew :opa-proto:vendorProtoSchemas
  ```

Either way, regenerate the bindings and run the tests afterward:

```sh
./gradlew :opa-proto:generateProto :opa-proto:test
```

CI enforces this: the `verify-proto-vendor` job in `.github/workflows/pull-request.yml`
re-vendors from the go.mod-pinned version on every PR and fails if the committed
`.proto` files differ — so bumping the OPA version without re-vendoring cannot merge.

If a schema change adds a new IR statement kind, `PlanMapper`'s exhaustive
`switch` over the statement `oneof` will fail to compile until the new kind is
mapped — a deliberate guardrail.
