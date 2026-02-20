# `bear check` (v1.6)

## Command
`bear check <ir-file> --project <path>`

`bear check --all --project <repoRoot> [--blocks <path>] [--only <csv>] [--fail-fast] [--strict-orphans]`

`bear check` v1.6 is a deterministic gate for:
1. drift regeneration enforcement on BEAR-owned artifacts
2. undeclared-reach enforcement for covered preview JVM direct HTTP surfaces
3. project test execution after drift and undeclared-reach pass
4. boundary-expansion signaling derived from BEAR surface manifests
5. allowed-deps containment verification when IR declares `block.impl.allowedDeps`

For base-branch PR governance classification, use `bear pr-check`.

It performs:
1. Parse + validate + normalize IR.
2. Compile normalized IR into a temporary project root.
3. Diff temp BEAR-owned tree against project BEAR-owned tree.
4. If drift passes, run undeclared-reach scan on project source tree.
5. If IR declares allowed deps, verify containment script/index/marker hash handshake.
6. If containment passes, run undeclared-reach scan on project source tree.
7. If undeclared-reach passes, execute project tests via Gradle wrapper.
8. Fail deterministically on mismatch or test failure.

`--all` mode orchestrates the same single-block gate for every selected index block in canonical order.
It is deterministic and uses the block index as inclusion source.

## Scope (v1)
- Includes:
  - drift detection for BEAR-owned generated tree
- Includes:
  - project test execution after no-drift result
- Includes:
  - covered undeclared-reach static checks for preview JVM HTTP surfaces
- Includes:
  - allowed-deps containment handshake gate for supported Java+Gradle targets
- Excludes:
  - full static isolation checks for arbitrary external surfaces beyond preview coverage

## Baseline and Candidate
- Baseline:
  - `<project>/build/generated/bear`
- Candidate:
  - `<tempRoot>/build/generated/bear` (from deterministic compile)

## Exit codes
Exit codes are defined centrally in `spec/commands/exit-codes.md`.
- `0`: no drift
- `2`: schema/semantic IR validation error
- `3`: drift detected (including missing baseline)
- `4`: project test failure (including timeout)
- `6`: undeclared reach detected
- `64`: usage error
- `74`: IO error
- `70`: internal/unexpected error

For `--all`, final exit code uses explicit severity-rank aggregation from `spec/commands/exit-codes.md` (not numeric max).

## Failure Envelope (non-zero exits)
For every non-zero exit, `check` appends the standard failure footer defined in `spec/commands/exit-codes.md`:
- `CODE=<enum>`
- `PATH=<locator>`
- `REMEDIATION=<deterministic-step>`

Envelope invariants:
- emitted exactly once
- last three stderr lines
- no stderr output after `REMEDIATION=...`

For `--all`:
- aggregated multi-block failures use:
  - `CODE=REPO_MULTI_BLOCK_FAILED`
  - `PATH=bear.blocks.yaml`
  - `REMEDIATION=Review per-block results above and fix failing blocks, then rerun the command.`

## Drift output format
All drift lines go to stderr:
- `drift: ADDED: <relative/path>`
- `drift: REMOVED: <relative/path>`
- `drift: CHANGED: <relative/path>`

Missing baseline:
- `drift: MISSING_BASELINE: build/generated/bear (run: bear compile <ir-file> --project <path>)`
- Baseline is considered missing when:
  - `<project>/build/generated/bear` does not exist, OR
  - it exists but contains no regular files
- If drift is detected, project tests are not executed.

## Boundary manifest source
- Baseline manifest:
  - `<project>/build/generated/bear/surfaces/<blockKey>.surface.json`
- Candidate manifest:
  - `<tempRoot>/build/generated/bear/surfaces/<blockKey>.surface.json`

Boundary classification uses manifest data only (no Java source parsing).

