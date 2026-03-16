# Enforcement and Alerts

For rationale see [FOUNDATIONS.md](FOUNDATIONS.md). For exact command contracts see [CONTRACTS.md](CONTRACTS.md).

If you are new to BEAR vocabulary (effects/ports/ops, governed roots), start with [TERMS.md](TERMS.md).

Promise boundary:

- BEAR is a compiler and deterministic governance system for architecture and declared semantics.
- It enforces only declared behaviors that the active target can support through generated wrappers/ports.
- Undeclared or non-supportable behaviors are intentionally outside BEAR guarantees.

## How Enforcement Works (Mental Model)

BEAR enforcement and governance connect three things: the declared boundary (IR), the generated governed surface, and deterministic gates.

- **Declared boundary (IR is the source of truth).** IR declares operations (entrypoints) and the capability boundary (`effects.allow`). Capabilities are expressed as ports: `kind=external` uses `ops`; `kind=block` routes cross-block calls via `targetBlock` + `targetOps`. `uses.allow` lets each operation select a subset of the block boundary.
- **Generated surface and ownership.** `bear compile` generates wrappers/ports and wiring metadata. Wiring metadata includes governed source roots (`governedSourceRoots`) for ownership/containment checks, plus block-port bindings for routing and bypass enforcement.
- **Deterministic gates and deltas.** `bear check` enforces the repo state against the declared boundary and generated layout (drift, covered reach/bypass rules, optional containment) and runs project tests deterministically. `bear pr-check` compares normalized deltas against a base ref and flags boundary expansion (exit `5`), including shared policy deltas.

Practical contract:
- a green `check` means the repo is consistent with the current declared boundary, so the agent can keep working inside it (and the test gate passed)
- `pr-check` makes boundary changes explicit, so expansion can be intentionally accepted (or reverted)

## Preview invariant status model

Status labels used in this page:

- `ENFORCED`: mechanically enforced in current Preview contract.
- `PARTIAL`: enforced only for Preview-covered surfaces.

Scope caveat:

- `PARTIAL` does not mean optional; it means coverage is intentionally bounded in Preview.

## Preview invariant set (public view)

1. Explicit external reach only - `PARTIAL`
2. Boundary delta visibility in PR governance - `ENFORCED`
3. Deterministic generation - `ENFORCED`
4. No generated artifact drift - `ENFORCED`
5. Two-file ownership (generated vs impl) - `ENFORCED`
6. Fail-fast structural violations - `ENFORCED`
7. Actionable non-zero failure envelope (`CODE`, `PATH`, `REMEDIATION`) - `ENFORCED`

## What BEAR enforces

Primary enforcement happens in `bear check`:

- contract and generation consistency between IR and generated artifacts
- drift detection for BEAR-owned outputs
- static boundary policy checks (for example undeclared reach and boundary bypass classes, including block-port binding/reference discipline)
- containment policy checks where configured
- project test gate integration in deterministic check flow

Coverage note:

- Undeclared-reach detection is `PARTIAL` in Preview because static detection is limited to covered surfaces.
- Dynamic dispatch (reflection/method handles) is forbidden in governed roots because it can bypass boundary enforcement.

## What BEAR alerts on

Primary governance alerting happens in `bear pr-check`:

- normalized IR deltas against `--base`
- classification of boundary-expanding changes vs ordinary changes
- deterministic non-zero signal when boundary expansion is detected
- generated-port adapter containment checks (`CODE=BOUNDARY_BYPASS`) including:
  - `RULE=PORT_IMPL_OUTSIDE_GOVERNED_ROOT`
  - `RULE=BLOCK_PORT_IMPL_INVALID`
  - `RULE=BLOCK_PORT_REFERENCE_FORBIDDEN`
  - `RULE=BLOCK_PORT_INBOUND_EXECUTE_FORBIDDEN`

Rationale:
- generated port implementations are boundary authority and must remain in governed roots; app-layer adapters implementing generated ports are a bypass.

## Where signals show up for developers

- Agent loop: `check` gives immediate deterministic feedback while the agent is still working locally.
- PR review: boundary expansion is surfaced as explicit `pr-check` output.
- CI automation: deterministic exit codes and failure footer make policy enforcement automatable.
- Local triage: deterministic line ordering and path shapes help fast diagnosis.

See [output-format.md](output-format.md) and [exit-codes.md](exit-codes.md).

## What this does not claim

- BEAR is not a business-rules correctness engine.
- BEAR does not replace project testing strategy.
- BEAR does not infer domain intent outside declared IR and configured checks.

## Related

- [INDEX.md](INDEX.md)
- [TERMS.md](TERMS.md)
- [CONTRACTS.md](CONTRACTS.md)
- [commands-check.md](commands-check.md)
- [commands-pr-check.md](commands-pr-check.md)
- [troubleshooting.md](troubleshooting.md)

