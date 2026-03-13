---
inclusion: auto
description: Domain-specific knowledge modules available in .agent/skills/. Full descriptions in each skill's SKILL.md
---

# Skills Integration

Domain-specific knowledge modules available in `.agent/skills/`.
Full descriptions in each skill's `SKILL.md`.

## Quick Selection Matrix

| Task | Skills to Load |
|------|---------------|
| Non-trivial task (3+ steps) | `workflow-orchestration` |
| Architecture decisions | `architecture` |
| Writing/reviewing Java | `clean-code`, `tdd-workflow` |
| PR review | `code-review-checklist` |
| Debugging | `systematic-debugging` |
| Planning features | `plan-writing` |
| Writing tests | `testing-patterns`, `tdd-workflow` |
| Bash scripts | `bash-linux` |
| PowerShell scripts | `powershell-windows` |
| Node target work | `nodejs-best-practices` |
| Python target work | `python-patterns` |
| React target work | `nextjs-react-expert` |
| Subagent routing | `intelligent-routing` |
| Session end | `session-end` |

## Available Skills

- `.agent/skills/workflow-orchestration/` — **BEAR-adapted execution workflow** (load first for non-trivial tasks)
- `.agent/skills/architecture/` — system design, ADRs, trade-off analysis
- `.agent/skills/bash-linux/` — Linux/bash scripting
- `.agent/skills/clean-code/` — pragmatic coding standards
- `.agent/skills/code-review-checklist/` — PR review guidelines
- `.agent/skills/intelligent-routing/` — automatic agent/task routing
- `.agent/skills/nextjs-react-expert/` — React/Next.js performance (React target)
- `.agent/skills/nodejs-best-practices/` — Node.js patterns (Node target)
- `.agent/skills/plan-writing/` — structured task planning
- `.agent/skills/powershell-windows/` — PowerShell scripting
- `.agent/skills/python-patterns/` — Python standards (Python target)
- `.agent/skills/session-end/` — session documentation and state updates
- `.agent/skills/systematic-debugging/` — 4-phase debugging methodology
- `.agent/skills/tdd-workflow/` — RED-GREEN-REFACTOR cycle
- `.agent/skills/testing-patterns/` — unit, integration, mocking strategies

## BEAR Notes

- Verification uses Gradle, not web scripts
- Specs go in `.kiro/specs/`, not project root
- Session-end: update `state.md` + `partner_model.md`; no devlog files
