# Contributing Code

Thanks for your interest in contributing to the Java OPA SDK! This document
outlines the important guidelines for getting started as a contributor.

When contributing, please consider the following pointers:

- **Testing:** Almost all code changes should be accompanied by tests. All CI
  checks must pass, including the full Gradle build with no Checkstyle or PMD
  violations (`./gradlew build`).
- **Commits:** All code must be yours to contribute, and commits must be signed
  off (see [Commit Messages](#commit-messages) below). Related commits should be
  squashed before merge (this can be done in the PR UI on GitHub).
- **OPA parity:** Builtins and evaluation semantics must match OPA's Go
  reference implementation (see `topdown/` in the [OPA repo](https://github.com/open-policy-agent/opa),
  and `builtin_metadata.json` / `capabilities.json`). When implementing or
  fixing a builtin, verify the corresponding Go behavior for edge cases (key/JWK
  types, error vs. `false`, strict-mode behavior, pre-hashing, etc.) rather than
  guessing. The compliance-test harness skips a case only when the builtin it
  calls is listed in
  `opa-evaluator/src/test/resources/compliance/known-missing-builtins.txt`, so
  implementing a builtin means deleting its line there in the same change.
  Behavior the upstream fixtures do not cover still needs its own test — and
  consider contributing the missing case to OPA, so every implementation is held
  to it.
- **Public APIs:** This SDK is meant to be embedded, so keep the public surface
  minimal. Prefer package-private types and methods; only make something `public`
  when consumers genuinely need it. A published API is a long-term commitment.
- **Dependencies:** Avoid adding third-party dependencies. The SDK is designed to
  be minimal, lightweight, and easily embedded. New dependencies come with a cost
  for both maintainers and users (conflicts, security surface, debugging).
- **AI tooling:** You may use generative AI tooling to assist your work, but
  please review the [AI Guidelines](#ai-guidelines) below first.

## Development setup

The project is a Gradle multi-module build targeting **Java 17**. Use the
included wrapper:

```bash
./gradlew build                 # compile + Checkstyle + PMD + tests
./gradlew test                  # run all tests
./gradlew :opa-services:test    # test a single module
./gradlew checkstyleMain pmdMain  # static analysis only
```

Checkstyle (`config/checkstyle/checkstyle.xml`) and PMD (`config/pmd/ruleset.xml`)
run as part of the build and **fail it** on any violation. Notable rules: no
wildcard imports (`AvoidStarImport`), no unused imports, and a 200-character line
limit. Match the style, naming, and comment density of the surrounding code.

## Commit Messages

Commit messages should explain _why_ the change was made. This project follows
[Conventional Commits](https://www.conventionalcommits.org/); the subject line
should look like:

```
<type>(<optional scope>): description in ~50 characters or less
```

where `<type>` is one of `feat`, `fix`, `docs`, `chore`, `ci`, `test`, `refactor`,
etc. For example:

```txt
feat(token): implement io.jwt.verify_eddsa builtin

More detail on what changed and why. Keep body lines under ~72 characters.

Fixes: #145
Signed-off-by: Random J Developer <random@developer.example.org>
```

If your change relates to an open issue, include `Fixes #<ISSUE_NUMBER>` at the
end of the message.

### Developer Certificate of Origin

This project requires that contributors sign off on changes they submit. The
[Developer Certificate of Origin (DCO)](https://developercertificate.org/) is a
simple way to certify that you wrote or have the right to submit the code you are
contributing. It is a standard requirement for Linux Foundation and CNCF
projects.

You sign off by adding the following line to your commit messages:

```
Signed-off-by: Random J Developer <random@developer.example.org>
```

Git's `-s` option adds this automatically:

```sh
git commit -s -m 'This is my commit message'
```

Please review the [text of the DCO](https://developercertificate.org).

> **Note:** If you use AI or machine-learning tools to help author a patch, you
> must ensure the code you produce is compliant with the DCO and this project's
> Apache-2.0 license. All commits in your patch _must_ be signed off by a human
> author. Maintainers reserve the right to request additional information about a
> patch and to reject PRs where code origin cannot be verified. See the
> [AI Guidelines](#ai-guidelines).

## Code Review

Before a Pull Request is merged, it will undergo review from project maintainers.
To streamline review, when amending your PR in response to feedback, do not
squash your changes into the original commits until the PR has been approved for
merge — this lets the reviewer see only what changed. When adding temporary
fixup commits, consider a subject like `Fixup into <commit> (squash before merge)`
so the intent is clear. Squash into a clean history before the final merge.

If your PR is small, it is acceptable to squash during review. Use your judgement,
and ask on the PR if you aren't sure.

## AI Guidelines

Contributions are encouraged, including those assisted by AI tooling. To help
maintainers help you effectively, please follow these guidelines:

1. Follow the
   [Linux Foundation Generative AI Guidelines](https://www.linuxfoundation.org/legal/generative-ai),
   which in summary require you to ensure:
   - the AI tool's terms don't impose contractual limitations that conflict with
     this project's license; and
   - if generated output contains third-party copyrighted material, you confirm
     proper permissions exist and provide license information.
2. Respect maintainer time by:
   - Opening an issue with a clear proposal before starting work not already
     described in an existing issue.
   - Starting with small pull requests scoped to a single issue (one at a time
     for new contributors).
   - Never using LLM output to respond to maintainer comments in PRs or issues.
     Reviewers want to understand **your** reasoning about the code you submitted.
     Even if an LLM helped you write it, it's yours to own and explain.
3. Don't be afraid to get it wrong! Ask for clarification on a review comment,
   ask for input on an approach before investing time, and correct maintainers
   when you think they've misunderstood something.

## Contribution process

Small bug fixes and improvements can be submitted directly via a
[Pull Request](https://github.com/open-policy-agent/java-opa-sdk/pulls).

Before submitting large changes, please open an
[issue](https://github.com/open-policy-agent/java-opa-sdk/issues) outlining:

- The use case your change addresses.
- Steps to reproduce the issue, if applicable.
- A detailed description of what your change entails.
- Alternative approaches you considered, if applicable.

Use your judgement about what constitutes a large change. If you aren't sure,
open an issue and ask.
