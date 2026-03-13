---
inclusion: auto
description: Structured procedures from .agent/workflows/. Triggered automatically by Kiro based on user intent
---

# Workflows Integration

Structured procedures from `.agent/workflows/`. Triggered automatically by Kiro
based on user intent — no /command syntax needed.

## Workflow → Kiro Mapping

| Workflow | Trigger | Kiro Action |
|----------|---------|-------------|
| `brainstorm` | New feature, unclear requirements | Spec requirements phase |
| `plan` | Breaking down features | Spec task breakdown phase |
| `create` | Implementing features | Spec execution workflow |
| `debug` | Bug investigation | `systematic-debugging` skill + debugger persona |
| `test` | Writing/running tests | `testing-patterns` + `tdd-workflow` skills |
| `enhance` | Refactoring, code review | `clean-code` + `code-review-checklist` skills |
| `status` | Project state check | Read `docs/context/state.md` + `roadmap/board.md` |
| `orchestrate` | Complex multi-step tasks | **Load `workflow-orchestration` skill** (BEAR-adapted, overrides generic orchestrate.md) |

## Available Workflows

- `.agent/workflows/brainstorm.md`
- `.agent/workflows/plan.md`
- `.agent/workflows/create.md`
- `.agent/workflows/debug.md`
- `.agent/workflows/test.md`
- `.agent/workflows/enhance.md`
- `.agent/workflows/status.md`
- `.agent/workflows/orchestrate.md` ⚠️ generic — use `workflow-orchestration` skill for bear-cli work

## Notes

- Workflows are triggered automatically — no /command syntax
- Complex tasks chain workflows: brainstorm → plan → create → test
- BEAR's spec-driven development is the primary orchestration mechanism
- For non-trivial bear-cli tasks (3+ steps), always load `workflow-orchestration` skill first — it is BEAR-adapted and overrides the generic orchestrate workflow
