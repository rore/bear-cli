# TROUBLESHOOTING.md

Purpose:
- Deterministic troubleshooting router for BEAR gate failures.
- Consult only when command output is non-zero or blocked.


## Agent JSON-First Protocol

When running machine loops with `--agent`:
1. Parse only stdout JSON; do not parse human prose output for control decisions.
2. On failure, use `nextAction` first.
3. If `nextAction.commands` exists, execute only those commands.
4. If `nextAction` is `null`, route by `(category, failureCode, ruleId|reasonKey)` and escalate as required.
5. Treat stderr as evidence only.

## Triage Router

1. Usage/args issue (`64`) -> fix invocation/arguments and rerun same command.
2. IR validation/schema/semantic issue (`2`) -> fix IR or policy syntax, then rerun validate/check.
3. Drift failure (`3`) -> regenerate deterministically (`compile` or `fix`), then rerun check.
4. Boundary bypass (`7`) -> move wiring/logic back into governed seams/roots, then rerun.
5. Undeclared reach or strict hygiene (`6`) -> declare boundary or remediate unexpected path, then rerun.
6. Project tests failed (`4`) -> fix implementation/tests; rerun check.
7. PR boundary expansion / `BOUNDARY_EXPANSION_DETECTED` (`5`) -> confirm intentional expansion and review process.
8. `IO_LOCK` / IO/git/runtime environment (`74`) -> resolve repo/path/lock/bootstrap and rerun.
9. Schema/path mismatch or missing routed docs -> suspect stale package sync, re-sync package, verify `.bear/agent/**` tree parity, rerun.
10. `SPEC_POLICY_CONFLICT` -> apply conflict checklist and escalate if criteria hold.
11. `CONTAINMENT_METADATA_MISMATCH` -> apply bounded compile-once repair flow only for failing `check` with containment/classpath signatures.
12. Internal failure (`70`) -> capture output and report tool defect.

## TOOLING_ANOMALY_HARD_STOP

Stop immediately (no workaround edits) when any is true:
1. command exits `70` (`INTERNAL_ERROR`)
2. output contains crash signatures: `internal: INTERNAL_ERROR:` or `Exception in thread`
3. same command exits `124` twice in a row (only one immediate unchanged retry allowed)

Retry accounting contract:
1. timeout retries are per exact command string only
2. retry must be immediate and unchanged (no file edits, no arg/env variation)
3. no retries are allowed for exit `70`

Explicit non-remediations:
1. do not change IR semantics only to make compile pass (for example `mode:none -> use`)
2. do not add compatibility sources under `src/main/java/com/bear/generated/**`
3. do not add `_shared` classpath shim copies of impl/exception classes

## Deterministic Remediation by Failure Class

1. `CODE=POLICY_INVALID`:
- fix `.bear/policy/*.txt` format/order/path exactness.

2. `CODE=BLOCK_PORT_INDEX_REQUIRED`:
- single-file command on IR with `kind=block` effects requires a resolved index path: explicit `--index` or inferred `<project>/bear.blocks.yaml`.
- ensure the resolved index exists and the IR tuple `(ir, projectRoot)` is present.

3. Drift lane (`exit 3`):
- use `bear fix` / `fix --all` for generated artifacts.
- or rerun compile for changed IR.

IR schema cutover reminders:
1. `block.operations` is required in `v1`.
2. Each operation must declare `contract` and `uses`.
3. Operation `uses` must be subset of block `effects`.
4. If `operation.idempotency.mode=use`, operation `uses` must include idempotency store `getOp` and `putOp`.
5. Operation invariants must be subset of block allowed invariants.

4. Boundary bypass lane (`exit 7`):
- remove direct impl usage from production seams.
- remove classloading reflection unless allowlisted.
- remove governed logic->governed impl service/module bindings.
- ensure generated entrypoint wiring uses non-null required ports.
- ensure generated-port adapters live only under governed roots.
- for cross-block adapters, either split by block package or use valid `_shared` marker contract.
- for `SHARED_PURITY_VIOLATION`: remove mutable static shared state / `synchronized` from `_shared/pure`; move stateful code to adapter/state lanes.
- for `IMPL_PURITY_VIOLATION`: remove mutable static shared state / `synchronized` from `impl`; route state through generated ports/adapters.
- for `IMPL_STATE_DEPENDENCY_BYPASS`: remove `blocks._shared.state.*` dependencies from `impl`.
- for `SCOPED_IMPORT_POLICY_BYPASS`: remove forbidden package usage from guarded lane and move integration code to adapter/state lanes.
- for `SHARED_LAYOUT_POLICY_VIOLATION`: move `_shared` Java files under `blocks/_shared/pure/**` or `blocks/_shared/state/**`.
- for `STATE_STORE_OP_MISUSE`: in adapter lane, split update-path behavior from create calls (`createWallet`/`create*`) and keep explicit not-found semantics.
- for `STATE_STORE_NOOP_UPDATE`: in `_shared/state`, replace silent missing-state returns with explicit not-found behavior.

