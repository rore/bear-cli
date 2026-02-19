# BEAR_AGENT.md

Purpose:
- Canonical BEAR agent operating contract for generic backend projects.

## Read In This Order

1. `doc/BEAR_PRIMER.md`
2. `doc/IR_QUICKREF.md`
3. `doc/IR_EXAMPLES.md`
4. `doc/BLOCK_INDEX_QUICKREF.md`
5. `WORKFLOW.md`
6. the feature request

## Hard Rules

1. Do not reverse engineer BEAR binaries (`jar tf`, `javap`, decompiler tools) to infer IR shape.
2. Treat `doc/IR_QUICKREF.md` and `doc/IR_EXAMPLES.md` as the IR source of truth.
3. Do not edit generated files under `build/generated/bear/**`.
4. Use deterministic BEAR gates; no ad-hoc substitute scripts.
5. If multiple governed blocks or multiple IR files exist, `bear.blocks.yaml` is mandatory.
6. Do not remove `bear.blocks.yaml` to bypass `--all` governance.
7. In greenfield (no `spec/*.bear.yaml`), create IR and run `bear validate` + `bear compile` before writing implementation source files.
8. Do not invent replacement contracts/ports to bypass BEAR generation; implement against generated BEAR request/result/port interfaces.
9. Do not create domain logic classes under `com.bear.generated.*` except user-owned `*Impl.java` files created by BEAR compile.
10. If expected feature files/paths are missing, treat repository state as greenfield or extension based on actual `spec/*.bear.yaml` presence; do not switch to ad-hoc implementation-first mode.
11. If `bear validate`/`bear compile`/`bear check` fails, fix BEAR artifacts and rerun; do not bypass by writing non-BEAR replacement architecture.
12. Prefer the smallest design that satisfies requirements and BEAR constraints.
13. If you add new production architecture (platform/adapters/executors/etc.), include a brief necessity rationale tied to requirements and boundary ownership.
14. If BEAR tooling fails with IO/lock/environment defects, stop and report the tooling failure; do not mutate unrelated IR to fit stale generated outputs.
15. Never add workaround type stubs/classes under `src/main/java/com/bear/generated/**` (for example fake `BigDecimal`); only generated files and user-owned `*Impl.java` are allowed there.

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
3. Classify repo BEAR state from disk:
- `0` IR files: greenfield bootstrap mode
- `1` IR file: single-block mode
- `>=2` IR files: multi-block mode, index required
4. Decide whether boundaries change (contract/effects/idempotency/invariants).
5. Apply IR-first updates before implementation edits when boundaries change.
6. Decide block strategy:
- update an existing block when responsibility boundary is unchanged
- create a new block when responsibility implies a distinct authority boundary
7. If decomposition yields multiple governed blocks:
- create/update `bear.blocks.yaml`
- run `--all` command variants as canonical gates
  - if index validation fails, fix `name`/`ir`/`projectRoot` entries and rerun `check --all`
8. Compile/generate after IR changes.
9. In greenfield bootstrap (`0` IR at start), no feature implementation edits are allowed until at least one `validate` and `compile` succeeds.
10. Implement only after generated contracts exist.
11. Implement only in user-owned implementation/tests.
12. Run canonical gate to `0`.
13. Report deterministic completion summary.

## Generic Decomposition Rules

Split into multiple blocks when responsibilities imply distinct authority boundaries. Common split signals:
- different external ports/effects
- different lifecycle/trigger model (sync path vs async/scheduled/worker)
- different contract ownership/evolution cadence

Keep a single block when work stays within one existing responsibility boundary.
When a prompt says to keep existing behavior unchanged, prefer extending existing blocks unless a new lifecycle/effect boundary is explicitly required.

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
