# BEAR Program Board

Context entrypoint:
- `docs/context/CONTEXT_BOOTSTRAP.md`

## Last Updated

2026-03-02

## Current Milestone

`P2` (active)

## Milestone Pipeline

`v0 complete -> M1 complete -> M1.1 complete -> Preview Release complete -> P2 active -> P3`

## Interpretation Guardrails

- This file tracks milestone feature status and queue order.
- This file is not the canonical milestone feature-definition document.
- For "what features are in Preview?", use `docs/context/roadmap.md` -> `Preview Release` -> `Preview contract (must ship)`.

## Preview Feature Status (Roadmap Contract)

This section is the operational answer for:
- "What are Preview features?"
- "Where are we standing on Preview features?"

Status summary:
- `10/10` Preview contract features are `DONE`

1. Deterministic `bear validate` (schema/semantic + normalization).  
   Status: `DONE`
2. Deterministic `bear compile` + impl-preservation ownership.  
   Status: `DONE`
3. Deterministic `bear check` drift gate (`ADDED|REMOVED|CHANGED|MISSING_BASELINE`).  
   Status: `DONE`
4. Deterministic `check` test-stage semantics (drift-first, stable failure/timeout).  
   Status: `DONE`
5. Deterministic `bear pr-check` boundary verdict (`0` / `5`).  
   Status: `DONE`
6. Standardized non-zero failure envelope (`CODE/PATH/REMEDIATION`).  
   Status: `DONE`
7. Preview undeclared-reach enforcement (covered JVM HTTP surfaces, exit `6`).  
   Status: `DONE`
8. Self-hosting baseline (clean-clone style normal wrapper flow, no bespoke ritual).  
   Status: `DONE`
9. Single preview exit-code registry (`0,2,3,4,5,6,7,64,70,74`).  
   Status: `DONE`
10. Failure-envelope compliance coverage across validation/drift/test/usage/IO/git/internal paths.  
    Status: `DONE`

Preview standing note:
- Preview is treated as feature-complete for product development.
- No additional release-evidence documentation is required to start next feature work.

## Ready Queue (Ordered, Execution Work Items)

1. P2 stabilization: structural-test evidence bake period + strict-mode rollout decision
2. P3 prep: Maven allowed-deps containment parity

## Recently Completed (P2)

1. `Generated structural tests + minimal parity follow-up`
   - JVM compile now emits generated structural evidence tests:
     - `<BlockName>StructuralDirectionTest`
     - `<BlockName>StructuralReachTest`
   - placeholder generated tests (`*IdempotencyTest`, `*InvariantNonNegativeTest`) were replaced in generator output.
   - generated structural signal contract frozen:
     - `BEAR_STRUCTURAL_SIGNAL|blockKey=<blockKey>|test=<Direction|Reach>|kind=<KIND>|detail=<detail>`
     - fixed key order, no spaces, single-line detail, stable custom formatting.
   - expectations are embedded from generator canonical ordering (ports/ops), with runtime normalization before compare.
   - default mode is evidence-only; strict mode is opt-in via `-Dbear.structural.tests.strict=true` and fails once per test class with aggregated sorted mismatches.
   - unsupported-target containment parity lock added between single `check` and `check --all` for missing-wrapper scope-enabled path.
   - docs and package guidance updated for structural evidence semantics and strict-mode toggle.

2. `check` containment auto-wiring + post-test marker verification (Slice 1)
   - `ProjectTestRunner` now supports deterministic optional init-script injection (`-I build/generated/bear/gradle/bear-containment.gradle`).
   - `check`/`check --all` apply init-script injection only when containment scope is active per root.
   - containment preflight remains scope-gated and runs before tests only for containment-enabled roots.
   - `check --all` containment preflight is root-once (not per block), before the single root-level Gradle test invocation.
   - marker/hash verification now runs only after project tests exit `0`.
   - `check --all` keeps one root-level project test invocation per containment-enabled root (no per-block duplication).
   - docs + bear package guidance now state no manual `build.gradle` containment patching is required for `check`.
   - validated with `:app:test`, root `test`, and targeted smoke tests for:
     - containment-enabled fresh marker pass
     - stale marker fail (`exit 74`)
     - containment-disabled no-preflight/no-injection behavior
     - root-level one-invocation behavior in `check --all`.

