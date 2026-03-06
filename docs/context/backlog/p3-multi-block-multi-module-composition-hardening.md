# P3 Backlog: Multi-Block and Multi-Module Composition Hardening

## Status

Queued

## Milestone Target

P3

## Priority Bucket

P3

## Goal

Strengthen BEAR's deterministic behavior for repos that span multiple blocks and multiple modules without weakening boundary visibility or drift reporting.

## Scope

- Cross-block dependency constraints where composition should remain governed.
- Repo-wide drift reporting improvements for multi-root or multi-module layouts.
- Repo-wide boundary-expansion reporting that remains stable across composed projects.

## Non-Goals

- No system-level IR redesign in this slice.
- No orchestration platform or deployment modeling.
- No relaxation of existing deterministic compile, check, or pr-check contracts.

## Acceptance Criteria

1. Composition rules are explicit and deterministic across supported repo layouts.
2. Repo-wide reporting remains stable, ordered, and reproducible.
3. Tests cover representative multi-block and multi-module cases, including cross-root governance visibility.
