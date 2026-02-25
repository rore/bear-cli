# CONTRACTS.md

Purpose:
- Normative BEAR contract surface.
- Canonical definitions, invariants, MUST/MUST NOT requirements.

## Normative Scope

This file is contract-only.
- Contains: definitions, MUST/MUST NOT rules, canonical paths, canonical formats, frozen semantics.
- Excludes: troubleshooting flows, failure triage playbooks, procedural step-by-step runbooks.

## Canonical Wiring Recipe

Default adapter shape:
1. Keep `_shared` focused on shared state primitives and small pure utilities.
2. Prefer one adapter class per generated block package.
3. Place adapters only under governed roots:
- block root: `src/main/java/blocks/<blockKey>/...`
- shared governed root: `src/main/java/blocks/_shared/<blockKey>/...`
4. Each adapter class MUST implement generated ports from one generated block package.

Multi-block adapter opt-in:
1. Allowed only when intentional.
2. Class MUST be under `src/main/java/blocks/_shared/**`.
3. Marker MUST be exact `// BEAR:ALLOW_MULTI_BLOCK_PORT_IMPL`.
4. Marker MUST appear within 5 non-empty lines above class declaration.
5. Completion report MUST include required governance-signal disposition fields.

## Policy Contract (`check`)

Policy files are optional:
1. `.bear/policy/reflection-allowlist.txt`
2. `.bear/policy/hygiene-allowlist.txt`

Allowlist file format (if present) MUST be:
1. UTF-8, one repo-relative path per line.
2. Forward slashes only.
3. Sorted lexicographically.
4. Unique entries.
5. No globs, no absolute paths, no trailing slash.

Rule invariants:
1. Missing policy file means empty allowlist.
2. Malformed policy file fails with `CODE=POLICY_INVALID`.
3. `--strict-hygiene` enables unexpected-path enforcement.
4. Classloading reflection in `src/main/**` is blocked unless exact-path allowlisted.
5. Governed logic -> governed impl binding via `META-INF/services` or `module-info.java provides` is blocked.

## Editable Boundaries

MUST NOT edit:
1. `build/generated/bear/**`

MUST edit implementation in user-owned paths:
1. `src/main/java/blocks/<pkg-segment>/impl/<BlockName>Impl.java`
2. `src/test/java/**`
3. Repo-owned IR/docs/scripts

Path/package contract:
1. Preferred impl path: `src/main/java/blocks/<pkg-segment>/impl/<BlockName>Impl.java`
2. Preferred package: `blocks.<pkg-segment>.impl`
3. User-owned `*Impl.java` MUST NOT be relocated under `src/main/java/com/bear/generated/**`

## Semantics Policy (wrapper-owned)

Selection rule:
- Enforce only semantics that are wrapper-checkable from declared inputs/outputs/ports, require no hidden context, are deterministic, and have frozen contracts.

Implications:
1. Idempotency and invariants are wrapper-owned semantics.
2. Do not push wrapper-owned semantics into impl conventions or suppressions.
3. Generated `Wrapper.of(<ports...>)` is the sanctioned default production wiring path.
4. `(ports..., Logic)` constructor remains available for tests/advanced injection.

## Multi-Block Governance Invariants

1. Multi-block state requires `bear.blocks.yaml`.
2. In multi-block state, canonical done gates are repository-level `--all` commands.
3. Removing `bear.blocks.yaml` to force per-IR fallback is invalid.
4. Completion is valid only with both gates evidenced green:
- `bear check --all --project <repoRoot>`
- `bear pr-check --all --project <repoRoot> --base <ref>`
5. Completion report MUST include governance-signal disposition block as defined in `.bear/agent/REPORTING.md`.
