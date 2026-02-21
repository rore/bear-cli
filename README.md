# BEAR (bear-cli)

<p align="center">
  <img src="assets/logo/bear.png" alt="BEAR logo" width="360" />
</p>

Multi-module Java (17 target) Gradle project for the `bear` CLI.

BEAR is a deterministic boundary-governance layer for AI-assisted (or human) backend code changes.
It introduces a small BEAR IR (block intermediate representation) and compiles it into:
- non-editable skeletons
- structured capability ports (effects boundaries)
- deterministic tests (idempotency + invariants)
- a single `bear check` enforcement gate suitable for CI

Core governance litmus:
- New external interaction capability must not be introduced silently.
- Boundary-expanding IR changes must be visible/signaled deterministically.

Current preview guarantees:
- Structural contract and effect-boundary enforcement
- Deterministic invariant/idempotency test gating
- Drift detection for generated artifacts

Current preview non-guarantees:
- Business correctness beyond declared invariants
- Real DB/concurrency/transaction semantics
- Runtime guarantees outside governed generated wrappers
- Concurrency-safe duplicate handling (idempotency is deterministic replay in the test harness)

Semantics direction (v1.2):
- BEAR enforces semantics by construction only when they are deterministically enforceable from declared IR boundary data (inputs/outputs/ports).
- Idempotency is included because wrapper code can enforce it completely from declared key material, store port, and outputs.
- Invariants are intentionally limited to structural output checks.
- Semantics requiring hidden context or policy inference remain out of scope.

Canonical rule and rationale:
- `doc/IR_SPEC.md` -> `Semantics Decision Rule (Canonical)`

Start here: `doc/START_HERE.md`

Execution tracking:
- live milestone status and queue: `doc/PROGRAM_BOARD.md`
- current session handoff: `doc/STATE.md`
- milestone definitions and priorities: `doc/ROADMAP.md`
- archived superseded planning files: `doc/archive/README.md`

- `kernel`: deterministic core. Contains BEAR IR parsing (YAML), validation, normalization, and target abstractions. This module is trusted seed code and is never BEAR-generated.
- `app`: CLI wrapper. Exposes `bear validate`, `bear compile`, `bear fix`, `bear check`, and `bear pr-check`. BEAR may later self-host parts of `app`, but never `kernel`.

## Quickstart (dev)

Prereqs:
- JDK installed (`java -version`)

Run the CLI via Gradle (recommended during development).

Windows (PowerShell):
```powershell
.\gradlew.bat :app:run --args="--help"
.\gradlew.bat :app:run --args="validate spec/fixtures/withdraw.bear.yaml"
```

macOS/Linux (bash/zsh):
```sh
./gradlew :app:run --args="--help"
./gradlew :app:run --args="validate spec/fixtures/withdraw.bear.yaml"
```

The wrapper defaults `GRADLE_USER_HOME` to a temp location (`bear-cli-gradle-home`) when unset to avoid Windows lock/permission issues. You can still override it explicitly:
```powershell
$env:GRADLE_USER_HOME = "$env:LOCALAPPDATA\bear-gradle-home"
.\gradlew.bat --no-daemon :app:run --args="--help"
```

## Local install (run without Gradle)

Build an installable CLI distribution:
```powershell
.\gradlew.bat :app:installDist
```

This repo writes Gradle outputs to a temp build root (`%TEMP%\bear-cli-build\...`) to avoid Windows file locks in workspace `build/` directories.
For development, prefer running the CLI via `:app:run`.

Docs:
- `doc/PROGRAM_BOARD.md` (live milestone feature status + ordered feature queue)
- `doc/STATE.md` (short current-session handoff)
- `doc/USER_GUIDE.md` (user-facing command usage + failure envelope quick reference)
- `doc/demo/PREVIEW_DEMO.md` (preview demo operator guide and scenario map)
- `doc/NORTH_STAR.md` (broader motivation + long-horizon success criteria)
- `doc/ARCHITECTURE.md` (what BEAR is + active scope)
- `doc/GOVERNANCE.md` (normative IR diff classification and boundary-expansion policy)
- `doc/IR_SPEC.md` (canonical v1 IR model + validation rules)
- `doc/ROADMAP.md` (single roadmap: milestones, done criteria, post-preview priorities)
- `doc/PROJECT_LOG.md` (background + major decisions)
- `doc/FUTURE.md` (explicitly out-of-scope ideas)
- `doc/PROMPT_BOOTSTRAP.md` (paste into a fresh AI session to restore context)
- `doc/archive/README.md` (archived doc index)

Demo repo (sibling):
- `../bear-account-demo/README.md` (realistic app-facing demo context)

## Allowed Deps Containment Integration (Java+Gradle)

When an IR declares `block.impl.allowedDeps`, BEAR generates containment wiring artifacts during `bear compile`:
- `build/generated/bear/config/containment-required.json`
- `build/generated/bear/gradle/bear-containment.gradle`

Project integration requires one deterministic Gradle include:

```gradle
apply from: "$rootDir/build/generated/bear/gradle/bear-containment.gradle"
```

`bear check` verifies containment only by generated artifacts + marker hash:
- marker: `build/bear/containment/applied.marker`
- hash must match `sha256(build/generated/bear/config/containment-required.json)`

`bear check` does not invoke Gradle. If marker is missing/stale, run Gradle once, then rerun `bear check`.

Legacy evaluator docs (historical reference):
- `doc/m1-eval/*`