## Boundary signal format (stderr)
Boundary lines:
- `boundary: EXPANSION: CAPABILITY_ADDED: <capability>`
- `boundary: EXPANSION: PURE_DEP_ADDED: <groupId:artifactId@version>`
- `boundary: EXPANSION: PURE_DEP_VERSION_CHANGED: <groupId:artifactId@old->new>`
- `boundary: EXPANSION: CAPABILITY_OP_ADDED: <capability>.<op>`
- `boundary: EXPANSION: INVARIANT_RELAXED: non_negative:<field>`

Scope in v1 preview:
- `CAPABILITY_ADDED`: capability appears in candidate but not baseline
- `CAPABILITY_OP_ADDED`: op appears in candidate capability but not baseline capability
- `INVARIANT_RELAXED`: baseline `non_negative:<field>` missing in candidate
- `PURE_DEP_ADDED`: allowed dep appears in candidate surface but not baseline
- `PURE_DEP_VERSION_CHANGED`: same allowed dep GA with version change between baseline/candidate

Ordering (deterministic):
- sort by `(typePrecedence, key)`
- type precedence:
  1. `CAPABILITY_ADDED`
  2. `PURE_DEP_ADDED`
  3. `PURE_DEP_VERSION_CHANGED`
  4. `CAPABILITY_OP_ADDED`
  5. `INVARIANT_RELAXED`
- then key lexicographic

Output order in `check`:
1. baseline manifest diagnostics (if any)
2. boundary signal lines
3. drift lines
4. containment lines (if any)
5. undeclared-reach lines (if any)
6. test failure/timeout output (if reached)
7. failure envelope (if non-zero)

Relationship to drift:
- boundary signaling is a classification layer on top of drift context
- not a separate verdict channel
- exit codes remain unchanged

## Undeclared Reach Enforcement (v1.5 preview)
Detection runs only after drift pass and before project tests.

## Allowed Deps Containment (v1.6 preview)
Containment gate runs only when IR declares `block.impl.allowedDeps` and drift has passed.

Supported target:
- Java+Gradle project root with `gradlew` or `gradlew.bat`

Required artifacts:
- `build/generated/bear/gradle/bear-containment.gradle`
- `build/generated/bear/config/containment-required.json`
- `build/bear/containment/applied.marker`

Handshake:
- `applied.marker` must contain `hash=<sha256(containment-required.json)>`
- missing or stale marker fails deterministically
- `bear check` never invokes Gradle automatically

Deterministic failure lines:
- `check: CONTAINMENT_REQUIRED: UNSUPPORTED_TARGET: ...`
- `check: CONTAINMENT_REQUIRED: SCRIPT_MISSING: ...`
- `check: CONTAINMENT_REQUIRED: INDEX_MISSING: ...`
- `check: CONTAINMENT_REQUIRED: MARKER_MISSING: ...`
- `check: CONTAINMENT_REQUIRED: MARKER_STALE: ...`

Fatal output lines (stderr):
- `check: UNDECLARED_REACH: <relative/path>: <surface>`

Surfaces (preview JVM contract):
- `java.net.http.HttpClient`
- `java.net.URL#openConnection`
- `okhttp3.OkHttpClient`
- `org.springframework.web.client.RestTemplate`
- `java.net.HttpURLConnection`

Exclusions:
- generated tree under `build/generated/bear/**`
- test sources under `src/test/**`
- build output directories (`build/**`, `.gradle/**`)

Ordering (deterministic):
- sort by relative path lexicographic
- then by surface token lexicographic

Verdict:
- any undeclared-reach finding fails with exit `6` and `CODE=UNDECLARED_REACH`
- project tests do not run when undeclared-reach is detected
- in shared-root multi-block orchestration, undeclared-reach is a projectRoot-wide gate

## Manifest diagnostics
Baseline manifest problems are warning-only:
- `check: BASELINE_MANIFEST_MISSING: <path>`
- `check: BASELINE_MANIFEST_INVALID: <reasonCode>`
- `check: BASELINE_STAMP_MISMATCH: irHash/generatorVersion differ; classification may be stale`

Candidate manifest problems are fatal internal errors:
- missing candidate manifest => internal error (`70`)
- invalid candidate manifest => internal error (`70`)

