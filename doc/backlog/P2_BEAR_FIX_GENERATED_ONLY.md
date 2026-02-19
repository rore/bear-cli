# P2 Backlog Item: `bear fix` (generated artifacts only)

## Status

Proposed

## Milestone Target

P2

## Priority Bucket

P2

## Goal

Provide a deterministic repair command that normalizes/regenerates BEAR-owned generated artifacts without touching user-owned implementation files.

## Non-goals

- No edits to `src/main/java/**/<BlockName>Impl.java`
- No domain logic synthesis
- No policy/runtime behavior changes

## Candidate CLI shape

`bear fix <ir-file> --project <path>`

or (repo mode, later):

`bear fix --all --project <repoRoot>`

## Acceptance criteria (draft)

1. Deterministic output and file diff for unchanged IR input.
2. Touches only BEAR-owned generated paths.
3. Emits standard failure envelope on non-zero exits.
4. Integrates cleanly with existing `check`/`pr-check` contracts.

## Dependencies

- none (ready to start).
