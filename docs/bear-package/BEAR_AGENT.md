# BEAR_AGENT.md

Purpose:
- Canonical BEAR agent operating contract for generic backend projects.

## Read In This Order

1. `.bear/agent/doc/BEAR_PRIMER.md`
2. `.bear/agent/doc/IR_QUICKREF.md`
3. `.bear/agent/doc/IR_EXAMPLES.md`
4. `.bear/agent/doc/BLOCK_INDEX_QUICKREF.md`
5. `.bear/agent/WORKFLOW.md`
6. the feature request

## Hard Rules

1. Do not reverse engineer BEAR binaries (`jar tf`, `javap`, decompiler tools) to infer IR shape.
2. Treat `.bear/agent/doc/IR_QUICKREF.md` and `.bear/agent/doc/IR_EXAMPLES.md` as the IR source of truth.
3. Do not edit generated files under `build/generated/bear/**`.
4. Use deterministic BEAR gates; no ad-hoc substitute scripts.
5. If multiple governed blocks or multiple IR files exist, `bear.blocks.yaml` is mandatory.
6. Do not remove `bear.blocks.yaml` to bypass `--all` governance.
7. In greenfield (no `spec/*.bear.yaml`), create IR and run `bear validate` + `bear compile` before writing implementation source files.
8. Do not invent replacement contracts/ports to bypass BEAR generation; implement against generated BEAR request/result/port interfaces.
9. Do not create domain logic classes under `com.bear.generated.*`; generated runtime types live there, while user-owned `*Impl.java` files are created under `blocks.<pkg-segment>.impl`.
10. If expected feature files/paths are missing, treat repository state as greenfield or extension based on actual `spec/*.bear.yaml` presence; do not switch to ad-hoc implementation-first mode.
11. If `bear validate`/`bear compile`/`bear check` fails, fix the actual failing cause and rerun; do not bypass by writing non-BEAR replacement architecture.
12. Prefer the smallest design that satisfies requirements and BEAR constraints.
13. If you add new production architecture (platform/adapters/executors/etc.), include a brief necessity rationale tied to requirements and boundary ownership.
14. If BEAR tooling fails with IO/lock/environment defects, stop and report the tooling failure; do not mutate unrelated IR to fit stale generated outputs.
15. On `IO_ERROR`/`WINDOWS_FILE_LOCK`, do not rename blocks/IR files, do not alter filesystem ACL/permissions, and do not perform manual generated-file surgery as a workaround.
16. Retry budget for tooling/lock defects: one deterministic retry after setting repo-local `GRADLE_USER_HOME`; if failure persists, stop and report blocker details.
17. Never add workaround type stubs/classes under `src/main/java/com/bear/generated/**` (for example fake `BigDecimal`); generated classes there are BEAR-owned.
18. If implementation needs a new library, declare it in `block.impl.allowedDeps` (IR-first); do not silently add impl classpath reach.
19. For IR with `impl.allowedDeps` on Java+Gradle projects, ensure the project applies generated containment entrypoint and run Gradle once before relying on `bear check`.
20. For generated `*Impl.java`, replace the generated stub method body; do not keep the placeholder return/throw and append logic below it.
21. Canonical user-owned implementation path is `src/main/java/blocks/<pkg-segment>/impl/<BlockName>Impl.java` and package `blocks.<pkg-segment>.impl`; do not relocate `*Impl.java` to `src/main/java/com/bear/generated/**`.
22. In greenfield, default to exactly one block; creating block #2 requires `Decomposition Evidence` with direct spec quotes before generation.
23. `bear fix` is drift-repair only; do not run `bear fix` for `TEST_FAILURE` or `IO_ERROR`.
24. In `src/main/**`, do not import or instantiate governed `*Impl` classes directly; wire through generated entrypoints under `com.bear.generated.*`.
25. Do not wire governed entrypoints with top-level `null` port arguments in production code.
26. For each logic-required effect port, impl code must use the corresponding port parameter directly, pass it through to a helper call, or explicitly suppress with exact same-file line `// BEAR:PORT_USED <portParamName>`; wrapper-owned semantic ports must not be used/suppressed from impl code.
27. If `check` writes `build/bear/check.blocked.marker` (`PROJECT_TEST_LOCK`/`PROJECT_TEST_BOOTSTRAP`), stop feature edits and clear with `bear unblock --project <path>` after fixing environment.
28. Agent guidance must remain package-local: rely on `.bear/agent/**` plus project-local BEAR artifacts (`spec/*.bear.yaml`, `bear.blocks.yaml`, `build/generated/bear/**`), not non-shipped repo docs.

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
   - include allowed-deps allowlist changes (`block.impl.allowedDeps`) as boundary-surface changes
