# BOOTSTRAP.md

Purpose:
- Minimal BEAR startup contract.
- Always-load file for agent routing and non-negotiables.

Bootstrap guardrails:
- `BOOTSTRAP.md` must stay under 200 lines.
- If new detail is needed, add it to `CONTRACTS.md`, `TROUBLESHOOTING.md`, `REPORTING.md`, or `ref/IR_REFERENCE.md`.
- `BOOTSTRAP.md` contains routing and concise contract signals only; no full restatement of deep policy/triage text.

## If You Remember Nothing Else

1. Determine mode from disk first:
- greenfield: `0` files in `spec/*.bear.yaml`
- single block: `1` IR file
- multi-block: `>=2` IR files, `bear.blocks.yaml` required
- If `bear.blocks.yaml` exists, treat as multi-block regardless of IR file count.
2. IR files MUST be created under `spec/` (or repo-declared canonical IR directory). Never create IR at repo root.
3. IR-first always. In greenfield, run `bear validate` and `bear compile` before any implementation edits.
4. Never edit generated artifacts under `build/generated/bear/**`.
5. Implement against generated BEAR contracts/ports only; do not invent substitute interfaces.
6. Do not encode multiple externally visible operations as an action/command enum multiplexer unless the spec explicitly defines a command router.
7. Multi-block governance requires index + `--all`; do not bypass by deleting `bear.blocks.yaml`.
8. `pr-check` base must be merge-base against target branch (or provided base SHA). Do not use `--base HEAD` unless explicitly instructed.
9. Completion requires both gates green:
- `bear check --all --project <repoRoot>`
- `bear pr-check --all --project <repoRoot> --base <ref>`
10. For expected `BOUNDARY_EXPANSION_DETECTED`, do not attempt to force green; report `BLOCKED` with required next action.
11. If a spec requirement conflicts with an explicit repo enforcement rule or BEAR contract rule, stop and escalate unless the spec explicitly authorizes changing that rule.
12. Do not self-edit build/policy/runtime harness files unless explicitly instructed:
- `build.gradle`, `settings.gradle`, `gradlew`, `gradlew.bat`, `.bear/**`, `bin/bear*`
13. Completion output must follow `.bear/agent/REPORTING.md`.

## Routing Map

Always read:
1. `.bear/agent/BOOTSTRAP.md`

Read on demand:
1. IR authoring/validation rules -> `.bear/agent/ref/IR_REFERENCE.md`
2. Multi-block index syntax -> `.bear/agent/ref/BLOCK_INDEX_QUICKREF.md`
3. Normative policy/wiring/boundaries -> `.bear/agent/CONTRACTS.md`
4. Gate failure diagnosis/remediation -> `.bear/agent/TROUBLESHOOTING.md`
5. Completion report schema -> `.bear/agent/REPORTING.md`
6. Concept primer for first-time BEAR users -> `.bear/agent/ref/BEAR_PRIMER.md`

## Agent Start Checklist

1. Run baseline repo status check:
- `git status --short`
2. Inspect BEAR state from disk:
- `spec/*.bear.yaml`
- `bear.blocks.yaml` (if present)
- generated namespaces and existing `*Impl.java`
3. Classify mode:
- greenfield / single-block / multi-block
4. Detect whether repo expects index mode before IR creation:
- workflow/scripts/docs indicate canonical `check --all` / `pr-check --all` usage -> index-required mode
- if index-required, create `bear.blocks.yaml` immediately after first IR file creation
5. Decide if boundary changes are needed (contract/effects/idempotency/invariants/allowedDeps).
6. Choose smallest valid decomposition:
- default one block
- add block only with explicit spec evidence for lifecycle/effect/authority/state split reasons
- Decomposition signals are defined in `CONTRACTS.md` (single source).
7. Before gates, run scaffold and boundary quick checks:
- `rg -n "TODO: replace this entire method body|Do not append logic below this placeholder return" src/main/java/blocks`
- `rg -n "implements\\s+.*Port" src/main/java`
- never leave a generated placeholder return before real logic; replace generated stub body fully
- all generated-port implementations must remain under governed roots (`src/main/java/blocks/**`)
- `_shared` must not import app packages; app packages must not implement generated ports

