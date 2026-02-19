# BEAR_AGENT.md

Purpose:
- Canonical BEAR agent operating contract for generic backend projects.

## Read In This Order

1. `doc/BEAR_PRIMER.md`
2. `doc/IR_QUICKREF.md`
3. `doc/IR_EXAMPLES.md`
4. `WORKFLOW.md`
5. the feature request

## Hard Rules

1. Do not reverse engineer BEAR binaries (`jar tf`, `javap`, decompiler tools) to infer IR shape.
2. Treat `doc/IR_QUICKREF.md` and `doc/IR_EXAMPLES.md` as the IR source of truth.
3. Do not edit generated files under `build/generated/bear/**`.
4. Use deterministic BEAR gates; no ad-hoc substitute scripts.

## Session Baseline Check

Before planning or editing:
1. Run `git status --short`.
2. If pre-existing changes exist, report them and confirm how to treat them.

## Mandatory BEAR Loop

1. Read request in domain terms.
2. Discover current BEAR structure:
- inspect `spec/*.bear.yaml`
- inspect `bear.blocks.yaml` if present
- inspect generated namespaces and existing `*Impl.java` files
3. Decide whether boundaries change (contract/effects/idempotency/invariants).
4. Apply IR-first updates before implementation edits when boundaries change.
5. Decide block strategy:
- update an existing block when responsibility boundary is unchanged
- create a new block when responsibility implies a distinct authority boundary
6. If decomposition yields multiple governed blocks:
- create/update `bear.blocks.yaml`
- run `--all` command variants as canonical gates
7. Compile/generate after IR changes.
8. Implement only in user-owned implementation/tests.
9. Run canonical gate to `0`.
10. Report deterministic completion summary.

## Generic Decomposition Rules

Split into multiple blocks when responsibilities imply distinct authority boundaries. Common split signals:
- different external ports/effects
- different lifecycle/trigger model (sync path vs async/scheduled/worker)
- different contract ownership/evolution cadence

Keep a single block when work stays within one existing responsibility boundary.

## IR-First Rules

Update IR first if any of these change:
- new external call/reach capability
- new effect port or operation
- contract input/output shape
- idempotency key/store wiring
- invariants (add/remove/relax)

Boundary-expanding change expectation:
- stale baseline can fail with drift/boundary signals before regeneration
- compile/regenerate, implement, rerun gate

## Editable Boundaries

Do not edit:
- `build/generated/bear/**`

Edit:
- `src/main/java/**/<BlockName>Impl.java`
- `src/test/java/**`
- repo-owned IR/docs/scripts

## Canonical Gates

Use wrappers when provided:
- PowerShell: `.\bin\bear-all.ps1`
- Bash: `./bin/bear-all.sh`
- PR/base gate: `.\bin\pr-gate.ps1 <base-ref>` or `./bin/pr-gate.sh <base-ref>`

Direct CLI equivalents:
- single-block: `bear check <ir-file> --project <repoRoot>`
- multi-block: `bear check --all --project <repoRoot>`
- PR/base: `bear pr-check ...`

## Completion Report Template

Report completion in this format:
- `Request summary: <one line>`
- `Block decision: updated=<...> added=<...>`
- `IR delta: <files + boundary notes>`
- `Implementation delta: <files>`
- `Tests delta: <files>`
- `Gate result: <command> => <exit>`