5. Test failure lane (`exit 4`):
- fix business/test logic.
- for generated `*Impl.java` placeholder stubs, replace generated stub body fully.

6. Boundary expansion lane (`exit 5`, `pr-check`):
- treat as governance review signal, not random failure.
- verify `--base` selection first; `--base HEAD` can misclassify or hide intended delta unless explicitly instructed.
- operation add/remove is boundary-expanding surface change.
- operation `uses`, idempotency, and invariants deltas are boundary-expanding.
- operation contract deltas are operation-attributed (for example `op.ExecuteWithdraw:input.note:string`).
- if output contains `BOUNDARY_EXPANSION_DETECTED` but exit is not boundary-expansion exit (`5`), classify as tool anomaly (`OTHER`) and stop.

7. Greenfield artifact-mining contract lane:
- in greenfield, only trust current IR plus fresh generated contracts under `build/generated/bear/**` after compile.
- do not read stale `build*` outputs, run `javap` on prior class dirs, or copy signatures from prior builds.
- this is a hard agent contract rule and must be reported as a process violation if broken.

8. Schema/path mismatch or missing routed docs:
- rerun package sync from canonical source package.
- verify destination `.bear/agent/**` tree exactly matches source package tree.
- rerun the failing gate after parity is restored.

9. `SPEC_POLICY_CONFLICT`:
- apply conflict checklist in this file.
- if checklist confirms conflict, escalate and stop implementation edits.
- do not patch harness/policy/runtime files unless explicitly instructed.

10. `CONTAINMENT_METADATA_MISMATCH`:
- trigger this diagnosis only when `bear check` fails with containment/classpath signatures.
- inspect containment metadata for diagnostic evidence.
- run exactly one deterministic repair: `bear compile --all --project <repoRoot>`.
- rerun the same `bear check`; if the same containment/classpath signature persists, escalate with evidence.
- do not move/copy impl or exception classes into `_shared` as a containment workaround.

## SPEC_POLICY_CONFLICT

Decision checklist:
1. Does the spec require behavior that would violate an explicit repo enforcement or BEAR contract rule?
2. Is changing that rule absent from the spec and not explicitly instructed by the user?
3. Can implementation proceed without changing forbidden infrastructure/policy/runtime surfaces?

Interpretation:
1. If `1=true` and `2=true` and `3=false`, classify as `SPEC_POLICY_CONFLICT`, escalate, and stop.
2. If not all criteria hold, treat as normal implementation/gate remediation, not conflict.

## CONTAINMENT_METADATA_MISMATCH

Decision checklist:
1. Confirm `bear check` is failing with containment/classpath signatures (for example `CONTAINMENT_REQUIRED`, containment compile lane/classpath errors).
2. Inspect `build/generated/bear/config/containment-required.json` as diagnostic evidence.
3. Run one repair only: `bear compile --all --project <repoRoot>`.
4. Rerun the same `bear check` command.

Escalation threshold:
1. Escalate only if the same containment/classpath failure signature remains after the single deterministic repair.
2. Include pre/post `bear check` outputs and pre/post containment snapshots in escalation.


## Registry-Synced Template Keys

Lookup contract:
1. exact key `(category, failureCode, ruleId|reasonKey)`
2. failure default `(category, failureCode, *)`
3. category fallback (`INFRA` or `GOVERNANCE`)

This table is synchronized with runtime template maps by test coverage in `BearPackageDocsConsistencyTest`.

### Exact Template Keys (AgentTemplateRegistry.EXACT)

