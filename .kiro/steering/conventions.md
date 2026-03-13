---
inclusion: auto
description: BEAR development conventions including architecture principles, code organization, exit codes, testing strategy, and safety protocols
---

# BEAR Development Conventions

## Architecture Principles

### Determinism as Contract
- All validation, normalization, generation, and checks must be reproducible
- Exit codes, schemas, generated artifacts, and governance outputs are frozen
- Ordering must be deterministic across runs and environments

### Kernel Purity
- `kernel/` is the deterministic trusted seed
- No LLM/agent logic allowed in kernel
- Keep kernel small and focused on core abstractions

### Boundary Authority Over Style
- BEAR governs structure and boundary surfaces, not code style
- Focus on ports, dependency power, governed roots, and policy verification
- Implementation freedom inside declared boundaries is intentional

## Code Organization

### Module Boundaries
- `kernel/` - IR parse/validate/normalize, target abstractions
- `app/` - CLI orchestration, command handlers, output rendering
- Generic types in `com.bear.kernel.target`
- Target-specific code in `com.bear.kernel.target.<targetId>` (e.g., `jvm`)

### Package Ownership
- Generic ownership stays in `com.bear.kernel.target`
- JVM-only code under `com.bear.kernel.target.jvm`

- `Target.java` must not import target-specific package types

## Naming Conventions

### Feature Branches
- Pattern: `copilot/<feature-name>` or `<agent>/<feature-name>`
- Examples: `copilot/implement-phase-a`, `copilot/analyze-code-base`

### Roadmap Items
- Features: `p2-<name>`, `p3-<name>` (milestone prefix)
- Ideas: `future-<name>` (parked/uncommitted work)
- Files: `roadmap/features/*.md`, `roadmap/ideas/*.md`

### Spec Files
- Location: `.kiro/specs/`
- Pattern: `phase-<letter>-<description>.md`
- Examples: `phase-a-architecture-prerequisites.md`

## Exit Code Registry (Frozen)

BEAR uses a strict exit code contract:
- `0` - Pass
- `2` - Validation or semantic/config contract failure
- `3` - Drift failure (`check`)
- `4` - Project test failure or timeout (`check`)
- `5` - Boundary expansion detected (`pr-check`)
- `6` - Reach/hygiene policy failure (`check`)
- `7` - Structural bypass policy failure (`check` and `pr-check`)
- `64` - Usage or argument failure
- `70` - Internal or unexpected failure
- `74` - IO or git failure

### Exit Code by Command
- `validate`: 0, 2, 64, 70, 74
- `compile`: 0, 2, 64, 70, 74
- `fix`: 0, 2, 64, 70, 74
- `check`: 0, 2, 3, 4, 6, 7, 64, 70, 74
- `unblock`: 0, 64, 70, 74
- `pr-check`: 0, 2, 5, 7, 64, 70, 74

### Aggregation Severity Rank
`--all` commands aggregate by severity rank (not numeric max):
- `check --all`: 70 > 74 > 64 > 2 > 3 > 7 > 6 > 4 > 0
- `compile --all`: 70 > 74 > 64 > 2 > 0
- `fix --all`: 70 > 74 > 64 > 2 > 0
- `pr-check --all`: 70 > 74 > 64 > 2 > 7 > 5 > 0

Never add new exit codes without updating the frozen contract.

## Failure Envelope Contract

Last three stderr lines always conform to:
```
CODE=<error-code>
PATH=<file-path>
REMEDIATION=<action>
```

This contract is frozen and must remain deterministic.


## BEAR IR Conventions

### IR v1 Scope Lock
- One `logic` block per IR file
- `block.effects.allow` is authoritative capability boundary
- `operations[].uses.allow` must be subset of block effects
- No per-operation `impl` bindings
- No behavior DSL or transaction semantics

### IR File Location
- Target projects: `bear.blocks.yaml` at project root
- Demo/test projects: `.ci/bear.blocks.yaml`

## Testing Strategy

### Verification Workflow
- **Fast-by-default**: Batch related edits before verification
- **Targeted tests**: Use method-level tests during iteration
- **Gradle daemon**: Default for local work (faster)
- **Full verify**: Use `--no-daemon` for CI-parity checks
- **Docs guard**: Run `ContextDocsConsistencyTest` before push

### Test Commands
```bash
# Fast iteration (with daemon)
./gradlew :app:test :kernel:test

# CI-parity (no daemon)
./gradlew --no-daemon :app:test :kernel:test

# Specific test
./gradlew :app:test --tests ClassName

# Docs guard before push
./gradlew --no-daemon :app:test --tests com.bear.app.ContextDocsConsistencyTest
```

## Safety Protocols

### Delete Operations
- Never run recursive deletes outside repo root
- Use provided scripts: `safe-clean-temp.ps1`, `safe-clean-bear-generated.ps1`
- Always use `-WhatIf` for dry runs
- Read `docs/context/safety-rules.md` before cleanup

### Git Scope Discipline
- **NEVER use `git add -A` or `git add .`** - these stage unrelated files
- **ALWAYS stage files explicitly** by path: `git add path/to/file.java`
- **Stay focused on session scope** - only commit files related to current task
- **Check `git status`** before staging to verify what's being added
- **Stash unrelated changes** if they appear in working directory
- Example: If working on Phase A fixes, only stage Phase A implementation files

### Demo Cleanup
- Use `scripts/clean-demo-branch.ps1` for demo cleanup
- Default: reset branch, preserve committed BEAR paths
- `-IncludeGreenfieldReset`: clear BEAR-authored paths for spec-only branches
- Routine cleanup does NOT require `state.md` updates

## Documentation Maintenance

### Session Updates
When work progresses, update:
1. `docs/context/state.md` - Last Updated, Current Focus, Next Task, Session Notes
2. `roadmap/board.md` + `roadmap/scope.md` - if roadmap status/ordering changed
3. Canonical docs - only when semantics changed

### Session Close Protocol
Before ending a session:
1. Follow `docs/context/start-here.md` -> Session Close Protocol
2. Keep `Session Notes` concise (move old notes to archive)
3. Run docs guard: `ContextDocsConsistencyTest`

### Context Budget
- Keep `state.md` within `ContextDocsConsistencyTest` caps
- Move oldest notes to `docs/context/archive/archive-state-history.md`
- Do not store local tool/terminal diagnostics in `state.md`

## Roadmap Management

### Minimap Workflow
- `roadmap/board.md` - canonical item order and groups
- `roadmap/scope.md` - current focus narrative
- `roadmap/features/*.md` - active/completed work
- `roadmap/ideas/*.md` - parked/uncommitted ideas
- Follow `tools/minimap/SKILL.md` for roadmap updates

### Status Transitions
- Ideas → Features when committed to active work
- Features marked completed when shipped
- Keep `docs/context/roadmap.md` for milestone definitions only