5. Apply IR-first updates before implementation edits when boundaries change.
6. Decide block strategy using the decomposition protocol in this file and capture decomposition evidence.
7. If decomposition yields multiple governed blocks:
- create/update `bear.blocks.yaml`
- run `--all` command variants as canonical gates
  - if index validation fails, fix `name`/`ir`/`projectRoot` entries and rerun `check --all`
8. Compile/generate after IR changes.
   - when IR contains `impl.allowedDeps`:
     - confirm project applies `build/generated/bear/gradle/bear-containment.gradle`
     - run Gradle build/test once to refresh containment marker
9. If generated artifacts are stale/drifted, run `bear fix` (or `fix --all` when indexed).
10. In greenfield bootstrap (`0` IR at start), no feature implementation edits are allowed until at least one `validate` and `compile` succeeds.
11. Implement only after generated contracts exist.
12. Implement only in user-owned implementation/tests.
13. Run canonical gate to `0`.
14. Report deterministic completion summary.

## Generic Decomposition Rules

Default:
- start with exactly one block

You may split into additional blocks only when an explicit requirement in the spec supports at least one split reason:
- different lifecycle/trigger model:
  - for example sync request/response versus async worker/scheduled/queue-triggered behavior
- different external effect boundary:
  - capability sets differ materially (for example external service call/event sink versus local-only path)
- different authority boundary:
  - caller trust/permission classes differ (for example admin-only versus user/system paths)
- different state/idempotency authority:
  - invariants, idempotency domain/key policy, or state ownership differs by responsibility

Evidence requirement:
- every multi-block decomposition must include a `Decomposition Evidence` section in the completion report
- list each block and the exact spec sentence(s) that triggered the split reason(s)
- if you cannot cite spec text for a split reason, do not split

Anti-pattern ban:
- do not implement a multi-operation system as a single `action`/opcode router block that dispatches unrelated operations by enum/string
- if an opcode-style API is explicitly required by spec, cite that requirement in `Decomposition Evidence`

No operation-per-block by default:
- do not create one block per operation unless spec text explicitly requires separate lifecycle/effect/authority/state boundaries

Stability rule:
- when behavior is unchanged and no new split reason is introduced, prefer extending existing blocks

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

Preferred user-owned impl location:
- `src/main/java/blocks/<pkg-segment>/impl/<BlockName>Impl.java` (package `blocks.<pkg-segment>.impl`)

## Canonical Gates

Use direct CLI commands as canonical defaults:
- single-block: `bear check <ir-file> --project <repoRoot>`
- multi-block: `bear check --all --project <repoRoot>`
- clear check-only block marker: `bear unblock --project <repoRoot>`
- PR/base: `bear pr-check ...`
- repair generated artifacts: `bear fix <ir-file> --project <repoRoot>` / `bear fix --all --project <repoRoot>`
- allowed-deps enforcement prereq (Java+Gradle): apply generated containment script and run Gradle once so `build/bear/containment/applied.marker` is fresh

Wrappers are optional project policy:
- if a repo explicitly ships wrappers and documents them as canonical, use them
- do not assume `bin/bear-all.*` or `bin/pr-gate.*` exists

## Completion Report Template

Report completion in this format:
- `Request summary: <one line>`
- `Block decision: updated=<...> added=<...>`
- `Decomposition evidence: <single-block rationale OR per-block spec citations>`
- `IR delta: <files + boundary notes>`
- `Implementation delta: <files>`
- `Tests delta: <files>`
- `Gate result: <command> => <exit>`

