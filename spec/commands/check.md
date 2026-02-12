# `bear check` (v1)

## Command
`bear check <ir-file> --project <path>`

`bear check` v1 is a drift regeneration gate for BEAR-owned artifacts.

It performs:
1. Parse + validate + normalize IR.
2. Compile normalized IR into a temporary project root.
3. Diff temp BEAR-owned tree against project BEAR-owned tree.
4. Fail deterministically on any mismatch.

## Scope (v1)
- Includes:
  - drift detection for BEAR-owned generated tree
- Excludes:
  - project test execution
  - user-owned impl file checks
  - static boundary isolation checks

## Baseline and Candidate
- Baseline:
  - `<project>/build/generated/bear`
- Candidate:
  - `<tempRoot>/build/generated/bear` (from deterministic compile)

## Exit codes
- `0`: no drift
- `2`: schema/semantic IR validation error
- `3`: drift detected (including missing baseline)
- `64`: usage error
- `74`: IO error
- `70`: internal/unexpected error

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

## No-mutation guarantee
`bear check` does not modify project baseline files.
It is compare-only against temp-generated output.
