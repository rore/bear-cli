# bear check

## Purpose

Run deterministic enforcement for one block or all indexed blocks: drift, static boundary checks, and project test gate.

## Quick use

Canonical invocation:

```text
bear check --all --project <repoRoot>
```

Success looks like:
- all selected blocks pass and summary `EXIT_CODE: 0`

Main failure classes:
- validation/config (`exit 2`)
- drift (`exit 3`)
- test failure/timeout (`exit 4`)
- reach/hygiene (`exit 6`)
- boundary bypass (`exit 7`)
- usage/internal/IO (`64/70/74`)

Completion pairing note:
- `check --all` is the local integrity gate.
- for completion evidence, pair it with `bear pr-check --all --project <repoRoot> --base <ref>`.

## Invocation forms

```text
bear check <ir-file> --project <path> [--strict-hygiene] [--index <path>] [--collect=all] [--agent]
bear check --all --project <repoRoot> [--blocks <path>] [--only <csv>] [--fail-fast] [--strict-orphans] [--strict-hygiene] [--collect=all] [--agent]
```

If `bear.blocks.yaml` is missing for `check --all`, failure envelope is deterministic (exit `2`):

```text
index: VALIDATION_ERROR: INDEX_REQUIRED_MISSING: bear.blocks.yaml: project=.
CODE=INDEX_REQUIRED_MISSING
PATH=bear.blocks.yaml
REMEDIATION=Create bear.blocks.yaml or run non---all command
```

## Inputs and flags

- Single mode uses `<ir-file>` and `--project`.
- `--index <path>` is an optional override for single mode.
- for `kind=block`, single mode resolves index path as: explicit `--index` if provided, else `<project>/bear.blocks.yaml`; then validates tuple membership by normalized `(ir, projectRoot)`.
- `--all` mode uses index-driven orchestration from `bear.blocks.yaml` by default.
- `--blocks` overrides index path.
- `--only` restricts block set.
- `--fail-fast` stops block execution after first failure.
- `--strict-orphans` enables strict marker-orphan checks.
- `--strict-hygiene` enables opt-in unexpected-path hygiene checks (`.g`, `.gradle-user`) with exact-path allowlist support.
- `--collect=all` collects additional findings within reached enforcement lanes (default remains first/fail-fast behavior).
- `--agent` emits JSON-only diagnostics to stdout (`schemaVersion=bear.nextAction.v1`) with deterministic `problems`, `clusters`, and `nextAction`.

Optional policy files:
- `.bear/policy/reflection-allowlist.txt`
- `.bear/policy/hygiene-allowlist.txt`

Generated logic wrappers expose a sanctioned default wiring factory: `Wrapper.of(<ports...>)`.
- Prefer `Wrapper.of(...)` in user production wiring.
- Keep constructor `(ports..., Logic)` for tests/advanced injection.

## Output schema and ordering guarantees

`check --all` emits deterministic progress lines on `stdout` (as an ordered subsequence; other output may interleave):

```text
check-all: START project=.
check-all: BLOCK_START name=<block> ir=<irPath>
check-all: ROOT_TEST_START project=<projectRoot>
check-all: HEARTBEAT seconds=<n> phase=root_test project=<projectRoot>
check-all: ROOT_TEST_DONE project=<projectRoot> exit=<code>
check-all: DONE project=. exit=<code>
```

Heartbeat semantics:
- monotonic clock only
- first heartbeat at `30` seconds, then `60`, `90`, ...
- `seconds=<n>` is deterministic threshold value for that cadence point

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
- `check: UNDECLARED_REACH: <relative/path>: REACH_HYGIENE: KIND=REFLECTION_DISPATCH token=<token>`
- `check: BOUNDARY_BYPASS: RULE=<rule>: <relative/path>: <detail>`
- `check: HYGIENE_UNEXPECTED_PATHS: <relative/path>`
- `BEAR_STRUCTURAL_SIGNAL|blockKey=<blockKey>|test=<Direction|Reach>|kind=<KIND>|detail=<detail>`
- informational skip signal (selection does not include allowedDeps blocks, but containment-required set is non-empty):
  - `check: INFO: CONTAINMENT_SURFACES_SKIPPED_FOR_SELECTION: projectRoot=<root>: reason=no_selected_blocks_with_impl_allowedDeps`

Containment verification semantics:
- containment verification runs per `projectRoot` when any is true:
  - selected block set includes at least one block with `impl.allowedDeps`, or
  - `spec/_shared.policy.yaml` exists, or
  - `src/main/java/blocks/_shared/**` contains at least one `.java` source file.
