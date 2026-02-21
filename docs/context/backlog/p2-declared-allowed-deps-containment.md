# P2: Declared allowed dependencies (Boundary + Containment)

## Summary

Allow block impl logic to use explicitly allowlisted allowed dependencies while keeping boundary expansion visible and deterministic.

## Locked Decisions

1. Block-level declaration in IR:
   - `block.impl.allowedDeps`
   - entry: `maven: <groupId>:<artifactId>` + `version: <pinned>`
2. IR schema bump to `v1`:
   - parser accepts `v1` only
   - unknown fields remain hard errors
3. Preview policy:
   - no wildcard coords
   - no version ranges
   - duplicate `groupId:artifactId` is semantic error
   - deterministic sorted order

## Compile Outputs (Deterministic)

`bear compile` must emit:

1. `build/generated/bear/config/allowed-deps/<blockKey>.json`
2. `build/generated/bear/config/containment-required.json`
3. `build/generated/bear/gradle/bear-containment.gradle` (single canonical entrypoint)

`containment-required.json` is the authoritative block set for containment wiring.

## Gradle Containment Contract

Generated entrypoint (`bear-containment.gradle`) must:

1. Enforce impl compilation containment per block.
2. Compile impl helpers under:
   - `src/main/java/blocks/<blockKey>/impl/**`
3. Emit per-block impl classes to:
   - `build/bear/impl-classes/<blockKey>/`
4. Avoid duplicate impl compilation by normal `compileJava`.
5. Write markers during deterministic impl compile task execution:
   - per-block marker: `build/bear/containment/<blockKey>.applied.marker`
   - aggregate marker: `build/bear/containment/applied.marker`
6. Marker must include hash of:
   - `build/generated/bear/config/containment-required.json`

## Check / PR-check Behavior

### `bear pr-check`

Governance-only, marker-agnostic:

1. allowed dep added => `BOUNDARY_EXPANDING` (exit `5`)
2. allowed dep version changed => `BOUNDARY_EXPANDING` (exit `5`)
3. allowed dep removed => ordinary/non-expanding
4. Deterministic category + ordering:
   - include `ALLOWED_DEPS` in frozen ordering

### `bear check`

Enforcement gate:

1. If `allowedDeps` absent: unchanged behavior.
2. If `allowedDeps` present:
   - supported target required: Java + Gradle wrapper
   - require generated entrypoint/index + marker/hash match
   - do **not** invoke Gradle automatically
3. Missing/stale marker => deterministic failure with remediation:
   - run Gradle build once so containment tasks execute
   - rerun `bear check`

### Non-Gradle (P2)

1. `pr-check` governance still works.
2. `check` fails deterministically when `allowedDeps` present because enforcement cannot be guaranteed.

## Acceptance

1. Non-allowlisted import in impl fails due containment/classpath.
2. Allowlisted allowed dep in impl compiles and checks pass with marker handshake satisfied.
3. Pure-dep add/version changes surface in `pr-check` as boundary expansion (`exit 5`).
4. Impl helper classes are supported (not single-file-only impl model).
5. No duplicate impl compilation by `compileJava`.

