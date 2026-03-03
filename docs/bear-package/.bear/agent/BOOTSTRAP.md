# BOOTSTRAP.md

Purpose:
- Minimal BEAR startup contract.
- Always-load file for agent routing and non-negotiables.

Bootstrap guardrails:
- `BOOTSTRAP.md` must stay under 200 lines.
- If new detail is needed, add it to `CONTRACTS.md`, `TROUBLESHOOTING.md`, `REPORTING.md`, or `ref/IR_REFERENCE.md`.
- `BOOTSTRAP.md` contains routing and concise contract signals only.

## If You Remember Nothing Else

1. Determine mode from disk first:
- greenfield: `0` files in `spec/*.bear.yaml`
- single block: `1` IR file
- multi-block: `>=2` IR files, `bear.blocks.yaml` required
- if `bear.blocks.yaml` exists, treat as multi-block regardless of IR file count
2. Default canonical IR dir is `spec/` unless repo policy declares otherwise.
3. IR files MUST be created under canonical IR dir; never create IR at repo root.
4. Create canonical IR dir before writing the first IR file.
5. Write `bear.blocks.yaml` only after all referenced IR files exist.
6. IR-first always; in greenfield run `bear validate` and `bear compile` before implementation edits.
7. Never edit generated artifacts under `build/generated/bear/**`.
8. Implement against generated BEAR contracts/ports only.
9. Multi-block governance requires index + `--all`; do not bypass by deleting `bear.blocks.yaml`.
10. `pr-check` base must be merge-base against target branch (or provided base SHA).
11. Completion requires both gates:
- `bear check --all --project <repoRoot>`
- `bear pr-check --all --project <repoRoot> --base <ref>`
12. For expected greenfield baseline `BOUNDARY_EXPANSION_DETECTED`, do not force green; report `WAITING_FOR_BASELINE_REVIEW` per `.bear/agent/REPORTING.md`.
13. If spec conflicts with explicit enforcement/contract rules, stop and escalate unless spec explicitly authorizes rule changes.
14. Do not self-edit build/policy/runtime harness files unless explicitly instructed:
- `build.gradle`, `settings.gradle`, `gradlew`, `gradlew.bat`, `.bear/**`, `bin/bear*`
15. Completion output must follow `.bear/agent/REPORTING.md`.

## Routing Map

Always read:
1. `.bear/agent/BOOTSTRAP.md`

Read on demand:
1. IR authoring rules -> `.bear/agent/ref/IR_REFERENCE.md`
2. Multi-block index syntax -> `.bear/agent/ref/BLOCK_INDEX_QUICKREF.md`
3. Normative policy -> `.bear/agent/CONTRACTS.md`
4. Gate diagnosis/remediation -> `.bear/agent/TROUBLESHOOTING.md`
5. Completion report schema -> `.bear/agent/REPORTING.md`
6. First-time concept primer -> `.bear/agent/ref/BEAR_PRIMER.md`

## Agent Start Checklist

1. Run baseline status check: `git status --short`
2. Inspect BEAR state from disk:
- `spec/*.bear.yaml`
- `bear.blocks.yaml` (if present)
- generated namespaces and existing `*Impl.java`
3. Classify mode: greenfield / single-block / multi-block
4. Detect whether repo expects index mode before IR creation:
- workflow/scripts/docs indicate canonical `check --all` / `pr-check --all` usage -> index-required mode
- if index-required, create `bear.blocks.yaml` after all referenced IR files exist
5. Decide if boundary changes are needed (contract/effects/idempotency/invariants/allowedDeps).
6. Choose smallest valid decomposition using `DECOMPOSITION_DEFAULT` and `DECOMPOSITION_SPLIT_TRIGGERS`.
7. Before gates, run quick checks:
- `rg -n "TODO: replace this entire method body|Do not append logic below this placeholder return" src/main/java/blocks`
- `rg -n "implements\\s+.*Port" src/main/java`

## Mandatory Operating Loop

1. Read feature request in domain terms.
2. Discover BEAR structure from working tree.
3. Apply IR changes first when boundaries change.
4. After writing IR, verify exact IR path with `Test-Path`.
5. Validate and compile IR before implementation:
- `bear validate <ir-file>`
- `bear compile <ir-file> --project <repoRoot>` or `bear compile --all --project <repoRoot>`
6. Index preflight before `compile --all`:
- verify all `ir:` paths referenced by `bear.blocks.yaml` exist
- if any missing, stop and fix index/IR paths first
7. If generated artifacts drift, repair deterministically:
- `bear fix <ir-file> --project <repoRoot>` or `bear fix --all --project <repoRoot>`
8. Implement only in user-owned sources/tests.
9. Run completion gates:
- `bear check --all --project <repoRoot>`
- `bear pr-check --all --project <repoRoot> --base <ref>`
10. Report results using `.bear/agent/REPORTING.md`.

## Always-On Rules

