# BEAR CLI User Guide (Preview)

This guide is for day-to-day usage of `bear` in a project.

## What BEAR Is (quick)

BEAR is a deterministic CLI that helps you control boundary changes in backend code.

It uses an **IR** (Intermediate Representation): a small YAML spec file (for example `spec/withdraw.bear.yaml`) that declares:
- block contract (inputs/outputs)
- allowed external capabilities (effects/ports)
- key invariants and idempotency settings

From that IR, BEAR validates, generates deterministic code artifacts, and enforces consistency through `bear check` and `bear pr-check`.

## Operating model (developer + agent)

Default expectation in BEAR workflows:
- developer provides domain intent and reviews boundary-expanding changes
- agent performs BEAR mechanics (IR updates, generation, gate runs, and failure triage)

Developers are not expected to hand-edit IR as routine workflow.
IR remains the control surface that agents update when boundary/contract/effect changes are required.

## Why This Exists (AI development context)

In AI-assisted development, code can be produced quickly, but structural risk can spread just as quickly:
- new external calls can appear without explicit review
- generated artifacts can drift from declared design
- CI signals can become noisy or non-actionable

BEAR is designed to counter that pattern with deterministic, machine-checkable contracts:
- IR-first boundary declaration instead of implicit behavior spread
- explicit boundary-expansion signaling (`pr-check`) for review and governance
- deterministic gate outputs and failure envelopes for reliable CI/agent loops

Intent:
- keep inner-loop speed for AI/human implementation work
- make boundary power changes explicit, reviewable, and hard to miss
- provide stable, automatable signals rather than subjective prompts/process

For full normative command contracts, see:
- `docs/public/commands-validate.md`
- `docs/public/commands-compile.md`
- `docs/public/commands-fix.md`
- `docs/public/commands-check.md`
- `docs/public/commands-pr-check.md`
- `docs/public/exit-codes.md`

For full invariant intent and enforcement status (`ENFORCED`/`PARTIAL`/`PLANNED`), see:
- `docs/context/invariant-charter.md`

## Semantics Scope (v1.2)

BEAR uses enforcement-by-construction for a narrow semantic slice.

Meaning:
- if a semantic can be enforced entirely in generated wrapper code from declared IR boundary data, BEAR enforces it there
- BEAR does not rely on impl conventions or comment discipline for those semantics

Why idempotency is included:
- request key material is declared
- side-effect boundaries are declared ports/ops
- replay payload shape is declared outputs
- idempotency store boundary is explicitly declared

Why only these invariants:
- v1.2 invariants are structural output checks that are deterministic and boundary-checkable
- they run on fresh and replay paths so replay is not a bypass

Out of scope:
- business policy inference
- auth/time-window/distributed transaction semantics not declared in IR
- cross-port atomicity guarantees

Canonical decision rule:
- `docs/context/ir-spec.md` -> `Semantics Decision Rule (Canonical)`

## Core commands

### 1. Validate IR

```text
bear validate <ir-file>
```

Use when:
- reviewing or checking IR produced/updated by the agent
- checking schema/semantic correctness before generation

Success:
- exits `0`
- prints canonical normalized YAML to stdout

### 2. Compile generated artifacts

```text
bear compile <ir-file> --project <path>
```

Use when:
- creating or updating generated BEAR-owned code from IR

Behavior:
- regenerates BEAR-owned artifacts under `<project>/build/generated/bear`
- runtime support classes are emitted only under `<project>/build/generated/bear/src/main/java/com/bear/generated/runtime`
- does not overwrite user-owned impl files under `<project>/src/main/java`
- generated logic wrappers include a default wiring factory: `Wrapper.of(<ports...>)`
- resolves block identity (`blockKey`) deterministically:
  - index-authoritative when exactly one `(ir, projectRoot)` tuple matches in `bear.blocks.yaml`
  - IR fallback when no tuple match exists
  - deterministic validation failure on canonical identity mismatch or ambiguous tuple matches
- semantics are wrapper-owned by generation:
  - idempotent wrappers compute/replay/persist idempotency payloads
  - invariant checks run in wrappers on fresh and replay paths

### 3. Repair generated artifacts

```text
bear fix <ir-file> --project <path>
```

Use when:
- repairing or normalizing BEAR-owned generated artifacts
- regenerating deterministic generated output without touching user-owned impl files

Behavior:
- regenerates BEAR-owned artifacts under `<project>/build/generated/bear`
- preserves user-owned impl files under `<project>/src/main/java`
- does not run tests or PR governance checks

### 3b. Repair generated artifacts (multi-block)

```text
bear fix --all --project <repoRoot>
```