- in skip mode (`considerContainmentSurfaces=false`), containment index/marker state does not fail the command.
- when containment verification is active:
  - in `check --all`, containment preflight, project tests, and marker verification run once per `projectRoot` (result fanout to blocks in that root).
  - `check` auto-injects the generated containment init script into the project test run:
    - `--no-daemon -I build/generated/bear/gradle/bear-containment.gradle test`
  - preflight for generated containment artifacts runs before tests.
  - marker/hash verification runs only after project tests exit `0`.
  - generated containment artifacts (`build/generated/bear/config/containment-required.json`, `build/generated/bear/gradle/bear-containment.gradle`) fail in drift lane (`exit 3`) with compile remediation.
  - handshake markers fail in containment-not-verified lane (`exit 74`):
    - aggregate marker: `build/bear/containment/applied.marker` must match both required hash and canonical `blocks=` CSV.
    - per-block markers: `build/bear/containment/<blockKey>.applied.marker` must exist and match `block=<blockKey>` + required hash.
  - `_shared` policy is path-scoped:
    - file: `spec/_shared.policy.yaml`
    - if missing while `_shared` sources are in scope, `_shared` uses JDK-only default allowlist (`allowedDeps=[]`)
  - `_shared` containment compile violations are surfaced in containment lane (`exit 74`) with remediation to:
    - add pinned dependency to `spec/_shared.policy.yaml`, or
    - remove external dependency usage from `src/main/java/blocks/_shared/**`.

`BOUNDARY_BYPASS` seam coverage for governed logic includes:
- direct governed impl usage in Java source (`src/main/**`)
- classloading reflection APIs in Java source (`Class.forName`, `loadClass`) unless allowlisted
- reflection/method-handle dynamic dispatch in source-owned governed roots (`CODE=REFLECTION_DISPATCH_FORBIDDEN`)
- governed logic-to-governed-impl binding through:
  - `src/main/resources/META-INF/services/**`
  - `src/main/java/module-info.java` (`provides ... with ...`)
- null port wiring and effect-port bypass checks on governed wrappers/impls
- placeholder impl stubs (`RULE=IMPL_PLACEHOLDER`)
- governed impl containment boundary violations (`RULE=IMPL_CONTAINMENT_BYPASS`)
- generated `com.bear.generated.*Port` implementations outside owning governed roots (`RULE=PORT_IMPL_OUTSIDE_GOVERNED_ROOT`)
- block-port interface implementations under `src/main/java/**` (`RULE=BLOCK_PORT_IMPL_INVALID`)
- direct cross-block target internals/wrapper references outside app lane (`RULE=BLOCK_PORT_REFERENCE_FORBIDDEN`)
- app-lane direct execute of inbound block-port target wrappers (`RULE=BLOCK_PORT_INBOUND_EXECUTE_FORBIDDEN`)
- generated `com.bear.generated.*Port` multi-block implementers without valid `_shared` marker (`RULE=MULTI_BLOCK_PORT_IMPL_FORBIDDEN`)

Block-port lane contract:
- app wiring lane is path-pinned to `src/main/java/com/**`.
- generated block-client scan scope is pinned to `build/generated/bear/src/main/java/**`.
- user-lane scan scope is pinned to `src/main/java/**`.

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
- containment and block-port enforcement consume v3 wiring manifests.
- required fields include `blockRootSourceDir`, `governedSourceRoots`, and `blockPortBindings`.
- `governedSourceRoots` ordering is deterministic in this slice:
  - first: `blockRootSourceDir`
  - mandatory second: `src/main/java/blocks/_shared` (reserved governed root)
- stale/non-v3/malformed wiring manifests fail deterministically with `CODE=MANIFEST_INVALID` (exit `2`); rerun `bear compile`.

For lock/bootstrap test-runner failures, detail lines append deterministic diagnostics:

- `attempts=<csv>`
- `CACHE_MODE=<isolated|user-cache|external-env>`
- `FALLBACK=<none|to_user_cache>`

Troubleshooting guardrail:
- do not patch `build.gradle` manually as first response to lock/bootstrap failures; first use BEAR retry/fallback flow and BEAR-owned generated wiring (`build/generated/bear/gradle/bear-containment.gradle` where applicable).

`--all` mode renders deterministic block sections and summary fields.
- pass sections may include contextual `DETAIL: ...` lines when non-failure informational signals apply.
- generated structural test evidence is enabled by default:
  - generated structural tests emit deterministic `BEAR_STRUCTURAL_SIGNAL|...` lines when mismatches are detected
  - strict mode is opt-in with JVM property `-Dbear.structural.tests.strict=true`
  - strict mode fails once per structural test class with aggregated sorted mismatch output

## Exit codes emitted

- `0` pass
- `2` validation/config failure (`IR_VALIDATION`, `POLICY_INVALID`, `MANIFEST_INVALID`)
- `3` drift failure
- `4` project test failure or timeout
- `6` reach/hygiene policy failure (`UNDECLARED_REACH`, `REFLECTION_DISPATCH_FORBIDDEN`, or `HYGIENE_UNEXPECTED_PATHS`)
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
- [troubleshooting.md#block_port_index_required](troubleshooting.md#block_port_index_required)
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


## Agent JSON mode

- `--agent` writes JSON to stdout only (no prose output mixed into stdout).
- `collectMode` in JSON is `first` by default and `all` when `--collect=all` is set.
- stream contract: BEAR itself emits no normal prose lines to stderr on normal command completion paths.