## Mandatory Operating Loop

1. Read feature request in domain terms.
2. Discover BEAR structure from current working tree.
3. Apply IR changes first when boundaries change.
4. After writing IR, print and verify the exact IR path:
- `Test-Path <ir-file>` must be true
- validate that exact created path (do not validate a different file)
5. Validate and compile IR before implementation:
- `bear validate <ir-file>`
- `bear compile <ir-file> --project <repoRoot>` or `bear compile --all --project <repoRoot>`
6. If generated artifacts drift, repair deterministically:
- `bear fix <ir-file> --project <repoRoot>` or `bear fix --all --project <repoRoot>`
7. Implement only in user-owned sources/tests.
8. Run completion gates:
- `bear check --all --project <repoRoot>`
- `bear pr-check --all --project <repoRoot> --base <ref>`
9. Containment metadata diagnosis trigger (deterministic, bounded):
- Do not interpret containment metadata preemptively.
- Inspect `build/generated/bear/config/containment-required.json` only when `bear check` fails with containment/classpath signatures.
- Run one repair compile: `bear compile --all --project <repoRoot>`, then rerun the same `bear check`.
- If `bear check` still fails with the same containment/classpath signature, classify `CONTAINMENT_METADATA_MISMATCH`, escalate, and stop (no harness/build edits).
10. Report results using `.bear/agent/REPORTING.md`.

## Always-On Rules

1. Do not reverse engineer BEAR binaries to infer IR shape.
2. Treat `.bear/agent/ref/IR_REFERENCE.md` as IR source of truth.
3. Do not edit `build/generated/bear/**`.
4. Greenfield hard stop: no implementation edits before successful validate+compile.
5. Implement against generated BEAR ports/contracts only.
6. IR files must live under `spec/` (or repo-declared canonical IR directory), and validation must target the exact created IR path.
7. Keep governed execute-path logic inside governed roots.
8. Do not implement generated `com.bear.generated.*Port` interfaces outside governed roots; never place generated-port implementations under app packages.
9. Do not use action/command enum multiplexers for multiple external operations unless the spec explicitly defines that router contract.
10. Multi-block repos must keep `bear.blocks.yaml`; no bypass by deletion.
11. Use deterministic BEAR commands; do not replace with ad-hoc scripts.
12. `pr-check` base must be merge-base/target-base SHA, not `HEAD` unless explicitly instructed.
13. Never leave generated placeholder returns before real logic; replace generated stub bodies fully before gates.
14. Do not self-edit build/policy/runtime harness files unless explicitly instructed (`build.gradle`, `settings.gradle`, `gradlew`, `gradlew.bat`, `.bear/**`, `bin/bear*`).
15. Do not bypass containment by moving impl seams to alternate roots, creating duplicate shim copies in `_shared`, or overriding containment excludes.
16. `_shared` must not depend on app packages, and app packages must not implement generated ports.
17. Lock/IO retries are bounded: perform only fixed retry actions and stop after 2 failed retries with escalation evidence.
18. If gates fail, fix root cause and rerun; do not bypass with alternate architecture.
19. Use `bear fix` for generated drift repair only; never for test or IO failures.
20. Do not claim done without both repo-level gates green.
21. For expected `BOUNDARY_EXPANSION_DETECTED`, do not attempt to force green; mark run `BLOCKED` with required governance next action.

## Done Gate Contract

Required evidence before completion:
1. `bear check --all --project <repoRoot> => 0`
2. `bear pr-check --all --project <repoRoot> --base <ref> => 0`
3. Completion report follows `.bear/agent/REPORTING.md` exactly.