## Ordering (deterministic)
- Primary key: relative path (lexicographic, `/` separators)
- Secondary key for same path: `ADDED`, `REMOVED`, `CHANGED`

## Diff semantics (frozen)
- Compare regular files only.
- Ignore directories and file metadata (permissions/timestamps/attrs).
- Compare file content bytes only.
- Empty files are included naturally.
- `<relative/path>` in drift lines is always relative to `build/generated/bear` root.

## Temp handling
- Temp root created via OS temp API (`Files.createTempDirectory("bear-check-")`).
- Temp path is internal-only:
  - never printed in command output
  - never part of test assertions
- Cleanup is best-effort and must not affect check verdict.

## Project test execution (v1.1)
- Wrapper invocation:
  - Windows: `<project>/gradlew.bat --no-daemon test`
  - Unix-like: `<project>/gradlew --no-daemon test`
- Wrapper requirements:
  - missing wrapper file => IO error (`74`) with guidance
  - non-executable Unix wrapper => IO error (`74`) with chmod guidance
  - no fallback to system `gradle`
- Timeout:
  - fixed 300s default (internal override may be used by tests)
  - timeout is reported as test failure exit code `4`
- Failure output:
  - `check: TEST_FAILED: project tests failed`
  - or `check: TEST_TIMEOUT: project tests exceeded <seconds>s`
  - then print last 40 lines of merged test output
  - line handling is deterministic: normalize `\r\n` and `\n`, tail by normalized lines
  - tail lines are printed without extra per-line prefixes

## No-mutation guarantee
`bear check` does not modify project baseline files.
It is compare-only against temp-generated output.

## `--all` Mode (v1.5)

Index source:
- default: `<repoRoot>/bear.blocks.yaml`
- override: `--blocks <path>` (repo-relative)
- schema and constraints: `spec/repo/block-index.md`

Selection:
- default: all index blocks
- `--only <csv>`: selected names only; unknown names fail with usage error (`64`)
- disabled blocks render as:
  - `STATUS: SKIP`
  - `REASON: DISABLED`

Execution:
- selected enabled blocks run in canonical `name` order
- default is continue-all (all selected enabled blocks are evaluated)
- `--fail-fast`:
  - after first block failure, remaining selected enabled blocks are rendered as:
    - `STATUS: SKIP`
    - `REASON: FAIL_FAST_ABORT`

Strict orphan mode:
- default (no strict flag): managed-root marker guard only
  - scan `<managedRoot>/build/generated/bear/surfaces/*.surface.json`
  - orphan marker under managed root fails
  - legacy marker `<managedRoot>/build/generated/bear/bear.surface.json` fails
- `--strict-orphans`: repo-wide marker scan (`**/build/generated/bear/surfaces/*.surface.json`)
  plus repo-wide legacy marker scan (`**/build/generated/bear/bear.surface.json`)
- with `--only`, strict mode remains repo-wide (strict means strict)

Execution details:
- per block: validation + block-scoped drift/boundary checks
- per root: undeclared-reach once and tests once (for roots with structural pass blocks)
- root-level reach/test failure is applied to all structurally-passed blocks in that root

Per-block output section (deterministic):
- `BLOCK: <name>`
- `IR: <path>`
- `PROJECT: <path>`
- `STATUS: PASS|FAIL|SKIP`
- `EXIT_CODE: <n>`
- on FAIL:
  - `CATEGORY: <...>`
  - `BLOCK_CODE: <...>`
  - `BLOCK_PATH: <...>`
  - `DETAIL: <single-line>`
  - `BLOCK_REMEDIATION: <single-line>`
- on SKIP:
  - `REASON: DISABLED|FAIL_FAST_ABORT`

Summary section:
- `SUMMARY:`
- `<N> blocks total`
- `<C> checked`
- `<P> passed`
- `<F> failed`
- `<S> skipped`
- `FAIL_FAST_TRIGGERED: true|false`
- `ROOT_REACH_FAILED: <n>`
- `ROOT_TEST_FAILED: <n>`
- `ROOT_TEST_SKIPPED_DUE_TO_REACH: <n>`
- `EXIT_CODE: <n>`


