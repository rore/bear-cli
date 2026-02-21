# BEAR (bear-cli)

<p align="center">
  <img src="assets/logo/bear.png" alt="BEAR logo" width="360" />
</p>

BEAR is a deterministic CLI for boundary governance in backend projects. It validates a small IR, compiles generated boundaries, and enforces consistency with deterministic checks.

## What BEAR is not (Preview non-goals)

- Not a business-rules engine.
- Not a runtime transaction framework.
- Not a verifier of domain correctness beyond declared contract checks.
- Not a replacement for application test strategy.

## Quickstart

Prerequisite: `bear` is available on `PATH`, and the demo repo is checked out at `../bear-account-demo`.

1. Open the demo repo.

```powershell
Set-Location ..\bear-account-demo
```

2. Let your agent implement the repo specs.

```text
Implement the specs.
```

3. Verify the full repo gate.

```powershell
bear check --all --project .
```

Success signal: `bear check --all --project .` exits `0` and reports no failing blocks.

## Mental model

- `IR`: declared block contract and allowed boundaries.
- `compile`: deterministic generated artifacts from IR.
- `check`: deterministic local gate (drift, static boundary checks, tests).
- `pr-check`: deterministic PR governance classification against base.

Pipeline: `IR -> compile -> check`, and `pr-check` for PR boundary governance.

## Links

- [docs/public/INDEX.md](docs/public/INDEX.md)
- [docs/public/QUICKSTART.md](docs/public/QUICKSTART.md)
- [docs/public/exit-codes.md](docs/public/exit-codes.md)
- [docs/public/output-format.md](docs/public/output-format.md)
- [docs/public/troubleshooting.md](docs/public/troubleshooting.md)
- Optional project context: [docs/context/state.md](docs/context/state.md)

## Preview scope and supported targets

Preview scope:

- Deterministic `validate`, `compile`, `fix`, `check`, and `pr-check` contracts.
- Deterministic failure footer and stable exit code registry.
- Deterministic boundary-expansion signaling in `pr-check`.

Supported targets:

- JVM/Java target in Preview.
- Primary containment enforcement path is Java plus Gradle wrapper when `impl.allowedDeps` is declared.


