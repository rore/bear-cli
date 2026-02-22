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
Likely cause: malformed allowlist or malformed reach-surface contract.
Fix:

1. Correct policy file format/order (exact repo-relative paths, sorted, no duplicates/globs).
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
Fix:

1. Run `bear compile <ir-file> --project <path>` or `bear fix ...`.
2. Re-run `bear check`.

## `BOUNDARY_EXPANSION`

Symptom: `pr-check: FAIL: BOUNDARY_EXPANSION_DETECTED` and exit `5`.
Likely cause: IR change widened boundary contract.
Fix:

1. Review `pr-delta:` lines.
2. Route through explicit review and acceptance for expansion.
3. Keep or revert change as policy requires.

## `UNDECLARED_REACH`

Symptom: `check: UNDECLARED_REACH` and exit `6`.
Likely cause: direct external client usage bypasses declared ports.
Fix:

1. Declare required effect port/op in IR.
2. Recompile.
3. Route calls through generated port interfaces.

## `BOUNDARY_BYPASS`

Symptom: `check: BOUNDARY_BYPASS` and exit `6`.
Likely cause: impl seam bypass (`DIRECT_IMPL_USAGE`, `NULL_PORT_WIRING`, `EFFECTS_BYPASS`).
Fix:

1. Remove seam bypass usage.
2. Do not bind governed logic interfaces to governed impls via:
   - `META-INF/services`
   - `module-info.java provides ... with ...`
3. Prefer generated `Wrapper.of(<ports...>)` for production wiring.
4. Keep `(ports..., Logic)` constructor only for tests/advanced injection.
5. Re-run `bear check`.

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

Symptom: wiring/surface semantic inconsistency with exit `2`.
Likely cause: generated manifest mismatch, missing governed binding fields (`logicInterfaceFqcn`, `implFqcn`), or unsupported semantic enforcement target.
Fix:

1. Re-run `bear compile` to regenerate manifests.
2. Ensure supported target and consistent generated artifacts.
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
