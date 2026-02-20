# `bear pr-check` (v1.1)

## Command
`bear pr-check <ir-file> --project <path> --base <ref>`

`bear pr-check --all --project <repoRoot> --base <ref> [--blocks <path>] [--only <csv>] [--strict-orphans]`

`bear pr-check` is a deterministic PR governance classifier.
It compares normalized IR in the current workspace (`HEAD` working tree) against the IR at merge-base with the provided base ref.

`pr-check` is classification-only:
- no generated-tree drift diff
- no project test execution

## Scope
- Includes:
  - base-branch IR diff classification
  - deterministic boundary-expansion signaling for CI
- Excludes:
  - local generated baseline drift checks (`bear check` owns this)
  - Gradle/project test execution (`bear check` owns this)

## Input contract
- `<ir-file>` must be repo-relative.
- Absolute paths are invalid.
- Paths escaping project root (for example `../...`) are invalid.
- `<ir-file>` is interpreted relative to `--project <path>`.
- If `<project>/<ir-file>` is missing in working tree:
  - fail with exit `74`
  - emit:
    - `pr-check: IO_ERROR: READ_HEAD_FAILED: <ir-file>`

## Git snapshot model
1. Resolve merge-base:
   - `git -C <project> merge-base HEAD <ref>`
2. Head IR snapshot:
   - `<project>/<ir-file>` in working tree
3. Base IR snapshot:
   - path existence probe:
     - `git -C <project> cat-file -e <mergeBase>:<ir-file>`
   - if path is missing at merge-base:
     - treat base as empty surface
     - emit:
       - `pr-check: INFO: BASE_IR_MISSING_AT_MERGE_BASE: <ir-file>: treated_as_empty_base`
   - if path exists:
     - read:
       - `git -C <project> show <mergeBase>:<ir-file>`

## Exit codes
Exit codes are defined centrally in `spec/commands/exit-codes.md`.
- `0`: no boundary-expanding deltas (no-delta or ordinary-only delta)
- `5`: one or more boundary-expanding deltas
- `2`: schema/semantic IR validation error (base or head)
- `64`: usage error
- `74`: git/IO error
- `70`: internal/unexpected error

`pr-check` does not use drift exit code `3`.

For `--all`, final exit code uses explicit severity-rank aggregation from `spec/commands/exit-codes.md`.

## Failure Envelope (non-zero exits)
For every non-zero exit, `pr-check` appends the standard failure footer defined in `spec/commands/exit-codes.md`:
- `CODE=<enum>`
- `PATH=<locator>`
- `REMEDIATION=<deterministic-step>`

Envelope invariants:
- emitted exactly once
- last three stderr lines
- no stderr output after `REMEDIATION=...`

For `--all` aggregated failures:
- `CODE=REPO_MULTI_BLOCK_FAILED`
- `PATH=bear.blocks.yaml`
- `REMEDIATION=Review per-block results above and fix failing blocks, then rerun the command.`

## Git/IO error prefixes
Git/IO failures are emitted with stable reason prefixes:
- `pr-check: IO_ERROR: NOT_A_GIT_REPO: <project>`
- `pr-check: IO_ERROR: MERGE_BASE_FAILED: <base>`
- `pr-check: IO_ERROR: READ_HEAD_FAILED: <ir-file>`
- `pr-check: IO_ERROR: BASE_IR_LOOKUP_FAILED: <ir-file>`
- `pr-check: IO_ERROR: BASE_IR_READ_FAILED: <ir-file>`
- `pr-check: IO_ERROR: INTERNAL_IO: <detail>`

Classification between `IO_ERROR` and `IO_GIT` follows `spec/commands/exit-codes.md`.

## Output format
Delta lines are emitted to stderr:
- `pr-delta: <CLASS>: <CATEGORY>: <CHANGE>: <KEY>`

Where:
- `<CLASS>` in `BOUNDARY_EXPANDING`, `ORDINARY`
- `<CATEGORY>` in `PORTS`, `ALLOWED_DEPS`, `OPS`, `IDEMPOTENCY`, `CONTRACT`, `INVARIANTS`
- `<CHANGE>` in `CHANGED`, `ADDED`, `REMOVED`

Verdict lines:
- boundary found (stderr):
  - `pr-check: FAIL: BOUNDARY_EXPANSION_DETECTED`
- no boundary found (stdout):
  - `pr-check: OK: NO_BOUNDARY_EXPANSION`

## Deterministic ordering
Sort by:
1. class precedence:
   - `BOUNDARY_EXPANDING`
   - `ORDINARY`
2. category precedence:
   - `PORTS`
   - `ALLOWED_DEPS`
   - `OPS`
   - `IDEMPOTENCY`
   - `CONTRACT`
   - `INVARIANTS`
3. change precedence:
   - `CHANGED`
   - `ADDED`
   - `REMOVED`
4. key lexicographic

## Classification rules

### `PORTS` (from `block.effects.allow[*].port`)
- `ADDED` => `BOUNDARY_EXPANDING`
- `REMOVED` => `ORDINARY`