- `GOVERNANCE|BOUNDARY_BYPASS|DIRECT_IMPL_USAGE`
- `GOVERNANCE|BOUNDARY_BYPASS|IMPL_CONTAINMENT_BYPASS`
- `GOVERNANCE|UNDECLARED_REACH|UNDECLARED_REACH`
- `GOVERNANCE|REFLECTION_DISPATCH_FORBIDDEN|REFLECTION_DISPATCH_FORBIDDEN`
- `INFRA|DRIFT_MISSING_BASELINE|DRIFT_MISSING_BASELINE`
- `INFRA|IO_ERROR|PROJECT_TEST_LOCK`
- `INFRA|IO_ERROR|PROJECT_TEST_BOOTSTRAP`
- `INFRA|CONTAINMENT_NOT_VERIFIED|CONTAINMENT_METADATA_MISMATCH`
- `INFRA|IO_GIT|MERGE_BASE_FAILED`
- `INFRA|IO_GIT|NOT_A_GIT_REPO`
- `INFRA|IO_ERROR|READ_HEAD_FAILED`
- `INFRA|MANIFEST_INVALID|MANIFEST_INVALID`

### Failure Default Keys (AgentTemplateRegistry.FAILURE_DEFAULTS)

- `GOVERNANCE|BOUNDARY_BYPASS|`
- `GOVERNANCE|BOUNDARY_EXPANSION|`
- `INFRA|IR_VALIDATION|`
- `INFRA|INDEX_REQUIRED_MISSING|`
- `INFRA|POLICY_INVALID|`
- `INFRA|DRIFT_DETECTED|`
- `INFRA|IO_ERROR|`
- `INFRA|IO_GIT|`
- `INFRA|TEST_FAILURE|`
- `INFRA|COMPILE_FAILURE|`
- `INFRA|TEST_TIMEOUT|`
- `INFRA|INVARIANT_VIOLATION|`

## Forbidden Actions

1. Do not edit `build.gradle`, `settings.gradle`, `gradlew`, `gradlew.bat`, `.bear/**`, or `bin/bear*` unless explicitly instructed.
2. Do not move impl seams to alternate roots or create duplicate shim copies in `_shared`.
3. Do not override containment excludes to force checks green.

## Lane Purity/Import Notes

1. Lane/package purity/import checks are deterministic token checks.
2. In guarded lanes, forbidden package tokens in comments/strings can still trigger violations.
3. If this happens, remove/rename the token text in guarded-lane comments/strings or move the text out of guarded lanes.

## REACH_REMEDIATION_NON_SOLUTIONS

These are explicitly invalid remediation patterns:
1. If a surface is forbidden by reach policy, switching import-form usage to FQCN-form usage is not remediation.
2. "Log and return" without explicit missing-state signaling for update paths.

Required direction:
1. Fix boundary declaration, lane placement, or explicit not-found signaling semantics.
2. Do not treat syntax rewrites as policy-compliant remediation.

## BOUNDARY_EXPANSION_DETECTED

Meaning:
1. `pr-check` detected boundary-affecting change relative to provided base.
2. This can be expected when adding/changing blocks, contracts, effects, idempotency, invariants, or governed adapter boundaries.

Success criteria:
1. Use correct base (merge-base against target branch, or provided SHA) and rerun if base was wrong.
2. Confirm classification matches intentional IR/implementation delta.
3. Report classification as expected or unintended in completion report with rationale.
4. Do not "eliminate" an intentional boundary expansion by hiding or reverting valid contract changes.
5. Do not use `bear unblock` for intentional boundary expansion.
6. If boundary expansion is expected, report `BLOCKED` with required governance next action.


## GREENFIELD_PR_CHECK_POLICY

Use this branch when greenfield baseline PR behavior is encountered:
1. `pr-check` may expectedly fail with `BOUNDARY_EXPANSION_DETECTED` for newly introduced blocks/contracts/ports.
2. Do not shrink IR/contracts to force green.
3. Route to `.bear/agent/REPORTING.md` baseline waiting semantics (`WAITING_FOR_BASELINE_REVIEW`).
## GREENFIELD_BASELINE_PR

Use this class when all are true:
1. repo is greenfield baseline (newly introduced block/spec set in this PR),
2. `pr-check` reports `BOUNDARY_EXPANSION_DETECTED`,
3. expansion matches intentional new blocks/contracts/ports.

Deterministic handling:
1. stop and escalate for human boundary review.
2. do not shrink IR/contracts as a workaround.
3. report using `.bear/agent/REPORTING.md` baseline waiting semantics.

## PR_CHECK_EXIT_ENVELOPE_ANOMALY

