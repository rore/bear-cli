# TROUBLESHOOTING.md

Purpose:
- Deterministic troubleshooting router for BEAR gate failures.
- Consult only when command output is non-zero or blocked.

## Triage Router

1. Usage/args issue (`64`) -> fix invocation/arguments and rerun same command.
2. IR validation/schema/semantic issue (`2`) -> fix IR or policy syntax, then rerun validate/check.
3. Drift failure (`3`) -> regenerate deterministically (`compile` or `fix`), then rerun check.
4. Boundary bypass (`7`) -> move wiring/logic back into governed seams/roots, then rerun.
5. Undeclared reach or strict hygiene (`6`) -> declare boundary or remediate unexpected path, then rerun.
6. Project tests failed (`4`) -> fix implementation/tests; rerun check.
7. PR boundary expansion (`5`) -> confirm intentional expansion and review process.
8. IO/git/runtime environment (`74`) -> resolve repo/path/lock/bootstrap and rerun.
9. Internal failure (`70`) -> capture output and report tool defect.

## Deterministic Remediation by Failure Class

1. `CODE=POLICY_INVALID`:
- fix `.bear/policy/*.txt` format/order/path exactness.

2. Drift lane (`exit 3`):
- use `bear fix` / `fix --all` for generated artifacts.
- or rerun compile for changed IR.

3. Boundary bypass lane (`exit 7`):
- remove direct impl usage from production seams.
- remove classloading reflection unless allowlisted.
- remove governed logic->governed impl service/module bindings.
- ensure generated entrypoint wiring uses non-null required ports.
- ensure generated-port adapters live only under governed roots.
- for cross-block adapters, either split by block package or use valid `_shared` marker contract.

4. Test failure lane (`exit 4`):
- fix business/test logic.
- for generated `*Impl.java` placeholder stubs, replace generated stub body fully.

5. Boundary expansion lane (`exit 5`, `pr-check`):
- treat as governance review signal, not random failure.

## Lock/IO Environment Branch

When lock signatures appear (for example `.zip.lck`, `Access is denied`, generated-file replacement lock):
1. Treat as tooling/environment IO issue first.
2. Do not mutate unrelated IR to match stale generated outputs.
3. Do not introduce workaround classes under `com.bear.generated.*`.
4. Rerun and let BEAR deterministic retry/fallback policy execute.
5. Ensure no concurrent gate/test process holds locks.
6. If failure persists after retries, stop and report blocker details.

## Marker Handling Branch

1. Check marker `build/bear/check.blocked.marker` is advisory.
2. Continue fixing root cause; do not treat marker as completion evidence.
3. Use `bear unblock --project <repoRoot>` only to clear stale marker state.
4. For containment markers, rerun `compile` then `check` after fixing stale/missing marker causes.
