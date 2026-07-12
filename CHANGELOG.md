# Change Log

All notable changes to this project will be documented in this file. This
project adheres to [Semantic Versioning](http://semver.org/).

## Unreleased

### Breaking Changes

- Renamed the statement type enum from `Stmt.STMT_TYPE` to `Stmt.StmtType`.
- Removed duplicate per-statement `StmtType` string constants, such as `ArrayAppendStmt.StmtType`; statement type names now come from `Stmt.StmtType#getTypeName()`.
- Removed the unused `MakeObjectStmt#getStmtType()` helper.

### Changed

- Updated statement deserialization to use `Stmt.StmtType#getTypeName()` as the single source of statement type names.

## 0.2.0

This release adds a CLI for testing policies, evaluation profiling with a
pretty printer, and mutual TLS (mTLS) support for the service clients,
alongside dependency updates.

### Runtime, SDK, Tooling

- CLI for testing ([#44](https://github.com/open-policy-agent/java-opa-sdk/pull/44)) authored by @kroekle
- Profiling/pretty printer ([#43](https://github.com/open-policy-agent/java-opa-sdk/pull/43)) authored by @johanfylling
- Add mTLS support ([#18](https://github.com/open-policy-agent/java-opa-sdk/pull/18)) authored by @sspaink

### Docs, Website, Ecosystem

- Add a link to the release artifacts in the README ([#32](https://github.com/open-policy-agent/java-opa-sdk/pull/32)) authored by @sspaink

### Miscellaneous

- prepare v0.2.0 release ([#48](https://github.com/open-policy-agent/java-opa-sdk/pull/48)) authored by @sspaink
- Dependency updates; notably:
    - Bump com.nimbusds:nimbus-jose-jwt from 10.5 to 10.9 ([#36](https://github.com/open-policy-agent/java-opa-sdk/pull/36))
    - Bump com.nimbusds:nimbus-jose-jwt from 10.9 to 10.9.1 ([#41](https://github.com/open-policy-agent/java-opa-sdk/pull/41))
    - Bump com.fasterxml.jackson.core:jackson-databind from 2.17.0 to 2.21.3 ([#38](https://github.com/open-policy-agent/java-opa-sdk/pull/38))
    - Bump org.assertj:assertj-core from 3.27.6 to 3.27.7 ([#37](https://github.com/open-policy-agent/java-opa-sdk/pull/37))
    - Bump org.json:json from 20250517 to 20260522 ([#42](https://github.com/open-policy-agent/java-opa-sdk/pull/42))
    - Bump com.github.seancfoley:ipaddress from 5.5.1 to 5.6.2 ([#46](https://github.com/open-policy-agent/java-opa-sdk/pull/46))
    - Bump gradle/actions from 4 to 6 ([#33](https://github.com/open-policy-agent/java-opa-sdk/pull/33))
    - Bump actions/setup-java from 4 to 5 ([#35](https://github.com/open-policy-agent/java-opa-sdk/pull/35))
    - Bump actions/checkout from 4 to 6 ([#34](https://github.com/open-policy-agent/java-opa-sdk/pull/34))

## 0.1.0

Initial release of the Java OPA SDK: a pure-Java evaluator for OPA's
intermediate representation (IR) with pluggable builtins, JSON
(de)serialization decoupled from the core, coverage reporting, and a Maven
Central publishing pipeline.

### Runtime, SDK, Tooling

- Coverage reporting ([#19](https://github.com/open-policy-agent/java-opa-sdk/pull/19)) authored by @johanfylling
- Refactor that decouples opa-evaluator from Jackson ([#21](https://github.com/open-policy-agent/java-opa-sdk/pull/21)) authored by @sspaink
- Remove Jackson annotations from IR statements ([#20](https://github.com/open-policy-agent/java-opa-sdk/pull/20)) authored by @sspaink
- Move builtins to the opa-builtins folder ([#12](https://github.com/open-policy-agent/java-opa-sdk/pull/12)) authored by @sspaink

### Build and CI

- Add release workflow ([#16](https://github.com/open-policy-agent/java-opa-sdk/pull/16)) authored by @sspaink
- Fix Maven Central publish: pin Jackson deps via jackson-bom ([#22](https://github.com/open-policy-agent/java-opa-sdk/pull/22)) authored by @sspaink
- Run `mvn validate` ([#31](https://github.com/open-policy-agent/java-opa-sdk/pull/31)) authored by @sspaink
- Add Checkstyle and PMD linter checks to the PR workflow ([#14](https://github.com/open-policy-agent/java-opa-sdk/pull/14)) authored by @sspaink
- Run unit tests in PR checks ([#13](https://github.com/open-policy-agent/java-opa-sdk/pull/13)) authored by @sspaink
- Add dependabot config ([#10](https://github.com/open-policy-agent/java-opa-sdk/pull/10)) authored by @sspaink
- Add a .gitignore file ([#1](https://github.com/open-policy-agent/java-opa-sdk/pull/1)) authored by @sspaink

### Miscellaneous

- Dependency updates; notably:
    - Bump org.slf4j:slf4j-api from 2.0.16 to 2.0.18 ([#30](https://github.com/open-policy-agent/java-opa-sdk/pull/30))
    - Bump org.mockito:mockito-core from 5.16.1 to 5.23.0 ([#24](https://github.com/open-policy-agent/java-opa-sdk/pull/24))
    - Bump org.apache.logging.log4j:log4j-slf4j2-impl from 2.24.3 to 2.26.0 ([#29](https://github.com/open-policy-agent/java-opa-sdk/pull/29))
    - Bump actions/setup-java from 4 to 5 ([#25](https://github.com/open-policy-agent/java-opa-sdk/pull/25))
    - Bump actions/checkout from 4 to 6 ([#26](https://github.com/open-policy-agent/java-opa-sdk/pull/26))
    - Bump gradle/actions from 4 to 6 ([#27](https://github.com/open-policy-agent/java-opa-sdk/pull/27))

### New Contributors

- @sspaink made their first contribution in [#1](https://github.com/open-policy-agent/java-opa-sdk/pull/1)
- @johanfylling made their first contribution in [#19](https://github.com/open-policy-agent/java-opa-sdk/pull/19)
- @dependabot made their first contribution in [#25](https://github.com/open-policy-agent/java-opa-sdk/pull/25)
