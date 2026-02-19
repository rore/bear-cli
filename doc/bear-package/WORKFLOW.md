# WORKFLOW.md

Purpose:
- Deterministic operating loop for BEAR in a generic backend project.

## Read In This Order

1. `doc/BEAR_PRIMER.md`
2. `doc/IR_QUICKREF.md`
3. `doc/IR_EXAMPLES.md`
4. `doc/BLOCK_INDEX_QUICKREF.md`
5. the feature request

## Canonical Flows

### A) Greenfield Flow (no IR yet)

1. Read request and identify responsibilities.
2. Decide block decomposition (single or multi-block).
3. Create initial `spec/*.bear.yaml`.
4. If multiple governed blocks exist, create `bear.blocks.yaml`.
5. For each touched IR file run:
- `bear validate <ir-file>`
- `bear compile <ir-file> --project <repoRoot>`
6. Run gate:
- single-block mode: `bear check <ir-file> --project <repoRoot>`
- multi-block mode: `bear check --all --project <repoRoot>`
7. Implement in `*Impl.java` and tests only.
8. Re-run check to `0`.

### B) Extension Flow (existing BEAR repo)

1. Discover existing IR/index/impl state.
2. Decide update-existing-block vs add-new-block.
3. Apply IR changes first when boundaries change.
4. Compile touched IR files.
5. Run check gate (`check` or `check --all`).
6. Implement and test.
7. Re-run check gate to `0`.
8. For PR/base governance run:
- `bear pr-check <ir-file> --project <repoRoot> --base <ref>`
- or `bear pr-check --all --project <repoRoot> --base <ref>` when indexed

## Block Index Gate

1. Multi-block state requires `bear.blocks.yaml`.
2. In multi-block state, use only:
- `bear check --all --project <repoRoot>`
- `bear pr-check --all --project <repoRoot> --base <ref>`
3. Single-block fallback loops are valid only when exactly one IR file exists and no index exists.
4. Removing `bear.blocks.yaml` to continue via per-IR fallback is invalid.

## Wrapper Preference

If wrappers are shipped in the project, use them as canonical gates:
- `.\bin\bear-all.ps1` / `./bin/bear-all.sh`
- `.\bin\pr-gate.ps1 <base-ref>` / `./bin/pr-gate.sh <base-ref>`

Wrappers should route to `--all` when `bear.blocks.yaml` exists.

## Failure Triage (Deterministic)

1. `64` usage error:
- fix args/command invocation

2. `2` validation/schema/semantic failure:
- fix IR structure/references/enums/duplicates

3. `3` drift failure:
- rerun compile for changed IR
- rerun check

4. `6` undeclared reach:
- declare required port/op in IR
- compile
- route call through generated port interface

5. `4` project tests failed:
- fix implementation/tests

6. `5` boundary expansion (`pr-check`):
- confirm expansion is intentional and reviewable

7. `74` IO/git failure:
- fix path/ref/permission/repo state

Index troubleshooting:
- `projectRoot` must be a repo-relative directory path.
- repo root is valid and represented as `.`.
- if index fails validation, fix `name`/`ir`/`projectRoot` and rerun `check --all`.

8. `70` internal failure:
- collect output and report as tool defect

## Constraints

- No generated-file edits.
- No silent boundary expansion.
- One deterministic gate determines done/not-done.
