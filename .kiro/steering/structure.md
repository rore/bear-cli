---
inclusion: auto
description: BEAR project structure including repository organization, module boundaries, and directory layout
---

# BEAR Project Structure

## Repository Organization

```
bear-cli/
├── kernel/          # Deterministic seed (trusted core)
├── app/             # CLI orchestration and commands
├── docs/            # Documentation and context
├── roadmap/         # Live planning surface (Minimap)
├── scripts/         # Utility scripts
└── .kiro/           # Kiro configuration
```

## Core Modules

### kernel/
- Deterministic seed layer
- IR parse/validate/normalize
- Target abstractions
- No LLM/agent logic allowed

### app/
- CLI command implementations
- Command handlers: `validate`, `compile`, `fix`, `check`, `unblock`, `pr-check`
- Output/exit contract rendering
- Main entry: `com.bear.app.BearCli`

## Documentation Structure

### docs/context/
Canonical context documents for development:

- `CONTEXT_BOOTSTRAP.md` - Always load first (routing map)
- `state.md` - Current session handoff (short, operational)
- `ir-spec.md` - Canonical IR schema and validation
- `governance.md` - Diff classification policy
- `architecture.md` - System guarantees and scope
- `roadmap.md` - Milestone definitions
- `user-guide.md` - Operator commands
- `safety-rules.md` - Cleanup/delete guardrails
- `archive/` - Historical records only

### docs/public/
User-facing documentation

### roadmap/
Live planning surface (uses Minimap):
- `board.md` - Canonical item order and groups
- `scope.md` - Current focus narrative
- `features/*.md` - Active/completed work
- `ideas/*.md` - Parked/uncommitted ideas

## Source Code Layout

```
app/src/main/java/com/bear/app/
├── BearCli.java                    # Main entry point
├── BearCliCommandHandlers.java     # Command routing
├── *CommandService.java            # Command implementations
└── [various support classes]
```

## Key Files

- `bear.blocks.yaml` - BEAR IR file (in target projects)
- `build.gradle` - Module build configuration
- `AGENTS.md` - Agent guidance (repo root)


## Navigation Rules

1. **Session start**: Read `docs/context/CONTEXT_BOOTSTRAP.md` first
2. **Current work**: Check `docs/context/state.md`
3. **Roadmap status**: Check `roadmap/board.md` and `roadmap/scope.md`
4. **IR questions**: Reference `docs/context/ir-spec.md`
5. **Historical context**: See `docs/context/archive/`

## Update Protocols

### Session updates
- Update `docs/context/state.md` when work progresses
- Keep `Session Notes` concise (move old notes to archive)
- Update roadmap files when status/ordering changes

### Before push
- Run docs guard: `ContextDocsConsistencyTest`
- Follow session close protocol in `docs/context/start-here.md`

## Guardrails

- Keep `kernel/` deterministic and small (no agent logic)
- Stay within v1-preview scope
- Treat `roadmap/ideas/*.md` as out-of-scope unless asked
- Read `docs/context/safety-rules.md` before delete operations
