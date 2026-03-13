---
inclusion: auto
description: Behavioral personas from .agent/agents/ that Kiro adopts automatically based on task context
---

# Agents Integration

Behavioral personas from `.agent/agents/`. These are not separate processes —
Kiro adopts them automatically based on task context.

## Persona → Action Mapping

| User Intent | Persona | Kiro Action |
|-------------|---------|-------------|
| Create spec | `project-planner` | Load `plan-writing` skill |
| Execute task | `backend-specialist` | Direct implementation |
| Debug issue | `debugger` | Load `systematic-debugging` skill |
| Explore code | `code-archaeologist` | Load `architecture` skill or use `context-gatherer` subagent |
| Write tests | `test-engineer` | Load `testing-patterns` skill |
| Write docs | `documentation-writer` | Update context docs |
| Complex feature | `orchestrator` | Coordinate multiple skills/workflows |

## Available Personas

- `.agent/agents/orchestrator.md` — multi-agent coordination
- `.agent/agents/project-planner.md` — discovery, planning, requirements
- `.agent/agents/debugger.md` — root cause analysis, systematic debugging
- `.agent/agents/test-engineer.md` — testing strategies, test design
- `.agent/agents/code-archaeologist.md` — legacy code analysis, refactoring
- `.agent/agents/documentation-writer.md` — technical documentation
- `.agent/agents/explorer-agent.md` — codebase analysis, file discovery
- `.agent/agents/backend-specialist.md` — API design, server-side code (Node/Python targets)

## Notes

- Personas are adopted automatically — no @agent-name syntax
- `intelligent-routing` skill handles automatic persona selection
- Personas map to skills/workflows in `.agent/` directory, not to Kiro's native subagents
- Kiro's native subagents (separate from personas):
  - `context-gatherer` — explores unfamiliar codebases
  - `general-task-execution` — delegates independent subtasks
  - `custom-agent-creator` — creates new custom agents
