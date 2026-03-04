# BEAR Block Index (v1)

## Purpose

`bear.blocks.yaml` is the source of truth for BEAR-managed blocks in repo-level `--all` enforcement.
v1 does not support discovery-based inclusion.

## File

Default location:
- `<repoRoot>/bear.blocks.yaml`

Override:
- `--blocks <path>` (repo-relative)

## Schema (v1)

Root fields:
- `version` (required, must be `v1`)
- `blocks` (required, non-empty list)

Each block entry:
- `name` (required, unique, regex `[a-z][a-z0-9-]*`)
- `ir` (required, repo-relative path to IR YAML)
- `projectRoot` (required, repo-relative directory path)
- `enabled` (optional, default `true`)

## Validation Rules

- fail on missing/invalid required fields
- fail on duplicate block names
- fail on non repo-relative paths
- fail when path escapes repo root
- enabled blocks may share `projectRoot`
- index `name` is canonical block key and must match normalized IR `block.name`

## Canonicalization

- execution and output order is canonical: sort selected blocks by `name`
- output paths use `/` separators

## Marker Path Invariant (v1)

Canonical marker path per enabled block:
- `<projectRoot>/build/generated/bear/surfaces/<blockKey>.surface.json`

Marker payload includes:
- `surfaceVersion: 2`

This path is a v1 BEAR-owned contract path.
Changing it is a breaking change and requires a spec/version update.

Legacy marker path (deprecated):
- `<projectRoot>/build/generated/bear/bear.surface.json`
- treated as invalid legacy layout; command fails with deterministic remediation

## Orphan / Legacy Marker Guards

Default `--all` guard (managed roots only):
- scan `<managedRoot>/build/generated/bear/surfaces/*.surface.json`
- any marker not mapped to enabled selected blocks for that root is orphan -> fail
- any legacy marker under managed roots -> fail

`--strict-orphans`:
- repo-wide scan for `**/build/generated/bear/surfaces/*.surface.json`
- repo-wide legacy marker scan for `**/build/generated/bear/bear.surface.json`
- any unmatched marker or legacy marker -> fail
