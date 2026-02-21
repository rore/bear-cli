# BLOCK_INDEX_QUICKREF.md

Purpose:
- Fast reference for valid `bear.blocks.yaml` in multi-block BEAR governance.

## Minimal Valid Shape

```yaml
version: v0
blocks:
  - name: execution-core
    ir: spec/execution-core.bear.yaml
    projectRoot: .
```

## Required Fields

Root:
- `version: v0`
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
- block `name` does not match IR block name normalization

Fix:
- align index `name` with normalized IR block name
- rerun `check --all`
