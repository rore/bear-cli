# BEAR Project State

This file captures execution state.  
It must stay concise and operational.

Last Updated: 2026-02-12

---

## Current Focus

Phase 2 (JVM target): begin deterministic code generation (`bear compile`) from validated/normalized IR.

---

## Current Phase

Phase: 2 -- JVM Target

Checklist:
- [x] Phase 1 `bear validate <file>` implemented end-to-end
- [x] Strict schema + semantic validation implemented and tested
- [x] Deterministic normalization + canonical YAML emission implemented and golden-tested
- [ ] Phase 2 codegen scaffolding started

Exit condition:
`bear compile` emits deterministic JVM artifacts (ports + skeleton + test templates) for the demo IR.

---

## Next Concrete Task

Phase 2 (JVM target): start `bear compile` for v0 Withdraw demo:

1. Generate ports (interfaces) from `effects.allow`
2. Generate a skeleton `Withdraw` block that can only use declared ports/ops
3. Generate deterministic JUnit tests for v0 invariants + idempotency pattern
4. Add drift detection (regen + compare) only once codegen exists

Notes:
- Gradle wrapper is available: use `.\gradlew.bat` (Windows) to build/run without a global Gradle install.
- Canonical IR specification is now `doc/IR_SPEC.md`.
- Canonical demo IR fixture path is `spec/fixtures/withdraw.bear.yaml`.

---

## Phase Pipeline (Do Not Skip)

Phase 1 -- IR foundation  
Phase 2 -- JVM target  
Phase 3 -- Two-file enforcement  
Phase 4 -- `bear check`  
Phase 5 -- Demo proof  

If work does not advance the pipeline toward:

> "Naive withdraw fails. Correct withdraw passes."

It is scope drift.

---

## Upcoming Design Decisions (Not Blocking Current Phase)

- Strictness model for effects enforcement (compile-time vs runtime test only)

---

## Non-Blocking (Parked)

- Early self-hosting (NormalizeIr)
- CLI packaging strategy
- Multi-target ideas
- Enterprise features

---

## v0 Success Condition

All of the following must be true:

- Deterministic IR validation
- Deterministic JVM code generation
- Two-file enforcement
- `bear check` gate
- Demo proves regression prevention

---

## Session Notes

Append short bullet points only.  
No essays. No philosophy.

- Added Gradle wrapper scripts + wrapper jar.
- Aligned docs to v0 clarified scope: deterministic constraint compiler, structured ports, explicit guarantees/non-guarantees.
- Locked canonical demo IR details (`version`, invariant `kind`, idempotency `store.port/getOp/putOp`).
- Implemented `bear validate <file>` end-to-end (strict schema + semantic validation, deterministic normalization, canonical YAML emission) with spec fixtures + golden output.
- Stabilized Gradle behavior for Windows locks: wrapper defaults `GRADLE_USER_HOME` to temp and Gradle build outputs are redirected to temp (`bear-cli-build/<runId>`).