3. `_shared` allowedDeps policy (path-scoped, containment-enforced, no IR schema changes)
   - added strict kernel-owned parser for `spec/_shared.policy.yaml` (`version: v1`, `scope: shared`, deterministic normalized deps).
   - `_shared` containment scope is active per `projectRoot` when policy exists or `_shared` Java sources exist (in addition to selected `impl.allowedDeps` blocks).
   - missing `_shared` policy in-scope defaults to JDK-only allowlist.
   - generated containment index/markers include `_shared` only when in scope.
   - `pr-check` now classifies shared-policy deltas:
     - add/change => `BOUNDARY_EXPANDING`
     - remove => `ORDINARY`
   - `pr-check --all` renders shared-policy deltas once in repo-level `REPO DELTA:` section before `SUMMARY`.
   - shared containment compile violations are mapped to containment lane (`exit 74`, `CODE=CONTAINMENT_NOT_VERIFIED`) with shared-policy-specific remediation.

4. `Wiring drift diagnostics` (deterministic canonical wiring paths + bounded detail)
   - wiring drift now reports canonical repo-relative paths:
     - `build/generated/bear/wiring/<blockKey>.wiring.json`
   - drift output no longer emits duplicate wiring path variants.
   - `check --all` block `DETAIL` now carries explicit wiring drift reason/path for faster remediation.
   - wiring drift detail ordering is frozen (`MISSING_BASELINE > REMOVED > CHANGED > ADDED`) and capped to 20 entries with deterministic overflow suffix.
   - exit taxonomy/envelopes/CLI surface unchanged.

5. `General agent done-gate hardening` (`check --all` + `pr-check --all --base <ref>`)
   - package agent workflow now requires dual-gate completion evidence before reporting done.
   - public command/context docs aligned to require both local gates as completion evidence.
   - CI remains authoritative remote `pr-check`; local `pr-check` required for fast governance feedback.

6. `Multi-block port implementer guard` (`MULTI_BLOCK_PORT_IMPL_FORBIDDEN`)
   - added structural bypass rule for classes implementing generated `*Port` interfaces across multiple generated block packages.
   - marker exception contract finalized:
     - exact marker line `// BEAR:ALLOW_MULTI_BLOCK_PORT_IMPL`
     - only valid under `src/main/java/blocks/_shared/**`
     - must appear within 5 non-empty lines above class declaration.
   - marker misuse outside `_shared` fails deterministically (`KIND=MARKER_MISUSED_OUTSIDE_SHARED`).
   - dedupe lock: when `PORT_IMPL_OUTSIDE_GOVERNED_ROOT` exists for a file, multi-block findings for that file are suppressed.
   - enforced via `check`/`check --all`/`pr-check` in bypass lane (`exit=7`, `CODE=BOUNDARY_BYPASS`).

7. `Declared allowed deps containment strict marker semantics` (selection-gated)
   - baseline selection-gated containment semantics shipped first; later extended by `_shared` policy/source scope in item `1` above.
   - skip mode is non-failing for containment artifacts/markers and emits deterministic info only when required index exists+parses+non-empty.
   - aggregate marker strictness:
     - `build/bear/containment/applied.marker` must match required hash and canonical `blocks=` CSV.
   - per-block marker strictness:
     - `build/bear/containment/<blockKey>.applied.marker` required for every canonical required block key (`block=` and `hash=` must match).
   - deterministic per-block fail-fast uses lexicographic canonical required block order.
   - lane/remediation split remains locked:
   - generated containment artifacts -> drift lane (`exit 3`, compile remediation)
   - handshake marker issues -> containment-not-verified lane (`exit 74`, marker refresh remediation)

8. `Guardrails v2.2.1: pure/shared state lane enforcement`:
   - `check`/`check --all` now enforce lane package/purity rules for:
     - `_shared/pure` purity + static-final constant constraints
     - `impl` purity and `_shared.state` dependency ban
     - scoped import policy by lane
     - `_shared` layout split (`pure` vs `state`)
   - new optional immutable-type allowlist contract:
     - `.bear/policy/pure-shared-immutable-types.txt` (FQCN-only, sorted, unique, comments/blank lines allowed)
   - docs package and consistency tests updated to keep enforcement deterministic and BEAR-generic.

9. `Guardrails v2.2.3: IO lock discipline + blocker evidence + scoped conflict precision`:
   - packaged agent docs now pin IO lock triage to deterministic steps:
     - `gradlew(.bat) --stop`
     - rerun the same failing command unchanged
     - rerun unchanged one more time
     - then stop and report `BLOCKED(IO_LOCK)`
   - lock lane now explicitly forbids command variants and environment knob changes (`GRADLE_USER_HOME`, `buildDir`, wrapper env tweaks) unless explicitly instructed.
   - reporting schema now requires blocker classification and first-failure evidence fields:
     - `Gate blocker`
     - `Stopped after blocker`
     - `First failing command`
     - `First failure signature`
   - scoped import-policy wording now explicitly states lane/path scope and app-layer non-global applicability unless separately constrained.
   - docs consistency tests now enforce these IO lock anchors and blocker-evidence tokens.

