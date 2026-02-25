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
2. IR-first always. In greenfield, run `bear validate` and `bear compile` before any implementation edits.
3. Never edit generated artifacts under `build/generated/bear/**`.
4. Implement against generated BEAR contracts/ports only; do not invent substitute interfaces.
5. Multi-block governance requires index + `--all`; do not bypass by deleting `bear.blocks.yaml`.
6. Completion requires both gates green:
- `bear check --all --project <repoRoot>`
- `bear pr-check --all --project <repoRoot> --base <ref>`
7. Completion output must follow `.bear/agent/REPORTING.md`.

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
4. Decide if boundary changes are needed (contract/effects/idempotency/invariants/allowedDeps).
5. Choose smallest valid decomposition:
- default one block
- add block only with explicit spec evidence for lifecycle/effect/authority/state split reasons

## Mandatory Operating Loop

1. Read feature request in domain terms.
2. Discover BEAR structure from current working tree.
3. Apply IR changes first when boundaries change.
4. Validate and compile IR before implementation:
- `bear validate <ir-file>`
- `bear compile <ir-file> --project <repoRoot>` or `bear compile --all --project <repoRoot>`
5. If generated artifacts drift, repair deterministically:
- `bear fix <ir-file> --project <repoRoot>` or `bear fix --all --project <repoRoot>`
6. Implement only in user-owned sources/tests.
7. Run completion gates:
- `bear check --all --project <repoRoot>`
- `bear pr-check --all --project <repoRoot> --base <ref>`
8. Report results using `.bear/agent/REPORTING.md`.

## Always-On Rules

1. Do not reverse engineer BEAR binaries to infer IR shape.
2. Treat `.bear/agent/ref/IR_REFERENCE.md` as IR source of truth.
3. Do not edit `build/generated/bear/**`.
4. Greenfield hard stop: no implementation edits before successful validate+compile.
5. Implement against generated BEAR ports/contracts only.
6. Keep governed execute-path logic inside governed roots.
7. Do not implement generated `com.bear.generated.*Port` interfaces outside governed roots.
8. Multi-block repos must keep `bear.blocks.yaml`; no bypass by deletion.
9. Use deterministic BEAR commands; do not replace with ad-hoc scripts.
10. If gates fail, fix root cause and rerun; do not bypass with alternate architecture.
11. Use `bear fix` for generated drift repair only; never for test or IO failures.
12. Do not claim done without both repo-level gates green.

## Done Gate Contract

Required evidence before completion:
1. `bear check --all --project <repoRoot> => 0`
2. `bear pr-check --all --project <repoRoot> --base <ref> => 0`
3. Completion report follows `.bear/agent/REPORTING.md` exactly.
