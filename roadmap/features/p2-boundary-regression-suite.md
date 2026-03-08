---
id: p2-boundary-regression-suite
title: Boundary regression suite
status: done
priority: medium
commitment: committed
milestone: P2
---

## Goal

Harden BEAR's boundary and governance contracts with regression tests that lock intended deterministic behavior without broadening scope into CI UX, Maven parity, or roadmap churn.

## Scope

- Expand classifier coverage for missing governance decision-table cases.
- Add focused CLI regressions for deterministic boundary-bypass and failure-envelope behavior.
- Tighten `check --all` and `pr-check --all` edge-case coverage where ordering and routing are contract-relevant.
- Prefer test-only changes unless a real determinism or contract bug is exposed.

## Non-Goals

- No CI wrapper or reporting UX work.
- No Maven parity work.
- No product-surface expansion beyond locking existing behavior.

## Contract Areas To Protect

1. Governance classification boundaries in `PrDeltaClassifier`.
2. Boundary-bypass failure code, path, and remediation envelope stability.
3. Deterministic ordering of `pr-delta`, governance-signal, and aggregated `--all` output.
4. Canonical `check --all` and `pr-check --all` argument validation and failure routing.

## Intended Test Surfaces

1. `PrDeltaClassifierTest` for missing decision-table cases and canonical delta ordering.
2. `AllModeOptionParserTest` for `--all` argument validation and invalid combinations.
3. Focused rendering or aggregation regressions for deterministic section ordering.
4. `BearCliTest` for true integration-level envelope and routing checks.

## Acceptance Criteria

1. Meaningful new regression coverage is added for classifier, bypass, ordering, and `--all` edge cases.
2. Production code changes occur only where tests expose a genuine determinism or contract bug.
3. Added tests are explicit about the protected contract, not accidental current output.
4. Verification includes the targeted app test slice and final BEAR gate checks when the feature changes warrant it.
