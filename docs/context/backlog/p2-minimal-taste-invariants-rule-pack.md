# P2 Backlog: Minimal Taste-Invariants Rule Pack

## Status

Queued

## Milestone Target

P2

## Priority Bucket

P2

## Goal

Add a small deterministic rule pack for obviously bad structural patterns without expanding BEAR into subjective style policing.

## Scope

- Deterministic layout and naming invariants for BEAR-owned files.
- Size and structure constraints on generated zones.
- Forbidden dependency edges between selected packages or modules where the contract is BEAR-owned and machine-checkable.
- Stable failure mapping and remediation for any new findings.

## Non-Goals

- No broad style or formatting linting.
- No repo-wide taste enforcement for user-owned implementation code beyond clearly governed boundaries.
- No subjective heuristics that are hard to explain or reproduce.

## Decision Locks

1. Rules must be deterministic, machine-checkable, and repo-local.
2. Rules should prefer BEAR-owned or generated surfaces before touching user-owned code.
3. Findings must fit existing deterministic failure-envelope behavior.
4. This feature is a narrow rule pack, not a general lint framework.

## Candidate Initial Rules

1. Generated BEAR-owned file layout and naming invariants.
2. Constraints on generated zone sprawl or unexpected file structure under `build/generated/bear/**`.
3. Clearly invalid dependency edges between guarded packages or modules where BEAR already owns the contract boundary.

## Acceptance Criteria

1. The initial rule pack is small, explicit, and deterministic.
2. New findings are stable in ordering, path rendering, and remediation text.
3. Existing runtime contracts and exit taxonomy remain unchanged unless an already existing lane is reused.
4. Docs and tests pin the intended rule scope so the feature does not drift into subjective policy.
