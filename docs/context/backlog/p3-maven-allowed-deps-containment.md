# P3 Backlog: Maven Support for Allowed Deps Containment

## Summary
Add parity support for allowed-deps containment on Java+Maven projects, matching the existing Java+Gradle governance/enforcement model.

## Goal
When IR declares `block.impl.allowedDeps`, Maven projects should have:
1. deterministic containment wiring generation
2. deterministic applied-marker handshake
3. `bear check` enforcement with actionable failure semantics
4. unchanged `pr-check` governance semantics

## Scope
- In scope:
  - Maven target detection contract for containment enforcement
  - generated Maven entrypoint/wiring artifact(s)
  - marker location/format/hash handshake equivalent to Gradle flow
  - deterministic `bear check` failure paths for missing/stale wiring
- Out of scope:
  - non-Java build tools
  - runtime purity proof
  - cross-language dependency modeling

## Proposed Contract (Draft)
1. `bear compile` emits Maven containment artifacts under `build/generated/bear/...`.
2. project integrates one canonical Maven include hook (to be finalized).
3. Maven build writes containment marker under `build/bear/containment/applied.marker` with hash of containment index.
4. `bear check` verifies marker/hash (no tool invocation from CLI).
5. If `allowedDeps` exists and Maven wiring is missing/stale, `bear check` fails deterministically with remediation.

## Acceptance Criteria
1. Non-allowlisted library usage in impl fails under Maven containment compile path.
2. Allowlisted allowed dep compiles successfully.
3. Marker missing/stale yields deterministic `bear check` failure.
4. Fresh marker yields `bear check: OK`.
5. `pr-check` allowed-deps delta behavior remains unchanged.

## Notes
- This backlog item is optional future expansion; current enforced target remains Java+Gradle for P2.