10. `Guardrails v2.2.4 (lock candidate): pr-check envelope anomaly + narrow state misuse checks`:
   - runtime `pr-check` envelope enforcement now fails deterministically when marker/exit disagree:
     - if output contains `BOUNDARY_EXPANSION_DETECTED` but exit != `5`, classify as internal anomaly (`PR_CHECK_EXIT_ENVELOPE_ANOMALY`, `exit 70`, `INTERNAL_ERROR`).
   - scanner rule additions:
     - `STATE_STORE_OP_MISUSE` for adapter update-path/create-call co-occurrence (including rename-dodge via balance/update tokens).
     - `STATE_STORE_NOOP_UPDATE` for silent missing-state returns in `_shared/state` update methods.
   - check/check-all remediation mapping now covers both new scanner rule IDs.
   - IR validation now blocks hidden-state under-spec:
     - empty `effects.allow` permitted only for echo-safe pure blocks (no idempotency/invariants; output `name:type` tuples mirror inputs with order-independent canonical matching).
   - docs/tests updates shipped:
     - no-artifact-mining stated as contract rule (docs/test enforced, not claimed as runtime scanner proof),
     - troubleshooting/reporting anomaly routing clarified,
     - new `RepoArtifactPolicyTest` enforces no tracked `build[0-9]+/` paths and no stale build path tokens in tracked text sources.

11. `Guardrails v2.2.5-lite (revised): lane-scope hardening + minimal anchor checks`:
   - scanner rule applicability is now explicitly path-allowlisted via `ruleAppliesToPath(ruleId, relPath)`.
   - `_shared/state` is explicitly excluded from purity/import bans (`SHARED_PURITY_VIOLATION`, `SCOPED_IMPORT_POLICY_BYPASS`).
   - scanner tests now include white-box rule-scope checks and a non-evaluation regression for `_shared/state`.
   - packaged docs add deterministic `POLICY_SCOPE_MISMATCH` escalation anchors.
   - docs consistency checks were reduced to minimal section-anchor coverage plus package file/legacy checks.

12. `Guardrails v2.2.6: baseline waiting semantics + decomposition determinism`:
   - packaged docs now define deterministic greenfield baseline waiting semantics (`WAITING_FOR_BASELINE_REVIEW`) with explicit blocker/outcome pairing rules.
   - bootstrap now includes explicit decomposition default + canonical split-trigger names to prevent endpoint-count dogma drift.
   - reporting schema now requires deterministic decomposition fields and baseline review scope for waiting outcomes.
   - docs consistency tests now anchor-check the new baseline/decomposition headings.
   - reach import/FQCN semantic symmetry is explicitly marked as deferred and non-enforced in this release.

13. `Guardrails v2.2.6.3: deterministic decomposition rubric + reporting precision + noop widening`:
   - BOOTSTRAP decomposition policy now uses canonical rubric tokens and derivation rules for grouped/split decisions.
   - CONTRACTS decomposition text no longer implies per-operation block mandates; anti-pattern remains router-specific.
   - REPORTING now enforces strict `DEVELOPER_SUMMARY`, deterministic status line format, grouped decomposition fields, and required `Surface evidence` forms.
   - TROUBLESHOOTING now includes `REACH_REMEDIATION_NON_SOLUTIONS` to disallow import-to-FQCN bypass remediation.
   - `STATE_STORE_NOOP_UPDATE` scanner now catches both early-return and null-guard silent no-op patterns with stable `PATTERN=` detail IDs.
   - docs consistency tests now enforce new heading anchors and normalized bootstrap line-budget ceiling.

## Next Feature Specs (Locked)

Detailed locked spec text was moved to:
- `docs/context/spec-locks/p2-completed-spec-locks.md`

## Backlog Buckets (P1/P2/P3)

- `P1`
  - `docs/context/backlog/p1-preview-closure-gaps.md` (parked; non-blocking for product development)
- `P2`
  - `docs/context/backlog/p2-bear-fix-generated-only.md` (`Completed`)
  - `docs/context/backlog/p2-declared-allowed-deps-containment.md` (`In Progress`)
- `P3`
  - `docs/context/backlog/p3-maven-allowed-deps-containment.md` (`Queued`)

## Open Risks / Decisions

- Risk: historical references still pointing to `docs/context/roadmap-v0.md` can reintroduce drift if not cleaned.
- Direction lock: BEAR semantic scope follows enforceability + determinism (wrapper-owned where possible), not domain-specific rule coverage.
- Decision lock: do not enforce endpoint-per-block decomposition; preserve structural governance focus over style/location policing.

