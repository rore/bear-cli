---
id: p3-optional-deterministic-policy-hooks
title: Optional deterministic policy hooks
status: queued
priority: medium
commitment: committed
milestone: P3
---

## Goal

Allow projects to run a small set of deterministic custom checks without turning BEAR into a policy engine.

## Scope

- Project-provided deterministic checks or scripts invoked through a narrow BEAR-owned hook contract.
- Stable mapping from hook failures into existing BEAR categories or lanes.
- Clear reproducibility and timeout rules for hook execution.

## Non-Goals

- No open-ended plugin policy engine.
- No non-deterministic remote integrations.
- No authority inversion where hooks redefine BEAR core semantics.

## Decision Locks

1. Hooks are optional and explicitly configured.
2. Hook execution must be deterministic, reproducible, and locally auditable.
3. Hook outcomes map into stable BEAR result categories instead of inventing a parallel taxonomy.

## Acceptance Criteria

1. The hook contract is narrow enough to stay deterministic and supportable.
2. A project can add at least one custom deterministic check without changing BEAR core semantics.
3. Failures remain explainable through stable BEAR lanes, codes, paths, and remediation.
