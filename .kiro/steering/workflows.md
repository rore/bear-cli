---
inclusion: auto
description: BEAR development workflows including session start, feature development, testing, and deployment procedures
---

# BEAR Development Workflows

## Starting a New Session

1. Read `docs/context/CONTEXT_BOOTSTRAP.md` first
2. Check `docs/context/state.md` for current work
3. Review `roadmap/board.md` and `roadmap/scope.md` for priorities
4. Load task-relevant context docs on demand

## Implementing a Feature

### Standard Flow
1. Check if spec exists in `.kiro/specs/`
2. If no spec, consider creating one for non-trivial work (3+ steps)
3. Update BEAR IR first if boundary authority must change
4. Implement code inside generated constraints
5. Run deterministic gates: `validate` → `compile` → `check`
6. Verify with tests (batch edits, use daemon for speed)
7. Run `pr-check` before PR

### BEAR Command Sequence
```bash
# 1. Validate IR
bear validate --all --project .

# 2. Generate artifacts
bear compile --all --project .

# 3. Fix generated artifacts if needed
bear fix --all --project .

# 4. Run enforcement gate
bear check --all --project .

# 5. PR governance gate
bear pr-check --all --project . --base <target-branch>
```

## Multi-Target Work

### Current State
- JVM target is in Preview (active)
- Node, Python, .NET, React are parked in `roadmap/ideas/`