Optional flags:
- `--blocks <path>`
- `--only <name1,name2,...>`
- `--fail-fast`
- `--strict-orphans`

### 4. Local gate (drift + tests)

```text
bear check <ir-file> --project <path> [--strict-hygiene]
```

Use when:
- verifying generated artifacts are in sync
- running project test gate after drift passes

Behavior:
- fails with drift exit code when generated baseline is stale/missing
- fails with undeclared-reach exit code when covered direct HTTP client usage bypasses declared ports
- fails with boundary-bypass exit code when BEAR seam rules are violated:
  - direct impl usage in `src/main/**`
  - classloading reflection APIs in `src/main/**` (`Class.forName`, `loadClass`) unless allowlisted
  - governed logic-to-governed-impl binding in production seams:
    - `src/main/resources/META-INF/services/**`
    - `src/main/java/module-info.java` (`provides ... with ...`)
  - top-level `null` port args in governed entrypoint constructors
  - governed impl missing required effect-port usage (unless exact suppression comment is present)
  - governed impl placeholder stubs left unimplemented (`RULE=IMPL_PLACEHOLDER`)
- sanctioned production wiring path is generated `Wrapper.of(<ports...>)`
  - keep constructor `(ports..., Logic)` for tests/advanced injection
- optional strict hygiene mode (`--strict-hygiene`) fails on unexpected seed paths (`.g`, `.gradle-user`) unless allowlisted
- policy allowlist files (optional, exact-path deterministic parser):
  - `.bear/policy/reflection-allowlist.txt`
  - `.bear/policy/hygiene-allowlist.txt`
- fails with validation (`MANIFEST_INVALID`) when wiring semantics are inconsistent
  - wrapper-owned semantic ports must not overlap logic-required ports
- runs project tests only after no-drift result
- invariant marker-first classification:
  - test output marker `BEAR_INVARIANT_VIOLATION|block=...|kind=...|field=...|observed=...|rule=...`
  - deterministic `CODE=INVARIANT_VIOLATION` in test-failure bucket
- when IR declares `block.impl.allowedDeps`, also enforces containment handshake (script/index/marker hash) before tests
- uses the same frozen block-key canonicalizer as `compile` for single-command index matching and identity checks

### 4b. Repo gate (multi-block)

```text
bear check --all --project <repoRoot> [--strict-hygiene]
```

Optional flags:
- `--blocks <path>`
- `--only <name1,name2,...>`
- `--fail-fast`
- `--strict-orphans`
- `--strict-hygiene`

Use when:
- a repo has multiple BEAR-managed blocks declared in `bear.blocks.yaml`
- CI/local workflow needs one deterministic gate for all managed blocks
- multiple blocks can share one `projectRoot`; `check --all` runs undeclared-reach/tests once per root

### 4c. Clear check block marker

```text
bear unblock --project <path>
```

Use when:
- you want to clear a stale marker after prior lock/bootstrap failures

Behavior:
- clears `<project>/build/bear/check.blocked.marker` if present
- idempotent (`unblock: OK` even when marker is absent)
- retries marker delete up to 3 attempts with fixed `200ms` backoff
- on persistent marker lock, fails with `CODE=UNBLOCK_LOCKED` (exit `74`)
- marker is written only after BEAR exhausts deterministic retry/fallback for Gradle lock/bootstrap IO
- marker is advisory: `check`/`check --all` proceed with a fresh gate run even if marker exists

### 5. PR governance gate (base diff classification)

```text
bear pr-check <ir-file> --project <path> --base <ref>
```

Use when:
- classifying IR deltas against base branch in CI/review flow

Behavior:
- exits `5` when boundary expansion is detected
- exits `0` for no-boundary-expansion outcomes

### 5b. PR governance gate (multi-block)

```text
bear pr-check --all --project <repoRoot> --base <ref>
```

Optional flags:
- `--blocks <path>`
- `--only <name1,name2,...>`
- `--strict-orphans`

## Non-zero failure envelope

All non-zero exits in `validate`, `compile`, `fix`, `check`, and `pr-check` include:

```text
CODE=<enum>
PATH=<locator>
REMEDIATION=<deterministic-step>
```

Contract:
- emitted exactly once
- always the last 3 stderr lines
- no stderr output after `REMEDIATION=...`

## `PATH` locator meaning

`PATH` is a locator, not only a filesystem path.

Allowed forms:
- repo-relative path (example: `spec/withdraw.bear.yaml`)
- stable pseudo-path token (example: `cli.args`, `cli.command`, `project.tests`, `internal`, `build/generated/bear`)

Disallowed:
- absolute filesystem paths

## Exit codes (quick reference)

