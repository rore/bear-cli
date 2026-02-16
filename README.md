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

v0 guarantees:
- Structural contract and effect-boundary enforcement
- Deterministic invariant/idempotency test gating
- Drift detection for generated artifacts

v0 non-guarantees:
- Business correctness beyond declared invariants
- Real DB/concurrency/transaction semantics
- Runtime enforcement beyond test harness
- Concurrency-safe duplicate handling (v0 idempotency is deterministic replay in the test harness)

Start here: `doc/START_HERE.md`

Current execution focus:
- `M1` workflow proof (minimal): isolated demo repo proves IR-first agent loop with one canonical gate command.
- Packaging/versioning is future work only, to be revisited when BEAR is mature enough for distribution design.
- Canonical BEAR workflow source texts for demo-copied resources: `doc/m1-canonical/` (`BEAR_PRIMER.md`, `AGENTS.md`, `BEAR_AGENT.md`, `WORKFLOW.md`).
- M1 evaluator harness docs (kept out of demo to avoid agent cheat hints): `doc/m1-eval/`.

- `kernel`: deterministic core. Contains BEAR IR parsing (YAML), validation, normalization, and target abstractions. This module is trusted seed code and is never BEAR-generated.
- `app`: CLI wrapper. Exposes `bear validate`, `bear compile`, and `bear check`. BEAR may later self-host parts of `app`, but never `kernel`.

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
- `doc/STATE.md` (current focus + next steps)
- `doc/NORTH_STAR.md` (broader motivation + long-horizon success criteria)
- `doc/ARCHITECTURE.md` (what BEAR is + v0 scope)
- `doc/GOVERNANCE.md` (normative IR diff classification and boundary-expansion policy)
- `doc/IR_SPEC.md` (canonical v0 IR model + validation rules)
- `doc/ROADMAP_V0.md` (concrete v0 execution roadmap)
- `doc/ROADMAP.md` (broader target roadmap)
- `doc/PROJECT_LOG.md` (background + major decisions)
- `doc/FUTURE.md` (explicitly out-of-scope ideas)
- `doc/PROMPT_BOOTSTRAP.md` (paste into a fresh AI session to restore context)

Demo repo (sibling):
- `../bear-account-demo/README.md` (realistic app-facing demo context)

M1 evaluator docs (this repo):
- `doc/m1-eval/RUN_MILESTONE.md`
- `doc/m1-eval/SCENARIOS.md`
- `doc/m1-eval/greenfield-build.md`
- `doc/m1-eval/feature-extension.md`
