# opa-builtins

Aggregator module that brings in all extended OPA builtin sub-modules for the java-opa-sdk.

## Overview

The opa-builtins module is a convenience aggregator — it has no source code of its own but transitively includes all builtin sub-modules. Depend on this module to get all extended builtins, or depend on individual sub-modules to control which builtins (and their dependencies) are included.

## Sub-modules

| Module | Provider | Functions | Examples |
|--------|----------|-----------|---------|
| **[opa-builtins-time](../opa-builtins-time/)** | TimeBuiltins | Time/date operations | `time.now_ns`, `time.parse_rfc3339_ns`, `time.date`, `time.diff` |
| **[opa-builtins-token](../opa-builtins-token/)** | TokenBuiltins | JWT encode/decode/verify | `io.jwt.decode`, `io.jwt.encode_sign`, `io.jwt.verify_rs256`, etc. |
| **[opa-builtins-regex](../opa-builtins-regex/)** | RegexBuiltins | Pattern matching | `regex.match`, `regex.split`, `regex.find_n`, `regex.globs_match` |
| **[opa-builtins-semver](../opa-builtins-semver/)** | SemverBuiltins | Semantic versioning | `semver.compare`, `semver.is_valid` |
| **[opa-builtins-net](../opa-builtins-net/)** | CidrBuiltins | Network/CIDR operations | `net.cidr_contains`, `net.cidr_intersects`, `net.cidr_merge` |
| **[opa-builtins-crypto](../opa-builtins-crypto/)** | CryptoBuiltins | Hashing and HMAC | `crypto.md5`, `crypto.sha256`, `crypto.hmac.sha256`, `crypto.hmac.equal` |
| **[opa-builtins-json](../opa-builtins-json/)** | JsonBuiltins | JSON operations | `json.filter`, `json.remove`, `json.patch`, `json.match_schema` |

Note: String builtins (`contains`, `concat`, `split`, `sprintf`, `trim`, etc.) are core builtins included directly in **opa-evaluator**.

## Detailed Builtin Support

| Builtin | Supported |
|---------|-----------|
| **Numbers** | |
| `abs`, `ceil`, `floor`, `round` | Yes |
| `numbers.range`, `numbers.range_step` | Yes |
| `+`, `-`, `*`, `/`, `%` | Yes |
| `rand.intn` | Yes |
| **Aggregates** | |
| `count`, `sum`, `product`, `max`, `min`, `sort` | Yes |
| **Arrays** | |
| `array.concat`, `array.reverse`, `array.slice` | Yes |
| **Sets** | |
| `&` (intersection), `\|` (union), `-` (difference) | Yes |
| `intersection`, `union` | Yes |
| **Objects** | |
| `object.filter`, `object.get`, `object.remove` | Yes |
| `object.union`, `object.keys`, `object.subset`, `object.union_n` | Yes |
| **Strings** (in opa-evaluator) | |
| `concat`, `contains`, `endswith`, `startswith` | Yes |
| `format_int`, `indexof`, `indexof_n` | Yes |
| `lower`, `upper`, `replace`, `split`, `sprintf` | Yes |
| `substring`, `trim`, `trim_left`, `trim_right`, `trim_space` | Yes |
| `trim_prefix`, `trim_suffix` | Yes |
| `strings.any_prefix_match`, `strings.any_suffix_match` | Yes |
| `strings.count`, `strings.replace_n`, `strings.reverse` | Yes |
| `strings.render_template` | No |
| **Regex** (opa-builtins-regex) | |
| `regex.is_valid`, `regex.match`, `regex.split` | Yes |
| `regex.find_n`, `regex.find_all_string_submatch_n` | Yes |
| `regex.globs_match`, `regex.template_match`, `regex.replace` | Yes |
| **Glob** | |
| `glob.match`, `glob.quote_meta` | No |
| **Types** | |
| `is_array`, `is_boolean`, `is_null`, `is_number` | Yes |
| `is_object`, `is_set`, `is_string`, `type_name` | Yes |
| **Encoding** | |
| `base64.decode`, `base64.encode`, `base64.is_valid` | Yes |
| `base64url.decode`, `base64url.encode`, `base64url.encode_no_pad` | Yes |
| `hex.decode`, `hex.encode` | Yes |
| `yaml.marshal`, `yaml.unmarshal` | Yes |
| `urlquery.*` | No |
| **JSON** (opa-builtins-json) | |
| `json.is_valid`, `json.unmarshal`, `json.marshal` | Yes |
| `json.marshal_with_options` | Yes |
| `json.filter`, `json.remove`, `json.patch` | Yes |
| `json.match_schema`, `json.verify_schema` | Yes |
| **JWT** (opa-builtins-token) | |
| `io.jwt.encode_sign`, `io.jwt.encode_sign_raw` | Yes |
| `io.jwt.decode`, `io.jwt.decode_verify` | Yes |
| `io.jwt.verify_rs256/384/512` | Yes |
| `io.jwt.verify_ps256/384/512` | Yes |
| `io.jwt.verify_es256/384/512` | Yes |
| `io.jwt.verify_hs256/384/512` | Yes |
| **Time** (opa-builtins-time) | |
| `time.now_ns`, `time.parse_duration_ns`, `time.parse_ns` | Yes |
| `time.parse_rfc3339_ns`, `time.format` | Yes |
| `time.clock`, `time.date`, `time.diff`, `time.weekday` | Yes |
| `time.add_date` | Yes |
| **Cryptography** (opa-builtins-crypto) | |
| `crypto.md5`, `crypto.sha1`, `crypto.sha256` | Yes |
| `crypto.hmac.md5/sha1/sha256/sha512` | Yes |
| `crypto.hmac.equal` | Yes |
| `crypto.x509.*`, `crypto.parse_private_keys` | No |
| **Net** (opa-builtins-net) | |
| `net.cidr_contains`, `net.cidr_contains_matches` | Yes |
| `net.cidr_intersects`, `net.cidr_expand`, `net.cidr_merge` | Yes |
| `net.cidr_is_valid`, `net.lookup_ip_addr` | Yes |
| **Semantic Versions** (opa-builtins-semver) | |
| `semver.compare`, `semver.is_valid` | Yes |
| **Comparison** | |
| `equal`, `neq`, `lt`, `lte`, `gt`, `gte` | Yes |
| **Not Yet Implemented** | |
| `bits.*`, `graph.*`, `units.*`, `http.send` | No |
| `uuid.*`, `graphql.*`, `rego.*`, `providers.*` | No |

## Adding Custom Builtins

Implement the `BuiltinProvider` interface and register via ServiceLoader:

```java
import io.github.open_policy_agent.opa.ast.builtin.BuiltinProvider;
import io.github.open_policy_agent.opa.rego.EvaluationContext;
import io.github.open_policy_agent.opa.ast.types.*;
import java.util.Map;
import java.util.function.BiFunction;

public class MyBuiltinProvider implements BuiltinProvider {
    @Override
    public Map<String, BiFunction<EvaluationContext, RegoValue[], RegoValue>> builtins() {
        return Map.of(
            "custom.greet", (ctx, args) -> {
                String name = ((RegoString) args[0]).getValue();
                return new RegoString("Hello, " + name);
            }
        );
    }
}
```

Register in `META-INF/services/io.github.open_policy_agent.opa.ast.builtin.BuiltinProvider`:

```
com.example.MyBuiltinProvider
```
