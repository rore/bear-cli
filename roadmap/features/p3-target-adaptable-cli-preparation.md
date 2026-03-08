---
id: p3-target-adaptable-cli-preparation
title: Target-adaptable CLI preparation
status: done
priority: high
commitment: committed
milestone: P3
---

## Goal

Prepare BEAR CLI for multi-target support by extracting one deterministic target seam while preserving current JVM behavior byte-for-byte.

This slice is preparation only:
- no Node target yet
- no IR schema changes
- no exit-code expansion
- no broad docs-neutralization sweep

## Why This Matters

- It creates the architectural seam required for BEAR to expand beyond JVM without scattering target conditionals through the core.
- It is higher product value than several remaining hardening slices because it unlocks future applicability directly.
- It keeps BEAR aligned with explicit seams, deterministic contracts, and a small trusted core.

## Scope

Promote the preparation phases of the parked multi-target initiative into active roadmap work:

1. Contract freeze and regression harness
- add or extend parity coverage that pins current JVM behavior for:
  - `check` stage ordering and exit mapping
  - `check --all` aggregation behavior
  - `pr-check` rendering and exit mapping
  - deterministic failure-envelope behavior
- add one behavior-parity lock that compares pre-seam and post-seam JVM paths once the seam exists

2. Introduce one target seam
- add deterministic target abstractions such as:
  - `TargetId`
  - `TargetDetector`
  - `Target`
  - `TargetRegistry`
- add one dispatch point per command service instead of scattered `if (java)` / `if (node)` branching
- add deterministic ambiguity behavior and optional `.bear/target.id` pin handling

3. Move JVM behavior behind `JvmTarget`
- move current JVM-specific generation, wiring-only generation, governed-root interpretation, scanners, and verification runner ownership behind `JvmTarget`
- keep core orchestration generic and target-agnostic

## Non-Goals

- no `NodeTarget` implementation in this slice
- no TypeScript, pnpm, or Node import-containment work yet
- no IR `target:` field
- no new configuration model beyond optional `.bear/target.id`
- no new public command surface

## Acceptance

1. Existing JVM command behavior remains byte-identical on pinned parity tests.
2. Core command services do not reference JVM-only scanners or path conventions directly outside the target dispatch seam.
3. No generated JVM layout or CLI contract changes are introduced by the refactor.
4. The seam is sufficient to let a later `NodeTarget` land without reopening core orchestration design.

## Follow-On Work

After this preparation slice lands, the remainder of the parked initiative stays future-scoped:
- initial `NodeTarget` scan-only support
- Node covered undeclared-reach
- Node dependency governance
- Node project verification runner
- target-profile docs and stabilization follow-ups

Canonical future initiative:
- `roadmap/ideas/future-target-adaptable-cli-node.md`