1. Do not reverse engineer BEAR binaries to infer IR shape.
2. Treat `.bear/agent/ref/IR_REFERENCE.md` as IR source of truth.
3. Do not edit `build/generated/bear/**`.
4. Greenfield hard stop: no implementation edits before successful validate+compile.
5. Keep generated-port implementations under governed roots only (`src/main/java/blocks/**`).
6. Do not use action/command enum multiplexers for unrelated operations unless the spec explicitly defines that router contract.
7. Use deterministic BEAR commands; do not replace with ad-hoc scripts.
8. For expected `BOUNDARY_EXPANSION_DETECTED`, do not force green; report per `.bear/agent/REPORTING.md`.
9. Greenfield contract source is current IR + fresh generated sources in `build/generated/bear/**`; do not mine stale `build*` artifacts.
10. Hard-stop on BEAR tooling anomalies:
- any `INTERNAL_ERROR` / exit `70`
- compiler crash signatures (`internal: INTERNAL_ERROR:` or `Exception in thread`)
- repeated timeout exit `124` after one immediate unchanged retry
11. Retry accounting for timeout `124`:
- counter is per exact command string
- retry is immediate and unchanged (no edits/flags/env changes between attempts)
12. Forbidden compile-pass workarounds:
- changing semantic IR intent only to pass compile (for example `idempotency.mode: none -> use`)
- widening effects/idempotency store usage outside spec intent
- injecting compatibility sources under `src/main/java/com/bear/generated/**`
13. Do not duplicate impl/exception shims under `_shared` only to satisfy classpath/containment lanes.
14. If anomaly criteria hold, stop and report; do not continue workaround edits.

## POLICY_SCOPE_MISMATCH

Deterministic escalation condition:
1. If `finding.path` matches `_shared/state/**` AND `ruleId` is one of `{SHARED_PURITY_VIOLATION, SCOPED_IMPORT_POLICY_BYPASS}`, classify as policy/tool anomaly.
2. Stop and escalate; do not alter implementation semantics as workaround.
3. Report blocker under `OTHER` in `.bear/agent/REPORTING.md`.

## AGENT_PACKAGE_PARITY_PRECONDITION

Before implementation work starts, these routed files MUST exist:
1. `.bear/agent/CONTRACTS.md`
2. `.bear/agent/TROUBLESHOOTING.md`
3. `.bear/agent/REPORTING.md`
4. `.bear/agent/ref/IR_REFERENCE.md`

If any required file is missing:
1. classify as process/tool anomaly
2. stop immediately and escalate
3. do not continue implementation until parity is restored

## GREENFIELD_HARD_STOP

If `spec/*.bear.yaml` is empty:
1. next action MUST be creating IR files under canonical `spec/`
2. run `bear validate` and `bear compile` before implementation edits
3. do not continue to implementation until this precondition is satisfied

## INDEX_REQUIRED_PREFLIGHT

If index-required mode is inferred from workflow/docs:
1. `bear.blocks.yaml` MUST be created after IR files exist and before `--all` gates
2. if preflight is unmet, stop and fix index/IR preconditions first
3. do not continue to implementation or gates while preflight is unmet

## GREENFIELD_PR_CHECK_POLICY

1. In a greenfield baseline PR, `bear pr-check` may expectedly fail with `BOUNDARY_EXPANSION_DETECTED` for newly introduced blocks/contracts/ports.
2. Do not shrink IR/contracts to force green.
3. Report deterministic baseline-waiting semantics from `.bear/agent/REPORTING.md` and stop for boundary review.

## DECOMPOSITION_DEFAULT

Canonical decomposition dimensions:
1. `state_domain_same|state_domain_split`
2. `effects_read_only|effects_write`
3. `idempotency_same|idempotency_split`
4. `lifecycle_same|lifecycle_split`
5. `authority_same|authority_split`

Derivation rules:
1. `effects_read_only` means no mutation ports/ops are used by operations in a group.
2. `effects_write` means any mutation port/op is used by any operation in a group.
3. `idempotency_same` applies only when grouped operations share identical key shape and identical store tuple `port|getOp|putOp`.
4. `idempotency_n/a` applies only when no operation in the decomposition is idempotent.
5. Grouped mode is allowed only when all applicable dimensions are `_same` and none are `_split`.
6. In grouped mode, keep one IR file per block and model grouped operations structurally via `block.operations` inside that block.
7. Endpoint count alone is never a split trigger.

## DECOMPOSITION_SPLIT_TRIGGERS

Allowed canonical trigger names for `trigger:<canonical_name>`:
1. `state_domain_split`
2. `effects_split`
3. `idempotency_split`
4. `lifecycle_split`
5. `authority_split`
6. `operation_multiplexer_anti_pattern`

`operation_multiplexer_anti_pattern` applies only to unrelated mega-router/switch designs; it does not mean "multiple operations implies split".
Non-whitelisted trigger names are invalid in reports.

## GREENFIELD_ARTIFACT_SOURCE_RULE

In greenfield mode:
1. Contract source of truth is current IR plus fresh `build/generated/bear/**` artifacts from this run.
2. Do not inspect stale `build*` outputs or run `javap` on prior class directories.
3. If violated, classify as process/tool anomaly, stop, and escalate (`Gate blocker: OTHER`).

## Done Gate Contract

Required evidence before completion:
1. `bear check --all --project <repoRoot> => 0`
2. `bear pr-check --all --project <repoRoot> --base <ref> => 0`
3. Completion report follows `.bear/agent/REPORTING.md` exactly.
