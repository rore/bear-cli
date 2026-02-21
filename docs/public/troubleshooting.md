# Troubleshooting

Use `CODE=...` first, then follow exact remediation steps.

## `IR_VALIDATION`

Symptom: validation line on IR path and exit `2`.
Likely cause: unknown key, invalid enum, missing field, or bad reference.
Fix:

1. Correct the IR at the reported path.
2. Run `bear validate <ir-file>`.
3. Retry the original command.

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
2. Wire through generated entrypoints and declared ports.
3. Re-run `bear check`.

## `TEST_FAILURE`, `TEST_TIMEOUT`, or `INVARIANT_VIOLATION`

Symptom: test-stage failure with exit `4`.
Likely cause: project tests failed, timed out, or invariant marker emitted.
Fix:

1. Fix failing test or invariant cause.
2. Re-run `bear check`.

## `MANIFEST_INVALID`

Symptom: wiring/surface semantic inconsistency with exit `2`.
Likely cause: generated manifest mismatch or unsupported semantic enforcement target.
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

- `exit-codes.md`
- `output-format.md`
- `commands-check.md`
- `commands-pr-check.md`
- `commands-validate.md`
