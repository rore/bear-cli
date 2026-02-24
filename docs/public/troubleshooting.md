# Troubleshooting

Use `CODE=...` first, then follow exact remediation steps.

## `IR_VALIDATION`

Symptom: validation line on IR path and exit `2`.
Likely cause: unknown key, invalid enum, missing field, or bad reference.
Fix:

1. Correct the IR at the reported path.
2. Run `bear validate <ir-file>`.
3. Retry the original command.

## `POLICY_INVALID`

Symptom: `policy: VALIDATION_ERROR` and exit `2`.
Likely cause: malformed policy contract file.

Files covered:
- `.bear/policy/reflection-allowlist.txt`
- `.bear/policy/hygiene-allowlist.txt`

Fix:

1. Correct policy file format/order.
2. Re-run command.

## `USAGE_INVALID_ARGS`

Symptom: `usage: INVALID_ARGS` and exit `64`.
Likely cause: missing required args or bad flag shape.
Fix:

1. Run `bear --help`.
2. Re-run with exact invocation form from command contract page.

## `IO_ERROR`

Symptom: read/write or wrapper path failure, exit `74`.
Likely cause: missing file, unreadable path, unwritable project, missing Gradle wrapper.
Fix:

1. Verify file and project paths exist and are accessible.
2. Ensure required wrapper files are present for `check`.
3. Re-run command.

For Gradle lock/bootstrap paths during `check`:

1. BEAR runs deterministic attempts with fixed backoff (`200ms`) and bounded stale-file self-heal inside selected `GRADLE_USER_HOME`.
2. Windows default attempt order:
   - `isolated`
   - early fallback to `user-cache` on first lock/bootstrap classification
   - `user-cache-retry` if needed
3. Non-Windows default attempt order:
   - `isolated`
   - `isolated-retry`
   - `user-cache`
4. Failure detail includes deterministic diagnostics:
   - `attempts=<csv>`
   - `CACHE_MODE=<isolated|user-cache|external-env>`
   - `FALLBACK=<none|to_user_cache>`
5. Marker is advisory; if stale marker cleanup is needed, run `bear unblock --project <path>` after lock cause is resolved.

Do not patch `build.gradle` as first response to lock/bootstrap errors. First use BEAR retry/fallback flow and verify BEAR-owned generated wiring.

## `UNBLOCK_LOCKED`

Symptom: `bear unblock` cannot remove `build/bear/check.blocked.marker`, exit `74`.
Likely cause: filesystem lock/attribute constraint on marker file.
Fix:

1. Close processes locking marker path.
2. Re-run `bear unblock --project <path>`.
3. Re-run `bear check`/`bear check --all`.

## `IO_GIT`

Symptom: git merge-base or git read failure in `pr-check`, exit `74`.
Likely cause: invalid base ref or non-git project root.
Fix:

1. Confirm repo is a git repository.
2. Confirm `--base <ref>` exists and is reachable.
3. Re-run `bear pr-check`.

## `DRIFT_DETECTED` or `DRIFT_MISSING_BASELINE`

Symptom: `drift:` lines and exit `3`.
Likely cause: generated artifacts are stale, missing, or edited.
Diagnostic note:
- wiring drift now reports exact canonical wiring files and reason class (for example `build/generated/bear/wiring/<block>.wiring.json`, `CHANGED`/`MISSING_BASELINE`) so remediation can target the precise file.
Fix:

1. Run `bear compile <ir-file> --project <path>` (or `bear compile --all --project <repoRoot>`).
2. Re-run `bear check`.

Containment-specific drift note:
- when containment verification is active for the selected block set, missing/malformed generated containment artifacts also fail in drift lane:
  - `build/generated/bear/config/containment-required.json`
  - `build/generated/bear/gradle/bear-containment.gradle`
- remediation stays compile-only (`bear compile ...`), then rerun `bear check`.

## `CONTAINMENT_SURFACES_SKIPPED_FOR_SELECTION` (informational)

Symptom: `check` outputs:
- `check: INFO: CONTAINMENT_SURFACES_SKIPPED_FOR_SELECTION: projectRoot=<root>: reason=no_selected_blocks_with_impl_allowedDeps`

Meaning:
- selected block set for this invocation does not declare `impl.allowedDeps`,
- containment-required index exists with non-empty required block set,
- containment verification surfaces are intentionally not enforced for this selected set.

Action:

1. If you intended containment enforcement, run `check`/`check --all` on a selected set that includes allowedDeps blocks in that project root.
2. If selection was intentional, no remediation is required.

## `CONTAINMENT_NOT_VERIFIED`

Symptom: `check: CONTAINMENT_REQUIRED: ...` with exit `74`.
Likely cause:
- aggregate marker missing/stale (`build/bear/containment/applied.marker`), or
- per-block marker missing/stale (`build/bear/containment/<blockKey>.applied.marker`).
Fix:

1. Run Gradle build once to refresh containment markers for the current generated containment requirement.
2. Re-run `bear check`.

Marker strictness:
- aggregate marker must match both `hash=<sha256(containment-required.json)>` and canonical `blocks=<csv>` from required set.
- per-block markers are required for every canonical required block key and must match `block=<blockKey>` plus the same required hash.

## `BOUNDARY_EXPANSION`

Symptom: `pr-check: FAIL: BOUNDARY_EXPANSION_DETECTED` and exit `5`.
Likely cause: IR change widened boundary contract.
Fix:

