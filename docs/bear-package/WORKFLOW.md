# WORKFLOW.md

Purpose:
- Deterministic operating loop for BEAR in a generic backend project.

## Read In This Order

1. `.bear/agent/doc/BEAR_PRIMER.md`
2. `.bear/agent/doc/IR_QUICKREF.md`
3. `.bear/agent/doc/IR_EXAMPLES.md`
4. `.bear/agent/doc/BLOCK_INDEX_QUICKREF.md`
5. the feature request

Reference boundary:
- use only `.bear/agent/**` guidance plus project-local BEAR artifacts (`spec/*.bear.yaml`, `bear.blocks.yaml`, `build/generated/bear/**` when present).

## Canonical Flows

## Decomposition Protocol (Deterministic)

Default:
- start with exactly one block
- in greenfield, block #2 is allowed only after recording `Decomposition Evidence` with direct spec quotes

Split only when the spec explicitly supports at least one reason:
- different lifecycle/trigger model
- different external effect boundary
- different authority boundary
- different state/idempotency authority

Evidence requirement:
- if decomposition is multi-block, include `Decomposition Evidence` in completion output
- cite exact spec sentence(s) for each split reason and block
- no citation means no split

Anti-patterns:
- do not use a single `action`/opcode router block for unrelated operations unless spec explicitly requires opcode-style API
- do not create one block per operation unless spec explicitly requires separated boundaries

### A) Greenfield Flow (no IR yet)

1. Read request and identify responsibilities.
2. Apply decomposition protocol and record evidence.
3. Create initial `spec/*.bear.yaml`.
4. If multiple governed blocks exist, create `bear.blocks.yaml`.
5. For each touched IR file run:
- `bear validate <ir-file>`
- `bear compile <ir-file> --project <repoRoot>`
6. If IR declares `impl.allowedDeps` and project is Java+Gradle:
- ensure project applies `build/generated/bear/gradle/bear-containment.gradle`
- run Gradle build/test once to write containment marker
7. Run gate:
- single-block mode: `bear check <ir-file> --project <repoRoot>`
- multi-block mode: `bear check --all --project <repoRoot>`
  - if gate is blocked by `build/bear/check.blocked.marker`, clear with `bear unblock --project <repoRoot>` after fixing lock/bootstrap IO
8. Implement in `*Impl.java` and tests only.
9. Re-run check to `0`.

Greenfield hard stop:
- if no IR exists yet, do not write implementation source code first.
- IR -> validate -> compile must happen before implementation edits.
- if generated contracts are missing, compile; do not invent replacement interfaces/classes.
- if repository paths expected by the prompt are missing, this is still not permission for implementation-first fallback.

### B) Extension Flow (existing BEAR repo)

1. Discover existing IR/index/impl state.
2. Apply decomposition protocol and decide update-existing-block vs add-new-block.
3. Apply IR changes first when boundaries change.
4. Compile touched IR files.
5. If generated artifacts are stale or drifted, run `bear fix` for touched IR (or `fix --all` when indexed).
6. If touched IR declares `impl.allowedDeps` and project is Java+Gradle:
- ensure project applies `build/generated/bear/gradle/bear-containment.gradle`
- run Gradle build/test once to write containment marker
7. Run check gate (`check` or `check --all`).
  - if gate is blocked by `build/bear/check.blocked.marker`, clear with `bear unblock --project <repoRoot>` after fixing lock/bootstrap IO
8. Implement and test.
9. Re-run check gate to `0`.
10. For PR/base governance run:
- `bear pr-check <ir-file> --project <repoRoot> --base <ref>`
- or `bear pr-check --all --project <repoRoot> --base <ref>` when indexed

## Block Index Gate

1. Multi-block state requires `bear.blocks.yaml`.
2. In multi-block state, use only:
- `bear check --all --project <repoRoot>`
- `bear pr-check --all --project <repoRoot> --base <ref>`
3. Single-block fallback loops are valid only when exactly one IR file exists and no index exists.
4. Removing `bear.blocks.yaml` to continue via per-IR fallback is invalid.

## Wrapper Policy

Default to direct CLI commands.

If wrappers are shipped and explicitly documented by the project, use them.
Do not assume `bin/bear-all.*` or `bin/pr-gate.*` exists.

## Semantics Policy (v1.2)

Use enforcement-by-construction:
- when semantics are wrapper-enforceable from declared IR boundary data, BEAR enforces them in generated wrappers
- do not push those semantics into impl conventions or suppression comments

Why this matters:
- idempotency is wrapper-enforceable from declared key fields, declared store port, declared outputs
- invariants are wrapper-enforced structural output checks (fresh and replay)

Do not extend BEAR semantics by inference:
- if enforcement requires hidden domain context/policy, it is out of scope
- BEAR is not a business rules engine or transaction framework

Canonical rule:
- Enforce only semantics that are wrapper-checkable from declared inputs/outputs/ports, require no hidden context, are deterministic, and have frozen contracts.

## Failure Triage (Deterministic)

