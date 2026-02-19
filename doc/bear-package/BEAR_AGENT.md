# BEAR_AGENT.md (Package Source)

Purpose:
- Canonical BEAR agent contract distributed to adopter repos as `BEAR_AGENT.md`.
- Demo/adopter copies should be synced from this file.

## Read In This Order

1. `doc/BEAR_PRIMER.md`
2. `WORKFLOW.md`
3. the feature request

## Session Baseline Check

Before planning or editing:
1. Run `git status --short`.
2. If pre-existing changes exist, explicitly report them and confirm whether to treat them as baseline before proceeding.

## Mandatory BEAR Loop

1. Read the feature request in domain terms.
2. Discover existing BEAR structure:
- inspect `spec/*.bear.yaml`
- inspect `bear.blocks.yaml` if present
- inspect generated package namespaces and existing `*Impl.java` files
3. Decide if boundary/contract/effect changes are required.
4. If required, update IR before implementation edits.
5. Decide create-vs-update block:
- update an existing block when feature fits same contract responsibility and boundary
- create a new block when feature introduces a new responsibility/contract boundary
6. If no IR exists yet, create the first `spec/*.bear.yaml` before expecting gate success.
7. Run canonical gate command.
8. Fix failures by category (schema/validation, drift, boundary signal, tests).
9. Report exactly what changed:
- IR and boundary deltas
- implementation files
- tests and gate result

## IR-First Decision Rules

Update IR first if any of these are introduced or changed:
- new external call/reach
- new capability port or operation
- contract input/output shape changes
- persistence interaction changes
- new invariant or invariant relaxation/removal

If unsure:
- inspect IR and confirm capability already exists before writing impl code.

Boundary-expanding change expectation:
- after IR update and before regeneration, `bear-all` can fail with drift/boundary signals on stale generated baseline
- this is expected; compile/regenerate, implement, then rerun gate to green

## Edit Boundaries

Do not edit generated files:
- `build/generated/bear/**`

Editable locations:
- implementation: `src/main/java/**/<BlockName>Impl.java`
- tests: `src/test/java/**`
- IR/docs/scripts in repo-owned paths

## Canonical Command

Use one command as the done gate:
- PowerShell: `.\bin\bear-all.ps1`
- Bash: `./bin/bear-all.sh`

Interpretation:
- `0` => done
- `3` => drift (regen/update flow required)
- `4` => test/verification failure
- `2` => IR/schema/semantic issue
