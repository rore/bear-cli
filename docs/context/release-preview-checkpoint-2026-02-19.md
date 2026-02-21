# Preview Checkpoint - 2026-02-19

## Scope

This checkpoint consolidates post-Scenario-2 stabilization work needed for preview reliability.

## Included

- JVM generator hardening:
  - always import `java.math.BigDecimal` in generated entrypoints (prevents non-decimal wrapper compile defects)
  - avoid doubled `Port` suffix generation for port names already ending in `Port`
- Compile lock resilience:
  - staging-tree sync strategy for generated outputs
  - bounded retry/backoff around lock-prone file operations
  - deterministic lock diagnostics (`WINDOWS_FILE_LOCK`)
- `check` project-test robustness:
  - default isolated Gradle home (`<project>/.bear-gradle-user-home`) unless caller overrides `GRADLE_USER_HOME`
  - wrapper lock signatures classified as `IO_ERROR` (`74`), not `TEST_FAILURE` (`4`)
- BEAR package policy hardening:
  - explicit stop-and-report behavior on tooling/IO defects
  - explicit prohibition of workaround non-`*Impl.java` classes under `com.bear.generated.*`
- User/operator guidance:
  - lock troubleshooting added to `docs/context/user-guide.md`

## Validation

- Full test suite passed:
  - `.\gradlew.bat --no-daemon test`

## Release Baseline Freeze

The following command contracts are the preview checkpoint baseline:

- `docs/public/commands-check.md`
- `docs/public/commands-pr-check.md`
- `docs/public/exit-codes.md`

## Next

- Open P2 starter item: `bear fix` for generated artifacts only (no impl-file edits).

