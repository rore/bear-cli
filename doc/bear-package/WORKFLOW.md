# WORKFLOW.md (Package Source)

Purpose:
- Human-readable operating guide for isolated BEAR workflow.

## Read In This Order

1. `doc/BEAR_PRIMER.md`
2. the feature request

## Standard Flow

1. Read request.
2. Discover current BEAR structure from repo state:
- inspect `spec/*.bear.yaml` if present
- inspect `bear.blocks.yaml` if present
- inspect generated package namespaces and existing `*Impl.java` files
3. Apply IR-first rule if boundary/contract/effect changes are needed.
4. Decide create-vs-update for blocks:
- update existing block when feature fits current contract/capability boundary
- create a new block when feature introduces a distinct contract/responsibility boundary
5. If no IR exists, create initial `spec/*.bear.yaml` first.
6. Implement in `*Impl.java` and tests only.
7. Run canonical gate:
- `.\bin\bear-all.ps1` or `./bin/bear-all.sh`
  - if repo uses `bear.blocks.yaml`, canonical CLI gate is `bear check --all --project <repoRoot>`
8. Resolve failures by category until gate exits `0`.

## Failure Triage

1. `exit 2` (validation/schema/semantic):
- fix IR shape/references
- rerun gate

2. `exit 3` (drift):
- run compile for the IR file that triggered drift:
  - `./bin/bear.* compile <ir-file> --project .`
- ensure generated tree matches current IR
- rerun gate

3. boundary expansion lines present:
- confirm this is intended
- ensure IR change is explicit and reviewed
- continue with compile + implementation + gate

4. `exit 4` (tests/verification):
- fix impl/tests/verification issue
- rerun gate

## Constraints

- No generated-file edits.
- No silent boundary expansion.
- One command determines done/not-done.
