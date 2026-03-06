# P3 Backlog: Capability Templates

## Status

Queued

## Milestone Target

P3

## Priority Bucket

P3

## Goal

Provide deterministic starter packs that generate common capability scaffolding so agents and developers can start from governed templates instead of ad hoc boundary code.

## Scope

- Template packs that generate ports, effects scaffolding, invariants, structural tests, and standard stubs.
- Deterministic generation only; templates must produce reproducible BEAR-owned output.
- Template selection and emitted artifacts must align with existing IR and compile ownership rules.

## Non-Goals

- No template-specific runtime framework coupling in the kernel.
- No agent-specific generation behavior.
- No expansion of IR semantics solely to support template convenience.

## Acceptance Criteria

1. Template packs generate deterministic scaffolding for at least one clearly useful capability shape.
2. Generated artifacts preserve the existing two-tree ownership model.
3. Template output integrates with existing compile, check, and structural-test contracts.
