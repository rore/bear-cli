---
inclusion: auto
description: Resolves conflicts between antigravity kit conventions and BEAR's existing workflows
---

# Conflicts Resolution: Antigravity Kit vs BEAR

This document resolves conflicts between antigravity kit conventions and BEAR's existing workflows.

## Resolved Conflicts

### 1. Session-End Workflow

**Situation**: Antigravity session-end has two concerns — devlog files AND partner_model updates.
These map differently to BEAR.

**Resolution**:
- **Devlog files** → NOT used. `docs/context/state.md` serves this purpose.
  Do NOT create files in `docs/devlogs/`. Do NOT run `devlog_helper.sh`.
- **partner_model updates** → FULLY ADOPTED. `.kiro/steering/partner-model.md`
  is the behavioral memory file. Update it at every session end per the protocol
  in `.kiro/steering/session-end.md`.

**Why**: The two concerns are complementary, not competing. `state.md` = project
handoff. `partner-model.md` = behavioral memory. Both serve different purposes
and both need to be maintained.

### 2. Plan File Location

**Conflict**: Antigravity saves plans in project root, BEAR uses `.kiro/specs/`

**Resolution**: Use BEAR's approach
- Create specs in `.kiro/specs/` directory
- Follow BEAR's spec structure (requirements.md, design.md, tasks.md)
- Do NOT create plan files in project root
- The plan-writing skill informs task breakdown, not file location

**Why**: BEAR's spec-driven development is a core architectural principle.

### 3. Verification Scripts

**Conflict**: Antigravity references web/frontend scripts, BEAR uses Gradle

**Resolution**: Use BEAR's approach
- Run Gradle tests: `./gradlew :app:test :kernel:test`
- Run docs guard: `ContextDocsConsistencyTest`
- Run BEAR gates: `validate`, `compile`, `check`, `pr-check`
- Ignore antigravity script references (ux_audit.py, etc.)

**Why**: BEAR is a Java CLI tool, not a web application.

### 4. Documentation Conventions

**Conflict**: Antigravity has "Fourth Table specific" sections

**Resolution**: Ignore Fourth Table references
- Fourth Table is a different project (multi-agent LLM framework)
- BEAR documentation lives in `docs/context/` and `roadmap/`
- Follow BEAR's existing doc structure
- Use minimap for roadmap management

**Why**: Project-specific conventions don't transfer.


### 5. Code Style Conventions

**Conflict**: Antigravity clean-code has web-specific anti-patterns

**Resolution**: Adapt for Java
- Core principles apply (SRP, DRY, KISS, YAGNI)
- Naming conventions apply (camelCase for Java)
- Function rules apply (small, focused, few args)
- Ignore web-specific examples (utils.ts, React components)
- Apply to Java: small methods, clear names, guard clauses

**Why**: Clean code principles are universal, examples are language-specific.

### 6. Agent Persona Invocation

**Conflict**: Antigravity uses explicit agent mentions (@agent-name)

**Resolution**: Kiro adopts personas automatically
- Do NOT use @agent-name syntax
- Kiro analyzes context and adopts appropriate persona
- Personas inform behavior, not separate invocations
- Integration documented in `agents-integration.md`

**Why**: Kiro has its own subagent architecture via `invokeSubAgent`.

### 7. Workflow Slash Commands

**Conflict**: Antigravity uses /command syntax (/brainstorm, /plan, etc.)

**Resolution**: Kiro triggers workflows automatically
- Do NOT use /command syntax
- Kiro detects intent and applies workflows
- Workflows inform process, not explicit commands
- Integration documented in `workflows-integration.md`

**Why**: Kiro's spec workflow is the primary orchestration mechanism.

## Compatibility Matrix

| Antigravity Feature | BEAR Equivalent | Use BEAR's Approach |
|---------------------|-----------------|---------------------|
| devlog files | state.md updates | ✅ YES |
| Plan files in root | Specs in .kiro/specs/ | ✅ YES |
| Web validation scripts | Gradle tests | ✅ YES |
| @agent mentions | Auto persona adoption | ✅ YES |
| /command syntax | Auto workflow trigger | ✅ YES |
| Fourth Table refs | BEAR context docs | ✅ YES |
| Clean code principles | Apply to Java | ⚠️ ADAPT |
| Architecture skill | Use as-is | ✅ COMPATIBLE |
| Testing patterns | Use as-is | ✅ COMPATIBLE |
| Systematic debugging | Use as-is | ✅ COMPATIBLE |

## Usage Guidelines

### When Using Antigravity Skills

1. **Read the skill** - Understand the principles
2. **Adapt to BEAR** - Apply BEAR's conventions
3. **Ignore project-specific** - Skip Fourth Table references
4. **Use BEAR's tools** - Gradle, not web scripts
5. **Follow BEAR's structure** - specs/, docs/context/, roadmap/

### When in Doubt

If antigravity and BEAR conventions conflict:
1. **BEAR wins** - Use BEAR's existing conventions
2. **Principles transfer** - Core concepts apply universally
3. **Examples don't** - Language/project-specific examples are reference only
4. **Ask user** - When truly ambiguous, clarify with user

## Integration Success Criteria

The integration is successful when:
- ✅ Skills provide useful principles (architecture, testing, debugging)
- ✅ BEAR's workflows remain unchanged (specs, state.md, roadmap)
- ✅ No file conflicts (no devlogs, no root plans)
- ✅ No command conflicts (no /commands, no @mentions)
- ✅ Verification uses BEAR's tools (Gradle, not web scripts)
- ✅ Documentation follows BEAR's structure (docs/context/, roadmap/)

This ensures antigravity skills enhance Kiro without disrupting BEAR's architecture.