Use this class when:
1. `pr-check` output includes `pr-check: FAIL: BOUNDARY_EXPANSION_DETECTED`
2. but command exit is not boundary-expansion exit `5`.

Deterministic handling:
1. classify `Gate blocker` as `OTHER` (tool anomaly).
2. stop immediately; do not continue alternate `pr-check` variants.
3. include both marker and observed exit in `First failure signature`.

## POLICY_SCOPE_MISMATCH

Use this class when:
1. `finding.path` matches `_shared/state/**`
2. `ruleId` is `SHARED_PURITY_VIOLATION` or `SCOPED_IMPORT_POLICY_BYPASS`

Deterministic handling:
1. classify as policy/tool anomaly.
2. stop and escalate.
3. do not alter implementation semantics as workaround.
4. report blocker as `OTHER` with the exact finding evidence.

## PROCESS_VIOLATION

Use this class when contract preconditions were bypassed (for example missing routed docs, greenfield implementation before IR compile, or skipped index preflight).

Required first-failure signature format:
1. `PROCESS_VIOLATION|<label>|<evidence>`

Labels:
1. `AGENT_PACKAGE_PARITY_PRECONDITION`
2. `GREENFIELD_HARD_STOP`
3. `INDEX_REQUIRED_PREFLIGHT`
4. `POST_FAILURE_DISCIPLINE`

Label guidance:
1. `AGENT_PACKAGE_PARITY_PRECONDITION`:
- required files missing before implementation start (`.bear/agent/CONTRACTS.md`, `.bear/agent/TROUBLESHOOTING.md`, `.bear/agent/REPORTING.md`, `.bear/agent/ref/IR_REFERENCE.md`).
2. `GREENFIELD_HARD_STOP`:
- `spec/*.bear.yaml` empty and implementation edits attempted before successful `bear validate` + `bear compile`.
3. `INDEX_REQUIRED_PREFLIGHT`:
- index-required workflow inferred but `bear.blocks.yaml` preflight is unmet before `--all` gates.
4. `POST_FAILURE_DISCIPLINE`:
- after a failing `--agent` gate, a non-`nextAction.commands` command was executed.
- evidence is the first non-allowed command string after the failing gate.

Deterministic handling:
1. classify `Gate blocker` as `OTHER`.
2. stop immediately.
3. do not apply workaround implementation edits.
4. escalate with explicit label + evidence in first-failure signature.

## Lock/IO Environment Branch

When lock signatures appear (for example `.zip.lck`, `Access is denied`, generated-file replacement lock):
1. Treat as tooling/environment IO issue first.
2. Do not mutate unrelated IR to match stale generated outputs.
3. Do not introduce workaround classes under `com.bear.generated.*`.
4. Do not edit wrapper/build harness files as lock workaround (`build.gradle`, `settings.gradle`, `gradlew`, `gradlew.bat`).
5. Fixed retry action 1: run `gradlew(.bat) --stop`.
6. Fixed retry action 2: rerun the same failing command unchanged.
7. Fixed retry action 3: rerun the same failing command unchanged one more time.
8. Do not run build/test/check command variants after `IO_LOCK` is confirmed.
9. Do not change environment knobs (`GRADLE_USER_HOME`, `buildDir`, wrapper env tweaks) during `IO_LOCK` triage unless explicitly instructed by repo policy/user.
10. Retry budget is max 2 failed retries.
11. If still failing after budget, stop and escalate with command outputs and lock evidence.

## IO_LOCK

Use this class when failure signatures include:
1. `.zip.lck`
2. `Access is denied`
3. wrapper/bootstrap lock errors in project test lane

Deterministic flow:
1. Apply fixed retry actions from Lock/IO Environment Branch only.
2. Required stop command: `gradlew(.bat) --stop`.
3. Run the same failing command unchanged, then unchanged once more.
4. No environment changes and no command variants during this flow.
5. Stop after 2 retries and report `BLOCKED(IO_LOCK)` with evidence.
6. Do not reclassify as containment unless `check` also shows containment/classpath signatures.

## Marker Handling Branch

1. Check marker `build/bear/check.blocked.marker` is advisory.
2. Continue fixing root cause; do not treat marker as completion evidence.
3. Use `bear unblock --project <repoRoot>` only to clear stale marker state.
4. For containment markers, rerun `compile` then `check` after fixing stale/missing marker causes.
5. Do not use `bear unblock` to force expected boundary expansion green.


