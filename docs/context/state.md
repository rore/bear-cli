# BEAR Session State

This file is the short operational handoff for the current work window.
For live roadmap status and backlog ordering, use `roadmap/board.md` and `roadmap/scope.md`.
Long-form historical notes are archived in `docs/context/archive/archive-state-history.md`.

## Last Updated

2026-03-11

## Current Focus

The packaged downstream CI integration is complete and stable, and `main` now also includes the completed JVM-only target-adaptation prep slice plus the follow-on seam cleanup that makes `com.bear.kernel.target` genuinely generic while keeping JVM implementation code under `com.bear.kernel.target.jvm`. The planning workflow now uses minimap under `roadmap/` as the only live planning surface. With the target seam and package ownership cleaned up, the next active product-value feature is broader boundary-escape coverage. Python and React/frontend containment profiles have been added as new parked ideas in the roadmap to document honest first-slice plans for multi-language expansion.

## Next Concrete Task

1. Start `roadmap/features/p3-broader-boundary-escape-coverage.md` as the next execution slice.
2. Keep the shipped target-seam and CI contracts stable while future multi-target work stays parked behind the prep seam.
3. Keep `roadmap/board.md`, `roadmap/scope.md`, and minimap item files as the canonical live planning source.

## Session Notes

- Completed `P3` target-adaptable CLI preparation as a JVM-only slice: app command orchestration now routes through a kernel-owned `Target` seam via `TargetRegistry`, with no Node behavior, no `.bear/target.id`, and no CLI surface changes.
- Followed with target-seam package cleanup: generic ownership stays in `com.bear.kernel.target`, JVM-only renderers/scanners and `JvmTarget` live under `com.bear.kernel.target.jvm`, and `Target.java` no longer imports JVM package types.
- Kept runtime behavior unchanged during the split: target-owned manifest, findings, and project-verification DTOs now sit in the generic package, `TargetRegistry` still resolves `JvmTarget`, and app orchestration consumes only generic seam types.
- Adopted minimap as the canonical live planning workflow under `roadmap/`; completed roadmap history now lives in minimap item files and `roadmap/board.md`.
- Added parked Python containment profile (`future-python-containment-profile.md`) and React/TypeScript frontend containment profile (`future-react-containment-profile.md`) to document the honest first-slice plans for those targets. Both are marked uncommitted and parked behind the Node and .NET profiles.
- Expanded the Python containment profile with gap solutions: (1) static `site-packages` scan to surface power-surface exposure in installed pure-Python packages at dependency-change time (cross-platform path detection using `purelib`/`platlib`); (2) an explicit commit-time boundary gate model for branch/agent workflows. Native extension reach remains an accepted `NOT_COVERABLE` gap. Typo fix: "banneable" → "bannable".
- Added two new cross-cutting multi-target documents: `future-multi-target-expansion-plan.md` (unified problems/solutions/tradeoffs for Node, Python, React) and `future-multi-target-spec-design.md` (spec-driven design covering the Target interface contract, per-language detector/scanner/verification specs, phase ordering, and agent workflow integration). Both added to `roadmap/board.md` Ideas section.
- Refined the multi-target plan/spec docs with architectural guardrails before more target implementation: introduced a two-seam model (`Target` + `AnalyzerProvider`), added a canonical locator schema requirement (`PATH` + structured locator), separated target identity from governance profile identity, added React feature-boundary-first emphasis, added Python non-overclaim scope guardrails, and reordered rollout phases to gate on locator/profile/analyzer seams first.
- Elevated `TargetDetector` + `.bear/target.id` to an explicit prerequisite epic: defined detector result model (`SUPPORTED`/`UNSUPPORTED`/`NONE`), `.bear/target.id` pin semantics, deterministic ambiguity failure behavior, and prerequisite task checklist. Made post-Node target ordering strategy-aware: distinguished technical readiness order (.NET first) from market priority order (Python first) and recommended choosing based on product strategy rather than purely technical grounds.