1. Review `pr-delta:` lines.
2. Route through explicit review and acceptance for expansion.
3. Keep or revert change as policy requires.

## `PORT_IMPL_OUTSIDE_GOVERNED_ROOT`

Symptom: `pr-check` failure with exit `7`, `CODE=BOUNDARY_BYPASS`, and `RULE=PORT_IMPL_OUTSIDE_GOVERNED_ROOT`.
Likely cause: a class in `src/main/java/**` implements generated `com.bear.generated.*Port` outside governed roots.
Fix:

1. Move generated-port adapter implementation under governed roots:
   - block root (`src/main/java/blocks/<block>/...`)
   - shared governed root (`src/main/java/blocks/_shared/...`)
2. Re-run `bear pr-check`.

## `MULTI_BLOCK_PORT_IMPL_FORBIDDEN`

Symptom: `check`/`pr-check` failure with exit `7`, `CODE=BOUNDARY_BYPASS`, and `RULE=MULTI_BLOCK_PORT_IMPL_FORBIDDEN`.
Likely cause:
- one class implements generated ports from multiple generated block packages without valid `_shared` marker, or
- marker is used outside `_shared`.
Fix:

1. Split adapters so each class implements ports from one generated block package, or
2. if multi-block adapter is intentional, keep it under `src/main/java/blocks/_shared/**` and place marker:
   - `// BEAR:ALLOW_MULTI_BLOCK_PORT_IMPL`
   - within 5 non-empty lines above class declaration.
3. Remove marker usage outside `_shared`.
4. Re-run `bear check` / `bear pr-check`.

## `MULTI_BLOCK_PORT_IMPL_ALLOWED` (informational)

Symptom: `pr-check` prints `pr-check: GOVERNANCE: MULTI_BLOCK_PORT_IMPL_ALLOWED: ...` and exits `0`.
Meaning: multi-block generated-port implementation is explicitly allowed by valid `_shared` marker and surfaced for governance review.
Action:

1. Review whether the multi-block adapter is intentional.
2. Keep current marker placement/rule compliance, or split adapters per generated package if isolation should be stricter.

## `UNDECLARED_REACH`

Symptom: `check: UNDECLARED_REACH` and exit `6`.
Likely cause: direct external client usage bypasses declared ports.
Fix:

1. Declare required effect port/op in IR.
2. Recompile.
3. Route calls through generated port interfaces.

## `BOUNDARY_BYPASS`

Symptom: `check: BOUNDARY_BYPASS` (or `pr-check: BOUNDARY_BYPASS`) and exit `7`.
Likely cause: seam or containment rule violation.
Fix:

1. Remove seam bypass usage.
2. Do not bind governed logic interfaces to governed impls via:
   - `META-INF/services`
   - `module-info.java provides ... with ...`
3. Prefer generated `Wrapper.of(<ports...>)` for production wiring.
4. Keep `(ports..., Logic)` constructor only for tests/advanced injection.
5. For `RULE=IMPL_CONTAINMENT_BYPASS`, move helper logic into manifest `governedSourceRoots` (block root or `src/main/java/blocks/_shared`).
6. Re-run `bear check`.

`DIRECT_IMPL_USAGE` also includes classloading reflection API usage in `src/main/**` (`Class.forName`, `loadClass`) unless allowlisted.

## `HYGIENE_UNEXPECTED_PATHS`

Symptom: `check: HYGIENE_UNEXPECTED_PATHS` and exit `6` (strict mode only).
Likely cause: opt-in strict hygiene found unexpected seed paths (for example `.g`, `.gradle-user`).
Fix:

1. Remove the unexpected path, or
2. add exact path to `.bear/policy/hygiene-allowlist.txt`,
3. rerun `bear check ... --strict-hygiene`.

## `TEST_FAILURE`, `TEST_TIMEOUT`, or `INVARIANT_VIOLATION`

Symptom: test-stage failure with exit `4`.
Likely cause: project tests failed, timed out, or invariant marker emitted.
Mapping: `INVARIANT_VIOLATION` is surfaced in the test-stage failure bucket and exits `4`.
Fix:

1. Fix failing test or invariant cause.
2. Re-run `bear check`.

## `MANIFEST_INVALID`

Symptom: wiring semantic inconsistency with exit `2`.
Likely cause:
- generated wiring mismatch,
- missing required v2 semantic fields (`logicRequiredPorts`, `wrapperOwnedSemanticPorts`, `wrapperOwnedSemanticChecks`, `blockRootSourceDir`),
- missing required v2 containment fields (`governedSourceRoots`),
- unsupported/non-v2 wiring schema,
- missing governed binding fields (`logicInterfaceFqcn`, `implFqcn`).
Fix:

1. Re-run `bear compile` (or `bear compile --all`) to regenerate manifests.
2. Ensure generated artifacts are consistent with current IR/index.
3. Re-run `bear check`.

## `INTERNAL_ERROR`

Symptom: unexpected internal failure and exit `70`.
Likely cause: unexpected runtime path in CLI.
Fix:

1. Capture stderr and command used.
2. Re-run once to confirm reproducibility.
3. Open an issue with captured diagnostics.

## Related

- [commands-check.md](commands-check.md)
- [commands-unblock.md](commands-unblock.md)
- [commands-pr-check.md](commands-pr-check.md)
- [exit-codes.md](exit-codes.md)
- [output-format.md](output-format.md)
