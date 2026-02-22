# Enforcement and Alerts

For rationale see [FOUNDATIONS.md](FOUNDATIONS.md). For exact command contracts see [CONTRACTS.md](CONTRACTS.md).

Promise boundary:

- BEAR is a compiler and CI gate for architecture and declared semantics.
- It enforces only declared behaviors that the active target can support through generated wrappers/ports.
- Undeclared or non-supportable behaviors are intentionally outside BEAR guarantees.

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
- static boundary policy checks (for example undeclared reach and boundary bypass classes)
- containment policy checks where configured
- project test gate integration in deterministic check flow

Coverage note:

- Undeclared-reach detection is `PARTIAL` in Preview because static detection is limited to covered surfaces.

## What BEAR alerts on

Primary governance alerting happens in `bear pr-check`:

- normalized IR deltas against `--base`
- classification of boundary-expanding changes vs ordinary changes
- deterministic non-zero signal when boundary expansion is detected

## Where signals show up for developers

- PR review: boundary expansion is surfaced as explicit `pr-check` output.
- CI gate: deterministic exit codes and failure footer make policy enforcement automatable.
- Local triage: deterministic line ordering and path shapes help fast diagnosis.

See [output-format.md](output-format.md) and [exit-codes.md](exit-codes.md).

## What this does not claim

- BEAR is not a business-rules correctness engine.
- BEAR does not replace project testing strategy.
- BEAR does not infer domain intent outside declared IR and configured checks.

## Related

- [INDEX.md](INDEX.md)
- [CONTRACTS.md](CONTRACTS.md)
- [commands-check.md](commands-check.md)
- [commands-pr-check.md](commands-pr-check.md)
- [troubleshooting.md](troubleshooting.md)
