# P3 Backlog: Broader Boundary-Escape Coverage

## Status

Queued

## Milestone Target

P3

## Priority Bucket

P3

## Goal

Extend deterministic boundary-bypass coverage beyond the current JVM HTTP-focused surfaces to additional external-power paths.

## Scope

- Deterministic checks for direct database usage bypass paths.
- Deterministic checks for direct filesystem usage bypass paths.
- Deterministic checks for direct messaging usage bypass paths.
- Stable finding ordering, repo-relative path rendering, and existing failure-envelope behavior.

## Non-Goals

- No attempt to prove whole-program safety.
- No policy-engine integration.
- No broad static-analysis platform build-out.

## Acceptance Criteria

1. Newly covered bypass paths are explicit, documented, and deterministic.
2. Findings map into stable existing governance or bypass lanes rather than creating ad hoc semantics.
3. Tests pin representative positive and negative cases for each newly covered surface.