- `0` pass
- `2` validation/schema/semantic failure
- `3` drift failure
- `4` project test failure (including timeout)
- `5` boundary expansion detected in `pr-check`
- `6` undeclared reach detected in `check` (`CODE=UNDECLARED_REACH`)
- `6` boundary bypass detected in `check` (`CODE=BOUNDARY_BYPASS`)
- `64` usage/argument failure
- `70` internal/unexpected failure
- `74` IO/git failure

For `check --all` and `pr-check --all`, final exit code is aggregated via explicit severity ranking (not numeric max), defined in `docs/public/exit-codes.md`.

## Lock troubleshooting (Windows/Gradle)

If `compile`/`check`/`check --all` fails with lock signatures (for example `.zip.lck`, `Access is denied`, wrapper dist lock paths), treat this as tooling/IO, not domain-test failure.

Deterministic remediation order:
1. Ensure no concurrent Gradle/BEAR gate process is running on the same repo.
2. Rerun the command and let BEAR apply deterministic Gradle-home policy:
   - if `GRADLE_USER_HOME` is externally set: BEAR uses only that path (`external-env`, `external-env-retry`)
   - on Windows without external override: `isolated` -> early fallback `user-cache` -> `user-cache-retry`
   - on non-Windows without external override: `isolated` -> `isolated-retry` -> `user-cache`
3. If lock/bootstrap persists after BEAR retries, stop and report blocker details (path + command + output); do not apply IR renames, ACL edits, or manual generated-file surgery as workarounds.
4. If check writes `<project>/build/bear/check.blocked.marker`, clear it with `bear unblock --project <path>` after fixing lock/bootstrap cause.

Lock/bootstrap failure detail includes deterministic diagnostics:
- `attempts=<csv>`
- `CACHE_MODE=<isolated|user-cache|external-env>`
- `FALLBACK=<none|to_user_cache>`

Build wiring note:
- use BEAR-owned generated wiring (`build/generated/bear/gradle/bear-containment.gradle` where applicable)
- do not patch `build.gradle` manually as first response to lock/bootstrap failures

Expected classification:
- lock/tooling faults -> `IO_ERROR` (`74`)
- real failing tests -> `TEST_FAILURE` (`4`)

## Invariant Contract (preview)

`bear check` enforces:
- deterministic generated-artifact drift gate
- covered undeclared-reach gate for direct HTTP bypass surfaces
- boundary-bypass seam gate (`DIRECT_IMPL_USAGE`, `NULL_PORT_WIRING`, `EFFECTS_BYPASS`)
- project tests (only after drift and undeclared-reach pass)

`bear pr-check` enforces:
- deterministic base-vs-head boundary-delta visibility
- explicit boundary-expansion verdict for CI/review

Planned after preview:
- broader undeclared-reach coverage classes
- dependency-direction and cross-domain leakage hardening
- additional structural invariants listed in `docs/context/invariant-charter.md`

## allowed deps (v1 preview)

Use `block.impl.allowedDeps` when implementation logic needs a non-JDK pure library.

Example:
```yaml
impl:
  allowedDeps:
    - maven: com.fasterxml.jackson.core:jackson-databind
      version: 2.17.2
```

Governance:
- `bear pr-check` classifies allowed-deps add/version-change as `BOUNDARY_EXPANDING`
- allowed-deps removal is `ORDINARY`

Enforcement (`bear check`):
- supported target: Java+Gradle with wrapper
- requires generated containment artifacts:
  - `build/generated/bear/gradle/bear-containment.gradle`
  - `build/generated/bear/config/containment-required.json`
  - `build/bear/containment/applied.marker`
- marker hash must match containment index hash
- `bear check` does not invoke Gradle; run Gradle build/test once after compile to refresh marker

Non-Gradle projects:
- `pr-check` governance still works
- `check` fails deterministically when `impl.allowedDeps` is present because enforcement cannot be guaranteed in current preview scope

All non-zero command exits include deterministic footer lines:
- `CODE=...`
- `PATH=...`
- `REMEDIATION=...`

## Typical loop

1. Developer states feature intent in domain terms.
2. Agent updates IR if boundary/contract/effect changes are needed.
3. Agent runs `bear validate <ir-file>`.
4. Agent runs `bear compile <ir-file> --project <path>`.
5. If IR declares `impl.allowedDeps` on Java+Gradle, ensure project applies generated containment entrypoint and run Gradle build/test once.
6. If generated artifacts need deterministic repair, run `bear fix <ir-file> --project <path>` (or `fix --all`).
7. Agent implements user-owned logic/tests.
8. Agent runs `bear check <ir-file> --project <path>`.
9. For PR governance, run `bear pr-check <ir-file> --project <path> --base <ref>`.


