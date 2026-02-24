# bear check

## Purpose

Run deterministic enforcement for one block or all indexed blocks: drift, static boundary checks, and project test gate.

Completion pairing note:
- `check --all` is the local integrity gate.
- for completion evidence, pair it with `bear pr-check --all --project <repoRoot> --base <ref>`.

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

Optional policy files:
- `.bear/policy/reflection-allowlist.txt`
- `.bear/policy/hygiene-allowlist.txt`

Generated logic wrappers expose a sanctioned default wiring factory: `Wrapper.of(<ports...>)`.
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
- wiring-specific drift uses canonical repo-relative paths:
  - `drift: CHANGED: build/generated/bear/wiring/<blockKey>.wiring.json`
  - `drift: MISSING_BASELINE: build/generated/bear/wiring/<blockKey>.wiring.json`
- for wiring files, `check` emits one deterministic line per `(reason, path)` (no duplicated `wiring/...` and `build/generated/bear/wiring/...` variants).
- `check: UNDECLARED_REACH: <relative/path>: <surface>`
- `check: BOUNDARY_BYPASS: RULE=<rule>: <relative/path>: <detail>`
- `check: HYGIENE_UNEXPECTED_PATHS: <relative/path>`
- informational skip signal (selection does not include allowedDeps blocks, but containment-required set is non-empty):
  - `check: INFO: CONTAINMENT_SURFACES_SKIPPED_FOR_SELECTION: projectRoot=<root>: reason=no_selected_blocks_with_impl_allowedDeps`

`BOUNDARY_BYPASS` seam coverage for governed logic includes:
- direct governed impl usage in Java source (`src/main/**`)
- classloading reflection APIs in Java source (`Class.forName`, `loadClass`) unless allowlisted
- governed logic-to-governed-impl binding through:
  - `src/main/resources/META-INF/services/**`
  - `src/main/java/module-info.java` (`provides ... with ...`)
- null port wiring and effect-port bypass checks on governed wrappers/impls
- placeholder impl stubs (`RULE=IMPL_PLACEHOLDER`)
- governed impl containment boundary violations (`RULE=IMPL_CONTAINMENT_BYPASS`)
- generated `com.bear.generated.*Port` implementations outside owning governed roots (`RULE=PORT_IMPL_OUTSIDE_GOVERNED_ROOT`)
- generated `com.bear.generated.*Port` multi-block implementers without valid `_shared` marker (`RULE=MULTI_BLOCK_PORT_IMPL_FORBIDDEN`)

Rationale:
- generated port implementations are boundary authority; this rule preserves block isolation by preventing mega-adapters from collapsing multiple generated block packages into one class.

Containment scanner contract (always-on):
- scope: governed impl files from wiring manifests only
- scan scope: `execute(...)` method body only
- call shapes inspected:
  - `Type.method(...)`
  - `new Type(...).method(...)`
- target resolution:
  - token containing `.` -> FQCN
  - else explicit import (`import a.b.Type;`)
  - else same-package fallback (`package p;` -> `p.Type`)
  - unresolved target -> no failure
- source-path lookup for resolved FQCN `a.b.C`:
  - only `src/main/java/a/b/C.java`
  - no lookup in `build/**`, `build/generated/**`, `src/test/**`
  - missing file -> unresolved (no failure)
- allow namespace:
  - `java.*`, `javax.*` auto-allowed
  - `jakarta.*`, `com.sun.*` are not auto-allowed
- final allow/deny is path-based only:
  - resolved source path under any manifest `governedSourceRoots` entry -> allowed
  - otherwise -> `IMPL_CONTAINMENT_BYPASS`
- findings are emitted in deterministic order: `path`, then `rule`, then `detail`.

Wiring manifest requirement:
- containment consumes v2 wiring manifests only.
- required containment fields include `blockRootSourceDir` and `governedSourceRoots`.
- `governedSourceRoots` ordering is deterministic in this slice:
  - first: `blockRootSourceDir`
  - mandatory second: `src/main/java/blocks/_shared` (reserved governed root)
- stale/non-v2/malformed wiring manifests fail deterministically with `CODE=MANIFEST_INVALID` (exit `2`); rerun `bear compile`.

For lock/bootstrap test-runner failures, detail lines append deterministic diagnostics:

- `attempts=<csv>`
- `CACHE_MODE=<isolated|user-cache|external-env>`
- `FALLBACK=<none|to_user_cache>`

Troubleshooting guardrail:
- do not patch `build.gradle` manually as first response to lock/bootstrap failures; first use BEAR retry/fallback flow and BEAR-owned generated wiring (`build/generated/bear/gradle/bear-containment.gradle` where applicable).

`--all` mode renders deterministic block sections and summary fields.
- pass sections may include contextual `DETAIL: ...` lines when non-failure informational signals apply.

## Exit codes emitted

- `0` pass
- `2` validation/config failure (`IR_VALIDATION`, `POLICY_INVALID`, `MANIFEST_INVALID`)
- `3` drift failure
- `4` project test failure or timeout
- `6` reach/hygiene policy failure (`UNDECLARED_REACH` or `HYGIENE_UNEXPECTED_PATHS`)
- `7` structural bypass policy failure (`BOUNDARY_BYPASS`)
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
