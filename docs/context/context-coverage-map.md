# Context Coverage Map

Purpose:
- No-loss mapping for context refactors and compaction.
- Every critical topic has a canonical destination.

| Topic | Canonical active destination | Archive destination (if historical) |
| --- | --- | --- |
| Session handoff protocol | `docs/context/state.md`, `docs/context/start-here.md` | `docs/context/archive/archive-state-history.md` |
| Milestone status and ordered queue | `docs/context/program-board.md` | `docs/context/archive/archive-state-history.md` |
| Milestone definitions and done criteria | `docs/context/roadmap.md` | `docs/context/archive/archive-roadmap-v0.md` |
| IR schema/normalization/semantic rule | `docs/context/ir-spec.md` | n/a |
| Governance diff classification and enforcement intent | `docs/context/governance.md` | `docs/context/archive/archive-state-history.md` |
| Safety cleanup/deletion guardrails | `docs/context/safety-rules.md` | `docs/context/archive/archive-state-history.md` |
| Demo simulation protocol and grading rubric | `docs/context/demo-agent-simulation.md` | `docs/context/archive/demo-preview-demo.md` |
| Operator command/failure guidance | `docs/context/user-guide.md` | `docs/context/archive/archive-state-history.md` |
| Architecture scope lock and non-goals | `docs/context/architecture.md` | `docs/context/archive/archive-state-history.md` |
| Historical rationale trail | `docs/context/project-log.md` | `docs/context/archive/*` |

Compaction contract:
1. No section may be removed unless mapped here.
2. Historical details move to archive with a dated snapshot header.
3. Active docs keep only current canonical wording.