1. `64` usage error:
- fix args/command invocation

2. `2` validation/schema/semantic failure:
- fix IR structure/references/enums/duplicates

3. `3` drift failure:
- prefer `bear fix` (or `fix --all`) to deterministically repair generated artifacts
- alternatively rerun compile for changed IR
- rerun check
- do not run `bear fix` for `TEST_FAILURE` or `IO_ERROR`

4. `6` boundary-bypass or undeclared reach:
- for `CODE=BOUNDARY_BYPASS`:
  - remove direct impl usage from `src/main/**`
  - wire generated entrypoints with non-null ports
  - ensure declared logic-required effect ports are used
  - do not suppress wrapper-owned semantic ports (`// BEAR:PORT_USED ...` is invalid for those)
- declare required port/op in IR
- compile
- route call through generated port interface

5. `4` project tests failed:
- fix implementation/tests
- if `CODE=INVARIANT_VIOLATION`, treat marker details as authoritative semantic failure from wrapper checks (fresh/replay)
- if compiler reports unreachable code in `*Impl.java`, replace the generated stub body entirely (do not append logic below placeholder return/throw)
- verify `*Impl.java` stays in `src/main/java/blocks/<pkg-segment>/impl/` (package `blocks.<pkg-segment>.impl`) unless BEAR compile regenerated a different path

6. `5` boundary expansion (`pr-check`):
- confirm expansion is intentional and reviewable

7. `74` IO/git failure:
- fix path/ref/permission/repo state
- if lock/bootstrap marker exists (`build/bear/check.blocked.marker`), clear with:
  - `bear unblock --project <repoRoot>`

Index troubleshooting:
- `projectRoot` must be a repo-relative directory path.
- repo root is valid and represented as `.`.
- if index fails validation, fix `name`/`ir`/`projectRoot` and rerun `check --all`.
- single-command (`compile`/`check`/`fix`/`pr-check`) identity resolution uses `(ir, projectRoot)` tuple matching when an index is discoverable:
  - `0` matches => single-IR fallback identity mode
  - `1` match => index identity mode (index name authoritative)
  - `>1` matches => deterministic ambiguous-index validation failure
- canonical identity mismatch is validated using frozen normalization (camel split, non-alnum collapse, lowercase token join with `-`).

8. `70` internal failure:
- collect output and report as tool defect

8b. `2` manifest semantic validation failure:
- `CODE=MANIFEST_INVALID` means generated wiring semantic contracts are inconsistent
- regenerate compile artifacts and rerun; do not hand-edit generated wiring as a final fix

9. `74` containment failure (`CONTAINMENT_NOT_VERIFIED` / `CONTAINMENT_UNSUPPORTED_TARGET`):
- if missing/stale marker or missing generated containment script/index:
  - rerun `bear compile`
  - ensure project applies generated containment entrypoint
  - run Gradle build/test once to refresh marker
  - rerun `bear check`
- if unsupported target:
  - use Java+Gradle enforcement path for allowed deps
  - or remove `impl.allowedDeps` and keep governance-only behavior in `pr-check`

Lock and environment troubleshooting:
- If BEAR compile/check fails with file-lock/permission signatures (for example `.zip.lck`, `Access is denied`, generated-file replacement lock), treat it as tooling/environment IO issue first.
- Do not change unrelated IR to match stale generated outputs.
- Do not introduce workaround classes under `com.bear.generated.*`.
- Remediate by:
  - rerunning and letting BEAR apply deterministic Gradle-home policy:
    - external `GRADLE_USER_HOME` set: same path only, with one self-heal + one retry
    - no external `GRADLE_USER_HOME`: isolated home (`<project>/.bear-gradle-user-home`) + one retry, then one fallback attempt via user cache (`<user-home>/.gradle`)
  - ensuring no concurrent gate/test process holds locks
  - rerunning compile/check after lock release
  - if `check` writes `build/bear/check.blocked.marker`, clear it with `bear unblock --project <repoRoot>` after fixing lock/bootstrap cause

## Constraints

- No generated-file edits.
- No silent boundary expansion.
- One deterministic gate determines done/not-done.
- No implementation-first bypass in greenfield mode.
- Prefer minimal sufficient design; avoid unnecessary architecture expansion.
- If new production architecture is introduced, include a short necessity rationale mapped to requirements and BEAR boundaries.
- For extension prompts that keep existing behavior unchanged, prefer extending existing blocks; add new blocks only for distinct lifecycle/effect boundaries.

## Invalid Patterns (Fail the Run)

1. Writing feature classes before creating any `spec/*.bear.yaml`.
2. Implementing custom ports/contracts to replace missing generated BEAR interfaces.
3. Deleting or skipping `bear.blocks.yaml` in multi-block state to force per-IR fallback.

## Completion Report Addendum

If new production architecture was added, include:
- `Architecture rationale: <why required, and which boundary/lifecycle requirement it satisfies>`

If decomposition is multi-block, include:
- `Decomposition Evidence: <block -> split reason -> exact spec citation>`