Comparison is set-based (order-insensitive).

### `OPS` (from `block.effects.allow[*].ops` under common ports)
- `ADDED` => `ORDINARY`
- `REMOVED` => `ORDINARY`

Comparison is set-based (order-insensitive) within each common port.

### `ALLOWED_DEPS` (from `block.impl.allowedDeps`)
- added `ga@version` => `BOUNDARY_EXPANDING`
- removed `ga@version` => `ORDINARY`
- version change `ga@old->new` => `BOUNDARY_EXPANDING`

Rules:
- compare by `groupId:artifactId`
- version is exact pinned string
- ordering is deterministic by GA key

### `IDEMPOTENCY` (from `block.idempotency`)
- block added:
  - `ADDED: idempotency` => `BOUNDARY_EXPANDING`
- block removed:
  - `REMOVED: idempotency` => `BOUNDARY_EXPANDING`
- both present:
  - changed `key`:
    - `CHANGED: idempotency.key` => `BOUNDARY_EXPANDING`
  - changed `store.port`:
    - `CHANGED: idempotency.store.port` => `BOUNDARY_EXPANDING`
  - changed `store.getOp`:
    - `CHANGED: idempotency.store.getOp` => `BOUNDARY_EXPANDING`
  - changed `store.putOp`:
    - `CHANGED: idempotency.store.putOp` => `BOUNDARY_EXPANDING`

Emission rule:
- for idempotency add/remove, emit exactly one top-level delta key (`idempotency`)
- do not emit subkey deltas on add/remove

### `CONTRACT` (from `block.contract.inputs` / `block.contract.outputs`)
Type changes are defined only as v0 IR enum differences in `type`:
- `string`
- `decimal`
- `int`
- `bool`
- `enum`

No other metadata is considered for type-change classification.

Outputs:
- add/remove/type-change => `BOUNDARY_EXPANDING`

Inputs:
- add => `ORDINARY`
- remove/type-change => `BOUNDARY_EXPANDING`

### `INVARIANTS` (from canonical invariant keys, for example `non_negative:<field>`)
- removed => `BOUNDARY_EXPANDING`
- added => `ORDINARY`

## New IR file policy
If base IR path is missing at merge-base and head IR exists:
- base is treated as empty surface
- resulting additions are classified normally
- introducing a new IR file is boundary-expanding by policy

## Relationship to `bear check`
- `bear pr-check`:
  - base-branch governance classification only
- `bear check`:
  - local generated drift + project tests

## Output examples

Boundary-expanding example (port add + invariant remove):
```text
pr-delta: BOUNDARY_EXPANDING: PORTS: ADDED: audit
pr-delta: BOUNDARY_EXPANDING: INVARIANTS: REMOVED: non_negative:balance
pr-check: FAIL: BOUNDARY_EXPANSION_DETECTED
```

Ordinary-only example (op add under existing port):
```text
pr-delta: ORDINARY: OPS: ADDED: ledger.reverse
pr-check: OK: NO_BOUNDARY_EXPANSION
```

## `--all` Mode (v1.1 addendum)

Index source:
- default: `<repoRoot>/bear.blocks.yaml`
- override: `--blocks <path>` (repo-relative)
- schema and constraints: `spec/repo/block-index.md`

Selection:
- default: all index blocks
- `--only <csv>`: selected names only; unknown name => usage error (`64`)
- disabled selected blocks render as:
  - `STATUS: SKIP`
  - `REASON: DISABLED`

Strict orphan mode:
- default: managed-root marker guard only
  - scan `<managedRoot>/build/generated/bear/surfaces/*.surface.json`
  - orphan marker under managed root fails
  - legacy marker `<managedRoot>/build/generated/bear/bear.surface.json` fails
- `--strict-orphans`: repo-wide marker scan with orphan detection against enabled index entries
  plus repo-wide legacy marker scan
- with `--only`, strict mode still scans repo-wide

Per-block section (deterministic):
- `BLOCK: <name>`
- `IR: <path>`
- `PROJECT: <path>`
- `STATUS: PASS|FAIL|SKIP`
- `EXIT_CODE: <n>`
- `CLASSIFICATION:`
  - `BOUNDARY_EXPANDING` iff block exit code is `5`
  - `ORDINARY` iff block exit code is `0` and delta set is non-empty
  - `NO_CHANGES` iff block exit code is `0` and delta set is empty
- `DELTA:`:
  - sorted `pr-delta:` lines, or `(no changes)`

On non-boundary failures (`2`, `64`, `70`, `74`), include:
- `CATEGORY`
- `BLOCK_CODE`
- `BLOCK_PATH`
- `DETAIL`
- `BLOCK_REMEDIATION`

Summary section:
- `SUMMARY:`
- `<N> blocks total`
- `<C> checked`
- `<P> passed`
- `<F> failed`
- `<S> skipped`
- `BOUNDARY_EXPANDING: <count>`
- `EXIT_CODE: <n>`

