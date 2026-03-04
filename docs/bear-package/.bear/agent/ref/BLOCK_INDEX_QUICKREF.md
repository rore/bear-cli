# BLOCK_INDEX_QUICKREF.md

Purpose:
- Fast reference for valid `bear.blocks.yaml` in multi-block BEAR governance.

## Minimal Valid Shape

```yaml
version: v1
blocks:
  - name: execution-core
    ir: spec/execution-core.bear.yaml
    projectRoot: .
```

## Required Fields

Root:
- `version: v1`
- `blocks` (non-empty list)

Per block:
- `name` (regex `[a-z][a-z0-9-]*`, unique)
- `ir` (repo-relative path to IR)
- `projectRoot` (repo-relative directory path)
- `enabled` (optional boolean, default `true`)

## Key Rules

1. Paths must be repo-relative and must not escape repo root.
2. `projectRoot: .` is valid and means repo root.
3. `name` is canonical block key and must match normalized IR `block.name`.
4. Multi-block repos must keep `bear.blocks.yaml`; do not bypass governance by deleting it.
5. Single-file commands on IRs with `kind=block` effects resolve index as explicit `--index` or inferred `<project>/bear.blocks.yaml`, then enforce tuple membership `(ir, projectRoot)`.

## Common Mistakes and Fixes

1. Mistake:
- deleting `bear.blocks.yaml` after validation errors

Fix:
- restore file
- fix invalid fields
- rerun `bear check --all --project <repoRoot>`

2. Mistake:
- invalid `projectRoot` value (absolute path, escapes repo, or wrong directory)

Fix:
- use repo-relative path
- use `.` for repo root when applicable

3. Mistake:
- running single-file `compile`/`fix`/`check`/`pr-check` on a block-port IR when `<project>/bear.blocks.yaml` is missing

Fix:
- create `<project>/bear.blocks.yaml` or pass `--index <path-to-bear.blocks.yaml>`
- ensure exact `(ir, projectRoot)` tuple exists in index

4. Mistake:
- block `name` does not match IR block name normalization

Fix:
- align index `name` with normalized IR block name
- rerun `check --all`


