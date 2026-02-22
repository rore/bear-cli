# bear check

## Purpose

Run deterministic enforcement for one block or all indexed blocks: drift, static boundary checks, and project test gate.

## Invocation forms

```text
bear check <ir-file> --project <path> [--strict-hygiene]
bear check --all --project <repoRoot> [--blocks <path>] [--only <csv>] [--fail-fast] [--strict-orphans] [--strict-hygiene]
```

## Inputs and flags

- Single mode uses `<ir-file>` and `--project`.
- `--all` mode uses index-driven orchestration from `bear.blocks.yaml` by default.
- `--blocks` overrides index path.
- `--only` restricts block set.
- `--fail-fast` stops block execution after first failure.
- `--strict-orphans` enables strict marker-orphan checks.
- `--strict-hygiene` enables opt-in unexpected-path hygiene checks (`.g`, `.gradle-user`) with exact-path allowlist support.
- Optional policy files:
  - `.bear/policy/reflection-allowlist.txt`
  - `.bear/policy/hygiene-allowlist.txt`
- Generated logic wrappers expose a sanctioned default wiring factory: `Wrapper.of(<ports...>)`.
  - Prefer `Wrapper.of(...)` in user production wiring.
  - Keep constructor `(ports..., Logic)` for tests/advanced injection.

## Output schema and ordering guarantees

Single mode deterministic order:

1. baseline manifest diagnostics (if any)
2. boundary signal lines
3. drift lines
4. containment lines (if any)
5. undeclared-reach lines (if any)
6. boundary-bypass lines (if any)
7. test failure or timeout output (if reached)
8. failure footer on non-zero

Key line formats:

- `drift: ADDED|REMOVED|CHANGED: <relative/path>`
- `drift: MISSING_BASELINE: build/generated/bear (...)`
- `check: UNDECLARED_REACH: <relative/path>: <surface>`
- `check: BOUNDARY_BYPASS: RULE=<rule>: <relative/path>: <detail>`
- `check: HYGIENE_UNEXPECTED_PATHS: <relative/path>`

`BOUNDARY_BYPASS` seam coverage for governed logic includes:
- direct governed impl usage in Java source (`src/main/**`)
- classloading reflection APIs in Java source (`Class.forName`, `loadClass`) unless allowlisted
- governed logic-to-governed-impl binding through:
  - `src/main/resources/META-INF/services/**`
  - `src/main/java/module-info.java` (`provides ... with ...`)
- null port wiring and effect-port bypass checks on governed wrappers/impls

For lock/bootstrap test-runner failures, detail lines append deterministic diagnostics:

- `attempts=<csv>`
- `CACHE_MODE=<isolated|user-cache|external-env>`
- `FALLBACK=<none|to_user_cache>`

Diagnostics are emitted in detail text only (footer contract is unchanged).

Troubleshooting guardrail:
- do not patch `build.gradle` manually as first response to lock/bootstrap failures; first use BEAR retry/fallback flow and BEAR-owned generated wiring (`build/generated/bear/gradle/bear-containment.gradle` where applicable).

`--all` mode renders deterministic block sections and summary fields.

## Exit codes emitted

- `0` pass
- `2` validation/config failure (`IR_VALIDATION`, `POLICY_INVALID`, `MANIFEST_INVALID`)
- `3` drift failure
- `4` project test failure or timeout
- `6` boundary policy failure (`UNDECLARED_REACH`, `BOUNDARY_BYPASS`, or `HYGIENE_UNEXPECTED_PATHS`)
- `64` usage failure
- `70` internal failure
- `74` IO failure

## Deterministic failure footer

Non-zero exits append:

- `CODE=<enum>`
- `PATH=<locator>`
- `REMEDIATION=<step>`

For aggregated `--all` non-zero failures, footer code is `REPO_MULTI_BLOCK_FAILED`.

## Remediation pointers

- [troubleshooting.md#drift_detected-or-drift_missing_baseline](troubleshooting.md#drift_detected-or-drift_missing_baseline)
- [troubleshooting.md#undeclared_reach](troubleshooting.md#undeclared_reach)
- [troubleshooting.md#boundary_bypass](troubleshooting.md#boundary_bypass)
- [troubleshooting.md#test_failure-test_timeout-or-invariant_violation](troubleshooting.md#test_failure-test_timeout-or-invariant_violation)
- [troubleshooting.md#io_error](troubleshooting.md#io_error)

## Related

- [commands-compile.md](commands-compile.md)
- [commands-unblock.md](commands-unblock.md)
- [commands-pr-check.md](commands-pr-check.md)
- [exit-codes.md](exit-codes.md)
- [output-format.md](output-format.md)
- [troubleshooting.md](troubleshooting.md)

