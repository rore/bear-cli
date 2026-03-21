# How BEAR Works

This document covers BEAR's design rationale, workflow, architecture, and
vocabulary. Read the [README](../../README.md) first for the quick version.

## Why BEAR Exists

Agent-generated code can move fast and hide architectural drift. BEAR exists
to make boundary authority changes explicit, enforceable, visible to the agent
while it works, and reviewable in PRs and CI.

The goal is to move trust from intent to machine-checkable gates and explicit
governance signals.

## The Workflow

Agent loop:

1. **Declare boundary in IR.** When domain intent changes boundary authority,
   the agent writes or updates a small YAML contract (`bear-ir/<block>.bear.yaml`).
2. **Validate.** `bear validate` checks IR syntax and semantic rules.
3. **Compile.** `bear compile` (or `bear fix`) generates deterministic
   wrappers, port interfaces, typed request/result types, manifests, and
   structural tests.
4. **Implement.** The agent writes business logic inside the generated
   constraints — implementing the `<Block>Logic` interface and wiring port
   adapters.
5. **Check.** `bear check` gives immediate feedback: drift detection,
   boundary bypass scanning, undeclared reach, and project test execution.
6. **PR governance.** `bear pr-check` compares normalized IR against a base
   ref and classifies boundary-expanding deltas for human review.

Developer role:
- Review explicit boundary signals in PR/CI.
- Accept or reject boundary expansion intentionally.

Who edits IR:
- In normal flow, the agent updates IR.
- Developers are not expected to hand-author IR routinely.

## What Gets Generated

`bear compile` produces everything under `build/generated/bear/`:

**Per-block Java sources:**
- `<Block>Logic` — interface the user-authored impl must satisfy
- `<Block>_<Operation>` — wrapper class owning idempotency and invariant
  enforcement (the agent never writes this code)
- `<Block>_<Operation>Request` / `Result` — typed POJOs derived from IR
  contracts
- Port interfaces — `<Port>Port` with typed methods matching declared ops
- Block-client classes — dispatch clients for `kind=block` cross-block ports

**Manifests:**
- `surfaces/<block>.surface.json` — boundary snapshot (capabilities, invariants,
  IR hash) used for drift detection
- `wiring/<block>.wiring.json` — FQCN map for governance checks (impl class,
  governed source roots, block-port bindings)
- `config/containment-required.json` — Gradle containment hook configuration

**Structural tests:**
- Direction tests — verify wrapper factory and logic interface signatures
- Reach tests — verify port interface methods match IR declarations

**Gradle hook:**
- `bear-containment.gradle` — enforces impl class isolation at compile time

## Key Concepts

A **block** is one governed backend unit. Its boundary is declared in IR:
operations (entrypoints), allowed effects (external ports and cross-block
ports), idempotency wiring, and structural invariants.

An **operation** is one entrypoint inside a block. Each operation has a typed
contract (inputs/outputs), a `uses.allow` subset of the block boundary, and
optional idempotency and invariant declarations.

A **port** is a named dependency surface. Port kinds:
- `kind=external` — uses `ops` (e.g. a database adapter)
- `kind=block` — cross-block call contract via `targetBlock` + `targetOps`

**BearValue** is the universal wire type across all port calls — a
`Map<String, String>` carrier that prevents typed leakage across block
boundaries.

**Effects** declare the block-level capability boundary. `effects.allow` lists
all ports the block may use. `uses.allow` on each operation selects a subset.

## Governance Signals

**Drift** — generated artifacts no longer match IR-derived output. Detected by
`bear check`. Exit `3`.

**Undeclared reach** — code touches a forbidden surface outside the declared
boundary. Detected by `bear check`. Exit `6`.

**Boundary bypass** — code reaches around governed surfaces (e.g. app-layer
adapter implementing a generated port). Detected by `bear check` and
`bear pr-check`. Exit `7`.

**Boundary expansion** — IR delta widens declared authority (new ports, new
ops, relaxed invariants). Detected by `bear pr-check`. Exit `5`.

Every non-zero failure includes `CODE`, `PATH`, and `REMEDIATION` for
actionable triage.

## Architecture

BEAR CLI has two modules:
- `kernel/` — deterministic IR parsing, validation, normalization, and JVM
  target rendering. No app-layer dependencies.
- `app/` — CLI command handlers, governance checks (drift, bypass, reach),
  multi-block orchestration, agent diagnostics.

## Preview Scope

Preview focuses on deterministic contracts, governance signaling, and bounded
enforcement coverage. It is intentionally not full behavioral verification or
runtime policy enforcement.

The invariant status model uses two labels:
- `ENFORCED` — mechanically enforced in current Preview contract
- `PARTIAL` — enforced only for Preview-covered surfaces

See [ENFORCEMENT.md](ENFORCEMENT.md) for the full invariant set.
